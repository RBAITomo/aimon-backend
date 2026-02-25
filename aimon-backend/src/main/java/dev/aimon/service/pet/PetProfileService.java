package dev.aimon.service.pet;

import dev.aimon.dto.pet.PetStatusDto;
import dev.aimon.entity.pet.PetProfile;
import dev.aimon.model.PetActionEvent;
import dev.aimon.model.PetMood;
import dev.aimon.service.memory.PowerMemService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.LocalDateTime;

/**
 * Pet profile management service.
 * Handles CRUD, stat updates, XP progression, and mood derivation.
 */
@ApplicationScoped
public class PetProfileService {

    private static final Logger LOG = Logger.getLogger(PetProfileService.class);

    @Inject
    EntityManager em;

    @Inject
    Event<PetActionEvent> actionEvent;

    @Inject
    PetEvolutionService evolutionService;

    @Inject
    PowerMemService memoryService;

    /**
     * Get or create pet profile for user.
     */
    @Transactional
    public PetProfile getOrCreateProfile(Long userId) {
        PetProfile profile = em.createQuery(
            "SELECT p FROM PetProfile p WHERE p.userId = :userId", PetProfile.class)
            .setParameter("userId", userId)
            .getResultStream()
            .findFirst()
            .orElse(null);

        if (profile == null) {
            profile = new PetProfile();
            profile.setUserId(userId);
            profile.setName("Mon");
            profile.setHunger(50);
            profile.setEnergy(100);
            profile.setHappiness(80);
            profile.setXp(0L);
            profile.setLevel(1);
            profile.setAffinity(0);
            profile.setLoginStreak(0);
            profile.setTotalSessions(0);
            profile.setRegressionWarnings(0);
            profile.setLastDecayAt(LocalDateTime.now());
            em.persist(profile);
            LOG.infof("Created new pet profile for user %d", userId);
        }

        return profile;
    }

    /**
     * Look up userId by pet name (case-insensitive).
     * Returns null if no pet with that name exists.
     */
    public Long findUserIdByName(String name) {
        return em.createQuery(
            "SELECT p.userId FROM PetProfile p WHERE LOWER(p.name) = LOWER(:name)", Long.class)
            .setParameter("name", name)
            .getResultStream()
            .findFirst()
            .orElse(null);
    }

    /**
     * Get existing profile (no creation).
     */
    public PetProfile getProfile(Long userId) {
        return em.createQuery(
            "SELECT p FROM PetProfile p WHERE p.userId = :userId", PetProfile.class)
            .setParameter("userId", userId)
            .getResultStream()
            .findFirst()
            .orElse(null);
    }

    /**
     * Apply feeding action.
     */
    @Transactional
    public void applyFeed(Long userId, String foodName, int hungerReduction) {
        PetProfile profile = getOrCreateProfile(userId);
        int oldHunger = profile.getHunger();
        profile.setHunger(clamp(oldHunger - hungerReduction, 0, 100));

        LOG.infof("User %d fed pet with %s: hunger %d -> %d",
            userId, foodName, oldHunger, profile.getHunger());

        actionEvent.fire(new PetActionEvent(userId, "feed", 1));

        // Record to memory
        memoryService.recordPetCare(
            userId.intValue(),
            "feed",
            "Bé cho " + profile.getName() + " ăn " + foodName
        );
    }

    /**
     * Apply chat interaction.
     */
    @Transactional
    public void applyChat(Long userId, int happinessGain) {
        PetProfile profile = getOrCreateProfile(userId);
        int oldHappiness = profile.getHappiness();
        profile.setHappiness(clamp(oldHappiness + happinessGain, 0, 100));

        LOG.infof("User %d chatted with pet: happiness %d -> %d",
            userId, oldHappiness, profile.getHappiness());

        actionEvent.fire(new PetActionEvent(userId, "chat", 1));

        // Record to memory
        memoryService.recordPetCare(
            userId.intValue(),
            "chat",
            "Trò chuyện với " + profile.getName()
        );
    }

    /**
     * Apply quest completion.
     */
    @Transactional
    public void applyQuestComplete(Long userId, int happinessGain, int energyCost) {
        PetProfile profile = getOrCreateProfile(userId);

        int oldHappiness = profile.getHappiness();
        int oldEnergy = profile.getEnergy();

        profile.setHappiness(clamp(oldHappiness + happinessGain, 0, 100));
        profile.setEnergy(clamp(oldEnergy - energyCost, 0, 100));

        LOG.infof("User %d completed quest: happiness %d -> %d, energy %d -> %d",
            userId, oldHappiness, profile.getHappiness(), oldEnergy, profile.getEnergy());

        actionEvent.fire(new PetActionEvent(userId, "quest_complete", 1));
    }

    /**
     * Apply rest action.
     */
    @Transactional
    public void applyRest(Long userId, int energyGain) {
        PetProfile profile = getOrCreateProfile(userId);
        int oldEnergy = profile.getEnergy();
        profile.setEnergy(clamp(oldEnergy + energyGain, 0, 100));

        LOG.infof("User %d rested pet: energy %d -> %d",
            userId, oldEnergy, profile.getEnergy());

        actionEvent.fire(new PetActionEvent(userId, "rest", 1));
    }

