package com.makeasaying.engine.render;

/**
 * Pure class for generating 16x16 magenta/black checker pixel data with 8px squares.
 * No libGDX or OpenGL dependencies, allowing headless verification.
 */
public final class CheckerGenerator {
    public static final int SIZE = 16;
    public static final int SQUARE_SIZE = 8;

    // RGBA8888 color constants
    public static final int COLOR_MAGENTA = 0xFF00FFFF; // R=255, G=0, B=255, A=255
    public static final int COLOR_BLACK   = 0x000000FF; // R=0, G=0, B=0, A=255

    private CheckerGenerator() {}

    /**
     * Generates a 16x16 array of RGBA8888 integers.
     */
    public static int[] generatePixels() {
        int[] pixels = new int[SIZE * SIZE];
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                int squareX = x / SQUARE_SIZE;
                int squareY = y / SQUARE_SIZE;
                boolean isMagenta = ((squareX + squareY) % 2) == 0;
                pixels[y * SIZE + x] = isMagenta ? COLOR_MAGENTA : COLOR_BLACK;
            }
        }
        return pixels;
    }

    /**
     * Generates a 16x16x4 byte array formatted for RGBA8888.
     */
    public static byte[] generateRgbaBytes() {
        int[] pixels = generatePixels();
        byte[] bytes = new byte[pixels.length * 4];
        for (int i = 0; i < pixels.length; i++) {
            int rgba = pixels[i];
            bytes[i * 4 + 0] = (byte) ((rgba >> 24) & 0xFF); // R
            bytes[i * 4 + 1] = (byte) ((rgba >> 16) & 0xFF); // G
            bytes[i * 4 + 2] = (byte) ((rgba >> 8)  & 0xFF); // B
            bytes[i * 4 + 3] = (byte) (rgba         & 0xFF); // A
        }
        return bytes;
    }
}
