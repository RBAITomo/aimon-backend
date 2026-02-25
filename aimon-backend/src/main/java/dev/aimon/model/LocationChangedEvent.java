package dev.aimon.model;

/**
 * CDI event fired when user travels to a sub-location.
 * PetEventBridge observes this to send WebSocket notification with background filename.
 */
public record LocationChangedEvent(
    Long userId,
    String subLocationCode,
    String backgroundFile
) {}
