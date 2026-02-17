# AI-MON Code Standards & Conventions

**Last Updated:** 2026-02-15
**Project:** aimon-backend (Java/Quarkus)
**Status:** Active Development

---

## Overview

This document defines coding conventions, patterns, and best practices for the aimon-backend project. All contributors must follow these standards to maintain code consistency, readability, and maintainability.

**Key Principle:** Clean, readable, maintainable code that minimizes cognitive load.

---

## Project Structure Standards

### Package Naming & Organization

**Root Package:** `dev.aimon`

**Package Hierarchy:**
```
dev.aimon
├── client/              # REST/HTTP clients
├── config/              # Configuration classes
├── dto/                 # Data transfer objects
│   ├── ai/             # LLM DTOs
│   ├── conversation/   # Session DTOs
│   ├── powermem/       # Memory service DTOs
│   ├── tts/            # TTS DTOs
│   └── websocket/      # Protocol messages
├── entity/              # JPA entities (database models)
├── model/               # Domain models (in-memory)
├── service/             # Business logic
│   ├── ai/             # LLM services
│   ├── audio/          # Audio processing
│   ├── conversation/   # Conversation flow
│   ├── memory/         # Memory integration
│   ├── stt/            # Speech-to-text
│   └── tts/            # Text-to-speech
└── websocket/           # WebSocket handlers
```

**Rationale:**
- **client/:** External API clients (LiteLLM, PowerMem, Google Cloud)
- **config/:** Application configuration, bean definitions
- **dto/:** Data carriers for inter-layer communication
- **entity/:** JPA persistence annotations
- **model/:** Domain concepts (RobotSession, ConversationSession)
- **service/:** Business logic, orchestration
- **websocket/:** Protocol handlers, session management

### File Naming Conventions

**Java Classes:** PascalCase
```java
AudioPipelineService.java
OpusAudioProcessor.java
GoogleSttService.java
ConversationProcessService.java
```

**Interfaces:** PascalCase with optional `I` prefix (not required)
```java
AudioProcessor.java        // (preferred)
IDataProvider.java         // (acceptable but not preferred)
```

**Test Classes:** TestClass suffix or Test prefix
```java
AudioPipelineServiceTest.java
ConversationProcessServiceTest.java
```

**Configuration Files:**
```
application.properties     # Quarkus default
application-dev.properties # Dev profile
application-prod.properties# Prod profile
```

### File Size Limits

**Hard Limits:**
- **Service classes:** ≤ 300 LOC (modularize if larger)
- **Utility classes:** ≤ 150 LOC
- **Test classes:** ≤ 200 LOC
- **DTO classes:** ≤ 100 LOC

**Exception:** `AimonWebSocket` (277 LOC) — WebSocket handler, approved for size due to protocol complexity.

**Modularization Trigger:** When a class exceeds its category limit:
1. Identify logical separation boundaries
2. Extract into focused sub-classes
3. Use composition over inheritance
4. Update package structure if needed

---

## Naming Conventions

### Classes & Interfaces

**Service Classes:** `[Domain]Service`
```java
AudioPipelineService       // Orchestrates audio processing
ConversationProcessService // Orchestrates conversation
PowerMemService            // Memory integration
```

**Processor Classes:** `[Concept]Processor`
```java
OpusAudioProcessor         // OPUS encoding/decoding
```

**Handler Classes:** `[Event/Protocol]Handler`
```java
AimonWebSocket             // WebSocket handler
```

**Manager Classes:** `[Resource]Manager`
```java
ConversationSessionManager // Session lifecycle
```

**Provider Classes:** `[Service]Provider`
```java
TtsProviderService         // TTS failover logic
```

**Client Classes:** `[Service]Client`
```java
LiteLlmClient
PowerMemClient
```

**DTO Classes:** `[Concept][Type]`
```java
LiteLlmChatRequest
LiteLlmChatResponse
PowerMemObservation
TtsAudioRequest
WebSocketMessage
```

**Entity Classes:** `[Domain]Entity` or `[Domain]` (no suffix)
```java
User                       // User entity (child profile)
Parent                     // Parent entity
BannedKeyword             // Banned keyword entity
```

