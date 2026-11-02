package com.makeasaying.data.entity;

import com.makeasaying.engine.entity.EntityDefinition;
import com.makeasaying.engine.schema.ComponentSchemaRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class EntityDefinitionLoaderTest {
    private ComponentSchemaRegistry registry;

    @BeforeEach
    void setUp() {
        registry = ComponentSchemaRegistry.createDefault();
    }

    @Test
    void testLoadAuthoredDefinitions() throws IOException {
        Path playerPath = Path.of("content/entities/player.json");
        if (!Files.exists(playerPath)) playerPath = Path.of("../content/entities/player.json");
        assertTrue(Files.exists(playerPath));

        EntityDefinition playerDef = EntityDefinitionLoader.load(playerPath, registry);
        assertNotNull(playerDef);
        assertEquals("player", playerDef.id());
        assertFalse(playerDef.placeable(), "Player must have placeable: false");
        assertTrue(playerDef.components().containsKey("body"));
        assertTrue(playerDef.components().containsKey("collider"));

        Path platPath = Path.of("content/entities/moving_platform.json");
        if (!Files.exists(platPath)) platPath = Path.of("../content/entities/moving_platform.json");
        assertTrue(Files.exists(platPath));

        EntityDefinition platDef = EntityDefinitionLoader.load(platPath, registry);
        assertNotNull(platDef);
        assertEquals("moving_platform", platDef.id());
        assertTrue(platDef.placeable(), "Platform must have placeable: true");
        assertTrue(platDef.components().containsKey("platform"));
    }

    @Test
    void testInvalidIdThrowsExactCode() {
        String json = """
        {
          "version": 1,
          "id": "Player-Upper!",
          "components": { "body": { "x": 0, "y": 0 } }
        }
        """;
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
            EntityDefinitionLoader.parse(json, registry, "test-id")
        );
        assertTrue(ex.getMessage().contains(EntityDefinitionLoader.DEF_ERR_INVALID_ID));
    }

    @Test
    void testMissingRequiredBodyThrowsExactCode() {
        String json = """
        {
          "version": 1,
          "id": "bad_entity",
          "components": {
            "collider": { "width": 16, "height": 16 }
          }
        }
        """;
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
            EntityDefinitionLoader.parse(json, registry, "test-missing")
        );
        assertTrue(ex.getMessage().contains(EntityDefinitionLoader.DEF_ERR_MISSING_REQUIRED));
    }

    @Test
    void testUnknownComponentThrowsExactCode() {
        String json = """
        {
          "version": 1,
          "id": "bad_comp",
          "components": {
            "body": { "x": 0, "y": 0 },
            "laser_cannon_xyz": { "power": 9000 }
          }
        }
        """;
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
            EntityDefinitionLoader.parse(json, registry, "test-unknown-comp")
        );
        assertTrue(ex.getMessage().contains(EntityDefinitionLoader.DEF_ERR_UNKNOWN_COMPONENT));
    }

    @Test
    void testUnknownFieldThrowsExactCode() {
        String json = """
        {
          "version": 1,
          "id": "bad_field",
          "components": {
            "body": { "x": 0, "y": 0 },
            "collider": { "width": 16, "height": 16, "quantumTunneler": true }
          }
        }
        """;
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
            EntityDefinitionLoader.parse(json, registry, "test-unknown-field")
        );
        assertTrue(ex.getMessage().contains(EntityDefinitionLoader.DEF_ERR_UNKNOWN_FIELD));
    }

    @Test
    void testTypeMismatchThrowsExactCode() {
        String json = """
        {
          "version": 1,
          "id": "bad_type",
          "components": {
            "body": { "x": "ten", "y": 0 }
          }
        }
        """;
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
            EntityDefinitionLoader.parse(json, registry, "test-type")
        );
        assertTrue(ex.getMessage().contains(EntityDefinitionLoader.DEF_ERR_TYPE_MISMATCH));
    }

    @Test
    void testOutOfRangeThrowsExactCode() {
        String json = """
        {
          "version": 1,
          "id": "bad_range",
          "components": {
            "body": { "x": 0, "y": 0 },
            "collider": { "width": -5, "height": 16 }
          }
        }
        """;
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
            EntityDefinitionLoader.parse(json, registry, "test-range")
        );
        assertTrue(ex.getMessage().contains(EntityDefinitionLoader.DEF_ERR_OUT_OF_RANGE));
    }
}
