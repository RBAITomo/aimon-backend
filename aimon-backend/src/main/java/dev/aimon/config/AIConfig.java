package dev.aimon.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

import java.util.Optional;

/**
 * Central configuration for AI providers (LiteLLM, TTS, STT, PowerMem).
 */
@ConfigMapping(prefix = "ai")
public interface AIConfig {

    /**
     * Default AI provider backing conversation features.
     */
    @WithDefault("litellm")
    String provider();

    /**
     * LiteLLM proxy configuration (OpenAI compatible).
     */
    LiteLlmConfig litellm();

    /**
     * Timeout settings applied to outbound HTTP calls.
     */
    TimeoutConfig timeout();

    /**
     * VieNeu-TTS text-to-speech configuration.
     * GPU-accelerated Vietnamese TTS using VieNeu-TTS 0.5B model.
     */
    @WithName("vieneu-tts")
    VieNeuTtsConfig vieneuTts();

    /**
     * Speech-to-text configuration.
     */
    SttConfig stt();

    /**
     * Google Cloud Text-to-Speech configuration.
     */
    GoogleTtsConfig googleTts();

    /**
     * Google Cloud Speech-to-Text configuration.
     */
    GoogleSttConfig googleStt();

    /**
     * TTS Provider orchestration configuration.
     */
    TtsProviderConfig tts();

    /**
     * PowerMem memory service configuration.
     */
    PowerMemConfig powerMem();

    /**
     * Conversation session configuration.
     */
    ConversationConfig conversation();

    interface LiteLlmConfig {
        @WithName("base-url")
        @WithDefault("http://localhost:4000")
        String baseUrl();

        @WithName("api-key")
        Optional<String> apiKey();

        @WithName("default-model")
        @WithDefault("gpt-4.1-mini")
        String defaultModel();

        @WithName("embedding-model")
        @WithDefault("text-embedding-3-large")
        String embeddingModel();

        @WithName("max-output-tokens")
        @WithDefault("1024")
        int maxOutputTokens();

        @WithName("temperature")
        @WithDefault("0.7")
        double temperature();
    }

    interface TimeoutConfig {
        @WithDefault("30")
        long connect();

        @WithDefault("120")
        long read();
    }

    /**
     * VieNeu-TTS configuration for GPU-accelerated Vietnamese TTS.
     */
    interface VieNeuTtsConfig {
        @WithName("base-url")
        @WithDefault("http://vieneu-service:5001")
        String baseUrl();

        @WithName("model")
        @WithDefault("pnnbao-ump/VieNeu-TTS")
        String model();

        @WithName("voice-id")
        @WithDefault("Ngoc")
        String voiceId();

        @WithName("response-format")
        @WithDefault("pcm")
        String responseFormat();

        @WithName("sample-rate")
        @WithDefault("16000")
        int sampleRate();

        @WithName("silence-p")
        @WithDefault("0.2")
        double silenceP();

        @WithName("enabled")
        @WithDefault("false")
        boolean enabled();

        @WithName("streaming")
        @WithDefault("true")
        boolean streaming();

        @WithName("rollout-percentage")
        @WithDefault("0")
        int rolloutPercentage();
    }

    interface SttConfig {
        @WithDefault("google-streaming")
        String provider();

        @WithName("api-key")
        Optional<String> apiKey();
    }

    /**
     * Google Cloud Text-to-Speech configuration.
     */
    interface GoogleTtsConfig {
        @WithName("enabled")
        @WithDefault("true")
        boolean enabled();

        @WithName("credentials-file")
        Optional<String> credentialsFile();

        @WithName("project-id")
        Optional<String> projectId();

        @WithName("language-code")
        @WithDefault("vi-VN")
        String languageCode();

        @WithName("voice-name")
        @WithDefault("vi-VN-Neural2-A")
        String voiceName();

        @WithName("sample-rate")
        @WithDefault("16000")
        int sampleRate();

        @WithName("speaking-rate")
        @WithDefault("1.0")
        double speakingRate();

        @WithName("pitch")
        @WithDefault("0.0")
        double pitch();
    }

    /**
     * Google Cloud Speech-to-Text configuration.
     */
    interface GoogleSttConfig {
        @WithName("enabled")
        @WithDefault("true")
        boolean enabled();

        @WithName("credentials-file")
        Optional<String> credentialsFile();

        @WithName("language-code")
        @WithDefault("vi-VN")
        String languageCode();

        @WithName("model")
        @WithDefault("chirp")
        String model();

        @WithName("sample-rate")
        @WithDefault("16000")
        int sampleRate();
    }

    /**
     * TTS Provider orchestration configuration.
     */
    interface TtsProviderConfig {
        @WithName("primary-provider")
        @WithDefault("google")
        String primaryProvider();

        @WithName("fallback-provider")
        @WithDefault("vieneu")
        String fallbackProvider();

        CircuitBreakerConfig circuitBreaker();

        interface CircuitBreakerConfig {
            @WithName("failure-threshold")
            @WithDefault("3")
            int failureThreshold();

            @WithName("reset-timeout")
            @WithDefault("30000")
            long resetTimeout();
        }
    }

    /**
     * PowerMem memory service configuration.
     */
    interface PowerMemConfig {
        @WithName("base-url")
        @WithDefault("http://localhost:8100")
        String baseUrl();

        @WithName("enabled")
        @WithDefault("true")
        boolean enabled();
    }

    /**
     * Conversation session configuration.
     */
    interface ConversationConfig {
        @WithName("max-history-messages")
        @WithDefault("20")
        int maxHistoryMessages();

        @WithName("session-timeout-minutes")
        @WithDefault("30")
        int sessionTimeoutMinutes();
    }
}
