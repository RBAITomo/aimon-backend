# AI-MON Technical Design v0.2

## Full System Specification (Backend-Centric LLM Architecture)

Author: Project Owner Draft\
Target Platform: Raspberry Pi Zero 2 + Whisplay HAT\
Frontend: Pygame (Python)\
Backend: Java Quarkus\
LLM & AI Processing: Backend Only

------------------------------------------------------------------------

# 1. System Philosophy

AI-MON is a voice-driven AI companion designed for anybody who digital pet (Kid Mode
default). All intelligence, reasoning, memory processing, and LLM
interactions are handled exclusively on the backend.

Frontend is a thin client responsible only for: - UI rendering - Audio
capture/playback - Input handling - Local short-term logging

Backend is responsible for: - ASR - LLM reasoning - RAG - Personality
modeling - Memory management - TTS orchestration - Safety filtering

------------------------------------------------------------------------

# 2. Architecture Overview

## 2.1 Frontend Scope (Pi Zero 2)

Responsibilities: - Pygame UI state machine - PCM16 audio recording -
PCM16/WAV playback - Button input handling - Double-click detection for
camera - SQLite short-term turn logging - WebSocket client - Interrupt
handling - Emotion animation rendering

Frontend MUST NOT: - Call LLM directly - Store long-term memory -
Perform reasoning - Execute RAG logic

------------------------------------------------------------------------

## 2.2 Backend Scope (Quarkus)

Responsibilities: - WebSocket gateway - Session lifecycle management -
ASR (Google Speech-to-Text) - RAG context retrieval (if enabled) - LLM
orchestration (LiteLLM) - Personality parameter injection - Emotion
tagging - Memory summarization (PowerMem) - TTS generation (Vieneu) -
Safety filtering (Kid Mode) - Streaming orchestration - Metrics and
logging

Backend owns all AI logic.

------------------------------------------------------------------------

# 3. Interaction Model (Echo-Safe)

## States

IDLE\
LISTENING\
ASR\
ANSWER\
EMOTION

Strict Half-Duplex enforced: - Mic ON only in LISTENING - Mic OFF in all
other states

------------------------------------------------------------------------

# 4. Voice Flow

## 4.1 LISTENING

-   User presses and holds button
-   Frontend records PCM16 buffer
-   On release → mic OFF
-   Entire audio file sent to backend

## 4.2 ASR (Backend)

-   Audio sent to Google STT
-   If no transcript → return failure → FE goes IDLE
-   If transcript valid → continue

## 4.3 ANSWER (Backend-Centric Intelligence)

Backend pipeline:

1.  Retrieve recent turns
2.  Retrieve long-term memory (PowerMem)
3.  Build context prompt
4.  Inject personality parameters
5.  Apply Kid Mode constraints
6.  Send to LiteLLM
7.  Stream LLM response tokens
8.  Generate TTS progressively
9.  Stream audio + text to frontend

Frontend: - Displays streaming text - Plays streaming PCM audio

User interrupt: - Button press → backend cancels LLM + TTS - Immediate
transition to LISTENING

------------------------------------------------------------------------

# 5. Streaming Strategy

Backend handles streaming coordination.

Recommended approach: - Buffer tokens until sentence boundary OR 40
characters - Send chunk to TTS - Stream PCM audio chunk - Continue until
completion

This ensures: - Low latency - Smooth speech - Controlled CPU usage

------------------------------------------------------------------------

# 6. Memory Architecture

## 6.1 Frontend (Short-Term)

SQLite: - turn_log - device_state

Retention: 200 turns max No raw audio storage.

## 6.2 Backend (Long-Term)

PowerMem: - UserProfile - Memory entries - Daily summaries

Policy: - Store summarized insights only - No raw audio persistence -
Kid-safe memory filtering

------------------------------------------------------------------------

# 7. Kid Mode (Default Enabled)

Backend-enforced constraints: - No violent/sexual/self-harm content - No
unsafe instructions - No PII collection - Simplified language style

Output moderation required before streaming.

------------------------------------------------------------------------

# 8. WebSocket Protocol

Envelope: - type - sessionId - turnId - traceId - ts

Core messages: - hello / hello_ack - audio_file (binary) - asr_result -
llm_stream - tts_audio_stream - turn_end - error - interrupt

------------------------------------------------------------------------

# 9. Non-Functional Requirements

Latency target: - \< 4 seconds (90 percentile)

Reliability: - Reconnect within 10 seconds - Idempotent turn handling

Observability: - traceId across entire pipeline - Latency breakdown per
turn

------------------------------------------------------------------------

# 10. Error Handling

ASR failure → return IDLE LLM timeout → fallback apology response TTS
failure → text-only mode Backend disconnect → frontend shows offline
animation

------------------------------------------------------------------------

# 11. Acceptance Criteria

1.  Mic disabled during playback
2.  Interrupt works instantly
3.  Memory persists across sessions
4.  Kid Mode validated
5.  No echo observed
6.  Reconnect works automatically

------------------------------------------------------------------------

# 12. Out of Scope (Future Versions)

-   Vision processing
-   PvP battle
-   Evolution form system
-   Offline LLM fallback
-   Multi-user world interaction

------------------------------------------------------------------------

# 13. Design Principle Summary

Frontend = Console\
Backend = Brain

All intelligence lives in backend.

------------------------------------------------------------------------

End of Document
