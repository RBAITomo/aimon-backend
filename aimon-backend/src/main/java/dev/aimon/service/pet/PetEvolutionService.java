package dev.aimon.service.pet;

import dev.aimon.entity.pet.PetProfile;
import dev.aimon.entity.pet.PetVariant;
import dev.aimon.model.*;
import dev.aimon.service.memory.PowerMemService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Pet evolution and variant transformation service.
 * Handles stage progression, variant transformations, and regression mechanics.
 */
@ApplicationScoped
public class PetEvolutionService {

    private static final Logger LOG = Logger.getLogger(PetEvolutionService.class);

    @Inject
    EntityManager em;

    @Inject
    Event<PetEvolutionEvent> evolutionEvent;

    @Inject
    Event<PetTransformEvent> transformEvent;

    @Inject
    Event<PetRegressionEvent> regressionEvent;

    @Inject
    PowerMemService memoryService;

    /**
     * Check and apply evolution if level threshold crossed.
     * Called after level-up.
     */
    @Transactional
    public Optional<PetEvolutionEvent> checkEvolution(PetProfile profile) {
        PetStage currentStage = profile.getStage();
        PetStage expectedStage = PetLevelConfig.getStageForLevel(profile.getLevel());

        // Skip evolution if in VARIANT form
        if (currentStage == PetStage.VARIANT) {
            return Optional.empty();
        }

        if (currentStage != expectedStage) {
            String oldStage = currentStage.name();
            profile.setStage(expectedStage);

            LOG.infof("Pet evolved for user %d: %s -> %s (level %d)",
                profile.getUserId(), oldStage, expectedStage.name(), profile.getLevel());

            // Record to memory
            memoryService.recordMilestone(
                profile.getUserId().intValue(),
                profile.getName() + " tiến hóa thành " + getStageDisplayName(expectedStage)
            );

            PetEvolutionEvent event = new PetEvolutionEvent(
                profile.getUserId(), oldStage, expectedStage.name());
            evolutionEvent.fire(event);

            return Optional.of(event);
        }

        return Optional.empty();
    }

    /**
     * Transform pet to variant form.
     * Returns true if transformation succeeded, false otherwise.
     */
    @Transactional
    public boolean transformToVariant(Long userId, String variantCode) {
        try {
            PetProfile profile = em.createQuery(
                "SELECT p FROM PetProfile p WHERE p.userId = :userId", PetProfile.class)
                .setParameter("userId", userId)
                .getResultStream()
                .findFirst()
                .orElse(null);

            if (profile == null) {
                LOG.warnf("No profile found for user %d", userId);
                return false;
            }

            // Verify prerequisites
            if (profile.getStage() != PetStage.ADULT) {
                LOG.warnf("Pet must be ADULT stage to transform (current: %s)", profile.getStage());
                return false;
            }

            if (profile.getVariantCooldownAt() != null &&
                profile.getVariantCooldownAt().isAfter(LocalDateTime.now())) {
                LOG.warnf("Variant transformation on cooldown for user %d", userId);
                return false;
            }

            // Fetch variant config
            PetVariant variant = em.createQuery(
                "SELECT v FROM PetVariant v WHERE v.code = :code", PetVariant.class)
                .setParameter("code", variantCode)
                .getResultStream()
                .findFirst()
                .orElse(null);

            if (variant == null) {
                LOG.warnf("Variant not found: %s", variantCode);
                return false;
            }

            // Verify level requirement
            if (profile.getLevel() < variant.getMinLevel()) {
                LOG.warnf("Level %d required for %s (current: %d)",
                    variant.getMinLevel(), variantCode, profile.getLevel());
                return false;
            }

            // Verify badge requirements (handled by BadgeService)
            // Assuming badges are already checked by caller

            // Calculate duration
            int durationMinutes = calculateTransformDuration(variant, profile.getLevel());

            // Apply transformation
            profile.setVariant(variant);
            profile.setStage(PetStage.VARIANT);
            profile.setVariantExpiresAt(LocalDateTime.now().plusMinutes(durationMinutes));
            profile.setVariantCooldownAt(null); // Clear cooldown on transform

            LOG.infof("User %d transformed to variant %s for %d minutes",
                userId, variantCode, durationMinutes);

            PetTransformEvent event = new PetTransformEvent(userId, variantCode, durationMinutes);
            transformEvent.fire(event);

            return true;
        } catch (Exception e) {
            LOG.errorf(e, "Error transforming to variant %s for user %d", variantCode, userId);
            return false;
        }
    }

