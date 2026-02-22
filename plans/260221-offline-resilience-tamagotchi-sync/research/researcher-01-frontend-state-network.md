# Researcher-01: Frontend State & Network — Offline Resilience Analysis
Date: 2026-02-21 | Scope: state_machine, ws_client, pet-state-model, whisplay_hat, turn_logger

---

## 1. state_machine.py

### Current OFFLINE handling
- `State.OFFLINE` enum exists; `_offline: bool` flag tracks disconnection
- On disconnect: mic off → playback stop → `_offline = True` → `_set_state(State.IDLE)` → play clip → spawn `_reconnect_loop` thread
- **NOTE:** `_set_state(State.IDLE)` is called, NOT `State.OFFLINE` — the `_LED_MAP` has `State.OFFLINE` mapped but `_on_disconnect` never actually enters that state enum; the `_offline` bool is a shadow flag
- On button press while `_offline=True`: `_play_offline_clip()` instead of starting conversation
- On reconnect: `_on_reconnect` → `send_hello()` → `_on_hello_ack` sets `_offline = False` → `_set_state(State.IDLE)`
- `_reconnect_loop` calls `self._ws.reconnect()` — blocking, runs in daemon thread

### Insertion points for offline resilience
| Where | What to add |
|---|---|
| `_on_disconnect()` (line 402-408) | Queue a "session_interrupted" offline event to SQLite before reconnect loop |
| `_on_single_press_confirmed()` (line 228-237) | Already gated on `self._offline`; add tamagotchi-mode interaction here |
| `_on_hello_ack()` (line 319-322) | Trigger sync flush: send queued offline events to backend on reconnect |
| `_on_reconnect_failed` (does not exist) | Add callback for when all reconnect attempts exhaust; persist offline duration |
| `tick()` IDLE branch | Show offline indicator overlay if `self._offline` |

### Thread safety
- `_pet_state` guarded by `_pet_lock` (threading.Lock) — safe for cross-thread writes
- `_press_lock` guards double-press state — safe
- `_offline` bool is read/written from main thread AND ws callback thread — **not locked** (acceptable for boolean flip, but worth noting)
- `_reconnect_loop` runs in daemon thread; do not touch `_pet_state` from it without `_pet_lock`

### Gotchas
- Reconnect loop is **fire-and-forget** — no way to cancel if user shuts down mid-reconnect
- `WS_RECONNECT_MAX_ATTEMPTS = 5` with `WS_RECONNECT_INTERVAL_S = 2` → max 10s retry window, then gives up silently; need infinite retry or at least a "gave up" UI state
- `_play_offline_clip()` depends on `pygame.mixer` being initialized — wrapped safely
- `_STATE_ANIMATION_MAP[State.OFFLINE] = "idle"` but state never actually becomes OFFLINE in current code (led map entry wasted)

---

## 2. network/ws_client.py

### Disconnect detection
- `_recv_loop` (line 158-174): catches any Exception except TimeoutError → calls `_handle_disconnect()`
- `_handle_disconnect()` (line 279-283): sets `_connected = False`, fires `on_disconnect` callback
- `send_audio_frame` and `_send_json` also call `_handle_disconnect()` on send failure

### Reconnect logic
- `reconnect()` (line 74-85): calls `disconnect()` first (joins recv thread), then retries `connect()` up to `WS_RECONNECT_MAX_ATTEMPTS` times with `WS_RECONNECT_INTERVAL_S` sleep
- Returns `False` on exhaustion — **no callback fired on permanent failure**
- `on_reconnect` fires immediately after successful `connect()` — before `hello_ack`

### Sync plug-in points
| Where | What |
|---|---|
| After `if self.on_reconnect:` (line 80) | Add `on_reconnect_success` callback for sync manager to initiate flush |
| After exhausted retry loop (line 84) | Add `on_reconnect_failed` callback; state machine can show permanent-offline UI |
| `_send_json()` (line 148-154) | Intercept failed sends — buffer them for retry (offline queue) |
| New method `send_offline_events(events)` | Batch-send queued events post-reconnect as JSON array |

### Thread safety
- `_connected` and `_ws` written from recv thread (via `_handle_disconnect`) and read from main thread send methods — **no lock**; bool assignment is GIL-safe in CPython but fragile
- `disconnect()` joins `_recv_thread` — safe shutdown
- Adding an offline queue here needs a `threading.Lock` around the deque

### Gotchas
- `disconnect()` calls `_recv_thread.join(timeout=2.0)` — if recv thread is blocked on WS, it may not exit cleanly within 2s
- `ws_sync.connect()` has no explicit timeout param beyond `close_timeout=5` — slow connect could block reconnect loop for many seconds on Pi's weak networking
- No heartbeat/ping logic beyond `send_ping()` (never auto-called) — dead connection not detected until next send or recv exception

---

## 3. display/pet-state-model.py

### PetState dataclass
```python
@dataclass
class PetState:
    stage: str = "egg"       # egg, baby, child, adult
    variant: str = None
    animation: str = "idle"
    direction: str = "south"
    char_x: int = -1
    char_y: int = -1
    hunger: int = 50         # 0-100, high=bad
    energy: int = 100        # 0-100, low=bad
    happiness: int = 80      # 0-100, low=bad
    level: int = 1
    xp: int = 0
    xp_for_next: int = 50
    mood: str = "neutral"
```

### Thread safety model
- Docstring says "Mutate from main loop only" — but `pet-event-handler` and `state_machine._on_disconnect` mutate via `update_pet_state()` which uses `_pet_lock`
- `dataclasses.replace(self._pet_state)` under lock creates immutable snapshot for renderer — correct pattern
- No `__post_init__` validation; values can go out of range (0-100) — add clamp if offline decay is implemented

