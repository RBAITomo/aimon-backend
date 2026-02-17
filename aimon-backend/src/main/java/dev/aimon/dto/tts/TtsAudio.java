package dev.aimon.dto.tts;

/**
 * Simple holder for synthesized speech audio in PCM16 format.
 */
public record TtsAudio(byte[] pcmData, int sampleRate, int channels) {
    public TtsAudio {
        if (pcmData == null) {
            throw new IllegalArgumentException("pcmData must not be null");
        }
        if (sampleRate <= 0) {
            throw new IllegalArgumentException("sampleRate must be positive");
        }
        if (channels <= 0) {
            throw new IllegalArgumentException("channels must be positive");
        }
    }
}
