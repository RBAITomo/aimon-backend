package dev.aimon.service.memory;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.aimon.client.PowerMemClient;
import dev.aimon.dto.powermem.*;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service layer for PowerMem MCP integration.
 *
 * Provides high-level methods for:
 * - Async observation recording (fire-and-forget)
 * - Semantic search with metadata filters (with caching)
 * - Timeline retrieval for context
 * - Batch operations via ObservationBuffer
 *
 * Graceful degradation: returns empty results when MCP unavailable.
 * Includes Caffeine cache for search results with configurable TTL.
 */
@ApplicationScoped
public class PowerMemService {
    private static final Logger log = LoggerFactory.getLogger(PowerMemService.class);

    @Inject
    @RestClient
    PowerMemClient client;

    @Inject
    ObservationBuffer buffer;

    @ConfigProperty(name = "memory.mcp.enabled", defaultValue = "true")
    boolean enabled;

    @ConfigProperty(name = "memory.cache.ttl-minutes", defaultValue = "5")
    int cacheTtlMinutes;

    @ConfigProperty(name = "memory.cache.max-size", defaultValue = "100")
    int cacheMaxSize;

    // Search result cache: key = "query:robotId", value = search results
    private Cache<String, List<SearchResult>> searchCache;

    // Cache statistics for monitoring
    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);

    @PostConstruct
    void initCache() {
        searchCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(cacheTtlMinutes))
            .maximumSize(cacheMaxSize)
            .recordStats()
            .build();
        log.info("Initialized search cache: TTL={}min, maxSize={}", cacheTtlMinutes, cacheMaxSize);
    }

    /**
     * Check if PowerMem is enabled.
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Record observation asynchronously (fire-and-forget).
     * Uses CompletableFuture for non-blocking execution.
     *
     * @param content  The observation content
     * @param metadata Metadata (robot_id, user_id, category, importance, etc.)
     */
    public void recordAsync(String content, Map<String, Object> metadata) {
        if (!enabled) {
            log.trace("PowerMem disabled, skipping record");
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                Observation obs = Observation.create(content, metadata);
                client.createObservation(obs);
                log.debug("Recorded observation: {} chars", content.length());
            } catch (Exception e) {
                log.warn("Failed to record observation (fire-and-forget): {}", e.getMessage());
            }
        });
    }

    /**
     * Add observation to buffer for batched recording.
     * Buffer flushes when threshold (50) is reached.
     *
     * @param content  The observation content
     * @param metadata Metadata
     */
    public void bufferObservation(String content, Map<String, Object> metadata) {
        if (!enabled) {
            return;
        }
        buffer.add(Observation.create(content, metadata));
    }

    /**
     * Force flush the observation buffer.
     */
    public void flushBuffer() {
        if (!enabled) {
            return;
        }
        buffer.flush();
    }

    /**
     * Semantic search across observations.
     * Results are cached with TTL to reduce API calls for repeated queries.
     *
     * @param query   Search query text
     * @param filters Metadata filters (robot_id, category, etc.)
     * @param limit   Max results
     * @return List of search results ordered by relevance
     */
    public List<SearchResult> search(String query, Map<String, Object> filters, int limit) {
        if (!enabled) {
            log.trace("PowerMem disabled, returning empty search results");
            return Collections.emptyList();
        }

        // Generate cache key: query + robotId (if present)
        String robotId = filters != null ? String.valueOf(filters.get("robot_id")) : "all";
        String cacheKey = query + ":" + robotId + ":" + limit;

        // Check cache first
        List<SearchResult> cached = searchCache.getIfPresent(cacheKey);
        if (cached != null) {
            cacheHits.incrementAndGet();
            log.debug("Cache HIT for query '{}': {} results", truncateQuery(query), cached.size());
            return cached;
        }

        // Cache miss - call MCP API
        cacheMisses.incrementAndGet();
        try {
            SearchRequest req = new SearchRequest(query, limit, filters, 0.0, null);
            SearchResponse response = client.search(req);
            List<SearchResult> results = response.results();

            // Store in cache
            searchCache.put(cacheKey, results);
            log.debug("Cache MISS for query '{}': {} results (stored)", truncateQuery(query), response.total());

            return results;
        } catch (Exception e) {
            log.warn("Search failed, returning empty: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Search with default limit of 10.
     */
    public List<SearchResult> search(String query, Map<String, Object> filters) {
        return search(query, filters, 10);
    }

    /**
     * Search without filters.
     */
    public List<SearchResult> search(String query) {
        return search(query, null, 10);
    }

    /**
     * Get timeline of observations for a robot.
     *
     * @param robotId Robot ID to filter by
     * @param limit   Max entries
     * @return Timeline entries ordered by creation time (newest first)
     */
    public List<TimelineEntry> getTimeline(Integer robotId, int limit) {
        if (!enabled) {
            return Collections.emptyList();
        }

        try {
            TimelineRequest req = new TimelineRequest(robotId, null, limit, 0, null);
            TimelineResponse response = client.timeline(req);
            log.debug("Timeline for robot {}: {} entries", robotId, response.total());
            return response.entries();
        } catch (Exception e) {
            log.warn("Timeline failed, returning empty: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Get timeline with default limit.
     */
    public List<TimelineEntry> getTimeline(Integer robotId) {
        return getTimeline(robotId, 20);
    }

    /**
     * Fetch full observation by ID.
     *
     * @param id Observation UUID
     * @return Observation or null if not found
     */
    public Observation getObservation(String id) {
        if (!enabled) {
            return null;
        }

        try {
            return client.getObservation(id);
        } catch (Exception e) {
            log.warn("Get observation {} failed: {}", id, e.getMessage());
            return null;
        }
    }

    /**
     * Check PowerMem MCP server health.
     *
     * @return true if healthy, false otherwise
     */
    public boolean isHealthy() {
        try {
            HealthResponse health = client.health();
            return "healthy".equals(health.status()) && health.databaseConnected();
        } catch (Exception e) {
            log.warn("Health check failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Create batch of observations directly (bypassing buffer).
     *
     * @param observations List of observations to create
     * @return Response with created count and IDs
     */
    public ObservationBatchResponse createBatch(List<Observation> observations) {
        if (!enabled || observations.isEmpty()) {
            return new ObservationBatchResponse(0, Collections.emptyList());
        }

        try {
            ObservationBatch batch = ObservationBatch.of(observations);
            return client.createObservationsBatch(batch);
        } catch (Exception e) {
            log.error("Batch creation failed: {}", e.getMessage());
            return new ObservationBatchResponse(0, Collections.emptyList());
        }
    }

    /**
     * Record pet care action to memory.
     * Used by pet system to track feeding, chatting, etc.
     *
     * @param robotId Robot/user ID
     * @param action Action type (feed, chat, rest, quest, etc.)
     * @param detail Detailed description
     */
    public void recordPetCare(Integer robotId, String action, String detail) {
        if (!enabled || robotId == null) {
            return;
        }

        recordAsync(
            detail,
            Map.of(
                "robot_id", robotId,
                "category", "pet_care",
                "action", action,
                "importance", 0.3
            )
        );
    }

    /**
     * Record achievement to memory.
     * Used when user earns badges or completes milestones.
     *
     * @param robotId Robot/user ID
     * @param achievement Achievement description
     */
    public void recordAchievement(Integer robotId, String achievement) {
        if (!enabled || robotId == null) {
            return;
        }

        recordAsync(
            achievement,
            Map.of(
                "robot_id", robotId,
                "category", "achievement",
                "importance", 0.8
            )
        );
    }

    /**
     * Record milestone to memory.
     * Used for significant events like evolution, transformation, etc.
     *
     * @param robotId Robot/user ID
     * @param milestone Milestone description
     */
    public void recordMilestone(Integer robotId, String milestone) {
        if (!enabled || robotId == null) {
            return;
        }

        recordAsync(
            milestone,
            Map.of(
                "robot_id", robotId,
                "category", "milestone",
                "importance", 0.9
            )
        );
    }

    /**
     * Clear the search cache.
     * Useful when observations are updated and cache may be stale.
     */
    public void clearSearchCache() {
        if (searchCache != null) {
            searchCache.invalidateAll();
            log.info("Search cache cleared");
        }
    }

    /**
     * Get cache statistics for monitoring.
     *
     * @return Cache stats record
     */
    public CacheStats getCacheStats() {
        long hits = cacheHits.get();
        long misses = cacheMisses.get();
        long total = hits + misses;
        double hitRate = total > 0 ? (double) hits / total : 0.0;

        return new CacheStats(
            hits,
            misses,
            hitRate,
            searchCache != null ? searchCache.estimatedSize() : 0
        );
    }

    /**
     * Truncate query for logging (avoid long messages in logs).
     */
    private String truncateQuery(String query) {
        if (query == null) return "";
        return query.length() > 50 ? query.substring(0, 50) + "..." : query;
    }

    /**
     * Cache statistics record.
     */
    public record CacheStats(
        long hits,
        long misses,
        double hitRate,
        long estimatedSize
    ) {}
}
