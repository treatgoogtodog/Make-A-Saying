package com.makeasaying.game;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.makeasaying.data.config.ConfigLoader;
import com.makeasaying.engine.config.EngineConfig;
import com.makeasaying.engine.util.Log;
import com.makeasaying.engine.util.LogCategory;

import java.nio.file.Path;

/**
 * Desktop entry point launching the game with LWJGL3 backend per AGENTS.md section 3.
 */
public final class GameLauncher {
    private GameLauncher() {}

    public static void main(String[] args) {
        Path configPath = Path.of("content/config.json");
        EngineConfig config = ConfigLoader.loadOrDefault(configPath);

        Log.info(LogCategory.GENERAL, "Starting %s (v%d)...", config.windowTitle(), config.version());

        Lwjgl3ApplicationConfiguration appConfig = new Lwjgl3ApplicationConfiguration();
        appConfig.setTitle(config.windowTitle());
        appConfig.setWindowedMode(config.windowWidth(), config.windowHeight());
        appConfig.setWindowSizeLimits(320, 180, -1, -1);
        appConfig.setResizable(true);
        appConfig.useVsync(config.vsync());
        appConfig.setForegroundFPS(config.targetFps());

        new Lwjgl3Application(new PlatformerGame(config), appConfig);
    }
}
