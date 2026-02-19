package dev.aimon.dto.tts;

/**
 * Metadata for a single available TTS voice (preset or custom cloned).
 */
public record VoiceInfo(
        String id,
        String name,
        String gender,
        String region,
        String type   // "preset" or "custom"
) {}
