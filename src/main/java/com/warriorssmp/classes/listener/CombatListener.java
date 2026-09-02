package com.warriorssmp.classes.listener;

import com.warriorssmp.classes.ClassesPlugin;
import com.warriorssmp.classes.data.PlayerClassData;
import com.warriorssmp.classes.task.AbilityService;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.persistence.PersistentDataType;

public final class CombatListener implements Listener {

    private final ClassesPlugin plugin;
    private final NamespacedKey bonusDamageKey;

    public CombatListener(ClassesPlugin plugin) {
        this.plugin = plugin;
        this.bonusDamageKey = new NamespacedKey(plugin, "bonus_damage");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        var data = plugin.dataStore().get(player.getUniqueId());
        plugin.abilityService().applyPassiveStats(player, data);
        plugin.manaBarManager().show(player, data);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.manaBarManager().hide(event.getPlayer());
        plugin.xpBarManager().hide(event.getPlayer());
        plugin.dataStore().unload(event.getPlayer().getUniqueId());
    }

    /** Right-click a Blaze Rod (Mage) or any sword (Warrior) to cast that
     *  class's active spell — always-available base kit, gated by mana and
     *  a cooldown rather than by tree investment. Archer's Power Shot is
     *  handled separately (see onArcherSwing below) since it needs a
     *  trigger that works reliably while aiming at a target — see that
     *  method's own comment for why.
     *
     *  This fires from TWO different events for the same reason: Bukkit
     *  does not send PlayerInteractEvent at all when the player's crosshair
     *  is on a living entity when they right-click — it sends
     *  PlayerInteractEntityEvent instead, meant for things like feeding
     *  animals. Since aiming at your target is exactly what happens during
     *  real combat, relying on PlayerInteractEvent alone meant every one of
     *  these spells silently failed to cast in the single moment they
     *  actually mattered. Both handlers below call the same dispatch logic. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClassCast(org.bukkit.event.player.PlayerInteractEvent event) {
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        var action = event.getAction();
        if (action != org.bukkit.event.block.Action.RIGHT_CLICK_AIR
                && action != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;

        var clickedBlock = event.getClickedBlock();
        boolean blockedByVanillaInteraction = clickedBlock != null && clickedBlock.getType().isInteractable();

        if (dispatchCast(event.getPlayer(), event.getItem(), blockedByVanillaInteraction)) {
            event.setCancelled(true);
        }
    }

    /** The entity-targeted counterpart to onClassCast above — same trigger
     *  conditions, fired when the player's crosshair was on a living
     *  entity instead of air/a block. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClassCastAtEntity(org.bukkit.event.player.PlayerInteractEntityEvent event) {
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        if (dispatchCast(event.getPlayer(), event.getPlayer().getInventory().getItemInMainHand(), false)) {
            event.setCancelled(true);
        }
    }

    /** Shared Mage/Warrior cast logic. Returns true if a spell was actually
     *  attempted (regardless of success/failure result), so both callers
     *  know whether to cancel the underlying vanilla interaction. */
    private boolean dispatchCast(Player player, org.bukkit.inventory.ItemStack item, boolean blockedByVanillaInteraction) {
        if (item == null) return false;
        PlayerClassData data = plugin.dataStore().get(player.getUniqueId());
        if (data.chosenClass == null) return false;

        var material = item.getType();
        AbilityService.CastResult castResult = switch (data.chosenClass) {
            case MAGE -> {
                if (material != org.bukkit.Material.BLAZE_ROD) yield null;
                yield player.isSneaking()
                        ? plugin.abilityService().castLightning(player, data)
                        : plugin.abilityService().castFireball(player, data);
            }
            case WARRIOR -> {
                // Any right-click (air, block, or entity) triggers it — but
                // skip if the clicked BLOCK has a real vanilla interaction
                // (chest, door, button, furnace, etc.) so Whirlwind doesn't
                // swallow that normal interaction. Entity targets never
                // have this restriction, since there's no vanilla
                // right-click-on-mob interaction for a sword to conflict with.
                if (!material.name().endsWith("_SWORD")) yield null;
                if (blockedByVanillaInteraction) yield null;
                yield plugin.abilityService().castWhirlwind(player, data);
            }
            case ARCHER -> null; // handled entirely by onArcherSwing instead
        };

        if (castResult == null) return false;
        switch (castResult) {
            case NOT_ENOUGH_MANA -> player.sendMessage("§cNot enough mana.");
            case ON_COOLDOWN -> player.sendMessage("§cThat ability is still on cooldown.");
            case NO_ARROWS -> player.sendMessage("§cYou need at least one arrow in your inventory.");
            case NOT_A_MAGE, SUCCESS -> {}
        }
        return true;
    }

