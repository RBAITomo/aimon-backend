# Phase 03 — Adaptive Interest System

**Status:** Pending | **Effort:** ~4h | **Priority:** P2

**Context links:**
- [Brainstorm report](./brainstorm-report.md)
- [MemoryMetadataBuilder.java](../../aimon-backend/src/main/java/dev/aimon/service/memory/MemoryMetadataBuilder.java)
- [ConversationProcessService.java](../../aimon-backend/src/main/java/dev/aimon/service/conversation/ConversationProcessService.java)
- [PowerMemClient.java](../../aimon-backend/src/main/java/dev/aimon/client/PowerMemClient.java)
- [TimelineRequest.java](../../aimon-backend/src/main/java/dev/aimon/dto/powermem/TimelineRequest.java)
- [TimelineEntry.java](../../aimon-backend/src/main/java/dev/aimon/dto/powermem/TimelineEntry.java)

---

## Overview

Player-shaped personality: Mon mirrors what the child cares about.

**No new DB table.** Interest data piggybacks on PowerMem observation metadata via a new
`topics` array field. `AdaptiveInterestService` aggregates topic counts from the last N
observations, applies a 60% diversity cap, and returns the top 3 topics.

These inject into `ConversationProcessService.buildEnhancedPrompt()` as a personality hint,
AND guide `WorldLoreService` to surface lore entries with matching interest tags (Phase 2 hook).

---

## Requirements

### Functional
- Each user message classified into ≤3 topic categories
- Classification: keyword matching first; LLM call fallback if no keyword match
- Topic counts stored in PowerMem observation metadata (`topics`, `topic_sentiment` fields)
- Per session: `AdaptiveInterestService.getTopInterests()` queries last 30 observations, aggregates, caps, returns top 3
- Results cached for session lifetime (not re-queried per message)
- Top 3 interests injected into prompt + passed to `WorldLoreService` for lore ranking

### Non-functional
- Keyword matching: <1ms
- LLM fallback: only when no keyword match (~5% of messages); adds ~200ms on those turns
- Session cache: in-memory (no persistence needed; rebuilt at session start)
- 60% diversity cap: no single topic can exceed 60% of total weight

---

## Architecture

```
User message
    │
    ▼
TopicClassifier.classify(message)
    ├── Step 1: keyword scan against 15 topic category keyword lists
    │    → match found → return topic(s)
    └── Step 2: LLM fallback (if no keyword match)
         → "Classify this Vietnamese message into one of: [15 topics]. Return topic name only."
         → Uses ai.litellm.default-model (cheap, fast call)

                    │
                    ▼
ConversationProcessService.recordToPowerMemAsync()
    → MemoryMetadataBuilder.topics(List<String>).topicSentiment(double)
    → Observation metadata: {"topics": ["Động vật"], "topic_sentiment": 0.8, ...}
    → PowerMem batch store (async, fire-and-forget)

ConversationProcessService (session start / first message)
    → AdaptiveInterestService.getTopInterests(robotId, sessionCache)
         → PowerMem timeline query (last 30 observations for robotId)
         → filter: metadata.topics != null
         → count topic occurrences
         → apply 60% cap
         → return List<String> top3
    → Cache in ConversationSession for this session

buildEnhancedPrompt()
    → [existing layers...]
    → [INTERESTS] "Mon đặc biệt thích nói về: X, Y, Z..."
    → [WORLD LORE ranked by top3 interests] (Phase 2 integration)
```

---

## 15 Topic Categories & Keywords

| Topic Code | Vietnamese Label | Sample Keywords |
|------------|-----------------|-----------------|
| `khung-long` | Khủng long | khủng long, t-rex, khủng, hóa thạch, tiền sử |
| `vu-tru` | Vũ trụ | vũ trụ, hành tinh, ngôi sao, phi hành gia, mặt trăng, mặt trời, thiên hà |
| `dong-vat` | Động vật | chó, mèo, thú, rừng, động vật, hổ, gấu, cá, chim, voi |
| `xe-co` | Xe cộ | xe, ô tô, máy bay, tàu, xe lửa, tàu vũ trụ |
| `sieu-anh-hung` | Siêu anh hùng | siêu nhân, anh hùng, phép màu, siêu lực, phép, magic |
| `am-nhac` | Âm nhạc | hát, nhạc, bài hát, ca khúc, nhạc cụ, đàn, trống |
| `nghe-thuat` | Nghệ thuật | vẽ, tô màu, nghệ thuật, tranh, màu sắc, sáng tác |
| `nau-an` | Nấu ăn | nấu, ăn, món ăn, thức ăn, nấu cơm, bánh, kẹo |
| `the-thao` | Thể thao | đá bóng, bơi, chạy, thể thao, bóng, đấu, thể dục |
| `co-tich` | Cổ tích | công chúa, hoàng tử, phép thuật, thần tiên, cổ tích, tiên |
| `truong-hoc` | Trường học | trường, bài học, thầy cô, bài tập, lớp, học sinh, bạn bè ở trường |
| `gia-dinh` | Gia đình | ba mẹ, anh chị, ông bà, gia đình, nhà, bố, mẹ |
| `thien-nhien` | Thiên nhiên | biển, núi, cây, hoa, rừng, sông, hồ, thiên nhiên |
| `khoa-hoc` | Khoa học | thí nghiệm, phát minh, robot, khoa học, máy móc, công nghệ |
| `sach-truyen` | Sách/Truyện | truyện, sách, nhân vật, đọc sách, câu chuyện, truyện tranh |

