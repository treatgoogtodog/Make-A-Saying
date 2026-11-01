package com.makeasaying.editor.map.document;

import com.makeasaying.data.world.RoomLoader;
import com.makeasaying.data.world.RoomWriter;
import com.makeasaying.editor.core.command.UndoStack;
import com.makeasaying.editor.core.raster.GeometryRasterizer;
import com.makeasaying.editor.map.commands.ChangeTilesetAliasCommand;
import com.makeasaying.editor.map.commands.ExitCommands;
import com.makeasaying.editor.map.commands.PaintBatchCommand;
import com.makeasaying.editor.map.commands.ResizeRoomCommand;
import com.makeasaying.editor.map.commands.SpawnPointCommands;
import com.makeasaying.editor.map.commands.TilesetManagementCommands;
import com.makeasaying.engine.world.Room;
import com.makeasaying.engine.world.RoomExit;
import com.makeasaying.engine.world.RoomLayer;
import com.makeasaying.engine.world.RoomLayerType;
import com.makeasaying.engine.world.SpawnPoint;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class RoomDocumentTest {

    @Test
    public void testRoundTripByteIdenticalForAuthoredRooms() throws IOException {
        String[] roomFiles = {"test_01.json", "test_02.json"};
        for (String file : roomFiles) {
            Path path = Path.of("content/rooms", file);
            if (!Files.exists(path)) continue;

            Room originalRoom = RoomLoader.load(path);
            String originalJson = RoomWriter.toJson(originalRoom, 1);

            UndoStack undoStack = new UndoStack();
            RoomDocument doc = RoomDocument.fromRoom(originalRoom, undoStack);

            assertFalse(undoStack.isDirty(), "Newly loaded doc must not be dirty");
            assertEquals(originalRoom.id(), doc.getId());
            assertEquals(originalRoom.width(), doc.getWidth());
            assertEquals(originalRoom.height(), doc.getHeight());

            Room convertedBack = doc.toRoom();
            String reserializedJson = RoomWriter.toJson(convertedBack, 1);

            assertEquals(originalJson, reserializedJson, "Round-trip serialization must be byte-identical for " + file);
        }
    }

    @Test
    public void testPaintBatchCommandAndUndoRedo() {
        UndoStack undoStack = new UndoStack();
        RoomDocument doc = new RoomDocument("test_room", 10, 10, undoStack);

        assertEquals(RoomLayer.EMPTY_TOKEN, doc.getToken(RoomLayerType.MAIN, 2, 3));

        PaintBatchCommand paintCmd = PaintBatchCommand.create(
            doc,
            RoomLayerType.MAIN,
            "Paint block",
            List.of(new GeometryRasterizer.Point(2, 3), new GeometryRasterizer.Point(2, 4)),
            "a:solid"
        );

        undoStack.execute(paintCmd);

        assertEquals("a:solid", doc.getToken(RoomLayerType.MAIN, 2, 3));
        assertEquals("a:solid", doc.getToken(RoomLayerType.MAIN, 2, 4));
        assertTrue(undoStack.isDirty());

        undoStack.undo();
        assertEquals(RoomLayer.EMPTY_TOKEN, doc.getToken(RoomLayerType.MAIN, 2, 3));
        assertEquals(RoomLayer.EMPTY_TOKEN, doc.getToken(RoomLayerType.MAIN, 2, 4));

        undoStack.redo();
        assertEquals("a:solid", doc.getToken(RoomLayerType.MAIN, 2, 3));
        assertEquals("a:solid", doc.getToken(RoomLayerType.MAIN, 2, 4));
    }

    @Test
    public void testLayerLockEnforcement() {
        UndoStack undoStack = new UndoStack();
        RoomDocument doc = new RoomDocument("test_room", 10, 10, undoStack);

        doc.setLayerLocked(RoomLayerType.MAIN, true);
        assertTrue(doc.isLayerLocked(RoomLayerType.MAIN));

        PaintBatchCommand paintCmd = PaintBatchCommand.create(
            doc,
            RoomLayerType.MAIN,
            "Paint locked",
            List.of(new GeometryRasterizer.Point(1, 1)),
            "a:solid"
        );

        assertTrue(paintCmd.isEmpty(), "Command created on locked layer must be empty");
        undoStack.execute(paintCmd);
        assertEquals(RoomLayer.EMPTY_TOKEN, doc.getToken(RoomLayerType.MAIN, 1, 1));
    }

    @Test
    public void testChangeTilesetAliasCommand() {
        UndoStack undoStack = new UndoStack();
        RoomDocument doc = new RoomDocument("test_room", 5, 5, undoStack);
        doc.getTilesetAliases().put("a", "test_area");

        PaintBatchCommand paintCmd = PaintBatchCommand.create(
            doc,
            RoomLayerType.MAIN,
            "Paint",
            List.of(new GeometryRasterizer.Point(1, 1)),
            "a:tile_01"
        );
        undoStack.execute(paintCmd);
        assertEquals("a:tile_01", doc.getToken(RoomLayerType.MAIN, 1, 1));

        ChangeTilesetAliasCommand changeCmd = new ChangeTilesetAliasCommand(doc, "a", "lab");
        undoStack.execute(changeCmd);

        assertEquals("test_area", doc.getTilesetAliases().get("lab"));
        assertNull(doc.getTilesetAliases().get("a"));
        assertEquals("lab:tile_01", doc.getToken(RoomLayerType.MAIN, 1, 1));

        undoStack.undo();
        assertEquals("test_area", doc.getTilesetAliases().get("a"));
        assertEquals("a:tile_01", doc.getToken(RoomLayerType.MAIN, 1, 1));
    }

    @Test
    public void testTilesetManagementAddAndRemoveBlockedWhenUsed() {
        UndoStack undoStack = new UndoStack();
        RoomDocument doc = new RoomDocument("test_room", 5, 5, undoStack);

        TilesetManagementCommands.AddTilesetCommand addCmd =
            new TilesetManagementCommands.AddTilesetCommand(doc, "b", "test_area_b");
        undoStack.execute(addCmd);
        assertEquals("test_area_b", doc.getTilesetAliases().get("b"));

        // Paint a tile with prefix 'b:'
        PaintBatchCommand paintCmd = PaintBatchCommand.create(
            doc,
            RoomLayerType.FOREGROUND,
            "Paint",
            List.of(new GeometryRasterizer.Point(0, 0)),
            "b:flora"
        );
        undoStack.execute(paintCmd);

        // Removal must be blocked
        assertThrows(IllegalStateException.class, () -> new TilesetManagementCommands.RemoveTilesetCommand(doc, "b"));

        // Clear the tile
        PaintBatchCommand clearCmd = PaintBatchCommand.create(
            doc,
            RoomLayerType.FOREGROUND,
            "Clear",
            List.of(new GeometryRasterizer.Point(0, 0)),
            RoomLayer.EMPTY_TOKEN
        );
        undoStack.execute(clearCmd);

        // Now remove should succeed
        TilesetManagementCommands.RemoveTilesetCommand removeCmd =
            new TilesetManagementCommands.RemoveTilesetCommand(doc, "b");
        undoStack.execute(removeCmd);
        assertNull(doc.getTilesetAliases().get("b"));

        undoStack.undo();
        assertEquals("test_area_b", doc.getTilesetAliases().get("b"));
    }

    @Test
    public void testResizeRoomCommand() {
        UndoStack undoStack = new UndoStack();
        RoomDocument doc = new RoomDocument("test_room", 3, 3, undoStack);

        PaintBatchCommand paintCmd = PaintBatchCommand.create(
            doc,
            RoomLayerType.MAIN,
            "Paint",
            List.of(new GeometryRasterizer.Point(1, 1)),
            "a:solid"
        );
        undoStack.execute(paintCmd);

        ResizeRoomCommand resizeCmd = new ResizeRoomCommand(doc, 5, 5, ResizeRoomCommand.Anchor.BOTTOM_LEFT);
        undoStack.execute(resizeCmd);

        assertEquals(5, doc.getWidth());
        assertEquals(5, doc.getHeight());
        assertEquals("a:solid", doc.getToken(RoomLayerType.MAIN, 1, 1));

        undoStack.undo();
        assertEquals(3, doc.getWidth());
        assertEquals(3, doc.getHeight());
        assertEquals("a:solid", doc.getToken(RoomLayerType.MAIN, 1, 1));
    }

    @Test
    public void testSpawnAndExitCommands() {
        UndoStack undoStack = new UndoStack();
        RoomDocument doc = new RoomDocument("test_room", 20, 20, undoStack);

        SpawnPoint sp = new SpawnPoint("default", 32, 48);
        undoStack.execute(new SpawnPointCommands.AddSpawnPointCommand(doc, sp));
        assertEquals(1, doc.getSpawnPoints().size());
        assertEquals("default", doc.getSpawnPoints().get(0).id());

        undoStack.execute(new SpawnPointCommands.MoveSpawnPointCommand(doc, "default", 32, 48, 64, 80));
        assertEquals(64, doc.getSpawnPoints().get(0).x());
        assertEquals(80, doc.getSpawnPoints().get(0).y());

        RoomExit exit = new RoomExit("exit_right", 19 * 16, 0, 16, 32, "room_02", "default");
        undoStack.execute(new ExitCommands.AddExitCommand(doc, exit));
        assertEquals(1, doc.getExits().size());
        assertEquals("exit_right", doc.getExits().get(0).id());

        undoStack.undo(); // undo exit add
        assertTrue(doc.getExits().isEmpty());

        undoStack.undo(); // undo spawn move
        assertEquals(32, doc.getSpawnPoints().get(0).x());

        undoStack.undo(); // undo spawn add
        assertTrue(doc.getSpawnPoints().isEmpty());
    }
}
