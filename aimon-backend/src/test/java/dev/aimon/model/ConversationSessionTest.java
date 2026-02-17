package dev.aimon.model;

import dev.aimon.dto.ai.LiteLlmChatMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ConversationSession model.
 */
class ConversationSessionTest {

    private ConversationSession session;

    @BeforeEach
    void setUp() {
        session = new ConversationSession(1, "session-1", "System prompt", Instant.now(), 3);
    }

    @Test
    void constructor_setsFieldsCorrectly() {
        assertEquals(1, session.userId());
        assertEquals("session-1", session.sessionId());
        assertEquals("System prompt", session.consciousSystemPrompt());
        assertNotNull(session.lastActivity());
    }

    @Test
    void constructor_nullPrompt_defaultsToEmpty() {
        var s = new ConversationSession(1, "s1", null, Instant.now(), 5);
        assertEquals("", s.consciousSystemPrompt());
    }

    @Test
    void constructor_nullLastActivity_defaultsToNow() {
        var s = new ConversationSession(1, "s1", "prompt", null, 5);
        assertNotNull(s.lastActivity());
    }

    @Test
    void hasConsciousContext_withPrompt_returnsTrue() {
        assertTrue(session.hasConsciousContext());
    }

    @Test
    void hasConsciousContext_emptyPrompt_returnsFalse() {
        var s = new ConversationSession(1, "s1", "", Instant.now(), 5);
        assertFalse(s.hasConsciousContext());
    }

    @Test
    void hasConsciousContext_blankPrompt_returnsFalse() {
        var s = new ConversationSession(1, "s1", "   ", Instant.now(), 5);
        assertFalse(s.hasConsciousContext());
    }

    @Test
    void addTurn_incrementsHistorySize() {
        assertEquals(0, session.getHistorySize());
        session.addTurn("Hello", "Hi there!");
        assertEquals(1, session.getHistorySize());
    }

    @Test
    void addTurn_updatesLastActivity() {
        Instant before = session.lastActivity();
        // Small delay to ensure timestamp difference
        session.addTurn("msg", "resp");
        // lastActivity should be >= before
        assertFalse(session.lastActivity().isBefore(before));
    }

    @Test
    void addTurn_rollingWindow_removesOldest() {
        // maxHistorySize = 3
        session.addTurn("msg1", "resp1");
        session.addTurn("msg2", "resp2");
        session.addTurn("msg3", "resp3");
        assertEquals(3, session.getHistorySize());

        session.addTurn("msg4", "resp4");
        assertEquals(3, session.getHistorySize());

        // Oldest turn should be removed
        List<ConversationSession.ConversationTurn> turns = session.getAllTurns();
        assertEquals("msg2", turns.get(0).getUserMessage());
        assertEquals("msg4", turns.get(2).getUserMessage());
    }

    @Test
    void getAllTurns_returnsDefensiveCopy() {
        session.addTurn("msg", "resp");
        List<ConversationSession.ConversationTurn> turns = session.getAllTurns();
        turns.clear();
        assertEquals(1, session.getHistorySize());
    }

    @Test
    void getHistoryMessages_returnsAlternatingMessages() {
        session.addTurn("user msg", "ai resp");
        List<LiteLlmChatMessage> messages = session.getHistoryMessages();
        assertEquals(2, messages.size());
        assertEquals("user", messages.get(0).getRole());
        assertEquals("user msg", messages.get(0).getContent());
        assertEquals("assistant", messages.get(1).getRole());
        assertEquals("ai resp", messages.get(1).getContent());
    }

    @Test
    void clearHistory_emptiesHistory() {
        session.addTurn("msg", "resp");
        session.clearHistory();
        assertEquals(0, session.getHistorySize());
    }

    @Test
    void updateLastActivity_updatesTimestamp() {
        Instant before = session.lastActivity();
        session.updateLastActivity();
        assertFalse(session.lastActivity().isBefore(before));
    }

    @Test
    void toString_containsKeyInfo() {
        String str = session.toString();
        assertTrue(str.contains("userId=1"));
        assertTrue(str.contains("session-1"));
    }

    @Test
    void conversationTurn_getters() {
        var turn = new ConversationSession.ConversationTurn("hello", "world");
        assertEquals("hello", turn.getUserMessage());
        assertEquals("world", turn.getAiResponse());
        assertNotNull(turn.getTimestamp());
    }
}
