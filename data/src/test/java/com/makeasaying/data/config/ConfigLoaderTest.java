package com.makeasaying.data.config;

import com.makeasaying.engine.config.EngineConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ConfigLoaderTest {

    @Test
    void testLoadAuthoredConfig() throws IOException {
        Path path = Path.of("../content/config.json");
        if (!Files.exists(path)) {
            path = Path.of("content/config.json");
        }
        assertTrue(Files.exists(path), "content/config.json must exist in project");

        EngineConfig config = ConfigLoader.load(path);
        assertEquals(1, config.version());
        assertEquals(320, config.virtualWidth());
        assertEquals(180, config.virtualHeight());
        assertEquals(16, config.tileSize());
        assertEquals(12, config.bodyCollider().width());
        assertEquals(36, config.bodyCollider().standingHeight());
    }

    @Test
    void testValidationRejectsInvalidData() {
        assertThrows(IllegalArgumentException.class, () ->
            ConfigLoader.validate(null, "null-test")
        );

        EngineConfig badVersion = new EngineConfig(0, 320, 180, 16, 1280, 720, "Title", true, 60, 4.5f, 6,
            EngineConfig.BodyColliderConfig.defaultConfig());
        assertThrows(IllegalStateException.class, () ->
            ConfigLoader.validate(badVersion, "version-test")
        );

        EngineConfig badWidth = new EngineConfig(1, 0, 180, 16, 1280, 720, "Title", true, 60, 4.5f, 6,
            EngineConfig.BodyColliderConfig.defaultConfig());
        assertThrows(IllegalStateException.class, () ->
            ConfigLoader.validate(badWidth, "width-test")
        );

        EngineConfig badCollider = new EngineConfig(1, 320, 180, 16, 1280, 720, "Title", true, 60, 4.5f, 6,
            new EngineConfig.BodyColliderConfig(0, 36, 22));
        assertThrows(IllegalStateException.class, () ->
            ConfigLoader.validate(badCollider, "collider-test")
        );
    }

    @Test
    void testRoundTripSerialization(@TempDir Path tempDir) throws IOException {
        Path tempFile = tempDir.resolve("test_config.json");
        EngineConfig original = EngineConfig.defaultConfig();

        ConfigLoader.save(original, tempFile);
        assertTrue(Files.exists(tempFile));

        EngineConfig loaded = ConfigLoader.load(tempFile);
        assertEquals(original, loaded);
    }
}
