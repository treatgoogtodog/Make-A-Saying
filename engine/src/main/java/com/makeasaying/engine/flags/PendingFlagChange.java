package com.makeasaying.engine.flags;

/**
 * Preallocated pending flag mutation record.
 */
public final class PendingFlagChange {
    public static final int OP_SET = 1;
    public static final int OP_ADD = 2;
    public static final int OP_TOGGLE = 3;
    public static final int OP_CLEAR = 4;

    public int flagIndex;
    public int op;
    public int valueOrDelta;
    public int source;

    public void set(int flagIndex, int op, int valueOrDelta, int source) {
        this.flagIndex = flagIndex;
        this.op = op;
        this.valueOrDelta = valueOrDelta;
        this.source = source;
    }
}
