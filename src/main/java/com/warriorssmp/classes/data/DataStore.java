package com.warriorssmp.classes.data;

import com.warriorssmp.classes.ClassesPlugin;
import com.warriorssmp.classes.model.PlayerClass;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class DataStore {

    private final ClassesPlugin plugin;
    private final File folder;
    private final Map<UUID, PlayerClassData> cache = new HashMap<>();

    public DataStore(ClassesPlugin plugin) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), "playerdata");
        if (!folder.exists()) folder.mkdirs();
    }

    public PlayerClassData get(UUID uuid) {
        // Plain get-then-put instead of computeIfAbsent: computeIfAbsent
        // forbids the map being touched again while it is still running,
        // including reentrantly from the same thread (e.g. a scoreboard or
        // placeholder plugin querying this same player's data mid-join,
        // before this call returns) — that throws
        // ConcurrentModificationException. This pattern tolerates a race
        // instead of crashing: worst case is one redundant load() call.
        PlayerClassData existing = cache.get(uuid);
        if (existing != null) return existing;
        PlayerClassData loaded = load(uuid);
        cache.put(uuid, loaded);
        return loaded;
    }

    public void unload(UUID uuid) {
        PlayerClassData data = cache.remove(uuid);
        if (data != null) save(data);
    }

    public List<PlayerClassData> allKnownPlayers() {
        Map<UUID, PlayerClassData> merged = new HashMap<>(cache);
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files != null) {
            for (File f : files) {
                try {
                    UUID uuid = UUID.fromString(f.getName().replace(".yml", ""));
                    if (!merged.containsKey(uuid)) merged.put(uuid, load(uuid)); // see get() above for why not computeIfAbsent
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        return new ArrayList<>(merged.values());
    }

    private PlayerClassData load(UUID uuid) {
        PlayerClassData data = new PlayerClassData(uuid);
        File file = new File(folder, uuid + ".yml");
        if (!file.exists()) return data;

        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        data.totalXp = yml.getLong("total-xp", 0);
        String className = yml.getString("class");
        if (className != null) {
            try {
                data.chosenClass = PlayerClass.valueOf(className);
            } catch (IllegalArgumentException ignored) {
            }
        }
        data.chosenPath = yml.getString("path");
        var investmentSection = yml.getConfigurationSection("node-investment");
        if (investmentSection != null) {
            for (String nodeId : investmentSection.getKeys(false)) {
                int points = investmentSection.getInt(nodeId, 0);
                if (points > 0) data.nodeInvestment.put(nodeId, points);
            }
        }
        data.lifetimePvpDamageDealt = yml.getInt("lifetime-pvp-damage-dealt", 0);
        data.lifetimeAbilityProcs = yml.getInt("lifetime-ability-procs", 0);
        data.lastRespecAt = yml.getLong("last-respec-at", 0);
        data.lastClassChangeAt = yml.getLong("last-class-change-at", 0);
        data.lastGuideBookAt = yml.getLong("last-guide-book-at", 0);
        data.currentMana = yml.getDouble("current-mana", 0);
        data.fireballReadyAt = yml.getLong("fireball-ready-at", 0);
        data.lightningReadyAt = yml.getLong("lightning-ready-at", 0);
        data.whirlwindReadyAt = yml.getLong("whirlwind-ready-at", 0);
        data.powerShotReadyAt = yml.getLong("power-shot-ready-at", 0);
        var cooldownSection = yml.getConfigurationSection("ability-cooldowns");
        if (cooldownSection != null) {
            for (String key : cooldownSection.getKeys(false)) {
                data.abilityCooldowns.put(key, cooldownSection.getLong(key, 0));
            }
        }
        return data;
    }

    public void save(PlayerClassData data) {
        YamlConfiguration yml = new YamlConfiguration();
        yml.set("total-xp", data.totalXp);
        yml.set("class", data.chosenClass == null ? null : data.chosenClass.name());
        yml.set("path", data.chosenPath);
        for (var entry : data.nodeInvestment.entrySet()) {
            yml.set("node-investment." + entry.getKey(), entry.getValue());
        }
        yml.set("lifetime-pvp-damage-dealt", data.lifetimePvpDamageDealt);
        yml.set("lifetime-ability-procs", data.lifetimeAbilityProcs);
        yml.set("last-respec-at", data.lastRespecAt);
        yml.set("last-class-change-at", data.lastClassChangeAt);
        yml.set("last-guide-book-at", data.lastGuideBookAt);
        yml.set("current-mana", data.currentMana);
        yml.set("fireball-ready-at", data.fireballReadyAt);
        yml.set("lightning-ready-at", data.lightningReadyAt);
        yml.set("whirlwind-ready-at", data.whirlwindReadyAt);
        yml.set("power-shot-ready-at", data.powerShotReadyAt);
        for (var entry : data.abilityCooldowns.entrySet()) {
            yml.set("ability-cooldowns." + entry.getKey(), entry.getValue());
        }
        try {
            yml.save(new File(folder, data.uuid + ".yml"));
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to save class data for " + data.uuid + ": " + e.getMessage());
        }
    }

    public void saveAll() {
        for (PlayerClassData data : cache.values()) {
            save(data);
        }
    }
}
