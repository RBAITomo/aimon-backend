# AI-MON System Architecture

**Last Updated:** 2026-02-26
**Version:** v0.2+ (Phase 2c: Location Travel + Phase 3: Adaptive Interests + Phase 7: Game Loop & SFX + Phase 8: World Lore + Phase 10: Offline Resilience + Phase 11: Combat & Shards + Phase 12: Continuous Conversation Mode)
**Status:** Phase 12 VAD Integration In Progress

## System Overview

AI-MON is a distributed voice-driven AI companion system for Raspberry Pi with a clean microservices architecture. The refactored backend (`aimon-backend`) coordinates real-time voice conversations, pet game mechanics, turn-based combat system, and dynamic location travel within Sweet Dominion. The frontend (`aimon-frontend`) implements interactive pet gameplay: SFX feedback, badge notifications, quest system, evolution/regression/transformation sequences, combat battles, location-based background swapping, and 4-layer compositor rendering with TTS ducking on a 240x280 LCD display. Phase 12 replaces push-to-talk with continuous conversation mode using WebRTC VAD for automatic speech detection and auto-resume after playback.

```
┌─────────────────────────────────────────────────────────────────┐
│                    AIMON SYSTEM (v0.2)                          │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ Client (Pi Zero) — aimon-frontend                        │   │
│  │ ┌─────────────────────────────────────────────────────┐  │   │
│  │ │ Display: 4-Layer Compositor (240x280 LCD ST7789)  │  │   │
│  │ │  1. Background (cached)                           │  │   │
│  │ │  2. Status bars (cached until dirty)              │  │   │
│  │ │  3. Character sprite (per-frame blit)             │  │   │
│  │ │  4. Speech bubble (per-frame)                     │  │   │
│  │ └─────────────────────────────────────────────────────┘  │   │
│  │ - Button input (single press toggle: continuous mode+VAD) │   │
│  │   Double-press triggers camera feed detection            │   │
│  │ - Audio I/O (OPUS/PCM16)                                │   │
│  │ - Camera: OV5647 CSI → JPEG → Gemini 2.5 Flash (direct)│   │
│  │   Food detected → pet_feed_confirm → backend applies    │   │
│  └──────────────────────────────────────────────────────────┘   │
│           │                                                 │   │
│           │                                                 │   │
│           │ OPUS frames (48kHz)                             │   │
│           │ PCM16 audio (16kHz playback)                    │   │
│           │                                                 │   │
│           ▼                                                 │   │
│  ┌─────────────────────────────────────────────────────────┴─┐ │
│  │           aimon-backend (Java/Quarkus)                    │ │
│  │  ┌─────────────────────────────────────────────────┐      │ │
│  │  │  WebSocket Handler (v4 Protocol)               │      │ │
│  │  │  - Session management                          │      │ │
│  │  │  - Audio frame buffering                       │      │ │
│  │  │  - Continuous conversation state machine        │      │ │
│  │  │  - VAD integration (Phase 12)                  │      │ │
│  │  │  - Interrupt handling                          │      │ │
│  │  └────────────────┬────────────────────────────────┘      │ │
│  │                   │                                        │ │
│  │  ┌────────────────▼────────────────────────────────────┐  │ │
│  │  │         Core Service Layer                         │  │ │
│  │  │                                                     │  │ │
│  │  │  ┌──────────────────────────────────────────────┐  │  │ │
│  │  │  │ Audio Pipeline                              │  │  │ │
│  │  │  │  OPUS decode → downsample 48→16kHz          │  │  │ │
│  │  │  │                                              │  │  │ │
│  │  │  │ ↓                                             │  │  │ │
│  │  │  │ Google STT Service                           │  │  │ │
│  │  │  │  Streams PCM16 → transcript                  │  │  │ │
│  │  │  └───────────────┬────────────────────────────┘  │  │ │
│  │  │                  │                                │  │ │
│  │  │  ┌───────────────▼────────────────────────────┐  │  │ │
│  │  │  │ Conversation Process Service              │  │  │ │
│  │  │  │  - Personality injection                  │  │  │ │
│  │  │  │  - Memory context retrieval (PowerMem)    │  │  │ │
│  │  │  │  - Kid Mode safety filtering              │  │  │ │
│  │  │  │                                             │  │  │ │
│  │  │  │ ↓                                            │  │  │ │
│  │  │  │ LiteLLM AI Service                          │  │  │ │
│  │  │  │  Streams LLM tokens (OpenAI/Anthropic)     │  │  │ │
│  │  │  │                                             │  │  │ │
│  │  │  │ ↓                                            │  │  │ │
│  │  │  │ Sentence Splitter Service                   │  │  │ │
│  │  │  │  Chunks response for TTS                    │  │  │ │
│  │  │  └───────────────┬────────────────────────────┘  │  │ │
│  │  │                  │                                │  │ │
│  │  │  ┌───────────────▼────────────────────────────┐  │  │ │
│  │  │  │ TTS Provider Service (Circuit Breaker)     │  │  │ │
│  │  │  │                                             │  │  │ │
│  │  │  │  Primary: VieNeu TTS (Vietnamese)          │  │  │ │
│  │  │  │     ↓ (on failure)                          │  │  │ │
│  │  │  │  Fallback: Google TTS                      │  │  │ │
│  │  │  │                                             │  │  │ │
│  │  │  │ ↓                                            │  │  │ │
│  │  │  │ Response Stream Service                     │  │  │ │
│  │  │  │  PCM16 chunks → client (WebSocket binary)  │  │  │ │
│  │  │  └──────────────────────────────────────────┘  │  │ │
│  │  │                                                     │  │ │
│  │  │  Memory Context Layer (PowerMem Integration)       │  │ │
│  │  │  - PowerMemService (retrieval & observation)       │  │ │
│  │  │  - ContextRetrievalService                         │  │ │
│  │  │  - MemoryContextFormatter                          │  │ │
│  │  │                                                     │  │ │
│  │  └─────────────────────────────────────────────────┘  │  │ │
│  │                                                        │  │ │
│  └────────────────────────────────────────────────────────┘  │ │
│           │              │              │                     │ │
│           │              │              │                     │ │
│   HTTP    │              │              │      HTTP          │ │
│  REST     │              │              │     REST           │ │
│           │              │              │                     │ │
│  ┌────────▼───┐  ┌───────▼─────┐  ┌───▼─────────┐  ┌────────▼──┐
│  │  PostgreSQL │  │ LiteLLM     │  │ memoryservice│  │ VieNeu TTS│
│  │  (pgvector) │  │ Proxy       │  │ (PowerMem)   │  │ (GPU)     │
│  │             │  │             │  │              │  │           │
│  │ Database    │  │ LLM broker  │  │ Memory MCP   │  │ Vietnamese│
│  │ (5432)      │  │ (4000)      │  │ (8003)       │  │ (5001)    │
│  └─────────────┘  └─────────────┘  └──────────────┘  └───────────┘
│                                                                  │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │ Gemini API (google.generativeai.com) — Pi-direct only   │    │
│  │  - Called by VisionAnalysisService on Pi (google-genai) │    │
│  │  - Model: gemini-2.5-flash                              │    │
│  │  - Input: JPEG bytes from OV5647 camera                 │    │
│  │  - Output: JSON { is_food, food_name, description }     │    │
│  └─────────────────────────────────────────────────────────┘    │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

---

## Frontend Architecture (aimon-frontend)

### 4-Layer Display Compositor (+ Food Sprite Overlay)

**Purpose:** Efficient rendering of pet UI on Pi Zero 2's 240x280 LCD (ST7789) at 30 FPS, with food sprite animation overlay.

**Architecture:**
```
[Per-Frame Loop]
  ├─ Tick counter (animation timing)
  ├─ PetState dataclass (stage, animation, stats, level, XP, mood)
  │
  ├─ Layer 1-2: Pre-composited BASE (only rebuild on stat change)
  │   ├─ Background image (static, cached)
  │   ├─ Hunger/Energy/Happiness stat bars (top row)
  │   └─ Level/XP bar (bottom row)
  │
  ├─ Layer 3: Character sprite (per-frame blit)
  │   ├─ SpriteSheetManager loads frames by stage & animation
  │   ├─ Supports Coneko-egg-form & Coneko-baby-Form assets
  │   └─ Fallback to code-generated placeholder shapes
  │
  ├─ Layer 4: Speech bubble (per-frame, only when text active)
  │   ├─ Auto-scrolling text overlay
  │   └─ Fade in/out timing
  │
  └─ Layer 5: Food sprites (per-frame, managed by FoodSpriteManager)
      ├─ Up to 3 on-screen food sprites (FIFO queue)
      ├─ Tween animation (easing: ease-in-out)
      ├─ Auto-eat when collision/timer triggers
      └─ Sprite key matched to food sprite filenames via Gemini vision
