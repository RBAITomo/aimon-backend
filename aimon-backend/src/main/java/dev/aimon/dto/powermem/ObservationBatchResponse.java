package dev.aimon.dto.powermem;

import java.util.List;

/**
 * Response from batch observation creation.
 */
public record ObservationBatchResponse(int created, List<String> ids) {}