### Insertion points for offline tamagotchi decay
- Add `last_synced_ts: float = 0.0` field — timestamp of last backend sync
- Add `offline_since_ts: float = 0.0` field — when disconnect started
- Decay logic should NOT live in PetState itself; call `update_pet_state()` from a new `offline-tamagotchi-engine.py` module, acquire `_pet_lock` via the existing `StateMachine.update_pet_state()` method

### Gotchas
- PetState is a plain mutable dataclass — no dirty-flag; after offline decay, caller must trigger `display.on_stats_changed()` manually (same as `update_pet_state` does)
- `stage` and `variant` are strings, not enums — no type safety; offline code must use exact same string literals

---

## 4. hardware/whisplay_hat.py

### LED color methods
- `set_rgb(r, g, b)` — 0-255 per channel, common-anode (inverted duty cycle)
- `set_rgb_tuple(rgb)` — convenience wrapper, used everywhere in state machine via `_LED_MAP`

### Existing color constants (from config.py)
| Constant | Value | Usage |
|---|---|---|
| `LED_IDLE` | not shown | IDLE state |
| `LED_LISTENING` | not shown | LISTENING state |
| `LED_ASR` | not shown | ASR state |
| `LED_ANSWER` | not shown | ANSWER state |
| `LED_EMOTION_HAPPY` | not shown | EMOTION state |
| `LED_OFFLINE` | `(200, 30, 30)` | OFFLINE (currently unused in state transitions) |
| `LED_CAMERA` | not shown | Camera capture |

### Insertion points
- No changes needed to `whisplay_hat.py` itself — LED API is complete
- In config.py: add `LED_OFFLINE_PULSE` for a pulsing amber variant during reconnect vs hard-offline
- State machine: fix `_on_disconnect` to call `_set_state(State.OFFLINE)` instead of `State.IDLE` so `LED_OFFLINE` actually activates

### Gotchas
- HAT is a singleton — no concurrency issue with LED calls, but rapid calls from multiple threads can interleave PWM writes; wrap with a lock if pulsing LED from background thread
- No LED animation/pulse built-in — must be driven from `tick()` in main loop using frame counter

---

## 5. storage/turn_logger.py

### SQLite setup
- `check_same_thread=False` — safe for multi-thread access (SQLite WAL mode would be better but not configured)
- DB path from `config.TURN_DB_PATH`, default `~/.aimon/turns.db` (inferred from env var pattern)
- Auto-creates dir on init

### Current schema
```sql
CREATE TABLE IF NOT EXISTS turns (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    turn_id TEXT,
    timestamp INTEGER NOT NULL,
    user_text TEXT NOT NULL,
    assistant_text TEXT NOT NULL,
    emotion TEXT,
    duration_ms INTEGER
);
```

### Adding offline_events table
Add to `_SCHEMA` string — `executescript` is idempotent via `IF NOT EXISTS`:

```sql
CREATE TABLE IF NOT EXISTS offline_events (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    event_type TEXT NOT NULL,      -- 'disconnect', 'pet_decay', 'button_press', 'reconnect'
    timestamp INTEGER NOT NULL,
    payload TEXT,                  -- JSON blob for event-specific data
    synced INTEGER DEFAULT 0       -- 0=pending, 1=flushed to backend
);
CREATE INDEX IF NOT EXISTS idx_offline_events_synced ON offline_events(synced, timestamp);
```

### New methods needed on TurnLogger (or separate OfflineEventLogger)
- `log_offline_event(event_type, payload_dict)` — insert with `synced=0`
- `get_pending_events(limit=50)` — return unsynced rows for flush
- `mark_synced(ids)` — update `synced=1` for given id list
- `purge_synced(older_than_ts)` — cleanup flushed events

### Thread safety
- `self._conn` with `check_same_thread=False` means multiple threads CAN call it, but SQLite default journal mode has write serialization; concurrent writes are safe but may block
- Add a `threading.Lock` (`self._lock`) around all write operations for correctness — or switch to WAL: `self._conn.execute("PRAGMA journal_mode=WAL")`
- Recommend WAL pragma on init — single writer, multiple readers, no blocking on tick loop reads

### Gotchas
- `executescript` commits any pending transaction — fine for schema init but don't call mid-operation
- No explicit `close()` call path in current code beyond `TurnLogger.close()` — ensure it's called in main app shutdown
- `TURN_MAX_COUNT = 200` applies only to `turns` table; add separate max for `offline_events` (suggest 500)

---

## Summary: Key Integration Points

| File | Change | Priority |
|---|---|---|
| `state_machine.py` | Fix `_on_disconnect` to enter `State.OFFLINE` properly; add sync flush on `_on_hello_ack` | HIGH |
| `ws_client.py` | Add `on_reconnect_failed` callback; add connect timeout; consider heartbeat ping | HIGH |
| `turn_logger.py` | Add `offline_events` table + 4 new methods | HIGH |
| `pet-state-model.py` | Add `offline_since_ts` field for decay calculation | MEDIUM |
| `config.py` | Add `LED_OFFLINE_PULSE`, infinite reconnect option, offline event max count | LOW |

---

## Unresolved Questions
1. Should offline tamagotchi decay (hunger/energy) run from a background thread or from `tick()`? Background thread = more accurate but requires `_pet_lock` discipline. tick() = simpler but frame-rate dependent.
2. What is the backend sync protocol for offline events — new WS message type, or REST endpoint? Affects where `send_offline_events` lives and when it's called relative to `send_hello`.
3. Should `WS_RECONNECT_MAX_ATTEMPTS` become infinite (retry forever) or stay bounded with a "gave up" UI state? Pi always-on device suggests infinite with exponential backoff.
4. Is WAL mode acceptable for the Pi Zero 2's SD card write endurance? WAL increases write frequency slightly.
