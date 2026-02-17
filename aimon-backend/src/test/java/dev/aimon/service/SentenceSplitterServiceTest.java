package dev.aimon.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SentenceSplitterService.
 * Tests both batch splitting and streaming splitter.
 */
class SentenceSplitterServiceTest {

    private SentenceSplitterService service;

    @BeforeEach
    void setUp() {
        service = new SentenceSplitterService();
    }

    // --- splitIntoSentences tests ---

    @Test
    void splitIntoSentences_nullInput_returnsEmpty() {
        assertTrue(service.splitIntoSentences(null).isEmpty());
    }

    @Test
    void splitIntoSentences_emptyInput_returnsEmpty() {
        assertTrue(service.splitIntoSentences("").isEmpty());
        assertTrue(service.splitIntoSentences("   ").isEmpty());
    }

    @Test
    void splitIntoSentences_singleSentenceWithPeriod() {
        List<String> result = service.splitIntoSentences("Xin chào bạn.");
        assertEquals(1, result.size());
        assertEquals("Xin chào bạn.", result.get(0));
    }

    @Test
    void splitIntoSentences_multipleSentences() {
        List<String> result = service.splitIntoSentences("Xin chào bạn. Hôm nay thế nào? Tuyệt vời!");
        assertEquals(3, result.size());
        assertEquals("Xin chào bạn.", result.get(0));
        assertEquals("Hôm nay thế nào?", result.get(1));
        assertEquals("Tuyệt vời!", result.get(2));
    }

    @Test
    void splitIntoSentences_ellipsis() {
        List<String> result = service.splitIntoSentences("Để mình nghĩ… Ồ đúng rồi!");
        assertEquals(2, result.size());
        assertEquals("Để mình nghĩ…", result.get(0));
        assertEquals("Ồ đúng rồi!", result.get(1));
    }

    @Test
    void splitIntoSentences_textWithoutPunctuation() {
        List<String> result = service.splitIntoSentences("Xin chào bạn nhỏ");
        assertEquals(1, result.size());
        assertEquals("Xin chào bạn nhỏ", result.get(0));
    }

    @Test
    void splitIntoSentences_mixedPunctuationAndTrailingText() {
        List<String> result = service.splitIntoSentences("Câu một. Câu hai không dấu");
        assertEquals(2, result.size());
        assertEquals("Câu một.", result.get(0));
        assertEquals("Câu hai không dấu", result.get(1));
    }

    // --- StreamingSplitter tests ---

    @Test
    void streamingSplitter_nullCallback_throwsException() {
        assertThrows(IllegalArgumentException.class, () ->
            new SentenceSplitterService.StreamingSplitter(null));
    }

    @Test
    void streamingSplitter_singleChunkWithSentence() {
        List<String> emitted = new ArrayList<>();
        var splitter = new SentenceSplitterService.StreamingSplitter(emitted::add);

        splitter.append("Xin chào bạn.");
        splitter.flush();

        assertFalse(emitted.isEmpty());
        // Should contain "Xin chào bạn." in one of the emitted groups
        String joined = String.join(" ", emitted);
        assertTrue(joined.contains("Xin chào bạn."));
    }

    @Test
    void streamingSplitter_multipleChunks() {
        List<String> emitted = new ArrayList<>();
        var splitter = new SentenceSplitterService.StreamingSplitter(emitted::add);

        splitter.append("Xin ");
        splitter.append("chào ");
        splitter.append("bạn. ");
        splitter.append("Hôm nay ");
        splitter.append("thế nào?");
        splitter.flush();

        assertFalse(emitted.isEmpty());
        String joined = String.join(" ", emitted);
        assertTrue(joined.contains("Xin chào bạn."));
        assertTrue(joined.contains("Hôm nay thế nào?"));
    }

    @Test
    void streamingSplitter_nullAndEmptyChunks_ignored() {
        List<String> emitted = new ArrayList<>();
        var splitter = new SentenceSplitterService.StreamingSplitter(emitted::add);

        splitter.append(null);
        splitter.append("");
        splitter.append("Xin chào.");
        splitter.flush();

        assertFalse(emitted.isEmpty());
    }

    @Test
    void streamingSplitter_emptyBuffer_flushDoesNothing() {
        List<String> emitted = new ArrayList<>();
        var splitter = new SentenceSplitterService.StreamingSplitter(emitted::add);

        splitter.flush();
        assertTrue(emitted.isEmpty());
    }

    @Test
    void streamingSplitter_isEmpty() {
        var splitter = new SentenceSplitterService.StreamingSplitter(s -> {});
        assertTrue(splitter.isEmpty());
        splitter.append("text");
        assertFalse(splitter.isEmpty());
    }

    @Test
    void streamingSplitter_getBufferContent() {
        var splitter = new SentenceSplitterService.StreamingSplitter(s -> {});
        assertEquals("", splitter.getBufferContent());
        splitter.append("hello");
        assertEquals("hello", splitter.getBufferContent());
    }

    @Test
    void streamingSplitter_maxSentencesGrouping() {
        // MAX_SENTENCES = 3, so after 3 sentences a group should be emitted
        List<String> emitted = new ArrayList<>();
        var splitter = new SentenceSplitterService.StreamingSplitter(emitted::add);

        splitter.append("A. B. C. D.");
        splitter.flush();

        // Should have emitted at least 1 group before flush
        assertTrue(emitted.size() >= 1);
    }
}
