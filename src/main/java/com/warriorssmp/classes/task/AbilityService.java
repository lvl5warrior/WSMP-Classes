package com.warriorssmp.classes.task;

import com.warriorssmp.classes.data.PlayerClassData;
import com.warriorssmp.classes.model.SkillNode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.Random;

public final class AbilityService {

    private final ClassConfig config;
    private final org.bukkit.plugin.Plugin plugin;
    private final Random random = new Random();

    public AbilityService(ClassConfig config, org.bukkit.plugin.Plugin plugin) {
        this.config = config;
        this.plugin = plugin;
    }

    /** When a player has both a base node and its upgrade unlocked (the
     *  upgrade always requires the base as a prerequisite, so both stay
     *  "unlocked" forever), only the strongest one should ever fire — never
     *  both stacked on the same hit. "Strongest" = highest cost, which is how
     *  every tree here is authored (upgrades always cost more than their
     *  base). */
    private SkillNode bestNodeFor(PlayerClassData data, String abilityKey) {
        SkillNode best = null;
        for (String nodeId : data.nodeInvestment.keySet()) {
            SkillNode node = config.node(nodeId);
            if (node == null || !node.abilityKey().equals(abilityKey)) continue;
            if (data.chosenClass != null && node.playerClass() != data.chosenClass) continue;
            if (best == null || node.cost() > best.cost()) best = node;
        }
        return best;
    }

    private boolean rollAndCooldown(PlayerClassData data, SkillNode node) {
        long now = System.currentTimeMillis();
        Long readyAt = data.abilityCooldowns.get(node.id());
        if (readyAt != null && readyAt > now) return false;
        double chance = node.chance() * (1.0 + statBonusTotal(data, "STAT_PROC_CHANCE_BOOST", "percent-per-point") / 100.0);
        if (random.nextDouble() >= chance) return false;
        data.abilityCooldowns.put(node.id(), now + node.cooldownMillis());
        return true;
    }

    /** Sums investment × per-point value across every stat-buff node of a
     *  given ability key the player has points in — e.g. 3 points in a
     *  "+2% per point" node returns 6.0. Used for passive, always-on bonuses
     *  rather than the chance/cooldown-gated procs everything else here is. */
    private double statBonusTotal(PlayerClassData data, String abilityKey, String perPointParam) {
        double total = 0;
        for (var entry : data.nodeInvestment.entrySet()) {
            SkillNode node = config.node(entry.getKey());
            if (node == null || !node.abilityKey().equals(abilityKey)) continue;
            if (data.chosenClass != null && node.playerClass() != data.chosenClass) continue;
            total += entry.getValue() * node.param(perPointParam, 0);
        }
        return total;
    }

    /** Recomputes and re-applies real vanilla attribute modifiers (max health,
     *  movement speed) from the player's invested stat-buff points. Called on
     *  join and immediately after any node investment changes — always
     *  removes the old modifier first so relogging or investing more points
     *  never stacks duplicates. */
    public void applyPassiveStats(Player player, PlayerClassData data) {
        applyAttribute(player, data, "STAT_MAX_HEALTH_BOOST", "hearts-per-point",
                org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH, "wsmp_classes_max_health");
        applyAttribute(player, data, "STAT_MOVEMENT_SPEED_BOOST", "percent-per-point",
                org.bukkit.attribute.Attribute.GENERIC_MOVEMENT_SPEED, "wsmp_classes_movement_speed");
        applyAttribute(player, data, "STAT_ARMOR_BOOST", "armor-per-point",
                org.bukkit.attribute.Attribute.GENERIC_ARMOR, "wsmp_classes_armor");
        applyAttribute(player, data, "STAT_ATTACK_DAMAGE_BOOST", "damage-per-point",
                org.bukkit.attribute.Attribute.GENERIC_ATTACK_DAMAGE, "wsmp_classes_attack_damage");
        applyAttribute(player, data, "STAT_ARMOR_TOUGHNESS_BOOST", "toughness-per-point",
                org.bukkit.attribute.Attribute.GENERIC_ARMOR_TOUGHNESS, "wsmp_classes_armor_toughness");
    }

