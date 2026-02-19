package dev.aimon.model;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Per-connection session state for WebSocket v4 protocol.
 * Tracks audio streaming state, buffers, and conversation context.
 */
public class RobotSession {

    private final String sessionId;
    private final String petId;
    private SessionState state;
    private final Deque<byte[]> audioFrameBuffer;
    private final AtomicBoolean isCancelled;
    private ConversationSession conversation;
    private Long userId;

    // Audio configuration (negotiated in hello)
    private int sampleRate;
    private String audioFormat;

    public RobotSession(String petId) {
        this.sessionId = UUID.randomUUID().toString();
        this.petId = petId;
        this.state = SessionState.IDLE;
        this.audioFrameBuffer = new ArrayDeque<>(128);
        this.isCancelled = new AtomicBoolean(false);
        this.sampleRate = 16000; // Default PCM16 at 16kHz
        this.audioFormat = "pcm16";
    }

    public void reset() {
        this.state = SessionState.IDLE;
        this.audioFrameBuffer.clear();
        this.isCancelled.set(false);
    }

    // Getters and setters
    public String getSessionId() { return sessionId; }
    public String getPetId() { return petId; }
    public SessionState getState() { return state; }
    public void setState(SessionState state) { this.state = state; }
    public Deque<byte[]> getAudioFrameBuffer() { return audioFrameBuffer; }
    public boolean isCancelled() { return isCancelled.get(); }
    public void setCancelled(boolean cancelled) { isCancelled.set(cancelled); }
    public ConversationSession getConversation() { return conversation; }
    public void setConversation(ConversationSession conversation) { this.conversation = conversation; }
    public int getSampleRate() { return sampleRate; }
    public void setSampleRate(int sampleRate) { this.sampleRate = sampleRate; }
    public String getAudioFormat() { return audioFormat; }
    public void setAudioFormat(String audioFormat) { this.audioFormat = audioFormat; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
}
