package dev.aimon.service.pet;

import dev.aimon.entity.pet.ActionCounter;
import dev.aimon.entity.pet.Badge;
import dev.aimon.entity.pet.UserBadge;
import dev.aimon.model.BadgeConditionType;
import dev.aimon.model.BadgeEarnedEvent;
import dev.aimon.model.PetActionEvent;
import dev.aimon.service.memory.PowerMemService;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Badge management and achievement tracking service.
 * Listens to PetActionEvents and awards badges when conditions are met.
 */
@ApplicationScoped
public class BadgeService {

    private static final Logger LOG = Logger.getLogger(BadgeService.class);

    @Inject
    EntityManager em;

    @Inject
    Event<BadgeEarnedEvent> badgeEvent;

    @Inject
    PetProfileService profileService;

    @Inject
    PowerMemService memoryService;

    private Map<String, Badge> badgeCache;

    @PostConstruct
    void loadBadgeCache() {
        badgeCache = new HashMap<>();
        List<Badge> badges = em.createQuery("SELECT b FROM Badge b", Badge.class).getResultList();

        for (Badge badge : badges) {
            badgeCache.put(badge.getCode(), badge);
        }

        LOG.infof("Loaded %d badges into cache", badgeCache.size());
    }

    /**
     * CDI event listener for pet actions.
     */
    @Transactional
    public void onPetAction(@Observes PetActionEvent event) {
        LOG.debugf("Processing action event: user=%d, action=%s, amount=%d",
            event.userId(), event.actionType(), Integer.valueOf(event.amount()));

        // Increment counter
        long newCount = incrementCounter(event.userId(), event.actionType(), event.amount());

        // Check badge conditions
        checkBadgeConditions(event.userId(), event.actionType(), newCount);
    }

    /**
     * Increment or create action counter.
     */
    @Transactional
    long incrementCounter(Long userId, String actionType, int amount) {
        ActionCounter counter = em.createQuery(
            "SELECT a FROM ActionCounter a WHERE a.userId = :userId AND a.actionType = :actionType",
            ActionCounter.class)
            .setParameter("userId", userId)
            .setParameter("actionType", actionType)
            .getResultStream()
            .findFirst()
            .orElse(null);

        if (counter == null) {
            counter = new ActionCounter();
            counter.setUserId(userId);
            counter.setActionType(actionType);
            counter.setCount(0L);
            em.persist(counter);
        }

        counter.setCount(counter.getCount() + amount);
        counter.setLastAt(LocalDateTime.now());

        LOG.debugf("Counter updated: user=%d, action=%s, count=%d",
            userId, actionType, counter.getCount());

        return counter.getCount();
    }

    /**
     * Check if any badges should be awarded based on action count.
     */
    void checkBadgeConditions(Long userId, String actionType, long newCount) {
        // Find all badges with matching action type
        for (Badge badge : badgeCache.values()) {
            if (badge.getConditionType() != BadgeConditionType.COUNTER) {
                continue;
            }

            Map<String, Object> config = badge.getConditionConfig();
            if (config == null) {
                continue;
            }

            Object actionObj = config.get("action");
            Object countObj = config.get("count");

            if (actionObj == null || countObj == null) {
                continue;
            }

            String requiredAction = actionObj.toString();
            int requiredCount = ((Number) countObj).intValue();

            // Check if this badge matches the action
            if (requiredAction.equals(actionType) && newCount >= requiredCount) {
                // Check if not already earned
                if (!hasEarnedBadge(userId, badge.getCode())) {
                    awardBadge(userId, badge);
                }
            }
        }
    }

    /**
     * Award badge to user and fire event.
     */
    @Transactional
    void awardBadge(Long userId, Badge badge) {
        // Get or create user badge entry
        UserBadge userBadge = em.createQuery(
            "SELECT ub FROM UserBadge ub WHERE ub.userId = :userId AND ub.badge.id = :badgeId",
            UserBadge.class)
            .setParameter("userId", userId)
            .setParameter("badgeId", badge.getId())
            .getResultStream()
            .findFirst()
            .orElse(null);

        if (userBadge == null) {
            userBadge = new UserBadge();
            userBadge.setUserId(userId);
            userBadge.setBadge(badge);
            em.persist(userBadge);
        }

        // Mark as earned
        if (userBadge.getEarnedAt() == null) {
            userBadge.setEarnedAt(LocalDateTime.now());

            LOG.infof("Badge earned: user=%d, badge=%s (%s), xp=%d",
                userId, badge.getCode(), badge.getName(), badge.getXpReward());

            // Award XP
            profileService.addXp(userId, badge.getXpReward());

            // Record to memory
            memoryService.recordAchievement(
                userId.intValue(),
                "Đạt huy hiệu: " + badge.getName()
            );

            // Fire event
            badgeEvent.fire(new BadgeEarnedEvent(
                userId, badge.getCode(), badge.getName(),
                badge.getDescription() != null ? badge.getDescription() : "",
                badge.getXpReward()));
        }
    }

    /**
     * Get all badges for user.
     */
    public List<UserBadge> getUserBadges(Long userId) {
        return em.createQuery(
            "SELECT ub FROM UserBadge ub JOIN FETCH ub.badge WHERE ub.userId = :userId",
            UserBadge.class)
            .setParameter("userId", userId)
            .getResultList();
    }

    /**
     * Check if user has earned specific badge.
     */
    public boolean hasEarnedBadge(Long userId, String badgeCode) {
        Badge badge = badgeCache.get(badgeCode);
        if (badge == null) {
            return false;
        }

        Long count = em.createQuery(
            "SELECT COUNT(ub) FROM UserBadge ub WHERE ub.userId = :userId " +
            "AND ub.badge.id = :badgeId AND ub.earnedAt IS NOT NULL", Long.class)
            .setParameter("userId", userId)
            .setParameter("badgeId", badge.getId())
            .getSingleResult();

        return count > 0;
    }

    /**
     * Check if user has earned all specified badges.
     */
    public boolean hasAllBadges(Long userId, List<String> badgeCodes) {
        for (String code : badgeCodes) {
            if (!hasEarnedBadge(userId, code)) {
                return false;
            }
        }
        return true;
    }
}
