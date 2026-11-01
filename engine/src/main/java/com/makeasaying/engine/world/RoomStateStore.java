package com.makeasaying.engine.world;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * In-memory persistent per-room key-value store per Principle 4.10 and M5b brief.
 * Holds integer and string entries keyed by (roomId, key).
 * Engine module: pure data structures, zero Jackson or I/O.
 * Deterministic iteration and hashing via sorted keys.
 */
public final class RoomStateStore {
    private static final Pattern KEY_PATTERN = Pattern.compile("^[a-z0-9_.]+$");
    private static final long FNV_BASIS = 1125899906842597L;
    private static final long FNV_PRIME = 1099511628211L;

    public enum ValueType {
        INT,
        STRING
    }

    public record Entry(String roomId, String key, ValueType type, int intValue, String stringValue) {
        public Entry {
            if (roomId == null || roomId.isBlank()) throw new IllegalArgumentException("roomId must not be blank");
            if (key == null || !KEY_PATTERN.matcher(key).matches()) {
                throw new IllegalArgumentException("Invalid key '" + key + "': must match [a-z0-9_.]+");
            }
            if (type == null) throw new IllegalArgumentException("type must not be null");
        }
    }

    // Outer map: roomId -> Inner map: key -> Entry
    private final Map<String, Map<String, Entry>> store = new HashMap<>();

    public RoomStateStore() {}

    private void validateKey(String key) {
        if (key == null || !KEY_PATTERN.matcher(key).matches()) {
            throw new IllegalArgumentException("Invalid key '" + key + "': must match [a-z0-9_.]+");
        }
    }

    private void validateRoomId(String roomId) {
        if (roomId == null || roomId.isBlank()) {
            throw new IllegalArgumentException("roomId must not be blank");
        }
    }

    public void setInt(String roomId, String key, int value) {
        validateRoomId(roomId);
        validateKey(key);
        store.computeIfAbsent(roomId, k -> new HashMap<>())
            .put(key, new Entry(roomId, key, ValueType.INT, value, null));
    }

    public int getInt(String roomId, String key, int defaultValue) {
        validateRoomId(roomId);
        validateKey(key);
        Map<String, Entry> roomMap = store.get(roomId);
        if (roomMap == null) return defaultValue;
        Entry entry = roomMap.get(key);
        if (entry == null || entry.type() != ValueType.INT) return defaultValue;
        return entry.intValue();
    }

    public int incrementInt(String roomId, String key) {
        int current = getInt(roomId, key, 0);
        int next = current + 1;
        setInt(roomId, key, next);
        return next;
    }

    public void setString(String roomId, String key, String value) {
        validateRoomId(roomId);
        validateKey(key);
        store.computeIfAbsent(roomId, k -> new HashMap<>())
            .put(key, new Entry(roomId, key, ValueType.STRING, 0, value != null ? value : ""));
    }

    public String getString(String roomId, String key, String defaultValue) {
        validateRoomId(roomId);
        validateKey(key);
        Map<String, Entry> roomMap = store.get(roomId);
        if (roomMap == null) return defaultValue;
        Entry entry = roomMap.get(key);
        if (entry == null || entry.type() != ValueType.STRING) return defaultValue;
        return entry.stringValue();
    }

    public boolean has(String roomId, String key) {
        validateRoomId(roomId);
        validateKey(key);
        Map<String, Entry> roomMap = store.get(roomId);
        return roomMap != null && roomMap.containsKey(key);
    }

    public void remove(String roomId, String key) {
        validateRoomId(roomId);
        validateKey(key);
        Map<String, Entry> roomMap = store.get(roomId);
        if (roomMap != null) {
            roomMap.remove(key);
            if (roomMap.isEmpty()) {
                store.remove(roomId);
            }
        }
    }

    public void clear() {
        store.clear();
    }

    /**
     * Returns all entries sorted deterministically by roomId, then key.
     */
    public List<Entry> getAllEntriesSorted() {
        List<String> sortedRooms = new ArrayList<>(store.keySet());
        Collections.sort(sortedRooms);

        List<Entry> result = new ArrayList<>();
        for (String rId : sortedRooms) {
            Map<String, Entry> roomMap = store.get(rId);
            if (roomMap == null || roomMap.isEmpty()) continue;
            List<String> sortedKeys = new ArrayList<>(roomMap.keySet());
            Collections.sort(sortedKeys);
            for (String k : sortedKeys) {
                result.add(roomMap.get(k));
            }
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * Computes a deterministic 64-bit FNV-1a hash of all entries in sorted order.
     */
    public long stateHash() {
        long h = FNV_BASIS;
        for (Entry e : getAllEntriesSorted()) {
            for (int i = 0; i < e.roomId().length(); i++) {
                h ^= e.roomId().charAt(i);
                h *= FNV_PRIME;
            }
            h ^= 0x1F;
            h *= FNV_PRIME;
            for (int i = 0; i < e.key().length(); i++) {
                h ^= e.key().charAt(i);
                h *= FNV_PRIME;
            }
            h ^= e.type().ordinal();
            h *= FNV_PRIME;
            if (e.type() == ValueType.INT) {
                h ^= e.intValue();
                h *= FNV_PRIME;
            } else if (e.stringValue() != null) {
                for (int i = 0; i < e.stringValue().length(); i++) {
                    h ^= e.stringValue().charAt(i);
                    h *= FNV_PRIME;
                }
            }
        }
        return h;
    }
}
