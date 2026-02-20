package dev.aimon.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.aimon.model.RobotSession;
import dev.aimon.model.SessionState;
import dev.aimon.service.audio.AudioPipelineService;
import dev.aimon.service.audio.ResponseStreamService;
import dev.aimon.service.conversation.ConversationSessionManager;
import dev.aimon.service.memory.PowerMemService;
import dev.aimon.service.pet.PetProfileService;
import io.quarkus.websockets.next.*;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket v4 push-to-talk audio handler.
 * Simplified protocol: hello → audio_start → frames → audio_stop → process → respond → turn_end
 */
@WebSocket(path = "/ws/audio/{petId}")
public class AimonWebSocket {

    private static final Logger LOG = Logger.getLogger(AimonWebSocket.class);

    private final Map<String, RobotSession> sessions = new ConcurrentHashMap<>();

    @Inject
    ObjectMapper objectMapper;

    @Inject
    AudioPipelineService audioPipeline;

    @Inject
    ResponseStreamService responseStream;

    @Inject
    ConversationSessionManager sessionManager;

    @Inject
    PowerMemService memoryService;

    @Inject
    PetMessageHandler petMessageHandler;

    @Inject
    SessionConnectionRegistry connectionRegistry;

    @Inject
    PetProfileService petProfileService;

    @OnOpen
    public void onOpen(@PathParam String petId, WebSocketConnection connection) {
        RobotSession session = new RobotSession(petId);
        sessions.put(petId, session);
        LOG.infof("Robot %s connected, waiting for hello", petId);
    }

    @OnTextMessage
    public Uni<Void> onText(String text, @PathParam String petId, WebSocketConnection connection) {
        LOG.debugf("Robot %s message: %s", petId, text);

        return Uni.createFrom().item(() -> {
            try {
                JsonNode message = objectMapper.readTree(text);
                String type = message.get("type").asText("");

                return switch (type) {
                    case "hello" -> handleHello(petId, message, connection);
                    case "audio_start" -> handleAudioStart(petId, connection);
                    case "audio_stop" -> handleAudioStop(petId, connection);
                    case "interrupt" -> handleInterrupt(petId, connection);
                    case "ping" -> handlePing(petId, connection);
                    case "pet_feed_confirm" -> handlePetFeedConfirm(petId, message, connection);
                    case "quest_request" -> handleQuestRequest(petId, connection);
                    case "pet_transform" -> petMessageHandler.handleTransformRequest(petId, getUserId(petId), message, connection);
                    default -> sendError(connection, "UNKNOWN_TYPE", "Unknown message type: " + type);
                };
            } catch (Exception e) {
                LOG.errorf(e, "Error parsing message from robot %s", petId);
                return sendError(connection, "PARSE_ERROR", "Failed to parse message");
            }
        }).flatMap(result -> result);
    }

    @OnBinaryMessage
    public Uni<Void> onBinary(byte[] data, @PathParam String petId) {
        RobotSession session = sessions.get(petId);
        if (session == null) {
            LOG.warnf("Robot %s: No session for binary frame", petId);
            return Uni.createFrom().voidItem();
        }

        // Only accept frames in LISTENING state
        if (session.getState() != SessionState.LISTENING) {
            LOG.debugf("Robot %s: Ignoring binary frame in state %s", petId, session.getState());
            return Uni.createFrom().voidItem();
        }

        // Add audio frame to buffer
        synchronized (session.getAudioFrameBuffer()) {
            session.getAudioFrameBuffer().addLast(data);
        }

        LOG.tracef("Robot %s: Buffered %d bytes, total frames: %d",
                petId, data.length, session.getAudioFrameBuffer().size());

        return Uni.createFrom().voidItem();
    }

    @OnClose
    public void onClose(@PathParam String petId) {
        LOG.infof("Robot %s disconnected", petId);

        RobotSession session = sessions.remove(petId);
        if (session != null) {
            // Unregister pet connection
            if (session.getUserId() != null) {
                connectionRegistry.unregister(session.getUserId());
            }

            // Upload conversation history to PowerMem
            if (session.getConversation() != null) {
                uploadConversationHistory(petId, session);

                // Cleanup conversation session
                sessionManager.removeSession(session.getConversation().sessionId());
            }
        }
    }

