package dev.aimon.service.tts;

import dev.aimon.config.AIConfig;
import dev.aimon.dto.tts.TtsRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TtsProviderService.
 * Tests circuit breaker and provider failover logic.
 */
@ExtendWith(MockitoExtension.class)
class TtsProviderServiceTest {

    @Mock
    GoogleTtsStreamingService googleTtsService;

    @Mock
    VieNeuTtsService vieNeuTtsService;

    @Mock
    AIConfig aiConfig;

    @Mock
    AIConfig.TtsProviderConfig ttsConfig;

    @Mock
    AIConfig.TtsProviderConfig.CircuitBreakerConfig cbConfig;

    @Mock
    AIConfig.VieNeuTtsConfig vieneuConfig;

    @Mock
    AIConfig.GoogleTtsConfig googleConfig;

    @InjectMocks
    TtsProviderService ttsProviderService;

    @BeforeEach
    void setUp() {
        lenient().when(aiConfig.tts()).thenReturn(ttsConfig);
        lenient().when(ttsConfig.primaryProvider()).thenReturn("google");
        lenient().when(ttsConfig.fallbackProvider()).thenReturn("vieneu");
        lenient().when(ttsConfig.circuitBreaker()).thenReturn(cbConfig);
        lenient().when(cbConfig.failureThreshold()).thenReturn(3);
        lenient().when(cbConfig.resetTimeout()).thenReturn(30000L);
        lenient().when(aiConfig.vieneuTts()).thenReturn(vieneuConfig);
        lenient().when(aiConfig.googleTts()).thenReturn(googleConfig);
        lenient().when(vieneuConfig.enabled()).thenReturn(true);
        lenient().when(googleConfig.enabled()).thenReturn(true);
    }

    @Test
    void generateSpeechStreaming_primarySuccess_usesPrimary() {
        doAnswer(invocation -> {
            Runnable onComplete = invocation.getArgument(2);
            onComplete.run();
            return null;
        }).when(googleTtsService).generateSpeechStreaming(any(), any(), any(), any());

        boolean[] completed = {false};

        ttsProviderService.generateSpeechStreaming(
            mockTtsRequest(),
            chunk -> true,
            () -> completed[0] = true,
            error -> fail("Should not error")
        );

        assertTrue(completed[0]);
        verify(googleTtsService).generateSpeechStreaming(any(), any(), any(), any());
        verify(vieNeuTtsService, never()).generateSpeechStreaming(any(), any(), any(), any());
    }

