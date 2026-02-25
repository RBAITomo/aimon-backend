package dev.aimon.dto.pet;

/**
 * Pet status snapshot for client display.
 * Contains current stats, progression, and state.
 */
public record PetStatusDto(
    String name,
    String stage,
    String variant,
    String mood,
    int hunger,
    int energy,
    int happiness,
    int level,
    long xp,
    long xpForNext,
    int affinity,
    int loginStreak,
    String pendingQuestText,
    String pendingQuestCategory,
    String pendingQuestDifficulty,
    String background
) {}
