---
title: "Offline Resilience — Tamagotchi Gameplay & Sync"
description: "Keep basic tamagotchi gameplay alive when Pi loses WiFi, sync on reconnect"
status: complete
priority: P1
effort: 8h
branch: main
tags: [offline, frontend, backend, sync, tamagotchi]
created: 2026-02-21
---

# Offline Resilience — Tamagotchi Gameplay & Sync

## Problem
When Pi loses WiFi, AI-MON becomes unresponsive. Children lose engagement. Need visual-only tamagotchi gameplay offline with seamless sync on reconnect.

## Phases

| # | Phase | Effort | Status | File |
|---|-------|--------|--------|------|
| 1 | Event Journal & Response Bank | 2h | complete | [phase-01](phase-01-offline-event-journal-and-response-bank.md) |
| 2 | Game Engine & Stat Decay | 2h | complete | [phase-02](phase-02-offline-game-engine-and-stat-decay.md) |
| 3 | State Machine Integration | 2.5h | complete | [phase-03](phase-03-state-machine-integration.md) |
| 4 | Backend Sync Handler | 1.5h | complete | [phase-04](phase-04-backend-sync-handler.md) |

## Dependencies
- Phase 1 must complete before Phase 2 (journal + responses needed by engine)
- Phase 2 must complete before Phase 3 (engine needed by state machine)
- Phase 3 and Phase 4 can run in parallel (frontend sync client + backend sync handler)

## Key Decisions
- Visual-only offline (text bubbles + animations, no audio)
- SQLite event journal (max 1000 events, auto-prune)
- Last-write-wins sync with backend authority
- No stage evolution offline — deferred to backend
- Amber LED for offline state
- Infinite reconnect with exponential backoff (always-on device)

## Research
- [Frontend State & Network Analysis](research/researcher-01-frontend-state-network.md)
