# AI-MON Project Changelog

**Last Updated:** 2026-02-22

All significant changes, features, and fixes are documented here, ordered chronologically (newest first).

---

## Version 0.2+ (Current - In Development)

### [Phase 10] Offline Resilience & Tamagotchi Sync — 2026-02-22

**Status:** COMPLETE

**Scope:** Enable tamagotchi-style gameplay when Pi loses WiFi, with seamless state synchronization on reconnect.

#### New Features

##### Offline Gameplay System
- **OfflineGameEngine**: Orchestrates offline mode gameplay (visual-only, no audio)
  - Starts on WebSocket disconnect (network error detection)
  - Stops on reconnection (network restored)
  - Manages stat decay, feed handler, response bank, event journal

- **OfflineStatEngine**: Background daemon thread for stat decay
  - Decay formula: hunger +1, energy -0.5, happiness -0.3 per 60s
  - Critical thresholds: hunger >= 80 (warning), energy <= 20 (warning), hunger >= 100 (regression)
  - Thread-safe via callback pattern + `_pet_lock`
  - All ticks logged to SQLite journal

- **OfflineFeedHandler**: Double-press button feed mechanics
  - 30-second cooldown between feeds
  - Hunger reduction: -15 per successful feed
  - XP gain: +3 per feed
  - Cooldown enforced via timestamp tracking
  - Eat animation triggered on success

- **OfflineResponseBank**: Vietnamese text feedback (75 phrases, no-repeat)
  - 5 categories: hungry_high, energy_low, happy_high, neutral, critical
  - Context-aware selection based on dominant stat
  - No-repeat logic: exhausts category before repeating
  - Prevents repetitive feedback in extended offline sessions

- **OfflineEventJournal**: SQLite persistent event cache
  - Schema: offline_events table (id, event_type, timestamp, payload, synced)
  - Max 1000 events with auto-prune oldest on insert
  - WAL mode enabled for concurrent reads/writes
  - Thread-safe: `check_same_thread=False` + `threading.Lock`
  - Event types: decay_tick, feed, interaction, xp_gain, level_up, warning, regression

- **Offline Status Indicator**
  - Amber LED: solid on during offline mode
  - UI Label: "Chế độ ngoại tuyến" (offline mode) displayed in corner
  - Visual-only feedback: no audio processing offline
  - Status display module reuses badge-popup-renderer.py infrastructure

##### WebSocket Reconnection & Event Sync
- **Exponential Backoff Reconnection**
  - Strategy: 2s → 4s → 8s → 16s → 32s → 60s (cap)
  - Reduces connection attempts when WiFi unavailable
  - Prevents battery drain during extended outages
  - Automatic reset to 2s on successful connection

- **Offline Event Flush**
  - On reconnect: transmit ALL pending offline_events to backend
  - Events sent in chronological order (timestamp-sorted)
  - Backend processes atomically via new SyncHandler service

- **Backend Sync Handler** (Java/Quarkus)
  - Aggregates offline events into coherent state changes
  - Applies all stat deltas via existing `PetProfileService`
  - Validates final state (clamp hunger/energy to 0-100)
  - Checks level-up thresholds, triggers evolution if applicable
  - Returns authoritative `pet_status` to frontend

- **Last-Write-Wins Sync Strategy**
  - Events processed chronologically
  - Backend state takes precedence in conflicts
  - Frontend syncs local cache to match backend after sync complete
  - Ensures single source of truth

#### Architecture Changes

**Frontend:**
- 6 new Python modules (game engine, stat engine, feed handler, response bank, event journal, config constants)
- Pet state model extended: `offline_since_ts` field for tracking offline duration
- PetState helper methods: `clamp_stats()`, `is_critical()`
- Database schema: offline_events table added to turns.db
- Network module: exponential backoff logic in ws_client.py

**Backend:**
- New SyncHandler service for offline event aggregation
- Integration with PetProfileService for atomic state application
- WebSocket handler updated to recognize and process `offline_sync` messages
- Configuration: offline event batch processing limits

#### Configuration Updates

