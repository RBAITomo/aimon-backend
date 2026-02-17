package dev.aimon.dto.ai;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * OpenAI style chat message used by the LiteLLM proxy.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LiteLlmChatMessage {

    @JsonProperty("role")
    private String role;

    @JsonProperty("content")
    private String content;
    @JsonProperty("tool_calls")
    private String toolCalls;
    @JsonProperty("function_call")
    private String functionCall;
    
    public LiteLlmChatMessage() {
    }

    public LiteLlmChatMessage(String role, String content) {
        this.role = role;
        this.content = content;
    }

    public static LiteLlmChatMessage system(String content) {
        return new LiteLlmChatMessage("system", content);
    }

    public static LiteLlmChatMessage user(String content) {
        return new LiteLlmChatMessage("user", content);
    }

    public static LiteLlmChatMessage assistant(String content) {
        return new LiteLlmChatMessage("assistant", content);
    }

    // Record-style accessors for compatibility
    public String role() {
        return role;
    }

    public String content() {
        return content;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getToolCalls() {
        return toolCalls;
    }

    public void setToolCalls(String toolCalls) {
        this.toolCalls = toolCalls;
    }

    public String getFunctionCall() {
        return functionCall;
    }

    public void setFunctionCall(String functionCall) {
        this.functionCall = functionCall;
    }
}
