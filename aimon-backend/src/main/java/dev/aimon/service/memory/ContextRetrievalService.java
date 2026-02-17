package dev.aimon.service.memory;

import dev.aimon.dto.powermem.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Context Retrieval Service implementing 3-layer memory retrieval.
 *
 * Flow:
 * 1. Layer 1: search(query) returns relevant observation IDs (~50 tokens)
 * 2. Layer 2: timeline(anchorId) provides episodic context around anchor
 * 3. Layer 3: fetch_details(ids) pulls full observation data for top results
 *
 * This approach prevents token explosion by filtering before fetching full content.
 * Results are formatted for LLM prompt injection with <150 token budget.
 */
@ApplicationScoped
public class ContextRetrievalService {
    private static final Logger log = LoggerFactory.getLogger(ContextRetrievalService.class);

    @Inject
    PowerMemService memoryService;

    @ConfigProperty(name = "memory.context.max-tokens", defaultValue = "150")
    int maxTokens;

    @ConfigProperty(name = "memory.context.search-limit", defaultValue = "10")
    int searchLimit;

    @ConfigProperty(name = "memory.context.timeline-depth-before", defaultValue = "5")
    int timelineDepthBefore;

    @ConfigProperty(name = "memory.context.timeline-depth-after", defaultValue = "3")
    int timelineDepthAfter;

    @ConfigProperty(name = "memory.context.importance-threshold", defaultValue = "0.5")
    double importanceThreshold;

    /**
     * Retrieve context for conversation using 3-layer approach.
     * Returns formatted context string for LLM prompt injection.
     *
     * @param userMessage The user's message to search for relevant context
     * @param robotId     Robot ID for filtering observations
     * @return Formatted context string (max ~150 tokens)
     */
    public String retrieveContextForConversation(String userMessage, Integer robotId) {
        if (!memoryService.isEnabled()) {
            log.trace("PowerMem disabled, returning empty context");
            return "";
        }

        try {
            // Build filters for robot-specific search
            Map<String, Object> filters = new HashMap<>();
            if (robotId != null) {
                filters.put("robot_id", robotId);
            }

            // Layer 1: Search for relevant observation IDs
            List<SearchResult> searchResults = memoryService.search(userMessage, filters, searchLimit);
            if (searchResults.isEmpty()) {
                log.debug("No search results for query: {}", truncateForLog(userMessage));
                return "";
            }

            // Extract top 3 results by score
            List<SearchResult> topResults = searchResults.stream()
                .filter(r -> r.score() != null && r.score() >= importanceThreshold)
                .sorted(Comparator.comparingDouble(SearchResult::score).reversed())
                .limit(3)
                .collect(Collectors.toList());

            if (topResults.isEmpty()) {
                log.debug("No results above importance threshold {}", importanceThreshold);
                return "";
            }

            log.debug("Search found {} results, selecting top {} above threshold",
                searchResults.size(), topResults.size());

            // Layer 2: Get timeline context around first (anchor) result
            String anchorId = topResults.get(0).id();
            List<TimelineEntry> timelineEntries = getTimelineAroundAnchor(anchorId, robotId);

            // Layer 3: Fetch full details for top IDs
            List<String> topIds = topResults.stream()
                .map(SearchResult::id)
                .collect(Collectors.toList());
            List<Observation> observations = fetchObservationDetails(topIds);

            // Combine search results, timeline, and full observations
            List<ContextEntry> combinedContext = mergeAndDeduplicate(topResults, timelineEntries, observations);

            // Format as context string with token budget
            return MemoryContextFormatter.formatAsSystemPrompt(combinedContext, maxTokens);

        } catch (Exception e) {
            log.warn("Context retrieval failed, returning empty: {}", e.getMessage());
            return "";
        }
    }

    /**
     * Retrieve context asynchronously for non-blocking conversation flow.
     *
     * @param userMessage The user's message
     * @param robotId     Robot ID
     * @return CompletableFuture with formatted context string
     */
    public CompletableFuture<String> retrieveContextAsync(String userMessage, Integer robotId) {
        return CompletableFuture.supplyAsync(() -> retrieveContextForConversation(userMessage, robotId));
    }

