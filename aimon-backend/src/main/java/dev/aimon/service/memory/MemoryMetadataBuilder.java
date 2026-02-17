package dev.aimon.service.memory;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Fluent builder for PowerMem observation metadata.
 * Provides type-safe construction of metadata maps for memory recording.
 */
public class MemoryMetadataBuilder {
    private final Map<String, Object> metadata = new HashMap<>();

    public MemoryMetadataBuilder() {
        metadata.put("timestamp", Instant.now().toString());
    }

    public MemoryMetadataBuilder robotId(String robotId) {
        metadata.put("robot_id", robotId);
        return this;
    }

    public MemoryMetadataBuilder sessionId(String sessionId) {
        metadata.put("session_id", sessionId);
        return this;
    }

    public MemoryMetadataBuilder userId(String userId) {
        metadata.put("user_id", userId);
        return this;
    }

    public MemoryMetadataBuilder category(String category) {
        metadata.put("category", category);
        return this;
    }

    public MemoryMetadataBuilder type(String type) {
        metadata.put("type", type);
        return this;
    }

    public MemoryMetadataBuilder importance(double importance) {
        metadata.put("importance", importance);
        return this;
    }

    public MemoryMetadataBuilder audioFormat(String format) {
        metadata.put("audio_format", format);
        return this;
    }

    public MemoryMetadataBuilder sampleRate(int sampleRate) {
        metadata.put("sample_rate", sampleRate);
        return this;
    }

    public MemoryMetadataBuilder custom(String key, Object value) {
        if (value != null) {
            metadata.put(key, value);
        }
        return this;
    }

    public Map<String, Object> build() {
        return new HashMap<>(metadata);
    }
}
