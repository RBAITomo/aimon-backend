package dev.aimon.service.audio;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.aimon.dto.conversation.ConversationMessageRequest;
import dev.aimon.dto.tts.TtsRequest;
import dev.aimon.model.RobotSession;
import dev.aimon.model.SessionState;
import dev.aimon.service.SentenceSplitterService;
import dev.aimon.service.conversation.ConversationProcessService;
import dev.aimon.service.tts.TtsProviderService;
import io.quarkus.websockets.next.WebSocketConnection;
import io.vertx.core.buffer.Buffer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Response streaming service for v4 push-to-talk protocol.
 * Handles: LLM streaming → sentence split → TTS → PCM16 output
 */
@ApplicationScoped
public class ResponseStreamService {

    private static final Logger LOG = Logger.getLogger(ResponseStreamService.class);

    @Inject
    ConversationProcessService conversationService;

    @Inject
    TtsProviderService ttsProvider;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    dev.aimon.service.pet.PetProfileService petProfileService;

    /**
     * Stream full response to client: ASR result → LLM streaming → TTS per sentence → turn_end.
     *
     * @param transcript User speech transcript from STT
     * @param session Robot session state
     * @param connection WebSocket connection
     */
    public void streamResponse(String transcript, RobotSession session, WebSocketConnection connection) {
        LOG.infof("Streaming response for transcript: %s", transcript);

        // Step 1: Send ASR result
        sendAsrResult(connection, transcript);

        // Step 2: Build conversation request
        ConversationMessageRequest request = new ConversationMessageRequest();
        request.setUserId("1"); // Default user ID (can be configured per robot)
        request.setSessionId(session.getSessionId());
        request.setAction("chat");
        request.setMessage(transcript);

        // Step 3: Stream LLM response with sentence-level TTS
        session.setState(SessionState.RESPONDING);

        // Chain TTS sequentially so audio plays in sentence order
        AtomicReference<CompletableFuture<Void>> ttsChain =
                new AtomicReference<>(CompletableFuture.completedFuture(null));

        // Track pending sentences for has_more signaling
        java.util.concurrent.atomic.AtomicInteger pendingSentences =
                new java.util.concurrent.atomic.AtomicInteger(0);

        // Sentence splitter for streaming tokens
        SentenceSplitterService.StreamingSplitter splitter =
                new SentenceSplitterService.StreamingSplitter(sentence -> {
                    // Interrupt check
                    if (session.isCancelled()) {
                        LOG.info("Response cancelled by interrupt");
                        return;
                    }

                    // Send LLM token stream (text shows immediately)
                    sendLlmStream(connection, sentence, false);

                    // Track pending sentence count for has_more
                    pendingSentences.incrementAndGet();

                    // Chain TTS: sentence N+1 waits for sentence N to finish
                    ttsChain.updateAndGet(prev ->
                            prev.thenCompose(v -> streamSentenceTts(sentence, connection, session, pendingSentences)));
                });

        // Process message with streaming
        conversationService.processMessageStreaming(
                request,
                token -> {
                    // Feed tokens to sentence splitter
                    if (!session.isCancelled()) {
                        splitter.append(token);
                    }
                },
                () -> {
                    // Flush remaining tokens
                    splitter.flush();

                    // Send final LLM stream marker
                    sendLlmStream(connection, "", true);

                    // Wait for the sequential TTS chain to complete before turn_end
                    if (!session.isCancelled()) {
                        ttsChain.get().whenComplete((v, ex) -> {
                            if (ex != null) {
                                LOG.warnf("TTS error during turn: %s", ex.getMessage());
                            }
                            sendTurnEnd(connection, session);
                            session.setState(SessionState.IDLE);
                        });
                    }
                },
                error -> {
                    LOG.errorf(error, "LLM streaming error");
                    sendError(connection, "LLM_ERROR", error.getMessage());
                    session.setState(SessionState.IDLE);
                }
        );
    }

