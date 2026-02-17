package dev.aimon.model;

/**
 * CDI event fired when pet transforms to variant form.
 * Used for updating UI and applying temporary persona changes.
 */
public record PetTransformEvent(Long userId, String variantCode, int durationMinutes) {}
