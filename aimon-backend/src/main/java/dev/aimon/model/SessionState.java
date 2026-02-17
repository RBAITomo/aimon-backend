package dev.aimon.model;

/**
 * WebSocket session state for v4 push-to-talk protocol.
 * Simplified from v3 state machine (removed CHECK_IN, DETECT, VAD states).
 */
public enum SessionState {
    /**
     * Idle, waiting for user input.
     */
    IDLE,

    /**
     * Collecting audio frames (button held down).
     */
    LISTENING,

    /**
     * Processing audio (STT + LLM preparation).
     */
    PROCESSING,

    /**
     * Streaming response (LLM + TTS output).
     */
    RESPONDING
}
