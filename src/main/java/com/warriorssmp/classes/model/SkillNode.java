package com.warriorssmp.classes.model;

import org.bukkit.Material;

import java.util.List;
import java.util.Map;

/**
 * Every ability in this plugin is a real vanilla effect (potion effects,
 * extra real arrows, extra real knockback, Ender Pearl damage) triggered off
 * an existing vanilla action — melee hits, bow shots, thrown potions, thrown
 * Ender Pearls — never an invented mechanic. `abilityKey` tells AbilityService
 * which trigger/effect pairing to run; `chance` and `params` are the tunable
 * numbers behind it, all editable from the Admin Panel without touching code.
 */
public record SkillNode(
        String id,
        PlayerClass playerClass,
        String displayName,
        List<String> description,
        int cost,
        List<String> prerequisites,
        int minLevel,
        Material icon,
        String abilityKey,
        double chance,
        long cooldownMillis,
        Map<String, Double> params,
        Map<String, String> stringParams,
        int maxPoints,
        boolean capstone,
        int tier,
        String path
) {
    public double param(String key, double def) {
        return params.getOrDefault(key, def);
    }

    public String stringParam(String key, String def) {
        return stringParams.getOrDefault(key, def);
    }

    /** True for a passive stat-buff node investable up to several points
     *  (e.g. "Vitality" 0-5), false for the usual single-unlock abilities. */
    public boolean isMultiPoint() {
        return maxPoints > 1;
    }

    /** True for a node available regardless of which of the 3 paths a
     *  player picked (or before they've picked one at all) — the handful
     *  of universal class-wide nodes like the generic stat buffs. False
     *  means this node belongs to exactly one path and is locked out once
     *  the player commits to a different one. */
    public boolean isShared() {
        return path == null || path.isEmpty();
    }
}
