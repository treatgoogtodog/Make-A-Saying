package com.makeasaying.data.world;

import com.makeasaying.engine.world.Room;
import com.makeasaying.engine.world.RoomLayer;
import com.makeasaying.engine.world.RoomLayerType;
import com.makeasaying.engine.world.SpawnPoint;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RoomLoaderTest {
    private static Path resolveContentPath(String relative) {
        Path p = Path.of(relative);
        if (Files.exists(p)) return p;
        p = Path.of("..", relative);
        if (Files.exists(p)) return p;
        if (Files.exists(Path.of("..", "content"))) {
            return Path.of("..", relative);
        }
        return Path.of(relative);
    }

    private static Path getAuthoredRoomPath() {
        return resolveContentPath("content/rooms/test_01.json");
    }

    @BeforeAll
    static void ensureAuthoredRoomExists() throws IOException {
        Path path = getAuthoredRoomPath();
        if (!Files.exists(path)) {
            Room room = createTestRoom();
            RoomWriter.save(room, path);
        }
        assertTrue(Files.exists(path), "Authored room test_01.json must exist");
    }

    public static Room createTestRoom() {
        int width = 40;
        int height = 12;

        Map<String, String> aliases = new LinkedHashMap<>();
        aliases.put("a", "test_area");

        Map<RoomLayerType, RoomLayer> layers = new LinkedHashMap<>();

        // Create background, decoration, foreground as empty layers
        for (RoomLayerType type : RoomLayerType.values()) {
            if (type == RoomLayerType.MAIN) continue;
            String[] tokens = new String[width * height];
            Arrays.fill(tokens, RoomLayer.EMPTY_TOKEN);
            layers.put(type, new RoomLayer(type, width, height, tokens));
        }

        // Create main layer (Y-up coordinates: y=0 is floor, y=11 is ceiling)
        String[] mainTokens = new String[width * height];
        Arrays.fill(mainTokens, RoomLayer.EMPTY_TOKEN);

        // y=0: floor
        for (int x = 0; x < width; x++) {
            mainTokens[0 * width + x] = "a:solid_01";
        }

        // y=11: roof
        for (int x = 0; x < width; x++) {
            mainTokens[11 * width + x] = "a:solid_01";
        }

        // y=1..10: left and right boundary walls
        for (int y = 1; y < 11; y++) {
            mainTokens[y * width + 0] = "a:wall_01";
            mainTokens[y * width + (width - 1)] = "a:wall_01";
        }

        // y=1: slopes and fills
        mainTokens[1 * width + 5] = "a:slope_r";
        mainTokens[1 * width + 6] = "a:fill_01";
        mainTokens[1 * width + 7] = "a:fill_01";
        mainTokens[1 * width + 8] = "a:fill_01";
        mainTokens[1 * width + 12] = "a:fill_01";
        mainTokens[1 * width + 13] = "a:slope_l";

        // y=2: slope up right meeting flat solid and wall (slope seam DEC-013)
        mainTokens[2 * width + 6] = "a:slope_r";
        mainTokens[2 * width + 7] = "a:solid_01";
        mainTokens[2 * width + 8] = "a:wall_01";
        mainTokens[2 * width + 12] = "a:slope_l";

        // y=3: wall continuation and one-way platform 1
        mainTokens[3 * width + 8] = "a:wall_01";
        for (int x = 18; x <= 22; x++) {
            mainTokens[3 * width + x] = "a:one_way_01";
        }

        // y=5: one-way platform 2
        for (int x = 26; x <= 30; x++) {
            mainTokens[5 * width + x] = "a:one_way_01";
        }

        // y=7: ceiling corner correction notch
        for (int x = 18; x <= 20; x++) {
            mainTokens[7 * width + x] = "a:solid_01";
        }
        // x=21 is empty notch '.'
        for (int x = 22; x <= 24; x++) {
            mainTokens[7 * width + x] = "a:solid_01";
        }

        layers.put(RoomLayerType.MAIN, new RoomLayer(RoomLayerType.MAIN, width, height, mainTokens));

        List<SpawnPoint> spawnPoints = List.of(new SpawnPoint("player_start", 32, 16));

        return new Room("test_01", width, height, aliases, layers, spawnPoints, Collections.emptyList());
    }

    @Test
    void testLoadAuthoredRoom() throws IOException {
        Room room = RoomLoader.load(getAuthoredRoomPath());
        assertNotNull(room);
        assertEquals("test_01", room.id());
        assertEquals(40, room.width());
        assertEquals(12, room.height());
        assertEquals("test_area", room.getTilesetIdForAlias("a"));

        RoomLayer main = room.getMainLayer();
        assertNotNull(main);
        assertEquals("a:solid_01", main.getToken(0, 0));
        assertEquals("a:slope_r", main.getToken(5, 1));
        assertEquals("a:solid_01", main.getToken(7, 2));
        assertEquals("a:wall_01", main.getToken(8, 2));

        assertEquals(1, room.spawnPoints().size());
        SpawnPoint sp = room.spawnPoints().get(0);
        assertEquals("player_start", sp.id());
        assertEquals(32, sp.x());
        assertEquals(16, sp.y());
    }

    @Test
    void testYFlipTopBottomCoordinateMapping() throws IOException {
        Room room = RoomLoader.load(getAuthoredRoomPath());
        RoomLayer main = room.getMainLayer();

        // In JSON:
        // Row 0 is the top of the room (y = 11), which is solid roof
        assertEquals("a:solid_01", main.getToken(10, 11), "Top row of JSON must map to y=11");

        // Row 11 is the bottom of the room (y = 0), which is solid floor
        assertEquals("a:solid_01", main.getToken(10, 0), "Bottom row of JSON must map to y=0");

        // Row 10 in JSON is y = 1 (slope_r at col 5)
        assertEquals("a:slope_r", main.getToken(5, 1), "Row 10 of JSON must map to y=1");
    }

    @Test
    void testRoundTripSerialization(@TempDir Path tempDir) throws IOException {
        Path authored = getAuthoredRoomPath();
        Room room = RoomLoader.load(authored);
        Path savePath = tempDir.resolve("saved_room.json");
        RoomWriter.save(room, savePath, 1);

        String original = Files.readString(authored).replace("\r\n", "\n").trim();
        String saved = Files.readString(savePath).replace("\r\n", "\n").trim();

        assertEquals(original, saved, "Round-trip room serialization must be byte-identical");
    }

    @Test
    void testRejectUnknownNewerVersion(@TempDir Path tempDir) throws IOException {
        String json = """
            {
              "version": 99,
              "id": "future_room",
              "width": 10,
              "height": 10
            }
            """;
        Path file = tempDir.resolve("future_room.json");
        Files.writeString(file, json);

        assertThrows(IllegalArgumentException.class, () -> RoomLoader.load(file));
    }
}
