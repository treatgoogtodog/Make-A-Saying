package com.makeasaying.engine.world;

/**
 * Data zone representing an exit from a room per M5b brief.
 * Coordinates are in virtual pixels, Y-up, bottom-left origin.
 */
public record RoomExit(
    String id,
    int x,
    int y,
    int width,
    int height,
    String targetRoom,
    String targetSpawn
) {
    public RoomExit {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("RoomExit id must not be blank");
        if (targetRoom == null || targetRoom.isBlank()) throw new IllegalArgumentException("targetRoom must not be blank");
        if (targetSpawn == null || targetSpawn.isBlank()) throw new IllegalArgumentException("targetSpawn must not be blank");
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("RoomExit dimensions must be positive");
    }

    public boolean overlaps(int px, int py, int pw, int ph) {
        return px < x + width &&
               px + pw > x &&
               py < y + height &&
               py + ph > y;
    }
}
