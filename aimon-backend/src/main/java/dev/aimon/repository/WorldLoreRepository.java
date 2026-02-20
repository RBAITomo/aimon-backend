package dev.aimon.repository;

import dev.aimon.entity.world.WorldLore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.util.List;

/**
 * Repository for world_lore table queries.
 * Fetches AMBIENT lore entries unlocked by world and pet level.
 */
@ApplicationScoped
public class WorldLoreRepository {

    @Inject
    EntityManager em;

    /**
     * Fetch AMBIENT lore unlocked for given world and level.
     * Returns randomized list; caller handles interest ranking and limiting.
     */
    public List<WorldLore> findAmbientUnlocked(String worldCode, int petLevel) {
        return em.createQuery(
            "SELECT w FROM WorldLore w " +
            "WHERE w.worldCode = :worldCode " +
            "AND w.minLevel <= :level " +
            "AND w.shardType = 'AMBIENT' " +
            "AND w.isActive = true",
            WorldLore.class
        )
        .setParameter("worldCode", worldCode)
        .setParameter("level", petLevel)
        .getResultList();
    }
}
