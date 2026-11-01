package com.makeasaying.editor.core.input;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class ImGuiInputProcessorTest {
    private MockProcessor mockDownstream;
    private AtomicBoolean wantCaptureKeyboard;
    private AtomicBoolean wantCaptureMouse;
    private List<ImGuiInputProcessor.KeyEventData> sinkKeyEvents;
    private ImGuiInputProcessor processor;

    private static class MockProcessor implements InputProcessor {
        final List<String> events = new ArrayList<>();

        @Override
        public boolean keyDown(int keycode) {
            events.add("DOWN:" + keycode);
            return true;
        }

        @Override
        public boolean keyUp(int keycode) {
            events.add("UP:" + keycode);
            return true;
        }

        @Override
        public boolean keyTyped(char character) {
            events.add("TYPED:" + character);
            return true;
        }

        @Override
        public boolean touchDown(int screenX, int screenY, int pointer, int button) {
            events.add("TOUCH_DOWN:" + button);
            return true;
        }

        @Override
        public boolean touchUp(int screenX, int screenY, int pointer, int button) {
            events.add("TOUCH_UP:" + button);
            return true;
        }

        @Override public boolean touchCancelled(int screenX, int screenY, int pointer, int button) { return false; }
        @Override public boolean touchDragged(int screenX, int screenY, int pointer) { return false; }
        @Override public boolean mouseMoved(int screenX, int screenY) { return false; }
        @Override public boolean scrolled(float amountX, float amountY) { return false; }
    }

    @BeforeEach
    void setUp() {
        mockDownstream = new MockProcessor();
        wantCaptureKeyboard = new AtomicBoolean(false);
        wantCaptureMouse = new AtomicBoolean(false);
        sinkKeyEvents = new ArrayList<>();

        processor = new ImGuiInputProcessor(
            mockDownstream,
            wantCaptureKeyboard::get,
            wantCaptureMouse::get,
            sinkKeyEvents::add,
            c -> {},
            m -> {}
        );
    }

    @Test
    void testUncapturedEventsForwardDownstream() {
        processor.keyDown(Input.Keys.W);
        processor.keyUp(Input.Keys.W);

        assertEquals(List.of("DOWN:" + Input.Keys.W, "UP:" + Input.Keys.W), mockDownstream.events);
        assertEquals(2, sinkKeyEvents.size());
        assertTrue(sinkKeyEvents.get(0).down());
        assertFalse(sinkKeyEvents.get(1).down());
    }

    @Test
    void testCapturedEventsDoNotForwardDownstream() {
        wantCaptureKeyboard.set(true);

        processor.keyDown(Input.Keys.SPACE);
        processor.keyUp(Input.Keys.SPACE);

        assertTrue(mockDownstream.events.isEmpty(), "Captured events must not reach game processor");
        assertEquals(2, sinkKeyEvents.size(), "Events must still be pushed to ImGui sink");
    }

    @Test
    void testStuckKeyRuleKeyReleasedWhileCapturedAlwaysReachesGame() {
        // 1. Key down while NOT captured -> game receives DOWN
        wantCaptureKeyboard.set(false);
        processor.keyDown(Input.Keys.A);
        assertEquals(List.of("DOWN:" + Input.Keys.A), mockDownstream.events);
        assertTrue(processor.isGameKeyDown(Input.Keys.A));

        // 2. User focuses ImGui text input -> wantCaptureKeyboard becomes true
        wantCaptureKeyboard.set(true);

        // 3. Key up while captured -> MUST still reach downstream game to prevent stuck keys!
        processor.keyUp(Input.Keys.A);
        assertEquals(List.of("DOWN:" + Input.Keys.A, "UP:" + Input.Keys.A), mockDownstream.events);
        assertFalse(processor.isGameKeyDown(Input.Keys.A));
    }

    @Test
    void testStuckKeyRulePressCapturedByImGuiNeverLeaksReleaseToGame() {
        // 1. Key down while captured -> game receives NOTHING
        wantCaptureKeyboard.set(true);
        processor.keyDown(Input.Keys.D);
        assertTrue(mockDownstream.events.isEmpty());
        assertFalse(processor.isGameKeyDown(Input.Keys.D));

        // 2. ImGui loses focus -> wantCaptureKeyboard becomes false
        wantCaptureKeyboard.set(false);

        // 3. Key up while uncaptured -> must NOT leak to game
        processor.keyUp(Input.Keys.D);
        assertTrue(mockDownstream.events.isEmpty(), "Game must never receive release for key it never saw pressed");
    }

    @Test
    void testMouseCaptureFiltering() {
        wantCaptureMouse.set(false);
        processor.touchDown(100, 100, 0, 0);
        wantCaptureMouse.set(true);
        processor.touchUp(100, 100, 0, 0);

        // Mouse down reached game, so mouse up must also reach game
        assertEquals(List.of("TOUCH_DOWN:0", "TOUCH_UP:0"), mockDownstream.events);

        mockDownstream.events.clear();
        // Mouse down while captured
        wantCaptureMouse.set(true);
        processor.touchDown(150, 150, 0, 1);
        wantCaptureMouse.set(false);
        processor.touchUp(150, 150, 0, 1);

        assertTrue(mockDownstream.events.isEmpty(), "Captured mouse click must not leak release to game");
    }
}
