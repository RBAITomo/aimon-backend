# AI-MON Documentation Index

**Last Updated:** 2026-02-22
**Project:** aimon-backend (v0.2+ with Offline Resilience - Phase 10 Complete)

Welcome to the AI-MON documentation suite. Use this index to find the right guide for your needs.

---

## Quick Navigation

### For Different Roles

**👨‍💻 Developers**
1. **Start here:** [Code Standards](./code-standards.md) — Mandatory reading for coding conventions
2. **Understand the code:** [Codebase Summary](./codebase-summary.md) — File structure and key classes
3. **Deep dive:** [System Architecture](./system-architecture.md) — Service interactions and data flows
4. **Deploy locally:** [Deployment Guide](./deployment-guide.md) — Set up Docker Compose for development

**🏗️ Architects**
1. **System design:** [System Architecture](./system-architecture.md) — Detailed architecture diagrams
2. **Requirements:** [Project Overview & PDR](./project-overview-pdr.md) — Product requirements
3. **Implementation:** [Codebase Summary](./codebase-summary.md) — How it's actually built
4. **Standards:** [Code Standards](./code-standards.md) — Development patterns

**🚀 DevOps / Operations**
1. **Setup & deployment:** [Deployment Guide](./deployment-guide.md) — Docker Compose configuration
2. **Troubleshooting:** [Deployment Guide](./deployment-guide.md) — Common issues & solutions
3. **Scaling:** [Deployment Guide](./deployment-guide.md) — Performance tuning section
4. **Requirements:** [Project Overview & PDR](./project-overview-pdr.md) — Success criteria

**📊 Project Managers / Stakeholders**
1. **Overview:** [Project Overview & PDR](./project-overview-pdr.md) — Vision, scope, requirements
2. **Progress:** [Project Overview & PDR](./project-overview-pdr.md) — Phase completion status
3. **Roadmap:** [Project Overview & PDR](./project-overview-pdr.md) — Future phases and timeline
4. **Technical overview:** [System Architecture](./system-architecture.md) — How it works

---

## Document Descriptions

### 1. [Codebase Summary](./codebase-summary.md) — 475 LOC
**Overview of project structure and key components**

Best for: Understanding what's where in the codebase
- Project structure (8 packages, 47 files, 6.3K LOC)
- Key classes and their responsibilities
- Service layer breakdown
- Database schema
- File statistics by package
- Performance targets

**Time to read:** 20-30 minutes

---

### 2. [System Architecture](./system-architecture.md) — 598 LOC
**Detailed architecture with diagrams and data flows**

Best for: Understanding how services interact
- System overview (ASCII diagram)
- Architecture tiers (Presentation, Application, Integration)
- Data flows for full conversation turn (9 steps)
- WebSocket protocol states (IDLE → LISTENING → PROCESSING → RESPONDING)
- Service dependencies and communication patterns
- Security and safety mechanisms
- Comparison with legacy system

**Time to read:** 30-45 minutes

---

### 3. [Code Standards](./code-standards.md) — 957 LOC
**Comprehensive coding conventions and best practices**

Best for: Writing code that fits the project
- Package organization and naming
- File naming and size limits
- Java and Quarkus patterns
- Documentation standards (Javadoc)
- Testing patterns
- Git practices (commit messages, branches)
- Error handling and security
- Common pitfalls to avoid

**Time to read:** 45-60 minutes (reference document)

---

### 4. [Deployment Guide](./deployment-guide.md) — 864 LOC
**Setup, configuration, and troubleshooting**

Best for: Getting the system running
- Quick start (6 steps, <5 minutes)
- Environment variables (.env configuration)
- Docker Compose setup (full YAML)
- Database migrations (Flyway)
- Google Cloud credentials setup
- Health checks and monitoring
- Troubleshooting (8 scenarios)
- Performance tuning
- Backup and restore procedures
- Security hardening

**Time to read:** 30-45 minutes (skim) or 60-90 minutes (detailed)

