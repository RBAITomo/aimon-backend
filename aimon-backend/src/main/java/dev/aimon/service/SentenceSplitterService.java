package dev.aimon.service;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for splitting text into sentences.
 * Supports Vietnamese sentence detection with punctuation: . ! ? …
 * Used for streaming TTS to emit audio sentence-by-sentence.
 *
 * Simplified version without LangChain4j dependency.
 */
@ApplicationScoped
public class SentenceSplitterService {

    private static final Logger LOG = Logger.getLogger(SentenceSplitterService.class);

    // Vietnamese sentence detection pattern
    // Matches text ending with sentence punctuation (. ! ? …) followed by space/newline or end
    private static final Pattern SENTENCE_PATTERN = Pattern.compile("([^.!?…]*[.!?…]+)\\s*");

    @PostConstruct
    void init() {
        LOG.info("Initialized SentenceSplitterService with pattern-based sentence detection");
    }

    /**
     * Split text into complete sentences using regex pattern.
     * Supports Vietnamese with punctuation marks: . ! ? …
     *
     * @param text Text to split
     * @return List of trimmed sentences
     */
    public List<String> splitIntoSentences(String text) {
        if (text == null || text.trim().isEmpty()) {
            return List.of();
        }

        List<String> sentences = new ArrayList<>();
        Matcher matcher = SENTENCE_PATTERN.matcher(text);

        while (matcher.find()) {
            String sentence = matcher.group(1).trim();
            if (!sentence.isEmpty()) {
                sentences.add(sentence);
            }
        }

        // Add remaining text if no punctuation at end
        int lastEnd = 0;
        matcher.reset();
        while (matcher.find()) {
            lastEnd = matcher.end();
        }
        if (lastEnd < text.length()) {
            String remaining = text.substring(lastEnd).trim();
            if (!remaining.isEmpty()) {
                sentences.add(remaining);
            }
        }

        LOG.debugf("Split text into %d sentences", sentences.size());
        return sentences;
    }

    /**
     * Streaming sentence splitter for LLM streaming output.
     * Buffers text chunks and emits sentences when complete punctuation detected.
     */
    public static class StreamingSplitter {
        private final StringBuilder buffer = new StringBuilder();
        private final StringBuilder sentenceAccumulator = new StringBuilder();
        private int accumulatedSentenceCount = 0;
        private final Consumer<String> onSentence;
        private static final Logger LOG = Logger.getLogger(StreamingSplitter.class);

        // Configuration for sentence grouping
        private static final int MIN_SENTENCES = 1;
        private static final int MAX_SENTENCES = 3;
        private static final int MAX_CHARS = 100;

        /**
         * Create a streaming splitter with sentence callback.
         *
         * @param onSentence Callback invoked when a sentence group is ready
         */
        public StreamingSplitter(Consumer<String> onSentence) {
            if (onSentence == null) {
                throw new IllegalArgumentException("onSentence callback cannot be null");
            }
            this.onSentence = onSentence;
        }

        /**
         * Append a text chunk from LLM streaming.
         *
         * @param chunk Text chunk to append
         */
        public void append(String chunk) {
            if (chunk == null || chunk.isEmpty()) {
                return;
            }

            buffer.append(chunk);
            extractAndGroupSentences();
        }

        /**
         * Flush remaining buffer content and accumulated sentences.
         * Call this when LLM streaming completes.
         */
        public void flush() {
            // Flush any pending incomplete sentence in buffer
            if (buffer.length() > 0) {
                String remaining = buffer.toString().trim();
                if (!remaining.isEmpty()) {
                    accumulateSentence(remaining);
                }
                buffer.setLength(0);
            }

            // Emit accumulated sentences
            if (sentenceAccumulator.length() > 0) {
                String group = sentenceAccumulator.toString().trim();
                LOG.debugf("Flushing final sentence group (%d sentences): %s", accumulatedSentenceCount, group);
                onSentence.accept(group);
                sentenceAccumulator.setLength(0);
                accumulatedSentenceCount = 0;
            }
        }

        /**
         * Extract complete sentences from buffer and group them.
         */
        private void extractAndGroupSentences() {
            String text = buffer.toString();
            Matcher matcher = SENTENCE_PATTERN.matcher(text);

            int lastEnd = 0;
            while (matcher.find()) {
                String sentence = matcher.group(1).trim();
                if (!sentence.isEmpty()) {
                    accumulateSentence(sentence);
                }
                lastEnd = matcher.end();
            }

            // Keep remaining incomplete text in buffer
            if (lastEnd > 0) {
                buffer.delete(0, lastEnd);
            }
        }

        /**
         * Accumulate a sentence and emit group when conditions are met.
         */
        private void accumulateSentence(String sentence) {
            // Check if adding this sentence would exceed MAX_CHARS
            int newLength = sentenceAccumulator.length() + (sentenceAccumulator.length() > 0 ? 1 : 0) + sentence.length();

            // If already have some sentences and adding this would exceed limit, emit first
            if (accumulatedSentenceCount > 0 && newLength >= MAX_CHARS) {
                emitAccumulatedSentences();
            }

            // Add sentence to accumulator
            if (sentenceAccumulator.length() > 0) {
                sentenceAccumulator.append(" ");
            }
            sentenceAccumulator.append(sentence);
            accumulatedSentenceCount++;

            LOG.debugf("Accumulated sentence #%d (total %d chars): %s",
                accumulatedSentenceCount, sentenceAccumulator.length(), sentence);

            // Emit if conditions met
            if (accumulatedSentenceCount >= MAX_SENTENCES ||
                (accumulatedSentenceCount >= MIN_SENTENCES && sentenceAccumulator.length() >= MAX_CHARS)) {
                emitAccumulatedSentences();
            }
        }

        /**
         * Emit accumulated sentences as a group and reset accumulator.
         */
        private void emitAccumulatedSentences() {
            if (sentenceAccumulator.length() > 0) {
                String group = sentenceAccumulator.toString().trim();
                LOG.debugf("Emitting sentence group (%d sentences, %d chars): %s",
                    accumulatedSentenceCount, group.length(), group);
                onSentence.accept(group);
                sentenceAccumulator.setLength(0);
                accumulatedSentenceCount = 0;
            }
        }

        /**
         * Get current buffer content (for debugging).
         */
        public String getBufferContent() {
            return buffer.toString();
        }

        /**
         * Check if buffer is empty.
         */
        public boolean isEmpty() {
            return buffer.length() == 0;
        }
    }
}
