package com.makeasaying.data.world;

import com.makeasaying.engine.async.Jobs;
import com.makeasaying.engine.entity.ControlSource;
import com.makeasaying.engine.entity.EntityDefinition;
import com.makeasaying.engine.entity.EntitySpawner;
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
import com.makeasaying.engine.entity.components.PlatformComponent;
import com.makeasaying.engine.entity.components.RoomScopeComponent;
import com.makeasaying.engine.entity.systems.MovementSystem;
import com.makeasaying.engine.entity.systems.PlatformSystem;
import com.makeasaying.engine.entity.systems.PlayerControlSystem;
import com.makeasaying.engine.input.ActionDefinition;
import com.makeasaying.engine.input.ActionRegistry;
import com.makeasaying.engine.input.Actions;
import com.makeasaying.engine.input.InputState;
import com.makeasaying.engine.schema.ComponentSchemaRegistry;
import com.makeasaying.engine.world.Room;
import com.makeasaying.engine.world.RoomEntityData;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameModeRoomHotReloadTest {
    private Jobs jobs;
    private Map<String, Tileset> loadedTilesets;
    private ComponentSchemaRegistry schemaRegistry;
    private EntitySpawner spawner;
    private Map<String, EntityDefinition> definitions;

    @BeforeEach
    void setUp() {
        jobs = new Jobs(2);
        loadedTilesets = new LinkedHashMap<>();
        schemaRegistry = ComponentSchemaRegistry.createDefault();
        spawner = new EntitySpawner(schemaRegistry);

        List<TileDef> tiles = List.of(
            new TileDef("solid_01", TileShape.SOLID, "fill", Collections.emptyList())
        );
        Tileset ts = new Tileset("test_area", "test_area", 16, "test_area.png", tiles, Collections.emptyMap());
        loadedTilesets.put("test_area", ts);

        EntityDefinition platDef = new EntityDefinition(
            1, "moving_platform", List.of("platform"), "#4488FF", true,
            Map.of(
                "body", Map.of("x", 0, "y", 0),
                "collider", Map.of("width", 32, "height", 8),
                "platform", Map.of("travelX", 64, "travelY", 0, "speed", 60.0f, "pauseTicks", 15, "crushMode", "BLOCK"),
                "room_scope", Map.of("scope", "ROOM_LOCAL")
            )
        );
        definitions = Map.of("moving_platform", platDef);
    }

    @AfterEach
    void tearDown() {
        jobs.shutdown();
    }

    @Test
    void testGameModeRoomHotReloadRespawnLocalEntitiesAndKeepsPlayerPositionWithStuckBody(@TempDir Path tempDir) throws Exception {
        String v3RoomJson = """
            {
              "version": 3,
              "id": "reload_room",
              "width": 10,
              "height": 6,
              "entitySeq": 2,
              "tilesets": [{"alias": "a", "id": "test_area"}],
              "layers": {
                "main": [
                  ". . . . . . . . . .",
                  ". . . . . . . . . .",
                  ". . . . . . . . . .",
                  ". . . . . . . . . .",
                  ". . . . . . . . . .",
                  "a:solid_01 a:solid_01 a:solid_01 a:solid_01 a:solid_01 a:solid_01 a:solid_01 a:solid_01 a:solid_01 a:solid_01"
                ]
              },
              "spawnPoints": [{"id": "start", "x": 32, "y": 16}],
              "entities": [
                {
                  "instanceId": "plat_1",
                  "definition": "moving_platform",
                  "x": 64,
                  "y": 32,
                  "overrides": {
                    "platform": {
                      "travelX": 32,
                      "travelY": 0
                    }
                  }
                }
              ],
              "exits": []
            }
            """;

        Path roomFile = tempDir.resolve("reload_room.json");
        Files.writeString(roomFile, v3RoomJson);

        AtomicReference<RoomWatcher.RoomLoadResult> activeResult = new AtomicReference<>();
        RoomWatcher watcher = new RoomWatcher(roomFile, loadedTilesets, jobs, activeResult::set);

        Room room = RoomLoader.load(roomFile);
        TileGrid grid = RoomGridBuilder.build(room, loadedTilesets, 16);

        ActionRegistry registry = new ActionRegistry();
        registry.register(ActionDefinition.button(Actions.MOVE_RIGHT));
        registry.register(ActionDefinition.axis(Actions.MOVE_X, Actions.MOVE_LEFT, Actions.MOVE_RIGHT));

        MovementTuning tuning = MovementTuning.defaultTuning(60);
        World world = new World();
        PlayerControlSystem controlSystem = new PlayerControlSystem();
        MovementSystem movementSystem = new MovementSystem(tuning);
        PlatformSystem platformSystem = new PlatformSystem();

        // 1. Spawn carried player at (48, 16)
        long playerId = world.createEntity();
        world.setBody(playerId, new BodyComponent(48, 16));
        world.setCollider(playerId, new ColliderComponent(12, 36));
        world.setFacing(playerId, new FacingComponent(1));
        world.setController(playerId, new ControllerComponent(ControlSource.PLAYER));
        world.setJumpState(playerId, new JumpStateComponent());
        world.setInputState(playerId, new InputStateComponent(new InputState(registry)));
        world.setRoomScope(playerId, new RoomScopeComponent(RoomScopeComponent.Scope.PERSISTENT_CARRIED));

        // 2. Spawn initial room-local platform from room data
        long initialPlatId = -1L;
        for (RoomEntityData ed : room.entities()) {
            EntityDefinition def = definitions.get(ed.definition());
            long eid = spawner.spawn(world, def, ed.instanceId(), ed.x(), ed.y(), ed.overrides());
            if ("plat_1".equals(ed.instanceId())) {
                initialPlatId = eid;
            }
        }
        world.applyDeferredLifecycle();

        // Step 10 ticks: platform moves forward
        for (int i = 0; i < 10; i++) {
            platformSystem.update(world, grid);
        }
        assertTrue(world.isAlive(initialPlatId));
        PlatformComponent platComp = world.getPlatform(initialPlatId);
        assertTrue(platComp.currentX > 64, "Platform should have advanced from its start position");

        // 3. Hot reload room with updated platform (new travel distance) and solid terrain where player stands at tile (3, 1) -> (48, 16)
        // Spawn point stays at (16, 16) tile (1, 1) which is empty.
        String modifiedRoomJson = """
            {
              "version": 3,
              "id": "reload_room",
              "width": 10,
              "height": 6,
              "entitySeq": 3,
              "tilesets": [{"alias": "a", "id": "test_area"}],
              "layers": {
                "main": [
                  ". . . . . . . . . .",
                  ". . . . . . . . . .",
                  ". . . . . . . . . .",
                  ". . . . . . . . . .",
                  ". . . a:solid_01 . . . . . .",
                  "a:solid_01 a:solid_01 a:solid_01 a:solid_01 a:solid_01 a:solid_01 a:solid_01 a:solid_01 a:solid_01 a:solid_01"
                ]
              },
              "spawnPoints": [{"id": "start", "x": 16, "y": 16}],
              "entities": [
                {
                  "instanceId": "plat_1",
                  "definition": "moving_platform",
                  "x": 80,
                  "y": 32,
                  "overrides": {
                    "platform": {
                      "travelX": 48,
                      "travelY": 0
                    }
                  }
                }
              ],
              "exits": []
            }
            """;
        Thread.sleep(20);
        Files.writeString(roomFile, modifiedRoomJson);
        Files.setLastModifiedTime(roomFile, FileTime.fromMillis(System.currentTimeMillis() + 1000));

        watcher.checkAndReload();
        long waitStart = System.currentTimeMillis();
        while (System.currentTimeMillis() - waitStart < 3000) {
            jobs.drain();
            if (activeResult.get() != null) break;
            Thread.sleep(10);
        }

        assertNotNull(activeResult.get());
        Room reloadedRoom = activeResult.get().room();
        TileGrid updatedGrid = activeResult.get().grid();

        // 4. Perform room-local entity reconciliation (DEC-026 & DEC-029)
        // Despawn ROOM_LOCAL entities
        world.despawnRoomLocalEntities();
        world.applyDeferredLifecycle();
        assertFalse(world.isAlive(initialPlatId), "Old platform instance should be despawned");

        // Respawn ROOM_LOCAL entities from reloaded room
        long newPlatId = -1L;
        for (RoomEntityData ed : reloadedRoom.entities()) {
            EntityDefinition def = definitions.get(ed.definition());
            long eid = spawner.spawn(world, def, ed.instanceId(), ed.x(), ed.y(), ed.overrides());
            if ("plat_1".equals(ed.instanceId())) {
                newPlatId = eid;
            }
        }
        world.applyDeferredLifecycle();

        assertTrue(world.isAlive(newPlatId));
        PlatformComponent newPlatComp = world.getPlatform(newPlatId);
        assertEquals(80, newPlatComp.startX, "New platform respawns with updated initial position from data");
        assertEquals(80, newPlatComp.currentX, "New platform reset to start");

        // Carried player keeps position at (48, 16)
        BodyComponent playerBody = world.getBody(playerId);
        assertEquals(48, playerBody.x, "Carried player must retain position");
        assertEquals(16, playerBody.y, "Carried player must retain position");

        // Stuck-body rule: Player attempts to walk while stuck inside the newly placed solid terrain at (48, 16)
        InputState input = world.getInputState(playerId).state;
        input.beginTick();
        input.setButtonDown(Actions.MOVE_RIGHT, true);
        controlSystem.update(world, input);
        movementSystem.update(world, updatedGrid);

        assertEquals(48, playerBody.x, "Stuck player movement must be skipped without pushing or tunneling");
        assertEquals(16, playerBody.y, "Stuck player movement must be skipped without pushing or tunneling");
    }
}
