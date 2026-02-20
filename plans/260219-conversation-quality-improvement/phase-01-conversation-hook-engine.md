# Phase 01 — Conversation Hook Engine

**Status:** Complete | **Effort:** ~4h | **Priority:** P1 (deploy first, validate immediately)

**Context links:**
- [Brainstorm report](./brainstorm-report.md)
- [PetPromptAssembler.java](../../aimon-backend/src/main/java/dev/aimon/service/pet/PetPromptAssembler.java)

---

## Overview

Changes to `PetPromptAssembler` + `ConversationProcessService`. Adds conversation hook mechanics
so Mon stops answering and stopping. Also wires up user name from DB so Mon calls child by name.

**Root cause:** `BASE_RULES` rule 9 says "ask back or share more" but gives LLM zero examples.
LLM defaults to safe answer-and-stop pattern. Also, Mon uses "bé" which sounds condescending — must use child's name or "cậu".

---

## Requirements

- Mon ends ≥60% of non-quest turns with a hook (wonder, story invite, callback, curiosity follow, world share)
- Hook style matches stage: BABY uses simple wonder; CHILD uses all types; ADULT uses deep callbacks
- Hooks must feel natural, NOT forced after every sentence
- Must not inflate response length (still 2-4 sentences total)
- **Mon must call child by their name** (from `users.name` DB column) or use "cậu" — NEVER "bé"
- Create `User` entity + `UserRepository` to fetch user name from existing `users` table
- Wire `childName` in `ConversationProcessService` (currently passes `null`)
- **Cache pet profile + user profile at session start** — do NOT query DB every message

---

## Implementation Steps

### 0. Create User entity + UserRepository

The `users` table exists (V1 migration) but has no JPA entity. Create:

**`aimon-backend/src/main/java/dev/aimon/entity/User.java`:**
```java
@Entity
@Table(name = "users", schema = "aimon")
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(length = 100, nullable = false) private String name;
    @Column private Integer age;
    @Column(name = "parent_id") private Long parentId;
    // getters/setters
}
```

**`aimon-backend/src/main/java/dev/aimon/repository/UserRepository.java`:**
```java
@ApplicationScoped
public class UserRepository {
    @Inject EntityManager em;
    public User findById(Long id) { return em.find(User.class, id); }
}
```

### 1. Replace "bé" with "cậu" in PetPromptAssembler + add conversation hooks

Replace ALL instances of "bé" in BASE_RULES, STAGE_BEHAVIORS, AFFINITY_TIERS, and quest prompt with "cậu".

Remove rule 9 ("Hay hỏi ngược lại..."). Add `CONVERSATION_DYNAMICS` section.

**Updated BASE_RULES:**
```
=== Quy tắc cốt lõi ===
1. LUÔN trả lời bằng tiếng Việt.
2. Dùng ngôn ngữ tự nhiên, dễ hiểu cho trẻ em nhưng KHÔNG khô khan hay quá ngắn.
3. Kết thúc mỗi câu bằng dấu . ? hoặc !
4. KHÔNG dùng emoji hoặc ký tự đặc biệt.
5. Luôn an toàn, tốt bụng, phù hợp với lứa tuổi.
6. Nói chuyện như một người bạn thân — ấm áp, đồng cảm, tự nhiên.
7. Trả lời từ 2-4 câu, thể hiện cảm xúc thật sự và sự quan tâm.
8. Khi cậu ấy vui thì vui cùng, khi cậu ấy buồn thì an ủi chân thành, không sáo rỗng.
9. LUÔN gọi bạn bằng tên (nếu biết) hoặc xưng "cậu". KHÔNG BAO GIỜ dùng từ "bé".

=== Cách duy trì cuộc trò chuyện ===
Khi tự nhiên và không có nhiệm vụ đang chờ, kết thúc bằng MỘT trong các cách:
- Hỏi thêm: "Cậu đã bao giờ thử... chưa?" / "Cậu nghĩ... thế nào?"
- Chia sẻ từ thế giới của mình: "Ở chỗ mình sống, mình cũng từng..."
- Gợi mở câu chuyện: "Điều đó làm mình nhớ đến lúc mình..."
- Thể hiện tò mò: "Mình tự hỏi liệu... có..."
- Nhắc lại: "Hôm trước cậu kể về X... bây giờ thế nào rồi?"
Chỉ dùng 1 cách mỗi lần, chọn phù hợp ngữ cảnh. KHÔNG bắt buộc mọi câu.
```

