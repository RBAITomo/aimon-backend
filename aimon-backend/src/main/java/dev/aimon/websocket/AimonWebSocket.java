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
@WebSocket(path = "/ws/audio/{robotId}")
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
    public void onOpen(@PathParam String robotId, WebSocketConnection connection) {
        RobotSession session = new RobotSession(robotId);
        sessions.put(robotId, session);
        LOG.infof("Robot %s connected, waiting for hello", robotId);
    }

    @OnTextMessage
    public Uni<Void> onText(String text, @PathParam String robotId, WebSocketConnection connection) {
        LOG.debugf("Robot %s message: %s", robotId, text);

        return Uni.createFrom().item(() -> {
            try {
                JsonNode message = objectMapper.readTree(text);
                String type = message.get("type").asText("");

                return switch (type) {
                    case "hello" -> handleHello(robotId, message, connection);
                    case "audio_start" -> handleAudioStart(robotId, connection);
                    case "audio_stop" -> handleAudioStop(robotId, connection);
                    case "interrupt" -> handleInterrupt(robotId, connection);
                    case "ping" -> handlePing(robotId, connection);
                    case "pet_feed_confirm" -> handlePetFeedConfirm(robotId, message, connection);
                    case "quest_request" -> handleQuestRequest(robotId, connection);
                    case "pet_transform" -> petMessageHandler.handleTransformRequest(robotId, getUserId(robotId), message, connection);
                    default -> sendError(connection, "UNKNOWN_TYPE", "Unknown message type: " + type);
                };
            } catch (Exception e) {
                LOG.errorf(e, "Error parsing message from robot %s", robotId);
                return sendError(connection, "PARSE_ERROR", "Failed to parse message");
            }
        }).flatMap(result -> result);
    }

    @OnBinaryMessage
    public Uni<Void> onBinary(byte[] data, @PathParam String robotId) {
        RobotSession session = sessions.get(robotId);
        if (session == null) {
            LOG.warnf("Robot %s: No session for binary frame", robotId);
            return Uni.createFrom().voidItem();
        }

        // Only accept frames in LISTENING state
        if (session.getState() != SessionState.LISTENING) {
            LOG.debugf("Robot %s: Ignoring binary frame in state %s", robotId, session.getState());
            return Uni.createFrom().voidItem();
        }

        // Add audio frame to buffer
        synchronized (session.getAudioFrameBuffer()) {
            session.getAudioFrameBuffer().addLast(data);
        }

        LOG.tracef("Robot %s: Buffered %d bytes, total frames: %d",
                robotId, data.length, session.getAudioFrameBuffer().size());

        return Uni.createFrom().voidItem();
    }

    @OnClose
    public void onClose(@PathParam String robotId) {
        LOG.infof("Robot %s disconnected", robotId);

        RobotSession session = sessions.remove(robotId);
        if (session != null) {
            // Unregister pet connection
            if (session.getUserId() != null) {
                connectionRegistry.unregister(session.getUserId());
            }

            // Upload conversation history to PowerMem
            if (session.getConversation() != null) {
                uploadConversationHistory(robotId, session);

                // Cleanup conversation session
                sessionManager.removeSession(session.getConversation().sessionId());
            }
        }
    }

    @OnError
    public void onError(Throwable error, @PathParam String robotId) {
        LOG.errorf(error, "Robot %s WebSocket error", robotId);

        // Cleanup session on error
        RobotSession session = sessions.remove(robotId);
        if (session != null && session.getConversation() != null) {
            uploadConversationHistory(robotId, session);
            sessionManager.removeSession(session.getConversation().sessionId());
        }
    }

    // ==================== Message Handlers ====================

    private Uni<Void> handleHello(String robotId, JsonNode message, WebSocketConnection connection) {
        RobotSession session = sessions.get(robotId);
        if (session == null) {
            return sendError(connection, "NO_SESSION", "Session not found");
        }

        // Parse client hello
        int version = message.has("version") ? message.get("version").asInt(4) : 4;
        String deviceId = message.has("device_id") ? message.get("device_id").asText(robotId) : robotId;

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
                robotId, version, session.getAudioFormat(), session.getSampleRate());

        // Initialize conversation session
        session.setConversation(sessionManager.getOrCreateSession(1, session.getSessionId()));

        // Set userId (default to 1 for now, can be extracted from device_id or auth later)
        session.setUserId(1L);

        // Reply with hello_ack
        ObjectNode response = objectMapper.createObjectNode();
        response.put("type", "hello_ack");
        response.put("session_id", session.getSessionId());
        response.put("server_version", 4);

        // Register connection and send initial pet status
        Long userId = session.getUserId();
        if (userId != null) {
            connectionRegistry.register(userId, connection);
            petMessageHandler.sendPetStatus(userId, connection)
                .subscribe().with(v -> {}, e -> LOG.errorf(e, "Failed to send pet status"));
        }

        return connection.sendText(response.toString());
    }

    private Uni<Void> handleAudioStart(String robotId, WebSocketConnection connection) {
        RobotSession session = sessions.get(robotId);
        if (session == null) {
            return sendError(connection, "NO_SESSION", "Session not found");
        }

        // Clear previous buffers and reset state
        synchronized (session.getAudioFrameBuffer()) {
            session.getAudioFrameBuffer().clear();
        }
        session.setCancelled(false);
        session.setState(SessionState.LISTENING);

        LOG.infof("Robot %s: Started listening", robotId);
        return sendAck(connection, "audio_start");
    }

    private Uni<Void> handleAudioStop(String robotId, WebSocketConnection connection) {
        RobotSession session = sessions.get(robotId);
        if (session == null) {
            return sendError(connection, "NO_SESSION", "Session not found");
        }

        session.setState(SessionState.PROCESSING);
        LOG.infof("Robot %s: Stopped listening, processing audio", robotId);

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

    private Uni<Void> handleInterrupt(String robotId, WebSocketConnection connection) {
        RobotSession session = sessions.get(robotId);
        if (session == null) {
            return sendError(connection, "NO_SESSION", "Session not found");
        }

        LOG.infof("Robot %s: Interrupt requested", robotId);

        // Set cancellation flag
        session.setCancelled(true);

        // Reset to IDLE (client will send audio_start for new turn)
        session.setState(SessionState.IDLE);

        // Send interrupt ACK
        ObjectNode response = objectMapper.createObjectNode();
        response.put("type", "interrupt_ack");
        return connection.sendText(response.toString());
    }

    private Uni<Void> handlePing(String robotId, WebSocketConnection connection) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("type", "pong");
        response.put("timestamp", Instant.now().toEpochMilli());
        return connection.sendText(response.toString());
    }

    private Uni<Void> handlePetFeedConfirm(String robotId, JsonNode message, WebSocketConnection connection) {
        Long userId = getUserId(robotId);
        if (userId == null) {
            return sendError(connection, "NO_USER", "User ID not found");
        }
        String foodName = message.has("food_name") ? message.get("food_name").asText("unknown") : "unknown";
        if (foodName.length() > 100) foodName = foodName.substring(0, 100);
        foodName = foodName.replaceAll("[\\p{Cntrl}]", "");
        return petMessageHandler.handleFeedConfirm(robotId, userId, foodName, connection);
    }

    private Uni<Void> handleQuestRequest(String robotId, WebSocketConnection connection) {
        Long userId = getUserId(robotId);
        if (userId == null) {
            return sendError(connection, "NO_USER", "User ID not found");
        }
        return petMessageHandler.handleQuestRequest(robotId, userId, connection);
    }

    private Long getUserId(String robotId) {
        RobotSession session = sessions.get(robotId);
        return session != null ? session.getUserId() : null;
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

    private void uploadConversationHistory(String robotId, RobotSession session) {
        if (!memoryService.isEnabled() || session.getConversation() == null) {
            return;
        }

        LOG.infof("Uploading conversation history for robot %s", robotId);

        // Async upload to PowerMem
        // TODO: Implement conversation turn upload to PowerMem
        // This would iterate through session.getConversation().getHistory()
        // and call memoryService.recordAsync() for each turn
    }
}