    private void applyAttribute(Player player, PlayerClassData data, String abilityKey, String perPointParam,
                                 org.bukkit.attribute.Attribute attribute, String keyName) {
        var instance = player.getAttribute(attribute);
        if (instance == null) return;

        // Deterministic UUID derived from a fixed string, so the same
        // modifier can always be found and removed cleanly on relog or
        // re-investment, without needing to persist a random UUID anywhere.
        // (Bukkit's UUID+String AttributeModifier constructor is deprecated
        // in current Paper API and will eventually need the NamespacedKey-
        // based replacement — left as-is for now since it still compiles
        // and works, and swapping it needs verifying against a real build
        // rather than guessing at the exact modern method signatures.)
        java.util.UUID modifierId = java.util.UUID.nameUUIDFromBytes(keyName.getBytes());
        instance.getModifiers().stream()
                .filter(m -> m.getUniqueId().equals(modifierId))
                .findFirst()
                .ifPresent(instance::removeModifier);

        double perPointValue = statBonusTotal(data, abilityKey, perPointParam);
        if (perPointValue <= 0) return;

        // MAX_HEALTH is hearts -> health points (x2, flat). Percent-based
        // params scale off the attribute's base value (movement speed,
        // attack damage — a flat number wouldn't mean much there without
        // knowing the weapon/base scale). Everything else (armor) is just
        // added directly, since armor points are already a small flat scale
        // and a percentage of a base of 0 (no armor worn) would do nothing.
        double amount;
        if (abilityKey.equals("STAT_MAX_HEALTH_BOOST")) {
            amount = perPointValue * 2.0;
        } else if (perPointParam.equals("percent-per-point")) {
            amount = perPointValue / 100.0 * instance.getBaseValue();
        } else {
            amount = perPointValue;
        }
        var modifier = new org.bukkit.attribute.AttributeModifier(modifierId, keyName, amount,
                org.bukkit.attribute.AttributeModifier.Operation.ADD_NUMBER);
        instance.addModifier(modifier);
    }

    private PotionEffectType effect(SkillNode node, String key, String fallback) {
        PotionEffectType type = PotionEffectType.getByName(node.stringParam(key, fallback));
        return type != null ? type : PotionEffectType.getByName(fallback);
    }

    // ---------------------------------------------------------------- MELEE HIT

    /** ON_HIT_SELF_POTION_PROC — a melee hit has a chance to buff the
     *  attacker. Melee-only by design, to keep it a distinct Warrior flavor
     *  from Archer's ranged-only bow procs. */
    public void onMeleeHit(Player attacker, PlayerClassData data) {
        trySelfPotionProc(attacker, data);
    }

    private void trySelfPotionProc(Player player, PlayerClassData data) {
        SkillNode node = bestNodeFor(data, "ON_HIT_SELF_POTION_PROC");
        if (node == null || !rollAndCooldown(data, node)) return;

        PotionEffectType type = effect(node, "effect", "STRENGTH");
        int amplifier = (int) node.param("amplifier", 0);
        int durationTicks = (int) (node.param("duration-seconds", 5) * 20);

        player.addPotionEffect(new PotionEffect(type, durationTicks, amplifier));
        data.lifetimeAbilityProcs++;
    }

