package dev.aimon.client;

import dev.aimon.dto.powermem.*;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.faulttolerance.Fallback;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.eclipse.microprofile.rest.client.annotation.RegisterClientHeaders;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;

/**
 * REST client for PowerMem MCP server.
 *
 * Provides access to the memory storage and retrieval capabilities:
 * - Record observations (conversation turns, events)
 * - Semantic search across observations
 * - Timeline retrieval for context
 * - Batch operations for efficiency
 *
 * Configured via application.properties with key "powermem-api".
 * Includes fault tolerance: retry, timeout, circuit breaker fallbacks.
 */
@Path("/api/v1")
@RegisterRestClient(configKey = "powermem-api")
@RegisterClientHeaders(PowerMemClientHeaderFactory.class)
public interface PowerMemClient {

    /**
     * Create a single observation.
     */
    @POST
    @Path("/observations")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Retry(maxRetries = 2, delay = 100, delayUnit = ChronoUnit.MILLIS, jitter = 50)
    @Timeout(value = 5, unit = ChronoUnit.SECONDS)
    @Fallback(fallbackMethod = "createObservationFallback")
    Observation createObservation(Observation observation);

    /**
     * Batch create observations for efficient bulk recording.
     */
    @POST
    @Path("/observations/batch")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Retry(maxRetries = 2, delay = 100, delayUnit = ChronoUnit.MILLIS, jitter = 50)
    @Timeout(value = 10, unit = ChronoUnit.SECONDS)
    @Fallback(fallbackMethod = "createObservationsBatchFallback")
    ObservationBatchResponse createObservationsBatch(ObservationBatch batch);

    /**
     * Semantic search across observations.
     * Uses vector similarity (cosine distance) for ranking.
     */
    @POST
    @Path("/search")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Retry(maxRetries = 2, delay = 100, delayUnit = ChronoUnit.MILLIS)
    @Timeout(value = 5, unit = ChronoUnit.SECONDS)
    @Fallback(fallbackMethod = "searchFallback")
    SearchResponse search(SearchRequest request);

    /**
     * Get timeline of observations with pagination.
     */
    @POST
    @Path("/timeline")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Retry(maxRetries = 2, delay = 100, delayUnit = ChronoUnit.MILLIS)
    @Timeout(value = 5, unit = ChronoUnit.SECONDS)
    @Fallback(fallbackMethod = "timelineFallback")
    TimelineResponse timeline(TimelineRequest request);

    /**
     * Fetch full observation details by ID.
     */
    @GET
    @Path("/observations/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    @Retry(maxRetries = 2, delay = 100, delayUnit = ChronoUnit.MILLIS)
    @Timeout(value = 5, unit = ChronoUnit.SECONDS)
    @Fallback(fallbackMethod = "getObservationFallback")
    Observation getObservation(@PathParam("id") String id);

    /**
     * Health check endpoint (no auth required on server).
     */
    @GET
    @Path("/health")
    @Produces(MediaType.APPLICATION_JSON)
    @Timeout(value = 3, unit = ChronoUnit.SECONDS)
    HealthResponse health();

    // =========================================================================
    // Fallback Methods - Return safe defaults when MCP service unavailable
    // =========================================================================

    default Observation createObservationFallback(Observation observation) {
        return new Observation("fallback", observation.content(), null, null, observation.metadata());
    }

    default ObservationBatchResponse createObservationsBatchFallback(ObservationBatch batch) {
        return new ObservationBatchResponse(0, Collections.emptyList());
    }

    default SearchResponse searchFallback(SearchRequest request) {
        return new SearchResponse(request.query(), Collections.emptyList(), 0);
    }

    default TimelineResponse timelineFallback(TimelineRequest request) {
        return new TimelineResponse(Collections.emptyList(), 0, false);
    }

    default Observation getObservationFallback(String id) {
        return null;
    }
}