    /**
     * Check and expire variant if time limit reached.
     */
    @Transactional
    public void expireVariant(PetProfile profile) {
        if (profile.getVariant() != null &&
            profile.getVariantExpiresAt() != null &&
            profile.getVariantExpiresAt().isBefore(LocalDateTime.now())) {

            LOG.infof("Variant expired for user %d", profile.getUserId());

            // Revert to ADULT
            profile.setStage(PetStage.ADULT);
            profile.setVariant(null);
            profile.setVariantExpiresAt(null);

            // Set cooldown
            if (profile.getVariant() != null) {
                int cooldownMinutes = profile.getVariant().getCooldownMinutes();
                profile.setVariantCooldownAt(LocalDateTime.now().plusMinutes(cooldownMinutes));
            }
        }
    }

    /**
     * Check if pet can transform to variant.
     */
    public boolean canTransform(PetProfile profile, String variantCode) {
        if (profile.getStage() != PetStage.ADULT) {
            return false;
        }

        if (profile.getVariantCooldownAt() != null &&
            profile.getVariantCooldownAt().isAfter(LocalDateTime.now())) {
            return false;
        }

        PetVariant variant = em.createQuery(
            "SELECT v FROM PetVariant v WHERE v.code = :code", PetVariant.class)
            .setParameter("code", variantCode)
            .getResultStream()
            .findFirst()
            .orElse(null);

        if (variant == null) {
            return false;
        }

        return profile.getLevel() >= variant.getMinLevel();
    }

    /**
     * Check regression conditions.
     * Returns true if regression was executed.
     */
    @Transactional
    public boolean checkRegression(PetProfile profile) {
        int hunger = profile.getHunger();
        int energy = profile.getEnergy();
        int happiness = profile.getHappiness();

        // Regression trigger: all stats critically low
        if (hunger > 90 && energy < 10 && happiness < 10) {
            profile.setRegressionWarnings(profile.getRegressionWarnings() + 1);
            LOG.warnf("User %d regression warning %d/3",
                profile.getUserId(), profile.getRegressionWarnings());

            if (profile.getRegressionWarnings() >= 3) {
                executeRegression(profile);
                return true;
            }
        } else {
            // Reset warnings if stats recover
            if (profile.getRegressionWarnings() > 0) {
                profile.setRegressionWarnings(0);
                LOG.infof("User %d regression warnings reset", profile.getUserId());
            }
        }

        return false;
    }

    /**
     * Execute full regression to EGG.
     */
    @Transactional
    public void executeRegression(PetProfile profile) {
        LOG.warnf("Executing regression for user %d due to neglect", profile.getUserId());

        // Reset progression
        profile.setStage(PetStage.EGG);
        profile.setLevel(1);
        profile.setXp(0L);

        // Reset stats to defaults
        profile.setHunger(50);
        profile.setEnergy(100);
        profile.setHappiness(80);
        profile.setAffinity(0);

        // Clear variant state
        profile.setVariant(null);
        profile.setVariantExpiresAt(null);
        profile.setVariantCooldownAt(null);

        // Reset warnings
        profile.setRegressionWarnings(0);

        // Reset action counters
        em.createQuery("DELETE FROM ActionCounter a WHERE a.userId = :userId")
            .setParameter("userId", profile.getUserId())
            .executeUpdate();

        // Keep earned badges (user_badges with earned_at != null)
        // Keep unlocked variants (user_variants)

        regressionEvent.fire(new PetRegressionEvent(profile.getUserId()));
    }

    /**
     * Calculate transformation duration based on level.
     */
    public int calculateTransformDuration(PetVariant variant, int petLevel) {
        int baseMinutes = variant.getTransformBaseMinutes();
        int minLevel = variant.getMinLevel();

        // Formula: base * (1 + (level - min_level) * 0.1)
        double multiplier = 1.0 + ((petLevel - minLevel) * 0.1);
        return (int) (baseMinutes * multiplier);
    }

    /**
     * Get Vietnamese display name for stage.
     */
    private String getStageDisplayName(PetStage stage) {
        return switch (stage) {
            case EGG -> "trứng";
            case BABY -> "em bé";
            case CHILD -> "trẻ em";
            case ADULT -> "trưởng thành";
            case VARIANT -> "dạng biến thể";
        };
    }
}
