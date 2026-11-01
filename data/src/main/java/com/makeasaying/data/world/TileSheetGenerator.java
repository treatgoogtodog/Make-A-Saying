package com.makeasaying.data.world;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Generates authored placeholder tile sheet PNG per M5a brief.
 * Uses pure Java AWT/ImageIO (runs headlessly without GL natives).
 */
public final class TileSheetGenerator {
    public static final int TILE_SIZE = 16;
    public static final int SHEET_COLS = 4;
    public static final int SHEET_ROWS = 2;

    private TileSheetGenerator() {}

    public static BufferedImage generateSheet() {
        int width = SHEET_COLS * TILE_SIZE;  // 64
        int height = SHEET_ROWS * TILE_SIZE; // 32

        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        // Background transparent
        g.setColor(new Color(0, 0, 0, 0));
        g.fillRect(0, 0, width, height);

        // Tile 0, 0: solid_01 (Dark slate brick)
        int x0 = 0, y0 = 0;
        g.setColor(new Color(0x3a, 0x3f, 0x58));
        g.fillRect(x0, y0, TILE_SIZE, TILE_SIZE);
        g.setColor(new Color(0x56, 0x5d, 0x7e));
        g.drawRect(x0, y0, TILE_SIZE - 1, TILE_SIZE - 1);

        // Tile 1, 0: slope_r (SLOPE_45_UP_RIGHT: diagonal from bottom-left (0, 15) to top-right (15, 0))
        int x1 = 1 * TILE_SIZE, y1 = 0;
        g.setColor(new Color(0x4a, 0x50, 0x70));
        for (int c = 0; c < TILE_SIZE; c++) {
            // column c has height c + 1 from bottom (y1 + 15)
            int colHeight = c + 1;
            g.drawLine(x1 + c, y1 + TILE_SIZE - 1, x1 + c, y1 + TILE_SIZE - colHeight);
        }
        g.setColor(new Color(0x76, 0x7d, 0xa4));
        g.drawLine(x1, y1 + TILE_SIZE - 1, x1 + TILE_SIZE - 1, y1);

        // Tile 2, 0: slope_l (SLOPE_45_UP_LEFT: diagonal from top-left (0, 0) to bottom-right (15, 15))
        int x2 = 2 * TILE_SIZE, y2 = 0;
        g.setColor(new Color(0x4a, 0x50, 0x70));
        for (int c = 0; c < TILE_SIZE; c++) {
            int colHeight = 16 - c;
            g.drawLine(x2 + c, y2 + TILE_SIZE - 1, x2 + c, y2 + TILE_SIZE - colHeight);
        }
        g.setColor(new Color(0x76, 0x7d, 0xa4));
        g.drawLine(x2, y2, x2 + TILE_SIZE - 1, y2 + TILE_SIZE - 1);

        // Tile 3, 0: one_way_01 (Gold/amber top platform edge)
        int x3 = 3 * TILE_SIZE, y3 = 0;
        g.setColor(new Color(0xe5, 0xa8, 0x23));
        g.fillRect(x3, y3, TILE_SIZE, 4);
        g.setColor(new Color(0xff, 0xd0, 0x60));
        g.drawLine(x3, y3, x3 + TILE_SIZE - 1, y3);

        // Tile 0, 1: wall_01 (Wall fill)
        int x4 = 0, y4 = 1 * TILE_SIZE;
        g.setColor(new Color(0x28, 0x2c, 0x3e));
        g.fillRect(x4, y4, TILE_SIZE, TILE_SIZE);
        g.setColor(new Color(0x3e, 0x43, 0x5d));
        g.drawRect(x4, y4, TILE_SIZE - 1, TILE_SIZE - 1);

        // Tile 1, 1: fill_01 (Under-slope solid fill)
        int x5 = 1 * TILE_SIZE, y5 = 1 * TILE_SIZE;
        g.setColor(new Color(0x32, 0x36, 0x4c));
        g.fillRect(x5, y5, TILE_SIZE, TILE_SIZE);

        // Tile 2, 1: plat_01 (Platform block)
        int x6 = 2 * TILE_SIZE, y6 = 1 * TILE_SIZE;
        g.setColor(new Color(0x4e, 0x54, 0x74));
        g.fillRect(x6, y6, TILE_SIZE, TILE_SIZE);
        g.setColor(new Color(0x6e, 0x76, 0x9f));
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

    public static void generateToFile(Path outputPath) throws IOException {
        if (outputPath.getParent() != null) {
            Files.createDirectories(outputPath.getParent());
        }
        BufferedImage sheet = generateSheet();
        ImageIO.write(sheet, "png", outputPath.toFile());
    }
}
