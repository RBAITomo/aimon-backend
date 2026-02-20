# Phase 02 — World Lore System

**Status:** Pending | **Effort:** ~6h | **Priority:** P1

**Context links:**
- [Brainstorm report](./brainstorm-report.md)
- [Cotton Land Canon](../../docs/cotton-land-canon.md) ← create in step 1
- [V4__quest_system.sql](../../aimon-backend/src/main/resources/db/migration/V4__quest_system.sql) (pattern reference)
- [PetProfile.java](../../aimon-backend/src/main/java/dev/aimon/entity/pet/PetProfile.java)

---

## Overview

Gives Mon a real world to talk about. `world_lore` table stores Cotton Land facts unlocked
by pet level. `WorldLoreService` fetches + ranks by interest tags. Top 3 facts injected into
`ConversationProcessService.buildEnhancedPrompt()` as a new prompt layer.

System is multi-world ready: `world_code` on each row + `active_world` on `PetProfile`
means future worlds (Sky Realm, Ocean Depths) require no schema changes.

---

## Requirements

### Functional
- Lore entries gated by `min_level ≤ pet.level AND world_code = pet.active_world`
- Interest tag matching: entries with tags overlapping child's top interests surface first
- Inject top 3 entries per conversation (configurable)
- Default world = "COTTON_LAND" for all existing pets
- Lore prompt injected between pet personality section and quest/kid-mode sections

### Non-functional
- Query fast: single indexed SQL query, no N+1
- File limit: each new Java file ≤ 200 lines
- Lore content is Vietnamese, 1st-person Mon perspective, 2-3 sentences per entry

---

## Architecture

```
ConversationProcessService.buildEnhancedPrompt()
  │
  ├─ 1. petPromptAssembler.buildPetSystemPrompt()   ← personality + hooks
  ├─ 2. worldLoreService.getLorePrompt(...)          ← NEW: Cotton Land facts
  ├─ 3. questPrompt (existing)
  ├─ 4. kidMode (existing)
  ├─ 5. PowerMem context (existing)
  ├─ 6. session history (existing)
  └─ 7. current message (existing)

WorldLoreService
  ├─ getUnlockedLore(worldCode, petLevel, topInterestTags)
  │    → SELECT * FROM world_lore WHERE world_code=? AND min_level<=? AND is_active=true
  │    → Sort by interest_tag_overlap DESC, then RANDOM()
  │    → LIMIT max_lore_entries (default 3)
  └─ formatLorePrompt(List<WorldLore>)
       → "=== Thế giới của Mon ===\n{content}\n..."
```

---

## Related Code Files

**Modify:**
- `aimon-backend/src/main/java/dev/aimon/entity/pet/PetProfile.java` — add `activeWorld` field
- `aimon-backend/src/main/java/dev/aimon/service/conversation/ConversationProcessService.java` — inject lore layer
- `aimon-backend/src/main/resources/application.properties` — add lore config

**Create:**
- `aimon-backend/src/main/resources/db/migration/V5__world_lore_system.sql`
- `aimon-backend/src/main/java/dev/aimon/entity/world/WorldLore.java`
- `aimon-backend/src/main/java/dev/aimon/repository/WorldLoreRepository.java`
- `aimon-backend/src/main/java/dev/aimon/service/world/WorldLoreService.java`
- `aimon-backend/src/main/resources/db/seed/world_lore_cotton_land_seed.sql`
- `docs/cotton-land-canon.md`

---

## Implementation Steps

### Step 1: Write Cotton Land Canon doc

Create `docs/cotton-land-canon.md` — the ground truth for lore authorship.
Content to include (world bible):

