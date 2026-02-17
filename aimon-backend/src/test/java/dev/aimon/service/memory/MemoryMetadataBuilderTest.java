package dev.aimon.service.memory;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MemoryMetadataBuilder.
 */
class MemoryMetadataBuilderTest {

    @Test
    void builder_includesTimestampByDefault() {
        Map<String, Object> metadata = new MemoryMetadataBuilder().build();

        assertTrue(metadata.containsKey("timestamp"));
        assertNotNull(metadata.get("timestamp"));
    }

    @Test
    void builder_fluent_allFields() {
        Map<String, Object> metadata = new MemoryMetadataBuilder()
            .robotId("robot-1")
            .sessionId("session-1")
            .userId("user-1")
            .category("conversation")
            .type("user_message")
            .importance(0.8)
            .audioFormat("opus")
            .sampleRate(16000)
            .custom("extra_field", "value")
            .build();

        assertEquals("robot-1", metadata.get("robot_id"));
        assertEquals("session-1", metadata.get("session_id"));
        assertEquals("user-1", metadata.get("user_id"));
        assertEquals("conversation", metadata.get("category"));
        assertEquals("user_message", metadata.get("type"));
        assertEquals(0.8, metadata.get("importance"));
        assertEquals("opus", metadata.get("audio_format"));
        assertEquals(16000, metadata.get("sample_rate"));
        assertEquals("value", metadata.get("extra_field"));
    }

    @Test
    void builder_custom_nullValue_ignored() {
        Map<String, Object> metadata = new MemoryMetadataBuilder()
            .custom("key", null)
            .build();

        assertFalse(metadata.containsKey("key"));
    }

    @Test
    void builder_build_returnsDefensiveCopy() {
        MemoryMetadataBuilder builder = new MemoryMetadataBuilder();
        Map<String, Object> metadata1 = builder.build();
        Map<String, Object> metadata2 = builder.build();

        assertNotSame(metadata1, metadata2);
    }
}
