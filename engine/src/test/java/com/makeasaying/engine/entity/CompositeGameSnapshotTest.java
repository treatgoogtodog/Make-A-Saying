package com.makeasaying.engine.entity;

import com.makeasaying.engine.entity.components.BodyComponent;
import com.makeasaying.engine.entity.components.ColliderComponent;
import com.makeasaying.engine.flags.FlagChangeLogEntry;
import com.makeasaying.engine.flags.FlagDefinition;
import com.makeasaying.engine.flags.FlagRegistry;
import com.makeasaying.engine.flags.FlagScope;
import com.makeasaying.engine.flags.FlagStore;
import com.makeasaying.engine.flags.FlagType;
import com.makeasaying.engine.world.RoomStateStore;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class CompositeGameSnapshotTest {

    @Test
    void testCompositeSnapshotRunChangeFlagsRestoreHashEquals() {
        World world = new World();
        long player = world.createEntity();
        world.setBody(player, new BodyComponent(100, 50));
        world.setCollider(player, new ColliderComponent(16, 32));
        world.applyDeferredLifecycle();

        FlagDefinition defStage = new FlagDefinition(0, "test.stage", FlagType.INT, FlagScope.GLOBAL, 0, 0, 10, null, "doc");
        FlagDefinition defDoor = new FlagDefinition(1, "test.door_open", FlagType.BOOL, FlagScope.ROOM, 0, 0, 1, null, "doc");
        FlagRegistry registry = new FlagRegistry(List.of(defStage, defDoor));
        RoomStateStore roomStateStore = new RoomStateStore();
        FlagStore flagStore = new FlagStore(registry, roomStateStore);

        // Compute baseline hashes
        long initialWorldHash = world.stateHash();
        long initialFlagHash = flagStore.computeStateHash("test_01");
        long initialRoomStateHash = roomStateStore.stateHash();

        // 1. Capture snapshot
        CompositeGameSnapshot snapshot = CompositeGameSnapshot.capture(world, flagStore, roomStateStore);

        // 2. Run simulation & mutate state
        BodyComponent body = world.getBody(player);
        body.x = 250;
        body.y = 120;
        body.vx = 4.5f;

        flagStore.set(0, 7, FlagChangeLogEntry.SOURCE_GAMEPLAY);
        flagStore.set(1, 1, FlagChangeLogEntry.SOURCE_GAMEPLAY);
        flagStore.applyPendingChanges("test_01", null, 10L);

        roomStateStore.setInt("test_01", "custom.chest_opened", 1);

        // Verify state has changed
        assertNotEquals(initialWorldHash, world.stateHash());
        assertNotEquals(initialFlagHash, flagStore.computeStateHash("test_01"));
        assertNotEquals(initialRoomStateHash, roomStateStore.stateHash());

        // 3. Restore snapshot
        snapshot.restore(world, flagStore, roomStateStore);

        // 4. Assert bit-for-bit hash equality
        assertEquals(initialWorldHash, world.stateHash(), "World state hash must match initial snapshot bit-for-bit");
        assertEquals(initialFlagHash, flagStore.computeStateHash("test_01"), "FlagStore state hash must match initial snapshot bit-for-bit");
        assertEquals(initialRoomStateHash, roomStateStore.stateHash(), "RoomStateStore state hash must match initial snapshot bit-for-bit");

        BodyComponent restoredBody = world.getBody(player);
        assertEquals(100, restoredBody.x);
        assertEquals(50, restoredBody.y);
        assertEquals(0, flagStore.get(0, "test_01"));
        assertEquals(0, flagStore.get(1, "test_01"));
    }
}
