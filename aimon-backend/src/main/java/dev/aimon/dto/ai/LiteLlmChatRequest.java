package dev.aimon.dto.ai;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Request payload for the LiteLLM /v1/chat/completions endpoint.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LiteLlmChatRequest {

    @JsonProperty("model")
    private String model;

    @JsonProperty("messages")
    private List<LiteLlmChatMessage> messages = new ArrayList<>();

    @JsonProperty("temperature")
    private Double temperature;

    @JsonProperty("max_tokens")
    private Integer maxTokens;

    @JsonProperty("top_p")
    private Double topP;

    @JsonProperty("stop")
    private List<String> stop;

    @JsonProperty("stream")
    private Boolean stream;

    @JsonProperty("stream_options")
    private Map<String, Object> streamOptions;

    @JsonProperty("api_key")
    private String apiKey;

    @JsonProperty("tools")
    private List<Object> tools;

    @JsonProperty("tool_choice")
    private Object toolChoice;

    @JsonProperty("functions")
    private List<Object> functions;

    @JsonProperty("function_call")
    private Object functionCall;

    public LiteLlmChatRequest() {
    }

    public LiteLlmChatRequest(String model, List<LiteLlmChatMessage> messages, Double temperature, Integer maxTokens, Double topP, List<String> stop, Boolean stream) {
        this.model = model;
        if (messages != null) {
            this.messages.addAll(messages);
        }
        this.temperature = temperature;
        this.maxTokens = maxTokens;
        this.topP = topP;
        this.stop = stop;
        this.stream = stream;
    }

    public void addMessage(LiteLlmChatMessage message) {
        this.messages.add(message);
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public List<LiteLlmChatMessage> getMessages() {
        return messages;
    }

    public void setMessages(List<LiteLlmChatMessage> messages) {
        this.messages = messages == null ? new ArrayList<>() : new ArrayList<>(messages);
    }

    public Double getTemperature() {
        return temperature;
    }

    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }

    public Integer getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(Integer maxTokens) {
        this.maxTokens = maxTokens;
    }

    public Double getTopP() {
        return topP;
    }

    public void setTopP(Double topP) {
        this.topP = topP;
    }

    public List<String> getStop() {
        return stop;
    }

    public void setStop(List<String> stop) {
        this.stop = stop;
    }

    public Boolean getStream() {
        return stream;
    }

    public void setStream(Boolean stream) {
        this.stream = stream;
    }

    public Map<String, Object> getStreamOptions() {
        return streamOptions;
    }

    public void setStreamOptions(Map<String, Object> streamOptions) {
        this.streamOptions = streamOptions == null ? null : new HashMap<>(streamOptions);
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public List<Object> getTools() {
        return tools;
    }

    public void setTools(List<Object> tools) {
        this.tools = tools;
    }

    public Object getToolChoice() {
        return toolChoice;
    }

    public void setToolChoice(Object toolChoice) {
        this.toolChoice = toolChoice;
    }

    public List<Object> getFunctions() {
        return functions;
    }

    public void setFunctions(List<Object> functions) {
        this.functions = functions;
    }

    public Object getFunctionCall() {
        return functionCall;
    }

    public void setFunctionCall(Object functionCall) {
        this.functionCall = functionCall;
    }
}
