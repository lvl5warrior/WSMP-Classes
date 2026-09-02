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

import java.util.Map;
import java.util.Random;

public final class AbilityService {

    private final ClassConfig config;
    private final org.bukkit.plugin.Plugin plugin;
    private final com.warriorssmp.classes.data.DataStore dataStore;
    private final Random random = new Random();
    private final org.bukkit.NamespacedKey powerShotEffectKey;
    private final Map<Location, TrapInfo> activeTraps = new java.util.HashMap<>();

    private record TrapInfo(java.util.UUID owner, double damage, long expiresAt) {}

    public AbilityService(ClassConfig config, org.bukkit.plugin.Plugin plugin, com.warriorssmp.classes.data.DataStore dataStore) {
        this.config = config;
        this.plugin = plugin;
        this.dataStore = dataStore;
        this.powerShotEffectKey = new org.bukkit.NamespacedKey(plugin, "powershot_effect");
        startTrapWatcher();
        startLavaWalkWatcher();
    }

    /** Ice path: Water Breathing, refreshed on the slower mana-regen tick
     *  (a 200-tick/10s potion duration comfortably covers the ~2s gap
     *  between refreshes). Fire and Lightning paths have no ALWAYS-ON tick
     *  effect — Fire's lava safety needs a much faster correction loop (see
     *  startLavaWalkWatcher below) since lava pulls a player down far
     *  faster than a 2-second gap could ever catch, and Lightning's Chorus
     *  Fruit teleport is player-activated, not passive. */
    public void tickPathEffects(Player player, PlayerClassData data) {
        if ("ice".equals(data.chosenPath)) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, 200, 0, false, false));
        } else if ("marksman".equals(data.chosenPath)) {
            // Marksman's unique path perk: Glowing on nearby hostiles, so
            // they're visible through walls and foliage — a real Archer
            // never loses track of their target.
            for (Entity e : player.getNearbyEntities(24, 24, 24)) {
                if (e instanceof org.bukkit.entity.Monster monster) {
                    monster.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 60, 0, false, false));
                }
            }
        }
    }

    /** Lightning path's unique ability: a free, no-item-cost short-range
     *  random teleport (Chorus Fruit style) plus a brief Jump Boost
     *  afterward for the "double jump" mobility feel that was asked for —
     *  true mid-air double-jump detection needs packet-level tricks this
     *  plugin doesn't have the tooling for, so this captures the same
     *  "sudden burst of mobility" feeling through the landing buff instead.
     *  Triggered by swapping hands (F key) rather than any click, since
     *  every click-based input on a Blaze Rod is already spoken for. Tries
     *  several random nearby points and only teleports to one that's
     *  actually safe to stand in — never into a wall, lava, or the void. */
    public boolean tryLightningBlink(Player player, PlayerClassData data) {
        long now = System.currentTimeMillis();
        Long readyAt = data.abilityCooldowns.get("lightning_blink");
        if (readyAt != null && readyAt > now) return false;

        Location origin = player.getLocation();
        Location safeSpot = null;
        for (int attempt = 0; attempt < 12; attempt++) {
            double dx = (random.nextDouble() * 2 - 1) * 8;
            double dz = (random.nextDouble() * 2 - 1) * 8;
            Location candidate = origin.clone().add(dx, 0, dz);
            int groundY = origin.getWorld().getHighestBlockYAt(candidate.getBlockX(), candidate.getBlockZ());
            candidate.setY(groundY + 1);
            var below = candidate.clone().subtract(0, 1, 0).getBlock();
            var at = candidate.getBlock();
            var above = candidate.clone().add(0, 1, 0).getBlock();
            if (below.getType().isSolid() && !at.getType().isSolid() && !above.getType().isSolid()
                    && below.getType() != org.bukkit.Material.LAVA) {
                safeSpot = candidate;
                break;
            }
        }
        if (safeSpot == null) return false;

        data.abilityCooldowns.put("lightning_blink", now + config.lightningBlinkCooldownMillis());
        player.getWorld().spawnParticle(org.bukkit.Particle.PORTAL, origin, 40, 0.3, 0.5, 0.3, 0.3);
        player.teleport(safeSpot);
        player.getWorld().spawnParticle(org.bukkit.Particle.PORTAL, safeSpot, 40, 0.3, 0.5, 0.3, 0.3);
        player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 60, 2, false, false));
        player.playSound(safeSpot, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.3f);
        return true;
    }

    /** Warlord's unique path perk: "Rallying Cry" — free, no-mana-cost
     *  Strength + Speed burst for the caster and any nearby allies (any
     *  other player within range, PvP-agnostic on purpose — a Warlord
     *  rallying the battlefield doesn't check faction). Same swap-hands
     *  trigger as Lightning's blink; the two never collide since a player
     *  is only ever one class+path at a time. */
    public boolean tryRallyingCry(Player player, PlayerClassData data) {
        long now = System.currentTimeMillis();
        Long readyAt = data.abilityCooldowns.get("rallying_cry");
        if (readyAt != null && readyAt > now) return false;

        data.abilityCooldowns.put("rallying_cry", now + config.rallyingCryCooldownMillis());
        for (Entity e : player.getNearbyEntities(10, 5, 10)) {
            if (e instanceof Player ally) {
                ally.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 100, 0, false, true));
                ally.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 1, false, true));
            }
        }
        player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 100, 0, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 1, false, true));
        player.getWorld().spawnParticle(org.bukkit.Particle.CRIT, player.getLocation().add(0, 1, 0), 30, 1.5, 1, 1.5, 0.1);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 1.6f);
        return true;
    }

    /** Barrage's unique path perk: "Storm of Arrows" — a burst of real
     *  arrows fired at every hostile mob and enemy player nearby at once,
     *  each dealing modest damage. Same swap-hands trigger as the other
     *  two path-perk activated abilities. */
    public boolean tryStormOfArrows(Player player, PlayerClassData data) {
        long now = System.currentTimeMillis();
        Long readyAt = data.abilityCooldowns.get("storm_of_arrows");
        if (readyAt != null && readyAt > now) return false;

        data.abilityCooldowns.put("storm_of_arrows", now + config.stormOfArrowsCooldownMillis());
        double damage = config.stormOfArrowsDamage();
        World world = player.getWorld();
        for (Entity e : player.getNearbyEntities(10, 6, 10)) {
            if (e.equals(player)) continue;
            if (!(e instanceof LivingEntity target)) continue;
            if (target instanceof Player && target.equals(player)) continue;

            Location from = player.getEyeLocation();
            Vector toward = target.getLocation().add(0, 1, 0).toVector().subtract(from.toVector()).normalize();
            Arrow arrow = world.spawn(from, Arrow.class, a -> {
                a.setShooter(player);
                a.setVelocity(toward.multiply(2.5));
                a.setDamage(damage);
            });
        }
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ARROW_SHOOT, 1f, 1.5f);
        return true;
    }

    /** Fire path: "walking on lava" — Minecraft has no vanilla equivalent
     *  to Frost Walker for lava, so this is a from-scratch correction loop:
     *  every 2 ticks, any online Fire-path player standing in or on lava
     *  gets Fire Resistance (so it can never hurt them) and has any
     *  downward velocity cancelled outright, holding them at the surface
     *  instead of letting them sink. A single shared repeating task checks
     *  all online players rather than spawning one task per player. */
    private void startLavaWalkWatcher() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                var data = dataStore.get(player.getUniqueId());
                if (!"fire".equals(data.chosenPath)) continue;

                var feet = player.getLocation().getBlock();
                var belowFeet = player.getLocation().clone().subtract(0, 0.3, 0).getBlock();
                boolean inLava = feet.getType() == org.bukkit.Material.LAVA
                        || belowFeet.getType() == org.bukkit.Material.LAVA;
                if (!inLava) continue;

                player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 60, 0, false, false));
                var velocity = player.getVelocity();
                if (velocity.getY() < 0) {
                    player.setVelocity(velocity.setY(0.05));
                }
                player.setFallDistance(0);
            }
        }, 20L, 2L);
    }

    /** Archer's second active ability: right-click a Tripwire Hook on the
     *  ground to place a trap there — a different input entirely from
     *  Power Shot's shift+left-click, since sneak+right-click on a bow was
     *  already ruled out (always starts a real vanilla draw) and this uses
     *  a completely different item, so there's no overlap to worry about.
     *  A repeating watcher (started once in the constructor, not per-trap)
     *  checks all active traps for anything standing on them; expired,
     *  untouched traps just quietly disappear rather than lingering
     *  forever as world clutter. */
    public boolean placeTrap(Player player, PlayerClassData data, Location blockLocation) {
        long now = System.currentTimeMillis();
        Long readyAt = data.abilityCooldowns.get("trap_place");
        if (readyAt != null && readyAt > now) return false;

        double damageBonus = upgradeBonus(data, "TRAP_UPGRADE", "damage-bonus-percent");
        double damage = config.trapBaseDamage() * (1 + damageBonus / 100.0)
                + statBonusTotal(data, "STAT_TRAP_DAMAGE_BOOST", "damage-per-point");

        double trapCdReduction = Math.min(statBonusTotal(data, "STAT_TRAP_COOLDOWN_REDUCTION", "percent-per-point"), 80);
        data.abilityCooldowns.put("trap_place", now + (long) (config.trapCooldownMillis() * (1 - trapCdReduction / 100.0)));
        Location trapLoc = blockLocation.getBlock().getLocation();
        activeTraps.put(trapLoc, new TrapInfo(player.getUniqueId(), damage, now + config.trapLifetimeMillis()));
        player.getWorld().spawnParticle(org.bukkit.Particle.CRIT, trapLoc.clone().add(0.5, 0.5, 0.5), 12, 0.3, 0.2, 0.3, 0.05);
        player.playSound(player.getLocation(), Sound.BLOCK_TRIPWIRE_CLICK_ON, 1f, 1.2f);
        data.lifetimeAbilityProcs++;
        return true;
    }

    private void startTrapWatcher() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (activeTraps.isEmpty()) return;
            long now = System.currentTimeMillis();
            var iterator = activeTraps.entrySet().iterator();
            while (iterator.hasNext()) {
                var entry = iterator.next();
                Location trapLoc = entry.getKey();
                TrapInfo info = entry.getValue();
                if (now > info.expiresAt()) {
                    iterator.remove();
                    continue;
                }
                for (Entity e : trapLoc.getWorld().getNearbyEntities(trapLoc.clone().add(0.5, 0.5, 0.5), 0.6, 0.6, 0.6)) {
                    if (e instanceof LivingEntity le && !e.getUniqueId().equals(info.owner())) {
                        var owner = plugin.getServer().getPlayer(info.owner());
                        le.damage(info.damage(), owner);
                        le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 3));
                        trapLoc.getWorld().spawnParticle(org.bukkit.Particle.SWEEP_ATTACK, trapLoc.clone().add(0.5, 0.5, 0.5), 3);
                        trapLoc.getWorld().playSound(trapLoc, Sound.BLOCK_TRIPWIRE_DETACH, 1f, 0.8f);
                        iterator.remove();
                        break;
                    }
                }
            }
        }, 10L, 5L);
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
        applyGuardianKnockbackImmunity(player, data);
    }

    /** Guardian's unique path perk: total knockback immunity, via the real
     *  vanilla knockback-resistance attribute (a fixed 100%, not scaled by
     *  tree investment — this is granted just for picking Guardian, the
     *  same way Berserker/Skirmisher/etc.'s perks are). Uses the exact
     *  same find-by-key-then-remove-then-reapply pattern as applyAttribute
     *  above, so switching away from Guardian via respec correctly clears it. */
    private void applyGuardianKnockbackImmunity(Player player, PlayerClassData data) {
        var instance = player.getAttribute(org.bukkit.attribute.Attribute.GENERIC_KNOCKBACK_RESISTANCE);
        if (instance == null) return;
        java.util.UUID modifierId = java.util.UUID.nameUUIDFromBytes("wsmp_classes_guardian_kb".getBytes());
        instance.getModifiers().stream()
                .filter(m -> m.getUniqueId().equals(modifierId))
                .findFirst()
                .ifPresent(instance::removeModifier);
        if ("guardian".equals(data.chosenPath)) {
            var modifier = new org.bukkit.attribute.AttributeModifier(modifierId, "wsmp_classes_guardian_kb", 1.0,
                    org.bukkit.attribute.AttributeModifier.Operation.ADD_NUMBER);
            instance.addModifier(modifier);
        }
    }

    /** Flat percent damage reduction, scaled by investment like every other
     *  stat buff — applied directly to the incoming EntityDamageEvent
     *  rather than a vanilla attribute, since there's no single vanilla
     *  attribute for "take X% less damage from everything." Capped at 75%
     *  so a player can never become fully immune to damage. */
    public void applyDamageResistance(Player player, PlayerClassData data, org.bukkit.event.entity.EntityDamageEvent event) {
        double percentPerPoint = statBonusTotal(data, "STAT_DAMAGE_REDUCTION_BOOST", "percent-per-point");
        if (percentPerPoint <= 0) return;
        double reduction = Math.min(percentPerPoint, 75) / 100.0;
        event.setDamage(event.getDamage() * (1 - reduction));
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
        double fireballCdReduction = Math.min(statBonusTotal(data, "STAT_FIREBALL_COOLDOWN_REDUCTION", "percent-per-point"), 80);
        data.fireballReadyAt = now + (long) (config.fireballCooldownMillis() * (1 - fireballCdReduction / 100.0));

        double damageBonus = upgradeBonus(data, "FIREBALL_UPGRADE", "damage-bonus-percent");
        double damage = config.fireballBaseDamage() * (1 + damageBonus / 100.0)
                + statBonusTotal(data, "STAT_FIREBALL_DAMAGE_BOOST", "damage-per-point");

        org.bukkit.entity.Fireball fireball = player.launchProjectile(org.bukkit.entity.LargeFireball.class);
        fireball.setYield(0f); // no terrain destruction — damage/fire only, not a griefing tool
        fireball.setIsIncendiary(false); // real fire replaced by a controlled zone in onFireballImpact — a real vanilla fire can spread and burn down a forest with no way to contain it
        fireball.setDirection(player.getEyeLocation().getDirection());
        fireball.setMetadata("wsmp_classes_damage", new org.bukkit.metadata.FixedMetadataValue(
                (org.bukkit.plugin.Plugin) plugin, damage));

        player.playSound(player.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1f, 1f);
        data.lifetimeAbilityProcs++;
        return CastResult.SUCCESS;
    }

    /** Fireball's AOE splash plus a temporary "burning ground" zone at the
     *  impact point — real damage to anything standing in it, but no real
     *  fire blocks at all, so there's nothing that can spread and burn down
     *  a forest. The zone damages repeatedly for a few seconds then simply
     *  stops existing; FLAME particles make it look like real fire without
     *  it being one. `directHit` (whoever the fireball hit directly, if
     *  anyone) is excluded from the splash — they already took full damage
     *  from the normal hit, splashing them too would double it. */
    public void onFireballImpact(Player caster, Location impact, double damage, Entity directHit) {
        World world = impact.getWorld();
        double splashRadius = config.fireballAoeRadius();
        for (Entity e : world.getNearbyEntities(impact, splashRadius, splashRadius, splashRadius)) {
            if (e.equals(caster) || e.equals(directHit)) continue;
            if (e instanceof LivingEntity le) {
                le.damage(damage, caster);
            }
        }

        double zoneDamage = damage * 0.3;
        int totalTicks = (int) (config.fireballGroundFireSeconds() * 20);
        new org.bukkit.scheduler.BukkitRunnable() {
            int elapsed = 0;

            @Override
            public void run() {
                if (elapsed >= totalTicks) {
                    cancel();
                    return;
                }
                world.spawnParticle(org.bukkit.Particle.FLAME, impact, 20, 1.3, 0.15, 1.3, 0.02);
                for (Entity e : world.getNearbyEntities(impact, 2.0, 1.5, 2.0)) {
                    if (e.equals(caster)) continue;
                    if (e instanceof LivingEntity le) {
                        le.damage(zoneDamage, caster);
                        le.setFireTicks(20);
                    }
                }
                elapsed += 20;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    public CastResult castLightning(Player player, PlayerClassData data) {
        long now = System.currentTimeMillis();
        if (data.lightningReadyAt > now) return CastResult.ON_COOLDOWN;

        double costReduction = upgradeBonus(data, "LIGHTNING_UPGRADE", "mana-cost-reduction-percent");
        double manaCost = config.lightningManaCost() * (1 - costReduction / 100.0);
        if (data.currentMana < manaCost) return CastResult.NOT_ENOUGH_MANA;

        var rayTrace = player.rayTraceBlocks(30);
        Location firstStrike = rayTrace != null ? rayTrace.getHitPosition().toLocation(player.getWorld())
                : player.getEyeLocation().add(player.getEyeLocation().getDirection().multiply(10));

        data.currentMana -= manaCost;
        double lightningCdReduction = Math.min(statBonusTotal(data, "STAT_LIGHTNING_COOLDOWN_REDUCTION", "percent-per-point"), 80);
        data.lightningReadyAt = now + (long) (config.lightningCooldownMillis() * (1 - lightningCdReduction / 100.0));

        double damageBonus = upgradeBonus(data, "LIGHTNING_UPGRADE", "damage-bonus-percent");
        double damage = config.lightningBaseDamage() * (1 + damageBonus / 100.0)
                + statBonusTotal(data, "STAT_LIGHTNING_DAMAGE_BOOST", "damage-per-point");
        double radius = 2.0 + upgradeBonus(data, "LIGHTNING_UPGRADE", "extra-radius");

        // 3 strikes in a straight line running away from the player, not
        // scattered randomly — the horizontal component of where they're
        // looking, snapped to the actual ground height at each point
        // (a flat offset alone would leave later strikes floating above or
        // buried under sloped terrain).
        World world = player.getWorld();
        Vector forward = firstStrike.toVector().subtract(player.getLocation().toVector());
        forward.setY(0);
        if (forward.lengthSquared() < 0.01) forward = player.getLocation().getDirection().setY(0);
        forward.normalize();

        for (int i = 0; i < 3; i++) {
            Vector offset = forward.clone().multiply(i * 4.0);
            Location strikePoint = firstStrike.clone().add(offset);
            int groundY = world.getHighestBlockYAt(strikePoint.getBlockX(), strikePoint.getBlockZ());
            strikePoint.setY(groundY + 1);

            int delayTicks = i * 4;
            Location finalStrikePoint = strikePoint;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                world.strikeLightningEffect(finalStrikePoint); // visual + fire, no block damage from the strike itself
                for (Entity e : world.getNearbyEntities(finalStrikePoint, radius, radius, radius)) {
                    if (e instanceof LivingEntity le && !le.equals(player)) {
                        le.damage(damage, player);
                    }
                }
            }, delayTicks);
        }

        data.lifetimeAbilityProcs++;
        return CastResult.SUCCESS;
    }

    /** Called when a Mage's thrown Snowball hits something — cooldown-gated,
     *  no mana cost (throwing a snowball is already a free vanilla action). */
    /** Shift + left-click a Blaze Rod (arm swing, same trigger style as
     *  Archer's Power Shot) to fire an instant ice bolt at whatever the
     *  player is looking at — no thrown Snowball needed anymore. Uses a
     *  ray-trace the same way Lightning finds its target, rather than a
     *  real thrown projectile, since the effect is instant either way and
     *  this sidesteps needing a travel-time hit-detection path entirely. */
    public CastResult castIceball(Player player, PlayerClassData data) {
        long now = System.currentTimeMillis();
        if (data.iceballReadyAt > now) return CastResult.ON_COOLDOWN;

        double costReduction = upgradeBonus(data, "ICEBALL_UPGRADE", "mana-cost-reduction-percent");
        double manaCost = config.iceballManaCost() * (1 - costReduction / 100.0);
        if (data.currentMana < manaCost) return CastResult.NOT_ENOUGH_MANA;

        data.currentMana -= manaCost;
        double iceballCdReduction = Math.min(statBonusTotal(data, "STAT_ICEBALL_COOLDOWN_REDUCTION", "percent-per-point"), 80);
        data.iceballReadyAt = now + (long) (config.iceballCooldownMillis() * (1 - iceballCdReduction / 100.0));

        double damageBonus = upgradeBonus(data, "ICEBALL_UPGRADE", "damage-bonus-percent");
        double damage = config.iceballBonusDamage() * (1 + damageBonus / 100.0)
                + statBonusTotal(data, "STAT_ICEBALL_DAMAGE_BOOST", "damage-per-point");
        double extraSlowSeconds = upgradeBonus(data, "ICEBALL_UPGRADE", "extra-slowness-seconds");
        int slowTicks = (int) ((config.iceballSlownessSeconds() + extraSlowSeconds) * 20);

        Entity targetEntity = player.getTargetEntity(20);
        if (targetEntity instanceof LivingEntity target && !target.equals(player)) {
            target.damage(damage, player);
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, slowTicks, 1));
        }

        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection();
        for (int i = 1; i <= 15; i++) {
            player.getWorld().spawnParticle(org.bukkit.Particle.SNOWFLAKE,
                    eye.clone().add(dir.clone().multiply(i)), 3, 0.1, 0.1, 0.1, 0.01);
        }
        player.playSound(player.getLocation(), Sound.BLOCK_GLASS_BREAK, 1f, 1.4f);
        data.lifetimeAbilityProcs++;
        return CastResult.SUCCESS;
    }

    /** Right-click any sword to unleash a real AOE hit against everything
     *  nearby — always available to any Warrior from level 1, exactly the
     *  same "base kit the tree only upgrades" pattern as Mage's spells.
     *  Real damage dealt via LivingEntity#damage, no invented mechanic. */
    /** Shift + left-click any sword (same arm-swing trigger as Mage's
     *  Iceball / Archer's Power Shot) to dash forward, damaging and
     *  knocking back anything in the way exactly once each, then granting
     *  a brief Speed burst. Warrior's second active spell. */
    public CastResult castCharge(Player player, PlayerClassData data) {
        long now = System.currentTimeMillis();
        if (data.chargeReadyAt > now) return CastResult.ON_COOLDOWN;

        double costReduction = upgradeBonus(data, "CHARGE_UPGRADE", "mana-cost-reduction-percent");
        double manaCost = config.chargeManaCost() * (1 - costReduction / 100.0);
        if (data.currentMana < manaCost) return CastResult.NOT_ENOUGH_MANA;

        data.currentMana -= manaCost;
        double chargeCdReduction = Math.min(statBonusTotal(data, "STAT_CHARGE_COOLDOWN_REDUCTION", "percent-per-point"), 80);
        data.chargeReadyAt = now + (long) (config.chargeCooldownMillis() * (1 - chargeCdReduction / 100.0));

        double damageBonus = upgradeBonus(data, "CHARGE_UPGRADE", "damage-bonus-percent");
        double damage = config.chargeBaseDamage() * (1 + damageBonus / 100.0)
                + statBonusTotal(data, "STAT_CHARGE_DAMAGE_BOOST", "damage-per-point");

        Vector direction = player.getLocation().getDirection().clone();
        direction.setY(Math.max(direction.getY(), 0.15));
        player.setVelocity(direction.normalize().multiply(2.2));

        World world = player.getWorld();
        java.util.Set<java.util.UUID> alreadyHit = new java.util.HashSet<>();
        new org.bukkit.scheduler.BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= 8) {
                    cancel();
                    return;
                }
                for (Entity e : world.getNearbyEntities(player.getLocation(), 1.5, 1.5, 1.5)) {
                    if (e.equals(player) || alreadyHit.contains(e.getUniqueId())) continue;
                    if (e instanceof LivingEntity le) {
                        alreadyHit.add(e.getUniqueId());
                        le.damage(damage, player);
                        Vector kb = le.getLocation().toVector().subtract(player.getLocation().toVector())
                                .normalize().setY(0.3);
                        le.setVelocity(le.getVelocity().add(kb));
                    }
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 2L);

        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60, 1));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 0.8f);
        data.lifetimeAbilityProcs++;
        return CastResult.SUCCESS;
    }

    public CastResult castWhirlwind(Player player, PlayerClassData data) {
        long now = System.currentTimeMillis();
        if (data.whirlwindReadyAt > now) return CastResult.ON_COOLDOWN;

        double costReduction = upgradeBonus(data, "WHIRLWIND_UPGRADE", "mana-cost-reduction-percent");
        double manaCost = config.whirlwindManaCost() * (1 - costReduction / 100.0);
        if (data.currentMana < manaCost) return CastResult.NOT_ENOUGH_MANA;

        data.currentMana -= manaCost;
        double whirlwindCdReduction = Math.min(statBonusTotal(data, "STAT_WHIRLWIND_COOLDOWN_REDUCTION", "percent-per-point"), 80);
        data.whirlwindReadyAt = now + (long) (config.whirlwindCooldownMillis() * (1 - whirlwindCdReduction / 100.0));

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

        double costReduction = upgradeBonus(data, "POWERSHOT_UPGRADE", "mana-cost-reduction-percent")
                + Math.min(statBonusTotal(data, "STAT_POWERSHOT_MANA_COST_REDUCTION", "percent-per-point"), 80);
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
        // Only the plain arrow deals full damage — the other four exist for
        // the debuffs (Slowness/Weakness) and visual flair, not raw burst.
        // All 5 dealing full damage against a large hitbox (a Ravager, a
        // player) let them all connect at once, turning one Power Shot into
        // 5x its intended hit — this is the actual fix for that.
        double supportDamage = damage * 0.3;

        Vector direction = player.getEyeLocation().getDirection();
        Location eye = player.getEyeLocation();
        World world = player.getWorld();

        // One real Arrow, one real Spectral Arrow, and two more real Arrows
        // tagged to apply a potion effect on hit — "every arrow type" as a
        // single volley. Slowness and Weakness are applied via a
        // PersistentDataContainer tag read back in CombatListener's damage
        // handler (the same pattern already used for bow bonus-damage
        // procs), not by spawning a TippedArrow directly: on current Paper,
        // TippedArrow can't actually be spawned as its own entity type
        // anymore — the server hands back a plain Arrow internally and
        // casting it to TippedArrow throws a ClassCastException at spawn time.
        Arrow plain = player.launchProjectile(Arrow.class, direction.multiply(3));
        plain.setDamage(damage);
        plain.setCritical(true);

        org.bukkit.entity.SpectralArrow spectral = world.spawn(eye, org.bukkit.entity.SpectralArrow.class, a -> {
            a.setShooter(player);
            a.setVelocity(rotateAroundY(direction.clone(), Math.toRadians(6)).multiply(3));
            a.setDamage(supportDamage);
        });

        Arrow slow = world.spawn(eye, Arrow.class, a -> {
            a.setShooter(player);
            a.setVelocity(rotateAroundY(direction.clone(), Math.toRadians(-6)).multiply(3));
            a.setDamage(supportDamage);
            a.getPersistentDataContainer().set(powerShotEffectKey, org.bukkit.persistence.PersistentDataType.STRING, "SLOWNESS");
        });

        Arrow weak = world.spawn(eye, Arrow.class, a -> {
            a.setShooter(player);
            a.setVelocity(rotateAroundY(direction.clone(), Math.toRadians(12)).multiply(3));
            a.setDamage(supportDamage);
            a.getPersistentDataContainer().set(powerShotEffectKey, org.bukkit.persistence.PersistentDataType.STRING, "WEAKNESS");
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
        double max = effectiveManaMax(data);
        if (data.currentMana < max) {
            data.currentMana = Math.min(max, data.currentMana + config.manaRegenAmount());
        }
    }

    /** Base per-class mana cap plus flat bonus from the Mana Well buff,
     *  scaled by investment like every other stat buff. */
    public double effectiveManaMax(PlayerClassData data) {
        return config.manaMaxFor(data.chosenClass) + statBonusTotal(data, "STAT_MANA_MAX_BOOST", "mana-per-point");
    }
}
