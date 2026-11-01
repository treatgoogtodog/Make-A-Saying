package com.makeasaying.engine.config;

/**
 * Global engine configuration model matching AGENTS.md section 6 constants.
 * Populated from content/config.json via the data module loader.
 */
public record EngineConfig(
    int version,
    int virtualWidth,
    int virtualHeight,
    int tileSize,
    int windowWidth,
    int windowHeight,
    String windowTitle,
    String borderColor,
    boolean vsync,
    int targetFps,
    float cmPerPixel,
    int headUnitPx,
    BodyColliderConfig bodyCollider
) {
    public static final int CURRENT_VERSION = 1;
    public static final String DEFAULT_BORDER_COLOR = "#000000";

    public EngineConfig {
        if (borderColor == null || borderColor.isBlank()) {
            borderColor = DEFAULT_BORDER_COLOR;
        }
    }

    public record BodyColliderConfig(
        int width,
        int standingHeight,
        int crouchedHeight
    ) {
        public static BodyColliderConfig defaultConfig() {
            return new BodyColliderConfig(12, 36, 22);
        }
    }

    public EngineConfig(
        int version,
        int virtualWidth,
        int virtualHeight,
        int tileSize,
        int windowWidth,
        int windowHeight,
        String windowTitle,
        boolean vsync,
        int targetFps,
        float cmPerPixel,
        int headUnitPx,
        BodyColliderConfig bodyCollider
    ) {
        this(version, virtualWidth, virtualHeight, tileSize, windowWidth, windowHeight,
            windowTitle, DEFAULT_BORDER_COLOR, vsync, targetFps, cmPerPixel, headUnitPx, bodyCollider);
    }

    public static EngineConfig defaultConfig() {
        return new EngineConfig(
            CURRENT_VERSION,
            320,
            180,
            16,
            1280,
            720,
            "Make-A-Saying",
            DEFAULT_BORDER_COLOR,
            true,
            60,
            4.5f,
            6,
            BodyColliderConfig.defaultConfig()
        );
    }
}
