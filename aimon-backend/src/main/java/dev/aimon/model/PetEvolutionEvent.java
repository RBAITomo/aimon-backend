package dev.aimon.model;

/**
 * CDI event fired when pet evolves to next stage.
 * Used for triggering UI animations and celebration messages.
 */
public record PetEvolutionEvent(Long userId, String oldStage, String newStage) {}
