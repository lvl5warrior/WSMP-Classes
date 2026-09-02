package com.warriorssmp.classes.listener;

import com.warriorssmp.classes.ClassesPlugin;
import com.warriorssmp.classes.data.PlayerClassData;
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

    /** Right-click a Blaze Rod (Mage), any sword (Warrior), or a Spectral
     *  Arrow (Archer) to cast that class's active spell — all three are
     *  always-available base kit, gated by mana and a cooldown rather than
     *  by tree investment. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClassCast(org.bukkit.event.player.PlayerInteractEvent event) {
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        var action = event.getAction();
        if (action != org.bukkit.event.block.Action.RIGHT_CLICK_AIR
                && action != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        var item = event.getItem();
        if (item == null) return;

        PlayerClassData data = plugin.dataStore().get(player.getUniqueId());
        if (data.chosenClass == null) return;

        var material = item.getType();
        var castResult = switch (data.chosenClass) {
            case MAGE -> {
                if (material != org.bukkit.Material.BLAZE_ROD) yield null;
                event.setCancelled(true);
                yield player.isSneaking()
                        ? plugin.abilityService().castLightning(player, data)
                        : plugin.abilityService().castFireball(player, data);
            }
            case WARRIOR -> {
                // Any right-click (air or block) triggers it — but skip if the
                // clicked block has a real vanilla interaction (chest, door,
                // button, furnace, etc.) so Whirlwind doesn't swallow that
                // normal interaction. Material.isInteractable() is the real
                // Bukkit check for this, not a blunt "air only" restriction —
                // air-only was the bug: a player is almost always looking at
                // *some* block (ground, walls) during normal play, so
                // RIGHT_CLICK_BLOCK is the common case, not the rare one.
                if (!material.name().endsWith("_SWORD")) yield null;
                var clickedBlock = event.getClickedBlock();
                if (clickedBlock != null && clickedBlock.getType().isInteractable()) yield null;
                event.setCancelled(true);
                yield plugin.abilityService().castWhirlwind(player, data);
            }
            case ARCHER -> {
                if (material != org.bukkit.Material.BOW && material != org.bukkit.Material.CROSSBOW) yield null;
                if (!player.isSneaking()) yield null;
                event.setCancelled(true);
                yield plugin.abilityService().castPowerShot(player, data);
            }
        };

        if (castResult == null) return;
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
