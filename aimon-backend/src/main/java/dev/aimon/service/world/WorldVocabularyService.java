package dev.aimon.service.world;

import dev.aimon.repository.WorldVocabularyRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.transaction.Transactional.TxType;
import java.util.List;

/**
 * Service for fetching STT vocabulary hints by world and pet level.
 */
@ApplicationScoped
public class WorldVocabularyService {

    @Inject
    WorldVocabularyRepository repository;

    /**
     * Returns vocabulary terms to use as Google STT phrase hints.
     * Returns empty list if worldCode is blank or unknown.
     */
    @Transactional(TxType.SUPPORTS)
    public List<String> getVocabularyHints(String worldCode, int petLevel) {
        if (worldCode == null || worldCode.isBlank()) {
            return List.of();
        }
        return repository.findTerms(worldCode, petLevel);
    }
}
