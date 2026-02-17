package dev.aimon.dto.powermem;

import java.util.List;

/**
 * Batch request for creating multiple observations.
 */
public record ObservationBatch(List<Observation> observations) {
    public static ObservationBatch of(List<Observation> observations) {
        return new ObservationBatch(observations);
    }
}
