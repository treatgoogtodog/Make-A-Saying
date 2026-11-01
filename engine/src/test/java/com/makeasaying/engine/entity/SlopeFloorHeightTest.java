package com.makeasaying.engine.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SlopeFloorHeightTest {

    @Test
    void testSlopeUpRightAllColumns() {
        for (int c = 0; c < 16; c++) {
            int expected = c + 1;
            assertEquals(expected, TileGrid.floorHeightAt(TileShape.SLOPE_45_UP_RIGHT, c),
                "Column " + c + " of SLOPE_45_UP_RIGHT must have height " + expected);
        }
        assertEquals(1, TileGrid.floorHeightAt(TileShape.SLOPE_45_UP_RIGHT, 0));
        assertEquals(16, TileGrid.floorHeightAt(TileShape.SLOPE_45_UP_RIGHT, 15));
    }

    @Test
    void testSlopeUpLeftAllColumns() {
        for (int c = 0; c < 16; c++) {
            int expected = 16 - c;
            assertEquals(expected, TileGrid.floorHeightAt(TileShape.SLOPE_45_UP_LEFT, c),
                "Column " + c + " of SLOPE_45_UP_LEFT must have height " + expected);
        }
        assertEquals(16, TileGrid.floorHeightAt(TileShape.SLOPE_45_UP_LEFT, 0));
        assertEquals(1, TileGrid.floorHeightAt(TileShape.SLOPE_45_UP_LEFT, 15));
    }

    @Test
    void testSolidOneWayAndEmptyShapes() {
        for (int c = 0; c < 16; c++) {
            assertEquals(16, TileGrid.floorHeightAt(TileShape.SOLID, c));
            assertEquals(16, TileGrid.floorHeightAt(TileShape.ONE_WAY, c));
            assertEquals(0, TileGrid.floorHeightAt(TileShape.EMPTY, c));
        }
    }

    @Test
    void testLocalXBoundsCheck() {
        assertThrows(IllegalArgumentException.class, () ->
            TileGrid.floorHeightAt(TileShape.SLOPE_45_UP_RIGHT, -1)
        );
        assertThrows(IllegalArgumentException.class, () ->
            TileGrid.floorHeightAt(TileShape.SLOPE_45_UP_LEFT, 16)
        );
    }
}
