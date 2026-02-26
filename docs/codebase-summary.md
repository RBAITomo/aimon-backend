# AI-MON Codebase Summary

**Last Updated:** 2026-02-26
**Status:** Phase 12 In Progress — Continuous Conversation Mode with VAD Integration

## Overview

AI-MON is a voice-driven AI companion for Raspberry Pi with personality, memory, safety filtering, world lore immersion, dynamic location travel, and on-device camera vision. The refactored `aimon-backend` is a clean, focused Java/Quarkus backend. Phase 12 replaces push-to-talk with continuous conversation mode using WebRTC VAD for automatic speech detection and seamless auto-resume after playback.

**Metrics:**
- **File Reduction:** 90 → 47 files (48% reduction)
- **LOC Reduction:** 11K → 6.3K lines (43% reduction)
- **Build Status:** ✅ Clean compilation
- **Test Coverage:** ✅ All phases validated

---

## Project Structure

### Root Directory
```
AIMON/
├── aimon-backend/          # Main backend (47 files, 6.3K LOC)
├── aimon-frontend/         # Frontend UI & hardware control (Pygame compositor)
├── backyard/               # Legacy backend (deprecated, archived)
├── memoryService/          # PowerMem MCP server (memory system)
├── VieNeu-TTS/            # Vietnamese TTS engine (GPU-accelerated)
├── whisplay-ai-chatbot/   # Client-side companion (reference)
├── plans/                 # Implementation plans & reports
├── .claude/               # Development automation
└── docs/                  # Documentation (this folder)
```

### aimon-backend Structure

**8 Core Packages:**

```
src/main/java/dev/aimon/
├── client/                # REST clients (LiteLLM, PowerMem)
│   ├── LiteLlmClient
│   ├── PowerMemClient
│   └── PowerMemHeaderFactory
│
├── config/                # Application configuration
│   ├── AIConfig           # Service configuration (259 LOC)
│   ├── ApplicationConfig  # Quarkus beans
│   └── DevPropertiesFile  # Dev environment
│
├── dto/                   # Data transfer objects (6 subpackages)
│   ├── ai/                # LiteLLM messages, requests, responses
│   ├── conversation/      # Session DTOs
│   ├── powermem/          # Memory service DTOs
│   ├── tts/               # TTS request/response objects
│   ├── websocket/         # Protocol messages
│   └── world/             # World DTOs (LocationDto)
│
├── entity/                # Database entities
│   ├── Parent
│   ├── User               # Child profiles
│   ├── BannedKeyword      # Kid Mode safety lists
│   └── world/
│       └── WorldLore      # World lore entries (Phase 8)
│
├── model/                 # Domain models
│   ├── RobotSession       # WebSocket session state
│   ├── SessionState       # Enum (IDLE, LISTENING, PROCESSING, RESPONDING)
│   ├── ConversationSession  # Conversation context
│   └── AudioFrame         # Audio packet wrapper
│
├── service/               # Business logic (7 subpackages)
│   ├── ai/                # LLM orchestration
│   │   └── LiteLlmAIService (206 LOC)
│   │
│   ├── audio/             # Audio processing
│   │   ├── AudioPipelineService  # OPUS→STT pipeline
│   │   ├── OpusAudioProcessor    # Codec & downsampling (660 LOC)
│   │   ├── ResponseStreamService # LLM→TTS→PCM16 pipeline
│   │   └── OpusCodecService      # OPUS decoder only
│   │
│   ├── conversation/      # Conversation orchestration
│   │   ├── ConversationProcessService (298 LOC)
│   │   ├── ConversationSessionManager (241 LOC)
│   │   └── TopicClassifier (~130 LOC)     # Keyword + LLM-based topic classification
│   │
│   ├── memory/            # PowerMem integration
│   │   ├── PowerMemService (316 LOC)
│   │   ├── ContextRetrievalService (247 LOC)
│   │   ├── MemoryContextFormatter (210 LOC)
│   │   ├── ObservationBuffer
│   │   └── MemoryMetadataBuilder
│   │
│   ├── stt/               # Speech-to-text
│   │   └── GoogleSttService    # Google Cloud STT
│   │
│   ├── tts/               # Text-to-speech
│   │   ├── TtsProviderService (240 LOC)    # Circuit breaker
│   │   ├── VieNeuTtsService (263 LOC)      # Primary (GPU)
│   │   ├── GoogleTtsStreamingService (215 LOC)  # Fallback
│   │   ├── SentenceSplitterService (218 LOC)    # Chunking
│   │   └── TtsCircuitBreaker
│   │
│   ├── interest/          # Adaptive interest system (Phase 3)
│   │   └── AdaptiveInterestService (~100 LOC) # Derives top interests from timeline
│   │
│   └── world/             # World lore + travel system (Phase 8, 2c)
│       ├── WorldLoreService (~75 LOC)     # Fetches & ranks lore
│       ├── TravelService (~65 LOC)        # Sub-location travel validation & execution
│       ├── TravelPromptBuilder (~85 LOC)  # Travel context injection (layer 1e)
│       ├── TravelMarkerParser (~30 LOC)   # [TRAVEL:XXX] marker parsing
│       ├── SubLocation (enum)             # 5 Sweet Dominion locations + Vietnamese aliases
│       ├── LocationUnlockRule             # Shard-based access gates
│       └── LocationService                # Regional location management
│
├── repository/            # Data access layer
│   └── WorldLoreRepository            # Query world_lore table
│
└── websocket/             # WebSocket v4 protocol handler
    └── AimonWebSocket (277 LOC)  # Push-to-talk endpoint
```