**Model Classes:** `[Concept]` (business/domain objects)
```java
RobotSession              // In-memory session
ConversationSession       // Conversation context
AudioFrame                // Audio packet wrapper
```

### Variables & Constants

**Local Variables:** camelCase
```java
String robotId = "pi-001";
List<byte[]> audioFrames = new ArrayList<>();
boolean isInterrupted = false;
```

**Constants:** UPPER_SNAKE_CASE
```java
private static final int DEFAULT_SAMPLE_RATE = 16000;
private static final String WEBSOCKET_ENDPOINT = "/ws/audio/{robotId}";
private static final Duration SESSION_TIMEOUT = Duration.ofMinutes(5);
```

**Configuration Properties:** SNAKE_CASE
```properties
ai.litellm.base-url=http://litellm:4000
ai.vieneu-tts.voice-id=Ngoc
quarkus.datasource.jdbc.url=jdbc:postgresql://postgres:5432/aimon
```

**Method Variables:** camelCase
```java
RobotSession session = sessions.get(robotId);
List<AudioFrame> frames = bufferAudioFrames();
```

---

## Code Style & Formatting

### Indentation & Spacing

**Indentation:** 4 spaces (no tabs)
```java
public class AudioPipelineService {
    private static final Logger LOG = Logger.getLogger(AudioPipelineService.class);

    @Inject
    private GoogleSttService sttService;

    public void processAudio(byte[] opusData) {
        List<byte[]> frames = decodeOpus(opusData);
        for (byte[] frame : frames) {
            validateFrame(frame);
        }
    }
}
```

**Line Length:** Max 120 characters
```java
// Good
String systemPrompt = AIConfig.CONVERSATION_STARTER + personality.getTraits();

// Bad (> 120 chars)
String systemPrompt = AIConfig.CONVERSATION_STARTER + personality.getTraits() + " Here is additional context...";
```

**Blank Lines:**
- 1 blank line between methods
- 2 blank lines between classes in same file (rare)
- Blank line after imports, before class declaration

### Imports

**Order:**
1. Java standard library
2. Third-party libraries
3. Project packages

**Example:**
```java
import java.util.*;
import java.time.Instant;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.runtime.Quarkus;

import dev.aimon.service.AudioPipelineService;
import dev.aimon.dto.websocket.WebSocketMessage;
```

**Avoid:** Wildcard imports (`import java.util.*`) — list specific classes.

### Brackets & Braces

**K&R Style (Quarkus standard):**
```java
public void handleMessage(WebSocketMessage msg) {
    if (msg.getType().equals("audio_start")) {
        startAudioCapture(msg);
    } else {
        handleOtherMessage(msg);
    }
}
```

**Single-line conditional — allowed if simple:**
```java
if (isInterrupted) return;
boolean valid = frames.size() > 0 && confidence > 0.8;
```

---

## Java & Quarkus Patterns

### Dependency Injection

**Use Quarkus `@Inject`:**
```java
@ApplicationScoped
public class ConversationProcessService {

    @Inject
    private PowerMemService memoryService;

    @Inject
    private LiteLlmAIService aiService;

    @Inject
    ObjectMapper objectMapper;

    // Constructor injection avoided (use field injection in Quarkus)
}
```

**Scope Rules:**
| Scope | When to Use | Example |
|-------|-----------|---------|
| `@Singleton` | Stateless, expensive init | ObjectMapper, clients |
| `@ApplicationScoped` | App-lifetime, shared | Services |
| `@RequestScoped` | Per-request state | (rare in WebSocket context) |
| `@Dependent` | Default, no special handling | (avoid, prefer scoped) |

### Error Handling

**Try-Catch Pattern:**
```java
try {
    // Primary operation
    String transcript = googleSttService.transcribeAudio(pcmData);
} catch (GoogleApiException e) {
    LOG.error("STT failed, returning error to client", e);
    // Graceful degradation
    String transcript = "[audio not recognized]";
}
```

