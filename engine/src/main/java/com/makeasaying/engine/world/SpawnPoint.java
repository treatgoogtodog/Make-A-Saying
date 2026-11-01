package com.makeasaying.engine.world;

/**
 * Spawn point in a room per M5a brief.
 * Coordinates (x, y) are in pixels representing the feet position.
 */
public record SpawnPoint(String id, int x, int y) {
    public SpawnPoint {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("SpawnPoint id must not be blank");
        }
    }
}
