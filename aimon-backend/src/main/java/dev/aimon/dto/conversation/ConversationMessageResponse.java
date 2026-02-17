package dev.aimon.dto.conversation;

/**
 * Response DTO cho conversation message endpoint
 * Chứa phản hồi từ robot sau khi xử lý tin nhắn của user
 */
public class ConversationMessageResponse {

    private String responseText;
    private String sessionId;
    private String action;

    public ConversationMessageResponse() {
    }

    public ConversationMessageResponse(String responseText, String sessionId, String action) {
        this.responseText = responseText;
        this.sessionId = sessionId;
        this.action = action;
    }

    public String getResponseText() {
        return responseText;
    }

    public void setResponseText(String responseText) {
        this.responseText = responseText;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    @Override
    public String toString() {
        return "ConversationMessageResponse{" +
                "responseText='" + responseText + '\'' +
                ", sessionId='" + sessionId + '\'' +
                ", action='" + action + '\'' +
                '}';
    }

    /**
     * Create a success response.
     */
    public static ConversationMessageResponse success(String responseText, String sessionId) {
        return new ConversationMessageResponse(responseText, sessionId, "chat");
    }

    /**
     * Create an error response.
     */
    public static ConversationMessageResponse error(String errorMessage, String sessionId) {
        return new ConversationMessageResponse(errorMessage, sessionId, "error");
    }
}
