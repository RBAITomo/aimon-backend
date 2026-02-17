package dev.aimon.client;

import dev.aimon.dto.ai.LiteLlmChatRequest;
import dev.aimon.dto.ai.LiteLlmChatResponse;
import io.smallrye.mutiny.Multi;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/**
 * MicroProfile REST client targeting the LiteLLM proxy (OpenAI compatible endpoints).
 * Simplified for aimon-backend v0.2 - chat completions only.
 */
@RegisterRestClient(configKey = "litellm-api")
public interface LiteLlmClient {

    @POST
    @Path("/v1/chat/completions")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    LiteLlmChatResponse createChatCompletion(
        @HeaderParam("Authorization") String authorization,
        LiteLlmChatRequest request
    );

    @POST
    @Path("/v1/chat/completions")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.SERVER_SENT_EVENTS)
    Multi<String> createChatCompletionStream(
        @HeaderParam("Authorization") String authorization,
        LiteLlmChatRequest request
    );
}
