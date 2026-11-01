package com.makeasaying.data.world;

import com.makeasaying.engine.async.Jobs;
import com.makeasaying.engine.util.Log;
import com.makeasaying.engine.util.LogCategory;
import com.makeasaying.engine.world.Room;
import com.makeasaying.engine.world.RoomExit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Preload cache for Room instances per Principle 4.1, 4.4 and M5b brief.
 * Stored in data module. Pure data, zero GL.
 * Caches loaded Rooms, checks timestamp changes on access/invalidation,
 * evicts non-neighbors, and preloads exit neighbors using background Jobs.
 */
public final class RoomCache {
    public record CacheEntry(Room room, long lastModified, Path path) {}

    private final Map<String, CacheEntry> cache = new HashMap<>();

    public RoomCache() {}

    public synchronized void put(Room room, Path path) {
        if (room == null) return;
        long mod = 0L;
        if (path != null && Files.exists(path)) {
            try {
                mod = Files.getLastModifiedTime(path).toMillis();
            } catch (IOException ignored) {}
        }
        cache.put(room.id(), new CacheEntry(room, mod, path));
    }

    public synchronized Room get(String roomId) {
        CacheEntry entry = cache.get(roomId);
        if (entry == null) return null;

        // Invalidate if file was modified on disk
        if (entry.path() != null && Files.exists(entry.path())) {
            try {
                long currentMod = Files.getLastModifiedTime(entry.path()).toMillis();
                if (currentMod > entry.lastModified()) {
                    Log.info(LogCategory.DATA, "Room %s modified on disk, invalidating cache", roomId);
                    cache.remove(roomId);
                    return null;
                }
            } catch (IOException ignored) {}
        }
        return entry.room();
    }

    public synchronized boolean isCached(String roomId) {
        return get(roomId) != null;
    }

    public synchronized void remove(String roomId) {
        cache.remove(roomId);
    }

    public synchronized void clear() {
        cache.clear();
    }

    public synchronized Set<String> getCachedRoomIds() {
        return Collections.unmodifiableSet(new HashSet<>(cache.keySet()));
    }

    /**
     * Evicts any rooms from the cache whose id is NOT in keepRoomIds.
     */
    public synchronized void evictExcept(Set<String> keepRoomIds) {
        if (keepRoomIds == null) {
            cache.clear();
            return;
        }
        cache.keySet().removeIf(id -> !keepRoomIds.contains(id));
    }

    /**
     * Preloads exit targets of the current room asynchronously using Jobs.
     */
    public synchronized void preloadNeighbors(
        Room currentRoom,
        Path roomDirectory,
        Jobs jobs,
        BiConsumer<String, Room> onRoomLoaded,
        Consumer<Throwable> onError
    ) {
        if (currentRoom == null || jobs == null) return;

        // Keep current room and its exit neighbors
        Set<String> neighborIds = new HashSet<>();
        neighborIds.add(currentRoom.id());
        for (RoomExit exit : currentRoom.exits()) {
            neighborIds.add(exit.targetRoom());
        }
        evictExcept(neighborIds);

        for (RoomExit exit : currentRoom.exits()) {
            String targetId = exit.targetRoom();
            if (isCached(targetId)) continue;

            Path targetPath = roomDirectory.resolve(targetId + ".json");
            if (!Files.exists(targetPath)) continue;

            jobs.submit(
                () -> {
                    try {
                        return RoomLoader.load(targetPath);
                    } catch (Exception e) {
                        Log.error(LogCategory.DATA, "Failed async preloading room %s: %s", targetPath, e.getMessage());
                        throw new RuntimeException(e);
                    }
                },
                loadedRoom -> {
                    put(loadedRoom, targetPath);
                    if (onRoomLoaded != null) {
                        onRoomLoaded.accept(targetId, loadedRoom);
                    }
                },
                err -> {
                    if (onError != null) {
                        onError.accept(err);
                    }
                }
            );
        }
    }
}
