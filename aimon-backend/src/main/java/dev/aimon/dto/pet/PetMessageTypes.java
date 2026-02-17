package dev.aimon.dto.pet;

/**
 * WebSocket message type constants for pet system.
 * Defines all client-server communication types for pet interactions.
 */
public final class PetMessageTypes {

    private PetMessageTypes() {} // Prevent instantiation

    // Server → Client: Pet status updates
    public static final String PET_STATUS = "pet_status";

    // Client → Server: Feed confirmation
    public static final String PET_FEED_CONFIRM = "pet_feed_confirm";

    // Server → Client: Feed result
    public static final String PET_FEED_RESULT = "pet_feed_result";

    // Server → Client: Badge earned notification
    public static final String BADGE_EARNED = "badge_earned";

    // Server → Client: Pet evolution event
    public static final String PET_EVOLUTION = "pet_evolution";

    // Client → Server: Transform to variant request
    public static final String PET_TRANSFORM = "pet_transform";

    // Server → Client: Variant transformation end
    public static final String PET_TRANSFORM_END = "pet_transform_end";

    // Server → Client: Low stat warning
    public static final String PET_WARNING = "pet_warning";

    // Server → Client: Pet regression event
    public static final String PET_REGRESSION = "pet_regression";

    // Client → Server: Quest generation request
    public static final String QUEST_REQUEST = "quest_request";

    // Server → Client: Quest start notification
    public static final String QUEST_START = "quest_start";
}