**Test Structure:**
```
src/test/java/dev/aimon/
├── websocket/
│   └── AimonWebSocketTest
└── service/
    ├── AudioPipelineServiceTest
    ├── ConversationProcessServiceTest
    ├── TtsProviderServiceTest
    └── ...
```

### aimon-frontend Structure

**6 Core Packages + main.py:**

```
aimon-frontend/
├── main.py                       # Entry point: init HAT, display, state machine
│
├── state/
│   ├── state_machine.py (443+ LOC)   # Main orchestrator: IDLE→LISTENING→ASR→ANSWER→EMOTION, continuous mode toggle + VAD auto-resume (Phase 12)
│   ├── pet-event-handler.py (~157 LOC) # Pet event callbacks, SFX triggers, badge/quest/evolution mgmt (Phase 7)
│   └── __init__.py
│
├── display/                      # 4-layer (+food sprites) compositor UI rendering
│   ├── display_engine.py (117 LOC)      # Pygame wrapper, LCD output via SPI, food sprite rendering
│   ├── layer-compositor.py (155 LOC)    # 4-layer (+food sprites) compositor with dirty-region caching
│   ├── pet-state-model.py (24 LOC)      # PetState dataclass
│   ├── sprite-sheet-manager.py (150+ LOC) # Frame loader, stage lifecycle
│   ├── stat-bar-renderer.py (140+ LOC)  # Hunger/energy/happiness/XP bars
│   ├── speech-bubble-renderer.py (100+ LOC) # Auto-scrolling text overlay
│   ├── badge-popup-renderer.py (~62 LOC) # Badge notification overlay (Phase 7)
│   ├── food-sprite-manager.py (120+ LOC) # Food sprite animation, FIFO queue, auto-eat (Phase 9)
│   ├── sprite_manager.py (legacy fallback)
│   └── __init__.py
│
├── audio/
│   ├── audio_capture.py           # Record OPUS @ 48kHz, continuous mode with VAD (Phase 12)
│   ├── audio_playback.py          # Play PCM16 @ 16kHz, interrupt support
│   ├── voice-activity-detector.py # WebRTC VAD engine for continuous conversation mode (Phase 12)
│   ├── sfx-manager.py             # SFX mixer, channel mgmt, TTS ducking (Phase 7)
│   ├── sfx/                       # SFX audio files (eat.ogg, level-up.ogg, etc.)
│   ├── offline/                   # Offline TTS fallback audio cache
│   └── __init__.py
│
├── network/
│   ├── ws_client.py          # WebSocket v4 client: hello/audio_start/frames/stop
│   └── __init__.py
│
├── hardware/
│   ├── whisplay_hat.py              # ST7789 LCD driver (SPI), button/LED GPIO
│   ├── camera-capture-service.py   # OV5647 CSI → JPEG bytes (picamera2, no disk I/O)
│   ├── vision-analysis-service.py  # JPEG → Gemini 2.5 Flash → food JSON (google-genai)
│   └── __init__.py
│
├── storage/
│   ├── turn_logger.py        # Log conversations for debugging
│   └── __init__.py
│
├── config.py                 # Display constants (LCD_WIDTH, ASSET_DIR, STAGE_ASSET_MAP)
└── tests/                    # Unit tests (audio, config, state, ws, turn_logger)
```

