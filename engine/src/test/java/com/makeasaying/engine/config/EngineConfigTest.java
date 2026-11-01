package com.makeasaying.engine.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EngineConfigTest {

    @Test
    void testDefaultConfigValues() {
        EngineConfig config = EngineConfig.defaultConfig();

        assertEquals(1, config.version());
        assertEquals(320, config.virtualWidth());
        assertEquals(180, config.virtualHeight());
        assertEquals(16, config.tileSize());
        assertEquals(1280, config.windowWidth());
        assertEquals(720, config.windowHeight());
        assertEquals("Make-A-Saying", config.windowTitle());
        assertEquals("#000000", config.borderColor());
        assertTrue(config.vsync());
        assertEquals(60, config.targetFps());
        assertEquals(4.5f, config.cmPerPixel(), 0.001f);
        assertEquals(6, config.headUnitPx());

        assertNotNull(config.bodyCollider());
        assertEquals(12, config.bodyCollider().width());
        assertEquals(36, config.bodyCollider().standingHeight());
        assertEquals(22, config.bodyCollider().crouchedHeight());
    }
}