**Null Checks:**
```java
// Avoid: NullPointerException
String text = response.getText(); // ❌

// Good: Explicit null check
String text = response != null && response.getText() != null
    ? response.getText()
    : "[no response]";

// Better: Use Optional
Optional<String> text = Optional.ofNullable(response)
    .map(r -> r.getText());
```

**Circuit Breaker Pattern (TTS):**
```java
public class TtsProviderService {
    public AudioResponse synthesize(String text) {
        try {
            return vieNeuService.synthesize(text);  // Primary
        } catch (Exception e) {
            LOG.warn("VieNeu TTS failed, falling back to Google", e);
            try {
                return googleTtsService.synthesize(text);  // Fallback
            } catch (Exception e2) {
                LOG.error("Both TTS providers failed", e2);
                throw new TtsException("No TTS available", e2);
            }
        }
    }
}
```

### Resource Management

**Auto-closing Resources:**
```java
try (InputStream stream = connection.getInputStream()) {
    byte[] data = stream.readAllBytes();
    return processData(data);
} catch (IOException e) {
    LOG.error("Failed to read stream", e);
    throw new ServiceException("I/O error", e);
}
```

**WebSocket Session Cleanup:**
```java
@OnClose
public void onClose(String robotId) {
    RobotSession session = sessions.remove(robotId);
    if (session != null) {
        session.cleanup();
        LOG.info("Session closed for robot: {}", robotId);
    }
}
```

### Async & Reactive Patterns

**Quarkus Mutiny (Reactive):**
```java
@POST
@Path("/process")
public Uni<ConversationResponse> processConversation(ConversationRequest req) {
    return Uni.createFrom().item(req)
        .onItem().transform(r -> buildPrompt(r))
        .onItem().transformToUni(prompt -> callLlm(prompt))
        .onItem().transform(response -> formatResponse(response))
        .onFailure().recoverWithItem(new ConversationResponse("Error"));
}
```

**Synchronous (Blocking) — Preferred in aimon-backend:**
```java
// WebSocket handlers are blocking by default
public void onMessage(WebSocketMessage msg) {
    String text = msg.getText();
    String response = conversationService.process(text);
    sendMessage(response);
}
```

---

## Documentation Standards

### Javadoc Comments

**Class-level Javadoc:**
```java
/**
 * Orchestrates the complete conversation pipeline.
 *
 * Responsibilities:
 * - Personality injection
 * - Memory context retrieval
 * - LLM streaming
 * - Safety filtering (Kid Mode)
 * - TTS orchestration
 *
 * @see PowerMemService for memory integration
 * @see TtsProviderService for TTS failover
 */
@ApplicationScoped
public class ConversationProcessService {
    // ...
}
```

**Method-level Javadoc:**
```java
/**
 * Processes a single conversation turn.
 *
 * @param transcript User input text (result of STT)
 * @param userId Child user ID
 * @return Streamed conversation response
 * @throws ConversationException if LLM or memory service fails
 */
public ConversationResponse processUserInput(String transcript, String userId) {
    // ...
}
```

**Field Javadoc:**
```java
/**
 * In-memory session storage. Key: robotId, Value: active RobotSession.
 * Thread-safe for concurrent access.
 */
private final Map<String, RobotSession> sessions = new ConcurrentHashMap<>();
```

### Inline Comments

**Use sparingly — code should be self-documenting.**

```java
// Good: Explains "why", not "what"
// WebSocket v4 uses OPUS input (bandwidth-efficient) but PCM16 output (simple for Pi)
if (isInputFrame) {
    decodeOpus(frame);
} else {
    streamPcm16(frame);
}

// Bad: Obvious from code, adds noise
// Check if robotId is not null
if (robotId != null) {
    // ...
}
```

**Complex Logic Comment Template:**
```java
// [Domain] [Action] [Rationale/Result]
// Example:
// Audio pipeline downsamples 48→16kHz because Google STT expects 16kHz mono
int downsampledRate = 16000;
byte[] resampled = resample(audioData, 48000, downsampledRate);
```

---

## Testing Standards

### Unit Test Structure

**Naming:** `[ClassUnderTest]Test`

