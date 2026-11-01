package com.makeasaying.engine.world;

/**
 * Configuration for room transitions per M5b brief.
 * Fixed tick durations: fadeOutTicks 12, holdTicks 4, fadeInTicks 12.
 */
public record RoomTransitionConfig(
    int fadeOutTicks,
    int holdTicks,
    int fadeInTicks
) {
    public static final RoomTransitionConfig DEFAULT = new RoomTransitionConfig(12, 4, 12);

    public RoomTransitionConfig {
        if (fadeOutTicks < 0 || holdTicks < 0 || fadeInTicks < 0) {
            throw new IllegalArgumentException("Transition tick durations must be non-negative");
        }
    }
}
