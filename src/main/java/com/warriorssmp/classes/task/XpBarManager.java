package com.warriorssmp.classes.task;

import com.warriorssmp.classes.ClassesPlugin;
import com.warriorssmp.classes.data.PlayerClassData;
import com.warriorssmp.classes.model.XpTable;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A second boss bar, separate from ManaBarManager's persistent one, showing
 * level/XP progress — but only briefly, right after XP is gained, then
 * auto-hiding itself a few seconds later. Boss bars don't have a native
 * fade animation the way action bar text does, so "fading like the job
 * plugins" is simulated here: show it, schedule a hide a few seconds out,
 * and cancel+reschedule that hide every time more XP comes in so it doesn't
 * flicker during a flurry of hits.
 */
public final class XpBarManager {

    private static final long HIDE_DELAY_TICKS = 160L; // 8 seconds — was 3, which read as "instant"

    private final ClassesPlugin plugin;
    private final Map<UUID, BossBar> bars = new HashMap<>();
    private final Map<UUID, BukkitTask> hideTasks = new HashMap<>();

    public XpBarManager(ClassesPlugin plugin) {
        this.plugin = plugin;
    }

    /** Call whenever a player gains class XP — shows the bar (or refreshes
     *  it if already showing) and (re)starts the auto-hide timer. */
    public void showXpGain(Player player, PlayerClassData data) {
        int level = plugin.levelService().levelOf(data);
        boolean maxLevel = level >= XpTable.MAX_LEVEL;
        long xpIntoLevel = data.totalXp - XpTable.xpForLevel(level);
        long xpForNext = Math.max(1, XpTable.xpForNextLevel(level) - XpTable.xpForLevel(level));
        float fraction = maxLevel ? 1f : (float) Math.max(0.0, Math.min(1.0, (double) xpIntoLevel / xpForNext));

        BossBar bar = bars.computeIfAbsent(player.getUniqueId(),
                id -> BossBar.bossBar(Component.text(""), 0f, BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS));
        bar.progress(fraction);
        String pathSuffix = (data.chosenPath != null)
                ? " §7[" + Character.toUpperCase(data.chosenPath.charAt(0)) + data.chosenPath.substring(1) + "]"
                : "";
        String text = maxLevel
                ? "§eLevel " + level + pathSuffix + " §7— §aMAX LEVEL"
                : "§eLevel " + level + pathSuffix + " §7— §f" + xpIntoLevel + "/" + xpForNext + " XP";
        bar.name(Component.text(text));
        player.showBossBar(bar);

        BukkitTask existing = hideTasks.remove(player.getUniqueId());
        if (existing != null) existing.cancel();

        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            player.hideBossBar(bar);
            hideTasks.remove(player.getUniqueId());
        }, HIDE_DELAY_TICKS);
        hideTasks.put(player.getUniqueId(), task);
    }

    public void hide(Player player) {
        BossBar bar = bars.remove(player.getUniqueId());
        if (bar != null) player.hideBossBar(bar);
        BukkitTask task = hideTasks.remove(player.getUniqueId());
        if (task != null) task.cancel();
    }

    public void hideAll() {
        for (var entry : bars.entrySet()) {
            Player player = plugin.getServer().getPlayer(entry.getKey());
            if (player != null) player.hideBossBar(entry.getValue());
        }
        bars.clear();
        for (BukkitTask task : hideTasks.values()) task.cancel();
        hideTasks.clear();
    }
}
