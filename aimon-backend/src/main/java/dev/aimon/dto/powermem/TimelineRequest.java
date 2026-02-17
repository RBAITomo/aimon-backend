package dev.aimon.dto.powermem;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request for timeline retrieval with pagination.
 */
public record TimelineRequest(
    @JsonProperty("robot_id") Integer robotId,
    @JsonProperty("user_id") Integer userId,
    Integer limit,
    Integer offset,
    String category
) {
    /**
     * Timeline for a specific robot.
     */
    public TimelineRequest(Integer robotId) {
        this(robotId, null, 20, 0, null);
    }

    /**
     * Timeline with limit.
     */
    public TimelineRequest(Integer robotId, int limit) {
        this(robotId, null, limit, 0, null);
    }
}
