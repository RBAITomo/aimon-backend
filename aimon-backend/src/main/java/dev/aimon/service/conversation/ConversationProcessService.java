package dev.aimon.service.conversation;

import dev.aimon.dto.conversation.ConversationMessageRequest;
import dev.aimon.dto.conversation.ConversationMessageResponse;
import dev.aimon.dto.pet.PetStatusDto;
import dev.aimon.model.ConversationSession;
import dev.aimon.service.ai.LiteLlmAIService;
import dev.aimon.service.memory.ContextRetrievalService;
import dev.aimon.service.memory.PowerMemService;
import dev.aimon.dto.pet.QuestDto;
import dev.aimon.service.pet.PetPromptAssembler;
import dev.aimon.service.pet.PetProfileService;
import dev.aimon.service.pet.QuestEvaluationService;
import dev.aimon.service.world.WorldLoreService;
import dev.aimon.entity.pet.PetProfile;
import dev.aimon.entity.world.WorldLore;
import dev.aimon.service.pet.QuestService;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Conversation processing service for user-robot interactions.
 * Handles message streaming with PowerMem context integration.
 *
 * Simplified version focusing on:
 * - LLM streaming with memory context
 * - Session management with in-memory history
 * - PowerMem integration for long-term memory
 */
@ApplicationScoped
public class ConversationProcessService {

    private static final Logger LOG = Logger.getLogger(ConversationProcessService.class);

    // Pet system prompt is now built dynamically in buildEnhancedPrompt()
    // based on pet stage, mood, affinity, and variant state

    @Inject
    LiteLlmAIService aiService;

    @Inject
    ConversationSessionManager sessionManager;

    @Inject
    ContextRetrievalService contextRetrievalService;

    @Inject
    PowerMemService memoryService;

    @Inject
    PetPromptAssembler petPromptAssembler;

    @Inject
    PetProfileService petProfileService;

    @Inject
    QuestService questService;

    @Inject
    QuestEvaluationService questEvaluationService;

    @Inject
    WorldLoreService worldLoreService;

    @Inject
    dev.aimon.service.combat.TastelessSpawnService tastelessSpawnService;

    @Inject
    dev.aimon.service.world.NoirQuestService noirQuestService;

    @Inject
    TopicClassifier topicClassifier;

    @Inject
    AdaptiveInterestService adaptiveInterestService;

    @Inject
    dev.aimon.service.world.TravelPromptBuilder travelPromptBuilder;

    @Inject
    dev.aimon.service.world.TravelMarkerParser travelMarkerParser;

    @Inject
    dev.aimon.service.world.TravelService travelService;

    @ConfigProperty(name = "memory.mcp.enabled", defaultValue = "true")
    boolean powerMemEnabled;

    @ConfigProperty(name = "conversation.kid-mode.enabled", defaultValue = "false")
    boolean kidModeEnabled;

    @ConfigProperty(name = "conversation.kid-mode.age-group", defaultValue = "6-8")
    String kidModeAgeGroup;