```

**Key Components:**

| Module | Purpose | LOC |
|--------|---------|-----|
| `pet-state-model.py` | PetState dataclass (immutable-style, mutate from main loop only) | 24 |
| `layer-compositor.py` | 4-layer compositor, manages base surface caching | 155 |
| `sprite-sheet-manager.py` | Frame-based sprite loader, stage lifecycle | 150+ |
| `stat-bar-renderer.py` | Hunger/energy/happiness bars + XP/level bar | 140+ |
| `speech-bubble-renderer.py` | Auto-scrolling text bubble with fade timing | 100+ |
| `food-sprite-manager.py` | Food sprite animation & management (FIFO queue, tween anim, auto-eat) | 120+ |
| `hardware/camera-capture-service.py` | OV5647 CSI camera → JPEG bytes (picamera2, no disk I/O) | ~108 |
| `hardware/vision-analysis-service.py` | JPEG → Gemini 2.5 Flash → food detection JSON (google-genai SDK) | ~104 |

**Performance Optimization:**

- **Pre-composited base:** Layers 1-2 combined into reusable surface
- **Dirty-region caching:** Only rebuild base when stats change
- **Per-frame blits:** 2-3 blits per frame (base + character + optional bubble)
- **Target:** 30 FPS on Pi Zero 2 (ARM Cortex-A53 @ 1.2 GHz)

**Integration with DisplayEngine:**

```python
class DisplayEngine:
    def render(tick, pet_state, text=None):
        # Delegates to LayerCompositor
        done = self._compositor.render_frame(
            self._surface, tick, pet_state, text
        )
        # Convert to RGB565 and send to LCD via SPI
        self._blit_to_lcd()
        return done
```

**State Machine Integration:**

- Maintains thread-safe snapshot of PetState
- Updates animation based on conversation state (IDLE→idle, LISTENING→listening, ANSWER→speaking)
- Drives stat changes (hunger increase, energy decrease, happiness updates)

---

## Architecture Tiers

### 1. Presentation Tier (Client — aimon-frontend)

**Device:** Raspberry Pi Zero 2 (or equivalent)

**Hardware:**
- ST7789 LCD display (240x280)
- Physical button (single press: toggle continuous conversation mode; double-press: camera feed trigger)
- Microphone (audio input)
- Speaker (audio output)
- OV5647 CSI camera (640x480 JPEG, for food detection)
- WiFi connectivity
- SPI bus for LCD

**Software Modules:**

| Module | Purpose |
|--------|---------|
| `state_machine.py` | Main orchestrator: IDLE→LISTENING→ASR→ANSWER→EMOTION + quest/evolution overlays. Double-press triggers camera. |
| `state/pet-event-handler.py` | Pet WebSocket callbacks: status, feed, badge, evolution, transform, warning, regression, quest. Integrates food sprite manager. |
| `display_engine.py` | Thin wrapper over LayerCompositor, manages LCD output via SPI (RGB565). Badge popup & food sprite rendering. |
| `display/layer-compositor.py` | 4-layer (+food sprites) rendering (background, stats, character, speech, food overlay). Dirty-region caching. |
| `display/badge-popup-renderer.py` | Temporary badge notification overlay (3-second popup with SFX trigger). |
| `display/food-sprite-manager.py` | Food sprite animation & queue mgmt: FIFO (max 3), tween animation, auto-eat on hunger/timer. Sprite key from Gemini vision. |
| `audio/audio_capture.py` | Record audio continuously, VAD integration for speech detection (Phase 12). Send OPUS frames to backend. |
| `audio/voice-activity-detector.py` | WebRTC VAD engine: detects speech end automatically for continuous mode (Phase 12). |
| `audio/audio_playback.py` | Play PCM16 audio chunks from TTS, stop on interrupt. |
| `audio/sfx-manager.py` | SFX mixer: 3 channels (primary, notify, ambient), TTS ducking, OGG pre-loading. |
| `network/ws_client.py` | WebSocket v4 client: hello → audio_start/frames/stop → asr/llm/tts/pet events (21 msg types). |
| `hardware/camera-capture-service.py` | OV5647 CSI camera capture → raw JPEG bytes (picamera2). Rate-limited (30s). |
| `hardware/vision-analysis-service.py` | Gemini 2.5 Flash vision: JPEG → `{is_food, food_name, description}` JSON. |
| `storage/turn_logger.py` | Log conversations for debugging & analytics. |

**Protocol:** WebSocket v4+ (continuous conversation mode + pet events; Phase 12 VAD integration)
- Binary OPUS frames (input, 48kHz) / PCM16 chunks (output, 16kHz)
- JSON control + pet messages (21 message types total)
- Keepalive (ping/pong)
- Pet events: pet_status, pet_feed_result, badge_earned, pet_evolution, pet_transform, pet_transform_end, pet_warning, pet_regression, quest_start, camera_result

---

### 2. Application Tier (aimon-backend)

**Technology:** Java 21 + Quarkus 3.24.5

**Core Responsibilities:**
- Session management (per robot)
- Audio input buffering & decoding
- STT orchestration
- Conversation flow control
- Memory integration
- LLM interaction
- TTS orchestration
- Audio output streaming
- Pet system management (stats, feeding, XP via `pet_feed_confirm` from Pi)

**Note (Phase 8):** Vision analysis was moved off the backend. The backend no longer handles `camera_photo` messages or calls any vision API. The Pi does food detection directly via Gemini; the backend only receives `pet_feed_confirm` with the resolved `food_name` and applies the hunger update.

**Key Services (by layer):**

#### A. Transport Layer
| Service | Purpose |
|---------|---------|
| `AimonWebSocket` | WebSocket v4 handler, session mgmt |

#### B. Audio Layer
| Service | Purpose |
|---------|---------|
| `AudioPipelineService` | OPUS frame collection & STT routing |
| `OpusAudioProcessor` | OPUS decode, resample 48→16kHz |
| `OpusCodecService` | Low-level OPUS codec wrapper |

#### C. Speech-to-Text
| Service | Purpose |
|---------|---------|
| `GoogleSttService` | Google Cloud STT API client |

#### D. Conversation Layer
| Service | Purpose |
|---------|---------|
| `ConversationProcessService` | Orchestrator (personality, memory, LLM, safety) |
| `ConversationSessionManager` | Session lifecycle |

#### E. Memory Layer
| Service | Purpose |
|---------|---------|
| `PowerMemService` | MCP client for 3-layer memory |
| `ContextRetrievalService` | Memory search & formatting |
| `MemoryContextFormatter` | Prompt context builder |
| `ObservationBuffer` | Collects facts during turn |

#### F. AI Layer
| Service | Purpose |
|---------|---------|
| `LiteLlmAIService` | LLM streaming orchestrator |

#### G. Text-to-Speech
| Service | Purpose |
|---------|---------|
| `TtsProviderService` | Circuit breaker, failover logic |
| `VieNeuTtsService` | Vietnamese TTS (primary) |
| `GoogleTtsStreamingService` | English/Vietnamese TTS (fallback) |
| `SentenceSplitterService` | Response chunking for TTS |

#### H. Response Streaming
| Service | Purpose |
|---------|---------|
| `ResponseStreamService` | PCM16 chunk streaming to client |

#### I. Configuration
| Class | Purpose |
|-------|---------|
| `AIConfig` | Personality, prompts, feature flags |
| `ApplicationConfig` | Quarkus bean definitions |

---

### 3. Integration Tier (External Services)

#### A. PostgreSQL 16 (Database)
- **Port:** 5432
- **Purpose:** Persistent storage (users, parents, banned keywords)
- **Extension:** pgvector (for PowerMem)
- **Schema:** Minimal (3 core tables + PowerMem)

#### B. LiteLLM (LLM Broker)
- **Port:** 4000
- **Purpose:** LLM routing & load balancing
- **Supported Models:** OpenAI (GPT-4, GPT-4o-mini), Anthropic, others
- **Protocol:** OpenAI-compatible REST API
- **Feature:** Streaming responses, rate limiting, token counting

#### C. memoryservice (PowerMem MCP)
- **Port:** 8003
- **Purpose:** 3-layer memory system
- **Components:**
  - Short-term: Last 5 exchanges
  - Long-term: Vector-searched facts
  - Episodic: Timestamped observations
- **Integration:** REST API via `PowerMemClient`

#### D. VieNeu-TTS (GPU TTS)
- **Port:** 5001
- **Purpose:** High-quality Vietnamese speech synthesis
- **Voice:** Ngoc (natural, personalized)
- **Output:** PCM16 audio chunks
- **Hardware:** Requires NVIDIA GPU
- **Fallback:** Google TTS if unavailable

#### E. Google Cloud Services
- **STT API:** Speech-to-text (streaming)
- **TTS API:** Text-to-speech (fallback)
- **Auth:** Service account JSON credentials

---

## Data Flow (Full Conversation Turn)

### Step 1: Connection & Handshake
```
Client → Server: hello {version: "v4", device_id: "pi-001", ...}
Server → Client: hello_ack {session_id: "s123", ...}
```

### Step 2: Audio Input (Push-to-Talk)
```
Client → Server: audio_start {format: "opus", sample_rate: 48000}
Client → Server: [binary OPUS frame 1]
Client → Server: [binary OPUS frame 2]
...
Client → Server: [binary OPUS frame N]
Client → Server: audio_stop {}

Processing:
  1. OpusAudioProcessor collects frames
  2. Decodes OPUS → PCM16 (48kHz)
  3. Resamples to 16kHz (mono)
  4. Validates audio quality
```

### Step 3: Speech-to-Text
```
Processing:
  1. AudioPipelineService sends PCM16 to GoogleSttService
  2. Google Cloud STT API recognizes speech
  3. Returns transcript text