**Updated STAGE_BEHAVIORS (replace "bé" → "cậu"):**
- BABY: "tò mò hỏi cậu ấy về mọi thứ xung quanh", "muốn hiểu cậu ấy hơn"
- CHILD: "người bạn thân nhất của cậu ấy", "Luôn đồng cảm với cảm xúc của cậu ấy"
- ADULT: "người bạn đồng hành đáng tin cậy", "tôn trọng suy nghĩ của cậu ấy"
- VARIANT: "quan tâm đến cảm xúc của cậu ấy"

**Updated AFFINITY_TIERS:**
```
"[MỐI QUAN HỆ: Thân thiện. Gọi tên cậu ấy tự nhiên.]"
```

**Updated `[BÉ: ...]` tag → `[TÊN BẠN: ...]`:**
```java
if (childName != null && !childName.isBlank()) {
    sb.append("\n[TÊN BẠN: ").append(childName);
    sb.append(". Gọi bạn bằng tên hoặc xưng \"cậu\", KHÔNG dùng \"bé\".");
    if (childAge > 0) {
        sb.append(" ").append(childAge).append(" tuổi.");
    }
    sb.append("]\n");
}
```

**Updated quest prompt:** replace all "bé" → "cậu ấy" in quest evaluation instructions.

### 2. Enhance STAGE_BEHAVIORS with hook-specific guidance

**BABY — append:**
```
Ưu tiên hỏi về thế giới của cậu ấy (đồ vật, gia đình, động vật cậu ấy thích).
Dùng ngôn ngữ đơn giản: "Cậu thích... không?", "Cậu có... không?"
```

**CHILD — append:**
```
Ưu tiên kể chuyện Cotton Land hoặc chia sẻ điều thú vị mình biết.
Hỏi cậu ấy về trải nghiệm cụ thể: "Cậu đã từng thấy...", "Cậu có muốn thử... không?"
```

**ADULT — append:**
```
Ưu tiên hỏi sâu hơn về suy nghĩ và cảm xúc của cậu ấy.
Nhắc lại kỷ niệm chung nếu có, hoặc chia sẻ góc nhìn từ trải nghiệm của mình.
"Mình nghĩ điều đó vì..., cậu thấy thế nào?"
```

### 3. Cache pet + user profile at session start (avoid per-message DB queries)

**Problem:** Currently `petProfileService.getStatus(userId)` hits DB on EVERY message.
System prompt is rebuilt from scratch each time. This is wasteful — pet state rarely changes mid-conversation.

**Solution:** Cache `PetStatusDto`, `childName`, `childAge`, and the **built system prompt** in `ConversationSession`.
Load once at session creation (hello handshake). Invalidate only when pet state changes.

**3a. Add cached fields to `ConversationSession`:**

```java
private PetStatusDto cachedPetStatus;
private String cachedChildName;
private int cachedChildAge;
private String cachedSystemPrompt;  // the full assembled pet+hook prompt

// getters/setters + invalidation method:
public void invalidateCachedPrompt() { this.cachedSystemPrompt = null; }
public boolean hasSystemPrompt() { return cachedSystemPrompt != null; }
```

**3b. Load user + pet profile at session creation in `ConversationSessionManager.getOrCreateSession()`:**

Inject `UserRepository` and `PetProfileService`. When creating a new session:

```java
User user = userRepository.findById(userId.longValue());
PetStatusDto petStatus = petProfileService.getStatus(userId.longValue());

ConversationSession session = new ConversationSession(userId, sessionId, ...);
session.setCachedPetStatus(petStatus);
session.setCachedChildName(user != null ? user.getName() : null);
session.setCachedChildAge(user != null && user.getAge() != null ? user.getAge() : 0);
```

**3c. Build system prompt once, cache it:**

In `ConversationProcessService.buildEnhancedPrompt()`, check cache first:

