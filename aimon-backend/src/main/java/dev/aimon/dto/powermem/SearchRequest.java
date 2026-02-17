package dev.aimon.dto.powermem;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/**
 * Request for semantic search on observations.
 */
public record SearchRequest(
    String query,
    Integer limit,
    @JsonProperty("metadata_filters") Map<String, Object> metadataFilters,
    @JsonProperty("importance_threshold") Double importanceThreshold,
    @JsonProperty("time_range_hours") Integer timeRangeHours
) {
    /**
     * Simple query with defaults.
     */
    public SearchRequest(String query) {
        this(query, 10, null, 0.0, null);
    }

    /**
     * Query with filters.
     */
    public SearchRequest(String query, Map<String, Object> filters) {
        this(query, 10, filters, 0.0, null);
    }
}