    /**
     * Process message with streaming response (sentence-by-sentence).
     * Used for real-time TTS integration in WebSocket flow.
     *
     * Flow:
     * 1. Initialize session and retrieve memory context
     * 2. Build enhanced prompt with personality + memory + history
     * 3. Stream AI response sentence-by-sentence via callbacks
     * 4. Store conversation turn in session history
     *
     * @param request Conversation message request
     * @param onSentence Callback invoked for each complete sentence
     * @param onComplete Callback invoked when streaming completes
     * @param onError Callback invoked on error
     */
    @ActivateRequestContext
    public void processMessageStreaming(
        ConversationMessageRequest request,
        Consumer<String> onSentence,
        Runnable onComplete,
        Consumer<Throwable> onError
    ) {
        LOG.infof("Processing streaming message from user %s, session %s",
                  request.getUserId(), request.getSessionId());

        try {
            // Step 1: Ensure session is initialized
            ConversationSession session = sessionManager.getOrCreateSession(
                Integer.parseInt(request.getUserId()),
                request.getSessionId()
            );

            // Step 2: Get pet status from session cache (loaded at session creation)
            Long userId = Long.parseLong(request.getUserId());
            PetStatusDto petStatus = session.getCachedPetStatus();
            if (petStatus == null) {
                petStatus = petProfileService.getStatus(userId);
                session.setCachedPetStatus(petStatus);
            }

            // EGG stage: skip LLM call, return early
            if ("EGG".equalsIgnoreCase(petStatus.stage())) {
                LOG.debugf("Pet in EGG stage for user %s, skipping LLM call", request.getUserId());
                onComplete.run();
                return;
            }

            // Step 2b: Load interests once per session (cached)
            if (!session.hasTopInterests()) {
                Integer robotId = parseRobotId(request);
                java.util.List<String> interests = adaptiveInterestService.getTopInterests(robotId);
                session.setCachedTopInterests(interests);
                LOG.debugf("Loaded top interests for session %s: %s", request.getSessionId(), interests);
            }

            // Step 3: Build enhanced prompt with pet personality + memory context
            String enhancedPrompt = buildEnhancedPrompt(request, session, petStatus);

            // Step 4: Get conversation history as proper multi-turn messages
            java.util.List<dev.aimon.dto.ai.LiteLlmChatMessage> historyMessages = session.getHistoryMessages();

            // Step 5: Collect full response for history storage
            StringBuilder fullResponseBuilder = new StringBuilder();

            // Step 6: Stream from AI service with proper multi-turn history
            aiService.generateResponseStreaming(
                request.getMessage(),
                enhancedPrompt,
                historyMessages,
                // onSentence: forward to caller and collect
                sentence -> {
                    LOG.infof("LLM sentence [%d]: %s", fullResponseBuilder.length(), sentence);
                    fullResponseBuilder.append(sentence).append(" ");
                    // Strip quest and travel markers before TTS
                    String clean = questEvaluationService.stripQuestMarker(sentence);
                    clean = travelMarkerParser.strip(clean);
                    if (!clean.isEmpty()) {
                        onSentence.accept(clean);
                    }
                },
                // onComplete: store in session history and notify caller
                () -> {
                    String fullResponse = fullResponseBuilder.toString().trim();
                    LOG.infof("LLM streaming completed. Response length: %d chars. Full response: %s", fullResponse.length(), fullResponse);

                    // Process quest result off IO thread (needs JTA transaction)
                    String questResult = questEvaluationService.parseQuestResult(fullResponse);
                    if (questResult != null) {
                        Long uid = Long.parseLong(request.getUserId());
                        CompletableFuture.runAsync(() -> {
                            try {
                                QuestDto quest = questService.getPendingQuest(uid);
                                if (quest != null) {
                                    questEvaluationService.processResult(uid, questResult, quest);
                                }
                            } catch (Exception e) {
                                LOG.errorf(e, "Error processing quest result for user %s", uid);
                            }
                        }, Infrastructure.getDefaultExecutor());
                    }

                    // Strip quest and travel markers before storing
                    fullResponse = questEvaluationService.stripQuestMarker(fullResponse);
                    fullResponse = travelMarkerParser.strip(fullResponse);

                    // Store turn in session history (in-memory)
                    session.addTurn(request.getMessage(), fullResponse);

                    LOG.debugf("Stored turn in session history (user: %s, total: %d turns)",
                               request.getUserId(), session.getHistorySize());

                    // Record to PowerMem asynchronously (optional)
                    if (powerMemEnabled) {
                        recordToPowerMemAsync(request, fullResponse);
                    }

                    onComplete.run();

                    // Noir quest response processing (async)
                    Long uid = Long.parseLong(request.getUserId());
                    if (fullResponse.contains("[NOIR_RESPONSE]")) {
                        String childMsg = request.getMessage();
                        CompletableFuture.runAsync(() -> {
                            try {
                                var result = noirQuestService.processResponse(uid, childMsg);
                                LOG.infof("Noir quest result for user %d: passed=%s, score=%d",
                                    uid, result.passed(), result.score());
                            } catch (Exception e) {
                                LOG.warnf("Noir quest processing failed: %s", e.getMessage());
                            }
                        }, Infrastructure.getDefaultExecutor());
                    }

                    // Travel marker processing (async)
                    String travelCode = travelMarkerParser.parse(fullResponseBuilder.toString());
                    if (travelCode != null) {
                        String tCode = travelCode;
                        CompletableFuture.runAsync(() -> {
                            try {
                                boolean success = travelService.travel(uid, tCode);
                                LOG.infof("Travel for user %d to %s: %s", uid, tCode, success ? "OK" : "FAILED");
                            } catch (Exception e) {
                                LOG.warnf("Travel processing failed: %s", e.getMessage());
                            }
                        }, Infrastructure.getDefaultExecutor());
                    }

                    // Tasteless spawn check (async, non-blocking, after turn completes)
                    CompletableFuture.runAsync(() -> {
                        try {
                            PetProfile pet = petProfileService.getProfile(uid);
                            if (pet != null && !"EGG".equalsIgnoreCase(pet.getStage().name())) {
                                tastelessSpawnService.checkSpawn(pet);
                            }
                        } catch (Exception e) {
                            LOG.warnf("Tasteless spawn check failed: %s", e.getMessage());
                        }
                    }, Infrastructure.getDefaultExecutor());
                },
                // onError: forward to caller
                error -> {
                    LOG.errorf(error, "Error in streaming conversation: %s", error.getMessage());
                    onError.accept(error);
                }
            );

        } catch (Exception e) {
            LOG.errorf(e, "Error starting streaming conversation: %s", e.getMessage());
            onError.accept(e);
        }
    }