**New Constants (config.py):**
```python
OFFLINE_EVENT_MAX = 1000              # Max journal events
OFFLINE_DECAY_INTERVAL_S = 60         # Seconds between stat decay
OFFLINE_FEED_COOLDOWN_S = 30          # Seconds between feeds
OFFLINE_FEED_HUNGER_REDUCTION = 15    # Hunger decrease per feed
OFFLINE_XP_INTERACTION = 5            # XP per button tap
OFFLINE_XP_FEED = 3                   # XP per successful feed
OFFLINE_DB_PATH = TURN_DB_PATH        # Reuse turn_logger DB
```

#### Files Changed

**New Files:**
- `aimon-frontend/state/offline-game-engine.py` (~100 LOC)
- `aimon-frontend/state/offline-stat-engine.py` (~80 LOC)
- `aimon-frontend/state/offline-feed-handler.py` (~50 LOC)
- `aimon-frontend/state/offline-response-bank.py` (~60 LOC)
- `aimon-frontend/state/offline-event-journal.py` (~80 LOC)
- `aimon-frontend/data/offline-responses.json` (~120 lines)
- `aimon-backend/src/main/java/dev/aimon/service/offline/SyncHandler.java` (TBD)

**Modified Files:**
- `aimon-frontend/display/pet-state-model.py` — Added `offline_since_ts` field, helper methods
- `aimon-frontend/config.py` — Added offline constants
- `aimon-frontend/storage/turn_logger.py` — Added offline_events schema, WAL pragma
- `aimon-frontend/network/ws_client.py` — Added exponential backoff logic, offline_sync handling
- `aimon-frontend/state/state_machine.py` — Integrated OfflineGameEngine start/stop
- `docs/system-architecture.md` — Added Offline Resilience section (Phase 10)
- `docs/project-overview-pdr.md` — Phase 10 details, completion checklist
- `docs/project-changelog.md` — This file (new)

#### Database Schema

**offline_events Table:**
```sql
CREATE TABLE offline_events (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    event_type TEXT NOT NULL,
    timestamp INTEGER NOT NULL,
    payload TEXT,                              -- JSON serialized
    synced INTEGER DEFAULT 0                   -- 0=pending, 1=synced
);
CREATE INDEX idx_offline_synced ON offline_events(synced, timestamp);
```

**WAL Mode:** Enabled on turns.db for concurrent read/write from tick loop + decay thread.

#### Testing

**Unit Tests:**
- Event journal: insert, prune, get_pending, mark_synced, clear_synced
- Response bank: category selection, no-repeat exhaustion, reset logic
- Stat engine: decay application, critical threshold detection, event logging
- Feed handler: cooldown enforcement, hunger reduction, XP application
- XP/level-up: accumulation, threshold crossing, overflow handling

**Integration Tests:**
- Offline → online transition: state consistency after sync
- Decay + feed concurrency: no race conditions on stat updates
- Event journal recovery: persistence across app restarts
- Exponential backoff: reconnection attempts with correct delays

#### Performance Impact

- **Local Storage:** SQLite journal adds ~100 KB (1000 events × ~100 bytes)
- **Thread Overhead:** One daemon thread for decay (minimal CPU, negligible battery impact)
- **UI Responsiveness:** No change (decay runs in background, non-blocking)
- **Network:** Offline mode reduces bandwidth to zero (no WebSocket traffic)

#### Breaking Changes

None. Offline mode is opt-in; online mode behavior unchanged.

#### Known Limitations

- **No Stage Evolution Offline:** Evolution deferred to backend; prevents divergence
- **No Audio Offline:** Visual-only gameplay; no voice interaction or TTS
- **No Memory Context Offline:** Cannot retrieve PowerMem context without network
- **No LLM Offline:** Conversational AI requires backend LLM service

#### Migration Path

1. Frontend updates: deploy new modules, database schema migration (offline_events table)
2. Backend updates: deploy SyncHandler service, WebSocket message handler
3. Configuration: set offline constants in config.py (defaults provided)
4. Testing: validate offline → online transition, stat consistency

#### Security Considerations

