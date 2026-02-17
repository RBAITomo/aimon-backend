package dev.aimon.dto.ai;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Response payload returned by the LiteLLM /v1/chat/completions endpoint.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LiteLlmChatResponse {

    @JsonProperty("id")
    private String id;

    @JsonProperty("created")
    private Long created;

    @JsonProperty("model")
    private String model;

    @JsonProperty("object")
    private String responseObject;

    @JsonProperty("system_fingerprint")
    private String systemFingerprint;

    @JsonProperty("service_tier")
    private String serviceTier;

    @JsonProperty("response_ms")
    private Long responseMs;

    @JsonProperty("choices")
    private List<Choice> choices;

    @JsonProperty("usage")
    private Usage usage;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Long getCreated() {
        return created;
    }

    public void setCreated(Long created) {
        this.created = created;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getResponseObject() {
        return responseObject;
    }

    public void setResponseObject(String responseObject) {
        this.responseObject = responseObject;
    }

    public String getSystemFingerprint() {
        return systemFingerprint;
    }

    public void setSystemFingerprint(String systemFingerprint) {
        this.systemFingerprint = systemFingerprint;
    }

    public String getServiceTier() {
        return serviceTier;
    }

    public void setServiceTier(String serviceTier) {
        this.serviceTier = serviceTier;
    }

    public Long getResponseMs() {
        return responseMs;
    }

    public void setResponseMs(Long responseMs) {
        this.responseMs = responseMs;
    }

    public List<Choice> getChoices() {
        return choices == null ? Collections.emptyList() : choices;
    }

    public void setChoices(List<Choice> choices) {
        this.choices = choices;
    }

    public Usage getUsage() {
        return usage;
    }

    public void setUsage(Usage usage) {
        this.usage = usage;
    }

    /**
     * Convenient accessor used by higher layers.
     */
    public String firstMessageContent() {
        return getChoices().stream()
            .findFirst()
            .map(Choice::getMessage)
            .map(LiteLlmChatMessage::getContent)
            .orElse("");
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Choice {
        @JsonProperty("index")
        private Integer index;

        @JsonProperty("message")
        private LiteLlmChatMessage message;

        @JsonProperty("finish_reason")
        private String finishReason;

        @JsonProperty("logprobs")
        private JsonNode logprobs;

        public Integer getIndex() {
            return index;
        }

        public void setIndex(Integer index) {
            this.index = index;
        }

        public LiteLlmChatMessage getMessage() {
            return message;
        }

        public void setMessage(LiteLlmChatMessage message) {
            this.message = message;
        }

        public String getFinishReason() {
            return finishReason;
        }

        public void setFinishReason(String finishReason) {
            this.finishReason = finishReason;
        }

        public JsonNode getLogprobs() {
            return logprobs;
        }

        public void setLogprobs(JsonNode logprobs) {
            this.logprobs = logprobs;
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Usage {
        @JsonProperty("prompt_tokens")
        private Integer promptTokens;

        @JsonProperty("completion_tokens")
        private Integer completionTokens;

        @JsonProperty("total_tokens")
        private Integer totalTokens;

        @JsonProperty("prompt_tokens_details")
        private Map<String, Integer> promptTokensDetails;

        @JsonProperty("completion_tokens_details")
        private Map<String, Integer> completionTokensDetails;

        @JsonProperty("cache_creation_input_tokens")
        private Integer cacheCreationInputTokens;

        @JsonProperty("cache_read_input_tokens")
        private Integer cacheReadInputTokens;

        public Integer getPromptTokens() {
            return promptTokens;
        }

        public void setPromptTokens(Integer promptTokens) {
            this.promptTokens = promptTokens;
        }

        public Integer getCompletionTokens() {
            return completionTokens;
        }

        public void setCompletionTokens(Integer completionTokens) {
            this.completionTokens = completionTokens;
        }

        public Integer getTotalTokens() {
            return totalTokens;
        }

        public void setTotalTokens(Integer totalTokens) {
            this.totalTokens = totalTokens;
        }

        public Map<String, Integer> getPromptTokensDetails() {
            return promptTokensDetails;
        }

        public void setPromptTokensDetails(Map<String, Integer> promptTokensDetails) {
            this.promptTokensDetails = promptTokensDetails;
        }

        public Map<String, Integer> getCompletionTokensDetails() {
            return completionTokensDetails;
        }

        public void setCompletionTokensDetails(Map<String, Integer> completionTokensDetails) {
            this.completionTokensDetails = completionTokensDetails;
        }

        public Integer getCacheCreationInputTokens() {
            return cacheCreationInputTokens;
        }

        public void setCacheCreationInputTokens(Integer cacheCreationInputTokens) {
            this.cacheCreationInputTokens = cacheCreationInputTokens;
        }

        public Integer getCacheReadInputTokens() {
            return cacheReadInputTokens;
        }

        public void setCacheReadInputTokens(Integer cacheReadInputTokens) {
            this.cacheReadInputTokens = cacheReadInputTokens;
        }
    }
}