**Key Metrics:**
- 10 modules + handlers + vision hardware, ~1,700 LOC (Python)
- Compositor reduces display updates: 2-3 blits/frame vs. fullscreen redraws
- SFX: 3 reserved channels (primary, notify, ambient) with TTS ducking
- Badge/quest/evolution animations driven by WebSocket events
- Target: 30 FPS on Pi Zero 2; adaptive to 10 FPS during idle (Phase 3 power-save)
- Vision: Gemini 2.5 Flash called directly from Pi (no data sent over WS)
- **Power optimization:** Camera power-gating (-150–250 mA), adaptive FPS (-20–40 mA), backlight auto-dim (-0.1–0.3W)

---

## Key Classes

### WebSocket Handler
**`AimonWebSocket` (277 LOC)**
- Endpoint: `ws://localhost:8080/ws/audio/{robotId}`
- Protocol: v4 (push-to-talk, simplified) + 9 new pet message types (Phase 7)
- States: IDLE → LISTENING → PROCESSING → RESPONDING + quest/evolution overlays
- Handles: hello, audio_start, audio frames, audio_stop, interrupt, ping/pong
- Pet messages: pet_status, pet_feed_result, badge_earned, pet_evolution, pet_transform, pet_warning, pet_regression, quest_start, camera_result

### Audio Pipeline
**`AudioPipelineService`**
- Input: OPUS frames (48kHz, stereo)
- Processing:
  1. Collect OPUS frames during button press
  2. Decode OPUS → PCM16
  3. Downsample 48kHz → 16kHz (mono)
  4. Validate audio quality
  5. Send to Google STT
- Output: Transcript text

**`OpusAudioProcessor` (660 LOC)** — Largest file
- OPUS decoder (libopus via JNI)
- Resampling (48→16kHz)
- Frame buffering
- Quality metrics

### Conversation Processing
**`ConversationProcessService` (298 LOC)**
- Orchestrates conversation flow
- Integrates: PowerMem + LiteLLM + sentence splitting + TTS + interests
- Handles personality injection
- Filters content via Kid Mode
- Streams responses to client
- **Phase 3:** Loads & caches top interests per session, injects into prompt, records topics to PowerMem

### Adaptive Interests (Phase 3)

**`TopicClassifier` (~130 LOC)**
- Classifies Vietnamese messages into 15 topic categories
- Strategy: Keyword matching (< 1ms) → LLM fallback (~5% of messages)
- `classify(message)`: Fast keyword match, LLM fallback if no match
- `classifyKeywordOnly(message)`: Keyword-only, used for timeline re-classification
- 15 categories: khủng long, vũ trụ, động vật, xe cộ, siêu anh hùng, âm nhạc, nghệ thuật, nấu ăn, thể thao, cổ tích, trường học, gia đình, thiên nhiên, khoa học, sách/truyện

**`AdaptiveInterestService` (~100 LOC)**
- Derives top N interests from recent PowerMem observations
- `getTopInterests(robotId)`: Fetches timeline, re-classifies user messages, aggregates topic counts
- Applies diversity cap (default: 60% max frequency per topic)
- Returns top 3 topics (configurable) ranked by frequency
- Cached per session to avoid repeated PowerMem calls
- Configuration: `interest.observation-lookback=30`, `interest.max-topics=3`, `interest.diversity-cap=0.6`

### Memory System
**`PowerMemService` (316 LOC)**
- 3-layer memory via MCP:
  - Short-term: Last 5 exchanges
  - Long-term: Vector-searched facts
  - Episodic: Timestamped observations
- Observation buffering: collects facts during response generation
- Context formatting: preps memory for LLM prompt

### TTS Pipeline
**`TtsProviderService` (240 LOC)** — Circuit breaker pattern
- Primary: VieNeu (Vietnamese, GPU, natural)
- Fallback: Google TTS (reliable, English/Vietnamese)
- Failure handling: switches provider on errors
- Sentence splitting: chunks response before TTS

**`VieNeuTtsService` (263 LOC)** — Largest service
- Calls VieNeu-TTS GPU service (port 5001)
- Voice ID: Ngoc (natural Vietnamese)
- Streams PCM16 chunks to client

### Display & Rendering

**`LayerCompositor` (155 LOC)** — 4-layer rendering system

**Layer Stack:**
1. **Background (cached):** Static scene, loaded per stage
2. **Status bars (cached):** Hunger/energy/happiness/XP bars, redrawn only when stats change
3. **Character sprite (per-frame):** Animated pet sprite, new blit every frame
4. **Speech bubble (per-frame):** Text overlay with auto-scroll, only when text active

