package dev.aimon.service.pet;

import dev.aimon.model.PetStage;

/**
 * Static configuration for pet level progression and stage thresholds.
 * No CDI needed - pure utility class.
 */
public class PetLevelConfig {

    // XP requirements per level (cumulative)
    private static final long[] XP_THRESHOLDS = {
        0,      // Level 1
        50,     // Level 2
        150,    // Level 3
        350,    // Level 4
        550,    // Level 5
        1050,   // Level 6
        1550,   // Level 7
        2550,   // Level 8
        3550,   // Level 9
        5050,   // Level 10
        7050,   // Level 11
        10050,  // Level 12
        14050,  // Level 13
        19050,  // Level 14
        25050   // Level 15 (max)
    };

    private static final int MAX_LEVEL = 15;

    /**
     * Get required total XP for a specific level.
     */
    public static long getRequiredXp(int level) {
        if (level < 1) return 0;
        if (level > MAX_LEVEL) return XP_THRESHOLDS[MAX_LEVEL - 1];
        return XP_THRESHOLDS[level - 1];
    }

    /**
     * Calculate current level from total XP.
     */
    public static int calculateLevel(long totalXp) {
        for (int level = MAX_LEVEL; level >= 1; level--) {
            if (totalXp >= getRequiredXp(level)) {
                return level;
            }
        }
        return 1;
    }

    /**
     * Get stage for a given level.
     * Level 1: EGG, 2-6: BABY, 7-11: CHILD, 12+: ADULT
     */
    public static PetStage getStageForLevel(int level) {
        if (level <= 1) return PetStage.EGG;
        if (level <= 6) return PetStage.BABY;
        if (level <= 11) return PetStage.CHILD;
        return PetStage.ADULT;
    }
}
