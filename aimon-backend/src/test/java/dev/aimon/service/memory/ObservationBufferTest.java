package dev.aimon.service.memory;

import dev.aimon.client.PowerMemClient;
import dev.aimon.dto.powermem.Observation;
import dev.aimon.dto.powermem.ObservationBatch;
import dev.aimon.dto.powermem.ObservationBatchResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ObservationBuffer.
 * Tests buffering, flushing, and error re-queue behavior.
 */
@ExtendWith(MockitoExtension.class)
class ObservationBufferTest {

    @Mock
    PowerMemClient client;

    @InjectMocks
    ObservationBuffer observationBuffer;

    @BeforeEach
    void setUp() throws Exception {
        setField(observationBuffer, "batchThreshold", 3);
        setField(observationBuffer, "enabled", true);
    }

    @Test
    void add_disabled_doesNotBuffer() throws Exception {
        setField(observationBuffer, "enabled", false);

        observationBuffer.add(Observation.create("content", Map.of()));

        assertEquals(0, observationBuffer.size());
    }

    @Test
    void add_enabled_incrementsSize() {
        observationBuffer.add(Observation.create("content", Map.of()));

        assertEquals(1, observationBuffer.size());
    }

    @Test
    void add_atThreshold_triggersFlush() {
        ObservationBatchResponse response = new ObservationBatchResponse(3, List.of("1", "2", "3"));
        when(client.createObservationsBatch(any(ObservationBatch.class))).thenReturn(response);

        observationBuffer.add(Observation.create("content1", Map.of()));
        observationBuffer.add(Observation.create("content2", Map.of()));
        observationBuffer.add(Observation.create("content3", Map.of())); // threshold = 3

        verify(client).createObservationsBatch(any(ObservationBatch.class));
        assertEquals(0, observationBuffer.size());
    }

    @Test
    void flush_disabled_doesNothing() throws Exception {
        setField(observationBuffer, "enabled", false);

        observationBuffer.flush();

        verifyNoInteractions(client);
    }

    @Test
    void flush_emptyBuffer_doesNotCallClient() {
        observationBuffer.flush();

        verifyNoInteractions(client);
    }

    @Test
    void flush_withItems_sendsToClient() {
        ObservationBatchResponse response = new ObservationBatchResponse(1, List.of("id1"));
        when(client.createObservationsBatch(any(ObservationBatch.class))).thenReturn(response);

        observationBuffer.add(Observation.create("content", Map.of()));
        observationBuffer.flush();

        verify(client).createObservationsBatch(any(ObservationBatch.class));
        assertEquals(0, observationBuffer.size());
    }

    @Test
    void flush_clientException_requeuesItems() {
        when(client.createObservationsBatch(any(ObservationBatch.class)))
            .thenThrow(new RuntimeException("Connection failed"));

        observationBuffer.add(Observation.create("content", Map.of()));
        observationBuffer.flush();

        // Items should be re-queued
        assertTrue(observationBuffer.size() > 0);
    }

    @Test
    void clear_resetsBuffer() {
        observationBuffer.add(Observation.create("content", Map.of()));
        assertEquals(1, observationBuffer.size());

        observationBuffer.clear();

        assertEquals(0, observationBuffer.size());
    }

    @Test
    void scheduledFlush_emptyBuffer_doesNothing() throws Exception {
        observationBuffer.scheduledFlush();

        verifyNoInteractions(client);
    }

    @Test
    void scheduledFlush_disabled_doesNothing() throws Exception {
        setField(observationBuffer, "enabled", false);
        // Manually add to buffer queue bypassing the enabled check
        // Since disabled, scheduledFlush should still not call client
        observationBuffer.scheduledFlush();

        verifyNoInteractions(client);
    }

    @Test
    void scheduledFlush_withItems_flushes() {
        ObservationBatchResponse response = new ObservationBatchResponse(1, List.of("id1"));
        when(client.createObservationsBatch(any(ObservationBatch.class))).thenReturn(response);

        observationBuffer.add(Observation.create("content", Map.of()));
        observationBuffer.scheduledFlush();

        verify(client).createObservationsBatch(any(ObservationBatch.class));
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