    /** Archer's Power Shot trigger: sneak + left-click (arm swing) while
     *  holding a bow. This is a PlayerAnimationEvent, not a
     *  PlayerInteractEvent — deliberately, for two independent reasons:
     *  (1) right-click was ruled out entirely, since right-clicking a bow
     *  always starts a real vanilla draw no matter what, and that draw
     *  cannot be distinguished from "just casting the spell" without
     *  fighting the client's own bow-charging state; (2) left-click
     *  detection via PlayerInteractEvent's LEFT_CLICK_AIR is well known to
     *  be unreliable across clients — Bukkit doesn't consistently fire it.
     *  PlayerAnimationEvent's arm-swing fires for every left-click
     *  regardless of client quirks, AND regardless of whether the
     *  crosshair is on air, a block, or an entity — which also makes this
     *  naturally immune to the interact-event-doesn't-fire-on-entities
     *  problem described above. This is the single most reliable trigger
     *  available for this and is used for exactly that reason. */
    @EventHandler(ignoreCancelled = true)
    public void onArcherSwing(org.bukkit.event.player.PlayerAnimationEvent event) {
        if (event.getAnimationType() != org.bukkit.event.player.PlayerAnimationType.ARM_SWING) return;
        Player player = event.getPlayer();
        if (!player.isSneaking()) return;

        var item = player.getInventory().getItemInMainHand();
        if (item.getType() != org.bukkit.Material.BOW && item.getType() != org.bukkit.Material.CROSSBOW) return;

        PlayerClassData data = plugin.dataStore().get(player.getUniqueId());
        if (data.chosenClass != com.warriorssmp.classes.model.PlayerClass.ARCHER) return;

        var castResult = plugin.abilityService().castPowerShot(player, data);
        switch (castResult) {
            case NOT_ENOUGH_MANA -> player.sendMessage("§cNot enough mana.");
            case ON_COOLDOWN -> player.sendMessage("§cThat ability is still on cooldown.");
            case NO_ARROWS -> player.sendMessage("§cYou need at least one arrow in your inventory.");
            case NOT_A_MAGE, SUCCESS -> {}
        }
    }

