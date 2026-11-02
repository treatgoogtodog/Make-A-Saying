package com.makeasaying.data.save;

import com.makeasaying.data.world.RoomCache;
import com.makeasaying.engine.async.Jobs;
import com.makeasaying.engine.flags.FlagRegistry;
import com.makeasaying.engine.save.SaveClock;
import com.makeasaying.engine.save.SaveEnvelope;
import com.makeasaying.engine.save.SaveLocation;
import com.makeasaying.engine.save.SavePayload;
import com.makeasaying.engine.util.Log;
import com.makeasaying.engine.util.LogCategory;
import com.makeasaying.engine.world.Room;
import com.makeasaying.engine.world.RoomEntityData;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Coordinates asynchronous, crash-safe checkpoint saves and pre-validated loads per M7b brief.
 * Manages atomic writes on worker threads, write queueing (independent of disk speed),
 * backup quarantine, and pre-fade load verification.
 */
public final class SaveManager {
    private final SaveLocation location;
    private final FlagRegistry flagRegistry;
    private final Jobs jobs;
    private final SaveClock clock;

    private boolean writeInFlight = false;
    private SavePayload pendingSavePayload = null;
    private String pendingSlot = null;

    private final Object flightLock = new Object();
    private CountDownLatch flightLatch = null;

    public SaveManager(SaveLocation location, FlagRegistry flagRegistry, Jobs jobs, SaveClock clock) {
        this.location = location;
        this.flagRegistry = flagRegistry;
        this.jobs = jobs;
        this.clock = clock != null ? clock : SaveClock.systemUtc();
    }

    public SaveManager(SaveLocation location, FlagRegistry flagRegistry, Jobs jobs) {
        this(location, flagRegistry, jobs, SaveClock.systemUtc());
    }

    public SaveLocation getLocation() {
        return location;
    }

    public SaveClock getClock() {
        return clock;
    }

    public boolean isWriteInFlight() {
        return writeInFlight;
    }

    /**
     * Enqueues a checkpoint save request.
     * Simulation-visible behavior is independent of I/O speed (Required Change 1):
     * If a save is already in flight, the request is queued (latest request wins).
     */
    public void requestSave(String slot, SavePayload payload, Consumer<Boolean> onDoneOnMainThread) {
        if (payload == null) return;
        if (slot == null || slot.isBlank()) slot = "slot_1";

        if (writeInFlight) {
            // Queue latest request
            pendingSlot = slot;
            pendingSavePayload = payload;
            Log.info(LogCategory.ENGINE, "Save write already in flight. Queued latest save request for slot '%s'", slot);
            return;
        }

        executeSave(slot, payload, onDoneOnMainThread);
    }

    private void executeSave(String slot, SavePayload payload, Consumer<Boolean> onDoneOnMainThread) {
        writeInFlight = true;
        synchronized (flightLock) {
            flightLatch = new CountDownLatch(1);
        }

        // 1. Serialize synchronously on main thread (zero allocation in simulation loop)
        String serializedJson = SaveWriter.serialize(payload, flagRegistry);

        // 2. Dispatch disk write to worker thread via Jobs
        jobs.submit(
            () -> {
                performSafeWrite(slot, serializedJson);
                return true;
            },
            success -> {
                writeInFlight = false;
                synchronized (flightLock) {
                    if (flightLatch != null) {
                        flightLatch.countDown();
                    }
                }
                Log.info(LogCategory.ENGINE, "Checkpoint save completed successfully for slot '%s'", slot);
                if (onDoneOnMainThread != null) {
                    onDoneOnMainThread.accept(true);
                }

                // If a new save was requested while this write was in flight, start it now
                if (pendingSavePayload != null) {
                    String nextSlot = pendingSlot;
                    SavePayload nextPayload = pendingSavePayload;
                    pendingSlot = null;
                    pendingSavePayload = null;
                    executeSave(nextSlot, nextPayload, onDoneOnMainThread);
                }
            },
            error -> {
                writeInFlight = false;
                synchronized (flightLock) {
                    if (flightLatch != null) {
                        flightLatch.countDown();
                    }
                }
                Log.error(LogCategory.DATA, "Failed to write checkpoint save for slot '%s': %s", slot, error.getMessage());
                if (onDoneOnMainThread != null) {
                    onDoneOnMainThread.accept(false);
                }
            }
        );
    }

