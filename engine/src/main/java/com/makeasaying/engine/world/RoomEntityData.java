package com.makeasaying.engine.world;

import com.makeasaying.engine.condition.CompiledCondition;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Raw entity instance data parsed and round-tripped from Room file v3 per M6b and M7a briefs.
 * Format: id, definition, x, y, nested component overrides, and optional spawnCondition.
 */
public record RoomEntityData(
    String instanceId,
    String definition,
    int x,
    int y,
    Map<String, Map<String, Object>> overrides,
    CompiledCondition spawnCondition,
    String spawnConditionJson
) {
    public RoomEntityData(
        String instanceId,
        String definition,
        int x,
        int y,
        Map<String, Map<String, Object>> overrides
    ) {
        this(instanceId, definition, x, y, overrides, CompiledCondition.ALWAYS_TRUE, null);
    }

    public RoomEntityData {
        if (instanceId == null || instanceId.isBlank()) {
            throw new IllegalArgumentException("RoomEntityData instanceId must not be blank");
        }
        if (definition == null || definition.isBlank()) {
            throw new IllegalArgumentException("RoomEntityData definition must not be blank");
        }
        if (spawnCondition == null) {
            spawnCondition = CompiledCondition.ALWAYS_TRUE;
        }
        if (overrides != null) {
            Map<String, Map<String, Object>> copy = new LinkedHashMap<>();
            for (Map.Entry<String, Map<String, Object>> e : overrides.entrySet()) {
                copy.put(e.getKey(), (e.getValue() != null) ? Collections.unmodifiableMap(new LinkedHashMap<>(e.getValue())) : Collections.emptyMap());
            }
            overrides = Collections.unmodifiableMap(copy);
        } else {
            overrides = Collections.emptyMap();
        }
    }
}

