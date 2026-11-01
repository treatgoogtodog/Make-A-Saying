package com.makeasaying.editor.map.tools;

import com.makeasaying.editor.core.command.UndoStack;
import com.makeasaying.editor.map.document.RoomDocument;
import com.makeasaying.engine.world.RoomLayer;
import com.makeasaying.engine.world.RoomLayerType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class EditorToolTest {

    private RoomDocument doc;
    private UndoStack undoStack;

    @BeforeEach
    public void setup() {
        undoStack = new UndoStack();
        doc = new RoomDocument("test_room", 10, 10, undoStack);
    }

    @Test
    public void testPencilToolDrawsContinuousLineAndUndos() {
        PencilTool tool = new PencilTool();
        tool.onMouseDown(doc, RoomLayerType.MAIN, "a:solid", 1, 1);
        tool.onMouseDrag(doc, RoomLayerType.MAIN, "a:solid", 3, 1);
        tool.onMouseUp(doc, RoomLayerType.MAIN, "a:solid", 3, 1);

        assertEquals("a:solid", doc.getToken(RoomLayerType.MAIN, 1, 1));
        assertEquals("a:solid", doc.getToken(RoomLayerType.MAIN, 2, 1));
        assertEquals("a:solid", doc.getToken(RoomLayerType.MAIN, 3, 1));
        assertEquals(RoomLayer.EMPTY_TOKEN, doc.getToken(RoomLayerType.MAIN, 4, 1));

        undoStack.undo();
        assertEquals(RoomLayer.EMPTY_TOKEN, doc.getToken(RoomLayerType.MAIN, 1, 1));
        assertEquals(RoomLayer.EMPTY_TOKEN, doc.getToken(RoomLayerType.MAIN, 2, 1));
        assertEquals(RoomLayer.EMPTY_TOKEN, doc.getToken(RoomLayerType.MAIN, 3, 1));
    }

    @Test
    public void testEraserToolReplacesWithEmptyToken() {
        // First paint a tile
        PencilTool pencil = new PencilTool();
        pencil.onMouseDown(doc, RoomLayerType.MAIN, "a:solid", 2, 2);
        pencil.onMouseUp(doc, RoomLayerType.MAIN, "a:solid", 2, 2);
        assertEquals("a:solid", doc.getToken(RoomLayerType.MAIN, 2, 2));

        // Erase it
        EraserTool eraser = new EraserTool();
        eraser.onMouseDown(doc, RoomLayerType.MAIN, "a:solid", 2, 2);
        eraser.onMouseUp(doc, RoomLayerType.MAIN, "a:solid", 2, 2);
        assertEquals(RoomLayer.EMPTY_TOKEN, doc.getToken(RoomLayerType.MAIN, 2, 2));

        undoStack.undo();
        assertEquals("a:solid", doc.getToken(RoomLayerType.MAIN, 2, 2));
    }

    @Test
    public void testLineTool() {
        LineTool lineTool = new LineTool();
        lineTool.onMouseDown(doc, RoomLayerType.MAIN, "a:solid", 0, 0);
        lineTool.onMouseDrag(doc, RoomLayerType.MAIN, "a:solid", 0, 4);
        lineTool.onMouseUp(doc, RoomLayerType.MAIN, "a:solid", 0, 4);

        for (int y = 0; y <= 4; y++) {
            assertEquals("a:solid", doc.getToken(RoomLayerType.MAIN, 0, y));
        }
        assertEquals(RoomLayer.EMPTY_TOKEN, doc.getToken(RoomLayerType.MAIN, 1, 0));
    }

    @Test
    public void testRectToolOutlineAndFilled() {
        RectTool outlineTool = new RectTool(false);
        outlineTool.onMouseDown(doc, RoomLayerType.MAIN, "a:solid", 1, 1);
        outlineTool.onMouseUp(doc, RoomLayerType.MAIN, "a:solid", 3, 3);

        // Border should be solid
        assertEquals("a:solid", doc.getToken(RoomLayerType.MAIN, 1, 1));
        assertEquals("a:solid", doc.getToken(RoomLayerType.MAIN, 3, 3));
        assertEquals("a:solid", doc.getToken(RoomLayerType.MAIN, 1, 2));
        // Interior (2, 2) must remain empty in outline mode
        assertEquals(RoomLayer.EMPTY_TOKEN, doc.getToken(RoomLayerType.MAIN, 2, 2));

        RectTool filledTool = new RectTool(true);
        filledTool.onMouseDown(doc, RoomLayerType.FOREGROUND, "b:flora", 1, 1);
        filledTool.onMouseUp(doc, RoomLayerType.FOREGROUND, "b:flora", 3, 3);

        // Interior (2, 2) must be filled in filled mode
        assertEquals("b:flora", doc.getToken(RoomLayerType.FOREGROUND, 2, 2));
    }

    @Test
    public void testFloodFillTool() {
        // Enclose a 3x3 region with solid
        RectTool outline = new RectTool(false);
        outline.onMouseDown(doc, RoomLayerType.MAIN, "a:wall", 1, 1);
        outline.onMouseUp(doc, RoomLayerType.MAIN, "a:wall", 5, 5);

        // Flood fill the center at (3, 3)
        FloodFillTool floodFill = new FloodFillTool();
        floodFill.onMouseDown(doc, RoomLayerType.MAIN, "a:water", 3, 3);

        assertEquals("a:water", doc.getToken(RoomLayerType.MAIN, 2, 2));
        assertEquals("a:water", doc.getToken(RoomLayerType.MAIN, 3, 3));
        assertEquals("a:water", doc.getToken(RoomLayerType.MAIN, 4, 4));
        // Outside the enclosure should remain empty
        assertEquals(RoomLayer.EMPTY_TOKEN, doc.getToken(RoomLayerType.MAIN, 0, 0));
        assertEquals(RoomLayer.EMPTY_TOKEN, doc.getToken(RoomLayerType.MAIN, 6, 6));
    }

    @Test
    public void testEyeDropperTool() {
        doc.setTokenInternal(RoomLayerType.MAIN, 4, 5, "a:slope_up_right");

        AtomicReference<String> picked = new AtomicReference<>();
        EyeDropperTool dropper = new EyeDropperTool(picked::set);

        dropper.onMouseDown(doc, RoomLayerType.MAIN, "ignored", 4, 5);
        assertEquals("a:slope_up_right", picked.get());
    }
}
