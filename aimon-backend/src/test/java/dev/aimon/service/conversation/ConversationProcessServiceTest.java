package dev.aimon.service.conversation;

import dev.aimon.dto.conversation.ConversationMessageRequest;
import dev.aimon.dto.conversation.ConversationMessageResponse;
import dev.aimon.model.ConversationSession;
import dev.aimon.service.ai.LiteLlmAIService;
import dev.aimon.service.memory.ContextRetrievalService;
import dev.aimon.service.memory.PowerMemService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ConversationProcessService.
 * Tests non-streaming message processing with mocked dependencies.
 */
@ExtendWith(MockitoExtension.class)
class ConversationProcessServiceTest {

    @Mock
    LiteLlmAIService aiService;

    @Mock
    ConversationSessionManager sessionManager;

    @Mock
    ContextRetrievalService contextRetrievalService;

    @Mock
    PowerMemService memoryService;

    @InjectMocks
    ConversationProcessService processService;

    private ConversationMessageRequest request;
    private ConversationSession session;

    @BeforeEach
    void setUp() throws Exception {
        setField(processService, "powerMemEnabled", true);
        setField(processService, "kidModeEnabled", false);
        setField(processService, "kidModeAgeGroup", "6-8");

        request = new ConversationMessageRequest("1", "session-1", "Xin chào", "chat");
        session = new ConversationSession(1, "session-1", "System prompt", Instant.now(), 10);
    }

    // --- processMessage (non-streaming) ---

    @Test
    void processMessage_success_returnsResponse() {
        when(sessionManager.getOrCreateSession(1, "session-1")).thenReturn(session);
        when(contextRetrievalService.retrieveContextForConversation(anyString(), anyInt()))
            .thenReturn("Memory context");
        when(aiService.generateResponse(anyString(), anyString()))
            .thenReturn("Xin chào bạn!");

        ConversationMessageResponse response = processService.processMessage(request);

        assertNotNull(response);
        assertEquals("Xin chào bạn!", response.getResponseText());
        assertEquals("session-1", response.getSessionId());
        assertEquals("chat", response.getAction());
    }

    @Test
    void processMessage_storesInSessionHistory() {
        when(sessionManager.getOrCreateSession(1, "session-1")).thenReturn(session);
        when(contextRetrievalService.retrieveContextForConversation(anyString(), anyInt()))
            .thenReturn("");
        when(aiService.generateResponse(anyString(), anyString()))
            .thenReturn("Response");

        processService.processMessage(request);

        assertEquals(1, session.getHistorySize());
    }

    @Test
    void processMessage_powerMemEnabled_recordsConversation() {
        when(sessionManager.getOrCreateSession(1, "session-1")).thenReturn(session);
        when(contextRetrievalService.retrieveContextForConversation(anyString(), anyInt()))
            .thenReturn("");
        when(aiService.generateResponse(anyString(), anyString()))
            .thenReturn("Response");

        processService.processMessage(request);

        // Should record both user message and AI response
        verify(memoryService, times(2)).recordAsync(anyString(), anyMap());
    }

    @Test
    void processMessage_powerMemDisabled_doesNotRecord() throws Exception {
        setField(processService, "powerMemEnabled", false);

        when(sessionManager.getOrCreateSession(1, "session-1")).thenReturn(session);
        when(aiService.generateResponse(anyString(), anyString()))
            .thenReturn("Response");

        processService.processMessage(request);

        verify(memoryService, never()).recordAsync(anyString(), anyMap());
    }

    @Test
    void processMessage_aiServiceException_returnsErrorResponse() {
        when(sessionManager.getOrCreateSession(1, "session-1")).thenReturn(session);
        when(contextRetrievalService.retrieveContextForConversation(anyString(), anyInt()))
            .thenReturn("");
        when(aiService.generateResponse(anyString(), anyString()))
            .thenThrow(new RuntimeException("LLM error"));

        ConversationMessageResponse response = processService.processMessage(request);

        assertNotNull(response);
        assertEquals("error", response.getAction());
        assertTrue(response.getResponseText().contains("Xin lỗi"));
    }

