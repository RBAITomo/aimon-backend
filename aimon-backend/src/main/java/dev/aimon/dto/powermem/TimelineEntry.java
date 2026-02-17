package dev.aimon.dto.powermem;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

/**
 * Single timeline entry (observation summary).
 */
public record TimelineEntry(
    String id,
    @JsonProperty("content_preview") String contentPreview,
    @JsonProperty("created_at") Instant createdAt,
    String category,
    Double importance
) {}
