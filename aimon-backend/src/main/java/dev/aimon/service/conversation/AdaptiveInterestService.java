package dev.aimon.service.conversation;

import dev.aimon.dto.powermem.TimelineEntry;
import dev.aimon.service.memory.PowerMemService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Derives child's top interests from recent PowerMem observations.
 *
 * Fetches timeline entries, re-classifies contentPreview via TopicClassifier (keyword only),
 * aggregates topic counts, applies diversity cap, returns top N topics.
 * Results cached per session — rebuilt at session start only.
 */
@ApplicationScoped
public class AdaptiveInterestService {

    private static final Logger LOG = Logger.getLogger(AdaptiveInterestService.class);
    private static final String USER_MESSAGE_PREFIX = "User said: ";

    @Inject
    PowerMemService powerMemService;

    @Inject
    TopicClassifier topicClassifier;

    @ConfigProperty(name = "interest.observation-lookback", defaultValue = "30")
    int observationLookback;

    @ConfigProperty(name = "interest.max-topics", defaultValue = "3")
    int maxTopics;

    @ConfigProperty(name = "interest.diversity-cap", defaultValue = "0.6")
    double diversityCap;

    /**
     * Fetch top interests for a user by re-classifying recent timeline content.
     *
     * @param robotId Robot/user ID used in PowerMem
     * @return Ordered list of top topic codes (most frequent first)
     */
    public List<String> getTopInterests(Integer robotId) {
        if (robotId == null) return List.of();

        try {
            List<TimelineEntry> entries = powerMemService.getTimeline(robotId, observationLookback);
            if (entries == null || entries.isEmpty()) return List.of();

            // Re-classify user messages from contentPreview
            Map<String, Integer> topicCounts = new LinkedHashMap<>();
            int totalCount = 0;

            for (TimelineEntry entry : entries) {
                String preview = entry.contentPreview();
                if (preview == null || !preview.startsWith(USER_MESSAGE_PREFIX)) continue;

                String userMessage = preview.substring(USER_MESSAGE_PREFIX.length());
                List<String> topics = topicClassifier.classifyKeywordOnly(userMessage);
                for (String topic : topics) {
                    topicCounts.merge(topic, 1, Integer::sum);
                    totalCount++;
                }
            }

            if (topicCounts.isEmpty()) return List.of();

            // Apply diversity cap and return top N
            final int total = totalCount;
            final int capCount = (int) Math.ceil(diversityCap * total);

            return topicCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .map(e -> Map.entry(e.getKey(), Math.min(e.getValue(), capCount)))
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(maxTopics)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        } catch (Exception e) {
            LOG.warnf("Failed to retrieve interests from PowerMem: %s", e.getMessage());
            return List.of();
        }
    }

    /**
     * Format interests as a prompt hint for Mon's personality.
     */
    public String formatInterestsPrompt(List<String> topicCodes) {
        if (topicCodes == null || topicCodes.isEmpty()) return "";
        List<String> labels = topicCodes.stream()
            .map(code -> TopicClassifier.TOPIC_DISPLAY_NAMES.getOrDefault(code, code))
            .toList();
        return "[SỞ THÍCH CỦA BẠN: " + String.join(", ", labels)
            + " — đề cập tự nhiên khi phù hợp]\n";
    }
}
