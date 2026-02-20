# Phase 02 — World Lore System

**Status:** Complete | **Effort:** ~8h | **Priority:** P1

**Context links:**
- [Brainstorm LORE gaps](./brainstorm-lore-gaps.md)
- [Brainstorm Tasteless Combat](./brainstorm-tasteless-combat.md) ← Phase 2b (không implement ở đây, nhưng schema được chuẩn bị sẵn)
- [Cotton Land Canon](../../docs/cotton-land-canon.md) ← nguồn sự thật, tạo ở Step 1
- [V4__quest_system.sql](../../aimon-backend/src/main/resources/db/migration/V4__quest_system.sql) (pattern reference)
- [PetProfile.java](../../aimon-backend/src/main/java/dev/aimon/entity/pet/PetProfile.java)

---

## Overview

Gives Mon a real world to talk about. `world_lore` table stores **5-Flavor Cotton Land** facts
unlocked by pet level. `WorldLoreService` fetches + ranks by interest tags. Top 3 facts injected
into `ConversationProcessService.buildEnhancedPrompt()` as a new prompt layer.

**Phase 2 cũng chuẩn bị sẵn schema** cho Tasteless Combat (Phase 2b):
- Cột `shard_type` trên `world_lore` (AMBIENT / SIDE / MILESTONE)
- Bảng `user_shards` (schema sẵn, service minimal — UI collection làm sau)
- Cột `current_location` trên `pet_profiles`

Multi-world ready: `world_code` + `active_world` cho phép thêm world sau không cần migration phá vỡ.

---

## Requirements

### Functional
- Lore entries gated by `min_level ≤ pet.level AND world_code = pet.active_world`
- Chỉ inject lore type `AMBIENT` vào prompt (SIDE/MILESTONE không inject — dùng cho Phase 2b)
- Interest tag matching: entries với tags overlapping child's top interests surface first
- Inject top 3 AMBIENT entries per conversation (configurable)
- Default world = `COTTON_LAND`, default location = `SWEET_DOMINION`
- Lore prompt injected between pet personality section và quest/kid-mode sections

### Non-functional
- Query fast: single indexed SQL query, no N+1
- File limit: each new Java file ≤ 200 lines
- Lore content: tiếng Việt, ngôi thứ nhất Mon ("mình"), 2-3 câu mỗi entry
- Theo đúng quy tắc viết lore trong `docs/cotton-land-canon.md` Section 11

---

## Architecture

```
ConversationProcessService.buildEnhancedPrompt()
  │
  ├─ 1. petPromptAssembler.buildPetSystemPrompt()   ← personality + hooks
  ├─ 2. worldLoreService.getLorePrompt(...)          ← NEW: Cotton Land facts (AMBIENT only)
  ├─ 3. questPrompt (existing)
  ├─ 4. kidMode (existing)
  ├─ 5. PowerMem context (existing)
  ├─ 6. session history (existing)
  └─ 7. current message (existing)

WorldLoreService
  ├─ getUnlockedLore(worldCode, petLevel, topInterestTags)
  │    → SELECT * FROM world_lore
  │         WHERE world_code=? AND min_level<=? AND shard_type='AMBIENT' AND is_active=true
  │    → Sort by interest_tag_overlap DESC, then RANDOM()
  │    → LIMIT max_lore_entries (default 3)
  └─ formatLorePrompt(List<WorldLore>)
       → "=== Thế giới của Mon ===\n{content}\n..."
```

---

## Schema Changes (V5 Migration)

```sql
SET search_path TO aimon, public;

-- Multi-world lore table
CREATE TABLE IF NOT EXISTS world_lore (
    id            BIGSERIAL    PRIMARY KEY,
    world_code    VARCHAR(50)  NOT NULL,
    title         VARCHAR(100) NOT NULL,
    category      VARCHAR(50)  NOT NULL,   -- place | character | tradition | food | mystery | ability
    content       TEXT         NOT NULL,
    min_level     INT          NOT NULL DEFAULT 3,
    interest_tags TEXT[]       NOT NULL DEFAULT '{}',
    shard_type    VARCHAR(20)  NOT NULL DEFAULT 'AMBIENT',  -- AMBIENT | SIDE | MILESTONE
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ           DEFAULT now()
);

CREATE INDEX idx_world_lore_lookup
    ON world_lore(world_code, min_level, shard_type, is_active);

-- User shard tracking (schema sẵn cho Phase 2b, service minimal ở Phase 2)
CREATE TABLE IF NOT EXISTS user_shards (
    id          BIGSERIAL   PRIMARY KEY,
    user_id     BIGINT      NOT NULL,
    lore_id     BIGINT      NOT NULL REFERENCES world_lore(id),
    source      VARCHAR(50) NOT NULL DEFAULT 'EXPLORATION',
    -- Phase 2b values: 'BOSS_DEFEAT' | 'TASTELESS_DROP' | 'NPC_CONVERSATION' | 'EXPLORATION'
    unlocked_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(user_id, lore_id)
);

CREATE INDEX idx_user_shards_user ON user_shards(user_id);

-- Add fields to pet_profiles
ALTER TABLE pet_profiles
    ADD COLUMN IF NOT EXISTS active_world      VARCHAR(50) NOT NULL DEFAULT 'COTTON_LAND',
    ADD COLUMN IF NOT EXISTS current_location  VARCHAR(50) NOT NULL DEFAULT 'SWEET_DOMINION';
```