    /**
     * Add XP and handle level-up with evolution check.
     */
    @Transactional
    public void addXp(Long userId, int amount) {
        PetProfile profile = getOrCreateProfile(userId);

        long oldXp = profile.getXp();
        int oldLevel = profile.getLevel();

        profile.setXp(oldXp + amount);
        int newLevel = PetLevelConfig.calculateLevel(profile.getXp());

        if (newLevel > oldLevel) {
            profile.setLevel(newLevel);
            LOG.infof("User %d leveled up: %d -> %d (XP: %d)",
                userId, oldLevel, newLevel, profile.getXp());

            // Check for evolution
            evolutionService.checkEvolution(profile);
        }

        actionEvent.fire(new PetActionEvent(userId, "xp_gain", amount));
    }

    /**
     * Add happiness to pet profile (clamped 0-100).
     */
    @Transactional
    public void addHappiness(Long userId, int amount) {
        PetProfile profile = getOrCreateProfile(userId);
        profile.setHappiness(clamp(profile.getHappiness() + amount, 0, 100));
    }

    /**
     * Apply stat decay (called by scheduler).
     */
    @Transactional
    public void applyDecay(PetProfile profile, int hungerIncrease, int energyDecrease, int happinessDecrease) {
        profile.setHunger(clamp(profile.getHunger() + hungerIncrease, 0, 100));
        profile.setEnergy(clamp(profile.getEnergy() - energyDecrease, 0, 100));
        profile.setHappiness(clamp(profile.getHappiness() - happinessDecrease, 0, 100));
        profile.setLastDecayAt(LocalDateTime.now());

        LOG.debugf("Applied decay to user %d: hunger=%d, energy=%d, happiness=%d",
            profile.getUserId(), profile.getHunger(), profile.getEnergy(), profile.getHappiness());
    }

    /**
     * Apply combat loss penalties: increase hunger, decrease happiness.
     */
    @Transactional
    public void applyStatPenalty(Long userId, int hungerIncrease, int happinessDecrease) {
        PetProfile profile = getOrCreateProfile(userId);
        profile.setHunger(clamp(profile.getHunger() + hungerIncrease, 0, 100));
        profile.setHappiness(clamp(profile.getHappiness() - happinessDecrease, 0, 100));

        LOG.infof("Combat penalty for user %d: hunger+%d, happiness-%d", userId, hungerIncrease, happinessDecrease);
    }

    /**
     * Update affinity stat.
     */
    @Transactional
    public void updateAffinity(Long userId, int delta) {
        PetProfile profile = getOrCreateProfile(userId);
        profile.setAffinity(clamp(profile.getAffinity() + delta, 0, 100));

        LOG.infof("User %d affinity updated: %d", userId, profile.getAffinity());
    }

    /**
     * Record daily login and update streak.
     */
    @Transactional
    public void recordDailyLogin(Long userId) {
        PetProfile profile = getOrCreateProfile(userId);

        // Increment streak (actual date checking would be in calling code)
        profile.setLoginStreak(profile.getLoginStreak() + 1);
        profile.setTotalSessions(profile.getTotalSessions() + 1);

        LOG.infof("User %d daily login: streak=%d, total=%d",
            userId, profile.getLoginStreak(), profile.getTotalSessions());

        actionEvent.fire(new PetActionEvent(userId, "daily_login", 1));
    }

    /**
     * Get pet status DTO for user.
     * Returns current state snapshot for client display and prompt assembly.
     */
    public PetStatusDto getStatus(Long userId) {
        PetProfile profile = getOrCreateProfile(userId);
        PetMood mood = deriveMood(profile);

        long xpForNext = PetLevelConfig.getRequiredXp(profile.getLevel() + 1) - profile.getXp();

        return new PetStatusDto(
            profile.getName(),
            profile.getStage().name(),
            profile.getVariant() != null ? profile.getVariant().getCode() : null,
            mood.name(),
            profile.getHunger(),
            profile.getEnergy(),
            profile.getHappiness(),
            profile.getLevel(),
            profile.getXp(),
            xpForNext,
            profile.getAffinity(),
            profile.getLoginStreak(),
            null, null, null
        );
    }

    /**
     * Derive current mood from stat values.
     */
    public PetMood deriveMood(PetProfile profile) {
        int hunger = profile.getHunger();
        int energy = profile.getEnergy();
        int happiness = profile.getHappiness();

        // Priority order for mood determination
        if (hunger > 80) return PetMood.HUNGRY;
        if (energy < 20) return PetMood.SLEEPY;
        if (happiness > 80) return PetMood.JOYFUL;
        if (happiness < 30) return PetMood.SAD;
        if (hunger < 30 && energy > 60 && happiness > 50) return PetMood.CONTENT;

        return PetMood.NEUTRAL;
    }

    /**
     * Clamp value between min and max.
     */
    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
