package com.makeasaying.engine.flags;

/**
 * Preallocated change-log record in the FlagStore ring buffer.
 */
public final class FlagChangeLogEntry {
    public static final int SOURCE_GAMEPLAY = 1;
    public static final int SOURCE_DEBUG_PANEL = 2;
    public static final int SOURCE_ROOM_SWAP = 3;
    public static final int SOURCE_SCRIPT = 4;
    public static final int SOURCE_CUTSCENE = 5;

    public long tick;
    public int flagIndex;
    public int oldValue;
    public int newValue;
    public int source;

    public void set(long tick, int flagIndex, int oldValue, int newValue, int source) {
        this.tick = tick;
        this.flagIndex = flagIndex;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.source = source;
    }

    public static String getSourceName(int source) {
        return switch (source) {
            case SOURCE_GAMEPLAY -> "GAMEPLAY";
            case SOURCE_DEBUG_PANEL -> "DEBUG_PANEL";
            case SOURCE_ROOM_SWAP -> "ROOM_SWAP";
            case SOURCE_SCRIPT -> "SCRIPT";
            case SOURCE_CUTSCENE -> "CUTSCENE";
            default -> "UNKNOWN(" + source + ")";
        };
    }
}
