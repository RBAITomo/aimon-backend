package dev.aimon.service.pet;

import dev.aimon.entity.pet.PetProfile;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Scheduled task for applying stat decay to active pets.
 * Runs every 60 seconds to check and apply decay based on last_decay_at timestamps.
 */
@ApplicationScoped
public class PetStatDecayScheduler {

    private static final Logger LOG = Logger.getLogger(PetStatDecayScheduler.class);

    @Inject
    PetProfileService profileService;

    @Inject
    PetEvolutionService evolutionService;

    @Inject
    EntityManager em;

    @Inject
    dev.aimon.websocket.SessionConnectionRegistry connectionRegistry;

    @Inject
    dev.aimon.websocket.PetMessageHandler petMessageHandler;

    /**
     * Scheduler entry point. Runs decay in a transaction, then sends WebSocket updates outside it.
     */
    @Scheduled(every = "60s")
    void checkDecay() {
        List<Long> decayedUsers = applyDecay();

        // Send WebSocket updates outside the transaction
        for (Long userId : decayedUsers) {
            connectionRegistry.getConnection(userId).ifPresent(conn ->
                petMessageHandler.sendPetStatus(userId, conn)
                    .subscribe().with(v -> {}, e -> LOG.errorf(e, "Failed to send pet status update"))
            );
        }

        if (!decayedUsers.isEmpty()) {
            LOG.debugf("Applied decay to %d active pet profiles", decayedUsers.size());
        }
    }

    /**
     * Apply stat decay to active pets within a transaction.
     * Returns list of user IDs whose pets were decayed.
     */
    @Transactional
    List<Long> applyDecay() {
        java.util.Set<Long> activeUsers = connectionRegistry.getActiveUserIds();

        if (activeUsers.isEmpty()) {
            return List.of();
        }

        List<Long> decayedUsers = new java.util.ArrayList<>();

        for (Long userId : activeUsers) {
            PetProfile profile = profileService.getProfile(userId);
            if (profile == null) {
                continue;
            }

            int intervalMinutes = getDecayIntervalMinutes(profile);
            LocalDateTime nextDecayAt = profile.getLastDecayAt().plusMinutes(intervalMinutes);

            if (nextDecayAt.isBefore(LocalDateTime.now())) {
                profileService.applyDecay(profile, 5, 3, 3);
                decayedUsers.add(userId);

                if (profile.getVariant() != null) {
                    evolutionService.expireVariant(profile);
                }

                boolean regressed = evolutionService.checkRegression(profile);
                if (regressed) {
                    LOG.warnf("Pet regressed to EGG for user %d", profile.getUserId());
                }
            }
        }

        return decayedUsers;
    }

    /**
     * Calculate decay interval based on user engagement.
     * More active users have faster decay (more frequent interaction needed).
     */
    int getDecayIntervalMinutes(PetProfile profile) {
        int totalSessions = profile.getTotalSessions();

        if (totalSessions < 10) {
            return 60;  // New users: 1 hour interval
        } else if (totalSessions < 50) {
            return 30;  // Regular users: 30 min interval
        } else {
            return 15;  // Power users: 15 min interval
        }
    }
}
