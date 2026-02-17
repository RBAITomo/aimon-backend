package dev.aimon.service.audio;

import dev.aimon.service.audio.OpusAudioProcessor;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * OPUS decoder service with pooling for performance.
 *
 * Simplified version focusing on decoder operations only.
 * Features:
 * - Decoder pooling for reuse (avoid recreation overhead)
 * - Thread-safe operations
 * - Graceful cleanup on shutdown
 */
@ApplicationScoped
public class OpusCodecService {
    private static final Logger LOG = Logger.getLogger(OpusCodecService.class);

    private static final int MAX_POOL_SIZE = 10;

    private final ConcurrentLinkedQueue<PooledDecoder> decoderPool = new ConcurrentLinkedQueue<>();
    private final AtomicInteger poolSize = new AtomicInteger(0);

    @Inject
    OpusAudioProcessor opusAudioProcessor;

    /**
     * Wrapper class to track decoder configuration for pool reuse.
     */
    private static class PooledDecoder {
        final OpusAudioProcessor.StreamingDecoder decoder;
        final int sampleRate;
        final int channels;

        PooledDecoder(OpusAudioProcessor.StreamingDecoder decoder, int sampleRate, int channels) {
            this.decoder = decoder;
            this.sampleRate = sampleRate;
            this.channels = channels;
        }
    }

    /**
     * Decode OPUS to PCM16 (little-endian) bytes.
     *
     * @param opus OPUS-encoded audio bytes
     * @param sampleRate Sample rate in Hz (e.g., 16000)
     * @return PCM16 audio bytes or empty array on error
     */
    public byte[] decodeOpusToPcm(byte[] opus, int sampleRate) {
        if (opus == null || opus.length == 0) {
            return new byte[0];
        }

        try {
            if (!opusAudioProcessor.isOpusAvailable()) {
                LOG.debug("OPUS library not available, skipping decode");
                return new byte[0];
            }
            return opusAudioProcessor.opusToPcm16(opus, sampleRate);
        } catch (Exception e) {
            LOG.warnf(e, "OPUS decode failed: %s", e.getMessage());
            return new byte[0];
        }
    }

    /**
     * Decode OPUS to PCM16 with default 16kHz sample rate.
     */
    public byte[] decodeOpusToPcm(byte[] opus) {
        return decodeOpusToPcm(opus, 16000);
    }

    /**
     * Check if OPUS library is available.
     */
    public boolean isOpusAvailable() {
        return opusAudioProcessor.isOpusAvailable();
    }

    /**
     * Create a new streaming decoder (not pooled).
     * For session-bound decoders that maintain state across frames.
     */
    public OpusAudioProcessor.StreamingDecoder createStreamingDecoder(int sampleRate, int channels) throws IOException {
        if (!opusAudioProcessor.isOpusAvailable()) {
            throw new IOException("OPUS library not available. Please install libopus.");
        }
        return opusAudioProcessor.createStreamingDecoder(sampleRate, channels);
    }

    /**
     * Acquire a streaming decoder from pool or create new one.
     *
     * @param sampleRate Sample rate (e.g., 16000, 48000)
     * @param channels Number of channels (1 for mono, 2 for stereo)
     * @return Streaming decoder (caller must call releaseDecoder when done)
     * @throws IOException If decoder creation fails
     */
    public OpusAudioProcessor.StreamingDecoder acquireDecoder(int sampleRate, int channels) throws IOException {
        if (!opusAudioProcessor.isOpusAvailable()) {
            throw new IOException("OPUS library not available. Please install libopus.");
        }

        // Try to get matching decoder from pool
        PooledDecoder pooled = decoderPool.poll();
        while (pooled != null) {
            if (pooled.sampleRate == sampleRate && pooled.channels == channels) {
                poolSize.decrementAndGet();
                LOG.debugf("Acquired decoder from pool (rate=%d, ch=%d), pool size: %d",
                        sampleRate, channels, poolSize.get());
                return pooled.decoder;
            }
            // Wrong config, close and try next
            opusAudioProcessor.closeStreamingDecoder(pooled.decoder);
            poolSize.decrementAndGet();
            pooled = decoderPool.poll();
        }

        // Create new decoder
        LOG.debugf("Creating new decoder (rate=%d, ch=%d)", sampleRate, channels);
        return opusAudioProcessor.createStreamingDecoder(sampleRate, channels);
    }

    /**
     * Release decoder back to pool for reuse.
     *
     * @param decoder Decoder to release
     * @param sampleRate Sample rate of the decoder
     * @param channels Number of channels
     */
    public void releaseDecoder(OpusAudioProcessor.StreamingDecoder decoder, int sampleRate, int channels) {
        if (decoder == null) {
            return;
        }

        if (poolSize.get() < MAX_POOL_SIZE) {
            decoderPool.offer(new PooledDecoder(decoder, sampleRate, channels));
            poolSize.incrementAndGet();
            LOG.debugf("Released decoder to pool (rate=%d, ch=%d), pool size: %d",
                    sampleRate, channels, poolSize.get());
        } else {
            // Pool full, close decoder
            opusAudioProcessor.closeStreamingDecoder(decoder);
            LOG.debugf("Pool full, closed decoder (rate=%d, ch=%d)", sampleRate, channels);
        }
    }

    /**
     * Cleanup all pooled decoders on shutdown.
     */
    @PreDestroy
    void cleanup() {
        LOG.info("Cleaning up OpusCodecService decoder pool");
        PooledDecoder pooled;
        int closed = 0;
        while ((pooled = decoderPool.poll()) != null) {
            opusAudioProcessor.closeStreamingDecoder(pooled.decoder);
            closed++;
        }
        poolSize.set(0);
        LOG.infof("Closed %d pooled decoders", closed);
    }
}
