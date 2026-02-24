package dev.aimon.service.audio;

import dev.aimon.service.stt.SttOrchestrator;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.io.ByteArrayOutputStream;
import java.util.Deque;
import java.util.List;

/**
 * Audio processing pipeline for v4 push-to-talk protocol.
 * Handles: OPUS decode → validation → downsampling → STT
 */
@ApplicationScoped
public class AudioPipelineService {

    private static final Logger LOG = Logger.getLogger(AudioPipelineService.class);

    // Audio validation thresholds
    private static final double MIN_DURATION_SECONDS = 0.5;
    private static final double MIN_RMS_THRESHOLD = 500.0;

    @Inject
    OpusCodecService opusCodecService;

    @Inject
    SttOrchestrator sttOrchestrator;

    /**
     * Process collected audio frames into transcript.
     * Supports both PCM16 (direct) and OPUS (decode first) formats.
     *
     * @param audioFrames Collected audio frames during LISTENING state
     * @param sampleRate Audio sample rate (Hz)
     * @param audioFormat Audio format: "pcm16" or "opus"
     * @param vocabularyHints World-specific terms to boost in Google STT (may be empty)
     * @return Transcript text, or null if validation fails
     */
    public String processAudio(Deque<byte[]> audioFrames, int sampleRate, String audioFormat, List<String> vocabularyHints) {
        if (audioFrames == null || audioFrames.isEmpty()) {
            LOG.warn("No audio frames to process");
            return null;
        }

        // Step 1: Get PCM16 audio (decode OPUS if needed, or concatenate PCM16 directly)
        byte[] pcmAudio;
        if ("pcm16".equals(audioFormat)) {
            pcmAudio = concatenateFrames(audioFrames);
            LOG.infof("Concatenated %d PCM16 frames → %d bytes", audioFrames.size(), pcmAudio.length);
        } else {
            pcmAudio = decodeOpusFrames(audioFrames, sampleRate);
            if (pcmAudio.length == 0) {
                LOG.warn("Failed to decode OPUS frames");
                return null;
            }
        }

        // Step 2: Validate audio quality
        if (!isValidAudio(pcmAudio, sampleRate)) {
            LOG.info("Audio validation failed (too short or too quiet)");
            return null;
        }

        // Step 3: Downsample to 16kHz for STT if needed
        byte[] sttAudio = pcmAudio;
        int sttSampleRate = sampleRate;
        if (sampleRate > 16000) {
            sttAudio = downsamplePcm16(pcmAudio, sampleRate, 16000);
            sttSampleRate = 16000;
            LOG.debugf("Downsampled audio %dHz→16kHz (%d→%d bytes)",
                    sampleRate, pcmAudio.length, sttAudio.length);
        }

        // Step 4: Call STT
        String transcript = sttOrchestrator.transcribe(sttAudio, sttSampleRate, "vi-VN", "pcm16", vocabularyHints);
        LOG.infof("STT result: %s", transcript);
        return transcript;
    }

    /**
     * Concatenate raw PCM16 frames into a single byte array.
     */
    private byte[] concatenateFrames(Deque<byte[]> frames) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(frames.size() * 640);
        for (byte[] frame : frames) {
            if (frame != null && frame.length > 0) {
                out.write(frame, 0, frame.length);
            }
        }
        return out.toByteArray();
    }

    /**
     * Decode all OPUS frames to PCM16 audio.
     */
    private byte[] decodeOpusFrames(Deque<byte[]> opusFrames, int sampleRate) {
        ByteArrayOutputStream pcmOut = new ByteArrayOutputStream(opusFrames.size() * 512);

        for (byte[] frame : opusFrames) {
            if (frame == null || frame.length == 0) {
                continue;
            }

            byte[] decoded = opusCodecService.decodeOpusToPcm(frame, sampleRate);
            if (decoded.length > 0) {
                try {
                    pcmOut.write(decoded);
                } catch (Exception e) {
                    LOG.warnf(e, "Failed to write decoded PCM");
                }
            }
        }

        return pcmOut.toByteArray();
    }

    /**
     * Validate audio has meaningful content.
     * Checks duration (> 0.5s) and RMS energy (> 500).
     */
    private boolean isValidAudio(byte[] pcmAudio, int sampleRate) {
        int samples = pcmAudio.length / 2; // PCM16 = 2 bytes per sample
        double durationSeconds = samples / (double) sampleRate;

        // Calculate RMS (Root Mean Square) to detect audio energy
        long sumSq = 0L;
        for (int i = 0; i < samples; i++) {
            int lo = pcmAudio[i * 2] & 0xFF;
            int hi = pcmAudio[i * 2 + 1];
            short sample = (short) ((hi << 8) | lo);
            sumSq += (long) sample * (long) sample;
        }
        double rms = samples > 0 ? Math.sqrt(sumSq / (double) samples) : 0.0;

        LOG.debugf("Audio validation: duration=%.2fs, rms=%.1f", durationSeconds, rms);

        // Skip STT if too short or too quiet
        if (durationSeconds < MIN_DURATION_SECONDS) {
            LOG.infof("Audio too short: %.2fs (min %.2fs)", durationSeconds, MIN_DURATION_SECONDS);
            return false;
        }

        if (rms < MIN_RMS_THRESHOLD) {
            LOG.infof("Audio too quiet: rms=%.1f (min %.1f)", rms, MIN_RMS_THRESHOLD);
            return false;
        }

        return true;
    }

    /**
     * Downsample PCM16 audio using simple decimation.
     * Used to reduce bandwidth for STT (48kHz → 16kHz).
     *
     * @param input PCM16 little-endian bytes
     * @param sourceRate Source sample rate (e.g., 48000)
     * @param targetRate Target sample rate (e.g., 16000)
     * @return Downsampled PCM16 bytes
     */
    private byte[] downsamplePcm16(byte[] input, int sourceRate, int targetRate) {
        if (input == null || input.length < 2 || sourceRate <= targetRate) {
            return input != null ? input : new byte[0];
        }

        int ratio = sourceRate / targetRate;
        int inputSamples = input.length / 2;
        int outputSamples = inputSamples / ratio;
        byte[] output = new byte[outputSamples * 2];

        for (int i = 0; i < outputSamples; i++) {
            int srcIdx = i * ratio * 2;
            output[i * 2] = input[srcIdx];
            output[i * 2 + 1] = input[srcIdx + 1];
        }

        return output;
    }
}