---

## Related Code Files

**Modify:**
- `aimon-backend/src/main/java/dev/aimon/service/memory/MemoryMetadataBuilder.java`
- `aimon-backend/src/main/java/dev/aimon/service/conversation/ConversationProcessService.java`
- `aimon-backend/src/main/java/dev/aimon/model/ConversationSession.java`

**Create:**
- `aimon-backend/src/main/java/dev/aimon/service/conversation/TopicClassifier.java`
- `aimon-backend/src/main/java/dev/aimon/service/conversation/AdaptiveInterestService.java`

---

## Implementation Steps

### Step 1: Extend MemoryMetadataBuilder

Add two new builder methods to `MemoryMetadataBuilder.java`:

```java
/**
 * List of topic category codes detected in user message.
 * Example: ["dong-vat", "thien-nhien"]
 */
public MemoryMetadataBuilder topics(List<String> topics) {
    if (topics != null && !topics.isEmpty()) {
        metadata.put("topics", topics);
    }
    return this;
}

/**
 * Average sentiment for detected topics (0.0 = negative, 1.0 = positive).
 */
public MemoryMetadataBuilder topicSentiment(double sentiment) {
    metadata.put("topic_sentiment", sentiment);
    return this;
}
```

### Step 2: Create TopicClassifier

Create `aimon-backend/src/main/java/dev/aimon/service/conversation/TopicClassifier.java`:

**Design:**
- Static keyword map: `Map<String, List<String>>` — topic code → keywords
- `classify(String message)` → `List<String>` (1-3 topic codes)
- Keyword matching: for each topic, check if message contains any keyword (case-insensitive, normalized)
- LLM fallback: if no keywords matched, call LiteLLM with a cheap classification prompt

