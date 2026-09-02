package com.warriorssmp.classes;

import com.warriorssmp.classes.data.DataStore;
import com.warriorssmp.classes.economy.EconomyService;
import com.warriorssmp.classes.listener.CombatListener;
import com.warriorssmp.classes.menu.MenuManager;
import com.warriorssmp.classes.task.ManaBarManager;
import com.warriorssmp.classes.task.XpBarManager;
import com.warriorssmp.classes.task.AbilityService;
import com.warriorssmp.classes.task.ClassConfig;
import com.warriorssmp.classes.task.LevelService;
import com.warriorssmp.classes.task.SkillTreeService;
import org.bukkit.plugin.java.JavaPlugin;

public final class ClassesPlugin extends JavaPlugin {

    private static ClassesPlugin instance;

    private ClassConfig classConfig;
    private DataStore dataStore;
    private LevelService levelService;
    private SkillTreeService skillTreeService;
    private AbilityService abilityService;
    private EconomyService economyService;
    private MenuManager menuManager;
    private ManaBarManager manaBarManager;
    private XpBarManager xpBarManager;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        this.classConfig = new ClassConfig(this);
        this.dataStore = new DataStore(this);
        this.xpBarManager = new XpBarManager(this);
        this.levelService = new LevelService(classConfig, xpBarManager);
        this.skillTreeService = new SkillTreeService(classConfig, levelService);
        this.abilityService = new AbilityService(classConfig, this);
        this.economyService = new EconomyService(this);
        economyService.setupEconomy();
        this.menuManager = new MenuManager(this);
        this.manaBarManager = new ManaBarManager(this);

        getServer().getPluginManager().registerEvents(new CombatListener(this), this);
        getServer().getPluginManager().registerEvents(menuManager, this);
        getCommand("classmenu").setExecutor(new com.warriorssmp.classes.command.ClassCommand(this));
        getCommand("classeditor").setExecutor(new com.warriorssmp.classes.command.ClassAdminCommand(this));
        getCommand("cast").setExecutor(new com.warriorssmp.classes.command.ClassCastCommand(this));

        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new ClassesPlaceholders(this).register();
            getLogger().info("Hooked into PlaceholderAPI.");
        }

        // Periodic autosave, matching the pattern in every other WSMP skill plugin.
        getServer().getScheduler().runTaskTimerAsynchronously(this, dataStore::saveAll, 20L * 60 * 5, 20L * 60 * 5);

        // Mana regen tick — every class has mana now (Mage: Fireball/
        // Lightning, Warrior: Whirlwind Strike, Archer: Power Shot).
        // Interval comes from config, so it would need a reload to change;
        // fine for now, matches the level of dynamism the rest of this
        // plugin's tasks have. Floored at 1 tick so a misconfigured
        // "mana-regen-interval-seconds: 0" can't produce a 0-tick period,
        // which Bukkit's scheduler rejects/misbehaves on.
        long manaIntervalTicks = Math.max(1, classConfig.manaRegenIntervalMillis() / 50L);
        getServer().getScheduler().runTaskTimer(this, () -> {
            for (org.bukkit.entity.Player player : getServer().getOnlinePlayers()) {
                var data = dataStore.get(player.getUniqueId());
                if (data.chosenClass != null) {
                    abilityService.tickManaRegen(player, data);
                    manaBarManager.update(player, data);
                }
            }
        }, manaIntervalTicks, manaIntervalTicks);

        getLogger().info("WSMP-Classes enabled — " + classConfig.allNodes().size() + " skill tree node(s) loaded.");
    }

    @Override
    public void onDisable() {
        if (dataStore != null) dataStore.saveAll();
        if (manaBarManager != null) manaBarManager.hideAll();
        if (xpBarManager != null) xpBarManager.hideAll();
    }

    public static ClassesPlugin get() {
        return instance;
    }

    public ClassConfig classConfig() {
        return classConfig;
    }

    public DataStore dataStore() {
        return dataStore;
    }

    public LevelService levelService() {
        return levelService;
    }

    public SkillTreeService skillTreeService() {
        return skillTreeService;
    }

    public AbilityService abilityService() {
        return abilityService;
    }

    public EconomyService economy() {
        return economyService;
    }

    public MenuManager menuManager() {
        return menuManager;
    }

    public ManaBarManager manaBarManager() {
        return manaBarManager;
    }

    public XpBarManager xpBarManager() {
        return xpBarManager;
    }
}
