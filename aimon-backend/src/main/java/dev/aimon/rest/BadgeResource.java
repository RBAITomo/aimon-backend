package dev.aimon.rest;

import dev.aimon.entity.pet.Badge;
import dev.aimon.entity.pet.UserBadge;
import dev.aimon.service.pet.BadgeService;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.jboss.logging.Logger;

import java.util.*;

/**
 * REST endpoint for badge data. Used by frontend to fetch all badges on connect.
 */
@Path("/api/badges")
@Produces(MediaType.APPLICATION_JSON)
public class BadgeResource {

    private static final Logger LOG = Logger.getLogger(BadgeResource.class);

    @Inject
    BadgeService badgeService;

    @Inject
    EntityManager em;

    @GET
    @Path("/{userId}")
    public List<Map<String, Object>> getUserBadges(@PathParam("userId") Long userId) {
        LOG.infof("GET /api/badges/%d", userId);

        // All badges from catalog
        List<Badge> allBadges = em.createQuery("SELECT b FROM Badge b ORDER BY b.id", Badge.class)
            .getResultList();

        // User's earned badges
        List<UserBadge> earned = badgeService.getUserBadges(userId);
        Map<Long, UserBadge> earnedMap = new HashMap<>();
        for (UserBadge ub : earned) {
            earnedMap.put(ub.getBadge().getId(), ub);
        }

        // Build response with earned status
        List<Map<String, Object>> result = new ArrayList<>();
        for (Badge badge : allBadges) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("code", badge.getCode());
            entry.put("name", badge.getName());
            entry.put("description", badge.getDescription());
            entry.put("category", badge.getCategory());
            entry.put("icon", badge.getIcon());
            entry.put("xp_reward", badge.getXpReward());

            UserBadge ub = earnedMap.get(badge.getId());
            entry.put("earned", ub != null && ub.getEarnedAt() != null);
            entry.put("progress", ub != null ? ub.getProgress() : 0);
            entry.put("earned_at", ub != null && ub.getEarnedAt() != null ? ub.getEarnedAt().toString() : null);
            result.add(entry);
        }

        return result;
    }
}
