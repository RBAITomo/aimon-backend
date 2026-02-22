---
phase: 2
title: "Game Engine & Stat Decay"
status: complete
effort: 2h
---

# Phase 2: Offline Game Engine & Stat Decay

## Context Links
- [Phase 1](phase-01-offline-event-journal-and-response-bank.md) — journal + response bank (dependency)
- [pet-state-model.py](../../aimon-frontend/display/pet-state-model.py) — PetState dataclass
- [state_machine.py](../../aimon-frontend/state/state_machine.py) — _pet_lock, update_pet_state

## Overview
**Priority:** HIGH — core offline gameplay loop
Create stat decay timer, feed handler, and orchestrator that ties offline gameplay together.

## Key Insights
- Decay runs from background thread (daemon), acquires `_pet_lock` via callback
- PetState is mutable dataclass — add `offline_since_ts` field, clamp values 0-100
- Feed uses double-press button with 30s cooldown — need timestamp tracking
- XP accumulates locally; level-up increments level but NO stage evolution offline

## Requirements
**Functional:**
- Stat decay every 60s: hunger +1, energy -0.5, happiness -0.3
- Manual feed: -15 hunger, 30s cooldown, eat animation trigger
- XP: +5 per interaction, +3 per feed; local level-up when xp >= xp_for_next
- Warning animations at critical thresholds (hunger >= 80, energy <= 20)
- Regression animation if hunger hits 100

**Non-functional:**
- Thread-safe stat mutations via _pet_lock callback pattern
- Daemon thread for decay timer (auto-stops on main exit)

## Architecture
```
offline-game-engine.py (orchestrator)
  ├── offline-stat-engine.py (decay timer thread)
  ├── offline-feed-handler.py (feed logic + cooldown)
  ├── offline-response-bank.py (from Phase 1)
  └── offline-event-journal.py (from Phase 1)
```
Orchestrator exposes: `start()`, `stop()`, `on_interaction()`, `on_feed()`, `get_state_snapshot()`

## Related Code Files

### CREATE
| File | LOC | Purpose |
|------|-----|---------|
| `aimon-frontend/state/offline-game-engine.py` | ~100 | Orchestrator |
| `aimon-frontend/state/offline-stat-engine.py` | ~80 | Decay timer thread |
| `aimon-frontend/state/offline-feed-handler.py` | ~50 | Feed logic + cooldown |

### MODIFY
| File | Change |
|------|--------|
| `aimon-frontend/display/pet-state-model.py` | Add `offline_since_ts: float = 0.0` field |

## Implementation Steps

1. **Modify pet-state-model.py**
   - Add field: `offline_since_ts: float = 0.0`
   - Add helper: `clamp_stats()` — clamp hunger/energy/happiness to 0-100 range
   - Add helper: `is_critical() -> bool` — True if hunger >= 80 or energy <= 20

2. **Create offline-stat-engine.py**
   - Class `OfflineStatEngine`
   - Init: takes `update_callback(fn)`, `journal: OfflineEventJournal`, `interval_s` from config
   - `start()` — spawn daemon thread with `threading.Event` for cancellation
   - `stop()` — set event, join thread
   - Decay loop: sleep interval_s → call `update_callback` with decay deltas
   - Callback receives `(hunger_delta=1, energy_delta=-0.5, happiness_delta=-0.3)`
   - Log `decay_tick` event to journal with payload `{hunger, energy, happiness}` after apply
   - Check critical thresholds post-decay → log `warning` or `regression` event

3. **Create offline-feed-handler.py**
   - Class `OfflineFeedHandler`
   - Init: takes `update_callback(fn)`, `journal`, cooldown_s from config
   - `_last_feed_ts: float = 0`
   - `try_feed() -> bool` — check cooldown, apply -15 hunger via callback, log `feed` event, +3 XP via callback, return True/False
   - `can_feed() -> bool` — check if cooldown elapsed

4. **Create offline-game-engine.py**
   - Class `OfflineGameEngine`
   - Init: takes `pet_state_updater(fn)`, `display_callback(fn)`, `journal`, `response_bank`
   - Composes: `OfflineStatEngine`, `OfflineFeedHandler`
   - `start(offline_since_ts)` — start decay engine, set timestamp
   - `stop() -> dict` — stop decay, return final state snapshot for sync
   - `on_interaction()` — get response from bank based on current stats, +5 XP, log `interaction` event, trigger display callback with text
   - `on_feed()` — delegate to feed handler, trigger eat animation if success
   - `_apply_xp(amount)` — add XP, check level-up (xp >= xp_for_next → level+1, xp reset, xp_for_next increases), log `xp_gain`/`level_up`
   - XP formula: `xp_for_next = 50 * level` (matches PetLevelConfig on backend)
   - `_update_stats(hunger_d, energy_d, happiness_d)` — call pet_state_updater, clamp, check critical

5. **Level-up logic**
   - Local only: increment `level`, reset `xp` to overflow amount
   - Do NOT change `stage` — evolution deferred to backend sync
   - Log `level_up` event with `{new_level, xp_overflow}`

## Todo List
- [x] Add `offline_since_ts` + `clamp_stats()` + `is_critical()` to pet-state-model.py
- [x] Create offline-stat-engine.py with daemon decay thread
- [x] Create offline-feed-handler.py with cooldown logic
- [x] Create offline-game-engine.py orchestrator
- [x] Test: decay applies correctly over multiple ticks
- [x] Test: feed respects cooldown
- [x] Test: XP accumulation and level-up threshold

## Success Criteria
- Decay runs consistently every 60s in background thread
- Feed cooldown enforced, hunger decreases correctly
- XP accumulates, level-up triggers at threshold
- All events logged to journal
- No race conditions under concurrent access

## Risk Assessment
- **Timer drift**: `time.sleep(60)` may drift. Mitigation: use absolute next-tick timestamps
- **Thread deadlock**: decay thread + feed both acquire _pet_lock. Mitigation: keep critical sections short, never nest locks

## Security Considerations
- No network calls in this phase — all local
- Stats clamped to prevent overflow/underflow exploits

## Next Steps
Phase 3 wires this engine into state_machine.py and ws_client.py for full integration.
