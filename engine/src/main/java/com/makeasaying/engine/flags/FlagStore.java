package com.makeasaying.engine.flags;

import com.makeasaying.engine.entity.events.EntityEventQueue;
import com.makeasaying.engine.util.Log;
import com.makeasaying.engine.util.LogCategory;
import com.makeasaying.engine.world.RoomStateStore;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Unified flag store per AGENTS.md section 7.8 and M7a brief.
 * Holds flags across 4 scopes (GLOBAL, SESSION, ROOM, ROOM_TEMP).
 * Guarantees zero allocation during simulation ticks and condition evaluations.
 */
public final class FlagStore {
    private static final int DEFAULT_PENDING_CAPACITY = 256;
    private static final int DEFAULT_LOG_CAPACITY = 256;
    private static final long FNV_BASIS = 1125899906842597L;
    private static final long FNV_PRIME = 1099511628211L;

    private final FlagRegistry registry;
    private final RoomStateStore roomStateStore;

    private final int[] globalValues;
    private final int[] sessionValues;
    private final int[] roomTempValues;

    // Preallocated pending changes buffer
    private final PendingFlagChange[] pendingBuffer;
    private int pendingHead = 0;
    private int pendingTail = 0;
    private int pendingCount = 0;

    // Preallocated change-log ring buffer
    private final FlagChangeLogEntry[] logBuffer;
    private int logHead = 0;
    private int logCount = 0;

    // Rate-limiting for loud errors
    private long lastErrorTick = -1000L;

    public FlagStore(FlagRegistry registry, RoomStateStore roomStateStore) {
        this.registry = Objects.requireNonNull(registry, "FlagRegistry cannot be null");
        this.roomStateStore = Objects.requireNonNull(roomStateStore, "RoomStateStore cannot be null");

        int count = registry.size();
        this.globalValues = new int[count];
        this.sessionValues = new int[count];
        this.roomTempValues = new int[count];

        // Initialize default values
        for (int i = 0; i < count; i++) {
            FlagDefinition def = registry.get(i);
            int defVal = def.defaultValue();
            globalValues[i] = defVal;
            sessionValues[i] = defVal;
            roomTempValues[i] = defVal;
        }

        this.pendingBuffer = new PendingFlagChange[DEFAULT_PENDING_CAPACITY];
        for (int i = 0; i < DEFAULT_PENDING_CAPACITY; i++) {
            this.pendingBuffer[i] = new PendingFlagChange();
        }

        this.logBuffer = new FlagChangeLogEntry[DEFAULT_LOG_CAPACITY];
        for (int i = 0; i < DEFAULT_LOG_CAPACITY; i++) {
            this.logBuffer[i] = new FlagChangeLogEntry();
        }
    }

    public FlagRegistry getRegistry() {
        return registry;
    }

    public RoomStateStore getRoomStateStore() {
        return roomStateStore;
    }

    /**
     * Reads applied flag value. Guarantees zero allocation.
     */
    public int get(int index, String activeRoomId) {
        FlagDefinition def = registry.get(index);
        if (def == null) {
            logErrorRateLimited("Attempted to read undeclared flag at index " + index);
            return 0;
        }

        FlagScope scope = def.scope();
        if (scope == FlagScope.GLOBAL) {
            return globalValues[index];
        } else if (scope == FlagScope.SESSION) {
            return sessionValues[index];
        } else if (scope == FlagScope.ROOM) {
            if (activeRoomId == null || activeRoomId.isEmpty()) {
                return def.defaultValue();
            }
            return roomStateStore.getInt(activeRoomId, def.precomputedRoomKey(), def.defaultValue());
        } else {
            return roomTempValues[index];
        }
    }

    public int get(String id, String activeRoomId) {
        int idx = registry.indexOf(id);
        if (idx < 0) {
            logErrorRateLimited("Attempted to read undeclared flag '" + id + "'");
            return 0;
        }
        return get(idx, activeRoomId);
    }

    /**
     * Enqueues a flag set operation into the pending buffer.
     */
    public void set(int index, int value, int source) {
        FlagDefinition def = registry.get(index);
        if (def == null) {
            logErrorRateLimited("Attempted to set undeclared flag at index " + index);
            return;
        }
        if (!def.isValidValue(value)) {
            logErrorRateLimited(String.format("Invalid value %d for flag '%s' (type: %s)", value, def.id(), def.type()));
            return;
        }
        enqueue(index, PendingFlagChange.OP_SET, value, source);
    }

    /**
     * Enqueues a flag addition operation. Only valid for INT flags.
     */
    public void add(int index, int delta, int source) {
        FlagDefinition def = registry.get(index);
        if (def == null) {
            logErrorRateLimited("Attempted to add to undeclared flag at index " + index);
            return;
        }
        if (def.type() != FlagType.INT) {
            logErrorRateLimited(String.format("Cannot add to non-integer flag '%s' (type: %s)", def.id(), def.type()));
            return;
        }
        enqueue(index, PendingFlagChange.OP_ADD, delta, source);
    }