    /** ON_HIT_TARGET_POTION_PROC — any hit (melee OR bow) has a chance to
     *  debuff the target. Shared between Warrior's melee kit and Archer's bow
     *  kit, since both are "I hit something, it gets worse for them." */
    public void onHitTarget(Player attacker, PlayerClassData data, LivingEntity target) {
        SkillNode node = bestNodeFor(data, "ON_HIT_TARGET_POTION_PROC");
        if (node == null || !rollAndCooldown(data, node)) return;

        PotionEffectType type = effect(node, "effect", "SLOWNESS");
        int amplifier = (int) node.param("amplifier", 0);
        int durationTicks = (int) (node.param("duration-seconds", 3) * 20);

        target.addPotionEffect(new PotionEffect(type, durationTicks, amplifier));

        double knockbackMult = node.param("knockback-multiplier", 0);
        if (knockbackMult > 0) {
            Vector kb = target.getLocation().toVector().subtract(attacker.getLocation().toVector())
                    .normalize().multiply(knockbackMult).setY(0.3);
            target.setVelocity(target.getVelocity().add(kb));
        }

        data.lifetimeAbilityProcs++;
    }

    /** ON_HIT_BLEED_PROC — a separate, independent proc from the slow/
     *  knockback one above (different ability key, so a class can invest in
     *  both without one overriding the other's "best node" selection).
     *  Applies Wither by default rather than Poison — a damage-over-time
     *  effect without Poison's green particle cloud reads more like an
     *  actual bleeding wound. */
    public void onHitBleed(Player attacker, PlayerClassData data, LivingEntity target) {
        SkillNode node = bestNodeFor(data, "ON_HIT_BLEED_PROC");
        if (node == null || !rollAndCooldown(data, node)) return;

        PotionEffectType type = effect(node, "effect", "WITHER");
        int amplifier = (int) node.param("amplifier", 0);
        int durationTicks = (int) (node.param("duration-seconds", 4) * 20);
        target.addPotionEffect(new PotionEffect(type, durationTicks, amplifier));
        data.lifetimeAbilityProcs++;
    }

