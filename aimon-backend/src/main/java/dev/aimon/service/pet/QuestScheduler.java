package dev.aimon.service.pet;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.aimon.dto.pet.PetMessageTypes;
import dev.aimon.dto.pet.QuestDto;
import dev.aimon.entity.pet.PetProfile;
import dev.aimon.websocket.SessionConnectionRegistry;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.websockets.next.WebSocketConnection;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

/**
 * Periodic quest assignment scheduler.
 * Checks active sessions every hour, assigns quest if none pending.
 */
@ApplicationScoped
public class QuestScheduler {

    private static final Logger LOG = Logger.getLogger(QuestScheduler.class);

    @Inject
    QuestService questService;

    @Inject
    PetProfileService petProfileService;

    @Inject
    SessionConnectionRegistry connectionRegistry;

    @Inject
    ObjectMapper objectMapper;

    /**
     * Every hour, assign quests to active sessions without pending quests.
     */
    @Scheduled(every = "15m", identity = "quest-scheduler")
    void triggerQuests() {
        var activeUserIds = connectionRegistry.getActiveUserIds();
        if (activeUserIds.isEmpty()) {
            LOG.debug("No active sessions, skipping quest assignment");
            return;
        }

        LOG.infof("Quest scheduler: checking %d active sessions", activeUserIds.size());
        for (Long userId : activeUserIds) {
            try {
                // Skip if already has pending quest
                if (questService.getPendingQuest(userId) != null) {
                    continue;
                }

                PetProfile profile = petProfileService.getOrCreateProfile(userId);
                // EGG stage cannot receive quests
                if ("EGG".equals(profile.getStage().name())) continue;
                QuestDto quest = questService.assignQuest(userId, profile.getLevel());
                if (quest == null) {
                    continue;
                }

                // Push quest_start to connected client
                connectionRegistry.getConnection(userId).ifPresent(conn ->
                    sendQuestStart(conn, quest));

                LOG.infof("Scheduled quest %s for user %d", quest.code(), userId);
            } catch (Exception e) {
                LOG.errorf(e, "Error assigning scheduled quest to user %d", userId);
            }
        }
    }

    /**
     * Send quest_start WebSocket message.
     */
    void sendQuestStart(WebSocketConnection connection, QuestDto quest) {
        try {
            ObjectNode msg = objectMapper.createObjectNode();
            msg.put("type", PetMessageTypes.QUEST_START);
            msg.put("quest_text", "Đố bạn: " + quest.questionText());
            msg.put("category", quest.category());
            msg.put("difficulty", quest.difficulty());
            msg.put("hint", quest.hint());

            connection.sendText(objectMapper.writeValueAsString(msg))
                .subscribe().asCompletionStage();
        } catch (Exception e) {
            LOG.errorf(e, "Error sending quest_start message");
        }
    }
}
