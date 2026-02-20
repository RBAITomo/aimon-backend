package dev.aimon.model;

import dev.aimon.dto.ai.LiteLlmChatMessage;
import dev.aimon.dto.pet.PetStatusDto;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents an active conversation session with cached conscious context.
 * 
 * A session is created when a WebSocket connection is established and first message is received.
 * The conscious context (user identity, preferences, skills) is loaded ONCE at session start
 * and cached for the session lifetime (default 30 minutes idle timeout).
 * 
 * This avoids repeated API calls to load the same user context for every message.
 */
public class ConversationSession {
    
    private final Integer userId;
    private final String sessionId;
    private final String consciousSystemPrompt;
    private volatile Instant lastActivity;
    private final List<ConversationTurn> history = Collections.synchronizedList(new ArrayList<>());
    private final int maxHistorySize;

    // Cached pet + user profile (loaded once at session creation, invalidated on state changes)
    // Volatile for cross-thread visibility (written by invalidation, read by IO workers)
    private volatile PetStatusDto cachedPetStatus;
    private volatile String cachedChildName;
    private volatile int cachedChildAge;
    private volatile String cachedSystemPrompt;

    /**
     * Creates a new conversation session with conscious context.
     *
     * @param userId User ID for this session
     * @param sessionId Unique session identifier (typically WebSocket session ID)
     * @param consciousSystemPrompt Pre-formatted system prompt from conscious memories (can be empty)
     * @param lastActivity Timestamp of last activity (message received/sent)
     * @param maxHistorySize Maximum number of conversation turns to keep in memory
     */
    public ConversationSession(Integer userId, String sessionId, 
                              String consciousSystemPrompt, Instant lastActivity,
                              int maxHistorySize) {
        this.userId = userId;
        this.sessionId = sessionId;
        this.consciousSystemPrompt = consciousSystemPrompt != null ? consciousSystemPrompt : "";
        this.lastActivity = lastActivity != null ? lastActivity : Instant.now();
        this.maxHistorySize = maxHistorySize;
    }

    /**
     * Updates the last activity timestamp to current time.
     * Called on each message to track session idle time.
     */
    public void updateLastActivity() {
        this.lastActivity = Instant.now();
    }

    /**
     * Checks if this session has conscious context loaded.
     *
     * @return true if conscious system prompt is not empty
     */
    public boolean hasConsciousContext() {
        return consciousSystemPrompt != null && !consciousSystemPrompt.isBlank();
    }

    // Getters

    public Integer userId() {
        return userId;
    }

    public String sessionId() {
        return sessionId;
    }

    public String consciousSystemPrompt() {
        return consciousSystemPrompt;
    }

    public Instant lastActivity() {
        return lastActivity;
    }

    /**
     * Conversation turn (user input + AI response).
     */
    public static class ConversationTurn {
        private final String userMessage;
        private final String aiResponse;
        private final Instant timestamp;
        
        public ConversationTurn(String userMessage, String aiResponse) {
            this.userMessage = userMessage;
            this.aiResponse = aiResponse;
            this.timestamp = Instant.now();
        }
        
        public String getUserMessage() {
            return userMessage;
        }
        
        public String getAiResponse() {
            return aiResponse;
        }
        
        public Instant getTimestamp() {
            return timestamp;
        }
    }

    /**
     * Adds a conversation turn to history.
     * Implements rolling window (keeps last N turns).
     */
    public void addTurn(String userMessage, String aiResponse) {
        synchronized (history) {
            history.add(new ConversationTurn(userMessage, aiResponse));
            if (history.size() > maxHistorySize) {
                history.remove(0);
            }
        }
        updateLastActivity();
    }

    /**
     * Gets conversation history as LLM messages.
     * Returns alternating user/assistant messages.
     */
    public List<LiteLlmChatMessage> getHistoryMessages() {
        synchronized (history) {
            List<LiteLlmChatMessage> messages = new ArrayList<>();
            for (ConversationTurn turn : history) {
                messages.add(LiteLlmChatMessage.user(turn.userMessage));
                messages.add(LiteLlmChatMessage.assistant(turn.aiResponse));
            }
            return messages;
        }
    }

    /**
     * Gets all turns for batch upload to PowerMem.
     */
    public List<ConversationTurn> getAllTurns() {
        synchronized (history) {
            return new ArrayList<>(history);
        }
    }

    /**
     * Clears history (called after successful upload).
     */
    public void clearHistory() {
        history.clear();
    }

    /**
     * Gets current history size.
     */
    public int getHistorySize() {
        return history.size();
    }

    // --- Cached pet/user profile accessors ---

    public PetStatusDto getCachedPetStatus() { return cachedPetStatus; }
    public void setCachedPetStatus(PetStatusDto cachedPetStatus) { this.cachedPetStatus = cachedPetStatus; }

    public String getCachedChildName() { return cachedChildName; }
    public void setCachedChildName(String cachedChildName) { this.cachedChildName = cachedChildName; }

    public int getCachedChildAge() { return cachedChildAge; }
    public void setCachedChildAge(int cachedChildAge) { this.cachedChildAge = cachedChildAge; }

    public String getCachedSystemPrompt() { return cachedSystemPrompt; }
    public void setCachedSystemPrompt(String cachedSystemPrompt) { this.cachedSystemPrompt = cachedSystemPrompt; }

    public boolean hasSystemPrompt() { return cachedSystemPrompt != null; }

    /** Invalidate cached system prompt so it rebuilds on next message. */
    public void invalidateCachedPrompt() { this.cachedSystemPrompt = null; }

    @Override
    public String toString() {
        return "ConversationSession{" +
                "userId=" + userId +
                ", sessionId='" + sessionId + '\'' +
                ", hasConsciousContext=" + hasConsciousContext() +
                ", lastActivity=" + lastActivity +
                ", historySize=" + history.size() +
                '}';
    }
}
