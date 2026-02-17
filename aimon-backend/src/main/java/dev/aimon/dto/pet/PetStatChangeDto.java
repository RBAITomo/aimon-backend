package dev.aimon.dto.pet;

/**
 * Pet stat change result from actions.
 * Shows deltas and new values for client animation.
 */
public record PetStatChangeDto(
    int hungerDelta,
    int energyDelta,
    int happinessDelta,
    int hunger,
    int energy,
    int happiness,
    int xpGained
) {}