- **No Sensitive Data:** offline_events contains only stat numbers, timestamps, XP
- **JSON Read-Only:** response bank is read-only file, no user input stored
- **Local Storage:** SQLite journal encrypted at rest (device-dependent)
- **Sync Validation:** Backend validates final state, prevents stat overflow/underflow

#### Documentation

- **Updated:** `docs/system-architecture.md` (Offline Resilience System section)
- **Updated:** `docs/project-overview-pdr.md` (Phase 10 details, checklist)
- **New:** `docs/project-changelog.md` (this file)
- **Architecture Diagrams:** Data flow, offline-to-online transition workflow
- **Configuration Guide:** Offline constants and defaults

---

## Version 0.2 (Stable)

### [Phase 9] Enhanced Food Feeding UX — 2026-02-20

**Status:** COMPLETE

**Scope:** Visual food sprite animations during feeding (tamagotchi-style feedback).

#### New Features
- **FoodSpriteManager**: Manages on-screen food sprite animation overlay
  - FIFO queue (max 3 concurrent sprites)
  - Tween animation: top-center → pet mouth (60 frames, 2 seconds @ 30 FPS)
  - Auto-eat: triggered by hunger threshold or 5-second timeout
  - Sprite key integration with Gemini vision API

- **LayerCompositor Enhancement**: Layer 5 for food sprite overlay
  - Rendered after character (Layer 3) but above speech bubble (Layer 4)
  - Per-frame blit with collision detection
  - Seamless integration with existing rendering pipeline

- **Sprite Assets**: PNG files (32x32 or 48x48) in `aimon-frontend/assets/food/`
  - Filenames match sprite_key from Gemini vision (kebab-case convention)
  - Examples: apple.png, rice.png, milk-bottle.png

#### Modified Files
- `aimon-frontend/display/layer-compositor.py` — Added Layer 5, sprite rendering
- `aimon-frontend/display/food-sprite-manager.py` — New module (120+ LOC)
- `aimon-frontend/state/pet-event-handler.py` — Integrated food sprite queuing
- Backend `PetMessageHandler` — Updated pet_feed_confirm to include sprite_key
- `docs/system-architecture.md` — Camera Vision Feed Flow, Food Sprite Manager sections

#### Testing
- Unit tests: sprite queue FIFO behavior, tween animation calculations
- Integration tests: feed → sprite animation → auto-eat sequence
- Asset validation: sprite filename conventions

---

### [Phase 8] Camera Vision Direct Refactor — 2026-02-15

**Status:** COMPLETE

**Scope:** Move vision analysis from backend to Pi-direct Gemini API (reduce WebSocket frame bloat).

#### New Features
- **Pi-Direct Vision Analysis**: Google Gemini 2.5 Flash (no backend relay)
  - Python google-genai SDK on Pi
  - JPEG bytes never cross WebSocket (64KB frame limit restored)
  - JSON response: `{is_food, food_name, description, sprite_key}`

- **Camera Capture Service**: `aimon-frontend/hardware/camera-capture-service.py`
  - OV5647 CSI camera via picamera2
  - 640x480 JPEG output, no disk I/O
  - 30-second rate limiting

- **Vision Analysis Service**: `aimon-frontend/hardware/vision-analysis-service.py`
  - Gemini 2.5 Flash prompt engineering
  - Vietnamese food name detection
  - Sprite key mapping for food sprites

#### Removed from Backend
- `VisionAnalysisClient.java`
- `VisionAnalysisResult.java`
- `VisionConfig` class
- Backend vision API calls (no LiteLLM relay)

#### Configuration
- **Pi-only env vars:** `GEMINI_API_KEY`, `GEMINI_MODEL`
- **Rate limits:** Camera (30s), Vision service (independent)

#### Documentation
- `docs/system-architecture.md` — Camera Vision Feed Flow section (Phase 8)

---

### [Phase 7] Game Loop Mechanics & SFX — 2026-02-10

**Status:** COMPLETE

**Scope:** Full game loop with stat updates, SFX feedback, and badge notifications.

