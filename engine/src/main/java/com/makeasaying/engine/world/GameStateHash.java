package com.makeasaying.engine.world;

import com.makeasaying.engine.entity.World;

/**
 * Deterministic combined game-state hash utility per Principle 4.3 and M5b brief.
 * Computes a combined 64-bit FNV-1a hash across World state, active roomId,
 * transition state/tick, and RoomStateStore.
 * Ensures World never depends on RoomStateStore (DEC-002).
 */
public final class GameStateHash {
    private static final long FNV_BASIS = 1125899906842597L;
    private static final long FNV_PRIME = 1099511628211L;

    private GameStateHash() {}

    public static long compute(
        World world,
        String roomId,
        RoomTransitionManager transition,
        RoomStateStore stateStore
    ) {
        long h = FNV_BASIS;

        // 1. World hash (entities, components, generations)
        if (world != null) {
            h ^= world.stateHash();
            h *= FNV_PRIME;
        }

        // 2. Active Room ID
        if (roomId != null) {
            for (int i = 0; i < roomId.length(); i++) {
                h ^= roomId.charAt(i);
                h *= FNV_PRIME;
            }
            h ^= 0x3F;
            h *= FNV_PRIME;
        }

        // 3. Transition State Machine
        if (transition != null) {
            h ^= transition.getState().ordinal();
            h *= FNV_PRIME;
            h ^= transition.getTransitionTick();
            h *= FNV_PRIME;
        }

        // 4. Room State Store
        if (stateStore != null) {
            h ^= stateStore.stateHash();
            h *= FNV_PRIME;
        }

        return h;
    }
}
