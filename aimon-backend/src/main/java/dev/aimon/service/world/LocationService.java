package dev.aimon.service.world;

import dev.aimon.dto.world.LocationDto;
import dev.aimon.entity.pet.PetProfile;
import dev.aimon.model.LocationUnlockEvent;
import dev.aimon.repository.combat.CombatLogRepository;
import dev.aimon.repository.combat.TastelessConfigRepository;
import dev.aimon.repository.world.UserShardRepository;
import dev.aimon.service.pet.PetProfileService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.util.*;

/**
 * Manages location unlock checks and switching.
 * Called after shard grants to auto-unlock newly eligible locations.
 */
@ApplicationScoped
public class LocationService {

    private static final Logger LOG = Logger.getLogger(LocationService.class);

    @Inject
    PetProfileService petService;

    @Inject
    UserShardRepository shardRepo;

    @Inject
    CombatLogRepository combatLogRepo;

    @Inject
    TastelessConfigRepository tastelessRepo;

    @Inject
    Event<LocationUnlockEvent> unlockEvent;

    @Inject
    TravelService travelService;

    /**
     * Get all locations with unlock status for a user.
     */
    public List<LocationDto> getAllLocations(Long userId) {
        PetProfile pet = petService.getProfile(userId);
        if (pet == null) return List.of();

        long milestones = shardRepo.countMilestoneShards(userId);
        Set<String> bossRegions = getBossDefeatedRegions(userId);

        List<LocationDto> result = new ArrayList<>();
        for (LocationUnlockRule rule : LocationUnlockRule.values()) {
            boolean unlocked = rule.isEligible(pet.getLevel(), milestones, bossRegions);
            result.add(new LocationDto(rule.name(), rule.getDisplayName(), unlocked));
        }
        return result;
    }

    /**
     * Switch user's current location. Returns false if location is locked.
     */
    @Transactional
    public boolean switchLocation(Long userId, String locationCode) {
        // Delegate sub-location codes to TravelService
        if (SubLocation.fromCode(locationCode).isPresent()) {
            return travelService.travel(userId, locationCode);
        }
        try {
            LocationUnlockRule rule = LocationUnlockRule.valueOf(locationCode);
            PetProfile pet = petService.getProfile(userId);
            if (pet == null) return false;

            long milestones = shardRepo.countMilestoneShards(userId);
            Set<String> bossRegions = getBossDefeatedRegions(userId);

            if (!rule.isEligible(pet.getLevel(), milestones, bossRegions)) {
                LOG.warnf("User %d tried to switch to locked location: %s", userId, locationCode);
                return false;
            }

            pet.setCurrentLocation(locationCode);
            LOG.infof("User %d switched to location: %s", userId, locationCode);
            return true;
        } catch (IllegalArgumentException e) {
            LOG.warnf("Unknown location code: %s", locationCode);
            return false;
        }
    }

    /**
     * Check all locations for newly eligible unlocks. Called after milestone shard grants.
     * Fires LocationUnlockEvent for each newly unlocked location.
     */
    public void checkLocationUnlocks(Long userId) {
        PetProfile pet = petService.getProfile(userId);
        if (pet == null) return;

        long milestones = shardRepo.countMilestoneShards(userId);
        Set<String> bossRegions = getBossDefeatedRegions(userId);

        for (LocationUnlockRule rule : LocationUnlockRule.values()) {
            if (rule == LocationUnlockRule.SWEET_DOMINION) continue; // always unlocked
            if (rule.isEligible(pet.getLevel(), milestones, bossRegions)) {
                // Fire unlock event (frontend shows notification; idempotent — re-fire is fine)
                unlockEvent.fire(new LocationUnlockEvent(userId, rule.name(), rule.getDisplayName()));
            }
        }
    }

    /** Get set of region codes where the user has defeated the boss */
    private Set<String> getBossDefeatedRegions(Long userId) {
        Set<String> regions = new HashSet<>();
        for (LocationUnlockRule rule : LocationUnlockRule.values()) {
            var boss = tastelessRepo.findBossByLocation(rule.name());
            if (boss != null && combatLogRepo.hasDefeated(userId, boss.getId())) {
                regions.add(rule.name());
            }
        }
        return regions;
    }
}