---

### 5. [Project Overview & PDR](./project-overview-pdr.md) — 800+ LOC
**Product requirements and project roadmap**

Best for: Understanding what the project does and where it's going
- Product vision and mission
- Functional requirements (8 FRs)
- Non-functional requirements (performance, scalability, etc.)
- Architecture requirements
- Implementation phases (Phases 1-10 complete)
- Success criteria and metrics
- Risk assessment
- Timeline and roadmap
- Budget and resources
- Glossary of terms

**Time to read:** 40-60 minutes

---

### 6. [Project Changelog](./project-changelog.md) — 300+ LOC (NEW)
**Detailed record of all significant changes, features, and fixes**

Best for: Understanding what was built and when
- Phase-by-phase feature breakdown
- Breaking changes and migration paths
- Performance impact of changes
- Database schema evolution
- Files changed per phase
- Security considerations
- Testing coverage per phase

**Time to read:** 15-30 minutes (reference document)

---

## Common Tasks

### "I just joined the team, where do I start?"
1. Read: [Code Standards](./code-standards.md) (mandatory)
2. Skim: [Codebase Summary](./codebase-summary.md)
3. Review: [Deployment Guide](./deployment-guide.md) quick start
4. Set up: Run `docker compose up` and explore the code

**Time:** ~2 hours

### "I need to deploy this to production"
1. Review: [Deployment Guide](./deployment-guide.md) prerequisites
2. Configure: `.env` file with real credentials
3. Setup: Google Cloud credentials
4. Deploy: Follow quick start (6 steps)
5. Monitor: Health checks and logs
6. Troubleshoot: Refer to troubleshooting section

**Time:** ~30 minutes (first time), ~5 minutes (subsequent)

### "I need to understand the architecture"
1. Read: [System Architecture](./system-architecture.md)
2. Study: Data flow diagrams (9 steps)
3. Understand: Service dependencies
4. Reference: [Codebase Summary](./codebase-summary.md) for implementation details

**Time:** ~1 hour

### "I need to add a new feature"
1. Review: [Code Standards](./code-standards.md)
2. Identify: Relevant services in [Codebase Summary](./codebase-summary.md)
3. Understand: Service interactions in [System Architecture](./system-architecture.md)
4. Check: Requirements in [Project Overview & PDR](./project-overview-pdr.md)
5. Code: Following standards, patterns, and naming conventions

**Time:** Varies by feature complexity

### "Something is broken, how do I debug?"
1. Check: [Deployment Guide](./deployment-guide.md) troubleshooting section
2. Review: Logs section for log configuration and viewing
3. Debug: Use debug mode instructions
4. Reference: [System Architecture](./system-architecture.md) for data flows

**Time:** 15-60 minutes depending on issue

### "I need to scale the system"
1. Read: [Deployment Guide](./deployment-guide.md) scaling section
2. Review: [System Architecture](./system-architecture.md) concurrency section
3. Plan: Resource allocation based on requirements
4. Monitor: Health checks and performance metrics

**Time:** 30 minutes planning + implementation varies

---

## Document Statistics

| Document | Size | LOC | Time to Read |
|----------|------|-----|--------------|
| Codebase Summary | 16 KB | 475 | 20-30 min |
| System Architecture | 35 KB | 700+ | 40-50 min |
| Code Standards | 25 KB | 957 | 45-60 min |
| Deployment Guide | 20 KB | 864 | 30-90 min |
| Project Overview & PDR | 25 KB | 800+ | 40-60 min |
| Project Changelog | 18 KB | 300+ | 15-30 min |
| **Total** | **139 KB** | **4,100+** | **3.5-5 hours** |

**Recommendation:** Read all documents for comprehensive understanding (~5 hours total). Reference specific documents as needed during development.

---

## Key Metrics at a Glance

