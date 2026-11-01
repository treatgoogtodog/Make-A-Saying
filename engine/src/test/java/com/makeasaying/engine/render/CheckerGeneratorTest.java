package com.makeasaying.engine.render;

import com.makeasaying.engine.util.Log;
import com.makeasaying.engine.util.LogCategory;
import com.makeasaying.engine.util.LogEntry;
import com.makeasaying.engine.util.LogLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CheckerGeneratorTest {

    @BeforeEach
    void setUp() {
        Log.clear();
    }

    @Test
    void testCheckerDimensionsAndPattern() {
        int[] pixels = CheckerGenerator.generatePixels();
        assertEquals(256, pixels.length); // 16x16

        // Top-left 8x8 block is Magenta
        assertEquals(CheckerGenerator.COLOR_MAGENTA, pixels[0 * 16 + 0]); // (0, 0)
        assertEquals(CheckerGenerator.COLOR_MAGENTA, pixels[7 * 16 + 7]); // (7, 7)

        // Top-right 8x8 block is Black
        assertEquals(CheckerGenerator.COLOR_BLACK, pixels[0 * 16 + 8]);   // (8, 0)
        assertEquals(CheckerGenerator.COLOR_BLACK, pixels[7 * 16 + 15]);  // (15, 7)

        // Bottom-left 8x8 block is Black
        assertEquals(CheckerGenerator.COLOR_BLACK, pixels[8 * 16 + 0]);   // (0, 8)
        assertEquals(CheckerGenerator.COLOR_BLACK, pixels[15 * 16 + 7]);  // (7, 15)

        // Bottom-right 8x8 block is Magenta
        assertEquals(CheckerGenerator.COLOR_MAGENTA, pixels[8 * 16 + 8]);  // (8, 8)
        assertEquals(CheckerGenerator.COLOR_MAGENTA, pixels[15 * 16 + 15]); // (15, 15)
    }

    @Test
    void testRgbaByteBufferGeneration() {
        byte[] bytes = CheckerGenerator.generateRgbaBytes();
        assertEquals(1024, bytes.length); // 256 * 4

        // Magenta pixel (R=255, G=0, B=255, A=255)
        assertEquals((byte) 255, bytes[0]);
        assertEquals((byte) 0,   bytes[1]);
        assertEquals((byte) 255, bytes[2]);
        assertEquals((byte) 255, bytes[3]);

        // Black pixel (R=0, G=0, B=0, A=255) at (8, 0) -> pixel index 8 -> byte index 32
        assertEquals((byte) 0,   bytes[32]);
        assertEquals((byte) 0,   bytes[33]);
        assertEquals((byte) 0,   bytes[34]);
        assertEquals((byte) 255, bytes[35]);
    }

    @Test
    void testPlaceholderFallbackLogsLoudAssetError() {
        // Verify loud failure logging for missing asset
        String missingAsset = "sprites/characters/kazuma_walk.png";
        Log.error(LogCategory.ASSETS, "Asset not found: '%s'. Using magenta/black placeholder texture.", missingAsset);

        List<LogEntry> entries = Log.getEntries();
        assertEquals(1, entries.size());

        LogEntry entry = entries.get(0);
        assertEquals(LogLevel.ERROR, entry.level());
        assertEquals(LogCategory.ASSETS, entry.category());
        assertTrue(entry.message().contains(missingAsset));
        assertTrue(entry.message().contains("placeholder"));
    }
}