#### New Features
- **Pet Status Updates**: Real-time hunger/energy/happiness/XP/level display
- **SFX Channels**: 3-channel mixer with TTS ducking
  - Channel 1 (primary): eat, level-up, evolution, transform
  - Channel 2 (notify): badge, quest
  - Channel 3 (ambient): warning, regression

- **Badge Notifications**: 3-second popup with SFX trigger
- **Evolution/Regression Animations**: Stage transitions with feedback
- **Quest System**: Multi-turn questions with voice interaction

#### Files
- `aimon-frontend/audio/sfx-manager.py` — 3-channel mixer (160+ LOC)
- `aimon-frontend/display/badge-popup-renderer.py` — Badge display (90+ LOC)
- `aimon-frontend/state/pet-event-handler.py` — WebSocket pet event callbacks (180+ LOC)

---

### [Phase 6] Database & Configuration — 2026-02-05

**Status:** COMPLETE

**Scope:** PostgreSQL schema, Flyway migrations, environment management.

#### New Features
- **Flyway Migrations**: V1-V3 (Parent, User, BannedKeyword tables)
- **JPA Entities**: Parent, User, BannedKeyword
- **AIConfig**: Personality injection, prompts, feature flags
- **Health Checks**: /health, /health/ready, /health/live endpoints

#### Files
- `src/main/resources/db/migration/V1__initial_schema.sql`
- `src/main/resources/db/migration/V2__world_lore.sql`
- `src/main/resources/db/migration/V3__tasteless_combat_system.sql`
- Configuration classes (AIConfig, ApplicationConfig)

---

### [Phase 5] Integration & Testing — 2026-02-01

**Status:** COMPLETE

**Scope:** End-to-end testing, Docker Compose orchestration, comprehensive documentation.

#### Deliverables
- Full integration test suite (all components)
- Docker Compose stack (5 services: postgres, memoryservice, litellm, vieneu-tts, aimon-backend)
- Health checks & monitoring
- Performance validation (<4s turn latency)
- Complete documentation suite (5 docs, 3,674 LOC)

---

### [Phase 4] World Lore System — 2026-01-28

**Status:** COMPLETE

**Scope:** Inject contextual world-building facts into conversations.

#### New Features
- **WorldLoreService**: Fetch unlocked lore by world, level, interests
- **world_lore Table**: Lore entries (world_code, category, content, min_level, interest_tags, shard_type)
- **Interest-Based Ranking**: Prioritize lore matching child's detected interests
- **Max-Inject Limit**: Configurable entries per turn (default: 3)

#### Database
- `src/main/resources/db/migration/V2__world_lore.sql` — world_lore + user_shards schema
- Seed data: Cotton Land lore (30+ entries)

#### Configuration
```properties
world.lore.max-inject=3
```

---

### [Phase 3] Adaptive Interest System — 2026-01-25

**Status:** COMPLETE

**Scope:** Detect and track child's evolving interests from conversations.

#### New Features
- **TopicClassifier**: Keyword matching + LLM fallback (~130 LOC)
  - 15 Vietnamese topic categories
  - Fast keyword-only path (<1ms)
  - LLM semantic fallback (~5% of messages)

- **AdaptiveInterestService**: Interest aggregation and caching (~100 LOC)
  - Fetches recent PowerMem timeline
  - Applies diversity cap (60% default) to prevent dominance
  - Returns top 3 topics ranked by frequency
  - Cached per session

- **Prompt Injection**: Interest hint in system prompt
  - Format: "[SỐ THÍCH CỦA BẠN: topic1, topic2, topic3 — ...]"
  - Guides LLM to mention topics naturally

#### Configuration
```properties
interest.observation-lookback=30        # Days
interest.max-topics=3
interest.diversity-cap=0.6
```

#### Files
- `service/ai/TopicClassifier.java`
- `service/ai/AdaptiveInterestService.java`
- Updated `ConversationProcessService` with interest prompt injection

---

### [Phase 2] Core Services — 2026-01-15

**Status:** COMPLETE

