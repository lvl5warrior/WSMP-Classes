package com.warriorssmp.classes.task;

import com.warriorssmp.classes.ClassesPlugin;
import com.warriorssmp.classes.model.PlayerClass;
import com.warriorssmp.classes.model.SkillNode;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every node uses one of a small set of generic, parameterized ability
 * behaviors (see AbilityService) rather than 21 hand-coded one-off effects —
 * "Battle Instinct" and its upgrade "Reckless Slam" are the same underlying
 * behavior with different chance/amplifier/duration numbers, all editable
 * from the Admin Panel without a rebuild.
 */
public final class ClassConfig {

    private final ClassesPlugin plugin;
    private final Map<String, SkillNode> nodes = new LinkedHashMap<>();

    private int pointsPerLevelInterval;
    private double respecCost;
    private double classChangeCost;
    private long respecCooldownMillis;
    private long classChangeCooldownMillis;
    private long guideBookCooldownMillis;
    private double pvpXpPerDamage;
    private double pveXpPerDamage;
    private double pvpKillBonusXp;

    // Mage mana + base spell kit — always available to any Mage from level 1
    // (not gated by the tree at all), same as Warrior can always swing a
    // sword and Archer can always draw a bow. Tree nodes make these spells
    // stronger/cheaper rather than unlocking them from scratch.
    private double manaMax;
    private double mageManaBonus;
    private double manaRegenAmount;
    private long manaRegenIntervalMillis;
    private double fireballManaCost;
    private double fireballBaseDamage;
    private double fireballAoeRadius;
    private double fireballGroundFireSeconds;
    private long fireballCooldownMillis;
    private double lightningManaCost;
    private double lightningBaseDamage;
    private long lightningCooldownMillis;
    private long lightningBlinkCooldownMillis;
    private long rallyingCryCooldownMillis;
    private long stormOfArrowsCooldownMillis;
    private double stormOfArrowsDamage;
    private double iceballBonusDamage;
    private double iceballSlownessSeconds;
    private long iceballCooldownMillis;
    private double iceballManaCost;
    private double whirlwindManaCost;
    private double chargeManaCost;
    private double chargeBaseDamage;
    private long chargeCooldownMillis;
    private double whirlwindBaseDamage;
    private double whirlwindRadius;
    private long whirlwindCooldownMillis;
    private double powerShotManaCost;
    private double powerShotBaseDamage;
    private double trapBaseDamage;
    private long trapCooldownMillis;
    private long trapLifetimeMillis;
    private long powerShotCooldownMillis;

    public ClassConfig(ClassesPlugin plugin) {
        this.plugin = plugin;
        load();
    }

    private static String translateColors(String raw) {
        if (raw == null) return "";
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', raw);
    }

    private static List<String> translateColorsList(List<String> raw) {
        List<String> out = new ArrayList<>();
        for (String line : raw) out.add(translateColors(line));
        return out;
    }

