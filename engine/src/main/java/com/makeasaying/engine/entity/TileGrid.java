package com.makeasaying.engine.entity;

/**
 * Tile collision grid per AGENTS.md section 7.5 and DEC-007 / DEC-008 / DEC-011.
 * Arbitrary dimensions, Y-up coordinates, byte array storage.
 * Supports all 5 tile shapes: EMPTY, SOLID, ONE_WAY, SLOPE_45_UP_RIGHT, SLOPE_45_UP_LEFT.
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

    public TileShape getShape(int tileX, int tileY) {
        return TileShape.fromId(getTileId(tileX, tileY));
    }

    /**
     * Checks if a tile is a full solid block.
     * In M4, only TileShape.SOLID returns true; slopes and one-way platforms
     * provide floor only and do not block horizontal movement or act as ceilings.
     */
    public boolean isSolid(int tileX, int tileY) {
        return getTileId(tileX, tileY) == TileShape.SOLID.id;
    }

    public boolean isOneWay(int tileX, int tileY) {
        return getTileId(tileX, tileY) == TileShape.ONE_WAY.id;
    }

    public boolean isSlope(int tileX, int tileY) {
        byte shape = getTileId(tileX, tileY);
        return shape == TileShape.SLOPE_45_UP_RIGHT.id || shape == TileShape.SLOPE_45_UP_LEFT.id;
    }

    /**
     * Pure function returning floor height in pixels above the tile bottom for column c in 0..15.
     * Per Architect specification (DEC-011):
     * - SLOPE_45_UP_RIGHT: c + 1
     * - SLOPE_45_UP_LEFT: 16 - c
     * - SOLID: 16
     * - ONE_WAY: 16
     * - EMPTY: 0
     *
     * Invariant: Callers guarantee localX is in 0..15 by computing localX as Math.floorMod(colX, tileSize)
     * where tileSize is 16. In Java, floorMod(colX, 16) mathematically guarantees a range of [0, 15] for any
     * positive, zero, or negative integer, preventing any IllegalArgumentException in hot simulation loops.
     */
    public static int floorHeightAt(TileShape shape, int localX) {
        if (localX < 0 || localX >= 16) {
            throw new IllegalArgumentException("localX must be in 0..15, was " + localX);
        }
        return switch (shape) {
            case SLOPE_45_UP_RIGHT -> localX + 1;
            case SLOPE_45_UP_LEFT -> 16 - localX;
            case SOLID, ONE_WAY -> 16;
            case EMPTY -> 0;
        };
    }

    /**
     * Finds the highest floor surface Y at the specified column within the given vertical search window.
     * Returns Integer.MIN_VALUE if no floor exists in the window.
     */
    public int getFloorSurfaceAtCol(int colX, int feetY, int searchDownPx, int searchUpPx, boolean ignoreOneWay) {
        int tx = Math.floorDiv(colX, tileSize);
        int lx = Math.floorMod(colX, tileSize);
        int topRow = Math.floorDiv(feetY + searchUpPx, tileSize);
        int bottomRow = Math.floorDiv(feetY - searchDownPx, tileSize);

        for (int ty = topRow; ty >= bottomRow; ty--) {
            TileShape shape = getShape(tx, ty);
            int surfaceY = Integer.MIN_VALUE;

            if (shape == TileShape.SOLID) {
                surfaceY = (ty + 1) * tileSize;
            } else if (shape == TileShape.ONE_WAY) {
                if (!ignoreOneWay) {
                    surfaceY = (ty + 1) * tileSize;
                }
            } else if (shape == TileShape.SLOPE_45_UP_RIGHT || shape == TileShape.SLOPE_45_UP_LEFT) {
                surfaceY = ty * tileSize + floorHeightAt(shape, lx);
            }

            if (surfaceY != Integer.MIN_VALUE && surfaceY <= feetY + searchUpPx && surfaceY >= feetY - searchDownPx) {
                return surfaceY;
            }
        }
        return Integer.MIN_VALUE;
    }

    public void clear() {
        for (int i = 0; i < tiles.length; i++) {
            tiles[i] = TileShape.EMPTY.id;
        }
    }
}
