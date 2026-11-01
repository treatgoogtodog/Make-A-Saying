package com.makeasaying.engine.entity;

import com.makeasaying.engine.util.Log;
import com.makeasaying.engine.util.LogCategory;

/**
 * Tile collision grid per AGENTS.md section 7.5 and DEC-007 / DEC-008.
 * Arbitrary dimensions, Y-up coordinates, byte array storage.
 */
public final class TileGrid {
    private final int width;
    private final int height;
    private final int tileSize;
    private final byte[] tiles;

    public TileGrid(int width, int height, int tileSize) {
        if (width <= 0 || height <= 0 || tileSize <= 0) {
            throw new IllegalArgumentException("Grid dimensions and tile size must be positive");
        }
        this.width = width;
        this.height = height;
        this.tileSize = tileSize;
        this.tiles = new byte[width * height];
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getTileSize() {
        return tileSize;
    }

    public void setTile(int tileX, int tileY, TileShape shape) {
        if (tileX >= 0 && tileX < width && tileY >= 0 && tileY < height) {
            tiles[tileY * width + tileX] = shape.id;
        }
    }

    public byte getTileId(int tileX, int tileY) {
        // Out of bounds counts as SOLID per DEC-008
        if (tileX < 0 || tileX >= width || tileY < 0 || tileY >= height) {
            return TileShape.SOLID.id;
        }
        return tiles[tileY * width + tileX];
    }

    /**
     * Checks if a tile is solid. In M3, only EMPTY and SOLID are supported;
     * any other shape throws a loud error.
     */
    public boolean isSolid(int tileX, int tileY) {
        byte shapeId = getTileId(tileX, tileY);
        if (shapeId == TileShape.SOLID.id) {
            return true;
        } else if (shapeId == TileShape.EMPTY.id) {
            return false;
        } else {
            Log.error(LogCategory.PHYSICS, "Unsupported tile shape %d encountered at (%d, %d) in M3",
                shapeId, tileX, tileY);
            throw new IllegalStateException("Unsupported tile shape in M3: " + shapeId + " at (" + tileX + "," + tileY + ")");
        }
    }

    public void clear() {
        for (int i = 0; i < tiles.length; i++) {
            tiles[i] = TileShape.EMPTY.id;
        }
    }
}
