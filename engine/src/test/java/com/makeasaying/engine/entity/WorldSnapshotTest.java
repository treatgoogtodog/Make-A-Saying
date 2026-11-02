package com.makeasaying.engine.entity;

import com.makeasaying.engine.entity.components.BodyComponent;
import com.makeasaying.engine.entity.components.PlatformComponent;
import com.makeasaying.engine.entity.components.RoomScopeComponent;
import com.makeasaying.engine.entity.systems.PlatformSystem;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class WorldSnapshotTest {

    @Test
    public void testWorldSnapshotAndRestoreDeterminism() {
        World world = new World();
        PlatformSystem platformSystem = new PlatformSystem();
        TileGrid grid = new TileGrid(20, 20, 16);

        // Spawn player
        long playerId = PlayerFactory.createPlayer(world, 100, 100);

        // Spawn platforms
        long p1 = world.createEntity();
        world.setPlatform(p1, new PlatformComponent(50, 50, 150, 50, 32, 8, 2.0f, 5, PlatformComponent.CrushMode.BLOCK));
        world.setRoomScope(p1, new RoomScopeComponent(RoomScopeComponent.Scope.ROOM_LOCAL));

        long p2 = world.createEntity();
        world.setPlatform(p2, new PlatformComponent(200, 20, 200, 80, 24, 8, 1.5f, 0, PlatformComponent.CrushMode.CRUSH));
        world.setRoomScope(p2, new RoomScopeComponent(RoomScopeComponent.Scope.ROOM_LOCAL));

        // Advance 10 ticks
        for (int i = 0; i < 10; i++) {
            platformSystem.update(world, grid);
        }

        // Take snapshot
        WorldSnapshot snapshot = world.snapshot();
        long snapshotHash = world.stateHash();

        // Advance 30 ticks
        for (int i = 0; i < 30; i++) {
            platformSystem.update(world, grid);
        }
        long progressedHash = world.stateHash();
        assertNotEquals(snapshotHash, progressedHash, "State hash must change as simulation advances");

        // Restore snapshot
        world.restore(snapshot);
        assertEquals(snapshotHash, world.stateHash(), "Restored state hash must match snapshot hash exactly");

        // Advance 30 ticks again from restored state
        for (int i = 0; i < 30; i++) {
            platformSystem.update(world, grid);
        }
        assertEquals(progressedHash, world.stateHash(), "State after re-simulating from restored snapshot must be identical");
    }

    @Test
    public void testSnapshotDeterminismWithStaleIdsAndFreeListReuse() {
        World world = new World();
        PlatformSystem platformSystem = new PlatformSystem();
        TileGrid grid = new TileGrid(30, 20, 16);

        // 1. Populate world
        long e1 = world.createEntity();
        world.setBody(e1, new BodyComponent(10, 10));
        world.setCollider(e1, new com.makeasaying.engine.entity.components.ColliderComponent(12, 36));

        long e2 = world.createEntity();
        world.setBody(e2, new BodyComponent(50, 50));
        world.setCollider(e2, new com.makeasaying.engine.entity.components.ColliderComponent(48, 16));
        world.setPlatform(e2, new PlatformComponent(50, 50, 100, 50, 48, 16, 1.0f, 0, PlatformComponent.CrushMode.BLOCK));

        long e3 = world.createEntity();
        world.setBody(e3, new BodyComponent(200, 20));

        // 2. Destroy e2 and flush to generate free list entries and stale IDs
        world.destroyEntity(e2);
        world.applyDeferredLifecycle();
        assertFalse(world.isAlive(e2));

        // 3. Create new entity reusing recycled slot
        long recycled = world.createEntity();
        world.setBody(recycled, new BodyComponent(80, 80));
        world.setPlatform(recycled, new PlatformComponent(80, 80, 120, 80, 32, 16, 2.0f, 0, PlatformComponent.CrushMode.CRUSH));
        world.applyDeferredLifecycle();

        // 4. Stale ID e2 must remain dead
        assertFalse(world.isAlive(e2));
        assertNull(world.getBody(e2));

        // 5. Take snapshot
        long h0 = world.stateHash();
        WorldSnapshot snapshot = world.snapshot();

        // 6. Run 50 ticks
        for (int i = 0; i < 50; i++) {
            platformSystem.update(world, grid);
        }
        long h1 = world.stateHash();
        assertNotEquals(h0, h1, "Simulation must advance state");

        // 7. Restore snapshot and assert exact hash match
        world.restore(snapshot);
        assertEquals(h0, world.stateHash(), "Restored hash must match initial snapshot hash");
        assertFalse(world.isAlive(e2), "Stale ID e2 must remain dead after restore");
        assertNull(world.getBody(e2), "Stale ID must not have body component after restore");

        // 8. Re-simulate identical 50 ticks and assert exact determinism
        for (int i = 0; i < 50; i++) {
            platformSystem.update(world, grid);
        }
        long h2 = world.stateHash();
        assertEquals(h1, h2, "Replaying identical ticks from restored snapshot must yield identical state hash");
    }
}