Server → Client: asr_result {text: "Xin chào", confidence: 0.95}
```

### Step 4: Conversation Generation
```
Processing:
  1. ConversationProcessService receives transcript
  2. Load child interests (once per session, cached):
     - AdaptiveInterestService retrieves recent PowerMem timeline
     - TopicClassifier re-classifies user messages (keyword matching)
     - Aggregates topic frequencies, applies diversity cap (default: 60%)
     - Returns top 3 topics (configurable)
     - Cached in session to avoid repeated PowerMem calls
  3. PowerMemService retrieves memory context:
     - Short-term: last 5 exchanges
     - Long-term: vector-searched facts
     - Episodic: relevant observations
  4. AIConfig injects personality traits
  5. Adaptive interests prompt injected (Phase 3):
     - Formats cached top topics as hint: "[SỞ THÍCH CỦA BẠN: topic1, topic2, topic3 — ...]"
     - Signals to Mon to mention topics naturally when appropriate
  6. WorldLoreService injects world context (Phase 8):
     - Fetches AMBIENT lore entries from world_lore table
     - Filters by: active world, pet level, shard type
     - Ranks by interest tag overlap with child interests
     - Limits to max-inject entries (default: 3)
     - Formats into prompt context (Vietnamese)
  7. ContextRetrievalService formats prompt:
     ```
     System: [personality + safety guidelines]
     Interests: [child's detected interests hint]
     World: [world lore facts]
     Memory: [retrieved context]
     User: "Xin chào"
     ```
  8. LiteLlmAIService calls LLM (streaming):
     - OpenAI GPT-4o-mini by default
     - Streams tokens in real-time
```

### Step 5: Safety Filtering
```
Processing:
  1. ConversationProcessService checks kid_mode flag
  2. BannedKeywordService checks response against banned_keywords
  3. If unsafe: redact or replace response
  4. Continue with safe response
```

### Step 6: Sentence Splitting
```
Processing:
  1. SentenceSplitterService chunks LLM response:
     Input: "Xin chào! Tôi là AI-MON. Hôm nay bạn thế nào?"
     Output: [
       "Xin chào!",
       "Tôi là AI-MON.",
       "Hôm nay bạn thế nào?"
     ]
```

### Step 7: Text-to-Speech
```
Processing:
  For each sentence:
    1. TtsProviderService tries VieNeuTtsService (primary)
       - Calls VieNeu-TTS GPU service (port 5001)
       - Voice: Ngoc (Vietnamese, natural)
       - Returns: PCM16 audio chunks

    2. If VieNeu fails:
       - Fallback to GoogleTtsStreamingService
       - Returns: PCM16 audio chunks

    3. Streams to client via ResponseStreamService

Server → Client: tts_start {text: "Xin chào!"}
Server → Client: [binary PCM16 chunk 1]
Server → Client: [binary PCM16 chunk 2]
...
Server → Client: tts_stop {has_more: true}

Server → Client: tts_start {text: "Tôi là AI-MON."}
...
```

### Step 8: Memory Storage & Topic Classification
```
Processing:
  1. TopicClassifier analyzes user message (Phase 3):
     - Keyword matching against 15 Vietnamese topic categories
     - LLM fallback if no keyword match (~5% of messages)
     - Returns classified topics
  2. ObservationBuffer collects key facts from response
  3. PowerMemService records with topic metadata:
     - Episodic: Timestamped, conversation context + topics
     - Long-term: Vectorized for future retrieval
     - Topics stored in metadata for future interest re-classification
  4. PowerMem MCP service persists to database
```

### Step 9: Conversation Complete
```
Server → Client: turn_end {turn_id: "t456", ...}

Ready for new turn or interrupt.
```

---

## Game Loop Mechanics (Phase 7)

**Pet State Updates & SFX Feedback**

Frontend receives pet events via WebSocket and updates display in real-time:

```
[Backend sends pet_status]
    ↓
PetEventHandler.on_pet_status()
    ├─ Update hunger/energy/happiness/level/XP
    ├─ Mark dirty region for stat bar re-render
    └─ SfxManager: no SFX (passive update)

[Backend sends pet_feed_result] (on successful feed)
    ↓
PetEventHandler.on_pet_feed_result()
    ├─ SfxManager.play("eat")
    ├─ Update local hunger stat
    └─ Display refreshes immediately

[Backend sends badge_earned]
    ↓
PetEventHandler.on_badge_earned()
    ├─ SfxManager.play("badge") on notify channel
    ├─ BadgePopupRenderer.show(name, description)
    └─ Popup displays for 90 frames (3s @ 30fps)

[Backend sends pet_evolution]
    ↓
PetEventHandler.on_pet_evolution()
    ├─ SfxManager.play("evolution")
    ├─ Flag evolution animation pending
    └─ StateMachine.tick() handles animation sequence

[Backend sends quest_start]
    ↓
PetEventHandler.on_quest_start()
    ├─ SfxManager.play("quest") on notify channel
    ├─ Store question text
    └─ Display as speech bubble, child responds via normal voice flow
```

**SFX Channel Management**

Three dedicated mixer channels with TTS ducking:
- **Channel 1 (primary):** eat, level-up, evolution, transform (high priority)
- **Channel 2 (notify):** badge, quest (medium priority, interrupts ambient)
- **Channel 3 (ambient):** warning, regression (can overlap)

During TTS playback: SfxManager.duck_for_tts() reduces all SFX to 30% volume.
After TTS ends: SfxManager.unduck() restores 100% volume.

---

## Camera Vision Feed Flow (Phase 8)

Vision analysis runs entirely on the Pi — no camera data traverses the WebSocket.

### Double-Press to Feed
```
[Button double-press detected (within 500ms)]
    ↓
CameraCaptureService.capture_bytes()     ← picamera2, OV5647 CSI, 640x480 JPEG
    ↓
VisionAnalysisService.analyze(jpeg_bytes) ← google-genai SDK, Gemini 2.5 Flash
    │
    ├─ is_food=true:
    │   food_name = "bún bò"              ← Vietnamese name returned by Gemini
    │   Display bubble: "bún bò ngon quá! Cho mình ăn nhé?"
    │   ws_client.send_feed_confirm(food_name)
    │       → WS msg: { type: "pet_feed_confirm", food_name: "bún bò" }
    │
    └─ is_food=false:
        Display bubble: <child-friendly description>
        (no WS message sent)

[Backend receives pet_feed_confirm]
    ↓
PetMessageHandler.handleFeedConfirm()
    ├─ hungerReduction = 25 (base)
    ├─ PetProfileService.applyFeed(userId, foodName, hungerReduction)
    └─ Sends: { type: "pet_feed_result", success: true, food_name, hunger_reduction }

[Frontend receives pet_feed_result]
    ↓
state_machine._on_pet_feed_result()      ← updates local pet stats display
```

### Key Design Decisions
- **No base64 over WS:** JPEG bytes never cross the WebSocket; frame limit stays at 64KB.
- **Pi-direct Gemini call:** Reduces backend complexity; eliminates `VisionAnalysisClient`, `VisionAnalysisResult`, `VisionConfig` from backend.
- **Rate limiting:** Both camera (30s) and vision service enforce separate rate limits to prevent API abuse.
- **Env vars on Pi only:** `GEMINI_API_KEY` and `GEMINI_MODEL` are Pi-side env vars (not in backend `.env`).

### Gemini Vision Request Format
```python
# Prompt returns JSON only (no markdown fences)
{
  "is_food": true,
  "food_name": "<Vietnamese name>",
  "description": "<brief, child-appropriate>",
  "sprite_key": "<matching-food-sprite-filename>"   # Phase 9: Added for UX enhancement
}
```

### Food Sprite Manager (Phase 9 Enhancement)

**VisionAnalysisService Extension:**
- Gemini now returns `sprite_key` matching food sprite filenames (e.g., "apple", "rice", "milk_bottle")
- Enables visual food sprite animation overlay on pet display

**FoodSpriteManager Workflow:**
```
[Backend sends pet_feed_confirm with sprite_key]
    ↓
[Frontend: PetEventHandler.on_pet_feed_confirm()]
    ├─ Extract sprite_key from message
    ├─ FoodSpriteManager.queue_food(sprite_key)
    │   ├─ Add to FIFO queue (max 3 sprites)
    │   ├─ Schedule tween animation (start position → mouth)
    │   └─ Set auto-eat timer
    │
    ├─ Display renders food sprite with per-frame tween
    │   ├─ Position updates based on easing curve (ease-in-out)
    │   └─ Rendered on Layer 5 (above character, below speech bubble)
    │
    └─ When hunger is high OR timer expires:
        ├─ Auto-eat: pet sprite plays eat animation
        ├─ SfxManager.play("eat")
        ├─ Hunger reduced
        └─ Food sprite removed from queue
```

**Sprite Assets:**
- Located: `aimon-frontend/assets/food/`
- Format: PNG (32x32 or 48x48)
- Filename convention: kebab-case (matches `sprite_key` from Gemini)
- Examples: `apple.png`, `rice.png`, `milk-bottle.png`

**Display Integration:**
- Layer 5 (new): Food sprites rendered after character (Layer 3) but before/with speech bubble (Layer 4)
- Tween animation: 60 frames (~2 seconds @ 30 FPS) from top-center to mouth area
- Collision detection: Simplified, position-based trigger
- Auto-eat fallback: If pet hunger rises or 5-second timeout, auto-consume

---

## WiFi QR Manager (Phase 10+)

**Purpose:** Enable quick WiFi credential scanning in offline mode without manual text entry.

### Architecture

**Hardware Integration:**
```
[Button Long-Press ≥1.5s in Offline Mode]
    ↓
[LED turns bright cyan — WiFi QR scan active]
    ↓
[CameraCaptureService.capture_bytes() — OV5647 CSI camera]
    ↓
[WifiManager.scan_qr_for_wifi(jpeg_bytes) — pyzbar QR decode]
    ├─ Regex pattern: WIFI:S:SSID;T:security;P:password;
    ├─ Returns: {ssid, type, password} or None
    │
    ├─ SUCCESS:
    │   ├─ WifiManager.add_profile() → wifi-profiles.json (dedup by SSID)
    │   ├─ WifiManager.connect_to_profile() → nmcli device wifi connect
    │   ├─ Display bubble: "Kết nối thành công!" (Connected!)
    │   └─ LED: return to appropriate state (green for LISTENING if voice available)
    │
    └─ FAILURE:
        ├─ Display: "Không tìm thấy mã QR" (No QR found)
        └─ Return to offline mode