    @Test
    void processMessage_contextRetrievalFails_continuesWithoutContext() {
        when(sessionManager.getOrCreateSession(1, "session-1")).thenReturn(session);
        when(contextRetrievalService.retrieveContextForConversation(anyString(), anyInt()))
            .thenThrow(new RuntimeException("Memory error"));
        when(aiService.generateResponse(anyString(), anyString()))
            .thenReturn("Response without memory");

        ConversationMessageResponse response = processService.processMessage(request);

        // Should still succeed despite memory failure
        assertEquals("Response without memory", response.getResponseText());
    }

    @Test
    void processMessage_kidModeEnabled_includesKidContext() throws Exception {
        setField(processService, "kidModeEnabled", true);
        setField(processService, "kidModeAgeGroup", "4-6");

        when(sessionManager.getOrCreateSession(1, "session-1")).thenReturn(session);
        when(contextRetrievalService.retrieveContextForConversation(anyString(), anyInt()))
            .thenReturn("");
        when(aiService.generateResponse(anyString(), argThat(prompt ->
            prompt.contains("Kid Mode") && prompt.contains("4-6"))))
            .thenReturn("Kid-friendly response");

        ConversationMessageResponse response = processService.processMessage(request);

        assertEquals("Kid-friendly response", response.getResponseText());
    }

    @Test
    void processMessage_withSessionHistory_includesInPrompt() {
        session.addTurn("Previous question", "Previous answer");

        when(sessionManager.getOrCreateSession(1, "session-1")).thenReturn(session);
        when(contextRetrievalService.retrieveContextForConversation(anyString(), anyInt()))
            .thenReturn("");
        when(aiService.generateResponse(anyString(), argThat(prompt ->
            prompt.contains("Previous question") && prompt.contains("Previous answer"))))
            .thenReturn("Context-aware response");

        ConversationMessageResponse response = processService.processMessage(request);

        assertEquals("Context-aware response", response.getResponseText());
    }

    @Test
    void processMessage_sessionCreationFails_returnsError() {
        when(sessionManager.getOrCreateSession(anyInt(), anyString()))
            .thenThrow(new RuntimeException("Session error"));

        ConversationMessageResponse response = processService.processMessage(request);

        assertEquals("error", response.getAction());
    }

    // --- processMessageStreaming ---

    @Test
    void processMessageStreaming_success_callsCallbacks() {
        when(sessionManager.getOrCreateSession(1, "session-1")).thenReturn(session);
        when(contextRetrievalService.retrieveContextForConversation(anyString(), anyInt()))
            .thenReturn("");

        // Capture streaming callbacks
        doAnswer(invocation -> {
            var onSentence = invocation.getArgument(2, java.util.function.Consumer.class);
            var onComplete = invocation.getArgument(3, Runnable.class);
            onSentence.accept("Xin chào!");
            onComplete.run();
            return null;
        }).when(aiService).generateResponseStreaming(anyString(), anyString(), any(), any(), any());

        StringBuilder collected = new StringBuilder();
        boolean[] completed = {false};
        boolean[] errored = {false};

        processService.processMessageStreaming(
            request,
            sentence -> collected.append(sentence),
            () -> completed[0] = true,
            error -> errored[0] = true
        );

        assertTrue(collected.toString().contains("Xin chào!"));
        assertTrue(completed[0]);
        assertFalse(errored[0]);
    }

    @Test
    void processMessageStreaming_error_callsErrorCallback() {
        when(sessionManager.getOrCreateSession(1, "session-1")).thenReturn(session);
        when(contextRetrievalService.retrieveContextForConversation(anyString(), anyInt()))
            .thenReturn("");

        doAnswer(invocation -> {
            var onError = invocation.getArgument(4, java.util.function.Consumer.class);
            onError.accept(new RuntimeException("Stream error"));
            return null;
        }).when(aiService).generateResponseStreaming(anyString(), anyString(), any(), any(), any());

        boolean[] errored = {false};

        processService.processMessageStreaming(
            request,
            sentence -> {},
            () -> {},
            error -> errored[0] = true
        );

        assertTrue(errored[0]);
    }

    @Test
    void processMessageStreaming_sessionException_callsErrorCallback() {
        when(sessionManager.getOrCreateSession(anyInt(), anyString()))
            .thenThrow(new RuntimeException("Session error"));

        boolean[] errored = {false};

        processService.processMessageStreaming(
            request,
            sentence -> {},
            () -> {},
            error -> errored[0] = true
        );

        assertTrue(errored[0]);
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