```markdown
# Cotton Land Canon

## World
- Cloud-cotton world floating above the sky
- Soft, fluffy aesthetic — everything is made of cotton or cloud material
- Colors shift with emotions of the creatures living there

## Mon's Life
- Home: cozy cotton pod near "Suối Bông" (Cotton Stream)
- Ability: Mon's fur changes color with emotion
  white=calm, pink=happy, blue=sad, yellow=excited, orange=hungry, grey=sleepy
- Favourite food: kẹo sương (mist candy), bánh bông (cotton cake)
- Favourite spot: the Learning Tree at the edge of the Rainbow Garden

## Key Characters
- **Gió** (best friend, the Wind): hyperactive, loves pranks, blows things everywhere
- **Bà Mây** (Grandma Cloud): ancient, soft-spoken, knows all of Cotton Land's secrets
- **Ánh** (Sunshine Ray): warm, cheerful, visits Cotton Land every morning

## Places
- **Nhà Bông**: Mon's home — small, cozy, smells like fresh cotton
- **Suối Bông**: cotton stream with glittery water, Mon drinks here every morning
- **Chợ Bông**: weekly market — creatures sell cloud-fruits, woven cotton, mist candy
- **Vườn Cầu Vồng**: Rainbow Garden — Mon's favourite walking spot
- **Rừng Bông**: Cotton Forest — where new cotton creatures are born from cotton pods
- **Đỉnh Mây**: Cloud Peak — highest point, Mon dreams of reaching it one day
- **Hang Ký Ức**: Memory Cave — walls store echoes of past events

## Traditions
- **Hội Bông Bay**: Flying Cotton Festival each spring — cotton creatures float up and ride wind currents
- Morning greeting: all creatures hum a tune when sun rises
- Rainy season: Cotton Land becomes extra soft and quiet

## Mysteries
- The Edge of Cotton Land — no one has seen what lies beyond
- Bà Mây's age — she can't remember how old she is
- Why some cotton pods glow at night
```

### Step 2: Flyway migration V5

Create `V5__world_lore_system.sql`:

```sql
SET search_path TO aimon, public;

-- Multi-world lore table
CREATE TABLE IF NOT EXISTS world_lore (
    id            BIGSERIAL PRIMARY KEY,
    world_code    VARCHAR(50)  NOT NULL,
    title         VARCHAR(100) NOT NULL,
    category      VARCHAR(50)  NOT NULL,  -- place | character | tradition | food | mystery | ability
    content       TEXT         NOT NULL,
    min_level     INT          NOT NULL DEFAULT 3,
    interest_tags TEXT[]       NOT NULL DEFAULT '{}',
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ           DEFAULT now()
);

CREATE INDEX idx_world_lore_lookup
    ON world_lore(world_code, min_level, is_active);

-- Add active_world to pet_profiles
ALTER TABLE pet_profiles
    ADD COLUMN IF NOT EXISTS active_world VARCHAR(50) NOT NULL DEFAULT 'COTTON_LAND';
```

### Step 3: WorldLore entity

Create `aimon-backend/src/main/java/dev/aimon/entity/world/WorldLore.java`:

```java
package dev.aimon.entity.world;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "world_lore", schema = "aimon")
public class WorldLore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "world_code", length = 50, nullable = false)
    private String worldCode;

    @Column(length = 100, nullable = false)
    private String title;

    @Column(length = 50, nullable = false)
    private String category; // place | character | tradition | food | mystery | ability

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(name = "min_level", nullable = false)
    private Integer minLevel = 3;

    @Column(name = "interest_tags", columnDefinition = "TEXT[]")
    private String[] interestTags = new String[0];

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "created_at")
    private Instant createdAt;

    // Standard getters/setters (no Lombok to match project style)
    // ... (implementer adds all getters/setters)
}
```

### Step 4: WorldLoreRepository

Create `aimon-backend/src/main/java/dev/aimon/repository/WorldLoreRepository.java`:

```java
package dev.aimon.repository;

import dev.aimon.entity.world.WorldLore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.util.List;

@ApplicationScoped
public class WorldLoreRepository {

    @Inject
    EntityManager em;

    /**
     * Fetch all active unlocked entries for given world and level.
     * Caller handles interest ranking and limiting.
     */
    public List<WorldLore> findUnlocked(String worldCode, int petLevel) {
        return em.createQuery(
            "SELECT w FROM WorldLore w " +
            "WHERE w.worldCode = :worldCode " +
            "AND w.minLevel <= :level " +
            "AND w.isActive = true " +
            "ORDER BY RANDOM()",
            WorldLore.class
        )
        .setParameter("worldCode", worldCode)
        .setParameter("level", petLevel)
        .getResultList();
    }
}
```

> **Note on RANDOM():** PostgreSQL supports `ORDER BY RANDOM()`. Quarkus + Hibernate with PostgreSQL
> dialect supports this natively. Alternatively use `function('random')` for portability.

### Step 5: WorldLoreService

Create `aimon-backend/src/main/java/dev/aimon/service/world/WorldLoreService.java`:

```java
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

@ApplicationScoped
public class WorldLoreService {

    private static final Logger LOG = Logger.getLogger(WorldLoreService.class);

    @Inject
    WorldLoreRepository repository;

    @ConfigProperty(name = "world.lore.max-inject", defaultValue = "3")
    int maxLoreEntries;

    /**
     * Fetch unlocked lore entries, ranked by interest tag overlap.
     * Returns top maxLoreEntries entries.
     */
    @Transactional(Transactional.TxType.SUPPORTS)
    public List<WorldLore> getUnlockedLore(String worldCode, int petLevel, List<String> topInterests) {
        List<WorldLore> all = repository.findUnlocked(worldCode, petLevel);
        if (all.isEmpty()) return List.of();

        if (topInterests == null || topInterests.isEmpty()) {
            // No interests — return random selection
            return all.stream().limit(maxLoreEntries).collect(Collectors.toList());
        }

        // Score each entry by interest tag overlap
        Set<String> interestSet = new HashSet<>(topInterests);
        return all.stream()
            .sorted(Comparator.comparingInt(
                (WorldLore w) -> interestOverlap(w.getInterestTags(), interestSet)
            ).reversed())
            .limit(maxLoreEntries)
            .collect(Collectors.toList());
    }

    /**
     * Format lore entries as prompt section.
     * Returns empty string if no entries.
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
```

### Step 6: Update PetProfile entity

Add `activeWorld` field to `PetProfile.java`:

```java
@Column(name = "active_world", length = 50)
private String activeWorld = "COTTON_LAND";

// getter/setter
public String getActiveWorld() { return activeWorld; }
public void setActiveWorld(String activeWorld) { this.activeWorld = activeWorld; }
```

### Step 7: Inject lore in ConversationProcessService

In `buildEnhancedPrompt()`, add new layer **after** pet personality, **before** quest:

```java
@Inject
WorldLoreService worldLoreService;

// In buildEnhancedPrompt(), after pet prompt block:
// 1b. World Lore context
if (petStatus.stage() != null && !"EGG".equalsIgnoreCase(petStatus.stage())) {
    try {
        PetProfile petProfile = petProfileService.getProfileByUserId(userId); // get active_world
        String worldCode = petProfile.getActiveWorld() != null ? petProfile.getActiveWorld() : "COTTON_LAND";
        // topInterests is null at Phase 2 — Phase 3 will provide real values
        List<WorldLore> lore = worldLoreService.getUnlockedLore(worldCode, petStatus.level(), null);
        String lorePrompt = worldLoreService.formatLorePrompt(lore);
        if (!lorePrompt.isBlank()) {
            promptBuilder.append(lorePrompt).append("\n");
        }
    } catch (Exception e) {
        LOG.warnf("Lore retrieval failed, continuing without: %s", e.getMessage());
    }
}
```

> **Note:** Requires `petProfileService.getProfileByUserId()` method — check if it exists or add it.

### Step 8: Add config properties

In `application.properties`:
```properties
# --- World Lore System ---
world.lore.max-inject=3
```

### Step 9: Write 30 Cotton Land seed entries

Create `aimon-backend/src/main/resources/db/seed/world_lore_cotton_land_seed.sql`.

**30 entries across 5 level tiers (author manually, use canon as reference):**