    /** Bow shot: rolls the bonus-damage proc (tagged onto the arrow for the
     *  damage listener to read later) and the multishot proc (extra real
     *  arrows spawned immediately). */
    @EventHandler(ignoreCancelled = true)
    public void onShootBow(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player shooter)) return;
        if (!(event.getProjectile() instanceof Arrow arrow)) return;

        PlayerClassData data = plugin.dataStore().get(shooter.getUniqueId());
        if (data.chosenClass == null) return;

        Double bonus = plugin.abilityService().onBowShotBonusDamage(shooter, data);
        if (bonus != null) {
            arrow.getPersistentDataContainer().set(bonusDamageKey, PersistentDataType.DOUBLE, bonus);
        }
        plugin.abilityService().onBowShotMultishot(shooter, data, arrow);
    }

    /** Ender Pearl landing (the moment the throw actually teleports you) —
     *  rolls the self-buff / nearby-damage "teleport strike" proc. */
    @EventHandler(ignoreCancelled = true)
    public void onPearlTeleport(PlayerTeleportEvent event) {
        if (event.getCause() != PlayerTeleportEvent.TeleportCause.ENDER_PEARL) return;
        Player player = event.getPlayer();

        PlayerClassData data = plugin.dataStore().get(player.getUniqueId());
        if (data.chosenClass == null) return;

        plugin.abilityService().onPearlLand(player, data, event.getTo());
    }

    /** Iceball — a Mage's thrown Snowball hitting something. This can't hook
     *  EntityDamageByEntityEvent the way the other procs do: vanilla
     *  Snowballs deal zero damage to almost every entity, so that event
     *  simply never fires for a normal snowball hit. ProjectileHitEvent
     *  fires regardless of vanilla damage, which is what makes this work at
     *  all — the Iceball damage is dealt directly here, not layered on top
     *  of a vanilla hit. */
    @EventHandler(ignoreCancelled = true)
    public void onProjectileHit(org.bukkit.event.entity.ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof org.bukkit.entity.Snowball snowball)) return;
        if (!(snowball.getShooter() instanceof Player thrower)) return;
        if (!(event.getHitEntity() instanceof LivingEntity target) || target.equals(thrower)) return;

        PlayerClassData data = plugin.dataStore().get(thrower.getUniqueId());
        if (data.chosenClass != com.warriorssmp.classes.model.PlayerClass.MAGE) return;

        plugin.abilityService().onIceballHit(thrower, data, target);
    }

    /** Low-health ability check — fires off ANY damage source (mob, fall,
     *  PvP, etc.), not just player-vs-player combat, since "Second Wind"
     *  style abilities should trigger no matter what put you in danger. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAnyDamage(org.bukkit.event.entity.EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;

        PlayerClassData data = plugin.dataStore().get(victim.getUniqueId());
        if (data.chosenClass == null) return;

        var maxHealthAttr = victim.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH);
        double maxHealth = maxHealthAttr != null ? maxHealthAttr.getValue() : 20.0;
        double healthAfter = Math.max(0, victim.getHealth() - event.getFinalDamage());
        double fraction = maxHealth > 0 ? healthAfter / maxHealth : 1.0;

        plugin.abilityService().onLowHealthCheck(victim, data, fraction);
        plugin.abilityService().onTakeDamage(victim, data);
    }

    /** The main combat hook: melee ability procs, arrow bonus damage, and
     *  PvP/PvE XP gain all happen off the same event. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity victim)) return;

        Player attacker = null;
        boolean isMelee = false;

        if (event.getDamager() instanceof Player p) {
            attacker = p;
            isMelee = true;
        } else if (event.getDamager() instanceof Arrow arrow && arrow.getShooter() instanceof Player p) {
            attacker = p;
            Double bonus = arrow.getPersistentDataContainer().get(bonusDamageKey, PersistentDataType.DOUBLE);
            if (bonus != null) {
                event.setDamage(event.getDamage() + bonus);
            }
        } else if (event.getDamager() instanceof org.bukkit.entity.Fireball fireball && fireball.getShooter() instanceof Player p) {
            attacker = p;
            // Replace (not add to) vanilla Fireball damage — the metadata
            // value set on cast is the whole intended damage, computed from
            // the player's tree investment, so adding vanilla's own fireball
            // damage on top would double-count.
            if (fireball.hasMetadata("wsmp_classes_damage")) {
                double damage = fireball.getMetadata("wsmp_classes_damage").get(0).asDouble();
                event.setDamage(damage);
            }
        } else if (event.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player p) {
            attacker = p;
        }

        if (attacker == null || attacker.equals(victim)) return;

        PlayerClassData data = plugin.dataStore().get(attacker.getUniqueId());
        if (data.chosenClass == null) return;

        if (isMelee) {
            plugin.abilityService().onMeleeHit(attacker, data);
        }
        plugin.abilityService().onHitTarget(attacker, data, victim);
        plugin.abilityService().onHitBleed(attacker, data, victim);

        double finalDamage = event.getFinalDamage();
        plugin.abilityService().onHitLifesteal(attacker, data, finalDamage);
        boolean pvp = victim instanceof Player;
        double xpRate = pvp ? plugin.classConfig().pvpXpPerDamage() : plugin.classConfig().pveXpPerDamage();
        if (xpRate > 0) {
            plugin.levelService().grantXp(attacker, data, finalDamage * xpRate);
        }
        if (pvp) {
            data.lifetimePvpDamageDealt += Math.round(finalDamage);
        }
    }

    /** Bonus flat XP for a PvP killing blow, on top of the per-damage XP
     *  already granted by onDamage, plus the on-kill ability proc for any
     *  kill (PvP or PvE). */
    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;

        PlayerClassData data = plugin.dataStore().get(killer.getUniqueId());
        if (data.chosenClass == null) return;

        plugin.abilityService().onKill(killer, data);

        if (event.getEntity() instanceof Player victim) {
            double bonus = plugin.classConfig().pvpKillBonusXp();
            if (bonus > 0) {
                plugin.levelService().grantXp(killer, data, bonus);
                killer.sendMessage("§c§lPVP KILL §7— §f" + victim.getName() + " §7(+" + Math.round(bonus) + " bonus XP)");
            }
        }
    }
}
