package com.warriorssmp.classes.task;

import com.warriorssmp.classes.data.PlayerClassData;
import com.warriorssmp.classes.model.SkillNode;
import org.bukkit.entity.Player;

public final class SkillTreeService {

    public enum UnlockResult {SUCCESS, ALREADY_MAXED, MISSING_PREREQUISITE, LEVEL_TOO_LOW, NOT_ENOUGH_POINTS, WRONG_CLASS, NO_CLASS_CHOSEN}

    private final ClassConfig config;
    private final LevelService levelService;

    public SkillTreeService(ClassConfig config, LevelService levelService) {
        this.config = config;
        this.levelService = levelService;
    }

    /** Points earned from leveling minus the summed cost×investment of every
     *  node — the single source of truth for "how many points can I spend
     *  right now," recomputed on demand rather than tracked separately. */
    public int availablePoints(PlayerClassData data) {
        int earned = levelService.totalPointsEarned(data);
        int spent = 0;
        for (var entry : data.nodeInvestment.entrySet()) {
            SkillNode node = config.node(entry.getKey());
            if (node != null) spent += node.cost() * entry.getValue();
        }
        return Math.max(0, earned - spent);
    }

    public boolean hasPrerequisites(PlayerClassData data, SkillNode node) {
        for (String prereq : node.prerequisites()) {
            if (!data.isUnlocked(prereq)) return false;
        }
        return true;
    }

    /** Invests one more point in a node — for a normal single-unlock ability
     *  (max-points: 1) this is the same as "unlocking" it; for a multi-point
     *  stat buff (e.g. Vitality, max-points: 5) this adds one more point up
     *  to its cap, each point paid for separately at the node's cost. */
    public UnlockResult tryInvest(Player player, PlayerClassData data, SkillNode node) {
        if (data.chosenClass == null) return UnlockResult.NO_CLASS_CHOSEN;
        if (node.playerClass() != data.chosenClass) return UnlockResult.WRONG_CLASS;
        if (data.investmentIn(node.id()) >= node.maxPoints()) return UnlockResult.ALREADY_MAXED;
        if (levelService.levelOf(data) < node.minLevel()) return UnlockResult.LEVEL_TOO_LOW;
        if (!hasPrerequisites(data, node)) return UnlockResult.MISSING_PREREQUISITE;
        if (availablePoints(data) < node.cost()) return UnlockResult.NOT_ENOUGH_POINTS;

        data.nodeInvestment.merge(node.id(), 1, Integer::sum);
        if (node.isMultiPoint()) {
            player.sendMessage("§a§lPOINT INVESTED §7— §f" + node.displayName()
                    + " §7(" + data.investmentIn(node.id()) + "/" + node.maxPoints() + ")");
        } else {
            player.sendMessage("§a§lNODE UNLOCKED §7— §f" + node.displayName());
        }
        return UnlockResult.SUCCESS;
    }

    /** Clears every unlocked node/invested point so points can be redistributed.
     *  Class and level are untouched. */
    public void respec(PlayerClassData data) {
        data.resetTree();
    }
}