**Scope:** Port 33 services from backyard, implement audio pipeline, STT, TTS, LLM.

#### Services Ported
- Audio pipeline (OPUS decode, resample 48→16kHz)
- Google STT integration
- Memory services (PowerMem MCP client)
- TTS services (VieNeu, Google fallback)
- LLM orchestration (LiteLLM)
- Conversation flow (personality, memory, safety filtering)
- Response streaming

#### Deliverable
- 33 services, ~3,500 LOC
- Service layer complete and tested

---

### [Phase 1] Project Scaffolding — 2026-01-10

**Status:** COMPLETE

**Scope:** Quarkus project setup, Maven build, Docker Compose structure.

#### Deliverables
- Quarkus 3.24.5 project
- Maven build configuration
- Docker Compose template (5 services)
- Initial package hierarchy (8 packages)
- GitHub repository setup

---

## Version 0.1 (Legacy - Backyard)

Legacy implementation (90 files, 11K LOC) — replaced by v0.2 clean refactor.

---

## Summary by Phase

| Phase | Title | Status | Date | LOC Added | Files |
|-------|-------|--------|------|-----------|-------|
| 10 | Offline Resilience | ✅ COMPLETE | 2026-02-22 | ~450 | 7 new + 5 modified |
| 9 | Enhanced Food UX | ✅ COMPLETE | 2026-02-20 | ~180 | 3 modified |
| 8 | Camera Vision Direct | ✅ COMPLETE | 2026-02-15 | ~220 | 3 new + 2 modified |
| 7 | Game Loop & SFX | ✅ COMPLETE | 2026-02-10 | ~400 | 4 new + 3 modified |
| 6 | Database & Config | ✅ COMPLETE | 2026-02-05 | ~350 | 6 new + 2 modified |
| 5 | Integration & Tests | ✅ COMPLETE | 2026-02-01 | ~600 | 20+ modified |
| 4 | World Lore System | ✅ COMPLETE | 2026-01-28 | ~200 | 3 new + 4 modified |
| 3 | Adaptive Interests | ✅ COMPLETE | 2026-01-25 | ~230 | 2 new + 3 modified |
| 2 | Core Services | ✅ COMPLETE | 2026-01-15 | ~3500 | 33 new + 5 modified |
| 1 | Scaffolding | ✅ COMPLETE | 2026-01-10 | ~200 | 8 new + 0 modified |
| **Total** | | | | **~6,300 LOC** | **47 files** |

---

## Key Metrics

### Code Quality
- **v0.2+ Total:** 6,750+ LOC (47 core files + offline modules)
- **Legacy (v0.1):** 11,000 LOC (90 files)
- **Reduction:** 43% fewer lines, 48% fewer files
- **Max File Size:** 277 LOC (clean modularization)

### Performance
- **Turn Latency (p90):** <4s target, ~2-3s actual
- **Concurrent Sessions:** 10+ per instance
- **Setup Time:** <5 minutes (Docker Compose)

### Features Delivered
- Push-to-talk voice interaction
- Real-time STT/LLM/TTS streaming
- 3-layer memory system (PowerMem)
- Kid Mode safety filtering
- Game loop with SFX feedback
- Camera vision (Pi-direct Gemini)
- Food sprite animations
- **NEW:** Offline tamagotchi gameplay
- **NEW:** Event-based sync on reconnect

---

## Next Steps (Future Roadmap)

**Post-v0.2+ (v0.3 - Q2 2026):**
- [ ] Unit test coverage (70%+)
- [ ] TLS/HTTPS setup
- [ ] API authentication
- [ ] Advanced monitoring dashboard

**Q3 2026 (v0.4):**
- [ ] Kubernetes migration
- [ ] Multi-language TTS support
- [ ] Advanced memory pruning
- [ ] Analytics integration

**Q4 2026 (v0.5):**
- [ ] OPUS output encoding
- [ ] Whisper STT fallback
- [ ] Model fine-tuning
- [ ] Community beta launch

---

**Document Maintained By:** Documentation Team
**Last Updated:** 2026-02-22
**Status:** Production Ready (v0.2+)
