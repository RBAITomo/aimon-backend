package dev.aimon.service.pet;

import dev.aimon.dto.pet.QuestDto;
import dev.aimon.entity.pet.QuestHistory;
import dev.aimon.entity.pet.QuestQuestion;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Quest assignment and tracking service.
 * Picks questions from bank, manages quest_history lifecycle.
 */
@ApplicationScoped
public class QuestService {

    private static final Logger LOG = Logger.getLogger(QuestService.class);
    private static final int RECENT_HISTORY_LIMIT = 20;

    @Inject
    EntityManager em;

    /**
     * Assign a new quest to user based on pet level.
     * Returns the assigned quest DTO, or null if no questions available.
     */
    @Transactional
    public QuestDto assignQuest(Long userId, int petLevel) {
        // Check for existing pending quest first
        QuestDto pending = getPendingQuest(userId);
        if (pending != null) {
            LOG.debugf("User %d already has pending quest %s", userId, pending.code());
            return pending;
        }

        String difficulty = mapDifficulty(petLevel);
        QuestQuestion question = pickQuestion(difficulty, userId);
        if (question == null) {
            LOG.warnf("No available questions for user %d at difficulty %s", userId, difficulty);
            return null;
        }

        QuestHistory history = new QuestHistory();
        history.setUserId(userId);
        history.setQuestion(question);
        em.persist(history);

        LOG.infof("Assigned quest %s to user %d", question.getCode(), userId);
        return toDto(question);
    }

    /**
     * Get pending (unanswered) quest for user.
     */
    @Transactional
    public QuestDto getPendingQuest(Long userId) {
        try {
            QuestHistory h = em.createQuery(
                "SELECT h FROM QuestHistory h JOIN FETCH h.question " +
                "WHERE h.userId = :uid AND h.answeredAt IS NULL " +
                "ORDER BY h.assignedAt DESC", QuestHistory.class)
                .setParameter("uid", userId)
                .setMaxResults(1)
                .getSingleResult();
            return toDto(h.getQuestion());
        } catch (NoResultException e) {
            return null;
        }
    }

    /**
     * Mark pending quest as completed.
     */
    @Transactional
    public void completeQuest(Long userId, boolean isCorrect) {
        try {
            QuestHistory h = em.createQuery(
                "SELECT h FROM QuestHistory h WHERE h.userId = :uid AND h.answeredAt IS NULL " +
                "ORDER BY h.assignedAt DESC", QuestHistory.class)
                .setParameter("uid", userId)
                .setMaxResults(1)
                .getSingleResult();
            h.setAnsweredAt(LocalDateTime.now());
            h.setIsCorrect(isCorrect);
            em.merge(h);
            LOG.infof("User %d completed quest, correct=%s", userId, isCorrect);
        } catch (NoResultException e) {
            LOG.warnf("No pending quest to complete for user %d", userId);
        }
    }

    /**
     * Increment attempt count on pending quest.
     */
    @Transactional
    public void incrementAttempt(Long userId) {
        try {
            QuestHistory h = em.createQuery(
                "SELECT h FROM QuestHistory h WHERE h.userId = :uid AND h.answeredAt IS NULL " +
                "ORDER BY h.assignedAt DESC", QuestHistory.class)
                .setParameter("uid", userId)
                .setMaxResults(1)
                .getSingleResult();
            h.setAttemptCount(h.getAttemptCount() + 1);
            em.merge(h);
        } catch (NoResultException e) {
            LOG.debugf("No pending quest for attempt increment, user %d", userId);
        }
    }

    /**
     * Get current attempt count for pending quest.
     */
    @Transactional
    public int getAttemptCount(Long userId) {
        try {
            QuestHistory h = em.createQuery(
                "SELECT h FROM QuestHistory h WHERE h.userId = :uid AND h.answeredAt IS NULL " +
                "ORDER BY h.assignedAt DESC", QuestHistory.class)
                .setParameter("uid", userId)
                .setMaxResults(1)
                .getSingleResult();
            return h.getAttemptCount();
        } catch (NoResultException e) {
            return 0;
        }
    }

    /**
     * Pick a random question not recently answered by user.
     */
    private QuestQuestion pickQuestion(String difficulty, Long userId) {
        // Get recently answered question IDs to exclude
        List<Integer> recentIds = em.createQuery(
            "SELECT h.question.id FROM QuestHistory h WHERE h.userId = :uid " +
            "ORDER BY h.assignedAt DESC", Integer.class)
            .setParameter("uid", userId)
            .setMaxResults(RECENT_HISTORY_LIMIT)
            .getResultList();

        // Pick random question excluding recent ones
        String jpql = recentIds.isEmpty()
            ? "SELECT q FROM QuestQuestion q WHERE q.difficulty = :diff ORDER BY FUNCTION('RANDOM')"
            : "SELECT q FROM QuestQuestion q WHERE q.difficulty = :diff AND q.id NOT IN :exclude ORDER BY FUNCTION('RANDOM')";

        var query = em.createQuery(jpql, QuestQuestion.class)
            .setParameter("diff", difficulty)
            .setMaxResults(1);

        if (!recentIds.isEmpty()) {
            query.setParameter("exclude", recentIds);
        }

        try {
            return query.getSingleResult();
        } catch (NoResultException e) {
            // Fallback: all questions used, pick any from difficulty
            try {
                return em.createQuery(
                    "SELECT q FROM QuestQuestion q WHERE q.difficulty = :diff ORDER BY FUNCTION('RANDOM')",
                    QuestQuestion.class)
                    .setParameter("diff", difficulty)
                    .setMaxResults(1)
                    .getSingleResult();
            } catch (NoResultException e2) {
                return null;
            }
        }
    }

    /**
     * Map pet level to question difficulty.
     * Levels 1-3: easy, 4-6: medium, 7+: hard
     */
    private String mapDifficulty(int petLevel) {
        if (petLevel <= 3) return "easy";
        if (petLevel <= 6) return "medium";
        return "hard";
    }

    private QuestDto toDto(QuestQuestion q) {
        return new QuestDto(q.getCode(), q.getQuestionText(), q.getCategory(),
            q.getDifficulty(), q.getExpectedAnswer(), q.getHint());
    }
}
