package com.warriorssmp.classes.data;

import com.warriorssmp.classes.model.PlayerClass;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class PlayerClassData {

    public final UUID uuid;
    public PlayerClass chosenClass = null;
    /** Which of that class's 3 mutually-exclusive paths (e.g. Mage:
     *  fire/ice/lightning) the player has committed to — null means not
     *  chosen yet. Once set, only nodes with a matching path() (or no path
     *  at all, i.e. shared/universal nodes) can be invested in; see
     *  SkillTreeService.tryInvest(). Cleared by a respec, same as the tree
     *  itself, so choosing a new path requires spending points again. */
    public String chosenPath = null;
    public long totalXp = 0;
    /** Node ID -> points invested (0 = not present in this map at all).
     *  Single-unlock abilities just have 1 point (max-points: 1 in config);
     *  multi-point stat buffs (e.g. Vitality, 0-5) can have more. Available
     *  points are computed on demand (total earned from level, minus the
     *  summed cost×investment of everything here) so there's a single
     *  source of truth instead of a separately tracked counter. */
    public final Map<String, Integer> nodeInvestment = new HashMap<>();
    /** Node ID -> timestamp the ability can next trigger. */
    public final Map<String, Long> abilityCooldowns = new HashMap<>();
    public int lifetimePvpDamageDealt = 0;
    public int lifetimeAbilityProcs = 0;
    public long lastRespecAt = 0;
    public long lastClassChangeAt = 0;
    public long lastGuideBookAt = 0;
    /** A real resource spent casting each class's active spells (Fireball/
     *  Lightning for Mage, Whirlwind Strike for Warrior, Power Shot for
     *  Archer), regenerates over time — every class has one now. */
    public double currentMana = 0;
    public long fireballReadyAt = 0;
    public long lightningReadyAt = 0;
    public long whirlwindReadyAt = 0;
    public long powerShotReadyAt = 0;

    public PlayerClassData(UUID uuid) {
        this.uuid = uuid;
    }

    public int investmentIn(String nodeId) {
        return nodeInvestment.getOrDefault(nodeId, 0);
    }

    public boolean isUnlocked(String nodeId) {
        return investmentIn(nodeId) > 0;
    }

    /** Wipes everything — used by /classadmin reset and by a full class change
     *  (picking a new class starts your tree over, matching how a respec only
     *  clears the tree but keeps your class and level). */
    public void resetAll() {
        chosenClass = null;
        chosenPath = null;
        totalXp = 0;
        nodeInvestment.clear();
        abilityCooldowns.clear();
        lifetimePvpDamageDealt = 0;
        lifetimeAbilityProcs = 0;
        lastRespecAt = 0;
        lastClassChangeAt = 0;
        lastGuideBookAt = 0;
        currentMana = 0;
        fireballReadyAt = 0;
        lightningReadyAt = 0;
        whirlwindReadyAt = 0;
        powerShotReadyAt = 0;
    }

    /** Respec — clears the tree and path choice. Class and level are kept. */
    public void resetTree() {
        nodeInvestment.clear();
        chosenPath = null;
    }
}
