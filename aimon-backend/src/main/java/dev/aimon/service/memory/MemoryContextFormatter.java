package dev.aimon.service.memory;

import dev.aimon.service.memory.ContextRetrievalService.ContextEntry;
import java.util.List;

/**
 * Memory Context Formatter for LLM prompt injection.
 *
 * Formats retrieved memory observations into structured text for LLM prompts.
 * Handles token budget enforcement (~4 chars = 1 token approximation).
 *
 * Output formats:
 * - System prompt format: Markdown-style bullet list for LLM context
 * - JSON format: Structured data for debugging/logging
 */
public class MemoryContextFormatter {

    private static final int CHARS_PER_TOKEN = 4; // Approximate ratio

    /**
     * Format context entries as system prompt addition.
     * Enforces token budget by truncating when limit reached.
     *
     * @param entries   List of context entries to format
     * @param maxTokens Maximum tokens to use (chars = tokens * 4)
     * @return Formatted prompt string with memory context
     */
    public static String formatAsSystemPrompt(List<ContextEntry> entries, int maxTokens) {
        if (entries == null || entries.isEmpty()) {
            return "";
        }

        StringBuilder prompt = new StringBuilder();
        prompt.append("\n\n## Your Memory (Relevant Context):\n");
        prompt.append("You remember the following from past conversations:\n\n");

        int charLimit = maxTokens * CHARS_PER_TOKEN;
        int currentLength = prompt.length();

        for (ContextEntry entry : entries) {
            String line = formatEntryLine(entry);

            if (currentLength + line.length() > charLimit) {
                // Add truncation notice and break
                String truncationNote = "\n(... more memories available but omitted for brevity)\n";
                if (currentLength + truncationNote.length() <= charLimit + 50) { // Allow slight overflow for note
                    prompt.append(truncationNote);
                }
                break;
            }

            prompt.append(line);
            currentLength += line.length();
        }

        return prompt.toString();
    }

    /**
     * Format a single context entry as a prompt line.
     * Uses importance markers for visual hierarchy.
     */
    private static String formatEntryLine(ContextEntry entry) {
        String marker = getImportanceMarker(entry.importance());
        String content = sanitizeContent(entry.content());

        // Truncate individual entries if too long
        if (content.length() > 150) {
            content = content.substring(0, 147) + "...";
        }

        return marker + " " + content + "\n";
    }

    /**
     * Get importance marker based on score.
     */
    private static String getImportanceMarker(double importance) {
        if (importance >= 0.8) {
            return "★";  // High importance - starred
        } else if (importance >= 0.6) {
            return "✓";  // Medium importance - checked
        } else {
            return "•";  // Low importance - bullet
        }
    }

    /**
     * Sanitize content for safe prompt injection.
     * Removes potentially harmful patterns.
     */
    private static String sanitizeContent(String content) {
        if (content == null) {
            return "";
        }

        // Remove potential prompt injection patterns
        String sanitized = content
            .replace("```", "")           // Remove code blocks
            .replace("###", "")           // Remove markdown headers
            .replace("---", "")           // Remove horizontal rules
            .replace("\\n\\n", " ")       // Collapse multiple newlines
            .replace("\n", " ")           // Single newlines to space
            .trim();

        return sanitized;
    }

    /**
     * Format context entries as JSON for debugging.
     *
     * @param entries List of context entries
     * @return JSON string representation
     */
    public static String formatAsJson(List<ContextEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return "[]";
        }

        StringBuilder json = new StringBuilder("[\n");
        for (int i = 0; i < entries.size(); i++) {
            ContextEntry entry = entries.get(i);
            json.append("  {");
            json.append("\"id\":\"").append(escapeJson(entry.id())).append("\",");
            json.append("\"content\":\"").append(escapeJson(truncateForJson(entry.content()))).append("\",");
            json.append("\"importance\":").append(String.format("%.2f", entry.importance())).append(",");
            json.append("\"source\":\"").append(entry.source()).append("\"");
            json.append("}");

            if (i < entries.size() - 1) {
                json.append(",");
            }
            json.append("\n");
        }
        json.append("]");
        return json.toString();
    }

    /**
     * Format context entries as compact single-line summary.
     * Useful for logging without excessive output.
     *
     * @param entries List of context entries
     * @return Compact summary string
     */
    public static String formatAsCompactSummary(List<ContextEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return "No context available";
        }

        StringBuilder summary = new StringBuilder();
        summary.append(entries.size()).append(" memories: ");

        for (int i = 0; i < Math.min(entries.size(), 3); i++) {
            ContextEntry entry = entries.get(i);
            String preview = entry.content();
            if (preview != null && preview.length() > 30) {
                preview = preview.substring(0, 27) + "...";
            }
            summary.append("[").append(preview).append("]");
            if (i < Math.min(entries.size(), 3) - 1) {
                summary.append(", ");
            }
        }

        if (entries.size() > 3) {
            summary.append(" +").append(entries.size() - 3).append(" more");
        }

        return summary.toString();
    }

    /**
     * Escape special characters for JSON output.
     */
    private static String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }

    /**
     * Truncate content for JSON output.
     */
    private static String truncateForJson(String content) {
        if (content == null) {
            return "";
        }
        return content.length() > 100 ? content.substring(0, 97) + "..." : content;
    }

    /**
     * Calculate approximate token count for a string.
     *
     * @param text Text to measure
     * @return Approximate token count
     */
    public static int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return (text.length() + CHARS_PER_TOKEN - 1) / CHARS_PER_TOKEN;
    }
}
