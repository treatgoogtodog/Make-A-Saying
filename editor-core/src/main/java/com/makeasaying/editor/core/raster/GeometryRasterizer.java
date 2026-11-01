package com.makeasaying.editor.core.raster;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiPredicate;

/**
 * Pure 2D grid rasterization utilities per M6a brief.
 * Generates discrete cell coordinates for lines, rectangles, and flood fills.
 * Zero GL or UI dependencies.
 */
public final class GeometryRasterizer {
    public static final int DEFAULT_FLOOD_FILL_CAP = 4096;

    public record Point(int x, int y) {}

    private GeometryRasterizer() {}

    /**
     * Bresenham's line algorithm in grid cell coordinates.
     */
    public static List<Point> rasterizeLine(int x0, int y0, int x1, int y1) {
        List<Point> points = new ArrayList<>();
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;

        int currX = x0;
        int currY = y0;

        while (true) {
            points.add(new Point(currX, currY));
            if (currX == x1 && currY == y1) break;
            int e2 = 2 * err;
            if (e2 > -dy) {
                err -= dy;
                currX += sx;
            }
            if (e2 < dx) {
                err += dx;
                currY += sy;
            }
        }
        return Collections.unmodifiableList(points);
    }

    /**
     * Rasterizes the 1-cell outline of a rectangle between corner (x0, y0) and (x1, y1).
     */
    public static List<Point> rasterizeRectOutline(int x0, int y0, int x1, int y1) {
        int minX = Math.min(x0, x1);
        int maxX = Math.max(x0, x1);
        int minY = Math.min(y0, y1);
        int maxY = Math.max(y0, y1);

        List<Point> points = new ArrayList<>();
        // Top and bottom edges
        for (int x = minX; x <= maxX; x++) {
            points.add(new Point(x, minY));
            if (minY != maxY) {
                points.add(new Point(x, maxY));
            }
        }
        // Left and right edges (excluding corners already added)
        for (int y = minY + 1; y < maxY; y++) {
            points.add(new Point(minX, y));
            if (minX != maxX) {
                points.add(new Point(maxX, y));
            }
        }
        return Collections.unmodifiableList(points);
    }

    /**
     * Rasterizes a filled solid rectangle between corner (x0, y0) and (x1, y1).
     */
    public static List<Point> rasterizeRectFilled(int x0, int y0, int x1, int y1) {
        int minX = Math.min(x0, x1);
        int maxX = Math.max(x0, x1);
        int minY = Math.min(y0, y1);
        int maxY = Math.max(y0, y1);

        List<Point> points = new ArrayList<>((maxX - minX + 1) * (maxY - minY + 1));
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                points.add(new Point(x, y));
            }
        }
        return Collections.unmodifiableList(points);
    }

    /**
     * 4-way flood fill bounded by room dimensions and a maximum fill size cap.
     * Throws IllegalStateException if the fill area exceeds maxFillCap.
     */
    public static List<Point> floodFill(
        int startX,
        int startY,
        int gridWidth,
        int gridHeight,
        BiPredicate<Integer, Integer> shouldFillPredicate,
        int maxFillCap
    ) {
        if (startX < 0 || startX >= gridWidth || startY < 0 || startY >= gridHeight) {
            return Collections.emptyList();
        }
        if (!shouldFillPredicate.test(startX, startY)) {
            return Collections.emptyList();
        }

        List<Point> result = new ArrayList<>();
        boolean[] visited = new boolean[gridWidth * gridHeight];
        Deque<Point> queue = new ArrayDeque<>();

        Point start = new Point(startX, startY);
        queue.push(start);
        visited[startY * gridWidth + startX] = true;

        while (!queue.isEmpty()) {
            if (result.size() >= maxFillCap) {
                throw new IllegalStateException(String.format(
                    "Flood fill exceeded maximum allowed area cap of %d cells", maxFillCap));
            }

            Point p = queue.pop();
            result.add(p);

            int x = p.x();
            int y = p.y();

            // 4 neighbors: Right, Left, Top, Bottom
            int[][] neighbors = {{x + 1, y}, {x - 1, y}, {x, y + 1}, {x, y - 1}};
            for (int[] n : neighbors) {
                int nx = n[0];
                int ny = n[1];
                if (nx >= 0 && nx < gridWidth && ny >= 0 && ny < gridHeight) {
                    int idx = ny * gridWidth + nx;
                    if (!visited[idx] && shouldFillPredicate.test(nx, ny)) {
                        visited[idx] = true;
                        queue.push(new Point(nx, ny));
                    }
                }
            }
        }

        return Collections.unmodifiableList(result);
    }
}