**Key Features:**
- **Dirty-region tracking:** Detects stat changes, rebuilds base surface only when needed
- **Pre-composited base:** Layers 1-2 combined into reusable `_base_surface`
- **Per-frame blits:** 2-3 blits/frame (base + character + optional bubble)
- **Performance:** Targets 30 FPS on Pi Zero 2

**`SpriteSheetManager` (150+ LOC)** — Frame-based sprite loading

- Loads PNG frames from `assets/{stage}/animations/{anim}/south/` directories
- Supports Coneko-egg-form & Coneko-baby-Form assets
- Fallback to code-generated placeholder shapes if assets missing
- Caches frames in memory per stage, unloads on stage change to free RAM

**`StatBarRenderer` (140+ LOC)** — Stat visualization

- Renders 3 small bars (top): Hunger, Energy, Happiness (0-100 scale)
- Renders 1 large bar (bottom): Level/XP progression
- Color-coded: green (good), yellow (medium), red (bad)

**`SpeechBubbleRenderer` (100+ LOC)** — Text display overlay

- Auto-scrolling text with character-by-character reveal
- Fade in/out timing for smooth transitions
- Supports multi-line text wrapping

**`DisplayEngine` (115 LOC)** — High-level rendering API

- Wraps LayerCompositor for state machine integration
- Converts Pygame surface to RGB565 format
- Sends RGB565 data to LCD via SPI
- Provides offline/error screen rendering (no compositor)

**`PetState` (24 LOC)** — Immutable-style dataclass

```python
@dataclass
class PetState:
    stage: str              # egg, baby, child, adult
    animation: str          # idle, listening, speaking, happy, sad
    hunger: int             # 0-100
    energy: int             # 0-100
    happiness: int          # 0-100
    level: int              # Pet evolution level
    xp: int                 # Current XP
    xp_for_next: int        # XP needed for next level
```

---

## Service Layer Overview

| Service | Purpose | Lines | Status |
|---------|---------|-------|--------|
| **AudioPipelineService** | OPUS→STT pipeline | ~150 | ✅ Clean |
| **ResponseStreamService** | LLM→TTS→PCM16 pipeline | ~130 | ✅ Clean |
| **ConversationProcessService** | Conversation orchestrator | 298 | ✅ Refactored |
| **TopicClassifier** | Vietnamese topic classification | ~130 | ✅ Phase 3 |
| **AdaptiveInterestService** | Interest detection & caching | ~100 | ✅ Phase 3 |
| **TravelService** | Sub-location travel validation | ~65 | ✅ Phase 2c |
| **TravelPromptBuilder** | Travel context injection (layer 1e) | ~85 | ✅ Phase 2c |
| **TravelMarkerParser** | [TRAVEL:XXX] marker parsing | ~30 | ✅ Phase 2c |
| **PowerMemService** | Memory integration | 316 | ✅ Imported |
| **TtsProviderService** | TTS failover logic | 240 | ✅ Clean |
| **VieNeuTtsService** | Vietnamese TTS | 263 | ✅ Imported |
| **GoogleTtsStreamingService** | Fallback TTS | 215 | ✅ Imported |
| **LiteLlmAIService** | LLM orchestrator | 206 | ✅ Imported |
| **SentenceSplitterService** | Sentence chunking | 218 | ✅ Imported |
| **GoogleSttService** | Speech recognition | ~100 | ✅ Clean |
| **ContextRetrievalService** | Memory retrieval | 247 | ✅ Imported |

---

## Technology Stack

| Layer | Technology |
|-------|-----------|
| **Runtime** | Java 21 + Quarkus 3.24.5 |
| **Build** | Maven 3.9+ |
| **Database** | PostgreSQL 16 + pgvector |
| **Audio** | OPUS codec (libopus), PCM16 |
| **STT** | Google Cloud Speech-to-Text |
| **LLM** | LiteLLM proxy (OpenAI, Anthropic, others) |
| **TTS** | VieNeu (primary) + Google TTS (fallback) |
| **Memory** | PowerMem MCP server |
| **Container** | Docker + Docker Compose |

---

## Docker Stack (5 Services)

