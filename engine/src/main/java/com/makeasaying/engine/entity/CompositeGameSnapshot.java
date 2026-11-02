package com.makeasaying.engine.entity;

import com.makeasaying.engine.flags.FlagStore;
import com.makeasaying.engine.world.RoomStateStore;

/**
 * Composite snapshot capturing the complete simulation and persistent world state per Principle 4.10.
 * Bundles World, FlagStore, and RoomStateStore into a single atomic snapshot for editor Play/Stop,
 * rewind, and determinism verification. Zero per-frame allocation in hot simulation (only created on demand).
 */
public final class CompositeGameSnapshot {
    private final WorldSnapshot worldSnapshot;
    private final FlagStore.Snapshot flagSnapshot;
    private final RoomStateStore roomStateStoreSnapshot;

    public CompositeGameSnapshot(WorldSnapshot worldSnapshot, FlagStore.Snapshot flagSnapshot, RoomStateStore roomStateStoreSnapshot) {
        this.worldSnapshot = worldSnapshot;
        this.flagSnapshot = flagSnapshot;
        this.roomStateStoreSnapshot = roomStateStoreSnapshot != null ? roomStateStoreSnapshot.copy() : null;
    }

    public static CompositeGameSnapshot capture(World world, FlagStore flagStore, RoomStateStore roomStateStore) {
        WorldSnapshot ws = (world != null) ? world.snapshot() : null;
        FlagStore.Snapshot fs = (flagStore != null) ? flagStore.createSnapshot() : null;
        RoomStateStore rs = (roomStateStore != null) ? roomStateStore.copy() : null;
        return new CompositeGameSnapshot(ws, fs, rs);
    }

    public void restore(World world, FlagStore flagStore, RoomStateStore roomStateStore) {
        if (world != null && worldSnapshot != null) {
            world.restore(worldSnapshot);
        }
        if (flagStore != null && flagSnapshot != null) {
            flagStore.restoreSnapshot(flagSnapshot);
        }
        if (roomStateStore != null && roomStateStoreSnapshot != null) {
            roomStateStore.copyFrom(roomStateStoreSnapshot);
        }
    }

    public WorldSnapshot worldSnapshot() {
        return worldSnapshot;
    }

    public FlagStore.Snapshot flagSnapshot() {
        return flagSnapshot;
    }

    public RoomStateStore roomStateStoreSnapshot() {
        return roomStateStoreSnapshot;
    }
}