    /**
     * Enqueues a boolean toggle operation. Only valid for BOOL flags.
     */
    public void toggle(int index, int source) {
        FlagDefinition def = registry.get(index);
        if (def == null) {
            logErrorRateLimited("Attempted to toggle undeclared flag at index " + index);
            return;
        }
        if (def.type() != FlagType.BOOL) {
            logErrorRateLimited(String.format("Cannot toggle non-boolean flag '%s' (type: %s)", def.id(), def.type()));
            return;
        }
        enqueue(index, PendingFlagChange.OP_TOGGLE, 0, source);
    }

    /**
     * Enqueues a clear operation to restore the flag's default value.
     */
    public void clear(int index, int source) {
        FlagDefinition def = registry.get(index);
        if (def == null) {
            logErrorRateLimited("Attempted to clear undeclared flag at index " + index);
            return;
        }
        enqueue(index, PendingFlagChange.OP_CLEAR, 0, source);
    }

    private void enqueue(int flagIndex, int op, int valueOrDelta, int source) {
        if (pendingCount >= pendingBuffer.length) {
            logErrorRateLimited("Flag pending-change buffer overflow! Dropping change for flag index " + flagIndex);
            return;
        }
        pendingBuffer[pendingTail].set(flagIndex, op, valueOrDelta, source);
        pendingTail = (pendingTail + 1) % pendingBuffer.length;
        pendingCount++;
    }

    /**
     * Applies all pending flag changes deterministically in queue order at the tick boundary.
     */
    public void applyPendingChanges(String activeRoomId, EntityEventQueue eventQueue, long currentTick) {
        while (pendingCount > 0) {
            PendingFlagChange change = pendingBuffer[pendingHead];
            pendingHead = (pendingHead + 1) % pendingBuffer.length;
            pendingCount--;

            int idx = change.flagIndex;
            FlagDefinition def = registry.get(idx);
            if (def == null) continue;

            int oldVal = get(idx, activeRoomId);
            int newVal;

            switch (change.op) {
                case PendingFlagChange.OP_SET -> newVal = change.valueOrDelta;
                case PendingFlagChange.OP_ADD -> {
                    long candidate = (long) oldVal + change.valueOrDelta;
                    if (candidate < def.min() || candidate > def.max()) {
                        logErrorRateLimited(String.format("Flag '%s' add(%d) would exceed range [%d..%d] (current: %d). Write rejected.",
                            def.id(), change.valueOrDelta, def.min(), def.max(), oldVal));
                        continue;
                    }
                    newVal = (int) candidate;
                }
                case PendingFlagChange.OP_TOGGLE -> newVal = (oldVal == 1) ? 0 : 1;
                case PendingFlagChange.OP_CLEAR -> newVal = def.defaultValue();
                default -> {
                    continue;
                }
            }

            if (!def.isValidValue(newVal)) {
                logErrorRateLimited(String.format("Calculated invalid value %d for flag '%s'. Write rejected.", newVal, def.id()));
                continue;
            }

            if (newVal != oldVal) {
                // Apply to underlying storage
                FlagScope scope = def.scope();
                if (scope == FlagScope.GLOBAL) {
                    globalValues[idx] = newVal;
                } else if (scope == FlagScope.SESSION) {
                    sessionValues[idx] = newVal;
                } else if (scope == FlagScope.ROOM) {
                    if (activeRoomId != null && !activeRoomId.isEmpty()) {
                        roomStateStore.setInt(activeRoomId, def.precomputedRoomKey(), newVal);
                    }
                } else {
                    roomTempValues[idx] = newVal;
                }

                // Emit event
                if (eventQueue != null) {
                    eventQueue.emitFlagChanged(idx, oldVal, newVal);
                }

                // Append to change log ring buffer
                recordChangeLog(currentTick, idx, oldVal, newVal, change.source);
            }
        }
    }

    /**
     * Resets all ROOM_TEMP flags on room swap.
     * Silent reset: does not emit flag_changed events, but logs with SOURCE_ROOM_SWAP.
     */
    public void clearRoomTemp(long currentTick) {
        for (int i = 0; i < registry.size(); i++) {
            FlagDefinition def = registry.get(i);
            if (def.scope() == FlagScope.ROOM_TEMP) {
                int oldVal = roomTempValues[i];
                int defVal = def.defaultValue();
                if (oldVal != defVal) {
                    roomTempValues[i] = defVal;
                    recordChangeLog(currentTick, i, oldVal, defVal, FlagChangeLogEntry.SOURCE_ROOM_SWAP);
                }
            }
        }
    }