| Service | Port | Image | Purpose |
|---------|------|-------|---------|
| **postgres** | 5432 | `postgres:16-bullseye` | DB + pgvector |
| **memoryservice** | 8003 | `memoryservice:latest` | PowerMem MCP |
| **litellm** | 4000 | `ghcr.io/berriai/litellm:main` | LLM proxy |
| **vieneu-tts** | 5001 | `vieneu-tts:gpu` | Vietnamese TTS |
| **aimon-backend** | 8080 | `aimon/aimon-backend:latest` | Main backend |

---

## Core Flows

### Push-to-Talk Conversation (v4 Protocol)

```
CLIENT (aimon-frontend)             SERVER (aimon-backend)
  |                                |
  |--- hello (v4) ------------->   |  WebSocket handshake
  |<-- hello_ack ----------------   |  Session created
  |                                |
  |  [Button PRESSED]              |
  |  (State: LISTENING)            |
  |--- audio_start (OPUS) ------>  |
  |--- binary frames ------------>  |  (48kHz, while holding)
  |  [Button RELEASED]             |
  |--- audio_stop ------------->   |
  |                                |
  |  (State: ASR)                  |
  |                    [PROCESSING]|
  |<-- asr_result (text) --------  |  STT result
  |                                |
  |  (State: ANSWER)               |
  |<-- llm_stream (tokens) -----  |  (streaming)
  |<-- llm_stream              --|
  |                                |
  |<-- tts_start (sentence 1) --  |  TTS begins
  |<-- binary PCM16 chunks ----  |  (16kHz, via audio_playback)
  |<-- tts_stop ----------       |
  |<-- tts_start (sentence 2) --  |
  |<-- binary PCM16 chunks ----  |
  |<-- tts_stop ----------       |
  |                                |
  |<-- turn_end ----------------  |  Turn complete
  |  (State: EMOTION)              |
  |  (Display: happy animation)    |
  |                                |
  |  [After delay, return to IDLE] |
  |                                |
  |  [Button PRESSED during TTS] |
  |--- interrupt ------------->   |  Cancel TTS + prepare for new input
  |--- audio_start ----------->    |  New turn
  |   ...
```

**Frontend Display During Turn:**

```
IDLE              LISTENING          ASR              ANSWER            EMOTION
(idle sprite)     (listening sprite)  (idle sprite)    (speaking sprite) (happy sprite)
(stat bars)       (stat bars)         (stat bars)      (stat bars +      (stat bars)
                                                       speech bubble)
```

### Audio Processing Pipeline

```
OPUS Frames (48kHz, stereo)
    ↓
[OpusAudioProcessor]
    ├─ Collect frames into buffer
    ├─ Decode OPUS → PCM16 (48kHz)
    ├─ Validate audio quality
    ├─ Resample 48kHz → 16kHz (mono)
    ↓
[GoogleSttService]
    ├─ Stream PCM16 to Google STT API
    ↓
Transcript (text)
    ↓
[ConversationProcessService]
    ├─ Inject personality (from AIConfig)
    ├─ Retrieve memory context (PowerMem)
    ├─ Call LLM via LiteLLM
    ↓
LLM Response (tokens)
    ↓
[SentenceSplitterService]
    ├─ Chunk response into sentences
    ↓
[TtsProviderService]
    ├─ For each sentence:
    │   ├─ Try VieNeuTtsService (primary)
    │   ├─ Fallback to GoogleTtsStreamingService if fails
    ↓
PCM16 Audio Chunks
    ↓
[ResponseStreamService]
    ├─ Stream to client via WebSocket
    └─ Send tts_start / tts_stop events
```

### Memory Integration

```
[Conversation Turn]
    ↓
[PowerMemService]
    ├─ Retrieve short-term context (last 5 exchanges)
    ├─ Retrieve long-term context (vector search)
    ├─ Format as prompt context
    ↓
[LLM Receives]
    ├─ System prompt + personality
    ├─ Memory context
    ├─ Current user input
    ↓
[LLM Responds]
    ↓
[ObservationBuffer]
    ├─ Collect key facts from response
    ├─ Store back to PowerMem
    ├─ Updates long-term memory
```

---

## Configuration

**Backend key files:**
- `src/main/resources/application.properties` — Quarkus config
- `.env.example` — Backend environment variables
- `src/main/resources/db/migration/V*__*.sql` — Flyway migrations

**Frontend key files:**
- `aimon-frontend/config.py` — All Pi-side constants and env var reads

**Personality & Safety:**
- AIConfig class holds conversation starters, personality traits
- BannedKeyword entity + Kid Mode filtering in ConversationProcessService

**Pi Camera/Vision Env Vars (Phase 8):**

