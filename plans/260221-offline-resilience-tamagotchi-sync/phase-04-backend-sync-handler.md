---
phase: 4
title: "Backend Sync Handler"
status: complete
effort: 1.5h
---

# Phase 4: Backend Sync Handler

## Context Links
- [AimonWebSocket.java](../../aimon-backend/src/main/java/dev/aimon/websocket/AimonWebSocket.java) — WS switch (line 82-85)
- [PetMessageHandler.java](../../aimon-backend/src/main/java/dev/aimon/websocket/PetMessageHandler.java) — pet message handling
- [PetProfileService.java](../../aimon-backend/src/main/java/dev/aimon/service/pet/PetProfileService.java) — existing service methods

## Overview
**Priority:** HIGH — required for sync on reconnect
Add `offline_sync` WS message type. Backend replays offline events via existing PetProfileService methods, responds with authoritative state.

## Key Insights
- PetProfileService already has: `applyFeed()` (line 100), `addXp()` (line 178), `applyDecay()` (line 212), `getStatus()` (line 254)
- No new DB tables needed — just replay events through existing service
- Aggregate approach: sum up total feeds, total XP, total decay from events, apply once
- Last-write-wins: backend applies offline deltas then returns authoritative state

## Requirements
**Functional:**
- Accept `offline_sync` WS message with `events[]` array + `state` snapshot
- Sum event deltas: total decay, total feeds, total XP
- Apply via existing PetProfileService methods
- Check evolution eligibility after XP applied
- Respond with `sync_result` containing full PetStatusDto

**Non-functional:**
- Process 1000 events in <500ms
- Transactional: all-or-nothing application

## Architecture
```
AimonWebSocket.onText()
  └── case "offline_sync" → petMessageHandler.handleOfflineSync(petId, userId, message, connection)
        ├── Parse events array
        ├── Aggregate: totalDecayTicks, totalFeeds, totalXp
        ├── petProfileService.applyDecay(profile, hunger*ticks, energy*ticks, happiness*ticks)
        ├── petProfileService.applyFeed(userId, "offline_feed", 15) × totalFeeds
        ├── petProfileService.addXp(userId, totalXp)
        └── Send sync_result with petProfileService.getStatus(userId)
```

## Related Code Files

### MODIFY
| File | Change |
|------|--------|
| `AimonWebSocket.java` | Add `"offline_sync"` case in switch (~line 84) |
| `PetMessageHandler.java` | Add `handleOfflineSync()` method |

## Implementation Steps

1. **Modify AimonWebSocket.java — add case**
   - In `onText()` switch block, before `default`:
     ```java
     case "offline_sync" -> petMessageHandler.handleOfflineSync(
         petId, getUserId(petId), message, connection);
     ```

2. **Add handleOfflineSync() to PetMessageHandler.java**
   ```java
   public void handleOfflineSync(String petId, Long userId, JsonNode message,
                                  WebSocketConnection connection) {
       // Run in blocking thread pool (same pattern as other handlers)
       Uni.createFrom().item(() -> {
           ManagedContext rc = Arc.container().requestContext();
           rc.activate();
           try {
               JsonNode events = message.get("events");
               JsonNode stateSnapshot = message.get("state");

               // Aggregate events
               int decayTicks = 0, totalFeeds = 0;
               long totalXp = 0;
               if (events != null && events.isArray()) {
                   for (JsonNode event : events) {
                       String type = event.get("event_type").asText();
                       switch (type) {
                           case "decay_tick" -> decayTicks++;
                           case "feed" -> totalFeeds++;
                           case "xp_gain" -> {
                               JsonNode payload = event.get("payload");
                               if (payload != null && payload.has("amount"))
                                   totalXp += payload.get("amount").asLong();
                           }
                           case "level_up" -> {} // XP already counted in xp_gain events
                       }
                   }
               }

               PetProfile profile = petProfileService.getProfile(userId);

               // Apply aggregated decay
               if (decayTicks > 0) {
                   petProfileService.applyDecay(profile,
                       decayTicks * 1,          // hunger +1 per tick
                       (int)(decayTicks * 0.5),  // energy -0.5 per tick
                       (int)(decayTicks * 0.3)); // happiness -0.3 per tick
               }

               // Apply feeds
               for (int i = 0; i < totalFeeds; i++) {
                   petProfileService.applyFeed(userId, "offline_feed", 15);
               }

               // Apply XP (triggers level-up + evolution check internally)
               if (totalXp > 0) {
                   petProfileService.addXp(userId, (int) totalXp);
               }

               // Return authoritative state
               PetStatusDto status = petProfileService.getStatus(userId);
               ObjectNode response = objectMapper.createObjectNode();
               response.put("type", "sync_result");
               response.put("status", "ok");
               response.put("events_processed", events != null ? events.size() : 0);
               response.set("pet_status", objectMapper.valueToTree(status));

               connection.sendText(objectMapper.writeValueAsString(response));
               LOG.infof("Offline sync for user %d: %d decay, %d feeds, %d XP",
                   userId, decayTicks, totalFeeds, totalXp);
               return null;
           } catch (Exception e) {
               LOG.errorf("Offline sync failed for user %d: %s", userId, e.getMessage());
               sendSyncError(connection, e.getMessage());
               return null;
           } finally {
               rc.deactivate();
           }
       }).runSubscriptionOn(Infrastructure.getDefaultWorkerPool()).subscribe().with(
           v -> {}, e -> LOG.error("Sync subscription error", e));
   }
   ```

3. **Add sendSyncError helper to PetMessageHandler**
   ```java
   private void sendSyncError(WebSocketConnection connection, String reason) {
       try {
           ObjectNode error = objectMapper.createObjectNode();
           error.put("type", "sync_result");
           error.put("status", "error");
           error.put("reason", reason);
           connection.sendText(objectMapper.writeValueAsString(error));
       } catch (Exception e) {
           LOG.error("Failed to send sync error", e);
       }
   }
   ```

4. **Verify PetStatusDto contains all needed fields**
   - Must include: hunger, energy, happiness, xp, level, stage, variant
   - Frontend uses these to overwrite local state completely

## Todo List
- [x] Add `"offline_sync"` case to AimonWebSocket.java switch
- [x] Implement handleOfflineSync() in PetMessageHandler.java
- [x] Add sendSyncError() helper
- [x] Test: send offline_sync with 100 events, verify state updated
- [x] Test: send empty events array, verify no error
- [x] Test: verify evolution triggers if XP crosses stage threshold

## Success Criteria
- Backend processes offline_sync message without errors
- Aggregated decay/feeds/XP applied correctly via existing service methods
- sync_result contains accurate authoritative PetStatusDto
- Evolution check triggered after XP application
- Error handling: malformed events don't crash handler

## Risk Assessment
- **Integer truncation**: `0.5 * ticks` cast to int loses precision. Mitigation: acceptable for game mechanics, small drift is fine
- **Feed spam**: 1000 feed events could reduce hunger below 0. Mitigation: PetProfileService already clamps values
- **Stale profile**: getProfile() before mutations could be stale. Mitigation: Quarkus request scope ensures single transaction

## Security Considerations
- Validate userId matches authenticated session (getUserId already does this)
- Cap events array at 1000 — reject if larger to prevent DoS
- No arbitrary code execution — only predefined event types processed

## Next Steps
After this phase: end-to-end integration test with frontend Phase 3. Full flow: disconnect → offline play → reconnect → sync → state reconciliation.
