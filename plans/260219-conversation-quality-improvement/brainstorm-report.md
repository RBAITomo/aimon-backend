# Brainstorm Report: AI-MON Conversation Quality Improvement
**Date:** 2026-02-19
**Status:** Agreed — Ready for Implementation Planning

---

## Problem Statement

Current AI-MON conversation feels like Q&A: user speaks, Mon answers, conversation stops.
Three root causes identified:

1. **Flat responses** — Prompt gives no structural guidance on conversation hooks; LLM defaults to answer-and-stop
2. **Generic personality** — Mon has moods/stages but no personal obsessions, verbal quirks, or things it owns to talk about
3. **Hollow world** — Cotton Land is a label without substance; Mon never shares specific stories or knowledge about its world

---

## Evaluated Approaches

### Option A: Prompt Engineering Only
- Enhance `BASE_RULES` with hook mechanics in `PetPromptAssembler`
- **Verdict:** Necessary but insufficient alone. Without real content to hook with, Mon still sounds generic.

### Option B: Adaptive Interest Tracker
- Track child's topic interests and mirror them; diversity-capped
- **Verdict:** High value for personality. Refactored into memoryService observation metadata — no new DB table.

### Option C: World Lore System (Level-Gated, Multi-World Ready)
- DB-backed world facts unlocked by level + interest matching; designed for multiple worlds
- **Verdict:** Biggest personality leap. Clear content strategy, leverages existing pgvector.

### Final Decision: All Three, Sequentially
A → C → B. Hooks work best when there's actual world content to hook with.

---

## Final Agreed Architecture

### Layer 1: Conversation Hook Engine (Prompt Fix)

**Change:** Add `CONVERSATION_DYNAMICS` section to `PetPromptAssembler`.

Five hook types (injected as prompt rules):
| Hook | Vietnamese Example |
|------|-------------------|
| **Wonder** | "Mình tự hỏi liệu X có..." |
| **World Share** | "Ở Cotton Land, mình từng thấy..." |
| **Callback** | "Lần trước bạn kể về X, bây giờ X thế nào rồi?" |
| **Story Invite** | "Điều đó làm mình nhớ đến câu chuyện về..." |
| **Curiosity Follow** | "Bạn đã bao giờ thử... chưa?" |

Stage-specific enforcement:
- **BABY:** Simple wonder + curiosity follow only
- **CHILD:** All 5 hooks, story-heavy
- **ADULT:** Deep callbacks + philosophical wonder

Soft constraint: "Khi tự nhiên và phù hợp, kết thúc bằng 1 câu hỏi hoặc gợi mở"
(Avoids forced/robotic hook insertion)

---

### Layer 2: World Lore System (Multi-World Ready)

**New DB table** — designed to support multiple worlds (Cotton Land is just the first):
```sql
CREATE TABLE world_lore (
    id            BIGSERIAL PRIMARY KEY,
    world_code    VARCHAR(50)  NOT NULL,   -- e.g. "COTTON_LAND", "SKY_REALM", "DEEP_OCEAN"
    title         VARCHAR(100) NOT NULL,
    category      VARCHAR(50)  NOT NULL,   -- place | character | tradition | food | mystery
    content       TEXT         NOT NULL,   -- 2-3 sentences, 1st person Mon perspective
    min_level     INT          NOT NULL DEFAULT 3,
    interest_tags TEXT[]                   -- topic keywords for interest-matched surfacing
    is_active     BOOLEAN      DEFAULT TRUE
);
```

**New service:** `WorldLoreService`
- Fetch unlocked lore: `world_code = activePetWorld AND min_level <= pet.level`
- Rank by interest tag overlap with child's current top interests
- Inject top 3 entries into `PetPromptAssembler` as *"Thế giới của Mon — điều Mon có thể tự nhiên kể"*
- Future-ready: as new worlds unlock at higher levels, swap `world_code` in query