| Var | Default | Purpose |
|-----|---------|---------|
| `GEMINI_API_KEY` | `""` | Google Gemini API key (required for vision) |
| `GEMINI_MODEL` | `gemini-2.5-flash` | Gemini model for food detection |
| `CAMERA_ENABLED` | `"true"` | Enable/disable camera feature |
| `CAMERA_RATE_LIMIT_S` | `30` | Minimum seconds between camera captures |

---

## Database Schema (Minimal)

| Table | Purpose | Notes |
|-------|---------|-------|
| `parents` | Parent profiles | Links robots to guardians |
| `users` | Child profiles | Links to parents |
| `banned_keywords` | Safety filtering | Kid Mode content blocks |
| `world_lore` | World context entries | Phase 8: Cotton Land facts, gated by level/shard |
| `user_shards` | User lore discovery tracking | Phase 2b planned: track unlocked lore entries |
| `pet_profiles` | Pet state + world fields | Added: active_world, current_location (Phase 8) |
| PowerMem tables | 3-layer memory | Managed by memoryService |

**Dropped from backyard:** stories, story_chunks, session_logs, robot_entities, MoE tables, face vectors.

**Phase 8 Additions:**
- `world_lore`: 30+ Cotton Land entries (geography, characters, items, events)
- `user_shards`: Ready for future progression-based discovery system
- `pet_profiles`: Now tracks active world and location for multi-world support

---

## Legacy Codebase (backyard)

**Status:** Deprecated, archived for reference.

**Differences from aimon-backend:**
- 90 files, 11K LOC (vs. 47 files, 6.3K)
- Complex v3 WebSocket protocol with VAD events
- MoE (Mixture of Experts) routing
- Face recognition
- RAG/Story retrieval
- Multiple TTS providers (VbeeVoice, VietTTS)
- Check-in flow and session logging

**Migration Notes:** All reusable services (memory, TTS, conversation) were extracted and ported to aimon-backend. Legacy code removed.

---

## Features Implemented

### Phase 1-5: Completed ✅
1. **Project scaffolding** (Quarkus, Maven, Docker)
2. **Core services** (AI, Audio, Memory, TTS)
3. **WebSocket v4 protocol** (push-to-talk)
4. **Database migrations** (Flyway, PostgreSQL)
5. **Integration & testing** (all phases validated)

### Phase 2c: Location Travel System ✅
- **New:** `SubLocation.java` — Enum of 5 Sweet Dominion sub-locations (Whipcream Spire, Marshmallow Meadow, Candy Lantern Town, Biscuit Hills, Vanilla Promenade)
- **New:** `TravelService.java` — Validates and executes sub-location travel within regions
- **New:** `TravelPromptBuilder.java` — Builds system prompt layer 1e with current location flavor text, travel suggestions, and marker instructions
- **New:** `TravelMarkerParser.java` — Regex parser for `[TRAVEL:XXX]` markers in LLM responses
- **New:** `LocationChangedEvent.java` — CDI event record fired on location change
- **Modified:** `ConversationProcessService.java` — Injects travel prompt context + parses markers
- **Modified:** `PetEventBridge.java` — Observes LocationChangedEvent → WebSocket push with background filename
- **Modified:** `ConversationSession.java` — Tracks suggestedTravelLocations (once-per-session cap)
- **Frontend:** `ws_client.py` + `pet-event-handler.py` — Handle location change events → background swap via WebSocket
- **Sub-locations:** Each maps to display name, Vietnamese alias, background PNG, interest tags, parent region
- **Travel markers:** LLM emits `[TRAVEL:LOCATION_CODE]` to trigger switches (stripped for TTS)
- **Vietnamese aliases:** All 5 locations support Vietnamese names for natural LLM suggestions
- **Interest-based routing:** Travel suggestions ranked by overlap with child's top interests

### Phase 3: Adaptive Interest System ✅
- **New:** `TopicClassifier.java` — Vietnamese topic classification (keyword matching + LLM fallback)
- **New:** `AdaptiveInterestService.java` — Derives top interests from PowerMem timeline
- **Modified:** `MemoryMetadataBuilder.java` — Added `topics()` and `topicSentiment()` builder methods
- **Modified:** `ConversationSession.java` — Added `cachedTopInterests` volatile field for session-level caching
- **Modified:** `ConversationProcessService.java` — Interest loading at session start, prompt injection, topic classification in recordToPowerMemAsync
- **Topic categories:** 15 Vietnamese categories (dinosaurs, space, animals, vehicles, superheroes, music, art, cooking, sports, fairy tales, school, family, nature, science, books)
- **Configuration:** `interest.observation-lookback=30`, `interest.max-topics=3`, `interest.diversity-cap=0.6`
- **Classification strategy:** Fast keyword matching (< 1ms) → LLM fallback (~5% of messages)
- **Prompt injection:** `[SỐ THÍCH CỦA BẠN: topic1, topic2, topic3 — ...]` inserted into system prompt before LLM call

