package dev.aimon.service.stt;

import com.google.cloud.speech.v1.RecognitionAudio;
import com.google.cloud.speech.v1.RecognitionConfig;
import com.google.cloud.speech.v1.RecognizeResponse;
import com.google.cloud.speech.v1.SpeechClient;
import com.google.cloud.speech.v1.SpeechContext;
import com.google.cloud.speech.v1.SpeechRecognitionResult;
import com.google.protobuf.ByteString;
import dev.aimon.config.AIConfig;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.util.List;

/**
 * Speech-to-Text Orchestrator using Google Cloud Speech-to-Text.
 * Simplified to support only Google STT streaming path.
 */
@Startup
@ApplicationScoped
public class SttOrchestrator {
    private static final Logger LOG = Logger.getLogger(SttOrchestrator.class);

    @Inject
    AIConfig aiConfig;

    private SpeechClient speechClient;

    @PostConstruct
    void init() {
        try {
            AIConfig.GoogleSttConfig googleCfg = aiConfig.googleStt();

            if (!googleCfg.enabled()) {
                LOG.info("Google Cloud STT is disabled in configuration");
                return;
            }

            // Load credentials from file if specified, otherwise use Application Default Credentials
            if (googleCfg.credentialsFile().isPresent() && !googleCfg.credentialsFile().get().isBlank()) {
                String credentialsPath = googleCfg.credentialsFile().get();
                LOG.infof("Loading Google Cloud STT credentials from: %s", credentialsPath);

                java.io.FileInputStream credentialsStream = new java.io.FileInputStream(credentialsPath);
                com.google.auth.oauth2.GoogleCredentials credentials = com.google.auth.oauth2.GoogleCredentials
                        .fromStream(credentialsStream);
                credentialsStream.close();

                com.google.cloud.speech.v1.SpeechSettings settings = com.google.cloud.speech.v1.SpeechSettings
                        .newBuilder()
                        .setCredentialsProvider(() -> credentials)
                        .build();

                speechClient = SpeechClient.create(settings);
            } else {
                // Fallback to Application Default Credentials
                speechClient = SpeechClient.create();
            }
            LOG.info("Google Cloud Speech-to-Text client initialized successfully");
        } catch (IOException e) {
            LOG.warnf("Failed to initialize Google Cloud STT client: %s. Google STT will be unavailable.",
                    e.getMessage());
        }
    }

    @PreDestroy
    void cleanup() {
        if (speechClient != null) {
            speechClient.close();
            LOG.info("Google Cloud Speech-to-Text client closed");
        }
    }

    /**
     * Check if STT service is available.
     */
    public boolean isAvailable() {
        return speechClient != null && aiConfig.googleStt().enabled();
    }

    /**
     * Transcribe audio bytes using Google Cloud Speech-to-Text.
     *
     * @param audio PCM16 audio bytes
     * @param sampleRate Sample rate in Hz (e.g., 16000)
     * @param lang Language code (e.g., "vi-VN")
     * @param format Audio format ("pcm16", "wav", "mp3", "webm", "opus")
     * @param phraseHints World-specific terms to boost recognition (may be null or empty)
     * @return Transcribed text or empty string on error
     */
    public String transcribe(byte[] audio, int sampleRate, String lang, String format, List<String> phraseHints) {
        if (speechClient == null) {
            LOG.warn("Google Cloud STT client not initialized");
            return "";
        }

        AIConfig.GoogleSttConfig cfg = aiConfig.googleStt();

        if (!cfg.enabled()) {
            LOG.warn("Google Cloud STT is disabled in configuration");
            return "";
        }

        try {
            LOG.infof("Calling Google Cloud STT: audioSize=%d, sampleRate=%d, lang=%s, format=%s",
                    audio.length, sampleRate, lang, format);

            long startTime = System.currentTimeMillis();

            // Determine audio encoding based on format
            RecognitionConfig.AudioEncoding encoding = getAudioEncoding(format);

            // Build recognition config
            RecognitionConfig.Builder configBuilder = RecognitionConfig.newBuilder()
                    .setEncoding(encoding)
                    .setSampleRateHertz(sampleRate)
                    .setLanguageCode(lang != null ? lang : cfg.languageCode())
                    .setModel(cfg.model())
                    .setEnableAutomaticPunctuation(true);

            // Inject world-specific phrase hints to boost proper noun recognition.
            // Google STT SpeechContext limit: 500 phrases per context.
            // Boost 15.0f: strong preference for listed terms without blocking other words (range 0-20).
            if (phraseHints != null && !phraseHints.isEmpty()) {
                List<String> bounded = phraseHints.size() > 500
                        ? phraseHints.subList(0, 500)
                        : phraseHints;
                SpeechContext speechContext = SpeechContext.newBuilder()
                        .addAllPhrases(bounded)
                        .setBoost(15.0f)
                        .build();
                configBuilder.addSpeechContexts(speechContext);
                LOG.debugf("STT phrase hints: %d terms boosted", bounded.size());
            }

            RecognitionConfig config = configBuilder.build();

            // Build recognition audio
            RecognitionAudio recognitionAudio = RecognitionAudio.newBuilder()
                    .setContent(ByteString.copyFrom(audio))
                    .build();

            // Call Google Cloud STT API
            RecognizeResponse response = speechClient.recognize(config, recognitionAudio);

            // Extract transcript from response
            StringBuilder transcript = new StringBuilder();
            for (SpeechRecognitionResult result : response.getResultsList()) {
                if (!result.getAlternativesList().isEmpty()) {
                    String text = result.getAlternatives(0).getTranscript();
                    transcript.append(text).append(" ");
                }
            }

            long elapsed = System.currentTimeMillis() - startTime;
            String result = transcript.toString().trim();

            LOG.infof("Google Cloud STT success: transcript='%s' (took %dms)", result, elapsed);
            return result;

        } catch (Exception e) {
            LOG.errorf(e, "Google Cloud STT transcription failed: %s", e.getMessage());
            return "";
        }
    }

    /**
     * Determine Google Cloud Speech audio encoding from format string.
     */
    private RecognitionConfig.AudioEncoding getAudioEncoding(String format) {
        if (format == null) {
            return RecognitionConfig.AudioEncoding.LINEAR16;
        }

        return switch (format.toLowerCase()) {
            case "wav", "pcm16" -> RecognitionConfig.AudioEncoding.LINEAR16;
            case "mp3" -> RecognitionConfig.AudioEncoding.MP3;
            case "opus", "webm" -> RecognitionConfig.AudioEncoding.WEBM_OPUS;
            default -> {
                LOG.warnf("Unknown audio format '%s', defaulting to LINEAR16", format);
                yield RecognitionConfig.AudioEncoding.LINEAR16;
            }
        };
    }
}
