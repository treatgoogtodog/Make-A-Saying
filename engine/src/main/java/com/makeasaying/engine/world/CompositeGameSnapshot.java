package com.makeasaying.engine.world;

import com.makeasaying.engine.entity.World;
import com.makeasaying.engine.entity.WorldSnapshot;
import com.makeasaying.engine.flags.FlagStore;

import java.util.Objects;

/**
 * Composite game snapshot holding world entity state, flags, room state store,
 * transition manager state, and simulation tick per M7a brief.
 * Restored on editor Stop (reverting in-memory play state cleanly).
 */
public final class CompositeGameSnapshot {
    private final WorldSnapshot worldSnapshot;
    private final FlagStore.Snapshot flagSnapshot;
    private final RoomStateStore roomStateStoreSnapshot;
    private final RoomTransitionState transitionState;
    private final int transitionTick;
    private final String pendingExitId;
    private final long simulationTick;
    private final String activeRoomId;

    public CompositeGameSnapshot(
        WorldSnapshot worldSnapshot,
        FlagStore.Snapshot flagSnapshot,
        RoomStateStore roomStateStoreSnapshot,
        RoomTransitionState transitionState,
        int transitionTick,
        String pendingExitId,
        long simulationTick,
        String activeRoomId
    ) {
        this.worldSnapshot = Objects.requireNonNull(worldSnapshot, "worldSnapshot cannot be null");
        this.flagSnapshot = flagSnapshot;
        this.roomStateStoreSnapshot = roomStateStoreSnapshot != null ? roomStateStoreSnapshot.copy() : new RoomStateStore();
        this.transitionState = transitionState != null ? transitionState : RoomTransitionState.IDLE;
        this.transitionTick = transitionTick;
        this.pendingExitId = pendingExitId;
        this.simulationTick = simulationTick;
        this.activeRoomId = activeRoomId;
    }

    public static CompositeGameSnapshot capture(
        World world,
        FlagStore flagStore,
        RoomStateStore roomStateStore,
        RoomTransitionManager transitionManager,
        long simulationTick,
        String activeRoomId
    ) {
        WorldSnapshot ws = world != null ? world.snapshot() : null;
        FlagStore.Snapshot fs = flagStore != null ? flagStore.createSnapshot() : null;
        RoomStateStore rss = roomStateStore != null ? roomStateStore.copy() : new RoomStateStore();

        RoomTransitionState ts = transitionManager != null ? transitionManager.getState() : RoomTransitionState.IDLE;
        int tt = transitionManager != null ? transitionManager.getTransitionTick() : 0;
        String peId = (transitionManager != null && transitionManager.getPendingExit() != null)
            ? transitionManager.getPendingExit().id() : null;

        return new CompositeGameSnapshot(ws, fs, rss, ts, tt, peId, simulationTick, activeRoomId);
    }

    public void restore(
        World world,
        FlagStore flagStore,
        RoomStateStore roomStateStore,
        RoomTransitionManager transitionManager
    ) {
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

    public RoomTransitionState transitionState() {
        return transitionState;
    }

    public int transitionTick() {
        return transitionTick;
    }

    public String pendingExitId() {
        return pendingExitId;
    }

    public long simulationTick() {
        return simulationTick;
    }

    public String activeRoomId() {
        return activeRoomId;
    }
}
