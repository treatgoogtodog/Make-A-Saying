package com.makeasaying.data.tuning;

import com.makeasaying.engine.async.Jobs;
import com.makeasaying.engine.entity.MovementTuning;
import com.makeasaying.engine.util.Log;
import com.makeasaying.engine.util.LogCategory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Hot-reloader for movement tuning per AGENTS.md section 4.4 and M3 brief.
 * Polls file modified time on background worker, parses off-thread,
 * and atomically applies on main thread at tick boundary.
 */
public final class MovementTuningWatcher {
    private final Path path;
    private final int tickRate;
    private final Jobs jobs;
    private final Consumer<MovementTuning> onReload;

    private FileTime lastModifiedTime;
    private final AtomicBoolean checkInProgress = new AtomicBoolean(false);

    public MovementTuningWatcher(Path path, int tickRate, Jobs jobs, Consumer<MovementTuning> onReload) {
        this.path = path;
        this.tickRate = tickRate;
        this.jobs = jobs;
        this.onReload = onReload;
        try {
            if (Files.exists(path)) {
                this.lastModifiedTime = Files.getLastModifiedTime(path);
            }
        } catch (Exception ignored) {}
    }

    /**
     * Called on main thread periodically (e.g. once per second).
     */
    public void poll() {
        if (!Files.exists(path) || !checkInProgress.compareAndSet(false, true)) {
            return;
        }

        jobs.submit(() -> {
            try {
                FileTime currentModified = Files.getLastModifiedTime(path);
                if (lastModifiedTime != null && currentModified.equals(lastModifiedTime)) {
                    return null; // Not modified
                }

                MovementTuning newTuning = MovementTuningLoader.load(path, tickRate);
                lastModifiedTime = currentModified;
                return newTuning;
            } catch (Throwable t) {
                Log.error(LogCategory.DATA,
                    "Failed to hot-reload movement tuning from %s. Keeping current values. Error: %s",
                    t, path.toAbsolutePath(), t.getMessage());
                return null;
            } finally {
                checkInProgress.set(false);
            }
        }, reloadedTuning -> {
            if (reloadedTuning != null && onReload != null) {
                onReload.accept(reloadedTuning);
                Log.info(LogCategory.DATA, "Movement tuning hot-reloaded successfully from %s", path.getFileName());
            }
        }, null);
    }
}
