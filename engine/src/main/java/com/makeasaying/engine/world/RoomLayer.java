package com.makeasaying.engine.world;

import java.util.Arrays;

/**
 * Single grid layer of tokens in a Room, stored Y-up per DEC-007.
 * Tokens are "." (empty) or "alias:tileId".
 */
public final class RoomLayer {
    public static final String EMPTY_TOKEN = ".";

    private final RoomLayerType type;
    private final int width;
    private final int height;
    private final String[] tokens;

    public RoomLayer(RoomLayerType type, int width, int height, String[] tokens) {
        if (type == null) throw new IllegalArgumentException("RoomLayerType must not be null");
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("Layer dimensions must be positive");
        if (tokens == null || tokens.length != width * height) {
            throw new IllegalArgumentException("Token array length must equal width * height (" + (width * height) + ")");
        }

        this.type = type;
        this.width = width;
        this.height = height;
        this.tokens = Arrays.copyOf(tokens, tokens.length);
    }

    public RoomLayerType type() {
        return type;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public String getToken(int x, int y) {
        if (x < 0 || x >= width || y < 0 || y >= height) {
            return EMPTY_TOKEN;
        }
        return tokens[y * width + x];
    }

    public String[] tokens() {
        return tokens;
    }
}