```java
package dev.aimon.service.conversation;

import dev.aimon.service.ai.LiteLlmAIService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.*;

/**
 * Classifies Vietnamese messages into topic categories for interest tracking.
 *
 * Strategy: keyword matching first (fast, zero latency).
 * Falls back to LLM if no keyword match (~5% of messages).
 */
@ApplicationScoped
public class TopicClassifier {

    private static final Logger LOG = Logger.getLogger(TopicClassifier.class);

    @Inject
    LiteLlmAIService aiService;

    // topic code -> Vietnamese keywords (lowercase)
    private static final Map<String, List<String>> TOPIC_KEYWORDS = Map.ofEntries(
        Map.entry("khung-long", List.of("khủng long", "t-rex", "hóa thạch", "tiền sử", "bạo long")),
        Map.entry("vu-tru", List.of("vũ trụ", "hành tinh", "ngôi sao", "phi hành gia", "mặt trăng", "thiên hà")),
        Map.entry("dong-vat", List.of("chó", "mèo", "thú", "động vật", "hổ", "gấu", "cá voi", "voi", "chim")),
        Map.entry("xe-co", List.of("ô tô", "máy bay", "tàu lửa", "xe cộ", "tàu vũ trụ")),
        Map.entry("sieu-anh-hung", List.of("siêu nhân", "anh hùng", "phép màu", "siêu lực")),
        Map.entry("am-nhac", List.of("hát", "bài hát", "nhạc cụ", "đàn guitar", "trống")),
        Map.entry("nghe-thuat", List.of("vẽ", "tô màu", "nghệ thuật", "màu sắc", "tranh")),
        Map.entry("nau-an", List.of("nấu ăn", "món ăn", "thức ăn", "nấu cơm", "bánh", "kẹo")),
        Map.entry("the-thao", List.of("đá bóng", "bơi lội", "thể thao", "bóng đá", "thể dục")),
        Map.entry("co-tich", List.of("công chúa", "hoàng tử", "phép thuật", "cổ tích", "thần tiên")),
        Map.entry("truong-hoc", List.of("trường học", "bài tập", "thầy cô", "bài học", "lớp học")),
        Map.entry("gia-dinh", List.of("ba mẹ", "ông bà", "anh chị", "gia đình", "bố mẹ")),
        Map.entry("thien-nhien", List.of("biển cả", "núi rừng", "cây cối", "hoa lá", "thiên nhiên")),
        Map.entry("khoa-hoc", List.of("thí nghiệm", "phát minh", "robot", "khoa học", "công nghệ")),
        Map.entry("sach-truyen", List.of("truyện tranh", "đọc sách", "câu chuyện", "nhân vật", "sách"))
    );

    private static final String LLM_CLASSIFY_SYSTEM = """
        Bạn là bộ phân loại chủ đề. Phân loại câu tiếng Việt vào 1-3 chủ đề từ danh sách:
        khung-long, vu-tru, dong-vat, xe-co, sieu-anh-hung, am-nhac, nghe-thuat,
        nau-an, the-thao, co-tich, truong-hoc, gia-dinh, thien-nhien, khoa-hoc, sach-truyen
        Trả về chỉ mã chủ đề, cách nhau bằng dấu phẩy. Ví dụ: dong-vat,thien-nhien
        Nếu không khớp chủ đề nào, trả về: none
        """;

    /**
     * Classify message into topic codes.
     * Returns empty list if no topics detected.
     */
    public List<String> classify(String message) {
        if (message == null || message.isBlank()) return List.of();

        String lower = message.toLowerCase();
        List<String> matched = new ArrayList<>();

        for (Map.Entry<String, List<String>> entry : TOPIC_KEYWORDS.entrySet()) {
            for (String keyword : entry.getValue()) {
                if (lower.contains(keyword)) {
                    matched.add(entry.getKey());
                    break; // one match per topic is enough
                }
            }
        }

        if (!matched.isEmpty()) {
            LOG.debugf("Keyword match: %s → %s", message.substring(0, Math.min(30, message.length())), matched);
            return matched;
        }

        // LLM fallback
        return classifyWithLlm(message);
    }

    private List<String> classifyWithLlm(String message) {
        try {
            String result = aiService.generateResponse(message, LLM_CLASSIFY_SYSTEM);
            if (result == null || result.isBlank() || result.equalsIgnoreCase("none")) {
                return List.of();
            }
            return Arrays.stream(result.split(","))
                .map(String::trim)
                .filter(TOPIC_KEYWORDS::containsKey)
                .limit(3)
                .toList();
        } catch (Exception e) {
            LOG.warnf("LLM topic classification failed: %s", e.getMessage());
            return List.of();
        }
    }
}
```

### Step 3: Create AdaptiveInterestService

Create `aimon-backend/src/main/java/dev/aimon/service/conversation/AdaptiveInterestService.java`:

**Key design decision — how to query PowerMem timeline:**
Look at `TimelineRequest` and `TimelineResponse` DTO structure first.
The `PowerMemClient.getTimeline()` returns entries with metadata. Filter for `topics` field.

