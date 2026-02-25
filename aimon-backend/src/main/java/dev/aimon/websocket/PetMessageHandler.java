package dev.aimon.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.aimon.dto.pet.PetMessageTypes;
import dev.aimon.dto.pet.PetStatusDto;
import dev.aimon.entity.pet.PetProfile;
import dev.aimon.model.PetMood;
import dev.aimon.dto.pet.QuestDto;
import dev.aimon.service.combat.CombatResultHandler;
import dev.aimon.service.combat.CombatService;
import dev.aimon.service.combat.CombatSessionState;
import dev.aimon.service.world.LocationService;
import dev.aimon.service.conversation.ConversationSessionManager;
import dev.aimon.service.pet.BadgeService;
import dev.aimon.service.pet.PetEvolutionService;
import dev.aimon.service.pet.PetLevelConfig;
import dev.aimon.service.pet.PetProfileService;
import dev.aimon.service.pet.QuestService;
import io.quarkus.websockets.next.WebSocketConnection;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ManagedContext;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

/**
 * Handler for pet-related WebSocket messages.
 * Manages pet status delivery, feeding, and quest requests.
 * Vision analysis moved to Pi-side VisionAnalysisService (Phase 8).
 */
@ApplicationScoped
public class PetMessageHandler {

    private static final Logger LOG = Logger.getLogger(PetMessageHandler.class);

    @Inject
    PetProfileService petService;

    @Inject
    PetEvolutionService evolutionService;

    @Inject
    BadgeService badgeService;

    @Inject
    QuestService questService;

    @Inject
    ConversationSessionManager sessionManager;

    @Inject
    CombatSessionState combatState;

    @Inject
    CombatService combatService;

    @Inject
    CombatResultHandler combatResultHandler;

    @Inject
    LocationService locationService;

    @Inject
    ObjectMapper objectMapper;

    /**
     * Send full pet status to a WebSocket connection.
     * Called on connection hello and after stat changes.
     */
    public Uni<Void> sendPetStatus(Long userId, WebSocketConnection connection) {
        return Uni.createFrom().item(() -> {
            ManagedContext requestContext = Arc.container().requestContext();
            boolean activated = !requestContext.isActive();
            if (activated) {
                requestContext.activate();
            }
            try {
                PetProfile profile = petService.getOrCreateProfile(userId);
                LOG.infof("sendPetStatus: user=%d stage=%s level=%d", userId,
                    profile.getStage() != null ? profile.getStage().name() : "null", profile.getLevel());
                return buildStatusDto(profile);
            } catch (Exception e) {
                LOG.errorf(e, "Error sending pet status for user %d", userId);
                return null;
            } finally {
                if (activated) {
                    requestContext.terminate();
                }
            }
        })
        .runSubscriptionOn(Infrastructure.getDefaultWorkerPool())
        .onItem().transformToUni(status -> {
            if (status == null) {
                return Uni.createFrom().voidItem();
            }
            return sendJson(connection, buildPetStatusMessage(status));
        });
    }

    /**
     * Handle feed confirmation from client.
     * Pi detects food via Gemini, sends pet_feed_confirm with food_name.
     */
    public Uni<Void> handleFeedConfirm(String robotId, Long userId, String foodName, String spriteKey, WebSocketConnection connection) {
        int hungerReduction = 25; // Base reduction for camera-fed food

        return Uni.createFrom().item(() -> {
            ManagedContext requestContext = Arc.container().requestContext();
            boolean activated = !requestContext.isActive();
            if (activated) { requestContext.activate(); }
            try {
                petService.applyFeed(userId, foodName, hungerReduction);
                petService.addXp(userId, 10);
                sessionManager.invalidateUserCache(userId);
                return true;
            } catch (Exception e) {
                LOG.errorf(e, "Error applying feed for robot %s", robotId);
                return false;
            } finally {
                if (activated) { requestContext.terminate(); }
            }
        })
        .runSubscriptionOn(Infrastructure.getDefaultWorkerPool())
        .onItem().transformToUni(success -> {
            if (!success) {
                return sendError(connection, "FEED_ERROR", "Failed to apply feed");
            }
            ObjectNode result = objectMapper.createObjectNode();
            result.put("type", PetMessageTypes.PET_FEED_RESULT);
            result.put("success", true);
            result.put("food_name", foodName);
            result.put("sprite_key", spriteKey);
            result.put("hunger_reduction", hungerReduction);

            return sendJson(connection, result)
                .chain(() -> sendPetStatus(userId, connection));
        });
    }