```

### Key Components

**WifiManager (hardware/wifi-manager.py):**
```python
class WifiManager:
    def scan_qr_for_wifi(jpeg_bytes: bytes) -> dict | None
        # Decode WiFi QR from JPEG, return {ssid, type, password}

    def add_profile(ssid: str, password: str) -> None
        # Save profile, dedup by SSID, update if exists

    def connect_to_profile(ssid: str, password: str) -> bool
        # Try existing NM connection, then add new and connect

    def get_profiles() -> list
        # Load saved profiles from JSON
```

**QR Format Support:**
- Standard WiFi QR: `WIFI:S:<SSID>;T:<WPA|WEP>;P:<password>;`
- Lazy imports: `pyzbar` and `PIL` only loaded on demand (dev machines may not have these)
- Timeout: 15 seconds for QR scan operation

### Configuration

**Constants (in `config.py`):**
```python
WIFI_PROFILES_PATH = "data/wifi-profiles.json"      # Profile persistence
WIFI_QR_SCAN_TIMEOUT_S = 15                         # QR decode timeout
LED_WIFI_SCAN = (0, 200, 255)                       # Bright cyan during scan
LONG_PRESS_THRESHOLD_MS = 1500                      # Hold ≥1.5s for QR
```

**Dependencies:**
- `pyzbar>=0.1.9` — QR code decoder
- `Pillow>=10.0.0` — Image processing
- `libzbar0` — System library (apt-get)
- `nmcli` — NetworkManager CLI (system tool)

### Offline Trigger Flow

```python
# In state_machine.py / button handler
if offline_mode and button_press_duration_ms >= LONG_PRESS_THRESHOLD_MS:
    led.set_color(LED_WIFI_SCAN)  # Bright cyan

    jpeg_bytes = camera_capture_service.capture_bytes()
    result = wifi_manager.scan_qr_for_wifi(jpeg_bytes)

    if result:
        wifi_manager.add_profile(result['ssid'], result['password'])
        connected = wifi_manager.connect_to_profile(
            result['ssid'], result['password']
        )
        if connected:
            # WiFi restored, trigger reconnection to backend
            display_text("Kết nối thành công!")
        else:
            display_text("Không thể kết nối")
    else:
        display_text("Không tìm thấy mã QR")
```

### Design Decisions

1. **Lazy imports:** pyzbar/PIL only imported when scan triggered (dev machines may lack these)
2. **JPEG bytes only:** No disk I/O, direct memory processing
3. **Dedup by SSID:** Prevents duplicate profiles, updates password if rescanned
4. **Last-used tracking:** Stored in JSON for quick reconnect
5. **nmcli integration:** Leverages NetworkManager CLI for WiFi connect
6. **Rate limiting:** Camera already rate-limited (30s); QR scan timeout (15s) prevents hangs
7. **Visual feedback:** Bright cyan LED + text bubble guides user experience

---

## Adaptive Interest System (Phase 3)

**Purpose:** Detect and track child's evolving interests from recent conversations, enabling dynamic prompt injection and lore ranking based on observed preferences.

### Architecture

**Topic Categories (15 total):**
```
khung-long (Dinosaurs), vu-tru (Space), dong-vat (Animals), xe-co (Vehicles),
sieu-anh-hung (Superheroes), am-nhac (Music), nghe-thuat (Art), nau-an (Cooking),
the-thao (Sports), co-tich (Fairy Tales), truong-hoc (School), gia-dinh (Family),
thien-nhien (Nature), khoa-hoc (Science), sach-truyen (Books/Comics)
```

**Classification Strategy:**
1. **KeywordOnly (fast):** Check user message against Vietnamese keyword lists (< 1ms)
2. **LLM Fallback:** If no keyword match, call LLM for semantic classification (~5% of messages)
3. **Cached Results:** Topics stored in PowerMem observation metadata for future re-use

**Services:**

**TopicClassifier** (~130 LOC):
- `classify(message)`: Keyword match → LLM fallback → topic codes
- `classifyKeywordOnly(message)`: Fast keyword-only (used for bulk re-classification of timeline)
- `TOPIC_KEYWORDS`: Map<String, List<String>> — Vietnamese keywords per category
- `TOPIC_DISPLAY_NAMES`: Map<String, String> — Display labels for UI/prompts

**AdaptiveInterestService** (~100 LOC):
- `getTopInterests(robotId)`: Fetches recent PowerMem timeline, re-classifies content, aggregates topic counts
- Applies **diversity cap** (default: 60% threshold per topic) to prevent repetition dominance
- Returns top N topics (default: 3) ranked by frequency
- Results cached in `ConversationSession.cachedTopInterests` for session lifetime

**Configuration:**
```properties
interest.observation-lookback=30        # Days of timeline to consider
interest.max-topics=3                   # Top N topics to return
interest.diversity-cap=0.6              # Max 60% for single topic frequency
```

**Injection Point:**

In `ConversationProcessService.buildEnhancedPrompt()`:
1. Load interests once per session (cached in `ConversationSession.cachedTopInterests`)
2. Call `adaptiveInterestService.formatInterestsPrompt(topicCodes)`
3. Inject into system prompt before LLM call:
   ```
   [SỐ THÍCH CỦA BẠN: Khủng long, Vũ trụ, Động vật — đề cập tự nhiên khi phù hợp]
   ```
4. Gracefully continue if retrieval fails (catch-and-log)

**Topic Recording (Phase 3):**

In `recordToPowerMemAsync()`:
- `TopicClassifier.classify()` analyzes user message
- Topics stored in MemoryMetadataBuilder: `.topics(List<String> topics)`
- PowerMem observation includes `topics` and optional `topic_sentiment` fields
- Enables future re-classification without LLM re-processing

---

## World Lore System (Phase 8)

**Purpose:** Inject contextual world-building facts into conversations, enabling pet to naturally share lore that creates immersive world experience for children.

### Architecture

**Entity Structure:**
```
world_lore table:
  - world_code: "COTTON_LAND", "DEEP_FOREST", etc. (future worlds)
  - title: Lore entry name
  - category: "GEOGRAPHY", "CHARACTER", "HISTORY", "ITEM", etc.
  - content: Prompt-friendly Vietnamese text
  - min_level: Pet level required to unlock (default: 3)
  - interest_tags: String[] — tagged for interest-based ranking
  - shard_type: "AMBIENT" (active), "SIDE"/"MILESTONE" (Phase 2b)
  - is_active: Boolean flag for enabling/disabling entries

user_shards table:
  - Tracks which lore entries child has encountered (Phase 2b planned)
  - source: "EXPLORATION", "DIRECT", etc.
  - unlocked_at: Timestamp

pet_profiles table additions:
  - active_world: Currently active world (default: "COTTON_LAND")
  - current_location: Pet's location within world (default: "SWEET_DOMINION")
```

### Services

**WorldLoreService:**
- `getUnlockedLore(worldCode, petLevel, topInterests)`: Fetches eligible AMBIENT entries
- `formatLorePrompt(entries)`: Formats lore into Vietnamese conversation text
- Interest-based ranking: Prioritizes entries matching child's detected interests
- Graceful fallback: If lore retrieval fails, continues conversation without lore

**Repository:**
- `WorldLoreRepository.findAmbientUnlocked()`: Queries eligible entries by world, level, shard type

### Configuration

**Property:**
```properties
world.lore.max-inject=3    # Max entries injected per turn (configurable)
```

**Example Lore Injection:**
```
=== Thế giới của Mon (điều Mon có thể tự nhiên chia sẻ) ===
- Xứ Bông Hồng nằm giữa những cánh đồng bông trắng xinh đẹp.
- Ở đây có rất nhiều thứ ngon lành mà Mon yêu thích.
- Mỗi mùa, xứ Bông Hồng lại có những lễ hội vui nhộn.
Hãy nhắc đến những điều này tự nhiên trong cuộc trò chuyện khi phù hợp.
```

### Injection Point

In `ConversationProcessService.buildEnhancedPrompt()`:
1. Check pet stage (skip if EGG)
2. Retrieve active_world from pet_profiles
3. Call `worldLoreService.getUnlockedLore(worldCode, petLevel, interests)`
4. Format and insert into system prompt before LLM call
5. Gracefully continue if retrieval fails (catch-and-log)

### Scalability (Future Phases)

**Phase 2b — User Shard Tracking:**
- Track which lore entries user has discovered
- Prevent repetition via user_shards join
- Enable progression-based storytelling

**Phase 2c — Multi-World Support:**
- Seamless world switching (e.g., "Cotton Land" → "Deep Forest")
- Location-based context (current_location field)
- World-specific NPCs and interactions

**Data Seeding:**
- `world_lore_cotton_land_seed.sql`: Pre-populated Cotton Land lore (30+ entries)
- Categories: geography, characters, items, events, history

---

## Location Travel System (Phase 2c)

**Purpose:** Enable dynamic sub-location travel within Sweet Dominion, driven by LLM-generated travel markers and interest-based suggestions. Players navigate 5 themed locations with distinct visual backgrounds and atmospheric context.

### Architecture

**Sub-Locations (5 within Sweet Dominion):**

| Location | Vietnamese Alias | Background PNG | Interest Tags | Description |
|----------|------------------|----------------|---------------|-------------|
| WHIPCREAM_SPIRE | Tháp Kem | whipcream-spire.png | (none) | Home/spawn location, no suggestion target |
| MARSHMALLOW_MEADOW | Đồng Kẹo Dẻo | marshmallow-meadow.png | động-vật, thiên-nhiên | Soft grasslands with candy rabbits |
| CANDY_LANTERN_TOWN | Thị Trấn Đèn Kẹo | candy-lantern-town.png | nghệ-thuật, gia-đình | Colorful lantern-lit streets |
| BISCUIT_HILLS | Đồi Bánh Quy | biscuit-hills.png | nấu-ăn, thể-thao | Fragrant cookie-scented hills |
| VANILLA_PROMENADE | Đại Lộ Vani | vanilla-promenade.png | âm-nhạc, cổ-tích, sách-truyện | Elegant avenue with gentle melodies |

**Enum: `SubLocation`**
- Each has display name, Vietnamese alias, background filename, interest tags, parent region
- `fromCode(code)`: Lookup by enum name
- `bestMatchForInterests(interests)`: Find location with highest tag overlap

### Travel Flow

**Step 1: Prompt Injection (Layer 1e)**
```
ConversationProcessService.buildEnhancedPrompt()
    ├─ Injects current location flavor text
    ├─ Suggests travel destination based on top interests
    ├─ Instructs LLM to emit [TRAVEL:CODE] marker at end of response
    └─ Provides codex of all available locations
