package com.makeasaying.data.flags;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.makeasaying.data.world.RoomLoader;
import com.makeasaying.data.world.ValidationFinding;
import com.makeasaying.engine.flags.FlagRegistry;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlagUsageIndexTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void testAuthoredFlagsUsageReportsUnreadFlag() throws IOException {
        Path flagsPath = Path.of("content/flags.json");
        if (!Files.exists(flagsPath)) flagsPath = Path.of("..", "content/flags.json");
        FlagRegistry registry = FlagDefinitionLoader.load(flagsPath);

        Path r1Path = Path.of("content/rooms/test_01.json");
        if (!Files.exists(r1Path)) r1Path = Path.of("..", "content/rooms/test_01.json");
        RoomLoader.RoomData r1 = RoomLoader.loadData(r1Path);

        FlagUsageIndex index = FlagUsageIndex.build(registry, List.of(r1));
        assertNotNull(index);

        // test.door_open is read by conditional platform in test_01
        assertTrue(index.getReaders("test.door_open").size() >= 1);

        // test.stage is read by exit in test_01
        assertTrue(index.getReaders("test.stage").size() >= 1);

        // test.unread_flag has no readers
        assertTrue(index.getReaders("test.unread_flag").isEmpty());

        List<ValidationFinding> findings = index.generateFindings(registry);
        assertNotNull(findings);
        boolean foundUnread = false;
        boolean foundUnwritten = false;

        for (ValidationFinding f : findings) {
            if ("FLAG_INFO_UNREAD".equals(f.code()) && f.message().contains("test.unread_flag")) {
                foundUnread = true;
            }
            if ("FLAG_INFO_UNWRITTEN".equals(f.code()) && f.message().contains("test.stage")) {
                foundUnwritten = true;
            }
        }

        assertTrue(foundUnread, "Expected FLAG_INFO_UNREAD finding for test.unread_flag");
        assertTrue(foundUnwritten, "Expected FLAG_INFO_UNWRITTEN finding for test.stage");
    }
}
