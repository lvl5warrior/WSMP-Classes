package com.warriorssmp.classes.task;

import com.warriorssmp.classes.data.PlayerClassData;
import com.warriorssmp.classes.model.XpTable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.time.Duration;

public final class LevelService {

    private final ClassConfig config;

    public LevelService(ClassConfig config) {
        this.config = config;
    }

    public int levelOf(PlayerClassData data) {
        return XpTable.levelForXp(data.totalXp);
    }

    /** Skill points earned so far from leveling — available points are this
     *  minus whatever's already spent on unlocked nodes (see SkillTreeService). */
    public int totalPointsEarned(PlayerClassData data) {
        return levelOf(data) / config.pointsPerLevelInterval();
    }

    /** Grants XP and announces a level-up (and any newly earned skill point)
     *  if it happened. Returns the new level. */
    public int grantXp(Player player, PlayerClassData data, double amount) {
        int levelBefore = levelOf(data);
        int pointsBefore = totalPointsEarned(data);

        data.totalXp += Math.round(amount);

        int levelAfter = levelOf(data);
        int pointsAfter = totalPointsEarned(data);

        if (levelAfter > levelBefore) {
            announceLevelUp(player, levelAfter, pointsAfter - pointsBefore);
        }
        return levelAfter;
    }

    private void announceLevelUp(Player player, int newLevel, int pointsGained) {
        String pointsLine = pointsGained > 0
                ? "§7+" + pointsGained + " Skill Point" + (pointsGained > 1 ? "s" : "")
                : "";
        player.showTitle(Title.title(
                Component.text("§6§lLEVEL UP!"),
                Component.text("§eClass Level " + newLevel + " " + pointsLine),
                Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(2000), Duration.ofMillis(500))
        ));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        player.sendMessage("§6§lLEVEL UP! §7You are now Class Level §f" + newLevel
                + (pointsGained > 0 ? " §7(+" + pointsGained + " Skill Point" + (pointsGained > 1 ? "s" : "") + ")" : ""));
    }
}