**Level 3-4 (6 entries — BABY hatches, first world impressions):**
- `ability`: Mon's color changes — ability + emotion categories
- `food`: kẹo sương description
- `place`: Nhà Bông (Mon's home)
- `food`: bánh bông, Mon's baking habit
- `place`: Suối Bông morning routine
- `character`: Mon waking up alone before everyone else

**Level 5-6 (6 entries — meeting friends):**
- `character`: Gió (best friend) introduction
- `character`: Bà Mây (Grandma Cloud) first meeting
- `place`: Chợ Bông market day
- `tradition`: morning hum greeting
- `character`: Ánh (Sunshine Ray) morning visit
- `place`: Vườn Cầu Vồng (Rainbow Garden) discovery

**Level 7-9 (8 entries — adventures begin):**
- `tradition`: Hội Bông Bay (Flying Cotton Festival)
- `place`: Rừng Bông (Cotton Forest) — where cotton pods grow
- `mystery`: glowing pods at night
- `place`: Learning Tree — Mon's reading spot
- `character`: Gió's funniest prank on Mon
- `tradition`: rainy season — Cotton Land becomes extra soft
- `place`: Chợ Bông item Mon saved up to buy
- `character`: Mon helping a baby cotton creature out of its pod

**Level 10-11 (5 entries — deeper world):**
- `place`: Hang Ký Ức (Memory Cave) — Mon discovered it accidentally
- `mystery`: Bà Mây's age mystery — she can't remember
- `mystery`: The Edge of Cotton Land — Mon saw it once from afar
- `character`: Bà Mây tells Mon about Cotton Land's origin
- `place`: Đỉnh Mây (Cloud Peak) — Mon's dream destination

**Level 12+ (5 entries — adult depth):**
- `mystery`: What lies beyond the Edge — Mon theorizes
- `character`: Mon's last deep conversation with Bà Mây
- `tradition`: How Cotton Land came to exist (origin story fragment)
- `place`: Đỉnh Mây — Mon finally reaches it, what it sees
- `mystery`: Why Mon can change colors when other creatures can't

**SQL format (follow V4 seed pattern):**
```sql
SET search_path TO aimon, public;

INSERT INTO world_lore (world_code, title, category, content, min_level, interest_tags) VALUES
('COTTON_LAND', 'Màu sắc của Mon', 'ability',
 'Mình có một khả năng đặc biệt — lông của mình đổi màu theo cảm xúc. Khi vui, mình trở nên hồng hồng như hoa đào. Khi buồn, mình xám xịt như đám mây mưa, và cậu có thể biết ngay mình đang cảm thấy gì!',
 3, ARRAY['động vật', 'nghệ thuật', 'khoa học']),
-- ... remaining 29 entries
ON CONFLICT DO NOTHING;
```

**Content rules for all entries:**
- Vietnamese, 1st person ("mình"), 2-3 sentences
- Warm, child-friendly, wonder-inducing
- Each ends in a way that invites curiosity or reaction

### Step 10: Compile and verify migration

```bash
cd aimon-backend && mvn compile -q
# Start dev server to trigger Flyway migration
mvn quarkus:dev -Dquarkus.args='--dev'
```

---

## Todo

- [ ] Write `docs/cotton-land-canon.md`
- [ ] Create `V5__world_lore_system.sql` migration
- [ ] Create `WorldLore.java` entity
- [ ] Create `WorldLoreRepository.java`
- [ ] Create `WorldLoreService.java`
- [ ] Add `activeWorld` field to `PetProfile.java`
- [ ] Inject `WorldLoreService` in `ConversationProcessService`; add lore layer to `buildEnhancedPrompt()`
- [ ] Add config to `application.properties`
- [ ] Write 30 lore entries in `world_lore_cotton_land_seed.sql`
- [ ] Run `mvn compile -q`; verify 0 errors
- [ ] Run dev server; verify Flyway V5 applies cleanly
- [ ] Manual test: start conversation at level 3+, check Mon mentions Cotton Land naturally

---

## Success Criteria

- V5 migration applies with no errors
- Lore entries injected into prompt for level ≥ 3 pets
- Mon references Cotton Land facts naturally in ≥1 of 5 test conversation turns
- No regression in existing conversation flow (quest, memory, TTS still work)

---

## Risk Assessment

| Risk | Likelihood | Mitigation |
|------|-----------|------------|
| Lore injection bloats prompt/token cost | Medium | Cap at 3 entries; each ~50 tokens; total <150 tokens overhead |
| Mon reads out lore verbatim instead of naturally referencing | Medium | Prompt says "tự nhiên chia sẻ" + hook rules from Phase 1 |
| V5 migration fails on existing DB | Low | `ADD COLUMN IF NOT EXISTS`; safe for re-run |
| `ORDER BY RANDOM()` performance on large lore table | Low | Table <200 rows; index on (world_code, min_level, is_active) |
| `PetProfileService.getProfileByUserId()` doesn't exist | Medium | Add method to `PetProfileService` if missing |

---

## Security Considerations

- Lore content is static/admin-authored; no user-controlled content in SQL
- Lore import should require API key auth if exposed via REST endpoint in future

---

## Next Steps

After Phase 2 validated → proceed to [Phase 03: Adaptive Interest System](./phase-03-adaptive-interest-system.md)
