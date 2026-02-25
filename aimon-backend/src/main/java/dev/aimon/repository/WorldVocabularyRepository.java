package dev.aimon.repository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.util.List;

/**
 * Repository for world_vocabulary table.
 * Returns term strings directly — no need to map full entities.
 */
@ApplicationScoped
public class WorldVocabularyRepository {

    @Inject
    EntityManager em;

    /**
     * Fetch vocabulary terms unlocked for the given world and pet level.
     * Only returns active terms with min_level <= petLevel.
     */
    public List<String> findTerms(String worldCode, int petLevel) {
        return em.createQuery(
            "SELECT v.term FROM WorldVocabulary v " +
            "WHERE v.worldCode = :worldCode " +
            "AND v.minLevel <= :level " +
            "AND v.isActive = true " +
            "ORDER BY v.term",
            String.class
        )
        .setParameter("worldCode", worldCode)
        .setParameter("level", petLevel)
        .getResultList();
    }
}
