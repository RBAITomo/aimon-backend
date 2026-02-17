package dev.aimon.service.ai;

import dev.aimon.config.AIConfig;
import dev.aimon.dto.ai.LiteLlmChatMessage;
import dev.aimon.dto.ai.LiteLlmChatRequest;
import dev.aimon.dto.ai.LiteLlmChatResponse;
import dev.aimon.service.SentenceSplitterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for LiteLlmAIService.
 * Mocks the LiteLlmClientService to avoid real API calls.
 */
@ExtendWith(MockitoExtension.class)
class LiteLlmAIServiceTest {

    @Mock
    AIConfig aiConfig;

    @Mock
    LiteLlmClientService liteLlmClientService;

    @Mock
    SentenceSplitterService sentenceSplitterService;

    @InjectMocks
    LiteLlmAIService aiService;

    @BeforeEach
    void setUp() {
        // No additional setup needed - Mockito handles injection
    }

    private LiteLlmChatRequest anyRequest() {
        return ArgumentMatchers.<LiteLlmChatRequest>any();
    }

    @Test
    void generateResponse_success_returnsContent() {
        LiteLlmChatResponse response = buildMockResponse("Xin chào bạn!");
        when(liteLlmClientService.chatCompletion(anyRequest())).thenReturn(response);

        String result = aiService.generateResponse("Hello", null);

        assertEquals("Xin chào bạn!", result);
        verify(liteLlmClientService).chatCompletion(anyRequest());
    }

    @Test
    void generateResponse_withContext_includesContextInPrompt() {
        LiteLlmChatResponse response = buildMockResponse("Response with context");
        when(liteLlmClientService.chatCompletion(anyRequest())).thenReturn(response);

        String result = aiService.generateResponse("Hello", "Some context");

        assertEquals("Response with context", result);
        verify(liteLlmClientService).chatCompletion(ArgumentMatchers.<LiteLlmChatRequest>argThat(req -> {
            List<LiteLlmChatMessage> msgs = req.getMessages();
            String lastContent = msgs.get(msgs.size() - 1).getContent();
            return lastContent.contains("Some context") && lastContent.contains("Hello");
        }));
    }

    @Test
    void generateResponse_exception_returnsFallback() {
        when(liteLlmClientService.chatCompletion(anyRequest()))
            .thenThrow(new RuntimeException("API error"));

        String result = aiService.generateResponse("Hello", null);

        assertNotNull(result);
        assertTrue(result.contains("robot Min"));
    }

    @Test
    void generateStory_success_returnsStory() {
        LiteLlmChatResponse response = buildMockResponse("Ngày xửa ngày xưa...");
        when(liteLlmClientService.chatCompletion(anyRequest())).thenReturn(response);

        String result = aiService.generateStory("con mèo", "6-8");

        assertEquals("Ngày xửa ngày xưa...", result);
        verify(liteLlmClientService).chatCompletion(ArgumentMatchers.<LiteLlmChatRequest>argThat(req -> {
            List<LiteLlmChatMessage> msgs = req.getMessages();
            String lastContent = msgs.get(msgs.size() - 1).getContent();
            return lastContent.contains("con mèo") && lastContent.contains("6-8");
        }));
    }

    @Test
    void generateStory_exception_returnsFallback() {
        when(liteLlmClientService.chatCompletion(anyRequest()))
            .thenThrow(new RuntimeException("API error"));

        String result = aiService.generateStory("topic", "6-8");

        assertNotNull(result);
        assertTrue(result.contains("robot Min"));
    }

    @Test
    void createEmbedding_delegatesToClient() {
        float[] expected = new float[]{0.1f, 0.2f};
        when(liteLlmClientService.createEmbedding("test")).thenReturn(expected);

        float[] result = aiService.createEmbedding("test");

        assertArrayEquals(expected, result);
    }

    @Test
    void getProviderName_returnsLitellm() {
        assertEquals("litellm", aiService.getProviderName());
    }

    @Test
    void generateResponse_nullContext_treatedAsNoContext() {
        LiteLlmChatResponse response = buildMockResponse("Simple response");
        when(liteLlmClientService.chatCompletion(anyRequest())).thenReturn(response);

        String result = aiService.generateResponse("Hello", null);

        assertEquals("Simple response", result);
    }

    @Test
    void generateResponse_blankContext_treatedAsNoContext() {
        LiteLlmChatResponse response = buildMockResponse("Simple response");
        when(liteLlmClientService.chatCompletion(anyRequest())).thenReturn(response);

        String result = aiService.generateResponse("Hello", "   ");

        assertEquals("Simple response", result);
        verify(liteLlmClientService).chatCompletion(ArgumentMatchers.<LiteLlmChatRequest>argThat(req -> {
            List<LiteLlmChatMessage> msgs = req.getMessages();
            String lastContent = msgs.get(msgs.size() - 1).getContent();
            return lastContent.equals("Hello");
        }));
    }

    /**
     * Helper to build a mock LiteLlmChatResponse with given content.
     */
    private LiteLlmChatResponse buildMockResponse(String content) {
        LiteLlmChatMessage message = new LiteLlmChatMessage("assistant", content);

        LiteLlmChatResponse.Choice choice = new LiteLlmChatResponse.Choice();
        choice.setIndex(0);
        choice.setMessage(message);
        choice.setFinishReason("stop");

        LiteLlmChatResponse response = new LiteLlmChatResponse();
        response.setId("test-id");
        response.setChoices(List.of(choice));
        return response;
    }
}