    @OnError
    public void onError(Throwable error, @PathParam String petId) {
        LOG.errorf(error, "Robot %s WebSocket error", petId);

        // Cleanup session on error
        RobotSession session = sessions.remove(petId);
        if (session != null && session.getConversation() != null) {
            uploadConversationHistory(petId, session);
            sessionManager.removeSession(session.getConversation().sessionId());
        }
    }

    // ==================== Message Handlers ====================

    private Uni<Void> handleHello(String petId, JsonNode message, WebSocketConnection connection) {
        RobotSession session = sessions.get(petId);
        if (session == null) {
            return sendError(connection, "NO_SESSION", "Session not found");
        }

        // Parse client hello
        int version = message.has("version") ? message.get("version").asInt(4) : 4;

        // Parse audio params
        if (message.has("audio_params")) {
            JsonNode params = message.get("audio_params");
            if (params.has("format")) {
                session.setAudioFormat(params.get("format").asText("opus"));
            }
            if (params.has("sample_rate")) {
                session.setSampleRate(params.get("sample_rate").asInt(48000));
            }
        }

        LOG.infof("Hello from robot %s: version=%d, format=%s, rate=%dHz",
                petId, version, session.getAudioFormat(), session.getSampleRate());

        // Resolve userId on worker thread — name lookup hits JPA (blocking)
        return Uni.createFrom().item(() -> resolvePetId(petId))
            .runSubscriptionOn(Infrastructure.getDefaultWorkerPool())
            .onItem().transformToUni(resolvedUserId -> {
                if (resolvedUserId == null) {
                    LOG.warnf("Pet not found for petId: %s", petId);
                    return sendError(connection, "UNKNOWN_PET", "Pet not found: " + petId);
                }
                session.setUserId(resolvedUserId);
                session.setPetStage(petProfileService.getOrCreateProfile(resolvedUserId).getStage());
                session.setConversation(sessionManager.getOrCreateSession(resolvedUserId.intValue(), session.getSessionId()));

                ObjectNode response = objectMapper.createObjectNode();
                response.put("type", "hello_ack");
                response.put("session_id", session.getSessionId());
                response.put("server_version", 4);

                connectionRegistry.register(resolvedUserId, connection);

                // Send hello_ack first, then chain pet_status sequentially
                // to avoid concurrent sendText race condition on the same connection
                return connection.sendText(response.toString())
                    .chain(() -> petMessageHandler.sendPetStatus(resolvedUserId, connection))
                    .onFailure().invoke(e -> LOG.errorf(e, "Failed to send pet status"))
                    .onFailure().recoverWithNull();
            });
    }

    private Uni<Void> handleAudioStart(String petId, WebSocketConnection connection) {
        RobotSession session = sessions.get(petId);
        if (session == null) {
            return sendError(connection, "NO_SESSION", "Session not found");
        }

        // Clear previous buffers and reset state
        synchronized (session.getAudioFrameBuffer()) {
            session.getAudioFrameBuffer().clear();
        }
        session.setCancelled(false);
        session.setState(SessionState.LISTENING);

        LOG.infof("Robot %s: Started listening", petId);
        return sendAck(connection, "audio_start");
    }

