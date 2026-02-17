package dev.aimon.service.memory;

import dev.aimon.client.PowerMemClient;
import dev.aimon.dto.powermem.Observation;
import dev.aimon.dto.powermem.ObservationBatch;
import dev.aimon.dto.powermem.ObservationBatchResponse;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thread-safe buffer for batching observations before sending to PowerMem.
 *
 * Features:
 * - Lock-free concurrent queue for high throughput
 * - Automatic flush at threshold (default 50)
 * - Scheduled flush every 10 seconds to prevent stale data
 * - Re-queue on failure for reliability
 */
@ApplicationScoped
public class ObservationBuffer {
    private static final Logger log = LoggerFactory.getLogger(ObservationBuffer.class);

    @ConfigProperty(name = "memory.mcp.batch-threshold", defaultValue = "50")
    int batchThreshold;

    @ConfigProperty(name = "memory.mcp.enabled", defaultValue = "true")
    boolean enabled;

    @Inject
    @RestClient
    PowerMemClient client;

    private final Queue<Observation> buffer = new ConcurrentLinkedQueue<>();
    private final AtomicInteger size = new AtomicInteger(0);
    private final AtomicBoolean flushing = new AtomicBoolean(false);

    /**
     * Add observation to buffer.
     * Triggers flush if threshold reached.
     *
     * @param observation The observation to buffer
     */
    public void add(Observation observation) {
        if (!enabled) {
            return;
        }

        buffer.add(observation);
        int currentSize = size.incrementAndGet();

        if (currentSize >= batchThreshold) {
            flush();
        }
    }

    /**
     * Get current buffer size.
     */
    public int size() {
        return size.get();
    }

    /**
     * Clear all observations from buffer.
     * Used primarily for testing to reset state between tests.
     */
    public void clear() {
        buffer.clear();
        size.set(0);
        flushing.set(false);
    }

    /**
     * Force flush the buffer.
     * Thread-safe: only one flush can run at a time.
     */
    public void flush() {
        if (!enabled) {
            return;
        }

        // Prevent concurrent flushes
        if (!flushing.compareAndSet(false, true)) {
            log.trace("Flush already in progress, skipping");
            return;
        }

        try {
            doFlush();
        } finally {
            flushing.set(false);
        }
    }

    /**
     * Scheduled flush every 10 seconds.
     * Ensures observations don't stay buffered too long.
     */
    @Scheduled(every = "10s")
    void scheduledFlush() {
        if (!enabled || buffer.isEmpty()) {
            return;
        }
        log.trace("Scheduled flush triggered, buffer size: {}", size.get());
        flush();
    }

    private void doFlush() {
        // Drain buffer into batch
        List<Observation> batch = new ArrayList<>();
        Observation obs;
        while ((obs = buffer.poll()) != null) {
            batch.add(obs);
        }
        size.set(0);

        if (batch.isEmpty()) {
            return;
        }

        log.info("Flushing {} observations to PowerMem", batch.size());

        try {
            ObservationBatchResponse response = client.createObservationsBatch(
                ObservationBatch.of(batch)
            );
            log.info("Batch flush success: {} created", response.created());
        } catch (Exception e) {
            log.error("Batch flush failed, re-queuing {} observations: {}",
                batch.size(), e.getMessage());
            // Re-queue failed observations
            buffer.addAll(batch);
            size.addAndGet(batch.size());
        }
    }
}
