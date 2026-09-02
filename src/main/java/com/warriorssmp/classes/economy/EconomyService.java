package com.warriorssmp.classes.economy;

import com.warriorssmp.classes.ClassesPlugin;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

/** Unlike the gather/task skill plugins (which each have a standalone Points
 *  currency), WSMP-Classes has exactly one paid feature — the respec — so it
 *  just charges real server money via Vault instead of building a whole
 *  separate currency for one price tag. */
public final class EconomyService {

    private final ClassesPlugin plugin;
    private Economy economy;

    public EconomyService(ClassesPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean setupEconomy() {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> provider = plugin.getServer()
                .getServicesManager().getRegistration(Economy.class);
        if (provider == null) {
            return false;
        }
        economy = provider.getProvider();
        return economy != null;
    }

    public boolean isHooked() {
        return economy != null;
    }

    public boolean has(Player player, double amount) {
        return isHooked() && economy.has(player, amount);
    }

    /** Returns true if the withdrawal succeeded (i.e. the player could afford it). */
    public boolean withdraw(Player player, double amount) {
        if (amount <= 0) return true;
        if (!isHooked()) return false;
        if (!economy.has(player, amount)) return false;
        return economy.withdrawPlayer(player, amount).transactionSuccess();
    }

    public String format(double amount) {
        return isHooked() ? economy.format(amount) : String.valueOf(amount);
    }
}