```

**Step 2: LLM Response with Marker**
```
LLM Response: "Chúng mình đi Đồng Kẹo Dẻo để thấy những con thỏ kẹo nhé! [TRAVEL:MARSHMALLOW_MEADOW]"
```

**Step 3: Marker Parsing & Travel Execution**
```
TravelMarkerParser.parse(response)
    └─ Extract location code: "MARSHMALLOW_MEADOW"

TravelService.travel(userId, "MARSHMALLOW_MEADOW")
    ├─ Validate code exists
    ├─ Verify pet in parent region (SWEET_DOMINION)
    ├─ Update PetProfile.currentLocation
    └─ Fire LocationChangedEvent

TravelMarkerParser.strip(response)
    └─ Remove marker for TTS ("Chúng mình đi Đồng Kẹo Dẻo để thấy những con thỏ kẹo nhé!")
```

**Step 4: WebSocket Update**
```
PetEventBridge.onLocationChanged(event)
    └─ Send to client: { type: "location_changed", location: "MARSHMALLOW_MEADOW", background_file: "marshmallow-meadow.png" }

Frontend ws_client.py receives
    └─ Dispatches to pet-event-handler.py

pet-event-handler.py processes
    └─ Calls display_engine.update_background(background_file)
    └─ LCD renders new background next frame
```

### Services

**TravelService (~65 LOC):**
- `travel(userId, subLocationCode)`: Validates code, checks region, updates pet profile
- Returns false if code invalid or pet not in correct region
- Fires CDI event on successful travel

**TravelMarkerParser (~30 LOC):**
- `parse(text)`: Extract `[TRAVEL:XXX]` marker code
- `strip(text)`: Remove marker (for TTS + history storage)
- Regex pattern: `\[TRAVEL:([A-Z_]+)\]`

**TravelPromptBuilder (~85 LOC):**
- `build(currentLocation, topInterests, session)`: Build complete travel context block
- Includes: current flavor text, interest-based suggestion, marker instruction codex
- Once-per-session suggestion tracking via `ConversationSession.hasSuggestedTravel()`

### Integration Points

**In ConversationProcessService:**
1. Inject travel prompt layer via `TravelPromptBuilder.build()`
2. After LLM response: `String travelCode = travelMarkerParser.parse(response)`
3. If code found: `travelService.travel(userId, travelCode)`
4. Strip marker for TTS: `cleanedResponse = travelMarkerParser.strip(response)`

**In PetEventBridge:**
1. Observe `LocationChangedEvent`
2. Send `location_changed` WebSocket message with background filename
3. Update session state if needed

**In ConversationSession:**
- Track `suggestedTravelLocations` set (once-per-session cap)
- Methods: `hasSuggestedTravel(code)`, `markTravelSuggested(code)`

### Frontend Integration

**ws_client.py:**
- Handle `location_changed` messages
- Extract `background_file` field

**pet-event-handler.py:**
```python
def on_location_changed(message):
    background_file = message.get("background_file")
    display_engine.update_background(background_file)
```

**display_engine.py:**
- Load PNG from `assets/backgrounds/{background_file}`
- Update background layer in compositor on next frame
- Smooth transition (no animation for now)

### Configuration & Extensibility

**Prompt Flavor Text (Hardcoded in TravelPromptBuilder):**
- WHIPCREAM_SPIRE: "những tòa tháp kem khổng lồ lấp lánh"
- MARSHMALLOW_MEADOW: "cỏ mềm như bông gòn, thỏ kẹo"
- CANDY_LANTERN_TOWN: "đèn lồng nhiều màu sắc"
- BISCUIT_HILLS: "mùi bánh quy thơm lừng"
- VANILLA_PROMENADE: "giai điệu nhẹ nhàng"

**Interest Tag Mapping:**
- Travel suggestions ranked by overlap with AdaptiveInterestService top 3 interests
- Each location tagged with 1-3 interest codes (e.g., "động-vật", "thể-thao")
- Whipcream Spire has no tags (prevents suggestion as travel destination)

**Once-Per-Session Rule:**
- TravelPromptBuilder checks `session.hasSuggestedTravel(code)`
- Only suggests each location once per session to avoid repetition

### Future Enhancements

**Phase 2d — Location NPCs:**
- Add NPC encounters per location
- Dialog trees triggered on arrival

**Phase 3 — Travel History:**
- Track visited locations for progression
- Unlock special conversations based on exploration

**Phase 4 — Fast Travel:**
- Shortcut teleportation between discovered locations

---

## Protocol States

### WebSocket v4 Session States

```
┌──────────────────────────────────────────────────────┐
│                                                      │
│  ┌─────────┐     hello      ┌──────────┐           │
│  │  START  │──────────────► │  IDLE    │           │
│  └─────────┘                └────┬─────┘           │
│                                  │                  │
│                        audio_start│                 │
│                                  │                  │
│                            ┌─────▼──────┐          │
│                            │  LISTENING │          │
│                            │ (buffering)│          │
│                            └─────┬──────┘          │
│                                  │                  │
│                         audio_stop│                 │
│                                  │                  │
│                           ┌──────▼─────┐           │
│                           │ PROCESSING │           │
│                           │ (STT+LLM)  │           │
│                           └──────┬─────┘           │
│                                  │                  │
│                         LLM done │                  │
│                                  │                  │
│                           ┌──────▼──────┐          │
│                           │ RESPONDING  │          │
│                           │ (TTS stream)│          │
│                           └──────┬──────┘          │
│                                  │                  │
│                         turn_end │                  │
│                                  │                  │
│                            ┌─────▼─────┐           │
│                            │ IDLE      │           │
│                            │ (ready)   │           │
│                            └───────────┘           │
│                                                     │
│ INTERRUPT (during RESPONDING):                     │
│   RESPONDING ──interrupt──► IDLE (new turn)        │
│                                                     │
└──────────────────────────────────────────────────────┘
```

---

## Service Communication

### Internal Service Dependencies (Synchronous)

```
AimonWebSocket
  ├─► AudioPipelineService
  │    └─► OpusAudioProcessor
  │    └─► GoogleSttService
  │
  ├─► ConversationProcessService
  │    ├─► PowerMemService
  │    │    ├─► PowerMemClient (HTTP)
  │    │    └─► ContextRetrievalService
  │    ├─► LiteLlmAIService
  │    │    └─► LiteLlmClient (HTTP)
  │    ├─► SentenceSplitterService
  │    └─► TtsProviderService
  │         ├─► VieNeuTtsService (HTTP)
  │         └─► GoogleTtsStreamingService (HTTP)
  │
  ├─► ResponseStreamService
  │    └─► TtsProviderService
  │
  └─► ConversationSessionManager
       └─► ConversationSession (in-memory)
```

### External Service Calls (REST/HTTP)

| Service | Caller | Endpoint | Timeout | Retry | Fallback |
|---------|--------|----------|---------|-------|----------|
| **Google STT** | backend | `speech.googleapis.com` | 30s | 3 | Error response |
| **LiteLLM** | backend | `http://litellm:4000` | 60s | 2 | Error response |
| **PowerMem** | backend | `http://memoryservice:8003` | 10s | 1 | Continue w/o memory |
| **VieNeu TTS** | backend | `http://vieneu-tts:5001` | 15s | 1 | Fallback to Google |
| **Google TTS** | backend | `texttospeech.googleapis.com` | 15s | 1 | Error response |
| **Gemini Vision** | **Pi (frontend)** | `generativelanguage.googleapis.com` | SDK default | 0 | Return None (no feed) |

---

## Concurrency & Session Management

### Session Storage
```
Map<String, RobotSession> sessions = new ConcurrentHashMap<>();
```

**Per-session state:**
- `robotId` — identifier
- `sessionId` — unique session UUID
- `currentState` — (IDLE, LISTENING, PROCESSING, RESPONDING)
- `audioBuffer` — OPUS frame deque
- `conversationSession` — context (user, parent, turn history)
- `wsSession` — WebSocket connection reference
- `createdAt` — timestamp
- `lastActivity` — timestamp (for cleanup)

### Thread Safety
- **ConcurrentHashMap** for sessions
- **Deque<byte[]>** for audio buffering (thread-safe)
- **Immutable DTOs** for data transfer
- **Synchronized** methods where needed (rare)

