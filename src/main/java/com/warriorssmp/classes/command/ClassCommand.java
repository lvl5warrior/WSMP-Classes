package com.warriorssmp.classes.command;

import com.warriorssmp.classes.ClassesPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class ClassCommand implements CommandExecutor {

    private final ClassesPlugin plugin;

    public ClassCommand(ClassesPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        plugin.menuManager().openMainMenu(player);
        return true;
    }
}