```java
// Use cached pet status instead of DB query
PetStatusDto petStatus = session.getCachedPetStatus();

// Build system prompt only if not cached
if (!session.hasSystemPrompt()) {
    String petPrompt = petPromptAssembler.buildPetSystemPrompt(
        petStatus, session.getCachedChildName(), session.getCachedChildAge());
    session.setCachedSystemPrompt(petPrompt);
}
promptBuilder.append(session.getCachedSystemPrompt());
```

**3d. Invalidate cache when pet state changes:**

After feeding, quest completion, level up, or transform — call `session.invalidateCachedPrompt()`.
Also refresh `cachedPetStatus` from DB at that point.

Places that trigger invalidation:
- `PetMessageHandler.handleFeedConfirm()` — hunger/happiness change
- `QuestEvaluationService.processResult()` — XP/level change
- `PetMessageHandler.handleTransformRequest()` — stage/variant change

**3e. Remove per-message `petProfileService.getStatus()` from `processMessageStreaming()`:**

Replace:
```java
PetStatusDto petStatus = petProfileService.getStatus(userId);
```
With:
```java
PetStatusDto petStatus = session.getCachedPetStatus();
```

> **Note:** Quest prompt (`getPendingQuest()`) is still queried per message since quest state changes
> between messages (user answers quest). This is a lightweight query and acceptable.

### 4. Update VARIANT hook guidance

For VARIANT stage, hook style inherits from ADULT plus variant-specific topic pull:
- Scholar Mon: end with knowledge curiosity → "Mình tự hỏi tại sao..."
- Foodie Mon: end with food-themed invitation → "Nếu ở đây, mình sẽ nấu..."

Update VARIANT template accordingly.

### 5. Compile & test

```bash
cd aimon-backend && mvn compile -q
```

**Manual validation prompt test** (run a mock conversation):
- Input: "Hôm nay con đi học về" → expected: Mon responds using child's name + asks about school day
- Input: "Con thích khủng long" → expected: Mon responds with "cậu" + wonders about dinosaurs

---

## Code Snippet (modified BASE_RULES)

See Step 1 above for full BASE_RULES with "cậu" replacing "bé" and conversation hooks added.

---

## Todo

- [x] Create `User.java` entity for `users` table
- [x] Create `UserRepository.java` with `findById()`
- [x] Replace ALL "bé" with "cậu" in `PetPromptAssembler.java` (BASE_RULES, STAGE_BEHAVIORS, AFFINITY_TIERS, quest prompt)
- [x] Add rule 9: "gọi bằng tên hoặc xưng cậu, KHÔNG dùng bé"
- [x] Update `[BÉ: ...]` tag → `[TÊN BẠN: ...]` with explicit no-"bé" instruction
- [x] Add conversation hook dynamics to `BASE_RULES`
- [x] Update BABY/CHILD/ADULT/VARIANT stage templates with hook guidance (using "cậu")
- [x] Add `cachedPetStatus`, `cachedChildName`, `cachedChildAge`, `cachedSystemPrompt` to `ConversationSession`
- [x] Load user + pet profile at session creation in `ConversationSessionManager`
- [x] Cache system prompt in `ConversationProcessService.buildEnhancedPrompt()` — build once, reuse
- [x] Remove per-message `petProfileService.getStatus()` call — use cached version
- [x] Add `invalidateCachedPrompt()` calls after feed/quest/transform events
- [x] Run `mvn compile -q` — verify 0 errors
- [x] Manual test: verify Mon uses child's name or "cậu", never "bé"
- [x] Verify no extra DB queries per message (check logs)

---

## Success Criteria

- Compile passes with no errors
- In 3 test conversations, Mon ends response with a hook in ≥2 of 3 turns
- Hooks do not feel forced or repetitive
- Response length stays 2-4 sentences

---

## Risk Assessment

| Risk | Likelihood | Mitigation |
|------|-----------|------------|
| LLM over-applies hooks (every sentence) | Medium | "KHÔNG bắt buộc mọi câu" instruction |
| Hook feels generic/robotic | Low | Stage-specific hook examples guide LLM |
| Response gets longer than 4 sentences | Low | Rule 7 still enforces 2-4 sentence limit |

---

## Next Steps

After Phase 1 validated → proceed to [Phase 02: World Lore System](./phase-02-world-lore-system.md)
