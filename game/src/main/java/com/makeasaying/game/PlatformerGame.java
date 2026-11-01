package com.makeasaying.game;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.utils.ScreenUtils;
import com.makeasaying.engine.config.EngineConfig;
import com.makeasaying.engine.util.Log;
import com.makeasaying.engine.util.LogCategory;

/**
 * Main game entry point implementing LibGDX ApplicationListener.
 * In M0, opens a clean window with black clear color.
 */
public class PlatformerGame implements ApplicationListener {
    private final EngineConfig config;

    public PlatformerGame(EngineConfig config) {
        this.config = config != null ? config : EngineConfig.defaultConfig();
    }

    @Override
    public void create() {
        Log.info(LogCategory.ENGINE, "PlatformerGame initialized. Target virtual resolution: %dx%d, Tile size: %d",
            config.virtualWidth(), config.virtualHeight(), config.tileSize());
    }

    @Override
    public void resize(int width, int height) {
        Log.info(LogCategory.RENDER, "Window resized to %dx%d", width, height);
    }

    @Override
    public void render() {
        // Clear screen to dark slate gray / black
        ScreenUtils.clear(0.08f, 0.08f, 0.1f, 1.0f);
    }

    @Override
    public void pause() {
        Log.info(LogCategory.ENGINE, "Game paused");
    }

    @Override
    public void resume() {
        Log.info(LogCategory.ENGINE, "Game resumed");
    }

    @Override
    public void dispose() {
        Log.info(LogCategory.ENGINE, "Game disposed");
    }

    public EngineConfig getConfig() {
        return config;
    }
}