**Template:**
```java
public class AudioPipelineServiceTest {

    private AudioPipelineService service;
    private GoogleSttService mockSttService;

    @BeforeEach
    public void setUp() {
        mockSttService = Mockito.mock(GoogleSttService.class);
        service = new AudioPipelineService();
        service.sttService = mockSttService;  // Inject mock
    }

    @Test
    public void testProcessAudioReturnsTranscript() {
        // Arrange
        byte[] opusData = createMockOpusData();
        String expectedTranscript = "Xin chào";
        Mockito.when(mockSttService.transcribe(any()))
            .thenReturn(expectedTranscript);

        // Act
        String result = service.processAudio(opusData);

        // Assert
        assertEquals(expectedTranscript, result);
        Mockito.verify(mockSttService).transcribe(any());
    }

    @Test
    public void testProcessAudioThrowsOnInvalidData() {
        // Arrange
        byte[] invalidData = new byte[0];

        // Act & Assert
        assertThrows(IllegalArgumentException.class,
            () -> service.processAudio(invalidData));
    }
}
```

**Test Categories:**
- **Unit Tests:** Single class, mocked dependencies (fast, run on every commit)
- **Integration Tests:** Multiple services, real database (slower, run pre-push)
- **End-to-End Tests:** Full pipeline with mock Pi client (slowest, run on CI/CD)

### Mocking & Test Data

**Mock Patterns:**
```java
// Mock external service
GoogleSttService mockSttService = Mockito.mock(GoogleSttService.class);
Mockito.when(mockSttService.transcribe(any()))
    .thenReturn("test transcript");

// Mock WebSocket session
WebSocketConnection mockWs = Mockito.mock(WebSocketConnection.class);
Mockito.doNothing().when(mockWs).sendMessage(any());
```

**Test Data Builders:**
```java
public class TestDataFactory {
    public static RobotSession createTestSession(String robotId) {
        return new RobotSession(
            robotId,
            UUID.randomUUID().toString(),
            SessionState.IDLE,
            Instant.now()
        );
    }

    public static byte[] createMockOpusFrame(int sizeBytes) {
        return new byte[sizeBytes];
    }
}
```

**Avoid:** Hardcoded test data in test methods (use factories instead).

---

## Git & Version Control

### Commit Messages

**Format:** Conventional Commits

```
<type>(<scope>): <subject>

<body>

<footer>
```

**Types:**
- `feat:` New feature
- `fix:` Bug fix
- `refactor:` Code reorganization (no functionality change)
- `test:` Test additions or modifications
- `docs:` Documentation changes
- `chore:` Build, dependencies, tooling

**Examples:**
```
feat(websocket): implement v4 push-to-talk protocol

Implement WebSocket v4 handler with simplified push-to-talk:
- hello/hello_ack handshake
- audio_start/audio_stop frame collection
- interrupt support
- Clean state machine (IDLE -> LISTENING -> PROCESSING -> RESPONDING)

Fixes #123

feat(memory): add PowerMem 3-layer integration

Integrate PowerMem MCP server for persistent memory:
- Short-term: last 5 exchanges
- Long-term: vector-searched facts
- Episodic: timestamped observations

Closes #124

fix(tts): handle VieNeu service unavailability

Add circuit breaker pattern to TTS provider:
- Primary: VieNeuTtsService
- Fallback: GoogleTtsStreamingService on failure
- Log failures for monitoring

Fixes #125

refactor(service): modularize ConversationProcessService

Split 500-line service into focused components:
- ConversationProcessService (orchestration)
- ConversationSessionManager (lifecycle)
- ResponseStreamService (output)

No functional changes, improves maintainability.
```

### Branch Naming

**Format:** `<type>/<description>`

```
feature/websocket-v4-protocol
feature/memory-integration
fix/tts-failover-logic
refactor/service-modularization
docs/architecture-documentation
```

### Code Review Checklist

Before merging:
- [ ] Code follows naming conventions
- [ ] File sizes within limits (modularized if needed)
- [ ] No magic numbers (constants defined)
- [ ] Error handling present (try-catch, null checks)
- [ ] Tests added or updated
- [ ] Javadoc for public classes/methods
- [ ] No hardcoded secrets or credentials
- [ ] Commit messages follow conventional format
- [ ] No merge conflicts

