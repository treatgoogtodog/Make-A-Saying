package com.makeasaying.engine.world;

import com.makeasaying.engine.entity.TileGrid;
import com.makeasaying.engine.entity.World;
import com.makeasaying.engine.entity.components.BodyComponent;
import com.makeasaying.engine.entity.components.ColliderComponent;
import com.makeasaying.engine.entity.components.JumpStateComponent;

/**
 * Executes atomic room swap operations for carried entities per M5b brief.
 * Despawns room-local entities, resets player kinematics and jump state,
 * recomputes grounded, resets event queue, and increments visited counter.
 */
public final class RoomTransitionHelper {
    private RoomTransitionHelper() {}

    public static void executeRoomSwap(
        World world,
        long playerEntityId,
        Room targetRoom,
        String targetSpawnId,
        TileGrid targetGrid,
        RoomStateStore stateStore
    ) {
        if (world == null || targetRoom == null) return;

        // 1. Despawn room-local entities (stale IDs will fail cleanly)
        world.despawnRoomLocalEntities();

        // 2. Find target spawn point
        SpawnPoint spawn = null;
        for (SpawnPoint sp : targetRoom.spawnPoints()) {
            if (sp.id().equals(targetSpawnId)) {
                spawn = sp;
                break;
            }
        }
        if (spawn == null && !targetRoom.spawnPoints().isEmpty()) {
            spawn = targetRoom.spawnPoints().get(0);
        }

        // 3. Reset player position and kinematic state
        BodyComponent body = world.getBody(playerEntityId);
        if (body != null && spawn != null) {
            body.x = spawn.x();
            body.y = spawn.y();
            body.vx = 0f;
            body.vy = 0f;
            body.subpixelX = 0f;
            body.subpixelY = 0f;
            body.supportId = 0L;

            // Recompute grounded state against target grid
            ColliderComponent collider = world.getCollider(playerEntityId);
            if (targetGrid != null && collider != null) {
                int footY = body.y - 1;
                int minX = body.x + collider.offsetX;
                int maxX = minX + collider.width - 1;
                int footTileY = footY / targetGrid.getTileSize();
                boolean grounded = false;
                for (int x = minX; x <= maxX; x += 4) {
                    int tx = x / targetGrid.getTileSize();
                    if (targetGrid.isSolid(tx, footTileY)) {
                        grounded = true;
                        break;
                    }
                }
                body.grounded = grounded;
            }
        }

        // 4. Reset jump timers
        JumpStateComponent js = world.getJumpState(playerEntityId);
        if (js != null) {
            js.coyoteTimer = 0;
            js.jumpBufferTimer = 0;
            js.dropThroughTimer = 0;
        }

        // 5. Clear event queue
        world.getEventQueue().clear();

        // 6. Increment visited count in RoomStateStore
        if (stateStore != null) {
            stateStore.incrementInt(targetRoom.id(), "visited");
        }
    }
}
