---
title: "Conversation Quality Improvement"
description: "Three-layer enhancement: conversation hooks, world lore system, and adaptive interest tracking to make Mon feel like a real friend"
status: complete  # Updated: All 3 phases complete
priority: P1
effort: 14h
branch: main
tags: [feature, backend, conversation, pet, personality]
created: 2026-02-20
---

# Conversation Quality Improvement

## Overview

Transforms AI-MON from Q&A assistant to engaging companion via three sequential layers:
1. **Hook Engine** — prompt changes so Mon always invites continuation
2. **World Lore System** — Cotton Land facts unlocked by level; Mon has real stories to tell
3. **Adaptive Interest System** — Mon mirrors child's interests via PowerMem metadata

Source: [brainstorm-report.md](./brainstorm-report.md)

## Phases

| # | Phase | Status | Effort | Link |
|---|-------|--------|--------|------|
| 1 | Conversation Hook Engine + User Name Wiring + Session Caching | Complete | 4h | [phase-01](./phase-01-conversation-hook-engine.md) |
| 2 | World Lore System | Complete | 6h | [phase-02-world-lore-system.md](./phase-02-world-lore-system.md) |
| 3 | Adaptive Interest System | Complete | 4h | [phase-03-adaptive-interest-system.md](./phase-03-adaptive-interest-system.md) |

## Key Dependencies

- Phase 1 is self-contained; deploy first and validate LLM behaviour
- Phase 2 depends on Phase 1 (lore is worthless without hooks to share it)
- Phase 3 depends on Phase 2 (interests guide which lore surfaces)
- PowerMem must be running for Phase 3 (timeline query)

## Architecture Summary

```
PetPromptAssembler                WorldLoreService          AdaptiveInterestService
├── BASE_RULES + hook rules  ←    ├── world_lore table      ├── PowerMem timeline query
├── STAGE_BEHAVIORS               ├── level gate            ├── topic keyword/LLM classify
├── MOOD_OVERLAYS                 └── interest tag rank      ├── 60% diversity cap
└── AFFINITY_TIERS                                           └── top-3 inject to prompt

ConversationProcessService.buildEnhancedPrompt()
  [pet personality + hooks] → [world lore] → [interests]
  → [quest] → [kid mode] → [PowerMem ctx] → [history] → [message]
```

## Cross-Cutting: User Name Personalization

**Requirement:** Mon must call the child by their actual name (from `users.name` DB column) or use "cậu" as pronoun. NEVER use "bé".

**Impact across all phases:**
- `PetPromptAssembler`: replace all "bé" references with "cậu" in prompts; inject `[TÊN BẠN: {childName}]` tag instructing LLM to use child's real name
- `ConversationProcessService`: fetch `users.name` from DB and pass to `buildPetSystemPrompt()` (currently passes `null`)
- Create `User` entity + `UserRepository` to query `users` table (exists in V1 migration but has no JPA entity)
- All lore/hook/interest prompt examples must use "cậu" instead of "bé"

## Files Touched Across All Phases

**Modify:**
- `aimon-backend/src/main/java/dev/aimon/service/pet/PetPromptAssembler.java`
- `aimon-backend/src/main/java/dev/aimon/service/conversation/ConversationProcessService.java`
- `aimon-backend/src/main/java/dev/aimon/service/memory/MemoryMetadataBuilder.java`
- `aimon-backend/src/main/java/dev/aimon/entity/pet/PetProfile.java`
- `aimon-backend/src/main/resources/application.properties`

**Create:**
- `aimon-backend/src/main/java/dev/aimon/entity/User.java`
- `aimon-backend/src/main/java/dev/aimon/repository/UserRepository.java`
- `aimon-backend/src/main/resources/db/migration/V5__world_lore_system.sql`
- `aimon-backend/src/main/java/dev/aimon/entity/world/WorldLore.java`
- `aimon-backend/src/main/java/dev/aimon/repository/WorldLoreRepository.java`
- `aimon-backend/src/main/java/dev/aimon/service/world/WorldLoreService.java`
- `aimon-backend/src/main/java/dev/aimon/service/conversation/TopicClassifier.java`
- `aimon-backend/src/main/java/dev/aimon/service/conversation/AdaptiveInterestService.java`
- `aimon-backend/src/main/resources/db/seed/world_lore_cotton_land_seed.sql`
- `docs/cotton-land-canon.md`