---

## Configuration Management

### Environment Variables

**File:** `.env` (git-ignored) or `.env.example` (checked in)

```bash
# LiteLLM
AI_LITELLM_BASE_URL=http://litellm:4000
AI_LITELLM_MODEL=gpt-4o-mini

# Google Cloud
GOOGLE_APPLICATION_CREDENTIALS=/path/to/credentials.json

# Database
QUARKUS_DATASOURCE_USERNAME=aimon
QUARKUS_DATASOURCE_PASSWORD=secure_password
QUARKUS_DATASOURCE_JDBC_URL=jdbc:postgresql://postgres:5432/aimon

# VieNeu TTS
AI_VIENEU_TTS_BASE_URL=http://vieneu-tts:5001
AI_VIENEU_TTS_VOICE_ID=Ngoc

# PowerMem
QUARKUS_REST_CLIENT_POWERMEM_API_URL=http://memoryservice:8003
```

**Access Pattern:**
```java
@ConfigProperty(name = "ai.litellm.base-url")
String litellmBaseUrl;

// Or via injection:
@Inject
@ConfigProperty(name = "ai.vieneu-tts.voice-id")
String vieNeuVoiceId;
```

### Feature Flags

**In AIConfig:**
```java
@ConfigProperty(name = "feature.kid-mode.enabled", defaultValue = "true")
boolean kidModeEnabled;

@ConfigProperty(name = "feature.memory.enabled", defaultValue = "true")
boolean memoryEnabled;
```

**Usage:**
```java
if (kidModeEnabled) {
    response = filterUnsafeContent(response);
}
```

---

## Performance & Optimization

### Caching

**Service-level Caching:**
```java
@ApplicationScoped
public class ContextRetrievalService {

    private final Map<String, MemoryContext> contextCache = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MILLIS = 60000; // 1 minute

    public MemoryContext getContext(String userId) {
        String cacheKey = userId;
        MemoryContext cached = contextCache.get(cacheKey);

        if (cached != null && isCacheValid(cached)) {
            return cached;
        }

        MemoryContext fresh = fetchFromPowerMem(userId);
        contextCache.put(cacheKey, fresh);
        return fresh;
    }
}
```

### Connection Pooling

**Database:**
```properties
quarkus.datasource.jdbc.max-size=10
quarkus.datasource.jdbc.min-size=2
```

**REST Clients:**
```java
@RegisterRestClient(configKey = "litellm-api")
@Path("/v1")
public interface LiteLlmClient {
    @POST
    @Path("/chat/completions")
    Uni<ChatResponse> chat(ChatRequest request);
}
```

---

## Security Standards

### Credential Handling

**Never hardcode:**
```java
// ❌ BAD
String apiKey = "sk-12345-abcde";
String dbPassword = "root123";

// ✅ GOOD
@ConfigProperty(name = "ai.litellm.api-key")
String apiKey;
```

**Validation:**
```java
public RobotSession createSession(String robotId, String parentId) {
    if (robotId == null || robotId.isBlank()) {
        throw new IllegalArgumentException("robotId cannot be empty");
    }
    if (!robotId.matches("^[a-zA-Z0-9-_]{1,50}$")) {
        throw new IllegalArgumentException("Invalid robotId format");
    }
    return new RobotSession(robotId, parentId, SessionState.IDLE, Instant.now());
}
```

### Logging

**Sanitize Sensitive Data:**
```java
// ❌ BAD - logs full message with potential PII
LOG.info("Received message: {}", message.toString());

// ✅ GOOD - logs only safe fields
LOG.info("Received message type: {}, size: {} bytes",
    message.getType(),
    message.getPayloadSize());
```

---

## Dependency Management

### Maven Practices