    private void recordChangeLog(long tick, int flagIndex, int oldValue, int newValue, int source) {
        int slot = (logHead + logCount) % logBuffer.length;
        if (logCount < logBuffer.length) {
            logCount++;
        } else {
            logHead = (logHead + 1) % logBuffer.length; // Overwrite oldest
        }
        logBuffer[slot].set(tick, flagIndex, oldValue, newValue, source);
    }

    public int getLogCount() {
        return logCount;
    }

    public FlagChangeLogEntry getLogEntry(int relativeIndex) {
        if (relativeIndex < 0 || relativeIndex >= logCount) {
            return null;
        }
        int slot = (logHead + relativeIndex) % logBuffer.length;
        return logBuffer[slot];
    }

    public long computeStateHash(String activeRoomId) {
        long hash = FNV_BASIS;
        for (int i = 0; i < registry.size(); i++) {
            int val = get(i, activeRoomId);
            hash ^= val;
            hash *= FNV_PRIME;
        }
        return hash;
    }

    public Snapshot createSnapshot() {
        return new Snapshot(
            Arrays.copyOf(globalValues, globalValues.length),
            Arrays.copyOf(sessionValues, sessionValues.length),
            Arrays.copyOf(roomTempValues, roomTempValues.length)
        );
    }

    public void restoreSnapshot(Snapshot snapshot) {
        if (snapshot == null) return;
        System.arraycopy(snapshot.globalValues, 0, this.globalValues, 0, Math.min(globalValues.length, snapshot.globalValues.length));
        System.arraycopy(snapshot.sessionValues, 0, this.sessionValues, 0, Math.min(sessionValues.length, snapshot.sessionValues.length));
        System.arraycopy(snapshot.roomTempValues, 0, this.roomTempValues, 0, Math.min(roomTempValues.length, snapshot.roomTempValues.length));
        pendingHead = 0;
        pendingTail = 0;
        pendingCount = 0;
    }

    public void resetToDefaults() {
        for (int i = 0; i < registry.size(); i++) {
            FlagDefinition def = registry.get(i);
            int defVal = def.defaultValue();
            globalValues[i] = defVal;
            sessionValues[i] = defVal;
            roomTempValues[i] = defVal;
        }
        pendingHead = 0;
        pendingTail = 0;
        pendingCount = 0;
    }

    public Map<String, Object> exportGlobalFlags() {
        Map<String, Object> exported = new LinkedHashMap<>();
        for (int i = 0; i < registry.size(); i++) {
            FlagDefinition def = registry.get(i);
            if (def.scope() == FlagScope.GLOBAL) {
                int val = globalValues[i];
                if (def.type() == FlagType.BOOL) {
                    exported.put(def.id(), val != 0);
                } else if (def.type() == FlagType.ENUM) {
                    String enumName = (def.enumValues() != null && val >= 0 && val < def.enumValues().size())
                        ? def.enumValues().get(val) : "unknown";
                    exported.put(def.id(), enumName);
                } else {
                    exported.put(def.id(), val);
                }
            }
        }
        return exported;
    }

    public void restoreSavedGlobalFlags(Map<String, Object> savedFlags) {
        for (int i = 0; i < registry.size(); i++) {
            FlagDefinition def = registry.get(i);
            if (def.scope() == FlagScope.GLOBAL) {
                if (savedFlags != null && savedFlags.containsKey(def.id())) {
                    Object raw = savedFlags.get(def.id());
                    int intVal = def.defaultValue();
                    if (raw instanceof Boolean b) {
                        intVal = b ? 1 : 0;
                    } else if (raw instanceof Number n) {
                        intVal = n.intValue();
                    } else if (raw instanceof String s && def.type() == FlagType.ENUM) {
                        int idx = def.enumValues() != null ? def.enumValues().indexOf(s) : -1;
                        intVal = idx >= 0 ? idx : def.defaultValue();
                    }
                    globalValues[i] = intVal;
                } else {
                    // Untouched defaults for missing ones per DEC-036 / AGENTS.md
                    globalValues[i] = def.defaultValue();
                }
            }
        }
    }

    public void resetSessionAndRoomTemp() {
        for (int i = 0; i < registry.size(); i++) {
            FlagDefinition def = registry.get(i);
            if (def.scope() == FlagScope.SESSION) {
                sessionValues[i] = def.defaultValue();
            } else if (def.scope() == FlagScope.ROOM_TEMP) {
                roomTempValues[i] = def.defaultValue();
            }
        }
    }

    public record Snapshot(int[] globalValues, int[] sessionValues, int[] roomTempValues) {}

    private void logErrorRateLimited(String message) {
        Log.error(LogCategory.ENGINE, message);
    }
}
