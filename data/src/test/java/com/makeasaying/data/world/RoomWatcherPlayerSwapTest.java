package com.makeasaying.data.world;

import com.makeasaying.engine.async.Jobs;
import com.makeasaying.engine.entity.ControlSource;
import com.makeasaying.engine.entity.MovementTuning;
import com.makeasaying.engine.entity.TileGrid;
import com.makeasaying.engine.entity.TileShape;
import com.makeasaying.engine.entity.World;
import com.makeasaying.engine.entity.components.BodyComponent;
import com.makeasaying.engine.entity.components.ColliderComponent;
import com.makeasaying.engine.entity.components.ControllerComponent;
import com.makeasaying.engine.entity.components.FacingComponent;
import com.makeasaying.engine.entity.components.InputStateComponent;
import com.makeasaying.engine.entity.components.JumpStateComponent;
import com.makeasaying.engine.entity.systems.MovementSystem;
import com.makeasaying.engine.entity.systems.PlayerControlSystem;
import com.makeasaying.engine.input.ActionDefinition;
import com.makeasaying.engine.input.ActionRegistry;
import com.makeasaying.engine.input.Actions;
import com.makeasaying.engine.input.InputState;
import com.makeasaying.engine.world.Room;
import com.makeasaying.engine.world.TileDef;
import com.makeasaying.engine.world.Tileset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class RoomWatcherPlayerSwapTest {
    private Jobs jobs;
    private Map<String, Tileset> loadedTilesets;

    @BeforeEach
    void setUp() {
        jobs = new Jobs(2);
        loadedTilesets = new LinkedHashMap<>();

        List<TileDef> tiles = List.of(
            new TileDef("solid_01", TileShape.SOLID, "fill", Collections.emptyList()),
            new TileDef("plat_01", TileShape.SOLID, "platform", Collections.emptyList())
        );
        Tileset ts = new Tileset("test_area", "test_area", 16, "test_area.png", tiles, Collections.emptyMap());
        loadedTilesets.put("test_area", ts);
    }

    @AfterEach
    void tearDown() {
        jobs.shutdown();
    }

    @Test
    void testPlayerInsideSolidAfterHotReloadTriggersStuckBodyRule(@TempDir Path tempDir) throws Exception {
        // Room 4x4 tiles: y=0 is solid floor, y=1..3 are empty. Spawn point at (16, 16)
        String emptyRoomJson = """
            {
              "version": 1,
              "id": "swap_room",
              "width": 4,
              "height": 4,
              "tilesets": [{"alias": "a", "id": "test_area"}],
              "layers": {
                "main": [
                  ". . . .",
                  ". . . .",
                  ". . . .",
                  "a:solid_01 a:solid_01 a:solid_01 a:solid_01"
                ]
              },
              "spawnPoints": [{"id": "start", "x": 16, "y": 16}],
              "entities": []
            }
            """;

        Path roomFile = tempDir.resolve("swap_room.json");
        Files.writeString(roomFile, emptyRoomJson);

        AtomicReference<RoomWatcher.RoomLoadResult> activeResult = new AtomicReference<>();
        RoomWatcher watcher = new RoomWatcher(roomFile, loadedTilesets, jobs, activeResult::set);

        // Load initial room
        Room room = RoomLoader.load(roomFile);
        TileGrid grid = RoomGridBuilder.build(room, loadedTilesets, 16);

        ActionRegistry registry = new ActionRegistry();
        registry.register(ActionDefinition.button(Actions.MOVE_RIGHT));
        registry.register(ActionDefinition.axis(Actions.MOVE_X, Actions.MOVE_LEFT, Actions.MOVE_RIGHT));

        MovementTuning tuning = MovementTuning.defaultTuning(60);
        World world = new World();
        PlayerControlSystem controlSystem = new PlayerControlSystem();
        MovementSystem movementSystem = new MovementSystem(tuning);

        // Player placed at (32, 16) - grounded on floor at y=16 (tile 2, 1)
        long playerId = world.createEntity();
        world.setBody(playerId, new BodyComponent(32, 16));
        world.setCollider(playerId, new ColliderComponent(12, 36));
        world.setFacing(playerId, new FacingComponent(1));
        world.setController(playerId, new ControllerComponent(ControlSource.PLAYER));
        world.setJumpState(playerId, new JumpStateComponent());
        world.setInputState(playerId, new InputStateComponent(new InputState(registry)));
        world.applyDeferredLifecycle();

        // 1 tick of normal simulation: player stands on floor
        InputState input = world.getInputState(playerId).state;
        input.beginTick();
        controlSystem.update(world, input);
        movementSystem.update(world, grid);
        assertTrue(world.getBody(playerId).grounded);

        // Now HOT-RELOAD changes tile (2, 1) where player's feet stand into SOLID!
        // In JSON: y=1 is row 2 (from top: row 0 is y=3, row 1 is y=2, row 2 is y=1, row 3 is y=0)
        String solidSwapJson = """
            {
              "version": 1,
              "id": "swap_room",
              "width": 4,
              "height": 4,
              "tilesets": [{"alias": "a", "id": "test_area"}],
              "layers": {
                "main": [
                  ". . . .",
                  ". . . .",
                  ". . a:solid_01 .",
                  "a:solid_01 a:solid_01 a:solid_01 a:solid_01"
                ]
              },
              "spawnPoints": [{"id": "start", "x": 16, "y": 16}],
              "entities": []
            }
            """;
        Thread.sleep(20);
        Files.writeString(roomFile, solidSwapJson);
        Files.setLastModifiedTime(roomFile, FileTime.fromMillis(System.currentTimeMillis() + 1000));

        watcher.checkAndReload();
        Thread.sleep(100);
        jobs.drain();

        assertNotNull(activeResult.get());
        TileGrid updatedGrid = activeResult.get().grid();
        // Tile at (2, 1) is now solid
        assertTrue(updatedGrid.isSolid(32, 16));

        // Now step simulation with updated grid:
        // Player attempts to walk right while stuck in newly solid tile
        input.beginTick();
        input.setButtonDown(Actions.MOVE_RIGHT, true);
        controlSystem.update(world, input);
        movementSystem.update(world, updatedGrid);

        BodyComponent body = world.getBody(playerId);
        // Stuck body rule: position does NOT move (movement loop skipped), no tunneling
        assertEquals(32, body.x, "Stuck player must not move horizontally");
        assertEquals(16, body.y, "Stuck player must not move vertically");
    }
}
