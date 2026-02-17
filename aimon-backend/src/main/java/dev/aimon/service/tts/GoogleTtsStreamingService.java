package dev.aimon.service.tts;

import com.google.cloud.texttospeech.v1.AudioConfig;
import com.google.cloud.texttospeech.v1.AudioEncoding;
import com.google.cloud.texttospeech.v1.SynthesisInput;
import com.google.cloud.texttospeech.v1.SynthesizeSpeechResponse;
import com.google.cloud.texttospeech.v1.TextToSpeechClient;
import com.google.cloud.texttospeech.v1.VoiceSelectionParams;
import com.google.protobuf.ByteString;
import dev.aimon.config.AIConfig;
import dev.aimon.dto.tts.TtsRequest;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Google Cloud Text-to-Speech Streaming Service.
 * Converts text to speech using Google Cloud TTS API and streams audio chunks
 * for low-latency playback.
 */
@Startup
@ApplicationScoped
public class GoogleTtsStreamingService {

    private static final Logger LOG = Logger.getLogger(GoogleTtsStreamingService.class);

    // Chunk size for streaming audio to client
    private static final int CHUNK_SIZE = 32768; // 32KB per chunk

    @Inject
    AIConfig aiConfig;

    private TextToSpeechClient ttsClient;
    private boolean initialized = false;

    @PostConstruct
    void init() {
        try {
            AIConfig.GoogleTtsConfig config = aiConfig.googleTts();

            // Load credentials from file if specified, otherwise use Application Default
            // Credentials
            if (config.credentialsFile().isPresent() && !config.credentialsFile().get().isBlank()) {
                String credentialsPath = config.credentialsFile().get();
                LOG.infof("Loading Google Cloud TTS credentials from: %s", credentialsPath);

                java.io.FileInputStream credentialsStream = new java.io.FileInputStream(credentialsPath);
                com.google.auth.oauth2.GoogleCredentials credentials = com.google.auth.oauth2.GoogleCredentials
                        .fromStream(credentialsStream);
                credentialsStream.close();

                com.google.cloud.texttospeech.v1.TextToSpeechSettings settings = com.google.cloud.texttospeech.v1.TextToSpeechSettings
                        .newBuilder()
                        .setCredentialsProvider(() -> credentials)
                        .build();

                ttsClient = TextToSpeechClient.create(settings);
            } else {
                // Fallback to Application Default Credentials
                ttsClient = TextToSpeechClient.create();
            }
            initialized = true;
            LOG.info("Google Cloud TTS client initialized successfully");
        } catch (Exception e) {
            LOG.errorf(e, "Failed to initialize Google Cloud TTS client: %s", e.getMessage());
            // Don't throw, just mark as not initialized - fallback provider will be used
        }
    }

    @PreDestroy
    void cleanup() {
        if (ttsClient != null) {
            ttsClient.close();
            LOG.info("Google Cloud TTS client closed");
        }
    }

    /**
     * Check if Google TTS client is available.
     */
    public boolean isAvailable() {
        return initialized && ttsClient != null;
    }

    /**
     * Generate speech and stream audio chunks asynchronously.
     *
     * This method calls Google Cloud TTS API and streams the audio response
     * in chunks, enabling low-latency playback.
     *
     * @param request    TTS request containing text to convert
     * @param onChunk    Callback for each audio chunk, return false to stop
     *                   streaming
     * @param onComplete Callback when streaming completes successfully
     * @param onError    Callback on error with the exception
     */
    public void generateSpeechStreaming(
            TtsRequest request,
            Function<byte[], Boolean> onChunk,
            Runnable onComplete,
            Consumer<Exception> onError) {

        // Run async to not block WebSocket thread
        CompletableFuture.runAsync(() -> {
            try {
                AIConfig.GoogleTtsConfig config = aiConfig.googleTts();

                // Check if service is enabled
                if (!config.enabled()) {
                    throw new IllegalStateException("Google Cloud TTS is disabled in configuration");
                }

                // Truncate text for logging
                String textPreview = request.getText().length() > 50
                        ? request.getText().substring(0, 50) + "..."
                        : request.getText();

                LOG.infof("Starting Google TTS streaming for text: '%s' (length=%d)",
                        textPreview, request.getText().length());

                long startTime = System.currentTimeMillis();

                // Build synthesis input
                SynthesisInput input = SynthesisInput.newBuilder()
                        .setText(request.getText())
                        .build();

                // Build voice selection params
                VoiceSelectionParams voice = VoiceSelectionParams.newBuilder()
                        .setLanguageCode(config.languageCode())
                        .setName(config.voiceName())
                        .build();

                // Build audio config for PCM output (LINEAR16)
                AudioConfig audioConfig = AudioConfig.newBuilder()
                        .setAudioEncoding(AudioEncoding.LINEAR16)
                        .setSampleRateHertz(config.sampleRate())
                        .setSpeakingRate(config.speakingRate())
                        .setPitch(config.pitch())
                        .build();

                // Call Google TTS API
                SynthesizeSpeechResponse response = ttsClient.synthesizeSpeech(input, voice, audioConfig);

                long apiResponseTime = System.currentTimeMillis() - startTime;
                LOG.infof("Google TTS API responded in %d ms", apiResponseTime);

                // Get audio content
                ByteString audioContent = response.getAudioContent();
                byte[] audioBytes = audioContent.toByteArray();

                LOG.infof("Google TTS synthesized %d bytes", audioBytes.length);

                // Stream audio in chunks
                streamAudioChunks(audioBytes, onChunk);

                // Complete
                long totalTime = System.currentTimeMillis() - startTime;
                LOG.infof("Google TTS streaming completed in %d ms total", totalTime);
                onComplete.run();

            } catch (Exception e) {
                LOG.errorf(e, "Error in Google TTS streaming: %s", e.getMessage());
                onError.accept(e);
            }
        });
    }

    /**
     * Stream audio bytes to client in chunks.
     *
     * @param audioBytes Complete audio data
     * @param onChunk    Callback for each chunk, return false to stop
     */
    private void streamAudioChunks(byte[] audioBytes, Function<byte[], Boolean> onChunk) {
        int totalBytes = audioBytes.length;
        int offset = 0;
        int chunkIndex = 0;

        while (offset < totalBytes) {
            int remaining = totalBytes - offset;
            int chunkSize = Math.min(CHUNK_SIZE, remaining);

            byte[] chunk = new byte[chunkSize];
            System.arraycopy(audioBytes, offset, chunk, 0, chunkSize);

            if (chunkIndex == 0) {
                LOG.infof("First audio chunk ready: %d bytes", chunk.length);
            }

            LOG.debugf("Streaming chunk %d: %d bytes (offset: %d, total: %d)",
                    chunkIndex, chunk.length, offset, totalBytes);

            // Send chunk via callback
            // If callback returns false, stop streaming
            if (!onChunk.apply(chunk)) {
                LOG.infof("Streaming stopped by client callback after %d bytes", offset + chunkSize);
                return;
            }

            offset += chunkSize;
            chunkIndex++;
        }

        LOG.infof("Google TTS streaming completed: total %d bytes in %d chunks", totalBytes, chunkIndex);
    }
}