    /**
     * Get timeline entries around an anchor observation.
     * Fetches entries before and after the anchor for episodic context.
     */
    private List<TimelineEntry> getTimelineAroundAnchor(String anchorId, Integer robotId) {
        try {
            // Get timeline for the robot (newest first)
            List<TimelineEntry> timeline = memoryService.getTimeline(robotId, timelineDepthBefore + timelineDepthAfter + 1);
            if (timeline.isEmpty()) {
                return Collections.emptyList();
            }

            // Find anchor position and extract surrounding entries
            int anchorIndex = -1;
            for (int i = 0; i < timeline.size(); i++) {
                if (timeline.get(i).id().equals(anchorId)) {
                    anchorIndex = i;
                    break;
                }
            }

            if (anchorIndex == -1) {
                // Anchor not found in timeline, return first few entries
                return timeline.stream()
                    .limit(timelineDepthBefore)
                    .collect(Collectors.toList());
            }

            // Extract window around anchor
            int startIndex = Math.max(0, anchorIndex - timelineDepthBefore);
            int endIndex = Math.min(timeline.size(), anchorIndex + timelineDepthAfter + 1);

            return timeline.subList(startIndex, endIndex);

        } catch (Exception e) {
            log.warn("Timeline retrieval failed for anchor {}: {}", anchorId, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Fetch full observation details for a list of IDs.
     */
    private List<Observation> fetchObservationDetails(List<String> ids) {
        List<Observation> observations = new ArrayList<>();
        for (String id : ids) {
            try {
                Observation obs = memoryService.getObservation(id);
                if (obs != null) {
                    observations.add(obs);
                }
            } catch (Exception e) {
                log.warn("Failed to fetch observation {}: {}", id, e.getMessage());
            }
        }
        return observations;
    }

    /**
     * Merge and deduplicate context from search, timeline, and observations.
     * Prioritizes search results (highest relevance), then timeline (episodic context).
     */
    private List<ContextEntry> mergeAndDeduplicate(
        List<SearchResult> searchResults,
        List<TimelineEntry> timeline,
        List<Observation> observations
    ) {
        Set<String> seenIds = new HashSet<>();
        List<ContextEntry> combined = new ArrayList<>();

        // Priority 1: Search results (most relevant)
        for (SearchResult result : searchResults) {
            if (seenIds.add(result.id())) {
                double importance = result.score() != null ? result.score() : 0.5;
                combined.add(new ContextEntry(result.id(), result.content(), importance, "search"));
            }
        }

        // Priority 2: Timeline entries (episodic context)
        for (TimelineEntry entry : timeline) {
            if (seenIds.add(entry.id())) {
                double importance = entry.importance() != null ? entry.importance() : 0.3;
                combined.add(new ContextEntry(entry.id(), entry.contentPreview(), importance, "timeline"));
            }
        }

        // Priority 3: Full observations (detailed content - update existing entries)
        Map<String, Observation> obsMap = observations.stream()
            .filter(o -> o.id() != null)
            .collect(Collectors.toMap(Observation::id, o -> o, (a, b) -> a));

        // Upgrade content from observations where available
        for (int i = 0; i < combined.size(); i++) {
            ContextEntry entry = combined.get(i);
            Observation obs = obsMap.get(entry.id());
            if (obs != null && obs.content() != null && !obs.content().equals(entry.content())) {
                // Replace with full content from observation
                combined.set(i, new ContextEntry(entry.id(), obs.content(), entry.importance(), entry.source()));
            }
        }

        log.debug("Merged context: {} entries from {} search + {} timeline + {} observations",
            combined.size(), searchResults.size(), timeline.size(), observations.size());

        return combined;
    }

    /**
     * Truncate message for logging (avoid long messages in logs).
     */
    private String truncateForLog(String message) {
        if (message == null) return "";
        return message.length() > 50 ? message.substring(0, 50) + "..." : message;
    }

    /**
     * Internal record for combined context entries.
     */
    public record ContextEntry(
        String id,
        String content,
        double importance,
        String source  // "search", "timeline", or "observation"
    ) {}
}
