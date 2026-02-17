package dev.aimon.model;

/**
 * Badge unlock condition types
 */
public enum BadgeConditionType {
    COUNTER,  // Action count threshold (e.g., 20 correct answers)
    STREAK    // Consecutive days/actions (e.g., 7-day login streak)
}
