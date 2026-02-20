package dev.aimon.service.pet;

import dev.aimon.dto.pet.QuestDto;
import dev.aimon.model.PetActionEvent;
import dev.aimon.service.conversation.ConversationSessionManager;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Evaluates LLM responses for quest result markers.
 * Applies XP/happiness rewards and fires badge events.
 */
@ApplicationScoped
public class QuestEvaluationService {

    private static final Logger LOG = Logger.getLogger(QuestEvaluationService.class);
    private static final Pattern QUEST_MARKER = Pattern.compile("\\[QUEST_RESULT:(correct|incorrect)\\]");

    @Inject
    QuestService questService;

    @Inject
    PetProfileService petProfileService;

    @Inject
    Event<PetActionEvent> actionEvent;

    @Inject
    ConversationSessionManager sessionManager;

    /**
     * Parse quest result marker from LLM response.
     * @return "correct", "incorrect", or null if no marker
     */
    public String parseQuestResult(String fullResponse) {
        if (fullResponse == null) return null;
        Matcher m = QUEST_MARKER.matcher(fullResponse);
        return m.find() ? m.group(1) : null;
    }

    /**
     * Strip quest marker from text (for TTS).
     */
    public String stripQuestMarker(String text) {
        if (text == null) return "";
        return QUEST_MARKER.matcher(text).replaceAll("").trim();
    }

    private static final int MAX_ATTEMPTS = 3;

    /**
     * Process quest result: apply rewards if correct, increment attempt if incorrect.
     * Auto-completes as incorrect after MAX_ATTEMPTS to avoid nagging.
     */
    public void processResult(Long userId, String result, QuestDto quest) {
        if (result == null || quest == null) return;

        if ("correct".equals(result)) {
            applyRewards(userId, quest.difficulty());
            questService.completeQuest(userId, true);
            sessionManager.invalidateUserCache(userId);
            LOG.infof("User %d answered quest %s correctly", userId, quest.code());
        } else {
            questService.incrementAttempt(userId);
            // Auto-complete after max attempts to stop nagging
            int attempts = questService.getAttemptCount(userId);
            if (attempts >= MAX_ATTEMPTS) {
                questService.completeQuest(userId, false);
                sessionManager.invalidateUserCache(userId);
                // Fire event so frontend clears quest bubble via pet_status push
                actionEvent.fire(new PetActionEvent(userId, "quest_complete", 1));
                LOG.infof("User %d quest %s auto-completed after %d attempts", userId, quest.code(), attempts);
            } else {
                LOG.infof("User %d answered quest %s incorrectly (attempt %d/%d)", userId, quest.code(), attempts, MAX_ATTEMPTS);
            }
        }
    }

    /**
     * Apply XP + happiness rewards based on difficulty.
     * easy=25XP/+5 happiness, medium=50XP/+10, hard=75XP/+15
     */
    private void applyRewards(Long userId, String difficulty) {
        int xp;
        int happiness;
        switch (difficulty) {
            case "hard" -> { xp = 75; happiness = 15; }
            case "medium" -> { xp = 50; happiness = 10; }
            default -> { xp = 25; happiness = 5; }
        }

        petProfileService.addXp(userId, xp);
        petProfileService.addHappiness(userId, happiness);

        // Fire quest_complete action for badge tracking
        actionEvent.fire(new PetActionEvent(userId, "quest_complete", 1));
        LOG.infof("Applied quest rewards to user %d: +%dXP, +%d happiness", userId, xp, happiness);
    }
}
