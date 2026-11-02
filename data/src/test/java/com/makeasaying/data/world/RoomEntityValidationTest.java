package com.makeasaying.data.world;

import com.makeasaying.data.entity.EntityDefinitionLoader;
import com.makeasaying.engine.entity.EntityDefinition;
import com.makeasaying.engine.entity.TileShape;
import com.makeasaying.engine.schema.ComponentSchemaRegistry;
import com.makeasaying.engine.world.TileDef;
import com.makeasaying.engine.world.TileSheetCoord;
import com.makeasaying.engine.world.Tileset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoomEntityValidationTest {
    private RoomValidator validator;
    private ComponentSchemaRegistry registry;
    private Map<String, EntityDefinition> entityDefs;
    private Map<String, Tileset> loadedTilesets;

    @BeforeEach
    void setUp() throws IOException {
        validator = new RoomValidator(64, 48, 16);
        registry = ComponentSchemaRegistry.createDefault();
        entityDefs = new LinkedHashMap<>();

        Path entDir = Path.of("content/entities");
        if (!Files.exists(entDir)) entDir = Path.of("../content/entities");
        if (Files.exists(entDir)) {
            try (var s = Files.list(entDir)) {
                for (Path p : s.filter(p -> p.toString().endsWith(".json")).toList()) {
                    EntityDefinition def = EntityDefinitionLoader.load(p, registry);
                    entityDefs.put(def.id(), def);
                }
            }
        }

        loadedTilesets = new LinkedHashMap<>();
        List<TileDef> tiles = List.of(
            new TileDef("solid_01", TileShape.SOLID, "fill", Collections.emptyList()),
            new TileDef("empty_01", TileShape.EMPTY, "empty", Collections.emptyList())
        );
        Map<String, TileSheetCoord> coords = Map.of(
            "solid_01", new TileSheetCoord(0, 0),
            "empty_01", new TileSheetCoord(1, 0)
        );
        Tileset ts = new Tileset("test_area", "test_area", 16, "test_area.png", tiles, coords);
        loadedTilesets.put("test_area", ts);
    }

    private RoomLoader.RoomData createBaseRoom(List<RoomLoader.EntityInstanceData> entities, int entitySeq) {
        List<RoomLoader.TilesetAliasData> ts = List.of(new RoomLoader.TilesetAliasData("a", "test_area"));
        Map<String, List<String>> layers = new LinkedHashMap<>();
        List<String> mainRows = new ArrayList<>();
        for (int r = 0; r < 10; r++) {
            StringBuilder sb = new StringBuilder();
            for (int c = 0; c < 20; c++) {
                if (c > 0) sb.append(' ');
                sb.append(r == 9 ? "a:solid_01" : ".");
            }
            mainRows.add(sb.toString());
        }
        layers.put("main", mainRows);
        List<RoomLoader.SpawnPointData> spawns = List.of(new RoomLoader.SpawnPointData("start", 32, 16));
        return new RoomLoader.RoomData(3, "test_room", 20, 10, entitySeq, ts, layers, spawns, entities, Collections.emptyList());
    }

    @Test
    void testUnknownDefAndNonPlaceableDef() {
        List<RoomLoader.EntityInstanceData> entities = List.of(
            new RoomLoader.EntityInstanceData("ghost_1", "unknown_ghost", 32, 32, null),
            new RoomLoader.EntityInstanceData("player_1", "player", 64, 32, null)
        );
        RoomLoader.RoomData data = createBaseRoom(entities, 5);
        List<ValidationFinding> findings = validator.validate(data, loadedTilesets, entityDefs, registry);

        assertTrue(findings.stream().anyMatch(f -> "ROOM_ERR_UNKNOWN_DEF".equals(f.code())));
        assertTrue(findings.stream().anyMatch(f -> "ROOM_ERR_NON_PLACEABLE_DEF".equals(f.code())));
    }

    @Test
    void testDuplicateInstanceIdAndSeqInvalid() {
        List<RoomLoader.EntityInstanceData> entities = List.of(
            new RoomLoader.EntityInstanceData("platform_1", "moving_platform", 32, 32, null),
            new RoomLoader.EntityInstanceData("platform_1", "moving_platform", 64, 32, null)
        );
        // entitySeq is 1, but platform_1 has suffix 1, so seq must be > 1
        RoomLoader.RoomData data = createBaseRoom(entities, 1);
        List<ValidationFinding> findings = validator.validate(data, loadedTilesets, entityDefs, registry);

        assertTrue(findings.stream().anyMatch(f -> "ROOM_ERR_DUPLICATE_INSTANCE_ID".equals(f.code())));
        assertTrue(findings.stream().anyMatch(f -> "ROOM_ERR_ENTITY_SEQ_INVALID".equals(f.code())));
    }

    @Test
    void testOutOfBoundsEntity() {
        List<RoomLoader.EntityInstanceData> entities = List.of(
            new RoomLoader.EntityInstanceData("platform_1", "moving_platform", -10, 32, null)
        );
        RoomLoader.RoomData data = createBaseRoom(entities, 5);
        List<ValidationFinding> findings = validator.validate(data, loadedTilesets, entityDefs, registry);

        assertTrue(findings.stream().anyMatch(f -> "ROOM_ERR_ENTITY_OUT_OF_BOUNDS".equals(f.code())));
    }

    @Test
    void testOverrideValidationNonOverridableAndOutOfRange() {
        List<RoomLoader.EntityInstanceData> entities = List.of(
            new RoomLoader.EntityInstanceData("platform_1", "moving_platform", 32, 32, Map.of(
                "body", Map.of("x", 500), // body.x is non-overridable
                "platform", Map.of("speed", -10.0f) // min speed is 0.1f, so -10 is out of range
            ))
        );
        RoomLoader.RoomData data = createBaseRoom(entities, 5);
        List<ValidationFinding> findings = validator.validate(data, loadedTilesets, entityDefs, registry);

        assertTrue(findings.stream().anyMatch(f -> "ROOM_ERR_NON_OVERRIDABLE_FIELD".equals(f.code())));
        assertTrue(findings.stream().anyMatch(f -> "ROOM_ERR_OVERRIDE_OUT_OF_RANGE".equals(f.code())));
    }

    @Test
    void testPlatformCrossesSolidWarning() {
        List<RoomLoader.EntityInstanceData> entities = List.of(
            new RoomLoader.EntityInstanceData("platform_1", "moving_platform", 32, 32, Map.of(
                "platform", Map.of("travelY", -32) // travels into solid floor at y=0
            ))
        );
        RoomLoader.RoomData data = createBaseRoom(entities, 5);
        List<ValidationFinding> findings = validator.validate(data, loadedTilesets, entityDefs, registry);

        assertTrue(findings.stream().anyMatch(f -> "ROOM_WARN_PLATFORM_CROSSES_SOLID".equals(f.code())));
    }
}
