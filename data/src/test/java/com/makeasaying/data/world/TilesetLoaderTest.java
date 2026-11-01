package com.makeasaying.data.world;

import com.makeasaying.data.tools.TileSheetGenerator;
import com.makeasaying.engine.entity.TileShape;
import com.makeasaying.engine.world.TileDef;
import com.makeasaying.engine.world.TileSheetCoord;
import com.makeasaying.engine.world.Tileset;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class TilesetLoaderTest {
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

    private static Path getAuthoredTilesetPath() {
        return resolveContentPath("content/tilesets/test_area.json");
    }

    private static Path getAuthoredImagePath() {
        return resolveContentPath("content/tilesets/test_area.png");
    }

    @BeforeAll
    static void ensureTileSheetExists() throws IOException {
        Path imagePath = getAuthoredImagePath();
        if (!Files.exists(imagePath)) {
            TileSheetGenerator.generateToFile(imagePath);
        }
        assertTrue(Files.exists(imagePath), "Authored tile sheet PNG must exist");
    }

    @Test
    void testLoadAuthoredTileset() throws IOException {
        Tileset tileset = TilesetLoader.load(getAuthoredTilesetPath());
        assertNotNull(tileset);
        assertEquals("test_area", tileset.id());
        assertEquals("test_area", tileset.area());
        assertEquals(16, tileset.tileSize());
        assertEquals("test_area.png", tileset.image());

        // Check shapes and coords
        TileDef solid = tileset.getTile("solid_01");
        assertNotNull(solid);
        assertEquals(TileShape.SOLID, solid.shape());
        assertEquals("fill", solid.role());
        assertEquals(new TileSheetCoord(0, 0), tileset.getCoord("solid_01"));

        TileDef slopeR = tileset.getTile("slope_r");
        assertNotNull(slopeR);
        assertEquals(TileShape.SLOPE_45_UP_RIGHT, slopeR.shape());
        assertEquals("slope_r45", slopeR.role());
        assertEquals(new TileSheetCoord(1, 0), tileset.getCoord("slope_r"));

        TileDef slopeL = tileset.getTile("slope_l");
        assertNotNull(slopeL);
        assertEquals(TileShape.SLOPE_45_UP_LEFT, slopeL.shape());
        assertEquals("slope_l45", slopeL.role());
        assertEquals(new TileSheetCoord(2, 0), tileset.getCoord("slope_l"));

        TileDef oneWay = tileset.getTile("one_way_01");
        assertNotNull(oneWay);
        assertEquals(TileShape.ONE_WAY, oneWay.shape());
        assertEquals(new TileSheetCoord(3, 0), tileset.getCoord("one_way_01"));
    }

    @Test
    void testRoundTripSerialization(@TempDir Path tempDir) throws IOException {
        Path authored = getAuthoredTilesetPath();
        Tileset tileset = TilesetLoader.load(authored);
        Path savePath = tempDir.resolve("saved_tileset.json");
        TilesetWriter.save(tileset, savePath, 1);

        String original = Files.readString(authored).replace("\r\n", "\n").trim();
        String saved = Files.readString(savePath).replace("\r\n", "\n").trim();

        assertEquals(original, saved, "Round-trip serialization must be byte-identical");
    }

    @Test
    void testRejectUnknownNewerVersion(@TempDir Path tempDir) throws IOException {
        String json = """
            {
              "version": 99,
              "id": "future_tileset",
              "area": "future",
              "tileSize": 16,
              "image": "future.png",
              "tiles": []
            }
            """;
        Path path = tempDir.resolve("future.json");
        Files.writeString(path, json);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> TilesetLoader.load(path));
        assertTrue(ex.getMessage().contains("Unknown newer tileset version"));
    }
}
