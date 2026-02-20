# AI-MON System Architecture

**Last Updated:** 2026-02-20
**Version:** v0.2 (Phase 3: Adaptive Interests + Phase 7: Game Loop & SFX + Phase 8: World Lore)
**Status:** Adaptive Interest System Integration Complete

## System Overview

AI-MON is a distributed voice-driven AI companion system for Raspberry Pi with a clean microservices architecture. The refactored backend (`aimon-backend`) coordinates real-time push-to-talk conversations and pet game mechanics. The frontend (`aimon-frontend`) implements interactive pet gameplay: SFX feedback, badge notifications, quest system, evolution/regression/transformation sequences, and 4-layer compositor rendering with TTS ducking on a 240x280 LCD display. Phase 7 adds full game loop mechanics; Phase 8 adds camera vision analysis on-device.

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
│  │ - Button input (push-to-talk / double-press → camera)   │   │
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
│  │  │  - Push-to-talk state machine                  │      │ │
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
- Physical button (push-to-talk; double-press triggers camera)
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
| `audio/audio_capture.py` | Record audio in LISTENING state, send OPUS frames to backend. |
| `audio/audio_playback.py` | Play PCM16 audio chunks from TTS, stop on interrupt. |
| `audio/sfx-manager.py` | SFX mixer: 3 channels (primary, notify, ambient), TTS ducking, OGG pre-loading. |
| `network/ws_client.py` | WebSocket v4 client: hello → audio_start/frames/stop → asr/llm/tts/pet events (21 msg types). |
| `hardware/camera-capture-service.py` | OV5647 CSI camera capture → raw JPEG bytes (picamera2). Rate-limited (30s). |
| `hardware/vision-analysis-service.py` | Gemini 2.5 Flash vision: JPEG → `{is_food, food_name, description}` JSON. |
| `storage/turn_logger.py` | Log conversations for debugging & analytics. |

**Protocol:** WebSocket v4 (push-to-talk + pet events)
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

---

## References

- `./aimon-backend/README.md` — Quick start
- `./docs/codebase-summary.md` — File-level details
- `./docs/deployment-guide.md` — Docker setup
- `./docs/code-standards.md` — Code conventions
