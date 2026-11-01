package com.makeasaying.editor.core.raster;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GeometryRasterizerTest {
    @Test
    void testBresenhamLineRasterization() {
        List<GeometryRasterizer.Point> line = GeometryRasterizer.rasterizeLine(0, 0, 3, 3);
        assertEquals(4, line.size());
        assertEquals(new GeometryRasterizer.Point(0, 0), line.get(0));
        assertEquals(new GeometryRasterizer.Point(1, 1), line.get(1));
        assertEquals(new GeometryRasterizer.Point(2, 2), line.get(2));
        assertEquals(new GeometryRasterizer.Point(3, 3), line.get(3));
    }

    @Test
    void testRectangleOutlineAndFilled() {
        List<GeometryRasterizer.Point> outline = GeometryRasterizer.rasterizeRectOutline(2, 2, 4, 4);
        // 3x3 perimeter has 8 cells
        assertEquals(8, outline.size());

        List<GeometryRasterizer.Point> filled = GeometryRasterizer.rasterizeRectFilled(2, 2, 4, 4);
        // 3x3 filled has 9 cells
        assertEquals(9, filled.size());
    }

    @Test
    void testFloodFillCorrectnessAndCap() {
        boolean[][] grid = new boolean[10][10];
        // Enclose a 3x3 area
        for (int x = 1; x <= 5; x++) {
            grid[1][x] = true; // wall
            grid[5][x] = true;
            grid[x][1] = true;
            grid[x][5] = true;
        }

        // Fill empty inside from (2, 2)
        List<GeometryRasterizer.Point> fill = GeometryRasterizer.floodFill(
            2, 2, 10, 10,
            (x, y) -> !grid[y][x],
            100
        );
        assertEquals(9, fill.size(), "Interior of 5x5 box is 3x3 = 9 cells");

        // Test exceeding cap throws IllegalStateException
        assertThrows(IllegalStateException.class, () ->
            GeometryRasterizer.floodFill(2, 2, 10, 10, (x, y) -> !grid[y][x], 5)
        );
    }
}