    /** Flat percent-of-damage-dealt lifesteal, scaled by investment the same
     *  way Vitality/Swiftness/etc scale — always-on, no chance roll, no
     *  cooldown, just a straight heal-per-point-invested off real damage
     *  already dealt (passed in, not recomputed). Capped at the player's
     *  own max health so it can't overheal past their health bar. */
    public void onHitLifesteal(Player attacker, PlayerClassData data, double damageDealt) {
        double percentPerPoint = statBonusTotal(data, "STAT_LIFESTEAL_BOOST", "percent-per-point");
        if (percentPerPoint <= 0) return;

        double healAmount = damageDealt * (percentPerPoint / 100.0);
        if (healAmount <= 0) return;

        var maxHealthAttr = attacker.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH);
        double maxHealth = maxHealthAttr != null ? maxHealthAttr.getValue() : 20.0;
        attacker.setHealth(Math.min(maxHealth, attacker.getHealth() + healAmount));
    }

    // ---------------------------------------------------------------- LOW HEALTH

    /** ON_LOW_HEALTH_SELF_POTION_PROC — checked whenever the player takes
     *  damage; if their health (after damage) is below the node's threshold
     *  and the node is off cooldown, grant the configured buff. Chance is
     *  ignored here (always fires if the threshold + cooldown allow it) since
     *  "sometimes doesn't save you at low health" would feel bad. */
    public void onLowHealthCheck(Player player, PlayerClassData data, double healthFraction) {
        SkillNode node = bestNodeFor(data, "ON_LOW_HEALTH_SELF_POTION_PROC");
        if (node == null) return;
        double threshold = node.param("health-threshold-percent", 50) / 100.0;
        if (healthFraction > threshold) return;

        long now = System.currentTimeMillis();
        Long readyAt = data.abilityCooldowns.get(node.id());
        if (readyAt != null && readyAt > now) return;
        data.abilityCooldowns.put(node.id(), now + node.cooldownMillis());

        PotionEffectType type = effect(node, "effect", "REGENERATION");
        int amplifier = (int) node.param("amplifier", 1);
        int durationTicks = (int) (node.param("duration-seconds", 5) * 20);
        player.addPotionEffect(new PotionEffect(type, durationTicks, amplifier));

        double absorptionHearts = node.param("absorption-hearts", 0);
        if (absorptionHearts > 0) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION,
                    (int) (node.param("absorption-duration-seconds", 8) * 20), (int) absorptionHearts - 1));
        }

        player.playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 0.6f, 1.4f);
        player.sendMessage("§d✦ " + node.displayName() + " activates!");
        data.lifetimeAbilityProcs++;
    }

    // ---------------------------------------------------------------- BOW SHOT

    /** BOW_BONUS_DAMAGE_PROC — flags the arrow to deal bonus real damage on
     *  hit (read back out in the damage listener). Returns null if it didn't
     *  proc. */
    public Double onBowShotBonusDamage(Player shooter, PlayerClassData data) {
        SkillNode node = bestNodeFor(data, "BOW_BONUS_DAMAGE_PROC");
        if (node == null || !rollAndCooldown(data, node)) return null;
        data.lifetimeAbilityProcs++;
        return node.param("bonus-damage", 2.0);
    }

    /** BOW_MULTISHOT_PROC — fires N additional real arrows alongside the
     *  original, spread in a narrow cone, exactly like a vanilla Crossbow's
     *  Multishot enchant but usable on any bow. */
    public void onBowShotMultishot(Player shooter, PlayerClassData data, Arrow original) {
        SkillNode node = bestNodeFor(data, "BOW_MULTISHOT_PROC");
        if (node == null || !rollAndCooldown(data, node)) return;

        int extraArrows = (int) node.param("extra-arrows", 2);
        double spreadDegrees = node.param("spread-degrees", 8);
        Vector baseVelocity = original.getVelocity();
        World world = original.getWorld();

        for (int i = 0; i < extraArrows; i++) {
            double angle = Math.toRadians(spreadDegrees * (i % 2 == 0 ? 1 : -1) * ((i / 2) + 1));
            Vector spread = rotateAroundY(baseVelocity.clone(), angle);
            Arrow extra = world.spawnArrow(original.getLocation(), spread, 1.0f, 0f, Arrow.class);
            extra.setShooter(shooter);
            extra.setDamage(original.getDamage());
            extra.setPickupStatus(AbstractArrow.PickupStatus.ALLOWED);
        }
        data.lifetimeAbilityProcs++;
    }

    private Vector rotateAroundY(Vector v, double angleRad) {
        double cos = Math.cos(angleRad);
        double sin = Math.sin(angleRad);
        double x = v.getX() * cos - v.getZ() * sin;
        double z = v.getX() * sin + v.getZ() * cos;
        return new Vector(x, v.getY(), z);
    }

    // ---------------------------------------------------------------- SPLASH POTION

    // ---------------------------------------------------------------- ENDER PEARL

    /** PEARL_LANDING_PROC — when your thrown Ender Pearl lands and teleports
     *  you, optionally grant yourself a buff and/or deal real bonus damage to
     *  the nearest enemy within range (a "teleport strike"). */
    public void onPearlLand(Player player, PlayerClassData data, Location landing) {
        SkillNode node = bestNodeFor(data, "PEARL_LANDING_PROC");
        if (node == null || !rollAndCooldown(data, node)) return;

        double selfDurationSeconds = node.param("self-duration-seconds", 0);
        if (selfDurationSeconds > 0) {
            PotionEffectType selfType = effect(node, "self-effect", "SPEED");
            player.addPotionEffect(new PotionEffect(selfType, (int) (selfDurationSeconds * 20),
                    (int) node.param("self-amplifier", 0)));
        }

        double damage = node.param("nearby-damage", 0);
        double radius = node.param("nearby-radius", 3);
        if (damage > 0) {
            LivingEntity nearest = null;
            double nearestDist = Double.MAX_VALUE;
            for (Entity e : landing.getWorld().getNearbyEntities(landing, radius, radius, radius)) {
                if (e instanceof LivingEntity le && e != player) {
                    double d = le.getLocation().distanceSquared(landing);
                    if (d < nearestDist) {
                        nearestDist = d;
                        nearest = le;
                    }
                }
            }
            if (nearest != null) {
                nearest.damage(damage, player);
                double targetDebuffSeconds = node.param("target-duration-seconds", 0);
                if (targetDebuffSeconds > 0) {
                    PotionEffectType targetType = effect(node, "target-effect", "WEAKNESS");
                    nearest.addPotionEffect(new PotionEffect(targetType,
                            (int) (targetDebuffSeconds * 20), (int) node.param("target-amplifier", 0)));
                }
            }
        }

        data.lifetimeAbilityProcs++;
    }

    // ---------------------------------------------------------------- TAKE DAMAGE / KILL

    /** ON_TAKE_DAMAGE_SELF_POTION_PROC — taking any damage has a chance to
     *  grant a defensive buff (unconditional, unlike the low-health version
     *  which only fires below a health threshold). */
    public void onTakeDamage(Player player, PlayerClassData data) {
        SkillNode node = bestNodeFor(data, "ON_TAKE_DAMAGE_SELF_POTION_PROC");
        if (node == null || !rollAndCooldown(data, node)) return;

        PotionEffectType type = effect(node, "effect", "RESISTANCE");
        int amplifier = (int) node.param("amplifier", 0);
        int durationTicks = (int) (node.param("duration-seconds", 4) * 20);
        player.addPotionEffect(new PotionEffect(type, durationTicks, amplifier));
        data.lifetimeAbilityProcs++;
    }

    /** ON_KILL_SELF_POTION_PROC — a killing blow (PvP or PvE) has a chance to
     *  grant a self buff. Supports a second optional effect via
     *  "effect2"/"amplifier2"/"duration2-seconds" so a kill can grant two
     *  buffs at once (e.g. Strength + Speed) without needing a new behavior. */
    public void onKill(Player player, PlayerClassData data) {
        SkillNode node = bestNodeFor(data, "ON_KILL_SELF_POTION_PROC");
        if (node == null || !rollAndCooldown(data, node)) return;

        PotionEffectType type = effect(node, "effect", "STRENGTH");
        int amplifier = (int) node.param("amplifier", 0);
        int durationTicks = (int) (node.param("duration-seconds", 5) * 20);
        player.addPotionEffect(new PotionEffect(type, durationTicks, amplifier));

        double duration2 = node.param("duration2-seconds", 0);
        if (duration2 > 0) {
            PotionEffectType type2 = effect(node, "effect2", "SPEED");
            player.addPotionEffect(new PotionEffect(type2, (int) (duration2 * 20), (int) node.param("amplifier2", 0)));
        }

        data.lifetimeAbilityProcs++;
    }

    // ---------------------------------------------------------------- MAGE SPELLS
    // Fireball and Lightning are always-available base Mage kit (not gated by
    // the tree at all — same as Warrior can always swing a sword). They're
    // real vanilla mechanics: a genuine Fireball entity (the same one Ghasts/
    // Blazes use) and a genuine lightning strike (world.strikeLightning, the
    // same one a Trident with Channeling or a natural storm produces) — just
    // triggered by a player action instead of a mob or the weather. Iceball
    // enhances the vanilla Snowball throw every player already has for free.
    // Tree nodes (FIREBALL_UPGRADE / LIGHTNING_UPGRADE ability keys) make
    // these cheaper and stronger; they never gate whether you can cast at all.

    public enum CastResult {SUCCESS, NOT_A_MAGE, NOT_ENOUGH_MANA, ON_COOLDOWN, NO_ARROWS}

    private double upgradeBonus(PlayerClassData data, String abilityKey, String paramKey) {
        SkillNode node = bestNodeFor(data, abilityKey);
        return node == null ? 0 : node.param(paramKey, 0);
    }

    public CastResult castFireball(Player player, PlayerClassData data) {
        long now = System.currentTimeMillis();
        if (data.fireballReadyAt > now) return CastResult.ON_COOLDOWN;

        double costReduction = upgradeBonus(data, "FIREBALL_UPGRADE", "mana-cost-reduction-percent");
        double manaCost = config.fireballManaCost() * (1 - costReduction / 100.0);
        if (data.currentMana < manaCost) return CastResult.NOT_ENOUGH_MANA;

        data.currentMana -= manaCost;
        data.fireballReadyAt = now + config.fireballCooldownMillis();

        double damageBonus = upgradeBonus(data, "FIREBALL_UPGRADE", "damage-bonus-percent");
        double damage = config.fireballBaseDamage() * (1 + damageBonus / 100.0)
                + statBonusTotal(data, "STAT_FIREBALL_DAMAGE_BOOST", "damage-per-point");

        org.bukkit.entity.Fireball fireball = player.launchProjectile(org.bukkit.entity.LargeFireball.class);
        fireball.setYield(0f); // no terrain destruction — damage/fire only, not a griefing tool
        fireball.setIsIncendiary(true);
        fireball.setDirection(player.getEyeLocation().getDirection());
        fireball.setMetadata("wsmp_classes_damage", new org.bukkit.metadata.FixedMetadataValue(
                (org.bukkit.plugin.Plugin) plugin, damage));

        player.playSound(player.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1f, 1f);
        data.lifetimeAbilityProcs++;
        return CastResult.SUCCESS;
    }

    public CastResult castLightning(Player player, PlayerClassData data) {
        long now = System.currentTimeMillis();
        if (data.lightningReadyAt > now) return CastResult.ON_COOLDOWN;

        double costReduction = upgradeBonus(data, "LIGHTNING_UPGRADE", "mana-cost-reduction-percent");
        double manaCost = config.lightningManaCost() * (1 - costReduction / 100.0);
        if (data.currentMana < manaCost) return CastResult.NOT_ENOUGH_MANA;

        var rayTrace = player.rayTraceBlocks(30);
        Location target = rayTrace != null ? rayTrace.getHitPosition().toLocation(player.getWorld())
                : player.getEyeLocation().add(player.getEyeLocation().getDirection().multiply(10));

        data.currentMana -= manaCost;
        data.lightningReadyAt = now + config.lightningCooldownMillis();

        double damageBonus = upgradeBonus(data, "LIGHTNING_UPGRADE", "damage-bonus-percent");
        double damage = config.lightningBaseDamage() * (1 + damageBonus / 100.0)
                + statBonusTotal(data, "STAT_LIGHTNING_DAMAGE_BOOST", "damage-per-point");
        double radius = 2.0 + upgradeBonus(data, "LIGHTNING_UPGRADE", "extra-radius");

        target.getWorld().strikeLightningEffect(target); // visual + fire, no block damage from the strike itself
        for (Entity e : target.getWorld().getNearbyEntities(target, radius, radius, radius)) {
            if (e instanceof LivingEntity le && !le.equals(player)) {
                le.damage(damage, player);
            }
        }

        data.lifetimeAbilityProcs++;
        return CastResult.SUCCESS;
    }

    /** Called when a Mage's thrown Snowball hits something — cooldown-gated,
     *  no mana cost (throwing a snowball is already a free vanilla action). */
    public void onIceballHit(Player thrower, PlayerClassData data, LivingEntity target) {
        long now = System.currentTimeMillis();
        Long readyAt = data.abilityCooldowns.get("iceball_base");
        if (readyAt != null && readyAt > now) return;

        // Iceball has a mana cost like every other spell — unlike Fireball/
        // Lightning it costs nothing in ammo (snowballs are effectively
        // free/unlimited), so without a mana gate it would become a
        // completely free, sustained damage source once buffed by Frost
        // Affinity, undercutting the whole mana-management design. If mana
        // is too low, the snowball just flies as a normal vanilla snowball
        // — no message spam on every miss, it just quietly does nothing
        // extra until mana regenerates.
        double manaCost = config.iceballManaCost() * (1 - upgradeBonus(data, "ICEBALL_UPGRADE", "mana-cost-reduction-percent") / 100.0);
        if (data.currentMana < manaCost) return;
        data.currentMana -= manaCost;

        data.abilityCooldowns.put("iceball_base", now + config.iceballCooldownMillis());

        double damageBonus = upgradeBonus(data, "ICEBALL_UPGRADE", "damage-bonus-percent");
        double iceballDamage = config.iceballBonusDamage() * (1 + damageBonus / 100.0)
                + statBonusTotal(data, "STAT_ICEBALL_DAMAGE_BOOST", "damage-per-point");
        target.damage(iceballDamage, thrower);
        double extraSlowSeconds = upgradeBonus(data, "ICEBALL_UPGRADE", "extra-slowness-seconds");
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,
                (int) ((config.iceballSlownessSeconds() + extraSlowSeconds) * 20), 1));
        data.lifetimeAbilityProcs++;
    }

    /** Right-click any sword to unleash a real AOE hit against everything
     *  nearby — always available to any Warrior from level 1, exactly the
     *  same "base kit the tree only upgrades" pattern as Mage's spells.
     *  Real damage dealt via LivingEntity#damage, no invented mechanic. */
    public CastResult castWhirlwind(Player player, PlayerClassData data) {
        long now = System.currentTimeMillis();
        if (data.whirlwindReadyAt > now) return CastResult.ON_COOLDOWN;

        double costReduction = upgradeBonus(data, "WHIRLWIND_UPGRADE", "mana-cost-reduction-percent");
        double manaCost = config.whirlwindManaCost() * (1 - costReduction / 100.0);
        if (data.currentMana < manaCost) return CastResult.NOT_ENOUGH_MANA;

        data.currentMana -= manaCost;
        data.whirlwindReadyAt = now + config.whirlwindCooldownMillis();

        double damageBonus = upgradeBonus(data, "WHIRLWIND_UPGRADE", "damage-bonus-percent");
        double damage = config.whirlwindBaseDamage() * (1 + damageBonus / 100.0)
                + statBonusTotal(data, "STAT_WHIRLWIND_DAMAGE_BOOST", "damage-per-point");
        double radius = config.whirlwindRadius() + upgradeBonus(data, "WHIRLWIND_UPGRADE", "extra-radius");

        Location center = player.getLocation();
        for (Entity e : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (e instanceof LivingEntity le && !le.equals(player)) {
                le.damage(damage, player);
                Vector kb = le.getLocation().toVector().subtract(center.toVector())
                        .normalize().multiply(0.8).setY(0.3);
                le.setVelocity(le.getVelocity().add(kb));
            }
        }

        player.getWorld().spawnParticle(org.bukkit.Particle.SWEEP_ATTACK, center.add(0, 1, 0), 8, radius / 2, 0.2, radius / 2);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 0.8f);
        data.lifetimeAbilityProcs++;
        return CastResult.SUCCESS;
    }

    /** Sneak + left-click while holding a bow (arm swing, not a raw
     *  right-click draw) to fire a real volley — one arrow of each vanilla
     *  arrow type at once (Arrow, Spectral Arrow, and two Tipped Arrows
     *  with different real potion effects), consuming one arrow of any
     *  type from the player's inventory as its cost. Always available to
     *  any Archer from level 1, upgraded by the tree the same way
     *  Fireball/Whirlwind are. */
    public CastResult castPowerShot(Player player, PlayerClassData data) {
        long now = System.currentTimeMillis();
        if (data.powerShotReadyAt > now) return CastResult.ON_COOLDOWN;

        double costReduction = upgradeBonus(data, "POWERSHOT_UPGRADE", "mana-cost-reduction-percent");
        double manaCost = config.powerShotManaCost() * (1 - costReduction / 100.0);
        if (data.currentMana < manaCost) return CastResult.NOT_ENOUGH_MANA;

        if (!consumeOneArrow(player)) return CastResult.NO_ARROWS;

        data.currentMana -= manaCost;
        double cooldownReductionPercent = statBonusTotal(data, "STAT_POWERSHOT_COOLDOWN_REDUCTION", "percent-per-point");
        long adjustedCooldown = (long) (config.powerShotCooldownMillis() * (1 - Math.min(cooldownReductionPercent, 80) / 100.0));
        data.powerShotReadyAt = now + adjustedCooldown;

        double damageBonus = upgradeBonus(data, "POWERSHOT_UPGRADE", "damage-bonus-percent");
        double damage = config.powerShotBaseDamage() * (1 + damageBonus / 100.0)
                + statBonusTotal(data, "STAT_POWERSHOT_DAMAGE_BOOST", "damage-per-point");

        Vector direction = player.getEyeLocation().getDirection();
        Location eye = player.getEyeLocation();
        World world = player.getWorld();

        // One real Arrow, one real Spectral Arrow, two real Tipped Arrows
        // with different vanilla potion effects — "every arrow type" as a
        // single volley, each dealing the same tree-scaled damage.
        Arrow plain = player.launchProjectile(Arrow.class, direction.multiply(3));
        plain.setDamage(damage);
        plain.setCritical(true);

        org.bukkit.entity.SpectralArrow spectral = world.spawn(eye, org.bukkit.entity.SpectralArrow.class, a -> {
            a.setShooter(player);
            a.setVelocity(rotateAroundY(direction.clone(), Math.toRadians(6)).multiply(3));
            a.setDamage(damage);
        });

        org.bukkit.entity.TippedArrow slow = world.spawn(eye, org.bukkit.entity.TippedArrow.class, a -> {
            a.setShooter(player);
            a.setVelocity(rotateAroundY(direction.clone(), Math.toRadians(-6)).multiply(3));
            a.setDamage(damage);
            a.setBasePotionType(org.bukkit.potion.PotionType.SLOWNESS);
        });

        org.bukkit.entity.TippedArrow weak = world.spawn(eye, org.bukkit.entity.TippedArrow.class, a -> {
            a.setShooter(player);
            a.setVelocity(rotateAroundY(direction.clone(), Math.toRadians(12)).multiply(3));
            a.setDamage(damage);
            a.setBasePotionType(org.bukkit.potion.PotionType.WEAKNESS);
        });

        player.playSound(player.getLocation(), Sound.ENTITY_ARROW_SHOOT, 1f, 0.6f);
        data.lifetimeAbilityProcs++;
        return CastResult.SUCCESS;
    }

    /** Removes exactly one arrow of any vanilla arrow type (plain, spectral,
     *  or tipped) from the player's inventory, preferring plain arrows
     *  first so the more special ones (spectral/tipped from loot or the
     *  shop) are consumed last. Returns false if the player has none. */
    private boolean consumeOneArrow(Player player) {
        var inv = player.getInventory();
        for (org.bukkit.Material type : new org.bukkit.Material[]{
                org.bukkit.Material.ARROW, org.bukkit.Material.TIPPED_ARROW, org.bukkit.Material.SPECTRAL_ARROW}) {
            for (int i = 0; i < inv.getSize(); i++) {
                var stack = inv.getItem(i);
                if (stack != null && stack.getType() == type) {
                    stack.setAmount(stack.getAmount() - 1);
                    if (stack.getAmount() <= 0) inv.setItem(i, null);
                    return true;
                }
            }
        }
        return false;
    }

    /** Ticks mana regen for one player — called on a repeating scheduler for
     *  every class (Mage: Fireball/Lightning, Warrior: Whirlwind Strike,
     *  Archer: Power Shot). Display (the boss bar showing mana + cooldowns)
     *  is handled separately by ManaBarManager. */
    public void tickManaRegen(Player player, PlayerClassData data) {
        double max = config.manaMax();
        if (data.currentMana < max) {
            data.currentMana = Math.min(max, data.currentMana + config.manaRegenAmount());
        }
    }
}