    private void performSafeWrite(String slot, String serializedJson) throws IOException {
        // Backup rule: validate current main file before copying to .bak
        if (location.existsMain(slot)) {
            boolean mainIsValid = false;
            try {
                String existing = location.readMain(slot);
                SaveLoader.load(existing, flagRegistry);
                mainIsValid = true;
            } catch (Exception e) {
                Log.warn(LogCategory.DATA, "Existing main save '%s.json' is invalid/corrupt (%s). Quarantining without overwriting .bak", slot, e.getMessage());
            }

            if (mainIsValid) {
                location.copyToBak(slot);
            } else {
                location.quarantineCorrupt(slot);
            }
        }

        // Write to temp and atomically rename
        location.writeTemp(slot, serializedJson);
        location.atomicMoveTempToMain(slot);
    }

    /**
     * Synchronous load with fallback: main -> .bak -> new game.
     */
    public SaveEnvelope loadSaveWithFallback(String slot) {
        if (slot == null || slot.isBlank()) slot = "slot_1";

        // 1. Try main file
        if (location.existsMain(slot)) {
            try {
                String content = location.readMain(slot);
                return SaveLoader.load(content, flagRegistry);
            } catch (Exception e) {
                Log.warn(LogCategory.DATA, "Main save '%s.json' is corrupt: %s. Quarantining and falling back to backup.", slot, e.getMessage());
                try {
                    location.quarantineCorrupt(slot);
                } catch (IOException ignored) {}
            }
        }

        // 2. Try .bak file
        if (location.existsBak(slot)) {
            try {
                String bakContent = location.readBak(slot);
                SaveEnvelope env = SaveLoader.load(bakContent, flagRegistry);
                Log.warn(LogCategory.DATA, "Loaded checkpoint save from backup '%s.bak'", slot);
                return env;
            } catch (Exception e) {
                Log.error(LogCategory.DATA, "Backup save '%s.bak' is also corrupt: %s", slot, e.getMessage());
            }
        }

        return null;
    }

    /**
     * Pre-validates save data, target room, and target savepoint BEFORE initiating a fade transition (Required Change 2).
     * If valid, calls onValidPreload on main thread with the loaded envelope and target room.
     * If invalid, calls onFailed on main thread and leaves the current game untouched.
     */
    public void preValidateAndLoad(
        String slot,
        RoomCache roomCache,
        Consumer<SaveEnvelope> onValidPreload,
        Runnable onFailed
    ) {
        jobs.submit(
            () -> {
                SaveEnvelope envelope = loadSaveWithFallback(slot);
                if (envelope == null) {
                    throw new IllegalArgumentException("No valid save or backup found in slot '" + slot + "'");
                }

                String targetRoomId = envelope.data().currentRoomId();
                String targetSavepointId = envelope.data().savepointInstanceId();

                // Validate room exists in cache / disk
                Room room = roomCache.get(targetRoomId);
                if (room == null) {
                    throw new IllegalArgumentException("Saved target room '" + targetRoomId + "' not found on disk");
                }

                // Validate savepoint entity exists in target room
                boolean savepointFound = false;
                for (RoomEntityData ed : room.entities()) {
                    if (targetSavepointId.equals(ed.instanceId())) {
                        savepointFound = true;
                        break;
                    }
                }
                if (!savepointFound) {
                    throw new IllegalArgumentException("Saved savepoint '" + targetSavepointId + "' not found in room '" + targetRoomId + "'");
                }

                return envelope;
            },
            envelope -> {
                Log.info(LogCategory.ENGINE, "Pre-load validation successful for slot '%s' (room: %s, savepoint: %s)",
                    slot, envelope.data().currentRoomId(), envelope.data().savepointInstanceId());
                if (onValidPreload != null) {
                    onValidPreload.accept(envelope);
                }
            },
            error -> {
                Log.error(LogCategory.ENGINE, "Pre-load validation failed for slot '%s': %s. Aborting load; current game untouched.",
                    slot, error.getMessage());
                if (onFailed != null) {
                    onFailed.run();
                }
            }
        );
    }

    /**
     * Bounded shutdown waiting for any in-flight worker write to complete (Required Change 8).
     */
    public void shutdown(long timeoutMs) {
        CountDownLatch latch;
        synchronized (flightLock) {
            latch = flightLatch;
        }
        if (latch != null && latch.getCount() > 0) {
            try {
                Log.info(LogCategory.ENGINE, "Waiting up to %d ms for in-flight save write to complete before shutdown...", timeoutMs);
                boolean finished = latch.await(timeoutMs, TimeUnit.MILLISECONDS);
                if (!finished) {
                    Log.error(LogCategory.ENGINE, "Save write timed out after %d ms during shutdown!", timeoutMs);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
