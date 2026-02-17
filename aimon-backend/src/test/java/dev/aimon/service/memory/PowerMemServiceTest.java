package dev.aimon.service.memory;

import dev.aimon.client.PowerMemClient;
import dev.aimon.dto.powermem.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PowerMemService.
 * Mocks PowerMemClient to avoid real API calls.
 */
@ExtendWith(MockitoExtension.class)
class PowerMemServiceTest {

    @Mock
    PowerMemClient client;

    @Mock
    ObservationBuffer buffer;

    @InjectMocks
    PowerMemService service;

    @BeforeEach
    void setUp() throws Exception {
        // Set config properties via reflection
        setField(service, "enabled", true);
        setField(service, "cacheTtlMinutes", 5);
        setField(service, "cacheMaxSize", 100);
        // Initialize cache (normally done by @PostConstruct)
        service.initCache();
    }

    // --- isEnabled ---

    @Test
    void isEnabled_returnsConfiguredValue() {
        assertTrue(service.isEnabled());
    }

    // --- search ---

    @Test
    void search_disabled_returnsEmpty() throws Exception {
        setField(service, "enabled", false);

        List<SearchResult> results = service.search("query", null, 10);

        assertTrue(results.isEmpty());
        verifyNoInteractions(client);
    }

    @Test
    void search_success_returnsResults() {
        SearchResult result = new SearchResult("id1", "content", 0.9, Instant.now(), Map.of());
        SearchResponse response = new SearchResponse("query", List.of(result), 1);
        when(client.search(any(SearchRequest.class))).thenReturn(response);

        List<SearchResult> results = service.search("query", null, 10);

        assertEquals(1, results.size());
        assertEquals("id1", results.get(0).id());
    }

    @Test
    void search_cachesResults() {
        SearchResult result = new SearchResult("id1", "content", 0.9, Instant.now(), Map.of());
        SearchResponse response = new SearchResponse("query", List.of(result), 1);
        when(client.search(any(SearchRequest.class))).thenReturn(response);

        // First call - cache miss
        service.search("query", null, 10);
        // Second call - cache hit
        service.search("query", null, 10);

        // Client should only be called once
        verify(client, times(1)).search(any(SearchRequest.class));
    }

    @Test
    void search_exception_returnsEmpty() {
        when(client.search(any(SearchRequest.class))).thenThrow(new RuntimeException("API error"));

        List<SearchResult> results = service.search("query", null, 10);

        assertTrue(results.isEmpty());
    }

    @Test
    void search_withFilters_includesRobotIdInCacheKey() {
        SearchResponse response = new SearchResponse("query", List.of(), 0);
        when(client.search(any(SearchRequest.class))).thenReturn(response);

        service.search("query", Map.of("robot_id", 1), 10);
        service.search("query", Map.of("robot_id", 2), 10);

        // Different robot_id = different cache key = 2 calls
        verify(client, times(2)).search(any(SearchRequest.class));
    }

    @Test
    void search_defaultLimit() {
        SearchResponse response = new SearchResponse("q", List.of(), 0);
        when(client.search(any(SearchRequest.class))).thenReturn(response);

        service.search("q", Map.of("robot_id", 1));

        verify(client).search(any(SearchRequest.class));
    }

    @Test
    void search_noFilters() {
        SearchResponse response = new SearchResponse("q", List.of(), 0);
        when(client.search(any(SearchRequest.class))).thenReturn(response);

        service.search("q");

        verify(client).search(any(SearchRequest.class));
    }

    // --- getTimeline ---

    @Test
    void getTimeline_disabled_returnsEmpty() throws Exception {
        setField(service, "enabled", false);

        List<TimelineEntry> entries = service.getTimeline(1, 20);

        assertTrue(entries.isEmpty());
        verifyNoInteractions(client);
    }

    @Test
    void getTimeline_success_returnsEntries() {
        TimelineEntry entry = new TimelineEntry("id1", "preview", Instant.now(), "conversation", 0.5);
        TimelineResponse response = new TimelineResponse(List.of(entry), 1, false);
        when(client.timeline(any(TimelineRequest.class))).thenReturn(response);

        List<TimelineEntry> entries = service.getTimeline(1, 20);

        assertEquals(1, entries.size());
        assertEquals("id1", entries.get(0).id());
    }

    @Test
    void getTimeline_exception_returnsEmpty() {
        when(client.timeline(any(TimelineRequest.class))).thenThrow(new RuntimeException("Error"));

        List<TimelineEntry> entries = service.getTimeline(1, 20);

        assertTrue(entries.isEmpty());
    }

    @Test
    void getTimeline_defaultLimit() {
        TimelineResponse response = new TimelineResponse(List.of(), 0, false);
        when(client.timeline(any(TimelineRequest.class))).thenReturn(response);

        service.getTimeline(1);

        verify(client).timeline(any(TimelineRequest.class));
    }