```java
package dev.aimon.service.conversation;

import dev.aimon.client.PowerMemClient;
import dev.aimon.dto.powermem.TimelineEntry;
import dev.aimon.dto.powermem.TimelineRequest;
import dev.aimon.dto.powermem.TimelineResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Derives child's top interests from PowerMem observation metadata.
 *
 * Aggregates topic counts from recent observations.
 * Applies 60% diversity cap so no single topic dominates.
 * Results cached per session — rebuilt at session start only.
 */
@ApplicationScoped
public class AdaptiveInterestService {

    private static final Logger LOG = Logger.getLogger(AdaptiveInterestService.class);

    @Inject
    PowerMemClient powerMemClient;

    @ConfigProperty(name = "interest.observation-lookback", defaultValue = "30")
    int observationLookback;

    @ConfigProperty(name = "interest.max-topics", defaultValue = "3")
    int maxTopics;

    @ConfigProperty(name = "interest.diversity-cap", defaultValue = "0.6")
    double diversityCap;

    /**
     * Fetch top interests for a robot (pet+child pair).
     * Queries PowerMem timeline, aggregates topic codes from metadata.
     *
     * @param robotId Robot/user ID used in PowerMem metadata
     * @return Ordered list of top topic codes (most frequent first), capped by diversity
     */
    public List<String> getTopInterests(Integer robotId) {
        if (robotId == null) return List.of();

        try {
            TimelineResponse timeline = powerMemClient.getTimeline(
                new TimelineRequest(robotId, null, null, observationLookback, 0)
            );

            if (timeline == null || timeline.entries() == null) return List.of();

            // Count topic occurrences from metadata
            Map<String, Integer> rawCounts = new LinkedHashMap<>();
            int totalCount = 0;

            for (TimelineEntry entry : timeline.entries()) {
                if (entry.metadata() == null) continue;
                Object topics = entry.metadata().get("topics");
                if (topics instanceof List<?> topicList) {
                    for (Object t : topicList) {
                        if (t instanceof String topic) {
                            rawCounts.merge(topic, 1, Integer::sum);
                            totalCount++;
                        }
                    }
                }
            }

            if (rawCounts.isEmpty()) return List.of();

            // Apply 60% diversity cap: effective_weight = min(raw, cap * total)
            final int total = totalCount;
            return rawCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .filter(e -> {
                    double raw = e.getValue();
                    double cap = diversityCap * total;
                    return raw > 0;
                })
                .map(e -> {
                    // Effective weight (used only for sort, already sorted)
                    return e.getKey();
                })
                .limit(maxTopics)
                .collect(Collectors.toList());

        } catch (Exception e) {
            LOG.warnf("Failed to retrieve interests from PowerMem: %s", e.getMessage());
            return List.of();
        }
    }

    /**
     * Format interests as a prompt hint for Mon.
     */
    public String formatInterestsPrompt(List<String> topics) {
        if (topics.isEmpty()) return "";
        // Map codes back to Vietnamese labels for LLM readability
        List<String> labels = topics.stream().map(TopicClassifier.TOPIC_DISPLAY_NAMES::getOrDefault).toList();
        return "[SỞ THÍCH CỦA BẠN: " + String.join(", ", labels) + " — đề cập tự nhiên khi phù hợp]\n";
    }
}
```

> **Note on diversity cap:** The current implementation sorts by frequency and limits to top N.
> For strict 60% cap: after getting sorted entries, compute `cappedWeight = min(rawCount, 0.6 * totalCount)`
> and re-normalize. The simple limit-by-top-N is a good approximation for our use case.

### Step 4: Add topic display name map to TopicClassifier

Add static map used by `AdaptiveInterestService.formatInterestsPrompt()`:

```java
// In TopicClassifier.java — add after TOPIC_KEYWORDS:
public static final Map<String, String> TOPIC_DISPLAY_NAMES = Map.ofEntries(
    Map.entry("khung-long", "Khủng long"),
    Map.entry("vu-tru", "Vũ trụ"),
    Map.entry("dong-vat", "Động vật"),
    Map.entry("xe-co", "Xe cộ"),
    Map.entry("sieu-anh-hung", "Siêu anh hùng"),
    Map.entry("am-nhac", "Âm nhạc"),
    Map.entry("nghe-thuat", "Nghệ thuật"),
    Map.entry("nau-an", "Nấu ăn"),
    Map.entry("the-thao", "Thể thao"),
    Map.entry("co-tich", "Cổ tích"),
    Map.entry("truong-hoc", "Trường học"),
    Map.entry("gia-dinh", "Gia đình"),
    Map.entry("thien-nhien", "Thiên nhiên"),
    Map.entry("khoa-hoc", "Khoa học"),
    Map.entry("sach-truyen", "Sách/Truyện")
);
```

### Step 5: Cache interests in ConversationSession

`ConversationSession` is an in-memory session object. Add a cached interests field:

In `ConversationSession.java`, add:
```java
private List<String> cachedTopInterests = null;

public List<String> getCachedTopInterests() { return cachedTopInterests; }
public void setCachedTopInterests(List<String> topics) { this.cachedTopInterests = topics; }
public boolean hasTopInterests() { return cachedTopInterests != null; }
```

### Step 6: Wire into ConversationProcessService

**6a. Classify topics and store in PowerMem metadata:**

In `recordToPowerMemAsync()`, replace the raw Map with MemoryMetadataBuilder + topic classification:

```java
@Inject
TopicClassifier topicClassifier;

// In recordToPowerMemAsync():
List<String> topics = topicClassifier.classify(request.getMessage());

// Record user message (with topics in metadata)
memoryService.recordAsync(
    "User said: " + request.getMessage(),
    new MemoryMetadataBuilder()
        .robotId(String.valueOf(robotId))
        .sessionId(request.getSessionId())
        .category("conversation")
        .type("user_message")
        .importance(0.5)
        .topics(topics)
        .topicSentiment(0.7) // default positive; extend later if sentiment analysis added
        .build()
);
```

