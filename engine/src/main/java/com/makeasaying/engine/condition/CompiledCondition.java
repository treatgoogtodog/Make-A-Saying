package com.makeasaying.engine.condition;

import com.makeasaying.engine.flags.FlagStore;

/**
 * Compiled representation of a condition tree into a flat node array.
 * Evaluation over this representation guarantees zero heap allocations.
 */
public final class CompiledCondition {
    public static final int OP_ALWAYS_TRUE = 1;
    public static final int OP_ALWAYS_FALSE = 2;
    public static final int OP_FLAG_EQ = 3;
    public static final int OP_FLAG_NE = 4;
    public static final int OP_FLAG_LT = 5;
    public static final int OP_FLAG_LE = 6;
    public static final int OP_FLAG_GT = 7;
    public static final int OP_FLAG_GE = 8;
    public static final int OP_FLAG_SET = 9;
    public static final int OP_ALL = 10;
    public static final int OP_ANY = 11;
    public static final int OP_NOT = 12;

    public static final CompiledCondition ALWAYS_TRUE = new CompiledCondition(new Node[] {
        new Node(OP_ALWAYS_TRUE, -1, 0, new int[0])
    });

    public static final CompiledCondition ALWAYS_FALSE = new CompiledCondition(new Node[] {
        new Node(OP_ALWAYS_FALSE, -1, 0, new int[0])
    });

    public record Node(int op, int flagIndex, int targetValue, int[] childIndices) {}

    private final Node[] nodes;

    public CompiledCondition(Node[] nodes) {
        this.nodes = nodes != null && nodes.length > 0 ? nodes : ALWAYS_TRUE.nodes;
    }

    public Node[] nodes() {
        return nodes;
    }

    /**
     * Evaluates the condition against the FlagStore for the given room.
     * Guaranteed zero heap allocation.
     */
    public boolean evaluate(FlagStore store, String activeRoomId) {
        if (nodes.length == 0 || nodes[0].op == OP_ALWAYS_TRUE) {
            return true;
        }
        if (nodes[0].op == OP_ALWAYS_FALSE) {
            return false;
        }
        return evaluateNode(0, store, activeRoomId);
    }

    private boolean evaluateNode(int nodeIdx, FlagStore store, String activeRoomId) {
        Node node = nodes[nodeIdx];
        switch (node.op) {
            case OP_ALWAYS_TRUE -> {
                return true;
            }
            case OP_ALWAYS_FALSE -> {
                return false;
            }
            case OP_FLAG_EQ -> {
                int val = store.get(node.flagIndex, activeRoomId);
                return val == node.targetValue;
            }
            case OP_FLAG_NE -> {
                int val = store.get(node.flagIndex, activeRoomId);
                return val != node.targetValue;
            }
            case OP_FLAG_LT -> {
                int val = store.get(node.flagIndex, activeRoomId);
                return val < node.targetValue;
            }
            case OP_FLAG_LE -> {
                int val = store.get(node.flagIndex, activeRoomId);
                return val <= node.targetValue;
            }
            case OP_FLAG_GT -> {
                int val = store.get(node.flagIndex, activeRoomId);
                return val > node.targetValue;
            }
            case OP_FLAG_GE -> {
                int val = store.get(node.flagIndex, activeRoomId);
                return val >= node.targetValue;
            }
            case OP_FLAG_SET -> {
                int val = store.get(node.flagIndex, activeRoomId);
                return val != 0;
            }
            case OP_NOT -> {
                if (node.childIndices.length == 0) return false;
                return !evaluateNode(node.childIndices[0], store, activeRoomId);
            }
            case OP_ALL -> {
                // Empty all is true
                for (int childIdx : node.childIndices) {
                    if (!evaluateNode(childIdx, store, activeRoomId)) {
                        return false;
                    }
                }
                return true;
            }
            case OP_ANY -> {
                // Empty any is false
                for (int childIdx : node.childIndices) {
                    if (evaluateNode(childIdx, store, activeRoomId)) {
                        return true;
                    }
                }
                return false;
            }
            default -> {
                return true;
            }
        }
    }
}
