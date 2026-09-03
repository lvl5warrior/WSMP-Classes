package com.warriorssmp.classes.command;

import com.warriorssmp.classes.ClassesPlugin;
import com.warriorssmp.classes.data.PlayerClassData;
import com.warriorssmp.classes.model.PlayerClass;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class ClassAdminCommand implements CommandExecutor {

    private final ClassesPlugin plugin;

    public ClassAdminCommand(ClassesPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player player) {
                plugin.menuManager().openAdminPanel(player);
            } else {
                printUsage(sender);
            }
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> {
                plugin.reloadConfig();
                plugin.classConfig().load();
                sender.sendMessage("§aWSMP-Classes config reloaded — " + plugin.classConfig().allNodes().size() + " node(s) loaded.");
            }
            case "view" -> {
                if (args.length < 2) {
                    sender.sendMessage("§eUsage: /classeditor view <player>");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    sender.sendMessage("§cPlayer not found.");
                    return true;
                }
                PlayerClassData data = plugin.dataStore().get(target.getUniqueId());
                int level = plugin.levelService().levelOf(data);
                sender.sendMessage("§6--- Class data: " + target.getName() + " ---");
                sender.sendMessage("§7Class: §f" + (data.chosenClass == null ? "none chosen" : data.chosenClass.coloredName()));
                sender.sendMessage("§7Level: §f" + level + " §7(XP: " + data.totalXp + ")");
                sender.sendMessage("§7Available Points: §f" + plugin.skillTreeService().availablePoints(data));
                sender.sendMessage("§7Unlocked Nodes: §f" + data.nodeInvestment.size());
                sender.sendMessage("§7Lifetime PvP Damage Dealt: §f" + data.lifetimePvpDamageDealt);
            }
            case "setclass" -> {
                if (args.length < 3) {
                    sender.sendMessage("§eUsage: /classeditor setclass <player> <WARRIOR|MAGE|ARCHER>");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    sender.sendMessage("§cPlayer not found.");
                    return true;
                }
                PlayerClass playerClass;
                try {
                    playerClass = PlayerClass.valueOf(args[2].toUpperCase());
                } catch (IllegalArgumentException e) {
                    sender.sendMessage("§cUnknown class. Use WARRIOR, MAGE, or ARCHER.");
                    return true;
                }
                PlayerClassData data = plugin.dataStore().get(target.getUniqueId());
                data.chosenClass = playerClass;
                data.resetTree();
                plugin.abilityService().applyPassiveStats(target, data);
                sender.sendMessage("§aSet " + target.getName() + "'s class to " + playerClass.displayName() + " (tree reset).");
            }
            case "setlevel" -> {
                if (args.length < 3) {
                    sender.sendMessage("§eUsage: /classeditor setlevel <player> <level>");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    sender.sendMessage("§cPlayer not found.");
                    return true;
                }
                int level;
                try {
                    level = Integer.parseInt(args[2]);
                } catch (NumberFormatException e) {
                    sender.sendMessage("§cLevel must be a number.");
                    return true;
                }
                PlayerClassData data = plugin.dataStore().get(target.getUniqueId());
                data.totalXp = com.warriorssmp.classes.model.XpTable.xpForLevel(level);
                sender.sendMessage("§aSet " + target.getName() + "'s level to " + level + ".");
            }
            case "reset" -> {
                if (args.length < 2) {
                    sender.sendMessage("§eUsage: /classeditor reset <player>");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    sender.sendMessage("§cPlayer not found.");
                    return true;
                }
                plugin.dataStore().get(target.getUniqueId()).resetAll();
                plugin.abilityService().applyPassiveStats(target, plugin.dataStore().get(target.getUniqueId()));
                sender.sendMessage("§cReset all class progress for " + target.getName() + ".");
            }
            case "respec" -> {
                if (args.length < 2) {
                    sender.sendMessage("§eUsage: /classeditor respec <player>");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    sender.sendMessage("§cPlayer not found.");
                    return true;
                }
                PlayerClassData data = plugin.dataStore().get(target.getUniqueId());
                data.resetTree();
                plugin.abilityService().applyPassiveStats(target, data);
                sender.sendMessage("§aRespecced " + target.getName() + "'s skill tree (free, no cooldown applied).");
            }
            default -> printUsage(sender);
        }
        return true;
    }

    private void printUsage(CommandSender sender) {
        sender.sendMessage("§eUsage: /classeditor <reload|view|setclass|setlevel|reset|respec> ...");
    }
}