### Phase 7: Game Loop & SFX Integration ✅
- **New:** `aimon-frontend/audio/sfx-manager.py` (OGG mixer, 3 channels, TTS ducking)
- **New:** `aimon-frontend/display/badge-popup-renderer.py` (temporary badge notifications)
- **New:** `aimon-frontend/state/pet-event-handler.py` (pet message callbacks, SFX/animation triggers)
- **New directories:** `audio/sfx/`, `audio/offline/` (SFX assets + offline TTS cache)
- **Modified:** `config.py` (SFX config: channels, ducking volume, badge popup duration)
- **Modified:** `ws_client.py` (9 new pet message handlers)
- **Modified:** `state_machine.py` (SFX/badge/quest/evolution/warning animation integration)
- **Modified:** `display_engine.py` (badge popup renderer param)
- **Pet messages:** pet_status, pet_feed_result, badge_earned, pet_evolution, pet_transform, pet_transform_end, pet_warning, pet_regression, quest_start
- **SFX effects:** eat, level-up, evolution, badge, quest, transform, warning, regression
- **Animation flows:** Quest display, evolution/regression sequences, warning flash, transform overlay

### Phase 8: Camera Vision Direct Refactor ✅
- **Moved** vision analysis from backend (LiteLLM proxy) → Pi-direct Gemini API
- **New:** `aimon-frontend/hardware/vision-analysis-service.py` (google-genai SDK)
- **New:** `aimon-frontend/hardware/camera-capture-service.py` (picamera2, OV5647)
- **Removed from backend:** `VisionAnalysisClient.java`, `VisionAnalysisResult.java`, `VisionConfig`
- **Backend no longer handles:** `camera_photo` WS messages or vision API calls
- **Feed flow:** Pi double-press → JPEG → Gemini → `pet_feed_confirm{food_name}` → backend applies hunger update
- **WS frame size:** Restored to 64KB (no base64 photos over WebSocket)
- **New Pi env vars:** `GEMINI_API_KEY`, `GEMINI_MODEL`

### Phase 9: Enhanced Food Feeding UX ✅
- **Enhanced:** `aimon-frontend/hardware/vision-analysis-service.py` — Gemini returns `sprite_key` (food sprite filename)
- **New:** `aimon-frontend/display/food-sprite-manager.py` (food sprite animation, FIFO queue max 3, tween easing, auto-eat)
- **New:** `aimon-frontend/assets/food/` directory (PNG food sprite assets, 32x32 or 48x48)
- **Modified:** `aimon-frontend/display/layer-compositor.py` — Layer 5 added for food sprite overlay
- **Modified:** `aimon-frontend/display/display_engine.py` — Integrates food sprite rendering
- **Modified:** `aimon-frontend/state/pet-event-handler.py` — on_pet_feed_confirm() integrates FoodSpriteManager
- **Modified:** Backend `PetMessageHandler` — pet_feed_confirm message now includes sprite_key field
- **Feed flow:** Pi camera → Gemini (with sprite_key) → backend pet_feed_confirm → frontend FoodSpriteManager → on-screen food animation
- **UX Features:** Food sprites animate toward pet mouth, auto-eat on hunger rise or timer, max 3 concurrent sprites in queue
- **Sprite assets:** Kebab-case filenames (apple.png, rice.png, milk-bottle.png) match sprite_key from Gemini

### Phase 12: Continuous Conversation Mode with VAD 🔄 IN PROGRESS
- **New:** `aimon-frontend/audio/voice-activity-detector.py` (WebRTC VAD engine interface, ~150 LOC)
- **Modified:** `aimon-frontend/audio/audio_capture.py` — Continuous recording mode with VAD integration
- **Modified:** `aimon-frontend/state/state_machine.py` — Single-press toggle, continuous listening, auto-resume after playback
- **VAD Integration:** Automatic speech end detection, configurable sensitivity, fallback timeout (30s max)
- **UX Features:** Pulsing indicator for active listening, seamless auto-resume after TTS playback
- **Protocol:** WebSocket v4+ (backward compatible, VAD integration transparent to client)

