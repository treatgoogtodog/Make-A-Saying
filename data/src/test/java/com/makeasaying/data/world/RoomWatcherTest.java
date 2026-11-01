package com.makeasaying.data.world;

import com.makeasaying.engine.async.Jobs;
import com.makeasaying.engine.entity.TileShape;
import com.makeasaying.engine.world.Room;
import com.makeasaying.engine.world.TileDef;
import com.makeasaying.engine.world.Tileset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class RoomWatcherTest {
    private Jobs jobs;
    private Map<String, Tileset> loadedTilesets;

    @BeforeEach
    void setUp() {
        jobs = new Jobs(2);
        loadedTilesets = new LinkedHashMap<>();

        List<TileDef> tiles = List.of(
            new TileDef("solid_01", TileShape.SOLID, "fill", Collections.emptyList()),
            new TileDef("one_way_01", TileShape.ONE_WAY, "platform", Collections.emptyList())
        );
        Tileset ts = new Tileset("test_area", "test_area", 16, "test_area.png", tiles, Collections.emptyMap());
        loadedTilesets.put("test_area", ts);
    }

    @AfterEach
    void tearDown() {
        jobs.shutdown();
    }

    private String validRoomJson(int width, int height, String token) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"version\": 1,\n");
        sb.append("  \"id\": \"test_room\",\n");
        sb.append("  \"width\": ").append(width).append(",\n");
        sb.append("  \"height\": ").append(height).append(",\n");
        sb.append("  \"tilesets\": [{\"alias\": \"a\", \"id\": \"test_area\"}],\n");
        sb.append("  \"layers\": {\n");
        sb.append("    \"main\": [\n");
        for (int r = 0; r < height; r++) {
            sb.append("      \"");
            for (int c = 0; c < width; c++) {
                if (c > 0) sb.append(' ');
                sb.append(token);
            }
            sb.append(r < height - 1 ? "\",\n" : "\"\n");
        }
        sb.append("    ]\n");
        sb.append("  },\n");
        sb.append("  \"spawnPoints\": [{\"id\": \"start\", \"x\": 0, \"y\": 0}],\n");
        sb.append("  \"entities\": []\n");
        sb.append("}\n");
        return sb.toString();
    }

    @Test
    void testValidFileReloadsAtDrainBoundary(@TempDir Path tempDir) throws Exception {
        Path roomFile = tempDir.resolve("test_room.json");
        Files.writeString(roomFile, validRoomJson(4, 2, "."));

        AtomicReference<RoomWatcher.RoomLoadResult> activeResult = new AtomicReference<>();
        RoomWatcher watcher = new RoomWatcher(roomFile, loadedTilesets, jobs, activeResult::set);

        // Modify file
        Thread.sleep(20);
        Files.writeString(roomFile, validRoomJson(4, 2, "a:one_way_01"));
        Files.setLastModifiedTime(roomFile, FileTime.fromMillis(System.currentTimeMillis() + 1000));

        watcher.checkAndReload();

        // Wait for worker task to complete off-thread
        Thread.sleep(100);

        // Before drain: main thread callback hasn't fired yet
        assertNull(activeResult.get(), "Callback must not run before Jobs.drain()");

        // Drain on main thread
        jobs.drain();

        assertNotNull(activeResult.get(), "Callback must fire upon Jobs.drain()");
        assertEquals(TileShape.ONE_WAY, activeResult.get().grid().getShape(0, 0));
    }

    @Test
    void testInvalidFileKeepsOldValues(@TempDir Path tempDir) throws Exception {
        Path roomFile = tempDir.resolve("test_room.json");
        Files.writeString(roomFile, validRoomJson(4, 2, "."));

        AtomicReference<RoomWatcher.RoomLoadResult> activeResult = new AtomicReference<>();
        RoomWatcher watcher = new RoomWatcher(roomFile, loadedTilesets, jobs, activeResult::set);

        // Initial load
        Thread.sleep(20);
        Files.writeString(roomFile, validRoomJson(4, 2, "a:one_way_01"));
        Files.setLastModifiedTime(roomFile, FileTime.fromMillis(System.currentTimeMillis() + 1000));
        watcher.checkAndReload();
        Thread.sleep(100);
        jobs.drain();
        assertNotNull(activeResult.get());
        assertEquals(TileShape.ONE_WAY, activeResult.get().grid().getShape(0, 0));

        // Now write an INVALID room (unknown tileset alias)
        Thread.sleep(20);
        String invalidJson = """
            {
              "version": 1,
              "id": "test_room",
              "width": 4,
              "height": 2,
              "tilesets": [{"alias": "a", "id": "test_area"}],
              "layers": {
                "main": [
                  "z:bad_alias . . .",
                  ". . . ."
                ]
              }
            }
            """;
        Files.writeString(roomFile, invalidJson);
        Files.setLastModifiedTime(roomFile, FileTime.fromMillis(System.currentTimeMillis() + 2000));

        RoomWatcher.RoomLoadResult previousResult = activeResult.get();
        watcher.checkAndReload();
        Thread.sleep(100);
        jobs.drain();

        // Must still retain previous result!
        assertSame(previousResult, activeResult.get(), "Invalid file must not overwrite active room");
    }
}
