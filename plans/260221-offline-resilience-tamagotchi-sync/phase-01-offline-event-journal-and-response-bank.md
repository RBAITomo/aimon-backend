---
phase: 1
title: "Event Journal & Response Bank"
status: complete
effort: 2h
---

# Phase 1: Offline Event Journal & Response Bank

## Context Links
- [Research: Frontend State & Network](research/researcher-01-frontend-state-network.md)
- [turn_logger.py](../../aimon-frontend/storage/turn_logger.py) — existing SQLite logger
- [config.py](../../aimon-frontend/config.py) — constants

## Overview
**Priority:** HIGH — foundational for all other phases
Create SQLite event journal for offline events and Vietnamese response bank for visual feedback.

## Key Insights
- turn_logger.py already uses `check_same_thread=False`; add `offline_events` table to same DB
- WAL mode recommended for concurrent read/write from tick loop + decay thread
- Max 1000 events with auto-prune oldest when full
- Response bank: ~50-100 curated Vietnamese phrases keyed to stat conditions

## Requirements
**Functional:**
- SQLite `offline_events` table with columns: id, event_type, timestamp, payload (JSON), synced
- CRUD: log_event, get_pending, mark_synced, prune (max 1000)
- JSON response bank with categories: hungry_high, energy_low, happy_high, neutral, critical
- No-repeat-until-exhausted selection logic per category

**Non-functional:**
- Thread-safe writes (WAL mode + threading.Lock)
- <1ms per insert on Pi Zero 2

## Architecture
```
offline-event-journal.py ──► SQLite (turns.db, offline_events table)
offline-response-bank.py ──► offline-responses.json (read-only)
```

## Related Code Files

### CREATE
| File | LOC | Purpose |
|------|-----|---------|
| `aimon-frontend/state/offline-event-journal.py` | ~80 | SQLite event log |
| `aimon-frontend/data/offline-responses.json` | ~120 | Vietnamese response bank data |
| `aimon-frontend/state/offline-response-bank.py` | ~60 | Response selection logic |

### MODIFY
| File | Change |
|------|--------|
| `aimon-frontend/storage/turn_logger.py` | Add offline_events table to _SCHEMA, WAL pragma |
| `aimon-frontend/config.py` | Add OFFLINE_EVENT_MAX=1000, OFFLINE_DECAY_INTERVAL_S=60 |

## Implementation Steps

1. **Add offline constants to config.py**
   - `OFFLINE_EVENT_MAX = 1000`
   - `OFFLINE_DECAY_INTERVAL_S = 60`
   - `OFFLINE_FEED_COOLDOWN_S = 30`
   - `OFFLINE_FEED_HUNGER_REDUCTION = 15`
   - `OFFLINE_XP_INTERACTION = 5`
   - `OFFLINE_XP_FEED = 3`
   - `OFFLINE_DB_PATH` — reuse TURN_DB_PATH (same DB)

2. **Modify turn_logger.py schema**
   - Add WAL pragma in `__init__`: `self._conn.execute("PRAGMA journal_mode=WAL")`
   - Append to `_SCHEMA`:
     ```sql
     CREATE TABLE IF NOT EXISTS offline_events (
         id INTEGER PRIMARY KEY AUTOINCREMENT,
         event_type TEXT NOT NULL,
         timestamp INTEGER NOT NULL,
         payload TEXT,
         synced INTEGER DEFAULT 0
     );
     CREATE INDEX IF NOT EXISTS idx_offline_synced ON offline_events(synced, timestamp);
     ```

3. **Create offline-event-journal.py**
   - Class `OfflineEventJournal` — accepts DB path from config
   - Own `sqlite3.connect(check_same_thread=False)` + WAL + `threading.Lock`
   - `log_event(event_type: str, payload: dict) -> int` — insert, auto-prune if count > 1000
   - `get_pending(limit=200) -> list[dict]` — return unsynced rows ordered by timestamp
   - `mark_synced(ids: list[int])` — batch UPDATE synced=1
   - `clear_synced()` — DELETE WHERE synced=1
   - `get_all_pending_for_sync() -> list[dict]` — get ALL unsynced for reconnect flush
   - Event types: `decay_tick, feed, interaction, xp_gain, level_up, warning, regression`

4. **Create offline-responses.json**
   - Top-level keys: `hungry_high`, `energy_low`, `happy_high`, `neutral`, `critical`
   - Each key maps to array of Vietnamese strings (~15-20 per category)
   - Example: `"hungry_high": ["Bụng mình đói quá à...", "Cho mình ăn đi bạn ơi!"]`

5. **Create offline-response-bank.py**
   - Class `OfflineResponseBank` — load JSON on init
   - Track `_used_indices: dict[str, set]` per category for no-repeat
   - `get_response(dominant_condition: str) -> str` — pick random unused phrase
   - `determine_condition(hunger, energy, happiness) -> str` — return dominant stat key
   - Reset used set when exhausted for a category

## Todo List
- [x] Add offline constants to config.py
- [x] Add WAL pragma + offline_events schema to turn_logger.py
- [x] Create offline-event-journal.py with CRUD methods
- [x] Create offline-responses.json with Vietnamese phrases
- [x] Create offline-response-bank.py with no-repeat logic
- [x] Unit test: journal insert, prune, get_pending, mark_synced
- [x] Unit test: response bank exhaustion + reset

## Success Criteria
- Journal handles 1000+ events with auto-prune
- Response bank returns non-repeating phrases until exhausted
- Thread-safe: no crashes under concurrent access from tick + decay threads

## Risk Assessment
- **SD card wear**: WAL increases writes slightly. Mitigation: batch inserts, prune aggressively
- **JSON load time**: ~120 phrases loads in <10ms on Pi Zero 2. Low risk.

## Security Considerations
- No sensitive data in offline_events (only stat numbers, timestamps)
- JSON file read-only, no user input stored in response bank

## Next Steps
Phase 2 depends on journal (to log decay/feed events) and response bank (to show feedback).
