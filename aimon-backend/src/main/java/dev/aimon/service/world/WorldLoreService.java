package dev.aimon.service.world;

import dev.aimon.entity.world.WorldLore;
import dev.aimon.repository.WorldLoreRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Fetches and ranks world lore entries for prompt injection.
 * Only AMBIENT entries are injected; SIDE/MILESTONE reserved for Phase 2b.
 */
@ApplicationScoped
public class WorldLoreService {

    private static final Logger LOG = Logger.getLogger(WorldLoreService.class);

    @Inject
    WorldLoreRepository repository;

    @ConfigProperty(name = "world.lore.max-inject", defaultValue = "3")
    int maxLoreEntries;

    /**
     * Get unlocked AMBIENT lore, ranked by interest tag overlap.
     */
    @Transactional(Transactional.TxType.SUPPORTS)
    public List<WorldLore> getUnlockedLore(String worldCode, int petLevel, List<String> topInterests) {
        List<WorldLore> all = repository.findAmbientUnlocked(worldCode, petLevel);
        if (all.isEmpty()) return List.of();

        if (topInterests == null || topInterests.isEmpty()) {
            List<WorldLore> shuffled = new ArrayList<>(all);
            Collections.shuffle(shuffled);
            return shuffled.stream().limit(maxLoreEntries).collect(Collectors.toList());
        }

        Set<String> interestSet = new HashSet<>(topInterests);
        return all.stream()
            .sorted(Comparator.comparingInt(
                (WorldLore w) -> interestOverlap(w.getInterestTags(), interestSet)
            ).reversed())
            .limit(maxLoreEntries)
            .collect(Collectors.toList());
    }

    /**
     * Format lore entries into prompt text for LLM injection.
     */
    public String formatLorePrompt(List<WorldLore> entries) {
        if (entries.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("=== Thế giới của Mon (điều Mon có thể tự nhiên chia sẻ) ===\n");
        for (WorldLore entry : entries) {
            sb.append("- ").append(entry.getContent()).append("\n");
        }
        sb.append("Hãy nhắc đến những điều này tự nhiên trong cuộc trò chuyện khi phù hợp.\n");
        return sb.toString();
    }

    private int interestOverlap(String[] tags, Set<String> interests) {
        if (tags == null) return 0;
        int count = 0;
        for (String tag : tags) {
            if (interests.contains(tag)) count++;
        }
        return count;
    }
}
