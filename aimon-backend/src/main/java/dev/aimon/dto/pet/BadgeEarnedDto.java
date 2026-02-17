package dev.aimon.dto.pet;

/**
 * Badge earned notification DTO.
 * Sent to client when user unlocks a new achievement.
 */
public record BadgeEarnedDto(
    String code,
    String name,
    String description,
    int xpReward
) {}
