package com.makeasaying.engine.world;

/**
 * Position (col, row) of a tile within a tile sheet PNG.
 * Exists in Tileset metadata; rooms never store col/row.
 */
public record TileSheetCoord(int col, int row) {
    public TileSheetCoord {
        if (col < 0 || row < 0) {
            throw new IllegalArgumentException("Sheet col and row must be non-negative");
        }
    }
}