    private Uni<Void> handleAudioStop(String petId, WebSocketConnection connection) {
        RobotSession session = sessions.get(petId);
        if (session == null) {
            return sendError(connection, "NO_SESSION", "Session not found");
        }

        session.setState(SessionState.PROCESSING);
        LOG.infof("Robot %s: Stopped listening, processing audio", petId);

        // Offload audio processing to worker thread
        return Uni.createFrom().item(() -> {
            // Copy frames to avoid concurrent modification
            Deque<byte[]> frames;
            synchronized (session.getAudioFrameBuffer()) {
                frames = new ArrayDeque<>(session.getAudioFrameBuffer());
            }

            // Process audio pipeline
            String transcript = audioPipeline.processAudio(frames, session.getSampleRate(), session.getAudioFormat());

            if (transcript == null || transcript.trim().isEmpty()) {
                LOG.info("No valid transcript, returning to IDLE");
                session.setState(SessionState.IDLE);
                // Send empty ASR result so frontend transitions back from ASR state
                ObjectNode emptyAsr = objectMapper.createObjectNode();
                emptyAsr.put("type", "asr_result");
                emptyAsr.put("text", "");
                emptyAsr.put("confidence", 0.0);
                connection.sendText(emptyAsr.toString()).subscribe().asCompletionStage();
                return null;
            }

            // Stream response
            responseStream.streamResponse(transcript.trim(), session, connection);
            return null;
        })
        .runSubscriptionOn(Infrastructure.getDefaultWorkerPool())
        .replaceWithVoid();
    }

    private Uni<Void> handleInterrupt(String petId, WebSocketConnection connection) {
        RobotSession session = sessions.get(petId);
        if (session == null) {
            return sendError(connection, "NO_SESSION", "Session not found");
        }

        LOG.infof("Robot %s: Interrupt requested", petId);

        // Set cancellation flag
        session.setCancelled(true);

        // Reset to IDLE (client will send audio_start for new turn)
        session.setState(SessionState.IDLE);

        // Send interrupt ACK
        ObjectNode response = objectMapper.createObjectNode();
        response.put("type", "interrupt_ack");
        return connection.sendText(response.toString());
    }

    private Uni<Void> handlePing(String petId, WebSocketConnection connection) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("type", "pong");
        response.put("timestamp", Instant.now().toEpochMilli());
        return connection.sendText(response.toString());
    }

    private Uni<Void> handlePetFeedConfirm(String petId, JsonNode message, WebSocketConnection connection) {
        Long userId = getUserId(petId);
        if (userId == null) {
            return sendError(connection, "NO_USER", "User ID not found");
        }
        String foodName = message.has("food_name") ? message.get("food_name").asText("unknown") : "unknown";
        if (foodName.length() > 100) foodName = foodName.substring(0, 100);
        foodName = foodName.replaceAll("[\\p{Cntrl}]", "");
        String spriteKey = message.has("sprite_key") ? message.get("sprite_key").asText("default") : "default";
        spriteKey = spriteKey.replaceAll("[^a-zA-Z0-9_ ]", "");
        if (spriteKey.length() > 50) spriteKey = spriteKey.substring(0, 50);
        return petMessageHandler.handleFeedConfirm(petId, userId, foodName, spriteKey, connection);
    }

    private Uni<Void> handleQuestRequest(String petId, WebSocketConnection connection) {
        Long userId = getUserId(petId);
        if (userId == null) {
            return sendError(connection, "NO_USER", "User ID not found");
        }
        return petMessageHandler.handleQuestRequest(petId, userId, connection);
    }

    private Long getUserId(String petId) {
        RobotSession session = sessions.get(petId);
        return session != null ? session.getUserId() : null;
    }

    /**
     * Resolve userId from petId path param.
     * Accepts numeric userId directly, or pet name (case-insensitive DB lookup).
     */
    private Long resolvePetId(String petId) {
        try {
            return Long.parseLong(petId);
        } catch (NumberFormatException e) {
            return petProfileService.findUserIdByName(petId);
        }
    }

    // ==================== Helper Methods ====================

    private Uni<Void> sendAck(WebSocketConnection connection, String messageType) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("type", "ack");
        response.put("message_type", messageType);
        return connection.sendText(response.toString());
    }

    private Uni<Void> sendError(WebSocketConnection connection, String code, String message) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("type", "error");
        response.put("code", code);
        response.put("message", message);
        return connection.sendText(response.toString());
    }

    private void uploadConversationHistory(String petId, RobotSession session) {
        if (!memoryService.isEnabled() || session.getConversation() == null) {
            return;
        }

        LOG.infof("Uploading conversation history for robot %s", petId);

        // Async upload to PowerMem
        // TODO: Implement conversation turn upload to PowerMem
        // This would iterate through session.getConversation().getHistory()
        // and call memoryService.recordAsync() for each turn
    }
}
