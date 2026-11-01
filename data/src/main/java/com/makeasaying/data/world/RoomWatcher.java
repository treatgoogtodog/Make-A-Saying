package com.makeasaying.data.world;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.makeasaying.engine.async.Jobs;
import com.makeasaying.engine.entity.TileGrid;
import com.makeasaying.engine.util.Log;
import com.makeasaying.engine.util.LogCategory;
import com.makeasaying.engine.world.Room;
import com.makeasaying.engine.world.Tileset;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Hot-reload watcher for room files per M5a brief.
 * Parses and validates off-thread on worker pool.
 * Applies reloaded Room and TileGrid strictly at tick boundary via Jobs.drain().
 * Invalid files log loud errors and preserve the existing room.
 */
public final class RoomWatcher {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Path roomPath;
    private final Map<String, Tileset> tilesets;
    private final Jobs jobs;
    private final Consumer<RoomLoadResult> onReloaded;
    private final RoomValidator validator;

    private FileTime lastModifiedTime;
    private boolean checkPending = false;

    public record RoomLoadResult(Room room, TileGrid grid, List<ValidationFinding> findings) {}

    public RoomWatcher(Path roomPath, Map<String, Tileset> tilesets, Jobs jobs, Consumer<RoomLoadResult> onReloaded) {
        this.roomPath = roomPath;
        this.tilesets = tilesets;
        this.jobs = jobs;
        this.onReloaded = onReloaded;
        this.validator = new RoomValidator();

        try {
            if (Files.exists(roomPath)) {
                this.lastModifiedTime = Files.getLastModifiedTime(roomPath);
            }
        } catch (IOException e) {
            Log.warn(LogCategory.DATA, "Failed getting initial timestamp for %s", roomPath);
        }
    }

    public void checkAndReload() {
        if (checkPending || !Files.exists(roomPath)) {
            return;
        }

        try {
            FileTime currentModified = Files.getLastModifiedTime(roomPath);
            if (lastModifiedTime == null || currentModified.compareTo(lastModifiedTime) > 0) {
                lastModifiedTime = currentModified;
                checkPending = true;

                jobs.submit(
                    () -> parseAndValidateOffThread(roomPath, tilesets),
                    result -> {
                        checkPending = false;
                        if (result != null && onReloaded != null) {
                            onReloaded.accept(result);
                        }
                    },
                    error -> {
                        checkPending = false;
                        Log.error(LogCategory.DATA, "Error during off-thread room reload of %s: %s",
                            error, roomPath, error.getMessage());
                    }
                );
            }
        } catch (IOException e) {
            Log.warn(LogCategory.DATA, "Failed checking timestamp for %s: %s", roomPath, e.getMessage());
        }
    }

    public RoomLoadResult parseAndValidateOffThread(Path path, Map<String, Tileset> loadedTilesets) throws Exception {
        RoomLoader.RoomData data = MAPPER.readValue(path.toFile(), RoomLoader.RoomData.class);
        List<ValidationFinding> findings = validator.validate(data, loadedTilesets);

        boolean hasError = false;
        for (ValidationFinding f : findings) {
            if (f.severity() == ValidationSeverity.ERROR) {
                hasError = true;
                Log.error(LogCategory.DATA, "Hot reload validation error in %s: %s", path, f);
            } else {
                Log.warn(LogCategory.DATA, "Hot reload validation warning in %s: %s", path, f);
            }
        }

        if (hasError) {
            Log.error(LogCategory.DATA, "Room %s has validation errors. Keeping current active room.", path);
            return null;
        }

        Room room = RoomLoader.fromData(data);
        TileGrid grid = RoomGridBuilder.build(room, loadedTilesets, 16);
        Log.info(LogCategory.DATA, "Room %s successfully reloaded and validated off-thread.", path);
        return new RoomLoadResult(room, grid, findings);
    }
}