**Content plan (30 entries, Cotton Land, manually authored):**

| Level | Category | Examples |
|-------|----------|---------|
| 3–4 (BABY hatches) | place, food, ability | Nhà Bông, kẹo sương, Mon đổi màu theo cảm xúc |
| 5–6 | character, place | Gió (best friend), Suối Bông, Chợ Bông |
| 7–9 (CHILD) | tradition, mystery | Hội Bông Bay festival, chỗ trốn bí mật của Mon |
| 10–11 | mystery, character | Memory Cave, Bà Mây (Grandma Cloud) |
| 12+ (ADULT) | history, philosophy | Lịch sử Cotton Land, Đỉnh Mây, Edge of Cotton Land |

**World Canon: Cotton Land** (reference for lore writing):
- Cloud-cotton world floating above the sky
- Mon's home: cozy cotton pod near "Suối Bông" (Cotton Stream)
- Mon's ability: changes body color with emotion (white=calm, pink=happy, blue=sad, yellow=excited)
- Best friend: Gió (the Wind) — hyperactive, loves playing pranks
- Wise elder: Bà Mây (Grandma Cloud) — ancient, knows Cotton Land's origin
- Food: kẹo sương (mist candy), bánh bông (cotton cake), nước suối ngọt (sweet stream water)
- Seasonal event: Hội Bông Bay (Flying Cotton Festival) every spring
- Places: Vườn Cầu Vồng (Rainbow Garden), Rừng Bông (Cotton Forest), Đỉnh Mây (Cloud Peak)
- Mystery: The Edge of Cotton Land — no one knows what's beyond it

---

### Layer 3: Adaptive Interest System (via memoryService Metadata)

**No new DB table.** Interest data lives inside existing PowerMem observation metadata.

**Current observation metadata schema** (`MemoryMetadataBuilder.java`):
```json
{
  "timestamp": "2026-02-19T10:00:00Z",
  "robot_id": "...",
  "user_id": "...",
  "session_id": "...",
  "category": "conversation",
  "importance": 0.7
}
```

**Refactored metadata — add `topics` array:**
```json
{
  "timestamp": "2026-02-19T10:00:00Z",
  "robot_id": "...",
  "user_id": "...",
  "session_id": "...",
  "category": "conversation",
  "importance": 0.7,
  "topics": ["khủng long", "vũ trụ"],
  "topic_sentiment": 0.85
}
```

**Changes required:**
1. `MemoryMetadataBuilder.java` — add `topics(List<String> topics)` and `topicSentiment(double s)` builder methods
2. `ConversationProcessService.java` — before recording observation, classify user message into topic categories, attach to metadata
3. New `AdaptiveInterestService.java` — queries PowerMem timeline, aggregates `topics` from metadata across recent observations

**Topic classification (15 categories):**
| Vietnamese Label | English | Keywords (sample) |
|------------------|---------|-------------------|
| Khủng long | Dinosaurs | khủng long, T-Rex, hóa thạch |
| Vũ trụ | Space | vũ trụ, hành tinh, ngôi sao, phi hành gia |
| Động vật | Animals | chó, mèo, thú, rừng |
| Xe cộ | Vehicles | xe, ô tô, máy bay, tàu |
| Siêu anh hùng | Superheroes | siêu nhân, anh hùng, phép màu |
| Âm nhạc | Music | hát, nhạc, bài hát |
| Vẽ/Nghệ thuật | Art | vẽ, tô màu, nghệ thuật |
| Nấu ăn | Cooking | nấu, ăn, món ăn |
| Thể thao | Sports | đá bóng, bơi, chạy |
| Công chúa/Hoàng tử | Fantasy | công chúa, hoàng tử, phép thuật |
| Trường học | School | trường, bài học, thầy cô |
| Gia đình | Family | ba mẹ, anh chị, ông bà |
| Thiên nhiên | Nature | biển, núi, cây, hoa |
| Khoa học | Science | thí nghiệm, phát minh, robot |
| Sách/Truyện | Stories | truyện, sách, nhân vật |