### Project Stats
- **Files:** 47 (reduced from 90 legacy)
- **Lines of Code:** 6,302 (reduced from 11K legacy)
- **Packages:** 8 (with 6 subpackages in services)
- **Docker Services:** 5 (postgres, memoryservice, litellm, vieneu-tts, aimon-backend)

### Performance
- **Turn Latency:** <4s target, ~2-3s actual (90th percentile)
- **Concurrent Sessions:** 10+ per instance supported
- **Setup Time:** <5 minutes (Docker Compose)

### Architecture
- **WebSocket Protocol:** v4 (push-to-talk, simplified)
- **Audio Input:** OPUS frames (48kHz)
- **Audio Output:** PCM16 chunks (16kHz)
- **Memory System:** 3-layer (short/long/episodic)

### Features
- **Speech Recognition:** Google Cloud STT
- **AI:** LiteLLM proxy (OpenAI, Anthropic, etc.)
- **Text-to-Speech:** VieNeu (primary) + Google (fallback)
- **Memory:** PowerMem MCP integration
- **Safety:** Kid Mode content filtering

---

## Glossary of Key Terms

| Term | Meaning |
|------|---------|
| **OPUS** | Audio codec optimized for speech (bandwidth-efficient) |
| **PCM16** | Raw audio format (uncompressed, simple) |
| **TTS** | Text-to-Speech (generate speech from text) |
| **STT** | Speech-to-Text (transcribe audio to text) |
| **LLM** | Large Language Model (AI for text generation) |
| **MCP** | Model Context Protocol (for integrating services) |
| **PowerMem** | 3-layer memory system (short/long/episodic) |
| **VieNeu** | Vietnamese TTS engine (GPU-accelerated) |
| **LiteLLM** | LLM proxy service (multi-provider support) |
| **Kid Mode** | Safety filtering for children's content |
| **WebSocket** | Protocol for persistent bidirectional communication |
| **Circuit Breaker** | Pattern for handling service failures (failover) |

---

## Troubleshooting Documentation Lookup

**Can't find what you're looking for?**

| Question | Document | Section |
|----------|----------|---------|
| Where is the X class? | Codebase Summary | Key Classes or Service Layer |
| How does Y service work? | System Architecture | Architecture Tiers or Data Flow |
| What's the coding standard for Z? | Code Standards | Naming Conventions or Code Style |
| How do I set up the system? | Deployment Guide | Quick Start |
| Why did we choose technology X? | System Architecture | Technology Rationale |
| What are the requirements for feature Y? | Project Overview & PDR | Functional Requirements |
| How do services communicate? | System Architecture | Service Communication |
| How do I debug issue X? | Deployment Guide | Troubleshooting |
| What's the roadmap? | Project Overview & PDR | Timeline & Roadmap |
| What was built in Phase X? | Project Changelog | Phase X section |
| What breaking changes happened? | Project Changelog | Version section |
| How do I handle offline gameplay? | System Architecture | Offline Resilience System |

---

## Contributing to Documentation

When updating documentation:

1. **Update Trigger:** After significant code changes, new features, or architecture changes
2. **Which documents:** Check "Documentation Triggers" in [Deployment Guide](./deployment-guide.md)
3. **Style:** Follow conventions in [Code Standards](./code-standards.md) documentation section
4. **Review:** Ensure examples match actual code
5. **Cross-reference:** Add links to related documents
6. **Verify:** Check all internal links are valid

---

## Feedback & Questions

If documentation is unclear or outdated:
1. File an issue with the specific document and section
2. Provide context on what was confusing
3. Suggest improvements

Documentation should make development faster, not slower. Your feedback helps improve it.

---

## Version History

| Version | Date | Changes |
|---------|------|---------|
| 1.1 | 2026-02-22 | Added Project Changelog; Phase 10 offline resilience documentation |
| 1.0 | 2026-02-15 | Initial documentation suite: 5 documents, 3,674 LOC |

---

**Last Updated:** 2026-02-15
**Status:** Production Ready
**Maintained By:** Documentation Team
