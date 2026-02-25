package dev.aimon.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.aimon.dto.pet.PetMessageTypes;
import dev.aimon.dto.pet.PetStatusDto;
import dev.aimon.entity.pet.PetProfile;
import dev.aimon.dto.combat.CombatOutcome;
import dev.aimon.dto.combat.CombatRoundDto;
import dev.aimon.model.*;
import dev.aimon.dto.pet.QuestDto;
import dev.aimon.service.pet.PetLevelConfig;
import dev.aimon.service.pet.PetProfileService;
import dev.aimon.service.pet.QuestService;
import io.quarkus.websockets.next.WebSocketConnection;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

/**
 * Bridge between CDI pet events and WebSocket connections.
 * Observes pet system events and pushes notifications to connected clients.
 */
@ApplicationScoped
public class PetEventBridge {

    private static final Logger LOG = Logger.getLogger(PetEventBridge.class);

    @Inject
    SessionConnectionRegistry registry;

    @Inject
    PetProfileService petService;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    QuestService questService;

    /**
     * Handle badge earned event.
     * Push badge notification to user's WebSocket connection.
     */
    void onBadgeEarned(@Observes BadgeEarnedEvent event) {
        LOG.infof("Badge earned event for user %d: %s", event.userId(), event.badgeCode());

        ObjectNode message = objectMapper.createObjectNode();
        message.put("type", PetMessageTypes.BADGE_EARNED);
        message.put("badge_code", event.badgeCode());
        message.put("badge_name", event.badgeName());
        message.put("xp_reward", event.xpReward());

        sendToUser(event.userId(), message);
    }

    /**
     * Handle pet evolution event.
     * Push evolution notification + updated pet status.
     */
    void onEvolution(@Observes PetEvolutionEvent event) {
        LOG.infof("Pet evolution event for user %d: %s -> %s",
            event.userId(), event.oldStage(), event.newStage());

        // Send evolution event
        ObjectNode evolutionMsg = objectMapper.createObjectNode();
        evolutionMsg.put("type", PetMessageTypes.PET_EVOLUTION);
        evolutionMsg.put("old_stage", event.oldStage());
        evolutionMsg.put("new_stage", event.newStage());

        sendToUser(event.userId(), evolutionMsg);

        // Send updated pet status
        sendPetStatusUpdate(event.userId());
    }

    /**
     * Handle pet transform event.
     * Push transformation notification with duration.
     */
    void onTransform(@Observes PetTransformEvent event) {
        LOG.infof("Pet transform event for user %d: variant=%s, duration=%d min",
            event.userId(), event.variantCode(), event.durationMinutes());

        ObjectNode message = objectMapper.createObjectNode();
        message.put("type", PetMessageTypes.PET_TRANSFORM);
        message.put("variant_code", event.variantCode());
        message.put("duration_minutes", event.durationMinutes());

        sendToUser(event.userId(), message);

        // Send updated pet status
        sendPetStatusUpdate(event.userId());
    }

    /**
     * Handle pet regression event.
     * Push regression warning + updated pet status (EGG state).
     */
    void onRegression(@Observes PetRegressionEvent event) {
        LOG.warnf("Pet regression event for user %d", event.userId());

        ObjectNode message = objectMapper.createObjectNode();
        message.put("type", PetMessageTypes.PET_REGRESSION);
        message.put("message", "Your pet has regressed to EGG due to neglect. Take better care!");

        sendToUser(event.userId(), message);

        // Send updated pet status
        sendPetStatusUpdate(event.userId());
    }

    /**
     * Handle quest completion — push updated pet_status (quest cleared).
     */
    void onQuestComplete(@Observes PetActionEvent event) {
        if ("quest_complete".equals(event.actionType())) {
            LOG.infof("Quest completed for user %d, pushing pet_status update", event.userId());
            sendPetStatusUpdate(event.userId());
        }
    }

    // --- Combat system events ---

    /**
     * Handle tasteless encounter — send warning with flavor text for TTS.
     */
    void onTastelessEncounter(@Observes TastelessEncounterEvent event) {
        LOG.infof("Tasteless encounter for user %d: %s (boss=%s)",
            event.userId(), event.tasteless().getName(), event.tasteless().getIsBoss());

        ObjectNode message = objectMapper.createObjectNode();
        message.put("type", PetMessageTypes.TASTELESS_WARNING);
        message.put("tasteless_name", event.tasteless().getName());
        message.put("flavor_text", event.tasteless().getLoreFlavorText());
        message.put("is_boss", event.tasteless().getIsBoss());
        message.put("level", event.tasteless().getLevel());

        sendToUser(event.userId(), message);
    }

    /**
     * Handle combat round — stream round result to frontend.
     */
    void onCombatRound(@Observes CombatRoundEvent event) {
        CombatRoundDto round = event.round();
        ObjectNode message = objectMapper.createObjectNode();
        message.put("type", PetMessageTypes.COMBAT_ROUND);
        message.put("round", round.roundNumber());
        message.put("aimon_damage", round.aimonDamage());
        message.put("tasteless_damage", round.tastelessDamage());
        message.put("aimon_hp", round.aimonHpRemaining());
        message.put("tasteless_hp", round.tastelessHpRemaining());
        message.put("used_special", round.usedSpecialThisRound());
        message.put("description", round.description());

        sendToUser(event.userId(), message);
    }

