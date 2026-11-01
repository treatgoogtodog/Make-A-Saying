package com.makeasaying.data.tools;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Developer tool to generate authored tile sheet PNGs (test_area.png and test_area_b.png).
 * Documented in docs/FORMATS.md and executed via ./gradlew generateTestTiles.
 */
public final class TileSheetGenerator {
    public static final int TILE_SIZE = 16;
    public static final int SHEET_COLS = 4;
    public static final int SHEET_ROWS = 2;

    public static void main(String[] args) throws IOException {
        Path contentDir = Path.of("content/tilesets");
        if (!Files.exists(contentDir)) {
            contentDir = Path.of("../content/tilesets");
        }
        Files.createDirectories(contentDir);

        Path areaA = contentDir.resolve("test_area.png");
        Path areaB = contentDir.resolve("test_area_b.png");

        generateToFile(areaA, Theme.AREA_A);
        generateToFile(areaB, Theme.AREA_B);

        System.out.println("Generated " + areaA.toAbsolutePath());
        System.out.println("Generated " + areaB.toAbsolutePath());
    }

    public enum Theme {
        AREA_A,
        AREA_B
    }

    public static BufferedImage generateSheet(Theme theme) {
        int width = SHEET_COLS * TILE_SIZE;  // 64
        int height = SHEET_ROWS * TILE_SIZE; // 32

        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        // Background transparent
        g.setColor(new Color(0, 0, 0, 0));
        g.fillRect(0, 0, width, height);

        Color solidFill = (theme == Theme.AREA_A) ? new Color(0x3a, 0x3f, 0x58) : new Color(0x8c, 0x43, 0x2a);
        Color solidBorder = (theme == Theme.AREA_A) ? new Color(0x56, 0x5d, 0x7e) : new Color(0xb8, 0x5b, 0x3b);
        Color slopeFill = (theme == Theme.AREA_A) ? new Color(0x4a, 0x50, 0x70) : new Color(0xa0, 0x5a, 0x2c);
        Color slopeLine = (theme == Theme.AREA_A) ? new Color(0x76, 0x7d, 0xa4) : new Color(0xd4, 0x7a, 0x3b);
        Color oneWayBar = (theme == Theme.AREA_A) ? new Color(0xe5, 0xa8, 0x23) : new Color(0x5c, 0x3a, 0x21);
        Color oneWayLine = (theme == Theme.AREA_A) ? new Color(0xff, 0xd0, 0x60) : new Color(0x8f, 0x5c, 0x36);
        Color wallFill = (theme == Theme.AREA_A) ? new Color(0x28, 0x2c, 0x3e) : new Color(0x52, 0x2b, 0x1c);
        Color wallBorder = (theme == Theme.AREA_A) ? new Color(0x3e, 0x43, 0x5d) : new Color(0x6e, 0x3b, 0x27);
        Color underSlopeFill = (theme == Theme.AREA_A) ? new Color(0x32, 0x36, 0x4c) : new Color(0x61, 0x33, 0x22);
        Color platFill = (theme == Theme.AREA_A) ? new Color(0x4e, 0x54, 0x74) : new Color(0x73, 0x41, 0x25);
        Color platBorder = (theme == Theme.AREA_A) ? new Color(0x6e, 0x76, 0x9f) : new Color(0x99, 0x56, 0x31);

        // Tile 0, 0: solid_01
        int x0 = 0, y0 = 0;
        g.setColor(solidFill);
        g.fillRect(x0, y0, TILE_SIZE, TILE_SIZE);
        g.setColor(solidBorder);
        g.drawRect(x0, y0, TILE_SIZE - 1, TILE_SIZE - 1);

        // Tile 1, 0: slope_r (SLOPE_45_UP_RIGHT)
        int x1 = 1 * TILE_SIZE, y1 = 0;
        g.setColor(slopeFill);
        for (int c = 0; c < TILE_SIZE; c++) {
            int colHeight = c + 1;
            g.drawLine(x1 + c, y1 + TILE_SIZE - 1, x1 + c, y1 + TILE_SIZE - colHeight);
        }
        g.setColor(slopeLine);
        g.drawLine(x1, y1 + TILE_SIZE - 1, x1 + TILE_SIZE - 1, y1);

        // Tile 2, 0: slope_l (SLOPE_45_UP_LEFT)
        int x2 = 2 * TILE_SIZE, y2 = 0;
        g.setColor(slopeFill);
        for (int c = 0; c < TILE_SIZE; c++) {
            int colHeight = 16 - c;
            g.drawLine(x2 + c, y2 + TILE_SIZE - 1, x2 + c, y2 + TILE_SIZE - colHeight);
        }
        g.setColor(slopeLine);
        g.drawLine(x2, y2, x2 + TILE_SIZE - 1, y2 + TILE_SIZE - 1);

        // Tile 3, 0: one_way_01
        int x3 = 3 * TILE_SIZE, y3 = 0;
        g.setColor(oneWayBar);
        g.fillRect(x3, y3, TILE_SIZE, 4);
        g.setColor(oneWayLine);
        g.drawLine(x3, y3, x3 + TILE_SIZE - 1, y3);

        // Tile 0, 1: wall_01
        int x4 = 0, y4 = 1 * TILE_SIZE;
        g.setColor(wallFill);
        g.fillRect(x4, y4, TILE_SIZE, TILE_SIZE);
        g.setColor(wallBorder);
        g.drawRect(x4, y4, TILE_SIZE - 1, TILE_SIZE - 1);

        // Tile 1, 1: fill_01
        int x5 = 1 * TILE_SIZE, y5 = 1 * TILE_SIZE;
        g.setColor(underSlopeFill);
        g.fillRect(x5, y5, TILE_SIZE, TILE_SIZE);

        // Tile 2, 1: plat_01
        int x6 = 2 * TILE_SIZE, y6 = 1 * TILE_SIZE;
        g.setColor(platFill);
        g.fillRect(x6, y6, TILE_SIZE, TILE_SIZE);
        g.setColor(platBorder);
        g.drawRect(x6, y6, TILE_SIZE - 1, TILE_SIZE - 1);

        // Tile 3, 1: placeholder (Magenta checker 8x8)
        int x7 = 3 * TILE_SIZE, y7 = 1 * TILE_SIZE;
        g.setColor(new Color(0xff, 0x00, 0xff)); // Magenta
        g.fillRect(x7, y7, 8, 8);
        g.fillRect(x7 + 8, y7 + 8, 8, 8);
        g.setColor(Color.BLACK);
        g.fillRect(x7 + 8, y7, 8, 8);
        g.fillRect(x7, y7 + 8, 8, 8);

        g.dispose();
        return img;
    }

    public static void generateToFile(Path outputPath, Theme theme) throws IOException {
        if (outputPath.getParent() != null) {
            Files.createDirectories(outputPath.getParent());
        }
        BufferedImage img = generateSheet(theme);
        ImageIO.write(img, "png", outputPath.toFile());
    }

    public static void generateToFile(Path outputPath) throws IOException {
        generateToFile(outputPath, Theme.AREA_A);
    }
}
