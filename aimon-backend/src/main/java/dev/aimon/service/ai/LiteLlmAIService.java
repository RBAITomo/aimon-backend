package dev.aimon.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.aimon.config.AIConfig;
import dev.aimon.dto.ai.LiteLlmChatMessage;
import dev.aimon.dto.ai.LiteLlmChatRequest;
import dev.aimon.dto.ai.LiteLlmChatResponse;
import dev.aimon.dto.ai.LiteLlmEmbeddingResponse;
import dev.aimon.service.SentenceSplitterService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * AI service backed by LiteLLM proxy speaking the OpenAI API surface.
 */
@ApplicationScoped
public class LiteLlmAIService {

    private static final Logger LOG = Logger.getLogger(LiteLlmAIService.class);

    private static final String FALLBACK_RESPONSE =
        "Xin lỗi bạn nhé, mình đang hơi bận. Mình sẽ trả lời lại sau một chút nhé!";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Inject
    AIConfig aiConfig;

    @Inject
    LiteLlmClientService liteLlmClientService;

    @Inject
    SentenceSplitterService sentenceSplitterService;

    public String generateResponse(String message, String context) {
        try {
            List<LiteLlmChatMessage> messages = buildBaseMessages();
            messages.add(LiteLlmChatMessage.user(buildPromptWithContext(message, context)));

            LiteLlmChatRequest request = new LiteLlmChatRequest();
            request.setMessages(messages);

            LiteLlmChatResponse response = liteLlmClientService.chatCompletion(request);
            String result = response.firstMessageContent();
            LOG.infof("Generated LiteLLM response: %s", result);
            return result;
        } catch (Exception ex) {
            LOG.errorf(ex, "Error generating response with LiteLLM: %s", ex.getMessage());
            return FALLBACK_RESPONSE;
        }
    }

    public String generateStory(String topic, String ageGroup) {
        try {
            List<LiteLlmChatMessage> messages = buildBaseMessages();
            messages.add(LiteLlmChatMessage.user(buildStoryPrompt(topic, ageGroup)));

            LiteLlmChatRequest request = new LiteLlmChatRequest();
            request.setMessages(messages);

            LiteLlmChatResponse response = liteLlmClientService.chatCompletion(request);
            String result = response.firstMessageContent();
            LOG.infof("Generated LiteLLM story response");
            return result;
        } catch (Exception ex) {
            LOG.errorf(ex, "Error generating story with LiteLLM: %s", ex.getMessage());
            return FALLBACK_RESPONSE;
        }
    }

    public float[] createEmbedding(String text) {
        return liteLlmClientService.createEmbedding(text);
    }

    public String getProviderName() {
        return "litellm";
    }

    public void generateResponseStreaming(
        String message,
        String context,
        Consumer<String> onSentence,
        Runnable onComplete,
        Consumer<Throwable> onError
    ) {
        try {
            // Build messages with context
            List<LiteLlmChatMessage> messages = buildBaseMessages();
            messages.add(LiteLlmChatMessage.user(buildPromptWithContext(message, context)));

            // Create request with streaming enabled
            LiteLlmChatRequest request = new LiteLlmChatRequest();
            request.setMessages(messages);
            request.setStream(true);

            // Create streaming sentence splitter
            SentenceSplitterService.StreamingSplitter splitter =
                new SentenceSplitterService.StreamingSplitter(sentence -> {
                    LOG.debugf("Emitting sentence from LLM stream: %s", sentence);
                    onSentence.accept(sentence);
                });

            // Call streaming endpoint
            liteLlmClientService.chatCompletionStream(request)
                .subscribe().with(
                    // onItem: Process each SSE chunk
                    chunk -> {
                        try {
                            String content = extractContentFromSseChunk(chunk);
                            if (content != null && !content.isEmpty()) {
                                splitter.append(content);
                            }
                        } catch (Exception e) {
                            LOG.warnf(e, "Error processing SSE chunk: %s", chunk);
                        }
                    },
                    // onFailure: Error handling
                    error -> {
                        LOG.errorf(error, "LLM streaming error: %s", error.getMessage());
                        splitter.flush(); // Emit any remaining text
                        onError.accept(error);
                    },
                    // onCompletion: Stream completed
                    () -> {
                        LOG.debug("LLM streaming completed");
                        splitter.flush(); // Emit final sentence
                        onComplete.run();
                    }
                );

        } catch (Exception ex) {
            LOG.errorf(ex, "Error starting LLM streaming: %s", ex.getMessage());
            onError.accept(ex);
        }
    }

    /**
     * Extract content from SSE chunk in format: data: {"choices":[{"delta":{"content":"text"}}]}
     * OpenAI streaming format returns chunks with delta content
     */
    private String extractContentFromSseChunk(String chunk) {
        try {
            // SSE format: "data: {...json...}"
            if (chunk == null || chunk.isBlank()) {
                return null;
            }

            // Remove "data: " prefix if present
            String jsonStr = chunk.trim();
            if (jsonStr.startsWith("data: ")) {
                jsonStr = jsonStr.substring(6).trim();
            }

            // Skip [DONE] marker
            if ("[DONE]".equals(jsonStr)) {
                return null;
            }

            // Parse JSON and extract content
            JsonNode root = OBJECT_MAPPER.readTree(jsonStr);
            JsonNode choices = root.path("choices");
            if (choices.isArray() && !choices.isEmpty()) {
                JsonNode delta = choices.get(0).path("delta");
                JsonNode content = delta.path("content");
                if (!content.isMissingNode()) {
                    return content.asText();
                }
            }

            return null;
        } catch (Exception e) {
            LOG.debugf("Could not parse SSE chunk: %s", chunk);
            return null;
        }
    }

    private List<LiteLlmChatMessage> buildBaseMessages() {
        List<LiteLlmChatMessage> messages = new ArrayList<>();
        messages.add(LiteLlmChatMessage.system("You are a caring and playful digital pet living in Cotton Land. "
            + "Respond in Vietnamese unless explicitly asked otherwise. Keep answers short, kind, and easy to understand."));
        return messages;
    }

    private String buildPromptWithContext(String message, String context) {
        if (context == null || context.isBlank()) {
            return message;
        }
        return "Thông tin tham khảo:\n" + context + "\n\nCâu hỏi của bạn nhỏ:\n" + message;
    }

    private String buildStoryPrompt(String topic, String ageGroup) {
        return "Hãy kể một câu chuyện dành cho trẻ em với các yêu cầu sau:\n"
            + "- Chủ đề: " + topic + "\n"
            + "- Độ tuổi phù hợp: " + ageGroup + "\n"
            + "- Ngắn gọn (200-300 từ), dễ hiểu, vui tươi\n"
            + "- Nhân vật đáng yêu và kết thúc có hậu\n"
            + "- Lồng ghép một bài học nhỏ phù hợp với trẻ em\n"
            + "Bắt đầu với câu: 'Ngày xửa ngày xưa...'";
    }
}
