package dev.aimon.dto.powermem;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.Map;

/**
 * PowerMem observation record.
 * Represents a memory observation stored in the MCP server.
 */
public record Observation(
    String id,
    String content,
    @JsonProperty("created_at") Instant createdAt,
    @JsonProperty("updated_at") Instant updatedAt,
    Map<String, Object> metadata
) {
    /**
     * Factory method to create a new observation for recording.
     */
    public static Observation create(String content, Map<String, Object> metadata) {
        return new Observation(null, content, null, null, metadata);
    }
}
