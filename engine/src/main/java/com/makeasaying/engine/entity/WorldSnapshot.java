package com.makeasaying.engine.entity;

import com.makeasaying.engine.entity.components.BodyComponent;
import com.makeasaying.engine.entity.components.ColliderComponent;
import com.makeasaying.engine.entity.components.ControllerComponent;
import com.makeasaying.engine.entity.components.FacingComponent;
import com.makeasaying.engine.entity.components.InputStateComponent;
import com.makeasaying.engine.entity.components.JumpStateComponent;
import com.makeasaying.engine.entity.components.PlatformComponent;
import com.makeasaying.engine.entity.components.RoomScopeComponent;
import com.makeasaying.engine.entity.components.SavepointComponent;

/**
 * Deep-copied snapshot of World state per AGENTS.md section 4.10 and M6a brief.
 * Allows editor Play/Stop to simulate on in-memory world and restore cleanly on Stop.
 */
public final class WorldSnapshot {
    final int[] generations;
    final boolean[] alive;
    final int[] freeIndices;
    final int freeCount;

    final long[] deferredSpawns;
    final int deferredSpawnsCount;
    final long[] deferredDespawns;
    final int deferredDespawnsCount;

    final BodyComponent[] bodies;
    final ColliderComponent[] colliders;
    final FacingComponent[] facings;
    final ControllerComponent[] controllers;
    final InputStateComponent[] inputStates;
    final JumpStateComponent[] jumpStates;
    final PlatformComponent[] platforms;
    final RoomScopeComponent[] roomScopes;
    final SavepointComponent[] savepoints;
    final String[] instanceIds;

    WorldSnapshot(
        int[] generations,
        boolean[] alive,
        int[] freeIndices,
        int freeCount,
        long[] deferredSpawns,
        int deferredSpawnsCount,
        long[] deferredDespawns,
        int deferredDespawnsCount,
        BodyComponent[] bodies,
        ColliderComponent[] colliders,
        FacingComponent[] facings,
        ControllerComponent[] controllers,
        InputStateComponent[] inputStates,
        JumpStateComponent[] jumpStates,
        PlatformComponent[] platforms,
        RoomScopeComponent[] roomScopes,
        SavepointComponent[] savepoints,
        String[] instanceIds
    ) {
        this.generations = generations;
        this.alive = alive;
        this.freeIndices = freeIndices;
        this.freeCount = freeCount;
        this.deferredSpawns = deferredSpawns;
        this.deferredSpawnsCount = deferredSpawnsCount;
        this.deferredDespawns = deferredDespawns;
        this.deferredDespawnsCount = deferredDespawnsCount;
        this.bodies = bodies;
        this.colliders = colliders;
        this.facings = facings;
        this.controllers = controllers;
        this.inputStates = inputStates;
        this.jumpStates = jumpStates;
        this.platforms = platforms;
        this.roomScopes = roomScopes;
        this.savepoints = savepoints;
        this.instanceIds = instanceIds;
    }
}