### Features Kept
- PowerMem 3-layer memory ✅
- TTS circuit breaker (VieNeu → Google) ✅
- Kid Mode safety filtering ✅
- Interrupt support ✅
- Streaming responses ✅
- Camera vision (Pi-side, Gemini 2.5 Flash) ✅

### Features Removed (Out of Scope for v0.2)
- MoE routing
- Face recognition
- RAG/Story system
- VbeeVoice, VietTTS
- Check-in flow
- Push-to-talk protocol (replaced by continuous mode + VAD in Phase 12)
- OPUS output encoding
- Backend-side vision analysis (removed in Phase 8)

### Future Additions (Post-v0.2)
- OPUS output encoding (bandwidth optimization)
- Whisper fallback for STT
- More TTS voice IDs
- Analytics & session logging
- Advanced memory pruning
- Vision: non-food LLM reactions (scene description → TTS response)

---

## Performance Targets

| Metric | Target | Status |
|--------|--------|--------|
| **Latency** | <4s per turn (90th %ile) | ✅ On track |
| **Memory** | Persistent across sessions | ✅ PowerMem integration |
| **Interrupt** | <200ms to TTS cancellation | ✅ Clean state management |
| **Concurrent Users** | 10+ simultaneous sessions | ✅ ConcurrentHashMap sessions |

---

## Build & Test

**Build:**
```bash
./mvnw clean package
```

**Test:**
```bash
./mvnw test
```

**Local Dev (without Docker):**
```bash
docker compose up postgres memoryservice litellm vieneu-tts -d
./mvnw quarkus:dev
```

**Docker:**
```bash
docker compose up -d
docker compose logs -f aimon-backend
```

---

## File Statistics

**By Package:**
| Package | Files | LOC |
|---------|-------|-----|
| `service/` | 15 | ~2,800 |
| `dto/` | 12 | ~1,200 |
| `config/` | 3 | ~400 |
| `entity/` | 3 | ~200 |
| `model/` | 4 | ~300 |
| `client/` | 3 | ~200 |
| `websocket/` | 1 | 277 |
| **TOTAL** | **47** | **~6,302** |

---

## Key Metrics Summary

- **Compilation:** ✅ Clean, no warnings
- **Tests:** ✅ All phase validations passed
- **Code Quality:** ✅ No legacy references, file size limits respected
- **Documentation:** ✅ Comprehensive, up-to-date
- **Docker Stack:** ✅ 5-service composition, health checks included

---

## Development Tools & Skills

### Pixel-Art Skill
**Location:** `./.claude/skills/pixel-art/`

Generates pixel art sprites, animations, and rotations via PixelLab API (Python-based skill).

**6 Generation Modes:**
- `sprite`: Text-to-pixel-art (pixflux engine, max 400x400)
- `style`: Style-transfer generation (bitforge engine, max 200x200)
- `animate`: Text-driven animation frames (text engine, max 64x64)
- `skeleton`: Skeleton-pose animation (skeleton engine, max 256x256)
- `rotate`: Multi-direction sprite rotation (rotate engine, max 200x200)
- `edit`: Inpaint/edit existing sprites (inpaint engine, max 200x200)

**AIMON Use Case:** Generate tamagotchi creature sprites, walk cycles, directional variants, and facial expressions for pet customization and world content.

**Scripts:** `generate.py`, `animate.py`, `rotate.py`, `edit.py`, `balance.py`, `pixel_art_utils.py`

**Validation:** Mandatory interview required (use `--skip` to bypass). See `.claude/skills/pixel-art/references/validation-workflow.md`.

---

## Related Documents

- `./docs/system-architecture.md` — Detailed service diagrams
- `./docs/code-standards.md` — Coding conventions
- `./docs/deployment-guide.md` — Docker & production setup
- `./docs/project-overview-pdr.md` — Product requirements
- `./aimon-backend/README.md` — Quick start guide

---

## Questions & Notes

- Q: Can we add OPUS output encoding later? A: Yes, OpusAudioProcessor has infrastructure, just needs output direction
- Q: How is interrupt handled? A: SessionState tracks processing, interrupted flag cancels TTS via ResponseStreamService
- Q: Where is personality defined? A: AIConfig class, hardcoded conversation starters and traits
- Q: How does memory failover work? A: PowerMemService catches errors, continues without memory if service down