    @Test
    void generateSpeechStreaming_primaryFails_fallsBackToSecondary() {
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Consumer<Exception> onError = invocation.getArgument(3);
            onError.accept(new RuntimeException("Google TTS failed"));
            return null;
        }).when(googleTtsService).generateSpeechStreaming(any(), any(), any(), any());

        doAnswer(invocation -> {
            Runnable onComplete = invocation.getArgument(2);
            onComplete.run();
            return null;
        }).when(vieNeuTtsService).generateSpeechStreaming(any(), any(), any(), any());

        boolean[] completed = {false};

        ttsProviderService.generateSpeechStreaming(
            mockTtsRequest(),
            chunk -> true,
            () -> completed[0] = true,
            error -> fail("Should not error after fallback")
        );

        assertTrue(completed[0]);
        verify(googleTtsService).generateSpeechStreaming(any(), any(), any(), any());
        verify(vieNeuTtsService).generateSpeechStreaming(any(), any(), any(), any());
    }

    @Test
    void generateSpeechStreaming_primaryDisabled_usesFallback() {
        when(googleConfig.enabled()).thenReturn(false);

        doAnswer(invocation -> {
            Runnable onComplete = invocation.getArgument(2);
            onComplete.run();
            return null;
        }).when(vieNeuTtsService).generateSpeechStreaming(any(), any(), any(), any());

        boolean[] completed = {false};

        ttsProviderService.generateSpeechStreaming(
            mockTtsRequest(),
            chunk -> true,
            () -> completed[0] = true,
            error -> fail("Should not error")
        );

        assertTrue(completed[0]);
        verify(googleTtsService, never()).generateSpeechStreaming(any(), any(), any(), any());
        verify(vieNeuTtsService).generateSpeechStreaming(any(), any(), any(), any());
    }

    @Test
    void generateSpeechStreaming_bothDisabled_callsError() {
        when(googleConfig.enabled()).thenReturn(false);
        when(vieneuConfig.enabled()).thenReturn(false);

        boolean[] errored = {false};

        ttsProviderService.generateSpeechStreaming(
            mockTtsRequest(),
            chunk -> true,
            () -> fail("Should not complete"),
            error -> errored[0] = true
        );

        assertTrue(errored[0]);
    }

    @Test
    void circuitBreaker_opensAfterThreshold() {
        // Setup: Google fails, VieNeu succeeds as fallback
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Consumer<Exception> onError = invocation.getArgument(3);
            onError.accept(new RuntimeException("Failure"));
            return null;
        }).when(googleTtsService).generateSpeechStreaming(any(), any(), any(), any());

        doAnswer(invocation -> {
            Runnable onComplete = invocation.getArgument(2);
            onComplete.run();
            return null;
        }).when(vieNeuTtsService).generateSpeechStreaming(any(), any(), any(), any());

        // Fail primary 3 times (threshold)
        for (int i = 0; i < 3; i++) {
            ttsProviderService.generateSpeechStreaming(
                mockTtsRequest(), chunk -> true, () -> {}, error -> {}
            );
        }

        // After 3 failures, circuit should be open
        assertTrue(ttsProviderService.isCircuitBreakerOpen());
        assertEquals(3, ttsProviderService.getConsecutiveFailures());
    }

    @Test
    void circuitBreaker_resetManually() {
        // Setup: Google fails, VieNeu succeeds
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Consumer<Exception> onError = invocation.getArgument(3);
            onError.accept(new RuntimeException("Failure"));
            return null;
        }).when(googleTtsService).generateSpeechStreaming(any(), any(), any(), any());

        doAnswer(invocation -> {
            Runnable onComplete = invocation.getArgument(2);
            onComplete.run();
            return null;
        }).when(vieNeuTtsService).generateSpeechStreaming(any(), any(), any(), any());

        // Open the circuit
        for (int i = 0; i < 3; i++) {
            ttsProviderService.generateSpeechStreaming(
                mockTtsRequest(), chunk -> true, () -> {}, error -> {}
            );
        }

        assertTrue(ttsProviderService.isCircuitBreakerOpen());

        ttsProviderService.resetAllCircuitBreakers();

        assertFalse(ttsProviderService.isCircuitBreakerOpen());
        assertEquals(0, ttsProviderService.getConsecutiveFailures());
    }

    @Test
    void resetCircuitBreakerFor_specificProvider() {
        ttsProviderService.resetCircuitBreakerFor("google");
        assertFalse(ttsProviderService.isCircuitBreakerOpen());
    }

    @Test
    void getProviderStatus_returnsFormattedString() {
        String status = ttsProviderService.getProviderStatus();

        assertNotNull(status);
        assertTrue(status.contains("Primary=google"));
        assertTrue(status.contains("Fallback=vieneu"));
        assertTrue(status.contains("HEALTHY"));
    }

    @Test
    void getProviderStatus_disabledProvider_showsDisabled() {
        when(googleConfig.enabled()).thenReturn(false);

        String status = ttsProviderService.getProviderStatus();

        assertTrue(status.contains("DISABLED"));
    }

    @Test
    void resetCircuitBreaker_backwardCompatibility() {
        assertDoesNotThrow(() -> ttsProviderService.resetCircuitBreaker());
    }

    private TtsRequest mockTtsRequest() {
        TtsRequest request = new TtsRequest();
        request.setText("Xin chào");
        return request;
    }
}
