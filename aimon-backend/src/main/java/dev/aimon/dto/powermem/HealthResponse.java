package dev.aimon.dto.powermem;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Health check response from PowerMem server.
 */
public record HealthResponse(
    String status,
    String version,
    @JsonProperty("embedding_model") String embeddingModel,
    @JsonProperty("embedding_dimension") int embeddingDimension,
    @JsonProperty("database_connected") boolean databaseConnected,
    @JsonProperty("pool_size") Integer poolSize
) {}