    /**
     * Handle quest generation request.
     * Assigns a quest from question bank based on pet level.
     */
    public Uni<Void> handleQuestRequest(String robotId, Long userId, WebSocketConnection connection) {
        return Uni.createFrom().item(() -> {
            ManagedContext requestContext = Arc.container().requestContext();
            boolean activated = !requestContext.isActive();
            if (activated) { requestContext.activate(); }
            try {
                PetProfile profile = petService.getOrCreateProfile(userId);
                // EGG stage cannot receive quests
                if ("EGG".equals(profile.getStage().name())) return null;
                return questService.assignQuest(userId, profile.getLevel());
            } catch (Exception e) {
                LOG.errorf(e, "Error assigning quest for robot %s", robotId);
                return null;
            } finally {
                if (activated) { requestContext.terminate(); }
            }
        })
        .runSubscriptionOn(Infrastructure.getDefaultWorkerPool())
        .onItem().transformToUni(quest -> {
            if (quest == null) {
                return sendError(connection, "QUEST_UNAVAILABLE", "No quest available");
            }
            ObjectNode response = objectMapper.createObjectNode();
            response.put("type", PetMessageTypes.QUEST_START);
            response.put("quest_text", "Đố bạn: " + quest.questionText());
            response.put("category", quest.category());
            response.put("difficulty", quest.difficulty());
            response.put("hint", quest.hint());
            return sendJson(connection, response);
        });
    }

    /**
     * Handle variant transformation request.
     */
    public Uni<Void> handleTransformRequest(String robotId, Long userId, JsonNode message, WebSocketConnection connection) {
        if (!message.has("variant_code")) {
            return sendError(connection, "MISSING_VARIANT_CODE", "variant_code required");
        }

        String variantCode = message.get("variant_code").asText();

        return Uni.createFrom().item(() -> {
            ManagedContext requestContext = Arc.container().requestContext();
            boolean activated = !requestContext.isActive();
            if (activated) { requestContext.activate(); }
            try {
                boolean success = evolutionService.transformToVariant(userId, variantCode);
                if (success) { sessionManager.invalidateUserCache(userId); }
                return success;
            } catch (Exception e) {
                LOG.errorf(e, "Error transforming pet for robot %s", robotId);
                return false;
            } finally {
                if (activated) { requestContext.terminate(); }
            }
        })
        .runSubscriptionOn(Infrastructure.getDefaultWorkerPool())
        .onItem().transformToUni(success -> {
            if (!success) {
                return sendError(connection, "TRANSFORM_FAILED", "Variant transformation failed (check ownership and level)");
            }
            return sendPetStatus(userId, connection);
        });
    }

