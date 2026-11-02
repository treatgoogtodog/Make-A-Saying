package com.makeasaying.engine.entity;

import com.makeasaying.engine.entity.components.BodyComponent;
import com.makeasaying.engine.entity.components.ColliderComponent;
import com.makeasaying.engine.entity.components.ControllerComponent;
import com.makeasaying.engine.entity.components.FacingComponent;
import com.makeasaying.engine.entity.components.JumpStateComponent;
import com.makeasaying.engine.entity.components.PlatformComponent;
import com.makeasaying.engine.entity.components.RoomScopeComponent;
import com.makeasaying.engine.entity.systems.PlatformSystem;
import com.makeasaying.engine.schema.ComponentSchemaRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EntitySpawnerTest {
    private ComponentSchemaRegistry registry;
    private EntitySpawner spawner;

    @BeforeEach
    void setUp() {
        registry = ComponentSchemaRegistry.createDefault();
        spawner = new EntitySpawner(registry);
    }

    @Test
    void testSpawnSetsPreviousPositionAndZeroSubpixelRemainder() {
        World world = new World();
        EntityDefinition def = new EntityDefinition(
            1, "generic", List.of(), "#FFFFFF", true,
            Map.of("body", Map.of("x", 0, "y", 0), "collider", Map.of("width", 16, "height", 16))
        );

        long entity = spawner.spawn(world, def, "generic_1", 123, 456, null);
        BodyComponent b = world.getBody(entity);
        assertNotNull(b);
        assertEquals(123, b.x);
        assertEquals(456, b.y);
        assertEquals(123, b.prevX, "prevX must equal x to prevent first-frame interpolation smear");
        assertEquals(456, b.prevY, "prevY must equal y to prevent first-frame interpolation smear");
        assertEquals(0f, b.subpixelX, "subpixelX must be zeroed");
        assertEquals(0f, b.subpixelY, "subpixelY must be zeroed");
        assertEquals("generic_1", world.getInstanceId(entity));
    }

    @Test
    void testPlayerFactoryStateHashParity() {
        // Hand-built player matching M6a
        World worldA = new World();
        long pA = worldA.createEntity();
        worldA.setBody(pA, new BodyComponent(64, 32));
        worldA.setCollider(pA, new ColliderComponent(12, 36));
        worldA.setFacing(pA, new FacingComponent(1));
        worldA.setController(pA, new ControllerComponent(ControlSource.PLAYER));
        worldA.setJumpState(pA, new JumpStateComponent());
        worldA.setRoomScope(pA, new RoomScopeComponent(RoomScopeComponent.Scope.PERSISTENT_CARRIED));
        worldA.setInstanceId(pA, "player");
        worldA.applyDeferredLifecycle();

        // Spawned player via PlayerFactory thin wrapper
        World worldB = new World();
        long pB = PlayerFactory.createPlayer(spawner, PlayerFactory.DEFAULT_PLAYER_DEF, worldB, 64, 32);

        assertEquals(worldA.stateHash(), worldB.stateHash(), "Spawned player state hash must match M6a hand-built player exactly");
    }

    @Test
    void testPlatformParityBlockModeOver600Ticks() {
        TileGrid grid = new TileGrid(40, 20, 16);
        PlatformSystem platformSystem = new PlatformSystem();

        // 1. Hand-built M4 platform
        World worldA = new World();
        long pA = worldA.createEntity();
        worldA.setPlatform(pA, new PlatformComponent(64, 32, 160, 32, 48, 16, 2.0f, 15, PlatformComponent.CrushMode.BLOCK));
        worldA.setRoomScope(pA, new RoomScopeComponent(RoomScopeComponent.Scope.ROOM_LOCAL));
        worldA.applyDeferredLifecycle();

        // 2. Spawned platform from data definition + overrides (speed: 120 px/s = 2.0 px/tick at 60 Hz)
        World worldB = new World();
        EntityDefinition def = new EntityDefinition(
            1, "moving_platform", List.of(), "#4488FF", true,
            Map.of(
                "body", Map.of("x", 0, "y", 0),
                "collider", Map.of("width", 48, "height", 16),
                "platform", Map.of("travelX", 96, "travelY", 0, "speed", 120.0f, "pauseTicks", 15, "crushMode", "BLOCK"),
                "room_scope", Map.of("scope", "ROOM_LOCAL")
            )
        );
        long pB = spawner.spawn(worldB, def, "plat_1", 64, 32, null);
        worldB.applyDeferredLifecycle();

        for (int tick = 0; tick < 600; tick++) {
            platformSystem.update(worldA, grid);
            platformSystem.update(worldB, grid);

            var platA = worldA.getPlatform(pA);
            var platB = worldB.getPlatform(pB);

            assertEquals(platA.currentX, platB.currentX, "Tick " + tick + ": currentX mismatch");
            assertEquals(platA.currentY, platB.currentY, "Tick " + tick + ": currentY mismatch");
            assertEquals(platA.direction, platB.direction, "Tick " + tick + ": direction mismatch");
            assertEquals(platA.pauseTimer, platB.pauseTimer, "Tick " + tick + ": pauseTimer mismatch");
        }
    }

    @Test
    void testPlatformParityCrushModeOver600Ticks() {
        TileGrid grid = new TileGrid(40, 20, 16);
        PlatformSystem platformSystem = new PlatformSystem();

        World worldA = new World();
        long pA = worldA.createEntity();
        worldA.setPlatform(pA, new PlatformComponent(100, 100, 100, 20, 32, 16, 1.5f, 5, PlatformComponent.CrushMode.CRUSH));
        worldA.setRoomScope(pA, new RoomScopeComponent(RoomScopeComponent.Scope.ROOM_LOCAL));
        worldA.applyDeferredLifecycle();

        World worldB = new World();
        EntityDefinition def = new EntityDefinition(
            1, "moving_platform", List.of(), "#4488FF", true,
            Map.of(
                "body", Map.of("x", 0, "y", 0),
                "collider", Map.of("width", 32, "height", 16),
                "platform", Map.of("travelX", 0, "travelY", -80, "speed", 90.0f, "pauseTicks", 5, "crushMode", "CRUSH"),
                "room_scope", Map.of("scope", "ROOM_LOCAL")
            )
        );
        long pB = spawner.spawn(worldB, def, "plat_crush", 100, 100, null);
        worldB.applyDeferredLifecycle();

        for (int tick = 0; tick < 600; tick++) {
            platformSystem.update(worldA, grid);
            platformSystem.update(worldB, grid);

            var platA = worldA.getPlatform(pA);
            var platB = worldB.getPlatform(pB);

            assertEquals(platA.currentX, platB.currentX, "Tick " + tick + ": currentX mismatch");
            assertEquals(platA.currentY, platB.currentY, "Tick " + tick + ": currentY mismatch");
            assertEquals(platA.direction, platB.direction, "Tick " + tick + ": direction mismatch");
        }
    }
}
