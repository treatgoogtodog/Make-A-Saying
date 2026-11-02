package com.makeasaying.engine.world;

import com.makeasaying.engine.condition.CompiledCondition;

/**
 * Data zone representing an exit from a room per M5b and M7a briefs.
 * Coordinates are in virtual pixels, Y-up, bottom-left origin.
 * Supports optional condition for gating transitions.
 */
public record RoomExit(
    String id,
    int x,
    int y,
    int width,
    int height,
    String targetRoom,
    String targetSpawn,
    CompiledCondition condition,
    String conditionJson
) {
    public RoomExit(
        String id,
        int x,
        int y,
        int width,
        int height,
        String targetRoom,
        String targetSpawn
    ) {
        this(id, x, y, width, height, targetRoom, targetSpawn, CompiledCondition.ALWAYS_TRUE, null);
    }

    public RoomExit {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("RoomExit id must not be blank");
        if (targetRoom == null || targetRoom.isBlank()) throw new IllegalArgumentException("targetRoom must not be blank");
        if (targetSpawn == null || targetSpawn.isBlank()) throw new IllegalArgumentException("targetSpawn must not be blank");
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("RoomExit dimensions must be positive");
        if (condition == null) {
            condition = CompiledCondition.ALWAYS_TRUE;
        }
    }

    public boolean overlaps(int px, int py, int pw, int ph) {
        return px < x + width &&
               px + pw > x &&
               py < y + height &&
               py + ph > y;
    }
}

