package com.warriorssmp.classes;

import com.warriorssmp.classes.data.PlayerClassData;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

/**
 * Registers %wsmpclasses_class%, %wsmpclasses_level%, %wsmpclasses_points%,
 * %wsmpclasses_path% (the chosen path, e.g. "Fire" — empty string if no
 * path chosen yet), and %wsmpclasses_class_level% (a combined
 * "Warrior - Lv.42" style string, matching the same format used elsewhere
 * for scoreboard rows).
 */
public final class ClassesPlaceholders extends PlaceholderExpansion {

    private final ClassesPlugin plugin;

    public ClassesPlaceholders(ClassesPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "wsmpclasses";
    }

    @Override
    public @NotNull String getAuthor() {
        return "WarriorsSMP";
    }

    @Override
    public @NotNull String getVersion() {
        return "1.0.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) return "";
        PlayerClassData data = plugin.dataStore().get(player.getUniqueId());
        int level = plugin.levelService().levelOf(data);

        return switch (params.toLowerCase()) {
            case "class" -> data.chosenClass == null ? "None" : data.chosenClass.displayName();
            case "level" -> String.valueOf(level);
            case "points" -> String.valueOf(plugin.skillTreeService().availablePoints(data));
            case "path" -> data.chosenPath == null || data.chosenPath.isEmpty()
                    ? ""
                    : Character.toUpperCase(data.chosenPath.charAt(0)) + data.chosenPath.substring(1);
            case "class_level" -> data.chosenClass == null
                    ? "§7No Class"
                    : "§f" + data.chosenClass.displayName() + " - §6Lv." + level;
            default -> null;
        };
    }
}
