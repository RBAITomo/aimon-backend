package dev.aimon.model;

/**
 * Pet growth stages in the AI-MON lifecycle
 */
public enum PetStage {
    EGG,      // Initial stage, hatches after first interaction
    BABY,     // Levels 1-5, high dependency
    CHILD,    // Levels 6-11, learning phase
    ADULT,    // Level 12+, base form
    VARIANT   // Temporary transformation state
}
