package com.warriorssmp.classes.model;

/** The shared Level -> Total XP curve used across every WarriorsSMP skill
 *  plugin. Index 0 = level 1 (0 XP). */
public final class XpTable {

    private XpTable() {}

    public static final long[] TOTAL_XP_FOR_LEVEL = new long[]{
            0, 83, 174, 276, 388, 511, 648, 799, 966,
            1151, 1354, 1580, 1828, 2101, 2405, 2739, 3107, 3514, 3962,
            4458, 5005, 5609, 6274, 7009,
            7821, 8716, 9703, 10795, 11998, 13328, 14804, 16445, 18273, 20312, 22584, 24748, 27400, 30327, 33559,
            37126, 41074, 45422, 50221, 55518, 61349, 67803, 74928, 82794, 91478, 101065, 111649, 123314, 136247, 150588,
            166196, 183553, 202768, 223872, 247231, 273019, 301519, 332922, 367625, 405939, 448240, 495026, 546504, 603433, 666288,
            735674, 812287, 896876, 990257, 1093366, 1207217, 1332942, 1471715, 1624903, 1793886, 1980823, 2187019, 2414692, 2665353, 2942789,
            3248980, 3587166, 3960536, 4372289, 4827036, 5329924, 5884522, 6500000, 7176758, 7923780, 8750046, 9661641, 10666150, 11775485,
            13000000
    };

    public static final int MAX_LEVEL = 99;

    public static long xpForLevel(int level) {
        int idx = Math.max(1, Math.min(level, MAX_LEVEL)) - 1;
        return TOTAL_XP_FOR_LEVEL[idx];
    }

    public static long xpForNextLevel(int level) {
        if (level >= MAX_LEVEL) return TOTAL_XP_FOR_LEVEL[MAX_LEVEL - 1];
        return TOTAL_XP_FOR_LEVEL[level];
    }

    public static int levelForXp(long totalXp) {
        int level = 1;
        for (int i = 0; i < TOTAL_XP_FOR_LEVEL.length; i++) {
            if (totalXp >= TOTAL_XP_FOR_LEVEL[i]) {
                level = i + 1;
            } else {
                break;
            }
        }
        return level;
    }
}