**Diversity enforcement:**
No single topic can exceed 60% of total accumulated weight.
Formula: `effective_weight = min(raw_count, 0.6 * total_count)` across aggregated observations.
Ensures Mon always shows range even if child fixates.

**Prompt injection:**
Top 3 interests → *"Mon đặc biệt thích nói về: [topic1], [topic2], [topic3]. Tự nhiên đề cập đến khi phù hợp."*

**Data flow:**
```
User message → ConversationProcessService
  → Keyword classification → topics: ["khủng long"]
  → MemoryMetadataBuilder.topics([...]).topicSentiment(0.8)
  → ObservationBuffer → PowerMem (async, fire-and-forget)

Session start → AdaptiveInterestService.getTopInterests(petId, userId)
  → PowerMem timeline query (last 30 observations)
  → Aggregate topics from metadata
  → Apply 60% cap
  → Return top 3 → PetPromptAssembler injection
```

**Benefit:** Interest data is co-located with conversation memory — query PowerMem once to get both context AND interests.

---

## Implementation Sequence

```
Phase 1 (Quick Win):     Prompt hooks in PetPromptAssembler
Phase 2 (World Depth):   world_lore table + WorldLoreService + 30 Cotton Land entries + canon doc
Phase 3 (Personality):   MemoryMetadataBuilder refactor + AdaptiveInterestService + prompt injection
```

---

## Key Risks & Mitigations

| Risk | Mitigation |
|------|-----------|
| Token budget creep | Cap lore injection at 3 entries; interest injection at 3 topics |
| LLM ignores hook rules | Test with GPT-4.1-mini; if needed, strengthen enforcement |
| World inconsistency | Manual authorship + canon reference doc (`docs/cotton-land-canon.md`) |
| Interest reinforcement loop | 60% diversity cap prevents unhealthy single-topic fixation |
| PowerMem timeline query latency at session start | Cache interests per session; refresh once per session not per message |
| Multiple world scalability | `world_code` field + `pet_active_world` field on `PetProfile` |

---

## Success Metrics

- Child naturally continues conversation without prompting (test with target age group)
- Mon references child's interests unprompted ≥1x per 5 conversations
- Each level-up unlocks ≥2 new lore entries the child hasn't heard before
- Response ends with a hook in ≥60% of non-quest turns

---

## Files to Modify

| File | Change |
|------|--------|
| `PetPromptAssembler.java` | Add `CONVERSATION_DYNAMICS`, lore injection, interest injection |
| `ConversationProcessService.java` | Classify user message topics; add to observation metadata; call interest service at session start |
| `MemoryMetadataBuilder.java` | Add `topics(List<String>)` and `topicSentiment(double)` builder methods |
| `application.properties` | Config: max lore entries injected, max interests shown, topic classification method |

## Files to Create

| File | Purpose |
|------|---------|
| `WorldLore.java` (entity) | DB entity for `world_lore` table |
| `WorldLoreRepository.java` | JPA repo with level + world_code + interest tag queries |
| `WorldLoreService.java` | Fetch, rank by interest tags, inject into prompt |
| `AdaptiveInterestService.java` | Query PowerMem, aggregate topics, apply diversity cap |
| `V{n}__create_world_lore_table.sql` (Flyway) | Migration for `world_lore` table |
| `world_lore_cotton_land_seed.sql` | Initial 30 Cotton Land lore entries |
| `docs/cotton-land-canon.md` | World canon reference for consistent lore authorship |

---

## Unresolved Questions
- Does 30 lore entries cover the full level range (3–15) adequately, or plan for 50?
- Interest classification: keyword matching (simple, fast, no latency) vs. LLM classification (more accurate, adds ~200ms per turn)?
- Should `pet_active_world` be stored on `PetProfile` or derived from level range? (Needed for multi-world)
