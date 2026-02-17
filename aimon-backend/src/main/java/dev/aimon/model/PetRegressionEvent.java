package dev.aimon.model;

/**
 * CDI event fired when pet regresses to EGG due to neglect.
 * Triggers warning messages and stat resets.
 */
public record PetRegressionEvent(Long userId) {}
