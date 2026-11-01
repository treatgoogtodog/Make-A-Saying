package com.makeasaying.data.world;

import com.makeasaying.engine.entity.TileGrid;
import com.makeasaying.engine.entity.TileShape;
import com.makeasaying.engine.world.Room;
import com.makeasaying.engine.world.RoomExit;
import com.makeasaying.engine.world.SpawnPoint;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CrossRoomValidatorTest {
    @Test
    void testValidBidirectionalLink() {
        Room r1 = new Room("room_01", 20, 12, Map.of(), Map.of(),
            List.of(new SpawnPoint("spawn_01", 32, 16)),
            Collections.emptyList(),
            List.of(new RoomExit("to_02", 300, 16, 20, 48, "room_02", "spawn_02"))
        );

        Room r2 = new Room("room_02", 20, 12, Map.of(), Map.of(),
            List.of(new SpawnPoint("spawn_02", 32, 16)),
            Collections.emptyList(),
            List.of(new RoomExit("to_01", 0, 16, 20, 48, "room_01", "spawn_01"))
        );

        CrossRoomValidator validator = new CrossRoomValidator("room_01");
        List<ValidationFinding> findings = validator.validate(
            Map.of("room_01", r1, "room_02", r2),
            Collections.emptyMap()
        );

        assertTrue(findings.isEmpty(), "Valid bidirectional rooms should have no findings: " + findings);
    }

    @Test
    void testUnknownTargetRoomAndSpawn() {
        Room r1 = new Room("room_01", 20, 12, Map.of(), Map.of(),
            List.of(new SpawnPoint("spawn_01", 32, 16)),
            Collections.emptyList(),
            List.of(new RoomExit("bad_room", 300, 16, 20, 48, "missing_room", "spawn_02"))
        );

        Room r2 = new Room("room_02", 20, 12, Map.of(), Map.of(),
            List.of(new SpawnPoint("spawn_02", 32, 16)),
            Collections.emptyList(),
            List.of(new RoomExit("bad_spawn", 0, 16, 20, 48, "room_01", "missing_spawn"))
        );

        CrossRoomValidator validator = new CrossRoomValidator("room_01");
        List<ValidationFinding> findings = validator.validate(
            Map.of("room_01", r1, "room_02", r2),
            Collections.emptyMap()
        );

        boolean hasBadRoom = findings.stream().anyMatch(f -> f.code().equals(CrossRoomValidator.ERR_UNKNOWN_TARGET_ROOM));
        boolean hasBadSpawn = findings.stream().anyMatch(f -> f.code().equals(CrossRoomValidator.ERR_UNKNOWN_TARGET_SPAWN));

        assertTrue(hasBadRoom);
        assertTrue(hasBadSpawn);
    }

    @Test
    void testDuplicateExitIdAndInvalidBounds() {
        Room r1 = new Room("room_01", 20, 12, Map.of(), Map.of(),
            List.of(new SpawnPoint("spawn_01", 32, 16)),
            Collections.emptyList(),
            List.of(
                new RoomExit("dup", 300, 16, 20, 48, "room_01", "spawn_01"),
                new RoomExit("dup", 500, 16, 20, 48, "room_01", "spawn_01") // 500 exceeds 20*16 = 320
            )
        );

        CrossRoomValidator validator = new CrossRoomValidator("room_01");
        List<ValidationFinding> findings = validator.validate(
            Map.of("room_01", r1),
            Collections.emptyMap()
        );

        assertTrue(findings.stream().anyMatch(f -> f.code().equals(CrossRoomValidator.ERR_DUPLICATE_EXIT_ID)));
        assertTrue(findings.stream().anyMatch(f -> f.code().equals(CrossRoomValidator.ERR_INVALID_EXIT_BOUNDS)));
    }

    @Test
    void testWarningsSolidExitAndSpawnInsideExit() {
        TileGrid grid = new TileGrid(20, 12, 16);
        // Fill tiles x=18..19, y=1..3 with solid
        for (int ty = 1; ty <= 3; ty++) {
            for (int tx = 18; tx <= 19; tx++) {
                grid.setTile(tx, ty, TileShape.SOLID);
            }
        }

        Room r1 = new Room("room_01", 20, 12, Map.of(), Map.of(),
            List.of(new SpawnPoint("spawn_inside", 10, 16)), // Overlaps exit at 0..32
            Collections.emptyList(),
            List.of(
                new RoomExit("solid_exit", 18 * 16, 16, 32, 48, "room_01", "spawn_inside"),
                new RoomExit("overlap_exit", 0, 16, 32, 48, "room_01", "spawn_inside")
            )
        );

        CrossRoomValidator validator = new CrossRoomValidator("room_01");
        List<ValidationFinding> findings = validator.validate(
            Map.of("room_01", r1),
            Map.of("room_01", grid)
        );

        assertTrue(findings.stream().anyMatch(f -> f.code().equals(CrossRoomValidator.WARN_EXIT_INSIDE_SOLID)));
        assertTrue(findings.stream().anyMatch(f -> f.code().equals(CrossRoomValidator.WARN_SPAWN_INSIDE_EXIT)));
    }

    @Test
    void testInfoOrphanAndDeadEnd() {
        Room rStart = new Room("test_01", 20, 12, Map.of(), Map.of(),
            List.of(new SpawnPoint("s", 32, 16)),
            Collections.emptyList(),
            List.of(new RoomExit("to_dead", 300, 16, 20, 48, "dead_end", "s"))
        );

        Room rDeadEnd = new Room("dead_end", 20, 12, Map.of(), Map.of(),
            List.of(new SpawnPoint("s", 32, 16)),
            Collections.emptyList(),
            Collections.emptyList() // No exits
        );

        Room rOrphan = new Room("orphan", 20, 12, Map.of(), Map.of(),
            List.of(new SpawnPoint("s", 32, 16)),
            Collections.emptyList(),
            Collections.emptyList()
        );

        CrossRoomValidator validator = new CrossRoomValidator("test_01");
        List<ValidationFinding> findings = validator.validate(
            Map.of("test_01", rStart, "dead_end", rDeadEnd, "orphan", rOrphan),
            Collections.emptyMap()
        );

        assertTrue(findings.stream().anyMatch(f -> f.code().equals(CrossRoomValidator.INFO_DEAD_END_ROOM) && f.location().contains("dead_end")));
        assertTrue(findings.stream().anyMatch(f -> f.code().equals(CrossRoomValidator.INFO_ORPHAN_ROOM) && f.location().contains("orphan")));
        // test_01 is start room so not orphan
        assertFalse(findings.stream().anyMatch(f -> f.code().equals(CrossRoomValidator.INFO_ORPHAN_ROOM) && f.location().contains("test_01")));
    }

    @Test
    void testBrokenExitFixtureFile() throws IOException {
        Path fixturePath = Path.of("src/test/resources/fixtures/broken_rooms/broken_exit_room.json");
        if (!java.nio.file.Files.exists(fixturePath)) {
            fixturePath = Path.of("data/src/test/resources/fixtures/broken_rooms/broken_exit_room.json");
        }
        assertTrue(java.nio.file.Files.exists(fixturePath), "Fixture must exist at: " + fixturePath);

        Room brokenRoom = RoomLoader.load(fixturePath);
        CrossRoomValidator validator = new CrossRoomValidator("broken_exit_room");
        List<ValidationFinding> findings = validator.validate(
            Map.of("broken_exit_room", brokenRoom),
            Collections.emptyMap()
        );

        assertTrue(findings.stream().anyMatch(f -> f.code().equals(CrossRoomValidator.ERR_UNKNOWN_TARGET_ROOM)));
    }
}
