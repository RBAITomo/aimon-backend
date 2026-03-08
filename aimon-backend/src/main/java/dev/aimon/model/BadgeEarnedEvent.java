package dev.aimon.model;

/**
 * CDI event fired when user earns a new badge.
 * Can be consumed by UI notification services or analytics.
 */
public record BadgeEarnedEvent(Long userId, String badgeCode, String badgeName, String badgeDescription, int xpReward) {}
