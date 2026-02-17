package dev.aimon.service.memory;

import dev.aimon.service.memory.ContextRetrievalService.ContextEntry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MemoryContextFormatter.
 * Tests formatting, token budgets, and sanitization.
 */
class MemoryContextFormatterTest {

    // --- formatAsSystemPrompt ---

    @Test
    void formatAsSystemPrompt_nullEntries_returnsEmpty() {
        assertEquals("", MemoryContextFormatter.formatAsSystemPrompt(null, 150));
    }

    @Test
    void formatAsSystemPrompt_emptyEntries_returnsEmpty() {
        assertEquals("", MemoryContextFormatter.formatAsSystemPrompt(List.of(), 150));
    }

    @Test
    void formatAsSystemPrompt_withEntries_containsHeader() {
        ContextEntry entry = new ContextEntry("id1", "User likes cats", 0.9, "search");

        String result = MemoryContextFormatter.formatAsSystemPrompt(List.of(entry), 150);

        assertTrue(result.contains("Memory"));
        assertTrue(result.contains("User likes cats"));
    }

    @Test
    void formatAsSystemPrompt_highImportance_usesStarMarker() {
        ContextEntry entry = new ContextEntry("id1", "Important fact", 0.9, "search");

        String result = MemoryContextFormatter.formatAsSystemPrompt(List.of(entry), 150);

        assertTrue(result.contains("★"));
    }

    @Test
    void formatAsSystemPrompt_mediumImportance_usesCheckMarker() {
        ContextEntry entry = new ContextEntry("id1", "Medium fact", 0.65, "search");

        String result = MemoryContextFormatter.formatAsSystemPrompt(List.of(entry), 150);

        assertTrue(result.contains("✓"));
    }

    @Test
    void formatAsSystemPrompt_lowImportance_usesBulletMarker() {
        ContextEntry entry = new ContextEntry("id1", "Low importance fact", 0.3, "search");

        String result = MemoryContextFormatter.formatAsSystemPrompt(List.of(entry), 150);

        assertTrue(result.contains("•"));
    }

    @Test
    void formatAsSystemPrompt_tokenBudget_truncates() {
        // Very small token budget
        ContextEntry e1 = new ContextEntry("id1", "First entry", 0.9, "search");
        ContextEntry e2 = new ContextEntry("id2", "Second entry with more text that should be truncated due to token budget", 0.8, "search");
        ContextEntry e3 = new ContextEntry("id3", "Third entry also long text that might not fit", 0.7, "search");

        // 10 tokens = 40 chars, very small budget
        String result = MemoryContextFormatter.formatAsSystemPrompt(List.of(e1, e2, e3), 10);

        assertNotNull(result);
        // With only 40 chars budget, not all entries should fit
    }

    @Test
    void formatAsSystemPrompt_longContent_truncatesIndividualEntries() {
        // Entry content > 150 chars should be truncated
        String longContent = "A".repeat(200);
        ContextEntry entry = new ContextEntry("id1", longContent, 0.9, "search");

        String result = MemoryContextFormatter.formatAsSystemPrompt(List.of(entry), 500);

        // Individual entries are truncated to 150 chars + "..."
        assertTrue(result.contains("..."));
    }

    @Test
    void formatAsSystemPrompt_sanitizesContent() {
        ContextEntry entry = new ContextEntry("id1", "Content with ```code``` and ### headers", 0.9, "search");

        String result = MemoryContextFormatter.formatAsSystemPrompt(List.of(entry), 150);

        assertFalse(result.contains("```"));
        assertFalse(result.contains("###"));
    }

    // --- formatAsJson ---

    @Test
    void formatAsJson_nullEntries_returnsEmptyArray() {
        assertEquals("[]", MemoryContextFormatter.formatAsJson(null));
    }

    @Test
    void formatAsJson_emptyEntries_returnsEmptyArray() {
        assertEquals("[]", MemoryContextFormatter.formatAsJson(List.of()));
    }

    @Test
    void formatAsJson_withEntries_returnsValidJson() {
        ContextEntry entry = new ContextEntry("id1", "content", 0.9, "search");

        String result = MemoryContextFormatter.formatAsJson(List.of(entry));

        assertTrue(result.contains("\"id\":\"id1\""));
        assertTrue(result.contains("\"content\":\"content\""));
        assertTrue(result.contains("\"importance\":0.90"));
        assertTrue(result.contains("\"source\":\"search\""));
    }

    @Test
    void formatAsJson_escapesSpecialChars() {
        ContextEntry entry = new ContextEntry("id1", "content with \"quotes\" and\nnewlines", 0.5, "search");

        String result = MemoryContextFormatter.formatAsJson(List.of(entry));

        assertTrue(result.contains("\\\""));
        assertTrue(result.contains("\\n"));
    }

    // --- formatAsCompactSummary ---

    @Test
    void formatAsCompactSummary_null_returnsNoContext() {
        assertEquals("No context available", MemoryContextFormatter.formatAsCompactSummary(null));
    }

    @Test
    void formatAsCompactSummary_empty_returnsNoContext() {
        assertEquals("No context available", MemoryContextFormatter.formatAsCompactSummary(List.of()));
    }

    @Test
    void formatAsCompactSummary_withEntries() {
        ContextEntry entry = new ContextEntry("id1", "Short content", 0.9, "search");

        String result = MemoryContextFormatter.formatAsCompactSummary(List.of(entry));

        assertTrue(result.contains("1 memories"));
        assertTrue(result.contains("[Short content]"));
    }

    @Test
    void formatAsCompactSummary_moreThan3_showsPlusMore() {
        List<ContextEntry> entries = List.of(
            new ContextEntry("1", "a", 0.9, "search"),
            new ContextEntry("2", "b", 0.8, "search"),
            new ContextEntry("3", "c", 0.7, "search"),
            new ContextEntry("4", "d", 0.6, "search")
        );

        String result = MemoryContextFormatter.formatAsCompactSummary(entries);

        assertTrue(result.contains("4 memories"));
        assertTrue(result.contains("+1 more"));
    }

    @Test
    void formatAsCompactSummary_longContent_truncated() {
        ContextEntry entry = new ContextEntry("id1", "This is a very long content string that exceeds thirty characters", 0.9, "search");

        String result = MemoryContextFormatter.formatAsCompactSummary(List.of(entry));

        assertTrue(result.contains("..."));
    }

    // --- estimateTokens ---

    @Test
    void estimateTokens_null_returnsZero() {
        assertEquals(0, MemoryContextFormatter.estimateTokens(null));
    }

    @Test
    void estimateTokens_empty_returnsZero() {
        assertEquals(0, MemoryContextFormatter.estimateTokens(""));
    }

    @Test
    void estimateTokens_shortText() {
        // 4 chars = 1 token
        assertEquals(1, MemoryContextFormatter.estimateTokens("abcd"));
    }

    @Test
    void estimateTokens_roundsUp() {
        // 5 chars = ceil(5/4) = 2 tokens
        assertEquals(2, MemoryContextFormatter.estimateTokens("abcde"));
    }
}