---

## Related Code Files

**Modify:**
- `aimon-backend/src/main/java/dev/aimon/entity/pet/PetProfile.java` — add `activeWorld`, `currentLocation`
- `aimon-backend/src/main/java/dev/aimon/service/conversation/ConversationProcessService.java` — inject lore layer
- `aimon-backend/src/main/resources/application.properties` — add lore config

**Create:**
- `aimon-backend/src/main/resources/db/migration/V5__world_lore_system.sql`
- `aimon-backend/src/main/java/dev/aimon/entity/world/WorldLore.java`
- `aimon-backend/src/main/java/dev/aimon/repository/WorldLoreRepository.java`
- `aimon-backend/src/main/java/dev/aimon/service/world/WorldLoreService.java`
- `aimon-backend/src/main/resources/db/seed/world_lore_cotton_land_seed.sql`
- `docs/cotton-land-canon.md` ✅ (đã tạo)

---

## Implementation Steps

### Step 1: Xác nhận Cotton Land Canon ✅
File `docs/cotton-land-canon.md` đã được tạo. Đọc kỹ trước khi viết bất kỳ lore content nào.

---

### Step 2: Flyway migration V5

Tạo `V5__world_lore_system.sql` với nội dung schema ở mục trên.

**Lưu ý:** `current_location` dùng cho Phase 2b (Tasteless Combat) — Phase 2 chỉ set default, không có logic vị trí.

---

### Step 3: WorldLore entity

