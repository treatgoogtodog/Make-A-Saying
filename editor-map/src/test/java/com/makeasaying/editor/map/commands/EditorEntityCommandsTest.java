package com.makeasaying.editor.map.commands;

import com.makeasaying.editor.core.command.UndoStack;
import com.makeasaying.editor.map.document.RoomDocument;
import com.makeasaying.engine.world.RoomEntityData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EditorEntityCommandsTest {
    private RoomDocument document;
    private UndoStack undoStack;

    @BeforeEach
    void setUp() {
        undoStack = new UndoStack();
        document = new RoomDocument("test_room", 20, 10, undoStack);
        document.setEntitySeq(1);
        document.setSavedFloor(0);
    }

    @Test
    void testPlaceAndUndoEntityCommand() {
        int priorSeq = document.allocateEntitySeq(); // seq 1 allocated, entitySeq becomes 2
        RoomEntityData entity = new RoomEntityData("moving_platform_1", "moving_platform", 50, 50, Map.of());

        undoStack.execute(new EntityCommands.PlaceEntityCommand(document, entity, priorSeq));

        assertEquals(1, document.getEntities().size());
        assertEquals("moving_platform_1", document.getEntities().get(0).instanceId());

        // Undo
        undoStack.undo();
        assertEquals(0, document.getEntities().size());
        assertEquals(1, document.getEntitySeq(), "Undo must restore entitySeq counter");

        // Redo
        undoStack.redo();
        assertEquals(1, document.getEntities().size());
        assertEquals("moving_platform_1", document.getEntities().get(0).instanceId());
    }

    @Test
    void testMoveEntityCommand() {
        RoomEntityData entity = new RoomEntityData("moving_platform_1", "moving_platform", 50, 50, Map.of());
        document.addEntity(entity);

        undoStack.execute(new EntityCommands.MoveEntityCommand(document, "moving_platform_1", 50, 50, 80, 90));
        assertEquals(80, document.getEntity("moving_platform_1").x());
        assertEquals(90, document.getEntity("moving_platform_1").y());

        undoStack.undo();
        assertEquals(50, document.getEntity("moving_platform_1").x());
        assertEquals(50, document.getEntity("moving_platform_1").y());

        undoStack.redo();
        assertEquals(80, document.getEntity("moving_platform_1").x());
        assertEquals(90, document.getEntity("moving_platform_1").y());
    }

    @Test
    void testDeleteEntityCommand() {
        RoomEntityData entity = new RoomEntityData("moving_platform_1", "moving_platform", 50, 50, Map.of());
        document.addEntity(entity);

        undoStack.execute(new EntityCommands.DeleteEntityCommand(document, entity));
        assertNull(document.getEntity("moving_platform_1"));

        undoStack.undo();
        assertNotNull(document.getEntity("moving_platform_1"));
        assertEquals(50, document.getEntity("moving_platform_1").x());

        undoStack.redo();
        assertNull(document.getEntity("moving_platform_1"));
    }

    @Test
    void testEditOverrideFieldCommand() {
        RoomEntityData entity = new RoomEntityData("moving_platform_1", "moving_platform", 50, 50, Map.of());
        document.addEntity(entity);

        undoStack.execute(new EntityCommands.EditOverrideFieldCommand(
            document, "moving_platform_1", "platform", "speed", null, 60.0f
        ));
        RoomEntityData updated = document.getEntity("moving_platform_1");
        assertTrue(updated.overrides().containsKey("platform"));
        assertEquals(60.0f, ((Number) updated.overrides().get("platform").get("speed")).floatValue());

        undoStack.undo();
        RoomEntityData reverted = document.getEntity("moving_platform_1");
        assertFalse(reverted.overrides().containsKey("platform"));

        undoStack.redo();
        RoomEntityData redone = document.getEntity("moving_platform_1");
        assertTrue(redone.overrides().containsKey("platform"));
        assertEquals(60.0f, ((Number) redone.overrides().get("platform").get("speed")).floatValue());
    }

    @Test
    void testMovePlatformEndCommand() {
        RoomEntityData entity = new RoomEntityData("moving_platform_1", "moving_platform", 50, 50, Map.of(
            "platform", Map.of("travelX", 0, "travelY", 0)
        ));
        document.addEntity(entity);

        undoStack.execute(new EntityCommands.MovePlatformEndCommand(
            document, "moving_platform_1", 0, 0, 80, -32
        ));
        RoomEntityData updated = document.getEntity("moving_platform_1");
        assertEquals(80, ((Number) updated.overrides().get("platform").get("travelX")).intValue());
        assertEquals(-32, ((Number) updated.overrides().get("platform").get("travelY")).intValue());

        undoStack.undo();
        RoomEntityData reverted = document.getEntity("moving_platform_1");
        assertEquals(0, ((Number) reverted.overrides().get("platform").get("travelX")).intValue());
        assertEquals(0, ((Number) reverted.overrides().get("platform").get("travelY")).intValue());

        undoStack.redo();
        RoomEntityData redone = document.getEntity("moving_platform_1");
        assertEquals(80, ((Number) redone.overrides().get("platform").get("travelX")).intValue());
        assertEquals(-32, ((Number) redone.overrides().get("platform").get("travelY")).intValue());
    }

    @Test
    void testSavedFloorNeverReusesDiskSavedIdsAfterUndoPastSave() {
        // DEC-028: Suffixes that have reached disk are never reused within a room.
        int seq1 = document.allocateEntitySeq(); // 1, next is 2
        RoomEntityData e1 = new RoomEntityData("moving_platform_1", "moving_platform", 30, 30, Map.of());
        undoStack.execute(new EntityCommands.PlaceEntityCommand(document, e1, seq1));

        // Save room to disk
        document.updateSavedFloorOnSave();
        assertEquals(1, document.getSavedFloor(), "savedFloor must advance to 1 on save");

        // Place second entity
        int seq2 = document.allocateEntitySeq(); // 2, next is 3
        RoomEntityData e2 = new RoomEntityData("moving_platform_2", "moving_platform", 60, 60, Map.of());
        undoStack.execute(new EntityCommands.PlaceEntityCommand(document, e2, seq2));
        assertEquals(3, document.getEntitySeq());

        // Undo second entity placement -> restored to 2 (allowed since 2 > savedFloor 1)
        undoStack.undo();
        assertEquals(2, document.getEntitySeq());

        // Delete first entity (which is saved on disk)
        undoStack.execute(new EntityCommands.DeleteEntityCommand(document, e1));

        // Save room again
        document.updateSavedFloorOnSave();
        assertEquals(1, document.getSavedFloor(), "savedFloor remains at least 1");

        // Now allocate next entity: seq must be at least 2, NEVER reusing 1!
        int nextAllocated = document.allocateEntitySeq();
        assertEquals(2, nextAllocated, "Never reuse ID 1 which previously reached disk");
    }
}
