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

    // --- Combat system (Phase 2b) ---

    // Server → Client: Tasteless detected, warning before combat
    public static final String TASTELESS_WARNING = "tasteless_warning";

    // Server → Client: Combat has begun
    public static final String COMBAT_START = "combat_start";

    // Server → Client: Single combat round result
    public static final String COMBAT_ROUND = "combat_round";

    // Server → Client: Final combat outcome
    public static final String COMBAT_RESULT = "combat_result";

    // Client → Server: Special/Evolution move during combat
    public static final String COMBAT_SPECIAL = "combat_special";

    // --- Shard + Location system (Phase 3) ---

    // Server → Client: New shard unlocked
    public static final String SHARD_UNLOCKED = "shard_unlocked";

    // Server → Client: Location now available
    public static final String LOCATION_UNLOCK = "location_unlock";

    // Server → Client: Current location changed
    public static final String LOCATION_CHANGED = "location_changed";

    // Client → Server: Request to switch location
    public static final String LOCATION_SWITCH = "location_switch";

    // --- Noir Quest + Final Arc (Phase 4) ---

    // Server → Client: Final Arc unlock (all 5 milestones)
    public static final String FINAL_ARC_UNLOCK = "final_arc_unlock";

    // Server → Client: Noir quest question presented
    public static final String NOIR_QUEST_START = "noir_quest_start";

    // Server → Client: Noir quest evaluation result
    public static final String NOIR_QUEST_RESULT = "noir_quest_result";

    // --- Mini-game (Food Catcher) ---

    // Client → Server: Start mini-game request
    public static final String MINI_GAME_START = "mini_game_start";

    // Client → Server: Submit mini-game score
    public static final String MINI_GAME_RESULT = "mini_game_result";

    // Server → Client: Mini-game ready (or rejected)
    public static final String MINI_GAME_READY = "mini_game_ready";

    // Server → Client: Mini-game reward (cotton candy count)
    public static final String MINI_GAME_REWARD = "mini_game_reward";
}
