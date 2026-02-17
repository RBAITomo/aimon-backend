package dev.aimon.service.conversation;

import dev.aimon.model.ConversationSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ConversationSessionManager.
 * Pure unit tests without Quarkus container.
 */
class ConversationSessionManagerTest {

    private ConversationSessionManager manager;

    @BeforeEach
    void setUp() throws Exception {
        manager = new ConversationSessionManager();
        // Inject config properties via reflection (no CDI container)
        setField(manager, "idleTimeoutMinutes", 30);
        setField(manager, "maxHistoryTurns", 10);
    }

    @Test
    void getOrCreateSession_createsNewSession() {
        ConversationSession session = manager.getOrCreateSession(1, "session-1");
        assertNotNull(session);
        assertEquals(1, session.userId());
        assertEquals("session-1", session.sessionId());
    }

    @Test
    void getOrCreateSession_returnsCachedSession() {
        ConversationSession first = manager.getOrCreateSession(1, "session-1");
        ConversationSession second = manager.getOrCreateSession(1, "session-1");
        assertSame(first, second);
    }

    @Test
    void getOrCreateSession_differentSessions() {
        ConversationSession s1 = manager.getOrCreateSession(1, "session-1");
        ConversationSession s2 = manager.getOrCreateSession(2, "session-2");
        assertNotSame(s1, s2);
    }

    @Test
    void getSession_existingSession_returnsSession() {
        manager.getOrCreateSession(1, "session-1");
        assertNotNull(manager.getSession("session-1"));
    }

    @Test
    void getSession_nonExisting_returnsNull() {
        assertNull(manager.getSession("nonexistent"));
    }

    @Test
    void getSessionHistorySize_existingSession() {
        ConversationSession session = manager.getOrCreateSession(1, "session-1");
        session.addTurn("hello", "hi");
        assertEquals(1, manager.getSessionHistorySize("session-1"));
    }

    @Test
    void getSessionHistorySize_nonExisting_returnsZero() {
        assertEquals(0, manager.getSessionHistorySize("nonexistent"));
    }

    @Test
    void getSystemPrompt_existingSession_returnsPrompt() {
        manager.getOrCreateSession(1, "session-1");
        String prompt = manager.getSystemPrompt("session-1");
        assertNotNull(prompt);
        // Should contain fallback prompt content
        assertTrue(prompt.contains("Tomo"));
    }

    @Test
    void getSystemPrompt_nonExisting_returnsEmpty() {
        assertEquals("", manager.getSystemPrompt("nonexistent"));
    }

    @Test
    void updateActivity_existingSession() {
        manager.getOrCreateSession(1, "session-1");
        // Should not throw
        manager.updateActivity("session-1");
    }

    @Test
    void updateActivity_nonExisting_doesNotThrow() {
        assertDoesNotThrow(() -> manager.updateActivity("nonexistent"));
    }

    @Test
    void getActiveSessionCount() {
        assertEquals(0, manager.getActiveSessionCount());
        manager.getOrCreateSession(1, "s1");
        assertEquals(1, manager.getActiveSessionCount());
        manager.getOrCreateSession(2, "s2");
        assertEquals(2, manager.getActiveSessionCount());
    }

    @Test
    void removeSession_existingSession() {
        manager.getOrCreateSession(1, "session-1");
        manager.removeSession("session-1");
        assertNull(manager.getSession("session-1"));
        assertEquals(0, manager.getActiveSessionCount());
    }

    @Test
    void removeSession_nonExisting_doesNotThrow() {
        assertDoesNotThrow(() -> manager.removeSession("nonexistent"));
    }

    @Test
    void incrementBatchUploadSuccess() {
        manager.incrementBatchUploadSuccess();
        Map<String, Long> stats = manager.getStats();
        assertEquals(1L, stats.get("batchUploadSuccess"));
    }

    @Test
    void incrementBatchUploadFailure() {
        manager.incrementBatchUploadFailure();
        Map<String, Long> stats = manager.getStats();
        assertEquals(1L, stats.get("batchUploadFailure"));
    }

    @Test
    void getStats_returnsAllKeys() {
        Map<String, Long> stats = manager.getStats();
        assertTrue(stats.containsKey("activeSessions"));
        assertTrue(stats.containsKey("sessionsCreatedTotal"));
        assertTrue(stats.containsKey("consciousCacheHits"));
        assertTrue(stats.containsKey("consciousCacheMisses"));
        assertTrue(stats.containsKey("batchUploadSuccess"));
        assertTrue(stats.containsKey("batchUploadFailure"));
    }

    @Test
    void getStats_tracksCacheHitsAndMisses() {
        // First call = cache miss
        manager.getOrCreateSession(1, "s1");
        // Second call = cache hit
        manager.getOrCreateSession(1, "s1");

        Map<String, Long> stats = manager.getStats();
        assertEquals(1L, stats.get("consciousCacheMisses"));
        assertEquals(1L, stats.get("consciousCacheHits"));
    }

    @Test
    void cleanupExpiredSessions_removesExpiredOnly() throws Exception {
        // Create a session and manually set lastActivity to past
        ConversationSession session = manager.getOrCreateSession(1, "old-session");
        // Use reflection to set lastActivity to 1 hour ago
        Field lastActivityField = ConversationSession.class.getDeclaredField("lastActivity");
        lastActivityField.setAccessible(true);
        lastActivityField.set(session, java.time.Instant.now().minusSeconds(3600));

        // Create a recent session
        manager.getOrCreateSession(2, "new-session");

        manager.cleanupExpiredSessions();

        assertNull(manager.getSession("old-session"));
        assertNotNull(manager.getSession("new-session"));
        assertEquals(1, manager.getActiveSessionCount());
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
