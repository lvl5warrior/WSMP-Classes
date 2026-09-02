package com.warriorssmp.classes.task;

import com.warriorssmp.classes.ClassesPlugin;
import com.warriorssmp.classes.data.PlayerClassData;
import com.warriorssmp.classes.model.PlayerClass;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * One persistent boss bar per player with a class picked, showing mana and
 * their active spell's cooldown. Deliberately NOT the action bar — the
 * action bar is the same spot every other WSMP plugin's XP-gain and task-
 * progress messages use, so a mana readout sent there every couple of
 * seconds constantly fights with and overwrites those, which is exactly
 * the "distracting" complaint. A boss bar sits at the top of the screen,
 * a completely separate UI element.
 */
public final class ManaBarManager {

    private final ClassesPlugin plugin;
    private final Map<UUID, BossBar> bars = new HashMap<>();

    public ManaBarManager(ClassesPlugin plugin) {
        this.plugin = plugin;
    }

    public void show(Player player, PlayerClassData data) {
        if (data.chosenClass == null) return;
        BossBar bar = bars.computeIfAbsent(player.getUniqueId(),
                id -> BossBar.bossBar(Component.text("..."), 0f, colorFor(data.chosenClass), BossBar.Overlay.PROGRESS));
        player.showBossBar(bar);
        update(player, data);
    }

    public void hide(Player player) {
        BossBar bar = bars.remove(player.getUniqueId());
        if (bar != null) player.hideBossBar(bar);
    }

    /** Called every mana regen tick — refreshes the fill and text with
     *  current mana and however much longer until the class's spell(s) are
     *  off cooldown. */
    public void update(Player player, PlayerClassData data) {
        if (data.chosenClass == null) return;
        BossBar bar = bars.get(player.getUniqueId());
        if (bar == null) return;

        double max = plugin.classConfig().manaMax();
        float fraction = max > 0 ? (float) Math.max(0, Math.min(1, data.currentMana / max)) : 0f;
        bar.progress(fraction);
        bar.color(colorFor(data.chosenClass));

        String cooldowns = switch (data.chosenClass) {
            case MAGE -> "§c🔥Fireball " + readyText(data.fireballReadyAt) + " §7| §e⚡Lightning " + readyText(data.lightningReadyAt);
            case WARRIOR -> "§c🌀Whirlwind " + readyText(data.whirlwindReadyAt);
            case ARCHER -> "§a🏹Power Shot " + readyText(data.powerShotReadyAt);
        };

        bar.name(Component.text("§bMana " + (int) data.currentMana + "/" + (int) max + " §7— " + cooldowns));
    }

    private String readyText(long readyAt) {
        long remaining = readyAt - System.currentTimeMillis();
        return remaining <= 0 ? "§aReady" : "§7" + (remaining / 1000 + 1) + "s";
    }

    private BossBar.Color colorFor(PlayerClass playerClass) {
        return switch (playerClass) {
            case WARRIOR -> BossBar.Color.RED;
            case MAGE -> BossBar.Color.BLUE;
            case ARCHER -> BossBar.Color.GREEN;
        };
    }

    public void hideAll() {
        for (var entry : bars.entrySet()) {
            Player player = plugin.getServer().getPlayer(entry.getKey());
            if (player != null) player.hideBossBar(entry.getValue());
        }
        bars.clear();
    }
}
