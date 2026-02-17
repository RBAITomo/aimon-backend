package dev.aimon.model;

/**
 * CDI event fired when user performs a pet-related action.
 * Used by BadgeService to track progress toward achievement badges.
 */
public record PetActionEvent(Long userId, String actionType, int amount) {}
