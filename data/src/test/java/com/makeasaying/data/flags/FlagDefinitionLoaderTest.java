package com.makeasaying.data.flags;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.makeasaying.engine.flags.FlagDefinition;
import com.makeasaying.engine.flags.FlagRegistry;
import com.makeasaying.engine.flags.FlagScope;
import com.makeasaying.engine.flags.FlagType;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlagDefinitionLoaderTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void testLoadAuthoredFlags() throws IOException {
        Path flagsPath = Path.of("content/flags.json");
        if (!Files.exists(flagsPath)) flagsPath = Path.of("..", "content/flags.json");
        assertTrue(Files.exists(flagsPath), "Authored content/flags.json must exist");

        FlagRegistry registry = FlagDefinitionLoader.load(flagsPath);
        assertNotNull(registry);
        assertTrue(registry.size() >= 5);

        FlagDefinition stage = registry.get("test.stage");
        assertNotNull(stage);
        assertEquals(FlagType.INT, stage.type());
        assertEquals(FlagScope.GLOBAL, stage.scope());
        assertEquals(0, stage.defaultValue());
        assertEquals(0, stage.min());
        assertEquals(10, stage.max());

        FlagDefinition door = registry.get("test.door_open");
        assertNotNull(door);
        assertEquals(FlagType.BOOL, door.type());
        assertEquals(FlagScope.ROOM, door.scope());
        assertEquals(0, door.defaultValue());

        FlagDefinition lever = registry.get("test.lever_state");
        assertNotNull(lever);
        assertEquals(FlagType.ENUM, lever.type());
        assertEquals(FlagScope.SESSION, lever.scope());
        assertEquals(0, lever.defaultValue());
        assertEquals(3, lever.enumValues().size());

        FlagDefinition barrier = registry.get("test.temp_barrier");
        assertNotNull(barrier);
        assertEquals(FlagType.BOOL, barrier.type());
        assertEquals(FlagScope.ROOM_TEMP, barrier.scope());
        assertEquals(1, barrier.defaultValue());
    }

    @Test
    void testRejectInvalidId() {
        String json = """
        {
          "version": 1,
          "flags": {
            "bad-flag-name!": { "type": "bool", "scope": "global", "default": false }
          }
        }
        """;
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> FlagDefinitionLoader.parse(MAPPER.readTree(json)));
        assertTrue(ex.getMessage().contains("FLAG_ERR_INVALID_ID"));
    }

    @Test
    void testRejectUnknownType() {
        String json = """
        {
          "version": 1,
          "flags": {
            "test.flag": { "type": "float", "scope": "global", "default": 0 }
          }
        }
        """;
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> FlagDefinitionLoader.parse(MAPPER.readTree(json)));
        assertTrue(ex.getMessage().contains("FLAG_ERR_UNKNOWN_TYPE"));
    }

    @Test
    void testRejectUnknownScope() {
        String json = """
        {
          "version": 1,
          "flags": {
            "test.flag": { "type": "bool", "scope": "persistent", "default": false }
          }
        }
        """;
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> FlagDefinitionLoader.parse(MAPPER.readTree(json)));
        assertTrue(ex.getMessage().contains("FLAG_ERR_UNKNOWN_SCOPE"));
    }

    @Test
    void testRejectDefaultOutOfRange() {
        String json = """
        {
          "version": 1,
          "flags": {
            "test.flag": { "type": "int", "scope": "global", "default": 15, "min": 0, "max": 10 }
          }
        }
        """;
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> FlagDefinitionLoader.parse(MAPPER.readTree(json)));
        assertTrue(ex.getMessage().contains("FLAG_ERR_DEFAULT_OUT_OF_RANGE"));
    }

    @Test
    void testRejectInvalidEnumDefault() {
        String json = """
        {
          "version": 1,
          "flags": {
            "test.enum": { "type": "enum", "scope": "session", "default": "missing_option", "values": ["a", "b"] }
          }
        }
        """;
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> FlagDefinitionLoader.parse(MAPPER.readTree(json)));
        assertTrue(ex.getMessage().contains("FLAG_ERR_DEFAULT_NOT_IN_VALUES"));
    }
}