### Cleanup Policy
- Sessions timeout after 5 minutes inactivity
- Scheduled cleanup task runs every minute
- Interrupted sessions cleared immediately

---

## Security & Safety

### Authentication & Authorization
- **API Keys:** LiteLLM, Google Cloud (via service accounts)
- **Database:** PostgreSQL user credentials (read-only for app)
- **Credentials:** Stored in environment variables, mounted secrets

### Data Safety (Kid Mode)
1. **Input validation:** Check user input against banned_keywords
2. **Output filtering:** Check LLM response before TTS
3. **Mechanism:** BannedKeyword entity + simple substring matching
4. **Fallback:** Replace unsafe content or skip turn

### Error Handling
- **Graceful degradation:** TTS fallback (VieNeu → Google)
- **Circuit breaker:** Skip memory if PowerMem unavailable
- **User feedback:** Error messages via WebSocket
- **Logging:** All errors logged for debugging

### Network Security
- **WebSocket over TLS** (in production: wss://)
- **CORS:** Restricted to Pi client origin
- **Rate limiting:** Per-session message throttling (future)

---

## Deployment Architecture

### Container Orchestration
```
Docker Compose (dev/test) or Kubernetes (production)

Services:
  1. postgres:16        (database)
  2. memoryservice      (memory MCP)
  3. litellm:main       (LLM proxy)
  4. vieneu-tts:gpu     (GPU TTS)
  5. aimon-backend      (main backend)

Networks:
  - aimon-network       (internal communication)

Volumes:
  - postgres-data       (persistent database)
  - google-creds        (mounted secret)
```

### Scalability Considerations
- **Horizontal:** Multiple aimon-backend instances behind load balancer
- **Session affinity:** WebSocket connections sticky to single instance
- **Database:** PostgreSQL with connection pooling (10 max)
- **Memory:** Local in-memory session storage (no shared state)

---

## Monitoring & Observability

### Metrics to Track
- WebSocket connection count
- Message latency (audio_start → tts_stop)
- TTS provider failures & fallover rate
- Memory service availability
- Database connection pool usage

### Logging Strategy
- **Level:** INFO (default) → DEBUG (dev)
- **Format:** JSON with request IDs
- **Services:** Aggregate via ELK/CloudWatch
- **Retention:** 7 days (configurable)

### Health Checks
```
GET /health                    → Quarkus health
GET /health/ready              → Database, external services
GET /health/live               → WebSocket listener
```

---

## Technology Rationale

| Decision | Technology | Rationale |
|----------|-----------|-----------|
| **Runtime** | Java 21 + Quarkus | Fast startup, low memory, native compilation option |
| **Database** | PostgreSQL + pgvector | Relational + vector search for memory |
| **Audio Codec** | OPUS (input), PCM16 (output) | Bandwidth-efficient input, simple output for Pi |
| **STT** | Google Cloud | Reliable, multilingual, streaming support |
| **TTS Primary** | VieNeu (GPU) | Natural Vietnamese, high quality |
| **TTS Fallback** | Google TTS | Reliable, multi-language |
| **LLM Broker** | LiteLLM | Flexible provider switching, failover |
| **Memory** | PowerMem (MCP) | 3-layer system, persistent, extensible |
| **Container** | Docker Compose | Simple orchestration, reproducible |
| **Vision AI** | Gemini 2.5 Flash (Pi-direct) | Avoids WS frame bloat; Pi calls Gemini directly with JPEG bytes |
| **Vision SDK** | google-genai (Python) | Official SDK, supports multimodal Part.from_bytes() |

---

## Comparison with Legacy (backyard)

| Aspect | backyard (legacy) | aimon-backend (v0.2) |
|--------|-------------------|-------------------|
| **WebSocket Protocol** | v3 (complex, VAD-driven) | v4 (simple, push-to-talk) |
| **Files** | 90 | 47 (48% reduction) |
| **LOC** | 11K | 6.3K (43% reduction) |
| **MoE Routing** | Yes (5 experts) | No (kept simple) |
| **Face Recognition** | Yes | No (dropped) |
| **RAG/Stories** | Yes | No (dropped) |
| **TTS Providers** | 4 (VbeeVoice, VietTTS, Google, ...) | 2 (VieNeu, Google) |
| **Memory System** | Session logs | PowerMem 3-layer |
| **Check-in Flow** | Yes | No (simplified) |
| **Performance** | Complex state machine | Clean, straightforward |

---

## Offline Resilience System (Phase 10)

**Purpose:** Enable tamagotchi-style gameplay when Pi loses WiFi connectivity, with seamless state synchronization upon reconnection.

### Architecture Overview

Offline resilience comprises five integrated subsystems:

```
[WiFi Loss Detected]
    ↓
[OfflineGameEngine starts]
    ├─ OfflineStatEngine (decay timer, daemon thread)
    ├─ OfflineFeedHandler (double-press feed + 30s cooldown)
    ├─ OfflineResponseBank (75 Vietnamese phrases, no-repeat selection)
    ├─ OfflineEventJournal (SQLite, max 1000 events, auto-prune)
    └─ StatusDisplay (amber LED, visual-only UI)

[WiFi Restored]
    ↓
[WS Reconnect (exponential backoff: 2s→60s cap)]
    ↓
[OfflineEventJournal: flush ALL pending events]
    ↓
[Backend SyncHandler: aggregate & apply via PetProfileService]
    ↓
[Authoritative state returned to frontend]
```

### 1. Offline Game Engine (Frontend Orchestrator)

**File:** `aimon-frontend/state/offline-game-engine.py`

**Purpose:** Coordinates offline gameplay when WiFi is unavailable.

**Key Features:**
- Starts on WebSocket disconnect → network error detection
- Stops on WebSocket reconnection → network restored
- Non-interactive: visual-only gameplay (no audio, no LLM)
- Autonomous: stat decay timer runs from background daemon thread
- Event logging: all gameplay events recorded to SQLite journal
- Response feedback: Vietnamese text bubbles via response bank

**Public Interface:**
```python
class OfflineGameEngine:
    def start(offline_since_ts: float) -> None
    def stop() -> dict                          # Returns final state snapshot
    def on_interaction() -> None                # User taps button (no audio)
    def on_feed() -> bool                       # Double-press: feed if cooldown OK
    def get_state_snapshot() -> dict
```

### 2. Stat Decay Engine (Background Thread)

**File:** `aimon-frontend/state/offline-stat-engine.py`

**Purpose:** Automatically decreases hunger/energy and happiness every 60 seconds.

**Decay Formula (per 60s tick):**
```
hunger += 1
energy -= 0.5
happiness -= 0.3
```

**Mechanics:**
- Thread-safe: acquires `_pet_lock` callback pattern
- Daemon thread: auto-terminates on main process exit
- Critical thresholds monitored:
  - `hunger >= 80`: trigger warning animation
  - `energy <= 20`: trigger warning animation
  - `hunger >= 100`: trigger regression animation
- All decay ticks logged to `OfflineEventJournal`

### 3. Feed Handler (Double-Press Input)

**File:** `aimon-frontend/state/offline-feed-handler.py`

**Purpose:** Enables offline feeding via double-press button (within 500ms).

**Feed Mechanics:**
- **Cooldown:** 30 seconds between feeds
- **Hunger reduction:** -15 per feed
- **XP gain:** +3 per successful feed
- **Animation:** Eat animation triggered on success
- **Feedback:** "Ngon quá!" (Delicious!) text bubble

**Cooldown Enforcement:**
```python
if time.time() - _last_feed_ts >= 30:
    apply(-15 hunger, +3 XP)
    _last_feed_ts = time.time()
    return True
return False
```

### 4. Vietnamese Response Bank (No-Repeat Selection)

**File:** `aimon-frontend/state/offline-response-bank.py` + `aimon-frontend/data/offline-responses.json`

**Purpose:** Display context-aware Vietnamese text feedback during offline gameplay.

**Categories (75 total phrases across 5):**
- `hungry_high` (hunger >= 70): "Bụng mình đói quá à...", "Cho mình ăn đi...", etc.
- `energy_low` (energy <= 30): "Mệt quá rồi...", "Mình cần nghỉ ngơi...", etc.
- `happy_high` (happiness >= 70): "Vui vẻ quá!!", "Cảm ơn bạn nhé!", etc.
- `neutral` (balanced): "Hôm nay bạn khỏe không?", "Chơi cùng mình nhé!", etc.
- `critical` (hunger=100 or energy=0): "Giúp mình với!", "Mình không chịu nổi...", etc.

**Selection Logic (No-Repeat):**
1. Determine dominant condition: `max(hunger, 100-energy, 100-happiness)` → category key
2. Track used indices per category: `_used_indices[category] = set()`
3. Pick random unused phrase from category
4. If all used → reset set, cycle through again
5. Prevents repetitive feedback in extended offline sessions

### 5. Event Journal (SQLite Local Cache)

**File:** `aimon-frontend/state/offline-event-journal.py`

**Purpose:** Persistently record all offline events for later synchronization.

**Schema:**
```sql
CREATE TABLE offline_events (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    event_type TEXT NOT NULL,
    timestamp INTEGER NOT NULL,
    payload TEXT,                              -- JSON: {hunger, energy, xp, ...}
    synced INTEGER DEFAULT 0                   -- 0 = pending, 1 = sent to backend
);
CREATE INDEX idx_offline_synced ON offline_events(synced, timestamp);
```

**Event Types:**
- `decay_tick` — stat decay applied (payload: {hunger, energy, happiness})
- `feed` — successful feed (payload: {hunger_reduction, xp_gain})
- `interaction` — button tap (payload: {response_text, xp_gain})
- `xp_gain` — XP earned (payload: {xp_amount, source})
- `level_up` — level threshold crossed (payload: {new_level, xp_overflow})
- `warning` — critical threshold reached (payload: {condition_type})
- `regression` — hunger hit max (payload: {})

**Storage Limits:**
- Max 1000 events (auto-prune oldest on insert if >= 1000)
- Thread-safe: `check_same_thread=False` + `threading.Lock`
- WAL mode: enabled for concurrent reads/writes

### 6. Status Display (Amber LED + Visual Feedback)

**File:** `aimon-frontend/display/badge-popup-renderer.py` (reused)

**Offline State Indicators:**
- **Amber LED:** Solid on = offline mode active
- **UI Label:** "Chế độ ngoại tuyến" (Offline mode) shown in corner
- **Speech Bubble:** Response text displayed per interaction
- **Stat Bars:** Update in real-time during offline gameplay
- **No Audio:** All responses visual-only (text bubbles)

### 7. WebSocket Reconnection (Exponential Backoff)

**File:** `aimon-frontend/network/ws_client.py`

**Purpose:** Automatically reconnect with exponential backoff when WiFi returns.

**Backoff Strategy:**
```
Attempt 1: 2 seconds
Attempt 2: 4 seconds
Attempt 3: 8 seconds
...
Attempt N: 60 seconds (cap)
```

**Reconnection Flow:**
```python
def connect_with_backoff():
    backoff_ms = 2000
    while not connected:
        try:
            ws = WebSocket.connect(url, timeout=5s)
            backoff_ms = 2000  # Reset on success
        except ConnectionError:
            time.sleep(backoff_ms / 1000)
            backoff_ms = min(backoff_ms * 2, 60000)  # Cap at 60s
```

### 8. Backend Sync Handler

**File:** TBD (Java/Quarkus service)

**Purpose:** Aggregates offline events and applies them atomically to backend pet state.

**Sync Protocol:**

1. **Event Flush:** Frontend sends all pending offline_events on reconnect
2. **Payload Structure:**
   ```json
   {
     "type": "offline_sync",
     "offline_events": [
       { "event_type": "decay_tick", "timestamp": 1708600000, "payload": {...} },
       { "event_type": "feed", "timestamp": 1708600030, "payload": {...} },
       ...
     ]
   }
   ```

3. **Backend Processing:**
   - Aggregates events chronologically
   - Applies all stat deltas via existing `PetProfileService`
   - Validates final state (hunger/energy 0-100, level >= 1)
   - Checks for level-up → triggers evolution if applicable
   - Returns authoritative state to frontend

4. **Sync Strategy:** Last-write-wins
   - Events processed in timestamp order
   - Backend state takes precedence if conflicts
   - Frontend updates local cache to match backend

### 9. Offline-to-Online Transition

**Workflow:**

```
[Offline Mode Active]
    • Decay thread running
    • Events logged to SQLite
    • Button: interact/feed triggers response bank
    • Display: text bubbles, stat bars, amber LED

[WiFi Detected]
    • Stop decay thread
    • Flush all pending events via `get_all_pending_for_sync()`

[WebSocket Reconnect (with backoff)]
    • Send hello, restore session
    • Send offline_sync message with all events
    • Await authoritative state response

[Backend Processes Offline Sync]
    • Apply all stat changes via PetProfileService
    • Update database
    • Return final pet_status

[Frontend Receives pet_status]
    • Stop offline game engine
    • Sync local pet state
    • Mark all events as synced
    • Resume normal online mode
    • Amber LED off
```

### 10. Data Flow Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│ OFFLINE MODE (No WiFi)                                          │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  [Button Events]                                                │
│      ├─ Single Press → on_interaction()                         │
│      │    ├─ Determine condition (hunger/energy/happiness)      │
│      │    ├─ Get response from ResponseBank (no-repeat)         │
│      │    ├─ Display text bubble                                │
│      │    ├─ Apply +5 XP                                        │
│      │    └─ Log interaction event to journal                   │
│      │                                                           │
│      └─ Double Press → on_feed()                                │
│           ├─ Check 30s cooldown                                 │
│           ├─ Apply -15 hunger, +3 XP                            │
│           ├─ Trigger eat animation                              │
│           ├─ Log feed event to journal                          │
│           └─ Update stat bars                                   │
│                                                                  │
│  [Background Thread: OfflineStatEngine]                         │
│      Every 60 seconds:                                          │
│      ├─ Apply decay: hunger +1, energy -0.5, happiness -0.3    │
│      ├─ Clamp to 0-100 range                                    │
│      ├─ Check critical thresholds                               │
│      ├─ Log decay_tick event to journal                         │
│      ├─ Update display                                          │
│      └─ Trigger animations (warning/regression if needed)       │
│                                                                  │
│  [OfflineEventJournal: SQLite Storage]                          │
│      ├─ Persist all events (decay, feed, interaction, xp)      │
│      ├─ Auto-prune: keep max 1000 events                        │
│      ├─ WAL mode: concurrent reads/writes                       │
│      └─ Thread-safe: Lock + check_same_thread=False             │
│                                                                  │
│  [StatusDisplay]                                                │
│      ├─ Amber LED: solid on                                     │
│      ├─ UI Label: "Chế độ ngoại tuyến"                          │
│      └─ No audio output                                         │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
         │
         │ [WiFi Restored]
         ▼
┌─────────────────────────────────────────────────────────────────┐
│ RECONNECTION PHASE                                              │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  [Exponential Backoff Loop]                                     │
│      Attempt 1: wait 2s   → try_connect()                       │
│      Attempt 2: wait 4s   → try_connect()                       │
│      Attempt 3: wait 8s   → try_connect()                       │
│      ... (doubling) ...                                         │
│      Attempt N: wait 60s  → try_connect() [capped]              │
│                                                                  │
│  [OfflineEventJournal: Flush]                                   │
│      ├─ Get ALL pending events (get_all_pending_for_sync)       │
│      ├─ Build offline_sync WebSocket message                    │
│      └─ Send to backend                                         │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
         │
         │ [WebSocket Connected]
         ▼
┌─────────────────────────────────────────────────────────────────┐
│ BACKEND SYNC HANDLER                                            │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  [Process offline_sync Message]                                 │
│      ├─ Parse offline_events array (chronological)              │
│      ├─ For each event:                                         │
│      │    ├─ Apply stat deltas via PetProfileService            │
│      │    ├─ Check level-up thresholds                          │
│      │    ├─ Trigger evolution if needed                        │
│      │    └─ Log to database                                    │
│      │                                                           │
│      ├─ Validate final state (clamp to 0-100)                   │
│      ├─ Return authoritative pet_status                         │
│      └─ Send to frontend                                        │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
         │
         │ [Receive pet_status]
         ▼
┌─────────────────────────────────────────────────────────────────┐
│ FRONTEND SYNC COMPLETION                                        │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  [OfflineGameEngine: Stop]                                      │
│      ├─ Decay thread terminates                                 │
│      └─ Feed handler cleanup                                    │
│                                                                  │
│  [OfflineEventJournal: Mark Synced]                             │
│      ├─ Update all sent events: synced=1                        │
│      └─ Optionally clear_synced() to free space                 │
│                                                                  │
│  [PetState: Update]                                             │
│      ├─ Replace local state with backend authoritative          │
│      ├─ Update display stat bars                                │
│      └─ Trigger animations (evolution, badges, etc.)            │
│                                                                  │
│  [StatusDisplay: Clear Offline]                                 │
│      ├─ Amber LED: off                                          │
│      ├─ Remove "Chế độ ngoại tuyến" label                       │
│      └─ Resume audio mode                                       │
│                                                                  │
│  [ONLINE MODE ACTIVE]                                           │
│      Ready for normal WebSocket voice interaction               │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### Configuration

**Constants (in `aimon-frontend/config.py`):**
```python
OFFLINE_EVENT_MAX = 1000              # Max journal events
OFFLINE_DECAY_INTERVAL_S = 60         # Seconds between stat decay
OFFLINE_FEED_COOLDOWN_S = 30          # Seconds between feeds
OFFLINE_FEED_HUNGER_REDUCTION = 15    # Hunger decrease per feed
OFFLINE_XP_INTERACTION = 5            # XP per button tap
OFFLINE_XP_FEED = 3                   # XP per successful feed
OFFLINE_DB_PATH = TURN_DB_PATH        # Reuse turn_logger DB
```

### Key Design Decisions

1. **Visual-Only Gameplay:** No audio processing offline simplifies implementation and reduces battery drain
2. **Daemon Thread:** Background decay thread ensures continuous stat updates without blocking UI
3. **SQLite Persistence:** Event journal survives app restarts or crashes
4. **Auto-Prune:** Max 1000 events prevents unbounded storage growth
5. **Exponential Backoff:** Reduces connection attempts when WiFi unavailable, prevents battery drain
6. **Last-Write-Wins Sync:** Backend state takes precedence; ensures data consistency
7. **No Evolution Offline:** Evolution deferred to backend; prevents divergence
8. **30s Feed Cooldown:** Prevents abuse, requires meaningful player engagement
9. **Response Bank:** 75 curated Vietnamese phrases prevent repetitive feedback
10. **Amber LED Indicator:** Hardware signal of offline state (non-intrusive)

---

## Tasteless Combat System (Phase 11)

**Purpose:** Random turn-based battle encounters that spawn after conversation turns, with stat-based power calculations and Memory Shard rewards.

### Combat Flow

```
[Conversation Ends]
    ↓
[ConversationProcessService triggers spawn check]
    ├─ Calls TastelessSpawnService.shouldSpawn()
    │   ├─ Queries TastelessConfig (spawn probability by level)
    │   ├─ Random number generation
    │   └─ Returns boolean
    │
    ├─ If YES → POST /combat/start
    │
    └─ If NO → Next conversation
         ↓
    [TastelessEncounterEvent emitted]
         ↓
    [Frontend displays TASTELESS_WARNING]
         ↓
    [CombatService.initiateCombat()]
         ├─ Creates CombatSessionState (turn = 0, round = 0)
         ├─ Queries pet stats (level, hunger, energy, happiness)
         ├─ Calls CombatPowerCalculator.calculatePower(petStats)
         │   └─ Returns: power = base + (level * 10) + (hunger/10) + (energy/10) + (happiness/20)
         ├─ Generates random Tasteless power (between 80-200 scaled by difficulty)
         └─ Sends COMBAT_START message
              ├─ playerPower
              ├─ enemyPower
              └─ currentRound

    [Player chooses action: ATTACK, DEFEND, SPECIAL, FLEE]
         │
         ├─ ATTACK: random(playerPower - 20, playerPower + 20)
         ├─ DEFEND: reduce incoming damage by 30%
         ├─ SPECIAL: 1.5x power, 40% fail chance
         └─ FLEE: 50% success (escape), 50% fail (locked in)
              ↓
    [CombatService.executeRound(playerAction)]
         ├─ Rolls enemy action (weighted toward attack)
         ├─ Calculates damage: (playerAction - enemyAction) + variance
         ├─ Applies defense modifier if active
         ├─ Updates player/enemy HP
         ├─ Checks win condition (enemy HP <= 0)
         └─ Sends COMBAT_ROUND message
              ├─ playerDamage
              ├─ enemyDamage
              ├─ playerHP
              ├─ enemyHP
              └─ roundNumber

    [Combat Continues until WIN or LOSS]
         │
         ├─ WIN (enemyHP <= 0)
         │   ├─ CombatResultHandler.handleWin()
         │   ├─ XP reward = 50 + (roundCount * 10)
         │   ├─ Shard reward = random unlock from pool
         │   ├─ Emits CombatWonEvent
         │   └─ Sends COMBAT_RESULT (victory)
         │        ├─ xpGained
         │        ├─ shardUnlocked (if applicable)
         │        └─ newLevel (if level-up)
         │
         └─ LOSS (playerHP <= 0 OR all actions blocked)
             ├─ CombatResultHandler.handleLoss()
             ├─ PetProfileService.applyStatPenalty()
             │   └─ hunger +10, energy -15
             ├─ Emits CombatLostEvent
             └─ Sends COMBAT_RESULT (defeat)
                  ├─ statPenalties
                  └─ nextSpawnChance
```

### Combat Power Calculation

```java
public class CombatPowerCalculator {
    public int calculatePower(PetStats stats) {
        int base = 100;  // Base power all pets start with
        int levelBonus = stats.level * 10;
        int hungerBonus = stats.hunger / 10;  // 0-10
        int energyBonus = stats.energy / 10;  // 0-10
        int happinessBonus = stats.happiness / 20;  // 0-5

        return base + levelBonus + hungerBonus + energyBonus + happinessBonus;
    }
}
```

**Stat Impact:**
- **Level:** Strongest factor (+10 per level) — encourages leveling
- **Hunger:** Reduced power when hungry (lower hunger = lower power)
- **Energy:** Reduced power when tired
- **Happiness:** Bonus when happy (motivation boost)
- **Special moves:** +50% damage, 40% fail rate, requires sufficient stats

---

## Memory Shard System (Phase 11)

**Purpose:** Progressive lore discovery tied to combat victories and world exploration. Leads to Noir final arc.

### Shard Progression

```
[CombatResultHandler.handleWin()]
    ├─ Rolls shard unlock (20% chance per victory)
    ├─ Queries ShardService.getAvailableShards(petLevel)
    │   └─ Returns unlocked shards gated by level
    │
    ├─ ShardService.unlockShard(userId, shardId)
    │   ├─ Creates UserShard entry
    │   ├─ Stores in database
    │   └─ Emits ShardUnlockedEvent
    │
    └─ Frontend displays SHARD_UNLOCKED animation
         ├─ Shard name
         ├─ Lore snippet
         └─ Progress to next arc

[Location System (Phase 11)]
    ├─ Each shard unlocks access to new location
    ├─ LocationService tracks current_location
    ├─ Locations have:
    │   ├─ Name (Cotton Land geography)
    │   ├─ NPC encounters
    │   └─ Context injection into LLM prompts
    │
    └─ Messages: LOCATION_UNLOCK, LOCATION_CHANGED, LOCATION_SWITCH

[Noir Quest Arc (Phase 11)]
    ├─ Unlocked after collecting N shards (default: 3)
    ├─ NoirQuestService orchestrates multipart questions
    ├─ NoirQuestionBank provides 50+ ranked questions
    ├─ NoirResponseEvaluator grades answers via LLM rubric
    ├─ FinalArcService.canUnlockFinalArc()
    │   └─ Check: shards >= threshold AND questions answered >= threshold
    │
    └─ Final arc unlocks Noir character arc
         ├─ Emits FINAL_ARC_UNLOCK
         ├─ Noir becomes available as conversation NPC
         └─ Special Noir responses injected
```

### Shard Gating Example

```
Level 1-5:  Beginner Shards (3 available)
  ├─ "Cotton Fields" — Basic geography
  ├─ "Harvest Moon" — Calendar & seasons
  └─ "First Friends" — NPC introductions

Level 6-10: Adventure Shards (5 available)
  ├─ All Beginner shards
  ├─ "Lost Kingdom" — Ancient history
  ├─ "Midnight Forest" — Supernatural lore
  └─ "Shadow Whispers" — Noir hints

Level 11+:  Final Shards (7 available)
  ├─ All previous shards
  ├─ "Truth Unveiled" — Noir backstory
  └─ "Ascension" — Final arc exclusive

Noir Quest Arc Requirement:
  ├─ Minimum shards collected: 3
  ├─ Questions answered correctly: 5/10
  └─ Time since last attempt: 24 hours
```

### Noir Response Evaluation

```
[Player answers Noir question]
    ↓
[NoirResponseEvaluator.evaluateResponse()]
    ├─ Constructs LLM prompt with:
    │   ├─ Question
    │   ├─ Answer rubric (example good answers)
    │   ├─ Scoring criteria (0-10)
    │   └─ Player response
    │
    ├─ LLM returns score 0-10
    ├─ Score >= 7 → Correct answer
    ├─ Score < 7 → Incorrect, offer retry
    │
    └─ If all questions passed:
         ├─ FinalArcService.unlockFinalArc(userId)
         ├─ Emits FINAL_ARC_UNLOCK
         └─ Noir becomes fully unlocked character
```

### Database Entities

**UserShard:**
```
user_id (FK)
shard_id
discovered_at (timestamp)
noir_question_index (0-10, tracks progress)
noir_questions_correct (0-10)
noir_last_attempt (timestamp, rate limit)
```

**TastelessConfig:**
```
difficulty_level (easy, normal, hard)
spawn_probability (0.0-1.0)
min_level (default 5)
max_level (cap)
power_variance (±20% range)
xp_multiplier (1.0-2.0)
shard_drop_chance (0.0-1.0)
```

**CombatLog:**
```
id (UUID)
user_id (FK)
player_power
enemy_power
rounds_played
outcome (WIN, LOSS, FLEE)
xp_earned
shard_unlocked (if applicable)
created_at (timestamp)
```

---

## Frontend Combat Integration

**New WebSocket Messages:**
- `TASTELESS_WARNING` — Encounter initiated
- `COMBAT_START` — Battle begins (powers, round 0)
- `COMBAT_ROUND` — Turn result (damage, HP, round N)
- `COMBAT_RESULT` — Battle ends (victory/defeat, rewards)
- `COMBAT_SPECIAL` — Special move effect (critical hit animation)

**Frontend Combat Handler:**
```python
class PetEventHandler:
    def on_combat_start(self, message):
        # Display combat overlay
        # Show power bars, action buttons
        # Enable player action selection
        pass

    def on_combat_round(self, message):
        # Animate damage
        # Update HP bars
        # Display action outcome
        pass

    def on_combat_result(self, message):
        # Show victory/defeat screen
        # Animate XP gain / shard unlock
        # Display stat changes
        pass
```

---

## Future Enhancements

1. **OPUS Output Encoding** — Reduce bandwidth for TTS audio
2. **Whisper STT Fallback** — Local STT on edge
3. **Multi-language Support** — More TTS voices
4. **Analytics Dashboard** — Usage tracking
5. **Advanced Memory Pruning** — Trim old observations
6. **Model Fine-tuning** — Personality learning from interactions
7. **Kubernetes Migration** — Horizontal scaling
8. **GraphQL API** — Alternative to REST
9. **Vision: Non-food Reactions** — LLM/TTS response to scene descriptions from Gemini
10. **Combat Difficulty Scaling** — Dynamic enemy power based on win streaks
11. **Shard Trading System** — Exchange shards for cosmetics
12. **Multiplayer Combat** — Pet vs. Pet battles

---

## References

- `./aimon-backend/README.md` — Quick start
- `./docs/codebase-summary.md` — File-level details
- `./docs/deployment-guide.md` — Docker setup
- `./docs/code-standards.md` — Code conventions