    /**
     * Stream TTS for a single sentence.
     * Returns a CompletableFuture that completes when TTS audio is fully sent.
     */
    private CompletableFuture<Void> streamSentenceTts(String sentence, WebSocketConnection connection,
                                                       RobotSession session,
                                                       java.util.concurrent.atomic.AtomicInteger pendingSentences) {
        if (sentence == null || sentence.trim().isEmpty()) {
            pendingSentences.decrementAndGet();
            return CompletableFuture.completedFuture(null);
        }

        LOG.infof("TTS sentence start (pending=%d): %s", pendingSentences.get(), sentence);

        // Send tts_start
        sendTtsStart(connection, sentence);

        // TTS request
        TtsRequest request = new TtsRequest();
        request.setText(sentence.trim());
        request.setVoiceCode("vi-VN-HoaiMyNeural"); // Default voice
        request.setSpeedRate(1.0); // Normal speed

        // Track completion of this TTS operation
        CompletableFuture<Void> future = new CompletableFuture<>();

        // Track last binary send to ensure all bytes are flushed before completing
        AtomicReference<CompletableFuture<Void>> lastSend =
                new AtomicReference<>(CompletableFuture.completedFuture(null));

        // Stream PCM16 chunks
        ttsProvider.generateSpeechStreaming(
                request,
                pcmChunk -> {
                    // Interrupt check
                    if (session.isCancelled()) {
                        LOG.debug("TTS cancelled by interrupt");
                        return false; // Stop streaming
                    }

                    // Chain binary sends so we know when the last one completes
                    CompletableFuture<Void> sendFuture = new CompletableFuture<>();
                    connection.sendBinary(Buffer.buffer(pcmChunk))
                            .subscribe().with(
                                    v -> sendFuture.complete(null),
                                    err -> {
                                        LOG.warnf("sendBinary error: %s", err.getMessage());
                                        sendFuture.complete(null); // Don't block chain on send error
                                    });
                    lastSend.set(sendFuture);
                    return true; // Continue streaming
                },
                () -> {
                    // TTS generation complete — wait for last binary send to flush
                    lastSend.get().whenComplete((v, ex) -> {
                        int remaining = pendingSentences.decrementAndGet();
                        if (!session.isCancelled()) {
                            sendTtsStop(connection, remaining > 0);
                        }
                        future.complete(null);
                    });
                },
                error -> {
                    LOG.errorf(error, "TTS error for sentence: %s", sentence);
                    int remaining = pendingSentences.decrementAndGet();
                    sendTtsStop(connection, remaining > 0);
                    future.complete(null);
                }
        );

        return future;
    }

    // ==================== Message Sending Helpers ====================
    // All use non-blocking subscribe — callbacks may run on Vert.x event loop which cannot block.

    private void sendWsText(WebSocketConnection connection, String json) {
        connection.sendText(json)
                .subscribe().with(v -> {}, err -> LOG.warnf("sendText error: %s", err.getMessage()));
    }

    private void sendAsrResult(WebSocketConnection connection, String text) {
        ObjectNode msg = objectMapper.createObjectNode();
        msg.put("type", "asr_result");
        msg.put("text", text);
        msg.put("confidence", 0.95);
        sendWsText(connection, msg.toString());
    }

    private void sendLlmStream(WebSocketConnection connection, String token, boolean done) {
        ObjectNode msg = objectMapper.createObjectNode();
        msg.put("type", "llm_stream");
        msg.put("token", token);
        msg.put("done", done);
        sendWsText(connection, msg.toString());
    }

    private void sendTtsStart(WebSocketConnection connection, String text) {
        ObjectNode msg = objectMapper.createObjectNode();
        msg.put("type", "tts_start");
        msg.put("text", text);
        sendWsText(connection, msg.toString());
    }

    private void sendTtsStop(WebSocketConnection connection, boolean hasMore) {
        ObjectNode msg = objectMapper.createObjectNode();
        msg.put("type", "tts_stop");
        msg.put("has_more", hasMore);
        sendWsText(connection, msg.toString());
    }

    private void sendTurnEnd(WebSocketConnection connection, RobotSession session) {
        // Pet rewards for completing a conversation turn
        if (session.getUserId() != null) {
            int happinessGain = 3;  // base happiness for chat
            int xpGain = 20;        // 20 XP per turn — egg hatches after 3 messages (3×20=60 > level-2 threshold of 50)
            petProfileService.applyChat(session.getUserId(), happinessGain);
            petProfileService.addXp(session.getUserId(), xpGain);
        }

        ObjectNode msg = objectMapper.createObjectNode();
        msg.put("type", "turn_end");
        msg.put("turn_id", session.getSessionId());
        sendWsText(connection, msg.toString());
        LOG.info("Turn completed");
    }

    private void sendError(WebSocketConnection connection, String code, String message) {
        ObjectNode msg = objectMapper.createObjectNode();
        msg.put("type", "error");
        msg.put("code", code);
        msg.put("message", message);
        sendWsText(connection, msg.toString());
    }
}
