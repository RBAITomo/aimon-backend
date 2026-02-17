package dev.aimon.dto.conversation;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO cho conversation message endpoint
 * Chứa thông tin từ user gửi lên robot để trò chuyện hoặc yêu cầu kể chuyện
 */
public class ConversationMessageRequest {

    @NotBlank(message = "User ID can not be null")
    private String userId;

    @NotBlank(message = "Session ID can not be null")
    private String sessionId;

    @NotBlank(message = "Message can not be null")
    private String message;

    @NotBlank(message = "Action can not be null")
    private String action; // "chat" hoặc "storytelling"

    // Tuỳ chọn: số lượng tài liệu RAG cần lấy (mặc định 5 nếu null)
    private Integer topK;

    public ConversationMessageRequest() {
    }

    public ConversationMessageRequest(String userId, String sessionId, String message, String action) {
        this.userId = userId;
        this.sessionId = sessionId;
        this.message = message;
        this.action = action;
        this.topK = 5; // Mặc định lấy 5 tài liệu nếu không có giá trị truyền vào
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public Integer getTopK() {
        return topK;
    }

    public void setTopK(Integer topK) {
        this.topK = topK;
    }

    @Override
    public String toString() {
        return "ConversationMessageRequest{" +
                "userId='" + userId + '\'' +
                ", sessionId='" + sessionId + '\'' +
                ", message='" + message + '\'' +
                ", action='" + action + '\'' +
                ", topK=" + topK +
                '}';
    }
}
