package com.makeasaying.data.world;

import com.makeasaying.engine.entity.TileGrid;
import com.makeasaying.engine.entity.TileShape;
import com.makeasaying.engine.world.Room;
import com.makeasaying.engine.world.RoomLayer;
import com.makeasaying.engine.world.RoomLayerType;
import com.makeasaying.engine.world.TileDef;
import com.makeasaying.engine.world.Tileset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RoomGridBuilderTest {
    private Map<String, Tileset> loadedTilesets;

    @BeforeEach
    void setUp() {
        loadedTilesets = new LinkedHashMap<>();

        List<TileDef> tiles = List.of(
            new TileDef("solid_01", TileShape.SOLID, "fill", Collections.emptyList()),
            new TileDef("slope_r", TileShape.SLOPE_45_UP_RIGHT, "slope_r45", Collections.emptyList()),
            new TileDef("slope_l", TileShape.SLOPE_45_UP_LEFT, "slope_l45", Collections.emptyList()),
            new TileDef("one_way_01", TileShape.ONE_WAY, "platform", Collections.emptyList())
        );

        Tileset testTs = new Tileset("test_area", "test_area", 16, "test_area.png", tiles, Collections.emptyMap());
        loadedTilesets.put("test_area", testTs);
    }

    @Test
    void testAllFiveShapesMappedCorrectly() {
        int w = 5;
        int h = 1;
        String[] tokens = new String[] {
            ".", "a:solid_01", "a:one_way_01", "a:slope_r", "a:slope_l"
        };

        Map<RoomLayerType, RoomLayer> layers = Map.of(
            RoomLayerType.MAIN, new RoomLayer(RoomLayerType.MAIN, w, h, tokens)
        );
        Room room = new Room("test_shapes", w, h, Map.of("a", "test_area"), layers,
            Collections.emptyList(), Collections.emptyList());

        TileGrid grid = RoomGridBuilder.build(room, loadedTilesets, 16);
        assertEquals(TileShape.EMPTY, grid.getShape(0, 0));
        assertEquals(TileShape.SOLID, grid.getShape(1, 0));
        assertEquals(TileShape.ONE_WAY, grid.getShape(2, 0));
        assertEquals(TileShape.SLOPE_45_UP_RIGHT, grid.getShape(3, 0));
        assertEquals(TileShape.SLOPE_45_UP_LEFT, grid.getShape(4, 0));
    }

    @Test
    void testPlaceholderFallbackToSolidForMissingTile() {
        int w = 2;
        int h = 1;
        String[] tokens = new String[] {
            ".", "a:missing_tile_id"
        };

        Map<RoomLayerType, RoomLayer> layers = Map.of(
            RoomLayerType.MAIN, new RoomLayer(RoomLayerType.MAIN, w, h, tokens)
        );
        Room room = new Room("test_fallback_tile", w, h, Map.of("a", "test_area"), layers,
            Collections.emptyList(), Collections.emptyList());

        TileGrid grid = RoomGridBuilder.build(room, loadedTilesets, 16);
        assertEquals(TileShape.EMPTY, grid.getShape(0, 0));
        assertEquals(TileShape.SOLID, grid.getShape(1, 0), "Missing tile must fall back to SOLID");
    }

    @Test
    void testPlaceholderFallbackToSolidForMissingTileset() {
        int w = 2;
        int h = 1;
        String[] tokens = new String[] {
            ".", "b:solid_01" // alias 'b' not in room
        };

        Map<RoomLayerType, RoomLayer> layers = Map.of(
            RoomLayerType.MAIN, new RoomLayer(RoomLayerType.MAIN, w, h, tokens)
        );
        Room room = new Room("test_fallback_ts", w, h, Map.of("a", "test_area"), layers,
            Collections.emptyList(), Collections.emptyList());

        TileGrid grid = RoomGridBuilder.build(room, loadedTilesets, 16);
        assertEquals(TileShape.SOLID, grid.getShape(1, 0), "Missing tileset must fall back to SOLID");
    }

    @Test
    void testMissingMainLayerReturnsEmptyGrid() {
        Room room = new Room("test_no_main", 4, 4, Map.of("a", "test_area"), Collections.emptyMap(),
            Collections.emptyList(), Collections.emptyList());

        TileGrid grid = RoomGridBuilder.build(room, loadedTilesets, 16);
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                assertEquals(TileShape.EMPTY, grid.getShape(x, y));
            }
        }
    }
}