    /**
     * Handle offline_sync message: replay aggregated offline events through existing
     * PetProfileService methods, respond with authoritative state.
     */
    public Uni<Void> handleOfflineSync(String robotId, Long userId, JsonNode message, WebSocketConnection connection) {
        if (userId == null) {
            return sendError(connection, "NO_USER", "User ID not found for offline sync");
        }

        return Uni.createFrom().item(() -> {
            ManagedContext rc = Arc.container().requestContext();
            boolean activated = !rc.isActive();
            if (activated) rc.activate();
            try {
                JsonNode events = message.get("events");

                // Aggregate events
                int decayTicks = 0, totalFeeds = 0;
                long totalXp = 0;
                int eventCount = 0;
                if (events != null && events.isArray()) {
                    // Cap at 1000 to prevent DoS
                    eventCount = Math.min(events.size(), 1000);
                    for (int i = 0; i < eventCount; i++) {
                        JsonNode event = events.get(i);
                        String type = event.has("event_type") ? event.get("event_type").asText() : "";
                        switch (type) {
                            case "decay_tick" -> decayTicks++;
                            case "feed" -> totalFeeds++;
                            case "xp_gain" -> {
                                JsonNode payload = event.get("payload");
                                if (payload != null && payload.has("amount"))
                                    totalXp += payload.get("amount").asLong();
                            }
                            default -> {} // level_up, warning, regression — informational only
                        }
                    }
                }

                PetProfile profile = petService.getOrCreateProfile(userId);

                // Apply aggregated decay
                if (decayTicks > 0) {
                    petService.applyDecay(profile,
                        decayTicks,
                        (int) Math.round(decayTicks * 0.5),
                        (int) Math.round(decayTicks * 0.3));
                }

                // Apply feeds
                for (int i = 0; i < totalFeeds; i++) {
                    petService.applyFeed(userId, "offline_feed", 15);
                }

                // Apply XP
                if (totalXp > 0) {
                    petService.addXp(userId, (int) totalXp);
                }

                // Build response with authoritative state
                PetStatusDto status = buildStatusDto(petService.getOrCreateProfile(userId));
                ObjectNode response = objectMapper.createObjectNode();
                response.put("type", "sync_result");
                response.put("status", "ok");
                response.put("events_processed", eventCount);
                response.set("pet_status", objectMapper.valueToTree(status));

                LOG.infof("Offline sync for user %d: %d decay, %d feeds, %d XP (%d events)",
                    userId, decayTicks, totalFeeds, totalXp, eventCount);
                return objectMapper.writeValueAsString(response);
            } catch (Exception e) {
                LOG.errorf("Offline sync failed for user %d: %s", userId, e.getMessage());
                sendSyncError(connection, e.getMessage());
                return null;
            } finally {
                if (activated) rc.terminate();
            }
        })
        .runSubscriptionOn(Infrastructure.getDefaultWorkerPool())
        .onItem().transformToUni(json -> {
            if (json == null) return Uni.createFrom().voidItem();
            return connection.sendText(json);
        });
    }

    private void sendSyncError(WebSocketConnection connection, String reason) {
        try {
            ObjectNode error = objectMapper.createObjectNode();
            error.put("type", "sync_result");
            error.put("status", "error");
            error.put("reason", reason);
            connection.sendText(objectMapper.writeValueAsString(error)).subscribe().asCompletionStage();
        } catch (Exception e) {
            LOG.error("Failed to send sync error", e);
        }
    }

    /**
     * Handle combat_special from client: special_move or evolution_move.
     * Sets player input on pending combat state. Combat resolves on 5s timeout or this trigger.
     */
    public Uni<Void> handleCombatSpecial(String robotId, Long userId, JsonNode message, WebSocketConnection connection) {
        if (userId == null) {
            return sendError(connection, "NO_USER", "User ID not found");
        }

        String action = message.has("action") ? message.get("action").asText("") : "";
        CombatSessionState.PendingCombat pending = combatState.getPending(userId);
        if (pending == null) {
            return sendError(connection, "NO_COMBAT", "No active combat encounter");
        }

        switch (action) {
            case "special_move" -> combatState.setSpecialMove(userId, 1);
            case "evolution_move" -> combatState.setEvolutionMove(userId);
            default -> {
                return sendError(connection, "INVALID_ACTION", "Unknown combat action: " + action);
            }
        }

        LOG.infof("Combat special from user %d: %s", userId, action);
        return Uni.createFrom().voidItem();
    }

