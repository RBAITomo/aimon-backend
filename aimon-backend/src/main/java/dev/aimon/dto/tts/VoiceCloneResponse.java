package dev.aimon.dto.tts;

/**
 * Response returned after successfully cloning a voice from reference audio.
 */
public record VoiceCloneResponse(
        String voiceId,
        String status,
        String description,
        int codesCount,
        int latencyMs
) {}