Tạo `aimon-backend/src/main/java/dev/aimon/entity/world/WorldLore.java`:

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

    @Column(name = "shard_type", length = 20, nullable = false)
    private String shardType = "AMBIENT"; // AMBIENT | SIDE | MILESTONE

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "created_at")
    private Instant createdAt;

    // Getters/setters (no Lombok to match project style)
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getWorldCode() { return worldCode; }
    public void setWorldCode(String worldCode) { this.worldCode = worldCode; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Integer getMinLevel() { return minLevel; }
    public void setMinLevel(Integer minLevel) { this.minLevel = minLevel; }
    public String[] getInterestTags() { return interestTags; }
    public void setInterestTags(String[] interestTags) { this.interestTags = interestTags; }
    public String getShardType() { return shardType; }
    public void setShardType(String shardType) { this.shardType = shardType; }
    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
```

---

### Step 4: WorldLoreRepository

Tạo `aimon-backend/src/main/java/dev/aimon/repository/WorldLoreRepository.java`:

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
     * Fetch AMBIENT lore unlocked for given world and level.
     * Returns randomized list; caller handles interest ranking and limiting.
     */
    public List<WorldLore> findAmbientUnlocked(String worldCode, int petLevel) {
        return em.createQuery(
            "SELECT w FROM WorldLore w " +
            "WHERE w.worldCode = :worldCode " +
            "AND w.minLevel <= :level " +
            "AND w.shardType = 'AMBIENT' " +
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

---

### Step 5: WorldLoreService

Tạo `aimon-backend/src/main/java/dev/aimon/service/world/WorldLoreService.java`:

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

    @Transactional(Transactional.TxType.SUPPORTS)
    public List<WorldLore> getUnlockedLore(String worldCode, int petLevel, List<String> topInterests) {
        List<WorldLore> all = repository.findAmbientUnlocked(worldCode, petLevel);
        if (all.isEmpty()) return List.of();

        if (topInterests == null || topInterests.isEmpty()) {
            return all.stream().limit(maxLoreEntries).collect(Collectors.toList());
        }

        Set<String> interestSet = new HashSet<>(topInterests);
        return all.stream()
            .sorted(Comparator.comparingInt(
                (WorldLore w) -> interestOverlap(w.getInterestTags(), interestSet)
            ).reversed())
            .limit(maxLoreEntries)
            .collect(Collectors.toList());
    }

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

---

### Step 6: Update PetProfile entity

Thêm 2 field vào `PetProfile.java`:

```java
@Column(name = "active_world", length = 50)
private String activeWorld = "COTTON_LAND";

@Column(name = "current_location", length = 50)
private String currentLocation = "SWEET_DOMINION";

// getters/setters
public String getActiveWorld() { return activeWorld; }
public void setActiveWorld(String v) { this.activeWorld = v; }
public String getCurrentLocation() { return currentLocation; }
public void setCurrentLocation(String v) { this.currentLocation = v; }
```

---

### Step 7: Inject lore trong ConversationProcessService

Trong `buildEnhancedPrompt()`, thêm sau pet prompt block:

```java
@Inject
WorldLoreService worldLoreService;

// 1b. World Lore context (AMBIENT only — SIDE/MILESTONE dùng cho Phase 2b)
if (petStatus.stage() != null && !"EGG".equalsIgnoreCase(petStatus.stage())) {
    try {
        PetProfile petProfile = petProfileService.getProfileByUserId(userId);
        String worldCode = petProfile.getActiveWorld() != null
            ? petProfile.getActiveWorld() : "COTTON_LAND";
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

> **Note:** Kiểm tra `petProfileService.getProfileByUserId()` tồn tại chưa. Nếu chưa thì thêm vào `PetProfileService`.

---

### Step 8: Config properties

```properties
# --- World Lore System ---
world.lore.max-inject=3
```

---

### Step 9: Viết 30 Cotton Land seed entries (5-Flavor Canon)

Tạo `aimon-backend/src/main/resources/db/seed/world_lore_cotton_land_seed.sql`.

**Tất cả entries phải theo đúng quy tắc trong `docs/cotton-land-canon.md` Section 11.**
**Không dùng cloud-cotton content (Nhà Bông, Suối Bông, Bà Mây, Gió) — đó là lore cũ đã bỏ.**

**SQL format:**
```sql
SET search_path TO aimon, public;

INSERT INTO world_lore (world_code, title, category, content, min_level, interest_tags, shard_type) VALUES
('COTTON_LAND', 'Màu sắc của Mon', 'ability',
 'Mình có một khả năng đặc biệt — lông của mình đổi màu theo cảm xúc. Khi vui, mình trở nên hồng hồng như hoa đào. Khi buồn, mình xám xịt như đám mây mưa, và cậu có thể biết ngay mình đang cảm thấy gì!',
 3, ARRAY['động vật', 'nghệ thuật', 'khoa học'], 'AMBIENT'),
-- ... remaining 29 entries
ON CONFLICT DO NOTHING;
```

**Phân bổ 30 entries theo level tier:**

**Level 3-4 (6 AMBIENT — BABY, ấn tượng đầu tiên về Cotton Land):**
- `ability`: Mon giới thiệu khả năng đổi màu lông theo cảm xúc
- `place`: Marshmallow Meadow — cánh đồng kẹo dẻo, mỗi bước đi nảy nhẹ
- `food`: kẹo ngọt của Sweet Dominion, mùi vanilla
- `place`: Whipcream Spire nhìn từ xa — tháp kem vươn cao giữa Cotton Land
- `character`: Cư dân Sweet Dominion lúc nào cũng mỉm cười — Mon thấy ấm lòng
- `tradition`: Lễ Thắp Đèn Kẹo — mỗi đèn là một điều dễ thương được bảo vệ

**Level 5-6 (6 AMBIENT — gặp thế giới rộng hơn):**
- `place`: Candy Lantern Town — đêm nào cũng như lễ hội
- `character`: Mon nghe kể về những người ở Sour Groves hay đặt câu hỏi kỳ lạ
- `place`: Citrus Canopy ánh nắng chói như "soi thật" — Mon chưa bao giờ dám vào
- `place`: Pretzel Pier Salty Harbors — bến cảng sôi động, mùi muối và phô mai
- `character`: Cayenne Roadrunner chạy ngang Cotton Land, lông để lại vệt nhiệt
- `tradition`: Soda Springs phun lên như pháo hoa trong Lễ Soda Bloom

**Level 6-7 (4 SIDE — trigger cảm xúc):**
- `tradition`: Buộc Nút Thề ở Cảng Mặn — mỗi lời hứa được ghi vào dây thừng *(trigger: bé nói về lời hứa)*
- `tradition`: "Nếm trước khi tin" ở Rừng Chua *(trigger: bé nói về sự bất công)*
- `character`: Chili Chameleon ở Pepper Wastes — không ai giả được cảm xúc của nó *(trigger: bé nói về sự thành thật)*
- `tradition`: Lễ Cúng Lửa Đêm — viết nỗi sợ lên paprika paper, đốt đi *(trigger: bé nói về nỗi sợ)*

**Level 7-9 (6 AMBIENT — thế giới rộng mở thêm):**
- `place`: Red Dune Runway Pepper Wastes — cồn đỏ, gió nóng, chim săn mồi
- `place`: Bitter Hollow Basin — lòng chảo sương tụ thấp, Mon chưa dám đến
- `mystery`: Mocha Gate đang bị niêm phong — Mon không biết tại sao
- `character`: Cacao Stag già canh Mocha Gate, không bao giờ rời vị trí
- `place`: Cacao Cathedral Grove — rừng cây mọc như cột trụ, trầm tĩnh lạ thường
- `tradition`: Lễ Ủ Trà Bitter Hollow — mỗi gia đình ủ bình trà ký ức truyền qua thế hệ

**Level 9-10 (4 SIDE — mâu thuẫn bắt đầu lộ ra):**
- `mystery`: Nghe người Cảng Mặn kể, lõi năng lượng ngày xưa có nhiều màu hơn bây giờ *(Sugarcore hint)*
- `mystery`: Có người nói Bitter Mist không phải "độc" — mà là Cotton Land đang cố nói điều gì đó
- `character`: Tirakuma canh Choco Gate — đôi mắt buồn, như đang chờ ai *(Noir hint)*
- `place`: Ashcaramel Ruins — tàn tích caramel cháy, người ta tránh đến nhưng Mon cảm thấy... quen

**Level 10-11 (4 AMBIENT — chiều sâu của thế giới):**
- `mystery`: Sugarcore đôi khi phát ra ánh sáng lạ, không chỉ màu hồng thuần túy
- `character`: Noir Coneko — Mon nghe tên nhưng không biết sự thật, chỉ biết đó là "kẻ tạo ra Bitter Mist"
- `place`: Chili-Lime Gate giữa Rừng Chua và Hoang Mạc Cay — cổng mở nhưng không ai qua
- `mystery`: Vì sao Cotton Land chỉ có vị Ngọt ở trung tâm? Ngày xưa có khác không?

---

### Step 10: Compile và verify

```bash
cd aimon-backend && mvn compile -q
# Start dev server để trigger Flyway V5
mvn quarkus:dev
```

---

## Todo

- [x] Xác nhận `docs/cotton-land-canon.md` đúng nội dung ✅
- [x] Tạo `V5__world_lore_system.sql` (schema mới + user_shards + current_location)
- [x] Tạo `WorldLore.java` entity (thêm field `shardType`)
- [x] Tạo `WorldLoreRepository.java` (query theo `shard_type = 'AMBIENT'`)
- [x] Tạo `WorldLoreService.java`
- [x] Thêm `activeWorld` + `currentLocation` vào `PetProfile.java`
- [x] Inject `WorldLoreService` trong `ConversationProcessService`
- [x] Thêm config vào `application.properties`
- [x] Viết 30 lore entries trong `world_lore_cotton_land_seed.sql` (5-Flavor Canon, KHÔNG dùng cloud-cotton)
- [x] `mvn compile -q` — verify 0 errors
- [x] Start dev server — verify Flyway V5 applies cleanly
- [x] Manual test: conversation level 3+, Mon nhắc Cotton Land tự nhiên

---

## Success Criteria

- V5 migration applies without errors
- Query chỉ trả về `shard_type = 'AMBIENT'` entries khi inject vào prompt
- Mon references 5-Flavor Cotton Land facts tự nhiên trong ≥1 of 5 test turns
- `user_shards` table tồn tại và sẵn sàng cho Phase 2b
- `current_location` trên `pet_profiles` có giá trị `SWEET_DOMINION` cho mọi user hiện tại
- Không regression trong conversation flow (quest, memory, TTS vẫn hoạt động)

---

## Risk Assessment

| Risk | Likelihood | Mitigation |
|---|---|---|
| Lore injection bloát prompt token | Medium | Cap 3 entries; mỗi entry ~50 tokens; tổng <150 tokens overhead |
| Mon đọc lore verbatim thay vì tự nhiên | Medium | Prompt instruction "tự nhiên chia sẻ khi phù hợp" |
| V5 migration fail trên DB cũ | Low | `ADD COLUMN IF NOT EXISTS`; safe re-run |
| `ORDER BY RANDOM()` performance | Low | Table <200 rows; index đủ |
| `petProfileService.getProfileByUserId()` chưa có | Medium | Kiểm tra + thêm nếu thiếu |

---

## Security

- Lore content là static/admin-authored; không có user-controlled content trong SQL
- `user_shards` chỉ ghi bởi server-side service, không expose qua REST trong Phase 2

---

## Next Steps

Phase 2 validated → Phase 2b: **Tasteless Combat System**
Xem [`brainstorm-tasteless-combat.md`](./brainstorm-tasteless-combat.md) để biết scope và schema cần thêm.