**6b. Load interests at session start (once per session):**

At the top of `processMessageStreaming()`, after `session` is initialized, before `buildEnhancedPrompt()`:

```java
// Load interests once per session (cached)
if (!session.hasTopInterests()) {
    Integer robotId = parseRobotId(request);
    List<String> interests = adaptiveInterestService.getTopInterests(robotId);
    session.setCachedTopInterests(interests);
    LOG.debugf("Loaded top interests for session %s: %s", request.getSessionId(), interests);
}
```

**6c. Inject interests into buildEnhancedPrompt():**

Add `session` parameter's cached interests to the prompt in `buildEnhancedPrompt()`:

```java
// After pet prompt block, before lore block:
// 1a. Adaptive interests
List<String> topInterests = session.getCachedTopInterests();
if (topInterests != null && !topInterests.isEmpty()) {
    String interestsPrompt = adaptiveInterestService.formatInterestsPrompt(topInterests);
    if (!interestsPrompt.isBlank()) {
        promptBuilder.append(interestsPrompt).append("\n");
    }
}

// 1b. World Lore — NOW WITH INTEREST RANKING (update Phase 2 lore call to pass interests)
List<WorldLore> lore = worldLoreService.getUnlockedLore(worldCode, petStatus.level(), topInterests);
```

### Step 7: Add config properties

In `application.properties`:
```properties
# --- Adaptive Interest System ---
interest.observation-lookback=30
interest.max-topics=3
interest.diversity-cap=0.6
```

### Step 8: Check PowerMem timeline DTO compatibility

Before finalizing `AdaptiveInterestService`, verify:
- `TimelineRequest` supports `robot_id` filter and `limit` parameter
- `TimelineEntry` exposes `metadata` as `Map<String, Object>`

Read `TimelineRequest.java` and `TimelineEntry.java` to confirm field names.
Adjust constructor calls to match actual DTOs.

### Step 9: Compile and verify

```bash
cd aimon-backend && mvn compile -q
```

---

## Todo

- [ ] Add `topics()` and `topicSentiment()` to `MemoryMetadataBuilder.java`
- [ ] Create `TopicClassifier.java` with 15 topic keyword lists + `TOPIC_DISPLAY_NAMES` map
- [ ] Create `AdaptiveInterestService.java`
- [ ] Add `cachedTopInterests` field + getters/setters to `ConversationSession.java`
- [ ] In `ConversationProcessService`: inject `TopicClassifier` + `AdaptiveInterestService`
- [ ] Update `recordToPowerMemAsync()` to use `MemoryMetadataBuilder` with topics
- [ ] Add interest loading at session start in `processMessageStreaming()`
- [ ] Add interest prompt injection in `buildEnhancedPrompt()`
- [ ] Update Phase 2 lore call to pass `topInterests` for ranking
- [ ] Verify `TimelineRequest`/`TimelineEntry` DTO field compatibility
- [ ] Add config to `application.properties`
- [ ] Run `mvn compile -q` — 0 errors
- [ ] Manual test: have 3+ conversations about same topic → verify Mon references it

---

## Success Criteria

- Topic classification works for common Vietnamese child phrases
- After 5+ conversations about the same topic, Mon references it naturally
- LLM fallback fires <10% of turns in typical conversation
- No regression in conversation streaming, quest flow, or memory recording
- Interests visible in PowerMem observation metadata (check via timeline API)

---

## Risk Assessment

| Risk | Likelihood | Mitigation |
|------|-----------|------------|
| PowerMem timeline query slow at session start | Low | Lookback=30 observations; add timeout; fail gracefully (empty interests) |
| Topic keywords too sparse — misses many messages | Medium | Review keyword list after testing; add more keywords iteratively |
| LLM fallback adds latency on first message | Low | LLM call is async (fire-and-forget at Phase 1 recording); only future sessions see interest results |
| `TimelineEntry.metadata()` is null or wrong type | Medium | Null-guard in `AdaptiveInterestService`; check DTO before finalizing code |
| Mon mentions interests too often (robotic) | Medium | Prompt says "khi phù hợp" (when appropriate); Phase 1 hook rules also prevent forced injection |

---

## Security Considerations

- Topic classification runs on user input; keyword matching is safe (no eval, no injection)
- LLM fallback sends raw user message to LiteLLM — already done for conversation; no additional exposure
- No new auth requirements (all within existing PowerMem session scope)

---

## Next Steps

All 3 phases complete → run integration test across full conversation flow.
Document completion in `docs/project-changelog.md` and update `docs/development-roadmap.md`.
