package com.makeasaying.engine.world;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Raw entity instance data parsed and round-tripped from Room file per M5a brief.
 * Spawning into the World will be implemented when entity definitions exist.
 */
public record RoomEntityData(
    String instanceId,
    String definition,
    int x,
    int y,
    Map<String, Object> overrides
) {
    public RoomEntityData {
        if (instanceId == null || instanceId.isBlank()) {
            throw new IllegalArgumentException("RoomEntityData instanceId must not be blank");
        }
        if (definition == null || definition.isBlank()) {
            throw new IllegalArgumentException("RoomEntityData definition must not be blank");
        }
        overrides = (overrides != null) ? Collections.unmodifiableMap(new LinkedHashMap<>(overrides)) : Collections.emptyMap();
    }
}
