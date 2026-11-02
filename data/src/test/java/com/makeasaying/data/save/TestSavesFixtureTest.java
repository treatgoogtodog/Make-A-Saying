package com.makeasaying.data.save;

import com.makeasaying.engine.flags.FlagDefinition;
import com.makeasaying.engine.flags.FlagRegistry;
import com.makeasaying.engine.flags.FlagScope;
import com.makeasaying.engine.flags.FlagType;
import com.makeasaying.engine.save.SaveEnvelope;
import com.makeasaying.engine.save.SavePayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestSavesFixtureTest {
    private FlagRegistry registry;

    @BeforeEach
    void setUp() {
        FlagDefinition fStage = new FlagDefinition(0, "test.stage", FlagType.INT, FlagScope.GLOBAL, 0, 0, 10, null, "doc");
        FlagDefinition fUnread = new FlagDefinition(1, "test.unread_flag", FlagType.BOOL, FlagScope.GLOBAL, 0, 0, 1, null, "doc");
        FlagDefinition fDoor = new FlagDefinition(2, "test.door_open", FlagType.BOOL, FlagScope.ROOM, 0, 0, 1, null, "doc");
        registry = new FlagRegistry(List.of(fStage, fUnread, fDoor));
    }

    @Test
    void testCreateAndValidateAuthoredSaveFixtures() throws IOException {
        Path saveDir = Path.of("content/test_saves");
        if (!Files.exists(saveDir)) saveDir = Path.of("../content/test_saves");
        Files.createDirectories(saveDir);

        // 1. Fresh save fixture
        SavePayload freshPayload = new SavePayload(
            0L,
            "2026-11-02T08:00:00Z",
            "test_01",
            "savepoint_3",
            Map.of("test.stage", 0, "test.unread_flag", false),
            Map.of("test_01", Collections.emptyMap()),
            Collections.emptyMap()
        );
        String freshJson = SaveWriter.serialize(freshPayload, registry);
        Path freshPath = saveDir.resolve("fresh_save.json");
        Files.writeString(freshPath, freshJson);

        // 2. Mid progress save fixture
        SavePayload midPayload = new SavePayload(
            1800L,
            "2026-11-02T08:30:00Z",
            "test_02",
            "savepoint_2",
            Map.of("test.stage", 3, "test.unread_flag", false),
            Map.of(
                "test_01", Map.of("flag.test.door_open", Map.of("type", "INT", "value", 1)),
                "test_02", Map.of("custom.visited", 1)
            ),
            Collections.emptyMap()
        );
        String midJson = SaveWriter.serialize(midPayload, registry);
        Path midPath = saveDir.resolve("mid_progress_save.json");
        Files.writeString(midPath, midJson);

        // 3. Verify both fixtures load cleanly through SaveLoader
        SaveEnvelope freshLoaded = SaveLoader.load(Files.readString(freshPath), registry);
        assertNotNull(freshLoaded);
        assertEquals(0L, freshLoaded.data().playtimeTicks());
        assertEquals("test_01", freshLoaded.data().currentRoomId());
        assertEquals("savepoint_3", freshLoaded.data().savepointInstanceId());
        assertEquals(0, freshLoaded.data().globalFlags().get("test.stage"));

        SaveEnvelope midLoaded = SaveLoader.load(Files.readString(midPath), registry);
        assertNotNull(midLoaded);
        assertEquals(1800L, midLoaded.data().playtimeTicks());
        assertEquals("test_02", midLoaded.data().currentRoomId());
        assertEquals("savepoint_2", midLoaded.data().savepointInstanceId());
        assertEquals(3, midLoaded.data().globalFlags().get("test.stage"));
        assertTrue(midLoaded.data().roomRecords().containsKey("test_01"));
        assertTrue(midLoaded.data().roomRecords().containsKey("test_02"));
    }
}
