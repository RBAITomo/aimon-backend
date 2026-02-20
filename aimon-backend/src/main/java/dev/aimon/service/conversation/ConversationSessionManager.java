package dev.aimon.service.conversation;

import dev.aimon.dto.pet.PetStatusDto;
import dev.aimon.entity.User;
import dev.aimon.model.ConversationSession;
import dev.aimon.repository.UserRepository;
import dev.aimon.service.pet.PetProfileService;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Manages active conversation sessions with cached conscious context.
 * 
 * Responsibilities:
 * - Track active WebSocket sessions
 * - Load conscious context ONCE per session (on first message)
 * - Provide cached system prompt for session lifetime
 * - Auto-cleanup expired sessions (after idle timeout)
 * 
 * This avoids repeated API calls to load the same user identity/preferences
 * for every message in a conversation.
 */
@ApplicationScoped
public class ConversationSessionManager {
    
    private static final Logger LOG = Logger.getLogger(ConversationSessionManager.class);

    /**
     * Fallback system prompt if conscious context loading fails.
     * Taken from LiteLlmAIService default prompt.
     */
    private static final String FALLBACK_SYSTEM_PROMPT =
        "You are a caring and playful digital pet living in Cotton Land. "
        + "Respond in Vietnamese unless explicitly asked otherwise. "
        + "Keep answers short, kind, and easy to understand.";

    @Inject
    UserRepository userRepository;

    @Inject
    PetProfileService petProfileService;

    private final Map<String, ConversationSession> activeSessions = new ConcurrentHashMap<>();

    // Simple counters for monitoring (logging-based metrics)
    private final AtomicLong sessionsCreatedTotal = new AtomicLong(0);
    private final AtomicLong consciousCacheHits = new AtomicLong(0);
    private final AtomicLong consciousCacheMisses = new AtomicLong(0);
    private final AtomicLong batchUploadSuccess = new AtomicLong(0);
    private final AtomicLong batchUploadFailure = new AtomicLong(0);

    @ConfigProperty(name = "conversation.session.idle-timeout-minutes", defaultValue = "30")
    int idleTimeoutMinutes;

    @ConfigProperty(name = "conversation.history.max-turns", defaultValue = "10")
    int maxHistoryTurns;

    /**
     * Gets or creates a conversation session.
     * 
     * On first call for a sessionId, this creates a session with the default system prompt.
     * Subsequent calls return the cached session.
     *
     * @param userId User ID for this session
     * @param sessionId Unique session identifier
     * @return Conversation session with conscious context loaded
     */
    public ConversationSession getOrCreateSession(Integer userId, String sessionId) {
        ConversationSession existing = activeSessions.get(sessionId);

        if (existing != null) {
            consciousCacheHits.incrementAndGet();
            existing.updateLastActivity();
            return existing;
        }

        // Build session outside map lock to avoid blocking other segments
        ConversationSession newSession = buildNewSession(userId, sessionId);
        ConversationSession winner = activeSessions.putIfAbsent(sessionId, newSession);
        if (winner != null) {
            // Another thread created first — use theirs
            consciousCacheHits.incrementAndGet();
            winner.updateLastActivity();
            return winner;
        }

        consciousCacheMisses.incrementAndGet();
        sessionsCreatedTotal.incrementAndGet();
        return newSession;
    }

    private ConversationSession buildNewSession(Integer userId, String sessionId) {
        LOG.infof("Creating new session: %s for userId: %d", sessionId, userId);
        ConversationSession session = new ConversationSession(
            userId, sessionId, FALLBACK_SYSTEM_PROMPT, Instant.now(), maxHistoryTurns
        );

        try {
            User user = userRepository.findById(userId.longValue());
            session.setCachedChildName(user != null ? user.getName() : null);
            session.setCachedChildAge(user != null && user.getAge() != null ? user.getAge() : 0);

            PetStatusDto petStatus = petProfileService.getStatus(userId.longValue());
            session.setCachedPetStatus(petStatus);
            LOG.infof("Cached user/pet profile for session %s: name=%s, stage=%s",
                sessionId, session.getCachedChildName(),
                petStatus != null ? petStatus.stage() : "null");
        } catch (Exception e) {
            LOG.warnf(e, "Failed to pre-load user/pet profile for session %s", sessionId);
        }

        return session;
    }

    /**
     * Gets an existing session (without creating new one).
     * Used for cleanup operations.
     *
     * @param sessionId Session identifier
     * @return Conversation session or null if not found
     */
    public ConversationSession getSession(String sessionId) {
        return activeSessions.get(sessionId);
    }

    /**
     * Gets session history size for monitoring.
     *
     * @param sessionId Session identifier
     * @return History size or 0 if session doesn't exist
     */
    public int getSessionHistorySize(String sessionId) {
        ConversationSession session = activeSessions.get(sessionId);
        return session != null ? session.getHistorySize() : 0;
    }