    /**
     * Handle combat won — send result + updated pet status.
     */
    void onCombatWon(@Observes CombatWonEvent event) {
        sendCombatResult(event.userId(), event.outcome());
        sendPetStatusUpdate(event.userId());
    }

    /**
     * Handle combat lost — send result + updated pet status.
     */
    void onCombatLost(@Observes CombatLostEvent event) {
        sendCombatResult(event.userId(), event.outcome());
        sendPetStatusUpdate(event.userId());
    }

    private void sendCombatResult(Long userId, CombatOutcome outcome) {
        ObjectNode message = objectMapper.createObjectNode();
        message.put("type", PetMessageTypes.COMBAT_RESULT);
        message.put("won", outcome.won());
        message.put("is_boss_win", outcome.isBossWin());
        message.put("tasteless_name", outcome.tastelessName());
        message.put("xp_gained", outcome.xpGained());
        message.put("used_special", outcome.usedSpecial());
        message.put("used_evolution", outcome.usedEvolution());
        message.put("total_rounds", outcome.rounds().size());

        sendToUser(userId, message);
    }

    // --- Shard + Location events ---

    /**
     * Handle shard unlocked — notify frontend with shard details.
     */
    void onShardUnlocked(@Observes ShardUnlockedEvent event) {
        LOG.infof("Shard unlocked for user %d: %s (%s)", event.userId(), event.shardTitle(), event.shardType());

        ObjectNode message = objectMapper.createObjectNode();
        message.put("type", PetMessageTypes.SHARD_UNLOCKED);
        message.put("shard_title", event.shardTitle());
        message.put("shard_type", event.shardType());
        message.put("source", event.source());

        sendToUser(event.userId(), message);
    }

    /**
     * Handle location unlock — notify frontend a new region is available.
     */
    void onLocationUnlock(@Observes LocationUnlockEvent event) {
        LOG.infof("Location unlocked for user %d: %s", event.userId(), event.locationCode());

        ObjectNode message = objectMapper.createObjectNode();
        message.put("type", PetMessageTypes.LOCATION_UNLOCK);
        message.put("location_code", event.locationCode());
        message.put("display_name", event.displayName());

        sendToUser(event.userId(), message);
    }

    /**
     * Handle sub-location travel — notify frontend to swap background.
     */
    void onLocationChanged(@Observes LocationChangedEvent event) {
        LOG.infof("Location changed for user %d: %s", event.userId(), event.subLocationCode());

        ObjectNode message = objectMapper.createObjectNode();
        message.put("type", PetMessageTypes.LOCATION_CHANGED);
        message.put("location_code", event.subLocationCode());
        message.put("background", event.backgroundFile());

        sendToUser(event.userId(), message);
    }

    /**
     * Send updated pet status to user's connection.
     */
    private void sendPetStatusUpdate(Long userId) {
        registry.getConnection(userId).ifPresent(conn -> {
            try {
                PetProfile profile = petService.getProfile(userId);
                if (profile == null) {
                    LOG.warnf("No pet profile found for user %d", userId);
                    return;
                }

                PetStatusDto status = buildStatusDto(profile);
                ObjectNode statusMsg = buildPetStatusMessage(status);

                sendToConnection(conn, statusMsg);
            } catch (Exception e) {
                LOG.errorf(e, "Error sending pet status update to user %d", userId);
            }
        });
    }

    /**
     * Send JSON message to specific user's connection.
     */
    private void sendToUser(Long userId, ObjectNode message) {
        registry.getConnection(userId).ifPresent(conn -> sendToConnection(conn, message));
    }

    /**
     * Send JSON to WebSocket connection (non-blocking).
     */
    private void sendToConnection(WebSocketConnection connection, ObjectNode message) {
        try {
            String json = objectMapper.writeValueAsString(message);
            connection.sendText(json)
                .subscribe().with(
                    v -> {},
                    err -> LOG.warnf("Error sending WebSocket message: %s", err.getMessage())
                );
        } catch (JsonProcessingException e) {
            LOG.errorf(e, "Error serializing message");
        }
    }

    /**
     * Build PetStatusDto from profile entity.
     */
    private PetStatusDto buildStatusDto(PetProfile profile) {
        PetMood mood = petService.deriveMood(profile);
        long xpForNext = PetLevelConfig.getRequiredXp(profile.getLevel() + 1);

        String stage = profile.getStage() != null ? profile.getStage().name() : "EGG";
        String variant = profile.getVariant() != null ? profile.getVariant().getCode() : null;

        // Include pending quest so status pushes don't wipe quest display
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

        // Include quest data so frontend can show/clear quest bubble
        if (status.pendingQuestText() != null) {
            ObjectNode quest = objectMapper.createObjectNode();
            quest.put("text", status.pendingQuestText());
            quest.put("category", status.pendingQuestCategory());
            quest.put("difficulty", status.pendingQuestDifficulty());
            msg.set("quest", quest);
        }
        // quest key absent = no pending quest -> frontend clears bubble

        return msg;
    }
}