    public void load() {
        nodes.clear();
        var cfg = plugin.getConfig();

        ConfigurationSection settings = cfg.getConfigurationSection("settings");
        if (settings != null) {
            pointsPerLevelInterval = settings.getInt("points-per-level-interval", 5);
            respecCost = settings.getDouble("respec-cost", 500.0);
            classChangeCost = settings.getDouble("class-change-cost", 10000.0);
            respecCooldownMillis = settings.getLong("respec-cooldown-days", 7) * 86_400_000L;
            classChangeCooldownMillis = settings.getLong("class-change-cooldown-days", 7) * 86_400_000L;
            guideBookCooldownMillis = settings.getLong("guide-book-cooldown-hours", 24) * 3_600_000L;
            pvpXpPerDamage = settings.getDouble("pvp-xp-per-damage", 2.0);
            pveXpPerDamage = settings.getDouble("pve-xp-per-damage", 0.3);
            pvpKillBonusXp = settings.getDouble("pvp-kill-bonus-xp", 50.0);

            manaMax = settings.getDouble("mana-max", 100.0);
            mageManaBonus = settings.getDouble("mage-mana-bonus", 50.0);
            manaRegenAmount = settings.getDouble("mana-regen-amount", 5.0);
            manaRegenIntervalMillis = settings.getLong("mana-regen-interval-seconds", 2) * 1000L;
            fireballManaCost = settings.getDouble("fireball-mana-cost", 25.0);
            fireballBaseDamage = settings.getDouble("fireball-base-damage", 7.0);
            fireballAoeRadius = settings.getDouble("fireball-aoe-radius", 3.0);
            fireballGroundFireSeconds = settings.getDouble("fireball-ground-fire-seconds", 4.0);
            fireballCooldownMillis = settings.getLong("fireball-cooldown-seconds", 3) * 1000L;
            lightningManaCost = settings.getDouble("lightning-mana-cost", 50.0);
            lightningBaseDamage = settings.getDouble("lightning-base-damage", 9.0);
            lightningCooldownMillis = settings.getLong("lightning-cooldown-seconds", 8) * 1000L;
            lightningBlinkCooldownMillis = settings.getLong("lightning-blink-cooldown-seconds", 60) * 1000L;
            rallyingCryCooldownMillis = settings.getLong("rallying-cry-cooldown-seconds", 60) * 1000L;
            stormOfArrowsCooldownMillis = settings.getLong("storm-of-arrows-cooldown-seconds", 60) * 1000L;
            stormOfArrowsDamage = settings.getDouble("storm-of-arrows-damage", 2.5);
            iceballBonusDamage = settings.getDouble("iceball-bonus-damage", 3.5);
            iceballSlownessSeconds = settings.getDouble("iceball-slowness-seconds", 3.0);
            iceballCooldownMillis = settings.getLong("iceball-cooldown-seconds", 2) * 1000L;
            iceballManaCost = settings.getDouble("iceball-mana-cost", 8.0);
            whirlwindManaCost = settings.getDouble("whirlwind-mana-cost", 30.0);
            chargeManaCost = settings.getDouble("charge-mana-cost", 25.0);
            chargeBaseDamage = settings.getDouble("charge-base-damage", 4.0);
            chargeCooldownMillis = settings.getLong("charge-cooldown-seconds", 8) * 1000L;
            whirlwindBaseDamage = settings.getDouble("whirlwind-base-damage", 6.0);
            whirlwindRadius = settings.getDouble("whirlwind-radius", 3.0);
            whirlwindCooldownMillis = settings.getLong("whirlwind-cooldown-seconds", 5) * 1000L;
            powerShotManaCost = settings.getDouble("power-shot-mana-cost", 20.0);
            powerShotBaseDamage = settings.getDouble("power-shot-base-damage", 3.0);
            trapBaseDamage = settings.getDouble("trap-base-damage", 4.0);
            trapCooldownMillis = settings.getLong("trap-cooldown-seconds", 15) * 1000L;
            trapLifetimeMillis = settings.getLong("trap-lifetime-seconds", 45) * 1000L;
            powerShotCooldownMillis = settings.getLong("power-shot-cooldown-seconds", 4) * 1000L;
        }

        ConfigurationSection nodesSection = cfg.getConfigurationSection("nodes");
        if (nodesSection != null) {
            for (String id : nodesSection.getKeys(false)) {
                ConfigurationSection ns = nodesSection.getConfigurationSection(id);
                if (ns == null) continue;

                PlayerClass playerClass;
                try {
                    playerClass = PlayerClass.valueOf(ns.getString("class", "WARRIOR").toUpperCase());
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Unknown class for node " + id + ", skipping.");
                    continue;
                }

                Material icon = Material.matchMaterial(ns.getString("icon", "STONE"));
                if (icon == null) icon = Material.STONE;

                Map<String, Double> params = new HashMap<>();
                ConfigurationSection paramsSection = ns.getConfigurationSection("params");
                if (paramsSection != null) {
                    for (String key : paramsSection.getKeys(false)) {
                        params.put(key, paramsSection.getDouble(key));
                    }
                }

                Map<String, String> stringParams = new HashMap<>();
                ConfigurationSection stringParamsSection = ns.getConfigurationSection("string-params");
                if (stringParamsSection != null) {
                    for (String key : stringParamsSection.getKeys(false)) {
                        stringParams.put(key, stringParamsSection.getString(key, ""));
                    }
                }

                List<String> prereqs = ns.getStringList("prerequisites");

                nodes.put(id, new SkillNode(
                        id,
                        playerClass,
                        translateColors(ns.getString("display", id)),
                        translateColorsList(ns.getStringList("description")),
                        ns.getInt("cost", 1),
                        prereqs,
                        ns.getInt("min-level", 1),
                        icon,
                        ns.getString("ability", ""),
                        ns.getDouble("chance", 1.0),
                        ns.getLong("cooldown-seconds", 0) * 1000L,
                        params,
                        stringParams,
                        ns.getInt("max-points", 1),
                        ns.getBoolean("capstone", false),
                        ns.getInt("tier", 1),
                        ns.getString("path", null)
                ));
            }
        }
    }