    /**
     * Build enhanced prompt with pet personality, memory context, and conversation history.
     *
     * Layers:
     * 1. Pet system prompt (dynamic: stage + mood + affinity + variant)
     * 2. Kid Mode context (if enabled)
     * 3. PowerMem context (3-layer retrieval: search → timeline → fetch)
     * 4. Conscious context (user profile from session)
     * 5. Conversation history (recent turns)
     * 6. Current message
     */
    private String buildEnhancedPrompt(
        ConversationMessageRequest request,
        ConversationSession session,
        PetStatusDto petStatus
    ) {
        StringBuilder promptBuilder = new StringBuilder();

        // 1. Pet System Prompt — cached per session, rebuilt on invalidation
        if (!session.hasSystemPrompt()) {
            String petPrompt = petPromptAssembler.buildPetSystemPrompt(
                petStatus,
                session.getCachedChildName(),
                session.getCachedChildAge()
            );
            session.setCachedSystemPrompt(petPrompt);
        }

        String cachedPrompt = session.getCachedSystemPrompt();
        if (cachedPrompt != null && !cachedPrompt.isBlank()) {
            promptBuilder.append(cachedPrompt).append("\n\n");
        }

        // 1a. Adaptive interests prompt
        java.util.List<String> topInterests = session.getCachedTopInterests();
        if (topInterests != null && !topInterests.isEmpty()) {
            String interestsPrompt = adaptiveInterestService.formatInterestsPrompt(topInterests);
            if (!interestsPrompt.isBlank()) {
                promptBuilder.append(interestsPrompt).append("\n");
            }
        }

        // 1b. World Lore context (AMBIENT only) — pass interests as Vietnamese labels for tag matching
        Long userId = Long.parseLong(request.getUserId());
        if (petStatus.stage() != null && !"EGG".equalsIgnoreCase(petStatus.stage())) {
            try {
                PetProfile petProfile = petProfileService.getProfile(userId);
                if (petProfile == null) throw new IllegalStateException("No pet profile");
                String worldCode = petProfile.getActiveWorld() != null
                    ? petProfile.getActiveWorld() : "COTTON_LAND";
                // Convert topic codes to Vietnamese labels to match seed interest_tags
                java.util.List<String> loreInterests = (topInterests != null)
                    ? topInterests.stream()
                        .map(code -> TopicClassifier.TOPIC_DISPLAY_NAMES.getOrDefault(code, code))
                        .toList()
                    : null;
                java.util.List<WorldLore> lore = worldLoreService.getUnlockedLore(
                    worldCode, petStatus.level(), loreInterests);
                String lorePrompt = worldLoreService.formatLorePrompt(lore);
                if (!lorePrompt.isBlank()) {
                    promptBuilder.append(lorePrompt).append("\n");
                }
            } catch (Exception e) {
                LOG.warnf("Lore retrieval failed, continuing without: %s", e.getMessage());
            }
        }

        // 1c. Quest context (if pending quest exists)
        QuestDto pendingQuest = questService.getPendingQuest(userId);
        if (pendingQuest != null) {
            String questPrompt = petPromptAssembler.buildQuestPrompt(pendingQuest);
            promptBuilder.append(questPrompt).append("\n");
        }

        // 1d. Noir quest context (at Bitter Hollow, eligible)
        try {
            PetProfile petForNoir = petProfileService.getProfile(userId);
            if (petForNoir != null && "BITTER_HOLLOW".equals(petForNoir.getCurrentLocation())
                && noirQuestService.isEligible(userId)) {
                String noirQuestion = noirQuestService.getNextQuestion(userId);
                if (noirQuestion != null) {
                    promptBuilder.append("=== Noir Coneko Quest ===\n");
                    promptBuilder.append("Noir Coneko đang ở đây. Hãy hỏi bé (trong vai Noir, giọng trầm và bí ẩn): ");
                    promptBuilder.append(noirQuestion).append("\n");
                    promptBuilder.append("Sau khi bé trả lời, hãy kết thúc bằng [NOIR_RESPONSE] để đánh dấu câu trả lời cần đánh giá.\n\n");
                }
            }
        } catch (Exception e) {
            LOG.warnf("Noir quest context failed: %s", e.getMessage());
        }

        // 1e. Travel context (sub-location flavor + suggestion + marker instruction)
        try {
            PetProfile petForTravel = petProfileService.getProfile(userId);
            if (petForTravel != null) {
                String travelPrompt = travelPromptBuilder.build(
                    petForTravel.getCurrentLocation(), topInterests, session);
                if (!travelPrompt.isBlank()) {
                    promptBuilder.append(travelPrompt);
                }
            }
        } catch (Exception e) {
            LOG.warnf("Travel context failed: %s", e.getMessage());
        }

        // 2. Kid Mode Context
        if (kidModeEnabled) {
            promptBuilder.append("=== Kid Mode Activated ===\n");
            promptBuilder.append("You are speaking with a child (age group: ").append(kidModeAgeGroup).append(").\n");
            promptBuilder.append("Use simple vocabulary, short sentences, and encouraging tone.\n\n");
        }

        // 3. PowerMem Context Retrieval (3-layer)
        if (powerMemEnabled) {
            try {
                Integer robotId = parseRobotId(request);
                String memoryContext = contextRetrievalService.retrieveContextForConversation(
                    request.getMessage(),
                    robotId
                );
                if (memoryContext != null && !memoryContext.isBlank()) {
                    promptBuilder.append(memoryContext).append("\n");
                    LOG.debugf("Added PowerMem context to prompt (%d chars)", memoryContext.length());
                }
            } catch (Exception e) {
                LOG.warnf("PowerMem context retrieval failed, continuing without: %s", e.getMessage());
            }
        }

        // 4. Conscious Context (user profile)
        String consciousPrompt = session.consciousSystemPrompt();
        if (consciousPrompt != null && !consciousPrompt.isBlank()) {
            promptBuilder.append("=== User Context (Permanent Memory) ===\n");
            promptBuilder.append(consciousPrompt).append("\n\n");
        }

        // 5. Conversation history is now passed as proper multi-turn messages
        //    (not embedded in prompt text) — see processMessageStreaming()

        return promptBuilder.toString();
    }