**POM Organization:**
```xml
<properties>
    <maven.compiler.source>21</maven.compiler.source>
    <maven.compiler.target>21</maven.compiler.target>
    <quarkus.platform.version>3.24.5</quarkus.platform.version>
</properties>

<dependencies>
    <!-- Quarkus Core -->
    <dependency>
        <groupId>io.quarkus</groupId>
        <artifactId>quarkus-core</artifactId>
    </dependency>

    <!-- REST & WebSocket -->
    <dependency>
        <groupId>io.quarkus</groupId>
        <artifactId>quarkus-websockets-next</artifactId>
    </dependency>

    <!-- Data & Persistence -->
    <dependency>
        <groupId>io.quarkus</groupId>
        <artifactId>quarkus-hibernate-orm-panache</artifactId>
    </dependency>

    <!-- Testing -->
    <dependency>
        <groupId>io.quarkus</groupId>
        <artifactId>quarkus-junit5</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

**Exclude Unused Dependencies:**
```xml
<exclusions>
    <exclusion>
        <groupId>org.slf4j</groupId>
        <artifactId>slf4j-log4j12</artifactId>
    </exclusion>
</exclusions>
```

---

## Common Pitfalls & How to Avoid

| Pitfall | Example | Fix |
|---------|---------|-----|
| **Mutable Shared State** | Static field for session map | Use `ConcurrentHashMap`, immutable where possible |
| **Null Dereference** | `user.getProfile().getName()` | Use Optional, null checks, or default values |
| **Hardcoded Configuration** | `String url = "http://localhost:8080"` | Use `@ConfigProperty` |
| **Silent Failures** | Empty catch block | Log error, rethrow, or handle gracefully |
| **Mixing Concerns** | Service method handles HTTP & business logic | Separate WebSocket, service, and DAO layers |
| **Large Methods** | 200+ line method | Extract sub-methods, keep <50 lines |
| **Resource Leaks** | Open stream without try-with-resources | Always use try-with-resources |
| **Poor Test Names** | `test1()`, `testMethod()` | Use descriptive names: `testProcessAudioReturnsTranscript()` |

---

## Tools & Linting

### Maven Plugins

**Compile Check:**
```bash
./mvnw clean compile
```

**Testing:**
```bash
./mvnw test
```

**Code Quality (optional):**
```bash
./mvnw spotbugs:check  # Find potential bugs
```

**Formatting (optional):**
```bash
./mvnw spotless:apply  # Auto-format code
```

### IDE Configuration

**VS Code / IntelliJ IDEA:**
- Set indentation: 4 spaces
- Line length: 120 characters
- Import organization: Java default
- Code style: Google Java Style (or K&R variant)

---

## Refactoring Guidelines

### When to Refactor

**Safe to refactor if:**
- Comprehensive tests exist
- No functional change intended
- Single responsibility principle improved

**Refactoring patterns:**
1. **Extract Method:** Large method → smaller, focused methods
2. **Extract Class:** Class doing too much → split concerns
3. **Rename:** Unclear names → descriptive names
4. **Remove Duplication:** Copy-paste code → shared utility

**Example:**
```java
// Before: 300-line method
public void handleConversationTurn(WebSocketMessage msg) {
    // Step 1: Parse input (50 lines)
    // Step 2: Validate input (30 lines)
    // Step 3: Process conversation (100 lines)
    // Step 4: Format response (60 lines)
    // Step 5: Send response (20 lines)
}

// After: Modularized
public void handleConversationTurn(WebSocketMessage msg) {
    WebSocketRequest request = parseRequest(msg);
    validateRequest(request);
    ConversationResponse response = processConversation(request);
    WebSocketMessage responseMsg = formatResponse(response);
    sendResponse(responseMsg);
}

private WebSocketRequest parseRequest(WebSocketMessage msg) { /* ... */ }
private void validateRequest(WebSocketRequest req) { /* ... */ }
private ConversationResponse processConversation(WebSocketRequest req) { /* ... */ }
private WebSocketMessage formatResponse(ConversationResponse resp) { /* ... */ }
private void sendResponse(WebSocketMessage msg) { /* ... */ }
```

---

## References

- **Style Guide:** Google Java Style Guide (adapted for Quarkus)
- **Patterns:** Refactoring by Fowler, Clean Code by Martin
- **Framework:** Quarkus 3.24.5 Best Practices
- **Project:** aimon-backend README & inline documentation
