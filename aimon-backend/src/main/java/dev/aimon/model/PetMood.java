package dev.aimon.model;

/**
 * Pet emotional states derived from stat values
 */
public enum PetMood {
    HUNGRY,   // hunger < 30
    SLEEPY,   // energy < 30
    JOYFUL,   // happiness > 80
    SAD,      // happiness < 30
    NEUTRAL,  // Default balanced state
    CONTENT   // All stats healthy
}
