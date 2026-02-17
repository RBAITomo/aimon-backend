package dev.aimon.dto.powermem;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Response from timeline endpoint.
 */
public record TimelineResponse(
    List<TimelineEntry> entries,
    int total,
    @JsonProperty("has_more") boolean hasMore
) {}
