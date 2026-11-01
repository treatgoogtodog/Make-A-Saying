package com.makeasaying.engine.world;

import com.makeasaying.engine.entity.TileGrid;
import com.makeasaying.engine.entity.TileShape;
import com.makeasaying.engine.entity.World;
import com.makeasaying.engine.entity.components.BodyComponent;
import com.makeasaying.engine.entity.components.ColliderComponent;
import com.makeasaying.engine.entity.components.JumpStateComponent;
import com.makeasaying.engine.entity.components.RoomScopeComponent;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CarriedEntityTest {
    @Test
    void testCarriedEntityPreservedAndLocalEntitiesDespawnedWithCleanStaleIds() {
        World world = new World();
        RoomStateStore store = new RoomStateStore();

        // 1. Create player (PERSISTENT_CARRIED)
        long player = world.createEntity();
        BodyComponent playerBody = new BodyComponent(100, 50);
        playerBody.vx = 120.0f;
        playerBody.vy = -80.0f;
        playerBody.supportId = 42L;
        playerBody.grounded = false;
        world.setBody(player, playerBody);
        world.setCollider(player, new ColliderComponent(12, 36));
        JumpStateComponent jumpState = new JumpStateComponent();
        jumpState.coyoteTimer = 5;
        jumpState.jumpBufferTimer = 3;
        jumpState.dropThroughTimer = 2;
        world.setJumpState(player, jumpState);
        world.setRoomScope(player, new RoomScopeComponent(RoomScopeComponent.Scope.PERSISTENT_CARRIED));

        // 2. Create room-local entities
        long enemy = world.createEntity();
        world.setBody(enemy, new BodyComponent(200, 16));
        world.setCollider(enemy, new ColliderComponent(16, 16));
        world.setRoomScope(enemy, new RoomScopeComponent(RoomScopeComponent.Scope.ROOM_LOCAL));

        long prop = world.createEntity();
        world.setBody(prop, new BodyComponent(300, 16));
        // prop has no RoomScopeComponent (defaults to room-local)

        world.applyDeferredLifecycle();

        assertTrue(world.isAlive(player));
        assertTrue(world.isAlive(enemy));
        assertTrue(world.isAlive(prop));

        // 3. Create target room with solid floor at y=0 (pixels 0..15)
        Room targetRoom = new Room("target_room", 30, 12, Map.of(), Map.of(),
            List.of(new SpawnPoint("spawn_target", 48, 16)),
            Collections.emptyList(),
            Collections.emptyList()
        );
        TileGrid targetGrid = new TileGrid(30, 12, 16);
        for (int x = 0; x < 30; x++) {
            targetGrid.setTile(x, 0, TileShape.SOLID);
        }

        // 4. Perform room swap
        RoomTransitionHelper.executeRoomSwap(world, player, targetRoom, "spawn_target", targetGrid, store);

        // 5. Verify player state
        assertTrue(world.isAlive(player), "Player must remain alive after swap");
        BodyComponent updatedBody = world.getBody(player);
        assertNotNull(updatedBody);
        assertEquals(48, updatedBody.x, "Player x must match target spawn");
        assertEquals(16, updatedBody.y, "Player y must match target spawn");
        assertEquals(0f, updatedBody.vx, "Horizontal velocity must be zeroed");
        assertEquals(0f, updatedBody.vy, "Vertical velocity must be zeroed");
        assertEquals(0L, updatedBody.supportId, "Support ID must be cleared");
        assertTrue(updatedBody.grounded, "Grounded state must be recomputed true when placed on solid floor");

        JumpStateComponent updatedJs = world.getJumpState(player);
        assertNotNull(updatedJs);
        assertEquals(0, updatedJs.coyoteTimer, "Coyote timer must be cleared");
        assertEquals(0, updatedJs.jumpBufferTimer, "Jump buffer timer must be cleared");
        assertEquals(0, updatedJs.dropThroughTimer, "Drop-through timer must be cleared");

        // 6. Verify room-local entities are despawned and stale IDs fail cleanly
        assertFalse(world.isAlive(enemy), "Enemy must be despawned");
        assertFalse(world.isAlive(prop), "Prop must be despawned");

        // Clean stale ID access: returns null, does not crash or throw
        assertNull(world.getBody(enemy));
        assertNull(world.getCollider(enemy));
        assertNull(world.getBody(prop));

        // 7. Verify visited count incremented
        assertEquals(1, store.getInt("target_room", "visited", 0));
    }
}
