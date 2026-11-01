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
}
