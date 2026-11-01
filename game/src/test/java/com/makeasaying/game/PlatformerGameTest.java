package com.makeasaying.game;

import com.makeasaying.engine.config.EngineConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlatformerGameTest {

    @Test
    void testGameInitializationWithConfig() {
        EngineConfig config = EngineConfig.defaultConfig();
        PlatformerGame game = new PlatformerGame(config);

        assertSame(config, game.getConfig());
        assertEquals(320, game.getConfig().virtualWidth());
        assertEquals(180, game.getConfig().virtualHeight());
    }

    @Test
    void testGameInitializationWithNullFallback() {
        PlatformerGame game = new PlatformerGame(null);
        assertNotNull(game.getConfig());
        assertEquals(320, game.getConfig().virtualWidth());
    }
}
