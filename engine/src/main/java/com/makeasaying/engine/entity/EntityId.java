package com.makeasaying.engine.entity;

/**
 * 64-bit packed generational Entity ID per AGENTS.md section 7.2 and M3 brief.
 * High 32 bits: generation. Low 32 bits: index.
 * Index 0 is reserved as invalid/null ID (0L).
 */
public final class EntityId {
    public static final long NULL_ID = 0L;

    private EntityId() {}

    public static long of(int index, int generation) {
        if (index <= 0) return NULL_ID;
        return (((long) generation) << 32) | (index & 0xFFFFFFFFL);
    }

    public static int getIndex(long id) {
        return (int) (id & 0xFFFFFFFFL);
    }

    public static int getGeneration(long id) {
        return (int) (id >>> 32);
    }

    public static boolean isNull(long id) {
        return id == NULL_ID || getIndex(id) == 0;
    }

    public static boolean isValid(long id) {
        return !isNull(id);
    }
}
