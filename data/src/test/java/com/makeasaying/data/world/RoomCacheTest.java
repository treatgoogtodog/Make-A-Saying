package com.makeasaying.data.world;

import com.makeasaying.engine.async.Jobs;
import com.makeasaying.engine.world.Room;
import com.makeasaying.engine.world.RoomExit;
import com.makeasaying.engine.world.SpawnPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class RoomCacheTest {
    private RoomCache cache;
    private Jobs jobs;

    @BeforeEach
    void setUp() {
        cache = new RoomCache();
        jobs = new Jobs(2);
    }

    @AfterEach
    void tearDown() {
        jobs.shutdown();
    }

    @Test
    void testBasicPutGetAndEvictExcept() {
        Room r1 = new Room("room_01", 10, 10, Map.of(), Map.of(), List.of(new SpawnPoint("s", 0, 0)), Collections.emptyList());
        Room r2 = new Room("room_02", 10, 10, Map.of(), Map.of(), List.of(new SpawnPoint("s", 0, 0)), Collections.emptyList());
        Room r3 = new Room("room_03", 10, 10, Map.of(), Map.of(), List.of(new SpawnPoint("s", 0, 0)), Collections.emptyList());

        cache.put(r1, null);
        cache.put(r2, null);
        cache.put(r3, null);

        assertEquals(r1, cache.get("room_01"));
        assertEquals(r2, cache.get("room_02"));
        assertEquals(r3, cache.get("room_03"));

        cache.evictExcept(Set.of("room_01", "room_03"));
        assertEquals(r1, cache.get("room_01"));
        assertNull(cache.get("room_02"), "room_02 should have been evicted");
        assertEquals(r3, cache.get("room_03"));
    }

    @Test
    void testInvalidateOnDiskModification(@TempDir Path tempDir) throws IOException, InterruptedException {
        Room room = new Room("temp_room", 10, 10, Map.of(), Map.of(), List.of(new SpawnPoint("s", 0, 0)), Collections.emptyList());
        Path file = tempDir.resolve("temp_room.json");
        RoomWriter.save(room, file, RoomLoader.CURRENT_VERSION);

        cache.put(room, file);
        assertNotNull(cache.get("temp_room"));

        // Touch file with newer timestamp
        Thread.sleep(50);
        Files.writeString(file, Files.readString(file) + " ");

        assertNull(cache.get("temp_room"), "Cache must invalidate when file timestamp advances");
    }

    @Test
    void testPreloadNeighborsViaJobs(@TempDir Path tempDir) throws Exception {
        Room neighbor = new Room("target_room", 10, 10, Map.of(), Map.of(), List.of(new SpawnPoint("s", 0, 0)), Collections.emptyList());
        Path neighborPath = tempDir.resolve("target_room.json");
        RoomWriter.save(neighbor, neighborPath, RoomLoader.CURRENT_VERSION);

        Room current = new Room("current_room", 10, 10, Map.of(), Map.of(),
            List.of(new SpawnPoint("s", 0, 0)),
            Collections.emptyList(),
            List.of(new RoomExit("exit_1", 100, 16, 20, 48, "target_room", "s"))
        );

        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean loaded = new AtomicBoolean(false);

        cache.preloadNeighbors(current, tempDir, jobs, (id, r) -> {
            loaded.set(true);
            latch.countDown();
        }, err -> latch.countDown());

        // Wait for worker task to complete and run on main thread callback
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < 3000) {
            jobs.drain();
            if (latch.getCount() == 0) break;
            Thread.sleep(10);
        }

        assertTrue(loaded.get(), "Preloaded neighbor callback should have fired");
        assertNotNull(cache.get("target_room"), "Preloaded neighbor must be in cache");
    }
}
