package dev.aimon.dto.ai;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request payload for embedding generation through LiteLLM.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LiteLlmEmbeddingRequest {

    @JsonProperty("model")
    private String model;

    @JsonProperty("input")
    private String input;
    @JsonProperty("api_key")
    private String apiKey;
    public LiteLlmEmbeddingRequest() {
    }

    public LiteLlmEmbeddingRequest(String model, String input) {
        this.model = model;
        this.input = input;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getInput() {
        return input;
    }

    public void setInput(String input) {
        this.input = input;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }
}
