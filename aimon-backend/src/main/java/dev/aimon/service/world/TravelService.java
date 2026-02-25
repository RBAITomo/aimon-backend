package dev.aimon.service.world;

import dev.aimon.entity.pet.PetProfile;
import dev.aimon.model.LocationChangedEvent;
import dev.aimon.service.pet.PetProfileService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.util.Optional;

/**
 * Handles sub-location travel within a region.
 * Validates sub-location code, checks pet is in correct region, updates profile, fires CDI event.
 */
@ApplicationScoped
public class TravelService {

    private static final Logger LOG = Logger.getLogger(TravelService.class);

    @Inject
    PetProfileService petService;

    @Inject
    Event<LocationChangedEvent> locationChangedEvent;

    /**
     * Travel to a sub-location. Returns false if code invalid or pet not in parent region.
     */
    @Transactional
    public boolean travel(Long userId, String subLocationCode) {
        Optional<SubLocation> subLoc = SubLocation.fromCode(subLocationCode);
        if (subLoc.isEmpty()) {
            LOG.warnf("Unknown sub-location code: %s", subLocationCode);
            return false;
        }

        PetProfile pet = petService.getProfile(userId);
        if (pet == null) return false;

        SubLocation target = subLoc.get();
        String current = pet.getCurrentLocation();

        // Verify pet is in parent region or another sub-location of same region
        boolean inRegion = target.getParentRegion().equals(current)
            || SubLocation.fromCode(current)
                .map(sl -> sl.getParentRegion().equals(target.getParentRegion()))
                .orElse(false);

        if (!inRegion) {
            LOG.warnf("User %d not in region %s (current: %s), cannot travel to %s",
                userId, target.getParentRegion(), current, subLocationCode);
            return false;
        }

        pet.setCurrentLocation(subLocationCode);
        locationChangedEvent.fire(new LocationChangedEvent(
            userId, subLocationCode, target.getBackgroundFile()));
        LOG.infof("User %d traveled to %s", userId, subLocationCode);
        return true;
    }
}
