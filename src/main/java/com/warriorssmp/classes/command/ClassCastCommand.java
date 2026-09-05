package com.warriorssmp.classes.command;

import com.warriorssmp.classes.ClassesPlugin;
import com.warriorssmp.classes.data.PlayerClassData;
import com.warriorssmp.classes.model.PlayerClass;
import com.warriorssmp.classes.task.AbilityService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /cast <spell> — a guaranteed-reliable way to trigger each class's active
 * spell, independent of which item is in hand or how the client sends
 * right-click packets (which the item-based triggers in CombatListener
 * depend on, and which can behave inconsistently for items with no defined
 * vanilla "use" — bare swords/arrows in particular). The item-based
 * triggers stay as a convenience; this command is the one guaranteed to
 * always work the same way.
 */
public final class ClassCastCommand implements CommandExecutor {

    private final ClassesPlugin plugin;

    public ClassCastCommand(ClassesPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage("§eUsage: /cast <fireball|lightning|iceball|whirlwind|charge|powershot>");
            return true;
        }

        PlayerClassData data = plugin.dataStore().get(player.getUniqueId());
        if (data.chosenClass == null) {
            player.sendMessage("§cPick a class first with /classmenu.");
            return true;
        }

        String spell = args[0].toLowerCase();
        AbilityService.CastResult result = switch (spell) {
            case "fireball" -> data.chosenClass == PlayerClass.MAGE
                    ? plugin.abilityService().castFireball(player, data) : wrongClass(player, "Mage");
            case "lightning" -> data.chosenClass == PlayerClass.MAGE
                    ? plugin.abilityService().castLightning(player, data) : wrongClass(player, "Mage");
            case "iceball" -> data.chosenClass == PlayerClass.MAGE
                    ? plugin.abilityService().castIceball(player, data) : wrongClass(player, "Mage");
            case "whirlwind" -> data.chosenClass == PlayerClass.WARRIOR
                    ? plugin.abilityService().castWhirlwind(player, data) : wrongClass(player, "Warrior");
            case "charge" -> data.chosenClass == PlayerClass.WARRIOR
                    ? plugin.abilityService().castCharge(player, data) : wrongClass(player, "Warrior");
            case "powershot" -> data.chosenClass == PlayerClass.ARCHER
                    ? plugin.abilityService().castPowerShot(player, data) : wrongClass(player, "Archer");
            default -> {
                player.sendMessage("§eUnknown spell. Use: fireball, lightning, iceball, whirlwind, charge, or powershot.");
                yield null;
            }
        };

        if (result == null) return true;
        switch (result) {
            case NOT_ENOUGH_MANA -> player.sendMessage("§cNot enough mana.");
            case ON_COOLDOWN -> player.sendMessage("§cThat ability is still on cooldown.");
            case NO_ARROWS -> player.sendMessage("§cYou need at least one arrow in your inventory.");
            case NOT_A_MAGE, SUCCESS -> {}
        }
        return true;
    }

    private AbilityService.CastResult wrongClass(Player player, String needed) {
        player.sendMessage("§cThat's a " + needed + " ability — you're not playing " + needed + ".");
        return null;
    }
}
