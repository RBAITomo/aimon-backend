package dev.aimon.dto.powermem;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.Map;

/**
 * Single search result with similarity score.
 */
public record SearchResult(
    String id,
    String content,
    Double score,
    @JsonProperty("created_at") Instant createdAt,
    Map<String, Object> metadata
) {}