    /**
     * Gets the system prompt for a session (includes conscious context).
     * 
     * Returns empty string if session doesn't exist.
     *
     * @param sessionId Session identifier
     * @return System prompt with conscious context, or empty string
     */
    public String getSystemPrompt(String sessionId) {
        ConversationSession session = activeSessions.get(sessionId);
        return session != null ? session.consciousSystemPrompt() : "";
    }

    /**
     * Updates last activity timestamp for a session.
     * Call this on each message to track idle time.
     *
     * @param sessionId Session identifier
     */
    public void updateActivity(String sessionId) {
        ConversationSession session = activeSessions.get(sessionId);
        if (session != null) {
            session.updateLastActivity();
            LOG.tracef("Updated activity for session %s", sessionId);
        }
    }

    /**
     * Gets the number of active sessions.
     *
     * @return Count of active sessions
     */
    public int getActiveSessionCount() {
        return activeSessions.size();
    }

    /**
     * Invalidate cached system prompt for all sessions belonging to a user.
     * Called after pet state changes (feed, quest complete, transform, level up).
     * Also refreshes cached pet status from DB.
     */
    public void invalidateUserCache(Long userId) {
        for (ConversationSession session : activeSessions.values()) {
            if (session.userId().longValue() == userId) {
                session.invalidateCachedPrompt();
                try {
                    PetStatusDto freshStatus = petProfileService.getStatus(userId);
                    session.setCachedPetStatus(freshStatus);

                    User user = userRepository.findById(userId);
                    session.setCachedChildName(user != null ? user.getName() : null);
                    session.setCachedChildAge(user != null && user.getAge() != null ? user.getAge() : 0);
                } catch (Exception e) {
                    LOG.warnf(e, "Failed to refresh cached profile for userId %d", userId);
                }
                LOG.debugf("Invalidated cached prompt for userId %d", userId);
            }
        }
    }

    /**
     * Removes a session from active sessions.
     * Typically called when WebSocket connection closes.
     *
     * @param sessionId Session identifier to remove
     */
    public void removeSession(String sessionId) {
        ConversationSession removed = activeSessions.remove(sessionId);
        if (removed != null) {
            LOG.infof("Removed session: %s", sessionId);
        }
    }

    /**
     * Cleanup expired sessions based on idle timeout.
     * 
     * Runs every 5 minutes to remove sessions that haven't had activity
     * within the configured idle timeout period.
     * Also logs session statistics for monitoring.
     */
    @Scheduled(every = "5m")
    void cleanupExpiredSessions() {
        Instant cutoff = Instant.now().minus(idleTimeoutMinutes, ChronoUnit.MINUTES);
        int removedCount = 0;
        
        var iterator = activeSessions.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (entry.getValue().lastActivity().isBefore(cutoff)) {
                LOG.infof("Removing expired session: %s (idle for >%d min)", 
                         entry.getKey(), idleTimeoutMinutes);
                iterator.remove();
                removedCount++;
            }
        }
        
        // Log session statistics for monitoring
        logSessionStats(removedCount);
    }
    
    /**
     * Logs session statistics for monitoring purposes.
     */
    private void logSessionStats(int removedCount) {
        long cacheHits = consciousCacheHits.get();
        long cacheMisses = consciousCacheMisses.get();
        long total = cacheHits + cacheMisses;
        double hitRate = total > 0 ? (double) cacheHits / total * 100 : 0;
        
        LOG.infof("Session Stats: active=%d, created=%d, cache_hit_rate=%.1f%% (%d/%d), uploads(ok=%d,fail=%d), removed=%d",
                 activeSessions.size(),
                 sessionsCreatedTotal.get(),
                 hitRate,
                 cacheHits,
                 total,
                 batchUploadSuccess.get(),
                 batchUploadFailure.get(),
                 removedCount);
    }
    
    /**
     * Increment batch upload success counter.
     * Called after successful batch upload to PowerMem.
     */
    public void incrementBatchUploadSuccess() {
        batchUploadSuccess.incrementAndGet();
    }
    
    /**
     * Increment batch upload failure counter.
     * Called after failed batch upload to PowerMem.
     */
    public void incrementBatchUploadFailure() {
        batchUploadFailure.incrementAndGet();
    }
    
    /**
     * Get current statistics for monitoring/debugging.
     * 
     * @return Map of stat name to value
     */
    public Map<String, Long> getStats() {
        return Map.of(
            "activeSessions", (long) activeSessions.size(),
            "sessionsCreatedTotal", sessionsCreatedTotal.get(),
            "consciousCacheHits", consciousCacheHits.get(),
            "consciousCacheMisses", consciousCacheMisses.get(),
            "batchUploadSuccess", batchUploadSuccess.get(),
            "batchUploadFailure", batchUploadFailure.get()
        );
    }
}
