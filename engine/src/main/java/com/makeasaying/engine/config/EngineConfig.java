package com.makeasaying.engine.config;

/**
 * Global engine configuration model matching AGENTS.md section 6 constants and M1 brief.
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
    int tickRate,
    int workerThreads,
    float cmPerPixel,
    int headUnitPx,
    BodyColliderConfig bodyCollider,
    String saveSlot,
    boolean continueOnStart
) {
    public static final int CURRENT_VERSION = 1;
    public static final String DEFAULT_BORDER_COLOR = "#000000";
    public static final int DEFAULT_TICK_RATE = 60;
    public static final int DEFAULT_WORKER_THREADS = 2;
    public static final String DEFAULT_SAVE_SLOT = "slot_1";

    public EngineConfig {
        if (borderColor == null || borderColor.isBlank()) {
            borderColor = DEFAULT_BORDER_COLOR;
        }
        if (tickRate <= 0) {
            tickRate = DEFAULT_TICK_RATE;
        }
        workerThreads = Math.clamp(workerThreads <= 0 ? DEFAULT_WORKER_THREADS : workerThreads, 2, 4);
        if (saveSlot == null || saveSlot.isBlank()) {
            saveSlot = DEFAULT_SAVE_SLOT;
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
        String borderColor,
        boolean vsync,
        int targetFps,
        int tickRate,
        int workerThreads,
        float cmPerPixel,
        int headUnitPx,
        BodyColliderConfig bodyCollider
    ) {
        this(version, virtualWidth, virtualHeight, tileSize, windowWidth, windowHeight,
            windowTitle, borderColor, vsync, targetFps, tickRate, workerThreads,
            cmPerPixel, headUnitPx, bodyCollider, DEFAULT_SAVE_SLOT, false);
    }

    public EngineConfig(
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
        this(version, virtualWidth, virtualHeight, tileSize, windowWidth, windowHeight,
            windowTitle, borderColor, vsync, targetFps, DEFAULT_TICK_RATE, DEFAULT_WORKER_THREADS,
            cmPerPixel, headUnitPx, bodyCollider, DEFAULT_SAVE_SLOT, false);
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
            windowTitle, DEFAULT_BORDER_COLOR, vsync, targetFps, DEFAULT_TICK_RATE, DEFAULT_WORKER_THREADS,
            cmPerPixel, headUnitPx, bodyCollider, DEFAULT_SAVE_SLOT, false);
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
            DEFAULT_TICK_RATE,
            DEFAULT_WORKER_THREADS,
            4.5f,
            6,
            BodyColliderConfig.defaultConfig(),
            DEFAULT_SAVE_SLOT,
            false
        );
    }
}
