package com.makeasaying.data.util;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/**
 * Atomic file writer per AGENTS.md Section 7.8 and M6a brief.
 * Protocol:
 * 1. Creates parent directory if missing.
 * 2. Writes content to temporary file ({target}.tmp) in the same directory.
 * 3. Flushes and syncs to disk (FileChannel.force(true)).
 * 4. If target file already exists, creates backup ({target}.bak).
 * 5. Atomically renames/moves temporary file to target.
 * 6. Ensures temp file cleanup on failure.
 */
public final class AtomicFileWriter {

    private AtomicFileWriter() {}

    public static void write(Path targetPath, String content) throws IOException {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        write(targetPath, bytes);
    }

    public static void write(Path targetPath, byte[] bytes) throws IOException {
        if (targetPath == null) throw new IllegalArgumentException("targetPath must not be null");
        if (bytes == null) throw new IllegalArgumentException("bytes must not be null");

        Path parent = targetPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        String fileName = targetPath.getFileName().toString();
        Path tempPath = targetPath.resolveSibling(fileName + ".tmp");
        Path backupPath = targetPath.resolveSibling(fileName + ".bak");

        // Write to temp file and flush/sync
        try {
            try (FileChannel channel = FileChannel.open(
                tempPath,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING
            )) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }

            // Create backup if target file exists
            if (Files.exists(targetPath)) {
                Files.copy(targetPath, backupPath, StandardCopyOption.REPLACE_EXISTING);
            }

            // Atomic move to target
            try {
                Files.move(tempPath, targetPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tempPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            try {
                Files.deleteIfExists(tempPath);
            } catch (IOException ignored) {
            }
        }
    }
}
