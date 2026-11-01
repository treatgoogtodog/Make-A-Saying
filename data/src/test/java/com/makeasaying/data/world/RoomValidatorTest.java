package com.makeasaying.data.world;

import com.makeasaying.engine.entity.TileShape;
import com.makeasaying.engine.world.TileDef;
import com.makeasaying.engine.world.TileSheetCoord;
import com.makeasaying.engine.world.Tileset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RoomValidatorTest {
    private RoomValidator validator;
    private Map<String, Tileset> loadedTilesets;

    @BeforeEach
    void setUp() {
        validator = new RoomValidator(64, 48, 16);
        loadedTilesets = new LinkedHashMap<>();

        List<TileDef> tiles = List.of(
            new TileDef("solid_01", TileShape.SOLID, "fill", Collections.emptyList()),
            new TileDef("slope_r", TileShape.SLOPE_45_UP_RIGHT, "slope_r45", Collections.emptyList()),
            new TileDef("one_way_01", TileShape.ONE_WAY, "platform", Collections.emptyList())
        );
        Map<String, TileSheetCoord> coords = Map.of(
            "solid_01", new TileSheetCoord(0, 0),
            "slope_r", new TileSheetCoord(1, 0),
            "one_way_01", new TileSheetCoord(3, 0)
        );

        Tileset testTs = new Tileset("test_area", "test_area", 16, "test_area.png", tiles, coords);
        loadedTilesets.put("test_area", testTs);
    }

    private RoomLoader.RoomData createValidRoomData(int w, int h) {
        List<RoomLoader.TilesetAliasData> ts = List.of(new RoomLoader.TilesetAliasData("a", "test_area"));
        Map<String, List<String>> layers = new LinkedHashMap<>();

        List<String> mainRows = new ArrayList<>();
        for (int r = 0; r < h; r++) {
            StringBuilder sb = new StringBuilder();
            for (int c = 0; c < w; c++) {
                if (c > 0) sb.append(' ');
                sb.append(r == h - 1 ? "a:solid_01" : ".");
            }
            mainRows.add(sb.toString());
        }
        layers.put("main", mainRows);

        List<RoomLoader.SpawnPointData> spawns = List.of(new RoomLoader.SpawnPointData("start", 32, 16));
        List<RoomLoader.EntityInstanceData> entities = List.of(new RoomLoader.EntityInstanceData("npc_1", "guard", 64, 16, null));

        return new RoomLoader.RoomData(1, "room_valid", w, h, ts, layers, spawns, entities);
    }

    @Test
    void testValidRoomProducesNoErrors() {
        RoomLoader.RoomData data = createValidRoomData(20, 10);
        List<ValidationFinding> findings = validator.validate(data, loadedTilesets);
        assertTrue(findings.stream().noneMatch(f -> f.severity() == ValidationSeverity.ERROR),
            "Valid room must have zero ERROR findings");
    }

    @Test
    void testRoomIdValidation() {
        RoomLoader.RoomData badId = new RoomLoader.RoomData(1, "Room_With_Caps!", 20, 10,
            Collections.emptyList(), Collections.emptyMap(), Collections.emptyList(), Collections.emptyList());
        List<ValidationFinding> findings = validator.validate(badId, loadedTilesets);
        assertTrue(findings.stream().anyMatch(f -> "ERR_INVALID_ID".equals(f.code())));
    }

    @Test
    void testRoomSizeWarning() {
        RoomLoader.RoomData hugeRoom = createValidRoomData(70, 50);
        List<ValidationFinding> findings = validator.validate(hugeRoom, loadedTilesets);
        assertTrue(findings.stream().anyMatch(f -> "WARN_ROOM_SIZE".equals(f.code()) && f.severity() == ValidationSeverity.WARNING),
            "Rooms exceeding warn dimensions must produce WARN_ROOM_SIZE");
    }

    @Test
    void testUnknownTilesetId() {
        List<RoomLoader.TilesetAliasData> ts = List.of(new RoomLoader.TilesetAliasData("a", "nonexistent_tileset"));
        RoomLoader.RoomData data = new RoomLoader.RoomData(1, "room_bad_ts", 20, 10, ts,
            Map.of("main", List.of()), Collections.emptyList(), Collections.emptyList());

        List<ValidationFinding> findings = validator.validate(data, loadedTilesets);
        assertTrue(findings.stream().anyMatch(f -> "ERR_UNKNOWN_TILESET".equals(f.code())));
    }

    @Test
    void testDuplicateTilesetAlias() {
        List<RoomLoader.TilesetAliasData> ts = List.of(
            new RoomLoader.TilesetAliasData("a", "test_area"),
            new RoomLoader.TilesetAliasData("a", "test_area")
        );
        RoomLoader.RoomData data = new RoomLoader.RoomData(1, "room_dup_alias", 20, 10, ts,
            Map.of("main", List.of()), Collections.emptyList(), Collections.emptyList());

        List<ValidationFinding> findings = validator.validate(data, loadedTilesets);
        assertTrue(findings.stream().anyMatch(f -> "ERR_DUPLICATE_ALIAS".equals(f.code())));
    }

    @Test
    void testTileSizeMismatch() {
        Tileset badSizeTs = new Tileset("bad_ts", "bad_area", 32, "bad.png", Collections.emptyList(), Collections.emptyMap());
        loadedTilesets.put("bad_ts", badSizeTs);

        List<RoomLoader.TilesetAliasData> ts = List.of(new RoomLoader.TilesetAliasData("b", "bad_ts"));
        RoomLoader.RoomData data = new RoomLoader.RoomData(1, "room_size_mismatch", 20, 10, ts,
            Map.of("main", List.of()), Collections.emptyList(), Collections.emptyList());

        List<ValidationFinding> findings = validator.validate(data, loadedTilesets);
        assertTrue(findings.stream().anyMatch(f -> "ERR_TILE_SIZE_MISMATCH".equals(f.code())));
    }

    @Test
    void testMissingMainLayer() {
        RoomLoader.RoomData data = new RoomLoader.RoomData(1, "room_no_main", 20, 10,
            Collections.emptyList(), Map.of("foreground", List.of()), Collections.emptyList(), Collections.emptyList());

        List<ValidationFinding> findings = validator.validate(data, loadedTilesets);
        assertTrue(findings.stream().anyMatch(f -> "ERR_MISSING_MAIN_LAYER".equals(f.code())));
    }

    @Test
    void testRowCountMismatch() {
        Map<String, List<String>> layers = Map.of("main", List.of(". .", ". .")); // 2 rows instead of 10
        RoomLoader.RoomData data = new RoomLoader.RoomData(1, "room_bad_rows", 20, 10,
            Collections.emptyList(), layers, Collections.emptyList(), Collections.emptyList());

        List<ValidationFinding> findings = validator.validate(data, loadedTilesets);
        assertTrue(findings.stream().anyMatch(f -> "ERR_ROW_COUNT_MISMATCH".equals(f.code())));
    }

    @Test
    void testTokenCountMismatch() {
        List<String> rows = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            rows.add(". . ."); // 3 tokens instead of 20
        }
        Map<String, List<String>> layers = Map.of("main", rows);
        RoomLoader.RoomData data = new RoomLoader.RoomData(1, "room_bad_tokens", 20, 10,
            Collections.emptyList(), layers, Collections.emptyList(), Collections.emptyList());

        List<ValidationFinding> findings = validator.validate(data, loadedTilesets);
        assertTrue(findings.stream().anyMatch(f -> "ERR_TOKEN_COUNT_MISMATCH".equals(f.code())));
    }

    @Test
    void testUnknownAliasAndTile() {
        List<String> rows = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            rows.add("z:unknown_tile .");
        }
        Map<String, List<String>> layers = Map.of("main", rows);
        RoomLoader.RoomData data = new RoomLoader.RoomData(1, "room_bad_token", 2, 10,
            List.of(new RoomLoader.TilesetAliasData("a", "test_area")), layers, Collections.emptyList(), Collections.emptyList());

        List<ValidationFinding> findings = validator.validate(data, loadedTilesets);
        assertTrue(findings.stream().anyMatch(f -> "ERR_UNKNOWN_ALIAS".equals(f.code())));

        // Test known alias but unknown tile
        rows.clear();
        for (int i = 0; i < 10; i++) {
            rows.add("a:ghost_tile .");
        }
        RoomLoader.RoomData data2 = new RoomLoader.RoomData(1, "room_bad_tile", 2, 10,
            List.of(new RoomLoader.TilesetAliasData("a", "test_area")), Map.of("main", rows), Collections.emptyList(), Collections.emptyList());

        List<ValidationFinding> findings2 = validator.validate(data2, loadedTilesets);
        assertTrue(findings2.stream().anyMatch(f -> "ERR_UNKNOWN_TILE".equals(f.code())));
    }

    @Test
    void testSpawnOutOfBoundsAndInsideSolid() {
        // Spawn point out of bounds (room is 20x10 tiles = 320x160 px)
        List<RoomLoader.SpawnPointData> spawns = List.of(
            new RoomLoader.SpawnPointData("sp_oob", 400, 100),
            new RoomLoader.SpawnPointData("sp_solid", 32, 0) // inside y=0 solid floor
        );

        RoomLoader.RoomData valid = createValidRoomData(20, 10);
        RoomLoader.RoomData data = new RoomLoader.RoomData(1, "room_spawns", 20, 10,
            valid.tilesets(), valid.layers(), spawns, valid.entities());

        List<ValidationFinding> findings = validator.validate(data, loadedTilesets);
        assertTrue(findings.stream().anyMatch(f -> "ERR_SPAWN_OUT_OF_BOUNDS".equals(f.code())));
        assertTrue(findings.stream().anyMatch(f -> "ERR_SPAWN_INSIDE_SOLID".equals(f.code())));
    }

    @Test
    void testPrintDeliberatelyBrokenRoomFindings() {
        // Deliberately broken room exercising all validator checks
        List<RoomLoader.TilesetAliasData> ts = List.of(
            new RoomLoader.TilesetAliasData("a", "test_area"),
            new RoomLoader.TilesetAliasData("a", "test_area"), // duplicate alias
            new RoomLoader.TilesetAliasData("b", "unknown_ts") // unknown tileset
        );

        Map<String, List<String>> layers = new LinkedHashMap<>();
        layers.put("main", List.of(
            "a:solid_01 a:ghost_tile bad_token", // unknown tile + invalid token syntax + width mismatch (3 vs 40)
            "z:solid_01 . ."                     // unknown alias 'z'
            // row count mismatch: only 2 rows provided instead of height 50
        ));

        List<RoomLoader.SpawnPointData> spawns = List.of(
            new RoomLoader.SpawnPointData("bad_spawn", -10, 800), // out of bounds
            new RoomLoader.SpawnPointData("spawn_1", 32, 0),      // inside solid floor
            new RoomLoader.SpawnPointData("spawn_1", 32, 16)      // duplicate spawn id
        );

        List<RoomLoader.EntityInstanceData> entities = List.of(
            new RoomLoader.EntityInstanceData("ent_1", "guard", 32, 16, null),
            new RoomLoader.EntityInstanceData("ent_1", "guard", 64, 16, null) // duplicate entity id
        );

        RoomLoader.RoomData brokenRoom = new RoomLoader.RoomData(
            1,
            "Broken_Room_ID!", // invalid ID chars
            40,
            50, // exceeds warn height 48
            ts,
            layers,
            spawns,
            entities
        );

        List<ValidationFinding> findings = validator.validate(brokenRoom, loadedTilesets);
        System.out.println("--- DELIBERATELY BROKEN ROOM VALIDATION FINDINGS ---");
        for (ValidationFinding f : findings) {
            System.out.println(f);
        }
        System.out.println("---------------------------------------------------");
        assertFalse(findings.isEmpty());
    }
}