    // --- getObservation ---

    @Test
    void getObservation_disabled_returnsNull() throws Exception {
        setField(service, "enabled", false);

        assertNull(service.getObservation("id1"));
    }

    @Test
    void getObservation_success() {
        Observation obs = new Observation("id1", "content", Instant.now(), null, Map.of());
        when(client.getObservation("id1")).thenReturn(obs);

        Observation result = service.getObservation("id1");

        assertNotNull(result);
        assertEquals("id1", result.id());
    }

    @Test
    void getObservation_exception_returnsNull() {
        when(client.getObservation("id1")).thenThrow(new RuntimeException("Error"));

        assertNull(service.getObservation("id1"));
    }

    // --- isHealthy ---

    @Test
    void isHealthy_healthyResponse_returnsTrue() {
        HealthResponse health = new HealthResponse("healthy", "1.0", "model", 384, true, 5);
        when(client.health()).thenReturn(health);

        assertTrue(service.isHealthy());
    }

    @Test
    void isHealthy_unhealthyStatus_returnsFalse() {
        HealthResponse health = new HealthResponse("degraded", "1.0", "model", 384, true, 5);
        when(client.health()).thenReturn(health);

        assertFalse(service.isHealthy());
    }

    @Test
    void isHealthy_dbDisconnected_returnsFalse() {
        HealthResponse health = new HealthResponse("healthy", "1.0", "model", 384, false, 5);
        when(client.health()).thenReturn(health);

        assertFalse(service.isHealthy());
    }

    @Test
    void isHealthy_exception_returnsFalse() {
        when(client.health()).thenThrow(new RuntimeException("Connection refused"));

        assertFalse(service.isHealthy());
    }

    // --- createBatch ---

    @Test
    void createBatch_disabled_returnsEmptyResponse() throws Exception {
        setField(service, "enabled", false);

        ObservationBatchResponse response = service.createBatch(List.of());
        assertEquals(0, response.created());
    }

    @Test
    void createBatch_emptyList_returnsEmptyResponse() {
        ObservationBatchResponse response = service.createBatch(List.of());
        assertEquals(0, response.created());
        verifyNoInteractions(client);
    }

    @Test
    void createBatch_success() {
        Observation obs = Observation.create("content", Map.of());
        ObservationBatchResponse expected = new ObservationBatchResponse(1, List.of("id1"));
        when(client.createObservationsBatch(any(ObservationBatch.class))).thenReturn(expected);

        ObservationBatchResponse result = service.createBatch(List.of(obs));

        assertEquals(1, result.created());
    }

    @Test
    void createBatch_exception_returnsEmptyResponse() {
        Observation obs = Observation.create("content", Map.of());
        when(client.createObservationsBatch(any(ObservationBatch.class)))
            .thenThrow(new RuntimeException("Error"));

        ObservationBatchResponse result = service.createBatch(List.of(obs));

        assertEquals(0, result.created());
    }

    // --- bufferObservation ---

    @Test
    void bufferObservation_disabled_doesNotBuffer() throws Exception {
        setField(service, "enabled", false);

        service.bufferObservation("content", Map.of());

        verifyNoInteractions(buffer);
    }

    @Test
    void bufferObservation_enabled_addsToBuffer() {
        service.bufferObservation("content", Map.of());

        verify(buffer).add(any(Observation.class));
    }

    // --- flushBuffer ---

    @Test
    void flushBuffer_disabled_doesNotFlush() throws Exception {
        setField(service, "enabled", false);

        service.flushBuffer();

        verifyNoInteractions(buffer);
    }

    @Test
    void flushBuffer_enabled_flushesBuffer() {
        service.flushBuffer();

        verify(buffer).flush();
    }

    // --- clearSearchCache ---

    @Test
    void clearSearchCache_doesNotThrow() {
        assertDoesNotThrow(() -> service.clearSearchCache());
    }

    // --- getCacheStats ---

    @Test
    void getCacheStats_initialValues() {
        PowerMemService.CacheStats stats = service.getCacheStats();

        assertEquals(0, stats.hits());
        assertEquals(0, stats.misses());
        assertEquals(0.0, stats.hitRate());
    }

    @Test
    void getCacheStats_afterSearches() {
        SearchResponse response = new SearchResponse("q", List.of(), 0);
        when(client.search(any(SearchRequest.class))).thenReturn(response);

        service.search("q", null, 10); // miss
        service.search("q", null, 10); // hit

        PowerMemService.CacheStats stats = service.getCacheStats();
        assertEquals(1, stats.hits());
        assertEquals(1, stats.misses());
        assertEquals(0.5, stats.hitRate(), 0.01);
    }

    // --- recordAsync ---

    @Test
    void recordAsync_disabled_doesNotCallClient() throws Exception {
        setField(service, "enabled", false);

        service.recordAsync("content", Map.of());

        verifyNoInteractions(client);
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
