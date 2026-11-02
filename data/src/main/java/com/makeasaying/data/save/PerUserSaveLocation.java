package com.makeasaying.data.save;

import com.makeasaying.engine.save.SaveLocation;
import com.makeasaying.engine.util.Log;
import com.makeasaying.engine.util.LogCategory;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/**
 * Filesystem-backed SaveLocation targeting the OS per-user data directory per M7b brief.
 * Implements crash-safe atomic rename, .bak backup, and collision-free .corrupt quarantine (DEC-039).
 */
public final class PerUserSaveLocation implements SaveLocation {
    private final Path rootDir;

    public PerUserSaveLocation(Path rootDir) {
        this.rootDir = rootDir;
        try {
            Files.createDirectories(rootDir);
        } catch (IOException e) {
            Log.error(LogCategory.DATA, "Failed to create save directory: %s", rootDir);
        }
    }

    public static PerUserSaveLocation defaultLocation() {
        String os = System.getProperty("os.name", "").toLowerCase();
        String userHome = System.getProperty("user.home", ".");
        Path base;

        if (os.contains("win")) {
            String appData = System.getenv("LOCALAPPDATA");
            base = (appData != null && !appData.isBlank()) ? Path.of(appData) : Path.of(userHome, "AppData", "Local");
            base = base.resolve("MakeASaying").resolve("saves");
        } else if (os.contains("mac")) {
            base = Path.of(userHome, "Library", "Application Support", "MakeASaying", "saves");
        } else {
            String xdg = System.getenv("XDG_DATA_HOME");
            base = (xdg != null && !xdg.isBlank()) ? Path.of(xdg) : Path.of(userHome, ".local", "share");
            base = base.resolve("makeasaying").resolve("saves");
        }

        return new PerUserSaveLocation(base);
    }

    public Path getRootDir() {
        return rootDir;
    }

    private Path mainPath(String slot) {
        return rootDir.resolve(slot + ".json");
    }

    private Path bakPath(String slot) {
        return rootDir.resolve(slot + ".bak");
    }

    private Path tempPath(String slot) {
        return rootDir.resolve(slot + ".tmp");
    }

    @Override
    public boolean existsMain(String slot) {
        return Files.isRegularFile(mainPath(slot));
    }

    @Override
    public boolean existsBak(String slot) {
        return Files.isRegularFile(bakPath(slot));
    }

    @Override
    public String readMain(String slot) throws IOException {
        return Files.readString(mainPath(slot), StandardCharsets.UTF_8);
    }

    @Override
    public String readBak(String slot) throws IOException {
        return Files.readString(bakPath(slot), StandardCharsets.UTF_8);
    }

    @Override
    public void writeTemp(String slot, String content) throws IOException {
        Path tmp = tempPath(slot);
        Files.createDirectories(tmp.getParent());
        try (FileChannel channel = FileChannel.open(tmp,
            StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            channel.write(StandardCharsets.UTF_8.encode(content));
            channel.force(true); // Flush to underlying storage
        }
    }

    @Override
    public void copyToBak(String slot) throws IOException {
        Path main = mainPath(slot);
        if (Files.exists(main)) {
            Files.copy(main, bakPath(slot), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @Override
    public void atomicMoveTempToMain(String slot) throws IOException {
        Path tmp = tempPath(slot);
        Path main = mainPath(slot);
        try {
            Files.move(tmp, main, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            // Fallback for filesystems lacking atomic move support
            Files.move(tmp, main, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @Override
    public void quarantineCorrupt(String slot) throws IOException {
        Path main = mainPath(slot);
        if (!Files.exists(main)) return;

        Path target = rootDir.resolve(slot + ".corrupt");
        int counter = 1;
        while (Files.exists(target)) {
            target = rootDir.resolve(slot + ".corrupt." + counter);
            counter++;
        }

        Log.warn(LogCategory.DATA, "Quarantining corrupt save file '%s' to '%s'", main.getFileName(), target.getFileName());
        Files.move(main, target);
    }
}
