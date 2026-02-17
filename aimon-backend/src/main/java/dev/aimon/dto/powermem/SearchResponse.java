package dev.aimon.dto.powermem;

import java.util.List;

/**
 * Response from search endpoint.
 */
public record SearchResponse(String query, List<SearchResult> results, int total) {}
