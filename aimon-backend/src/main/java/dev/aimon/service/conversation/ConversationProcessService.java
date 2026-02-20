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

            // Step 3: Build enhanced prompt with pet personality + memory context
            String enhancedPrompt = buildEnhancedPrompt(request, session, petStatus);

            // Step 4: Collect full response for history storage
            StringBuilder fullResponseBuilder = new StringBuilder();

            // Step 5: Stream from AI service
            aiService.generateResponseStreaming(
                request.getMessage(),
                enhancedPrompt,
                // onSentence: forward to caller and collect
                sentence -> {
                    LOG.infof("LLM sentence [%d]: %s", fullResponseBuilder.length(), sentence);
                    fullResponseBuilder.append(sentence).append(" ");
                    // Strip quest marker before TTS
                    String clean = questEvaluationService.stripQuestMarker(sentence);
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

                    // Strip quest marker before storing
                    fullResponse = questEvaluationService.stripQuestMarker(fullResponse);

                    // Store turn in session history (in-memory)
                    session.addTurn(request.getMessage(), fullResponse);

                    LOG.debugf("Stored turn in session history (user: %s, total: %d turns)",
                               request.getUserId(), session.getHistorySize());

                    // Record to PowerMem asynchronously (optional)
                    if (powerMemEnabled) {
                        recordToPowerMemAsync(request, fullResponse);
                    }

                    onComplete.run();
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

        // 1b. Quest context (if pending quest exists)
        Long userId = Long.parseLong(request.getUserId());
        QuestDto pendingQuest = questService.getPendingQuest(userId);
        if (pendingQuest != null) {
            String questPrompt = petPromptAssembler.buildQuestPrompt(pendingQuest);
            promptBuilder.append(questPrompt).append("\n");
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

        // 5. Conversation History (short-term context)
        if (session.getHistorySize() > 0) {
            promptBuilder.append("=== Recent Conversation History ===\n");
            for (ConversationSession.ConversationTurn turn : session.getAllTurns()) {
                promptBuilder.append("User: ").append(turn.getUserMessage()).append("\n");
                promptBuilder.append(petStatus.name()).append(": ").append(turn.getAiResponse()).append("\n");
            }
            promptBuilder.append("\n");
        }

        // 6. Current Message
        promptBuilder.append("=== Current Message ===\n");
        promptBuilder.append(request.getMessage());

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
        try {
            Integer robotId = parseRobotId(request);

            // Record user message
            memoryService.recordAsync(
                "User said: " + request.getMessage(),
                java.util.Map.of(
                    "robot_id", robotId,
                    "session_id", request.getSessionId(),
                    "category", "conversation",
                    "type", "user_message",
                    "importance", 0.5
                )
            );

            // Record AI response
            memoryService.recordAsync(
                "Pet responded: " + response,
                java.util.Map.of(
                    "robot_id", robotId,
                    "session_id", request.getSessionId(),
                    "category", "conversation",
                    "type", "ai_response",
                    "importance", 0.5
                )
            );

            LOG.debugf("Queued conversation turn for PowerMem recording (session: %s)",
                       request.getSessionId());
        } catch (Exception e) {
            LOG.warnf(e, "Failed to record conversation to PowerMem: %s", e.getMessage());
        }
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

            String enhancedPrompt = buildEnhancedPrompt(request, session, petStatus);
            String response = aiService.generateResponse(request.getMessage(), enhancedPrompt);

            // Process quest result
            QuestDto quest = questService.getPendingQuest(userId);
            String questResult = questEvaluationService.parseQuestResult(response);
            if (questResult != null && quest != null) {
                questEvaluationService.processResult(userId, questResult, quest);
            }
            response = questEvaluationService.stripQuestMarker(response);

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
