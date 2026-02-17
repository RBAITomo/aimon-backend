package dev.aimon.service.ai;

import dev.aimon.client.LiteLlmClient;
import dev.aimon.config.AIConfig;
import dev.aimon.dto.ai.LiteLlmChatMessage;
import dev.aimon.dto.ai.LiteLlmChatRequest;
import dev.aimon.dto.ai.LiteLlmChatResponse;
import dev.aimon.dto.ai.LiteLlmEmbeddingRequest;
import dev.aimon.dto.ai.LiteLlmEmbeddingResponse;
import dev.aimon.dto.ai.LiteLlmImageRequest;
import dev.aimon.dto.ai.LiteLlmImageResponse;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.ClientWebApplicationException;

import java.util.List;
import java.util.Optional;

/**
 * Thin wrapper around the LiteLLM REST client that injects defaults and headers.
 */
@ApplicationScoped
public class LiteLlmClientService {

    private static final Logger LOG = Logger.getLogger(LiteLlmClientService.class);

    @Inject
    AIConfig aiConfig ;
    @Inject
    @RestClient
    LiteLlmClient liteLlmClient;

    public LiteLlmChatResponse chatCompletion(LiteLlmChatRequest request) {
        applyChatDefaults(request);
        try {
            LOG.infof("LiteLLM request: model=%s, messages=%d, auth=%s",
                    request.getModel(), request.getMessages().size(),
                    authorizationHeader() != null ? "Bearer ***" : "null");
            return liteLlmClient.createChatCompletion(authorizationHeader(), request);
        } catch (ClientWebApplicationException ex) {
            String body = ex.getResponse() != null ? ex.getResponse().readEntity(String.class) : "no body";
            LOG.errorf("LiteLLM 400 response body: %s", body);
            throw ex;
        } catch (Exception ex) {
            LOG.errorf(ex, "LiteLLM chat completion failed: %s", ex.getMessage());
            throw ex;
        }
    }

    public LiteLlmChatResponse chatCompletion(List<LiteLlmChatMessage> messages) {
        return chatCompletion(messages, false);
    }

    public LiteLlmChatResponse chatCompletion(List<LiteLlmChatMessage> messages, boolean stream) {
        LiteLlmChatRequest request = new LiteLlmChatRequest();
        request.setMessages(messages);
        request.setStream(stream);
        request.setApiKey(apiKey());
        return chatCompletion(request);
    }

    /**
     * Create embedding (disabled in simplified version).
     */
    public float[] createEmbedding(String input) {
        LOG.warn("Embedding creation is disabled in simplified AI-MON backend");
        return new float[0];
    }

    /**
     * Generate image using LiteLLM proxy.
     * ⚠️ WARNING: Image generation is EXPENSIVE (~$0.04-0.12 per image)
     * Disabled in simplified version to reduce dependencies.
     */
    public LiteLlmImageResponse generateImage(String prompt, String size, String responseFormat, String userTag) {
        LOG.error("Image generation is disabled in simplified AI-MON backend");
        throw new UnsupportedOperationException("Image generation is not supported in this version");
    }

    public Multi<String> chatCompletionStream(LiteLlmChatRequest request) {
        applyChatDefaults(request);
        request.setStream(true); // Ensure streaming is enabled
        try {
            LOG.infof("LiteLLM stream request: model=%s, messages=%d, auth=%s",
                    request.getModel(), request.getMessages().size(),
                    authorizationHeader() != null ? "Bearer ***" : "null");
            return liteLlmClient.createChatCompletionStream(authorizationHeader(), request);
        } catch (ClientWebApplicationException ex) {
            String body = ex.getResponse() != null ? ex.getResponse().readEntity(String.class) : "no body";
            LOG.errorf("LiteLLM stream 400 response body: %s", body);
            throw ex;
        } catch (Exception ex) {
            LOG.errorf(ex, "LiteLLM chat completion stream failed: %s", ex.getMessage());
            throw ex;
        }
    }

    private void applyChatDefaults(LiteLlmChatRequest request) {
        if (request.getModel() == null) {
            request.setModel(aiConfig.litellm().defaultModel());
        }
        if (request.getTemperature() == null) {
            request.setTemperature(aiConfig.litellm().temperature());
        }
        if (request.getMaxTokens() == null) {
            request.setMaxTokens(aiConfig.litellm().maxOutputTokens());
        }
        if (request.getStream() == null) {
            request.setStream(Boolean.FALSE);
        }
        if (request.getMessages() == null || request.getMessages().isEmpty()) {
            throw new IllegalArgumentException("chat completion requires at least one message");
        }
    }

    private String authorizationHeader() {
        Optional<String> apiKey = aiConfig.litellm().apiKey();
        return apiKey.map(key -> key.startsWith("Bearer ") ? key : "Bearer " + key).orElse(null);
    }

    private String apiKey() {
        // x-api-key should be the raw value, not Optional#toString()
        return aiConfig.litellm().apiKey().orElse(null);
    }
}
