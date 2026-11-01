package com.makeasaying.data.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class AtomicFileWriterTest {

    @TempDir
    Path tempDir;

    @Test
    public void testAtomicWriteCreatesFileAndCleansTemp() throws IOException {
        Path target = tempDir.resolve("nested/dir/test_file.txt");
        String content = "Hello Atomic World!\n";

        AtomicFileWriter.write(target, content);

        assertTrue(Files.exists(target));
        assertEquals(content, Files.readString(target, StandardCharsets.UTF_8));

        Path tempPath = target.resolveSibling("test_file.txt.tmp");
        assertFalse(Files.exists(tempPath), "Temp file must be cleaned up");

        Path bakPath = target.resolveSibling("test_file.txt.bak");
        assertFalse(Files.exists(bakPath), "Bak file should not exist on initial write");
    }

    @Test
    public void testAtomicWriteCreatesBakWhenOverwriting() throws IOException {
        Path target = tempDir.resolve("room.json");
        String initialContent = "{\"version\": 1, \"state\": \"initial\"}\n";
        AtomicFileWriter.write(target, initialContent);

        String updatedContent = "{\"version\": 1, \"state\": \"updated\"}\n";
        AtomicFileWriter.write(target, updatedContent);

        assertTrue(Files.exists(target));
        assertEquals(updatedContent, Files.readString(target, StandardCharsets.UTF_8));

        Path bakPath = tempDir.resolve("room.json.bak");
        assertTrue(Files.exists(bakPath), "Bak file must exist after overwriting");
        assertEquals(initialContent, Files.readString(bakPath, StandardCharsets.UTF_8));

        Path tempPath = tempDir.resolve("room.json.tmp");
        assertFalse(Files.exists(tempPath), "Temp file must be cleaned up");
    }
}
