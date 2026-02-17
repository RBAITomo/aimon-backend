package dev.aimon.dto.ai;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request payload for image generation through LiteLLM.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LiteLlmImageRequest {

    @JsonProperty("model")
    private String model;

    @JsonProperty("prompt")
    private String prompt;

    @JsonProperty("size")
    private String size;

    @JsonProperty("response_format")
    private String responseFormat;

    @JsonProperty("user")
    private String user;
    @JsonProperty("api_key")
    private String apiKey;
    public LiteLlmImageRequest() {
    }

    public LiteLlmImageRequest(String model, String prompt, String size, String responseFormat) {
        this.model = model;
        this.prompt = prompt;
        this.size = size;
        this.responseFormat = responseFormat;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public String getSize() {
        return size;
    }

    public void setSize(String size) {
        this.size = size;
    }

    public String getResponseFormat() {
        return responseFormat;
    }

    public void setResponseFormat(String responseFormat) {
        this.responseFormat = responseFormat;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }
}