    /**
     * Handle location_switch from client. Validates unlock status server-side.
     */
    public Uni<Void> handleLocationSwitch(String robotId, Long userId, JsonNode message, WebSocketConnection connection) {
        if (userId == null) {
            return sendError(connection, "NO_USER", "User ID not found");
        }
        String locationCode = message.has("location") ? message.get("location").asText("") : "";

        return Uni.createFrom().item(() -> {
            io.quarkus.arc.ManagedContext rc = io.quarkus.arc.Arc.container().requestContext();
            boolean activated = !rc.isActive();
            if (activated) rc.activate();
            try {
                boolean success = locationService.switchLocation(userId, locationCode);
                if (!success) return null;
                sessionManager.invalidateUserCache(userId);
                return locationCode;
            } finally {
                if (activated) rc.terminate();
            }
        })
        .runSubscriptionOn(Infrastructure.getDefaultWorkerPool())
        .onItem().transformToUni(result -> {
            if (result == null) {
                return sendError(connection, "LOCATION_LOCKED", "Location is locked or invalid");
            }
            ObjectNode response = objectMapper.createObjectNode();
            response.put("type", dev.aimon.dto.pet.PetMessageTypes.LOCATION_CHANGED);
            response.put("location", result);
            return sendJson(connection, response)
                .chain(() -> sendPetStatus(userId, connection));
        });
    }

    /**
     * Build PetStatusDto from profile entity.
     */
    private PetStatusDto buildStatusDto(PetProfile profile) {
        PetMood mood = petService.deriveMood(profile);
        long xpForNext = PetLevelConfig.getRequiredXp(profile.getLevel() + 1);

        String stage = profile.getStage() != null ? profile.getStage().name() : "EGG";
        String variant = profile.getVariant() != null ? profile.getVariant().getCode() : null;

        // Include pending quest if exists
        QuestDto quest = questService.getPendingQuest(profile.getUserId());
        String questText = quest != null ? "Đố bạn: " + quest.questionText() : null;
        String questCategory = quest != null ? quest.category() : null;
        String questDifficulty = quest != null ? quest.difficulty() : null;

        return new PetStatusDto(
            profile.getName(),
            stage,
            variant,
            mood.name(),
            profile.getHunger(),
            profile.getEnergy(),
            profile.getHappiness(),
            profile.getLevel(),
            profile.getXp(),
            xpForNext,
            profile.getAffinity(),
            profile.getLoginStreak(),
            questText, questCategory, questDifficulty
        );
    }

    /**
     * Build pet_status WebSocket message from DTO.
     */
    private ObjectNode buildPetStatusMessage(PetStatusDto status) {
        ObjectNode msg = objectMapper.createObjectNode();
        msg.put("type", PetMessageTypes.PET_STATUS);
        msg.put("name", status.name());
        msg.put("stage", status.stage());
        msg.put("variant", status.variant());
        msg.put("mood", status.mood());
        msg.put("hunger", status.hunger());
        msg.put("energy", status.energy());
        msg.put("happiness", status.happiness());
        msg.put("level", status.level());
        msg.put("xp", status.xp());
        msg.put("xp_for_next", status.xpForNext());
        msg.put("affinity", status.affinity());
        msg.put("login_streak", status.loginStreak());
        if (status.pendingQuestText() != null) {
            ObjectNode quest = objectMapper.createObjectNode();
            quest.put("text", status.pendingQuestText());
            quest.put("category", status.pendingQuestCategory());
            quest.put("difficulty", status.pendingQuestDifficulty());
            msg.set("quest", quest);
        }
        return msg;
    }

    private Uni<Void> sendJson(WebSocketConnection connection, ObjectNode message) {
        try {
            String json = objectMapper.writeValueAsString(message);
            return connection.sendText(json);
        } catch (JsonProcessingException e) {
            LOG.errorf(e, "Error serializing JSON message");
            return Uni.createFrom().voidItem();
        }
    }

    private Uni<Void> sendError(WebSocketConnection connection, String code, String errorMessage) {
        ObjectNode msg = objectMapper.createObjectNode();
        msg.put("type", "error");
        msg.put("code", code);
        msg.put("message", errorMessage);
        return sendJson(connection, msg);
    }
}
