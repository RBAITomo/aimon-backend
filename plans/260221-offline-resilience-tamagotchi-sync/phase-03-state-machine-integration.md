---
phase: 3
title: "State Machine Integration"
status: complete
effort: 2.5h
---

# Phase 3: State Machine Integration

## Context Links
- [Phase 2](phase-02-offline-game-engine-and-stat-decay.md) — game engine (dependency)
- [state_machine.py](../../aimon-frontend/state/state_machine.py) — main state machine
- [ws_client.py](../../aimon-frontend/network/ws_client.py) — WebSocket client
- [Research](research/researcher-01-frontend-state-network.md) — line-level insertion points

## Overview
**Priority:** HIGH — wires everything together
Fix OFFLINE state entry, integrate game engine, add sync-on-reconnect, infinite reconnect with backoff.

## Key Insights
- `_on_disconnect()` currently sets `State.IDLE` not `State.OFFLINE` — LED never fires
- `_on_single_press_confirmed()` already gated on `self._offline` — replace `_play_offline_clip()` with game engine interaction
- `_on_hello_ack()` is the right sync trigger point — after auth confirmed
- ws_client reconnect limited to 5 attempts — need infinite with exponential backoff (capped at 60s)
- Button: single-press → interaction (text response), double-press → feed

## Requirements
**Functional:**
- Enter `State.OFFLINE` on disconnect → amber LED
- Start OfflineGameEngine on disconnect, stop on reconnect
- Single-press offline → `engine.on_interaction()` → display text bubble
- Double-press offline → `engine.on_feed()` → eat animation
- On reconnect: send `offline_sync` WS message with all pending events + state snapshot
- Process `sync_result` from backend → overwrite local state, clear journal

**Non-functional:**
- Infinite reconnect with exponential backoff (2s, 4s, 8s, ... cap 60s)
- Sync payload < 100KB (1000 events max)

## Architecture
```
state_machine.py
  ├── _on_disconnect() → State.OFFLINE → start OfflineGameEngine
  ├── _on_single_press_confirmed() [offline] → engine.on_interaction()
  ├── _on_double_press_confirmed() [offline] → engine.on_feed()
  └── _on_hello_ack() → engine.stop() → ws.send_offline_sync() → apply sync_result

ws_client.py
  ├── reconnect() → infinite backoff loop
  ├── send_offline_sync(events, state) → JSON message
  └── on_reconnect_failed removed (never gives up now)
```

## Related Code Files

### MODIFY
| File | Change |
|------|--------|
| `aimon-frontend/state/state_machine.py` | OFFLINE state entry, game engine lifecycle, sync on reconnect, button handlers |
| `aimon-frontend/network/ws_client.py` | Infinite reconnect w/ backoff, send_offline_sync() |
| `aimon-frontend/config.py` | LED_OFFLINE_AMBER=(255, 140, 0), WS_RECONNECT_BACKOFF_CAP_S=60 |

## Implementation Steps

1. **Modify config.py — add LED + reconnect constants**
   - `LED_OFFLINE_AMBER = (255, 140, 0)` — replace existing LED_OFFLINE red
   - `WS_RECONNECT_BACKOFF_CAP_S = 60`
   - `WS_RECONNECT_INITIAL_S = 2`

2. **Modify ws_client.py — infinite reconnect + sync method**
   - Replace `reconnect()` loop: remove max attempts, use exponential backoff
     ```python
     delay = WS_RECONNECT_INITIAL_S
     while not self._stop_event.is_set():
         if self.connect(): return True
         time.sleep(delay)
         delay = min(delay * 2, WS_RECONNECT_BACKOFF_CAP_S)
     ```
   - Add `_stop_event = threading.Event()` for clean shutdown
   - Add `stop_reconnect()` — sets stop event (for app shutdown)
   - Add `send_offline_sync(events: list[dict], state_snapshot: dict)`:
     ```python
     self._send_json({"type": "offline_sync", "events": events, "state": state_snapshot})
     ```

3. **Modify state_machine.py — OFFLINE state + game engine**
   - Import `OfflineGameEngine`, `OfflineEventJournal`, `OfflineResponseBank`
   - In `__init__`: create journal + response_bank instances (lightweight, always available)
   - `_offline_engine: OfflineGameEngine = None`

   **Fix _on_disconnect() (~line 402):**
   - Change `_set_state(State.IDLE)` → `_set_state(State.OFFLINE)`
   - Create + start OfflineGameEngine:
     ```python
     self._offline_engine = OfflineGameEngine(
         pet_state_updater=self._apply_offline_stat_update,
         display_callback=self._show_offline_text,
         journal=self._offline_journal,
         response_bank=self._offline_response_bank
     )
     self._offline_engine.start(time.time())
     ```

   **Add _apply_offline_stat_update(hunger_d, energy_d, happiness_d):**
   - Acquire `_pet_lock`, apply deltas to `_pet_state`, clamp, call `display.on_stats_changed()`

   **Add _show_offline_text(text: str):**
   - Call speech bubble renderer to show Vietnamese text bubble (no audio)

   **Modify _on_single_press_confirmed() (~line 228):**
   - Replace `_play_offline_clip()` with `self._offline_engine.on_interaction()`

   **Add _on_double_press_confirmed() offline branch:**
   - If offline: `self._offline_engine.on_feed()`
   - Trigger eat animation via display engine

   **Modify _on_hello_ack() (~line 319):**
   - Before setting `_offline = False`:
     ```python
     if self._offline_engine:
         snapshot = self._offline_engine.stop()
         events = self._offline_journal.get_all_pending_for_sync()
         self._ws.send_offline_sync(
             events=[e for e in events],
             state_snapshot=snapshot
         )
         # Wait for sync_result before clearing
     ```

   **Add _on_sync_result(message) handler:**
   - Parse authoritative state from backend
   - Overwrite local `_pet_state` with backend values under `_pet_lock`
   - Call `self._offline_journal.clear_synced()`
   - Call `display.on_stats_changed()`

4. **Update _LED_MAP in state_machine.py**
   - `State.OFFLINE: config.LED_OFFLINE_AMBER`

5. **Register sync_result handler in WS message dispatch**
   - In existing message handler switch/if chain, add `"sync_result"` → `_on_sync_result()`

## Todo List
- [x] Add LED_OFFLINE_AMBER + reconnect constants to config.py
- [x] Implement infinite reconnect w/ backoff in ws_client.py
- [x] Add send_offline_sync() to ws_client.py
- [x] Fix _on_disconnect to enter State.OFFLINE + start engine
- [x] Wire single-press → on_interaction, double-press → on_feed
- [x] Implement sync flush in _on_hello_ack
- [x] Add _on_sync_result handler
- [x] Update LED_MAP for OFFLINE state
- [x] Integration test: disconnect → interact → feed → reconnect → sync

## Success Criteria
- LED turns amber on disconnect, returns to normal on reconnect
- Single-press shows Vietnamese text bubble offline
- Double-press triggers feed with cooldown enforcement
- Reconnect sends all offline events to backend
- Backend sync_result overwrites local state correctly

## Risk Assessment
- **Sync race condition**: hello_ack arrives before engine fully stops. Mitigation: engine.stop() is synchronous (joins decay thread)
- **Large sync payload**: 1000 events could be ~50KB. Well within WS frame limits.
- **Double-press detection**: existing _press_lock handles this — no new concurrency risk

## Security Considerations
- sync payload contains only stat data + timestamps — no PII
- Backend validates all values server-side (last-write-wins)

## Next Steps
Phase 4 (backend) can run in parallel. After both complete, end-to-end test full offline→reconnect→sync flow.