    public SkillNode node(String id) {
        return nodes.get(id);
    }

    public List<SkillNode> nodesFor(PlayerClass playerClass) {
        List<SkillNode> result = new ArrayList<>();
        for (SkillNode node : nodes.values()) {
            if (node.playerClass() == playerClass) result.add(node);
        }
        return result;
    }

    public Map<String, SkillNode> allNodes() {
        return nodes;
    }

    public int pointsPerLevelInterval() {
        return pointsPerLevelInterval;
    }

    public double respecCost() {
        return respecCost;
    }

    public double classChangeCost() {
        return classChangeCost;
    }

    public long respecCooldownMillis() {
        return respecCooldownMillis;
    }

    public long classChangeCooldownMillis() {
        return classChangeCooldownMillis;
    }

    public long guideBookCooldownMillis() {
        return guideBookCooldownMillis;
    }

    public double manaMax() {
        return manaMax;
    }

    /** Mage gets a larger mana pool than Warrior/Archer — its whole kit
     *  (Fireball, Lightning, Iceball, all three) draws from the same pool,
     *  where Warrior/Archer each only have one active spell. */
    public double manaMaxFor(com.warriorssmp.classes.model.PlayerClass cls) {
        return manaMax + (cls == com.warriorssmp.classes.model.PlayerClass.MAGE ? mageManaBonus : 0);
    }

    public double manaRegenAmount() {
        return manaRegenAmount;
    }

    public long manaRegenIntervalMillis() {
        return manaRegenIntervalMillis;
    }

    public double fireballManaCost() {
        return fireballManaCost;
    }

    public double fireballBaseDamage() {
        return fireballBaseDamage;
    }

    public double fireballAoeRadius() {
        return fireballAoeRadius;
    }

    public double fireballGroundFireSeconds() {
        return fireballGroundFireSeconds;
    }

    public long fireballCooldownMillis() {
        return fireballCooldownMillis;
    }

    public double lightningManaCost() {
        return lightningManaCost;
    }

    public double lightningBaseDamage() {
        return lightningBaseDamage;
    }

    public long lightningCooldownMillis() {
        return lightningCooldownMillis;
    }

    public long lightningBlinkCooldownMillis() {
        return lightningBlinkCooldownMillis;
    }

    public long rallyingCryCooldownMillis() {
        return rallyingCryCooldownMillis;
    }

    public long stormOfArrowsCooldownMillis() {
        return stormOfArrowsCooldownMillis;
    }

    public double stormOfArrowsDamage() {
        return stormOfArrowsDamage;
    }

    public double iceballBonusDamage() {
        return iceballBonusDamage;
    }

    public double iceballSlownessSeconds() {
        return iceballSlownessSeconds;
    }

    public long iceballCooldownMillis() {
        return iceballCooldownMillis;
    }

    public double iceballManaCost() {
        return iceballManaCost;
    }

    public double whirlwindManaCost() {
        return whirlwindManaCost;
    }

    public double chargeManaCost() {
        return chargeManaCost;
    }

    public double chargeBaseDamage() {
        return chargeBaseDamage;
    }

    public long chargeCooldownMillis() {
        return chargeCooldownMillis;
    }

    public double whirlwindBaseDamage() {
        return whirlwindBaseDamage;
    }

    public double whirlwindRadius() {
        return whirlwindRadius;
    }

    public long whirlwindCooldownMillis() {
        return whirlwindCooldownMillis;
    }

    public double powerShotManaCost() {
        return powerShotManaCost;
    }

    public double powerShotBaseDamage() {
        return powerShotBaseDamage;
    }

    public double trapBaseDamage() {
        return trapBaseDamage;
    }

    public long trapCooldownMillis() {
        return trapCooldownMillis;
    }

    public long trapLifetimeMillis() {
        return trapLifetimeMillis;
    }

    public long powerShotCooldownMillis() {
        return powerShotCooldownMillis;
    }

    public double pvpXpPerDamage() {
        return pvpXpPerDamage;
    }

    public double pveXpPerDamage() {
        return pveXpPerDamage;
    }

    public double pvpKillBonusXp() {
        return pvpKillBonusXp;
    }
}
