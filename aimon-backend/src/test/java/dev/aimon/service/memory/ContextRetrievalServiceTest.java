package dev.aimon.service.memory;

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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ContextRetrievalService.
 * Tests the 3-layer memory retrieval pipeline.
 */
@ExtendWith(MockitoExtension.class)
class ContextRetrievalServiceTest {

    @Mock
    PowerMemService memoryService;

    @InjectMocks
    ContextRetrievalService service;

    @BeforeEach
    void setUp() throws Exception {
        setField(service, "maxTokens", 150);
        setField(service, "searchLimit", 10);
        setField(service, "timelineDepthBefore", 5);
        setField(service, "timelineDepthAfter", 3);
        setField(service, "importanceThreshold", 0.5);
    }

    @Test
    void retrieveContext_memoryDisabled_returnsEmpty() {
        when(memoryService.isEnabled()).thenReturn(false);

        String result = service.retrieveContextForConversation("hello", 1);

        assertEquals("", result);
    }

    @Test
    void retrieveContext_noSearchResults_returnsEmpty() {
        when(memoryService.isEnabled()).thenReturn(true);
        when(memoryService.search(anyString(), anyMap(), anyInt())).thenReturn(Collections.emptyList());

        String result = service.retrieveContextForConversation("hello", 1);

        assertEquals("", result);
    }

    @Test
    void retrieveContext_resultsBelowThreshold_returnsEmpty() {
        when(memoryService.isEnabled()).thenReturn(true);
        SearchResult lowScore = new SearchResult("id1", "content", 0.1, Instant.now(), Map.of());
        when(memoryService.search(anyString(), anyMap(), anyInt())).thenReturn(List.of(lowScore));

        String result = service.retrieveContextForConversation("hello", 1);

        assertEquals("", result);
    }

    @Test
    void retrieveContext_withResults_returnsFormattedContext() {
        when(memoryService.isEnabled()).thenReturn(true);

        SearchResult highScore = new SearchResult("id1", "User likes cats", 0.9, Instant.now(), Map.of());
        when(memoryService.search(anyString(), anyMap(), anyInt())).thenReturn(List.of(highScore));

        // Timeline returns entries
        TimelineEntry entry = new TimelineEntry("id1", "User likes cats", Instant.now(), "conversation", 0.9);
        when(memoryService.getTimeline(anyInt(), anyInt())).thenReturn(List.of(entry));

        // Observation fetch
        Observation obs = new Observation("id1", "User likes cats very much", Instant.now(), null, Map.of());
        when(memoryService.getObservation("id1")).thenReturn(obs);

        String result = service.retrieveContextForConversation("Tell me about pets", 1);

        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertTrue(result.contains("Memory") || result.contains("remember"));
    }

    @Test
    void retrieveContext_nullRobotId_doesNotFilter() {
        when(memoryService.isEnabled()).thenReturn(true);
        when(memoryService.search(anyString(), anyMap(), anyInt())).thenReturn(Collections.emptyList());

        String result = service.retrieveContextForConversation("hello", null);

        assertEquals("", result);
        // Should still call search without robot_id filter throwing
        verify(memoryService).search(eq("hello"), argThat(map -> !map.containsKey("robot_id")), eq(10));
    }

    @Test
    void retrieveContext_searchException_returnsEmpty() {
        when(memoryService.isEnabled()).thenReturn(true);
        when(memoryService.search(anyString(), anyMap(), anyInt())).thenThrow(new RuntimeException("Error"));

        String result = service.retrieveContextForConversation("hello", 1);

        assertEquals("", result);
    }

    @Test
    void retrieveContext_multipleResults_takesTop3() {
        when(memoryService.isEnabled()).thenReturn(true);

        List<SearchResult> results = List.of(
            new SearchResult("id1", "content1", 0.9, Instant.now(), Map.of()),
            new SearchResult("id2", "content2", 0.8, Instant.now(), Map.of()),
            new SearchResult("id3", "content3", 0.7, Instant.now(), Map.of()),
            new SearchResult("id4", "content4", 0.6, Instant.now(), Map.of())
        );
        when(memoryService.search(anyString(), anyMap(), anyInt())).thenReturn(results);
        when(memoryService.getTimeline(anyInt(), anyInt())).thenReturn(Collections.emptyList());
        when(memoryService.getObservation(anyString())).thenReturn(null);

        String result = service.retrieveContextForConversation("hello", 1);

        // Should fetch observations for top 3 results only
        verify(memoryService, times(3)).getObservation(anyString());
    }

    @Test
    void retrieveContext_timelineFailure_continuesWithSearchResults() {
        when(memoryService.isEnabled()).thenReturn(true);

        SearchResult result1 = new SearchResult("id1", "content", 0.9, Instant.now(), Map.of());
        when(memoryService.search(anyString(), anyMap(), anyInt())).thenReturn(List.of(result1));
        when(memoryService.getTimeline(anyInt(), anyInt())).thenThrow(new RuntimeException("Timeline error"));
        when(memoryService.getObservation("id1")).thenReturn(null);

        String result = service.retrieveContextForConversation("hello", 1);

        // Should still return something from search results
        assertNotNull(result);
    }

    @Test
    void retrieveContextAsync_returnsCompletableFuture() {
        when(memoryService.isEnabled()).thenReturn(false);

        var future = service.retrieveContextAsync("hello", 1);

        assertNotNull(future);
        assertEquals("", future.join());
    }

    @Test
    void retrieveContext_nullScoreResults_filteredOut() {
        when(memoryService.isEnabled()).thenReturn(true);

        SearchResult nullScore = new SearchResult("id1", "content", null, Instant.now(), Map.of());
        when(memoryService.search(anyString(), anyMap(), anyInt())).thenReturn(List.of(nullScore));

        String result = service.retrieveContextForConversation("hello", 1);

        // Null score < 0.5 threshold, so filtered out
        assertEquals("", result);
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