    /**
     * Parse robot ID from request.
     * Currently uses userId as robotId (1:1 mapping).
     */
    private Integer parseRobotId(ConversationMessageRequest request) {
        try {
            return Integer.parseInt(request.getUserId());
        } catch (NumberFormatException e) {
            LOG.warnf("Failed to parse robotId from userId: %s", request.getUserId());
            return null;
        }
    }

    /**
     * Record conversation turn to PowerMem asynchronously.
     * Fire-and-forget operation to avoid blocking conversation flow.
     */
    private void recordToPowerMemAsync(ConversationMessageRequest request, String response) {
        // Run off Vert.x event loop — topicClassifier.classify() may call blocking LLM REST
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                Integer robotId = parseRobotId(request);

                // Classify topics from user message
                java.util.List<String> topics = topicClassifier.classify(request.getMessage());

                // Record user message (with topics in metadata)
                memoryService.recordAsync(
                    "User said: " + request.getMessage(),
                    new dev.aimon.service.memory.MemoryMetadataBuilder()
                        .robotId(String.valueOf(robotId))
                        .sessionId(request.getSessionId())
                        .category("conversation")
                        .type("user_message")
                        .importance(0.5)
                        .topics(topics)
                        .build()
                );

                // Record AI response
                memoryService.recordAsync(
                    "Pet responded: " + response,
                    new dev.aimon.service.memory.MemoryMetadataBuilder()
                        .robotId(String.valueOf(robotId))
                        .sessionId(request.getSessionId())
                        .category("conversation")
                        .type("ai_response")
                        .importance(0.5)
                        .build()
                );

                LOG.debugf("Queued conversation turn for PowerMem recording (session: %s)",
                           request.getSessionId());
            } catch (Exception e) {
                LOG.warnf(e, "Failed to record conversation to PowerMem: %s", e.getMessage());
            }
        });
    }

    /**
     * Non-streaming process message (for backward compatibility or batch processing).
     * Returns complete response after LLM finishes.
     */
    @ActivateRequestContext
    public ConversationMessageResponse processMessage(ConversationMessageRequest request) {
        LOG.infof("Processing non-streaming message from user %s, session %s",
                  request.getUserId(), request.getSessionId());

        try {
            ConversationSession session = sessionManager.getOrCreateSession(
                Integer.parseInt(request.getUserId()),
                request.getSessionId()
            );

            // Get pet status from session cache
            Long userId = Long.parseLong(request.getUserId());
            PetStatusDto petStatus = session.getCachedPetStatus();
            if (petStatus == null) {
                petStatus = petProfileService.getStatus(userId);
                session.setCachedPetStatus(petStatus);
            }

            // EGG stage: return early with no response
            if ("EGG".equalsIgnoreCase(petStatus.stage())) {
                LOG.debugf("Pet in EGG stage for user %s, skipping LLM call", request.getUserId());
                return ConversationMessageResponse.success("", request.getSessionId());
            }

            // Load interests once per session (cached)
            if (!session.hasTopInterests()) {
                Integer robotId = parseRobotId(request);
                java.util.List<String> interests = adaptiveInterestService.getTopInterests(robotId);
                session.setCachedTopInterests(interests);
            }

            String enhancedPrompt = buildEnhancedPrompt(request, session, petStatus);
            String response = aiService.generateResponse(request.getMessage(), enhancedPrompt);

            // Process quest result
            QuestDto quest = questService.getPendingQuest(userId);
            String questResult = questEvaluationService.parseQuestResult(response);
            if (questResult != null && quest != null) {
                questEvaluationService.processResult(userId, questResult, quest);
            }
            response = questEvaluationService.stripQuestMarker(response);

            // Travel marker processing
            String travelCode = travelMarkerParser.parse(response);
            if (travelCode != null) {
                travelService.travel(userId, travelCode);
            }
            response = travelMarkerParser.strip(response);

            // Store in session history
            session.addTurn(request.getMessage(), response);

            // Record to PowerMem
            if (powerMemEnabled) {
                recordToPowerMemAsync(request, response);
            }

            return ConversationMessageResponse.success(response, request.getSessionId());

        } catch (Exception e) {
            LOG.errorf(e, "Error processing message: %s", e.getMessage());
            return ConversationMessageResponse.error(
                "Xin lỗi, mình gặp chút vấn đề. Thử lại nhé!",
                request.getSessionId()
            );
        }
    }
}
