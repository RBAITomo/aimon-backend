package dev.aimon.dto.pet;

/**
 * Quest data sent to client for display. expectedAnswer included for backend grading only.
 */
public record QuestDto(
    String code,
    String questionText,
    String category,
    String difficulty,
    String expectedAnswer,
    String hint
) {}
