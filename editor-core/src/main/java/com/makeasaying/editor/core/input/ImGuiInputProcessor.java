package com.makeasaying.editor.core.input;

import com.badlogic.gdx.InputProcessor;
import imgui.ImGui;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * libGDX InputProcessor bridging events to Dear ImGui and filtering downstream game input.
 * Evaluates ImGui capture at event time (DEC-022).
 * Enforces the stuck-key rule: any key pressed by the game always receives its release,
 * while keys captured by ImGui never leak releases to the game.
 * Fully testable headlessly with mockable capture suppliers.
 */
public final class ImGuiInputProcessor implements InputProcessor {
    private static final int MAX_KEYCODES = 512;

    private final InputProcessor nextProcessor;
    private final BooleanSupplier wantCaptureKeyboard;
    private final BooleanSupplier wantCaptureMouse;
    private final Consumer<KeyEventData> keyEventSink;
    private final Consumer<Character> charEventSink;
    private final Consumer<MouseEventData> mouseEventSink;

    // Stuck-key rule state: keys that were forwarded as down to nextProcessor
    private final boolean[] gameKeysDown = new boolean[MAX_KEYCODES];
    private final boolean[] gameButtonsDown = new boolean[8];

    public record KeyEventData(int gdxKey, int imGuiKey, boolean down) {}
    public record MouseEventData(int screenX, int screenY, int button, boolean down, float scrollX, float scrollY) {}

    public ImGuiInputProcessor(
        InputProcessor nextProcessor,
        BooleanSupplier wantCaptureKeyboard,
        BooleanSupplier wantCaptureMouse,
        Consumer<KeyEventData> keyEventSink,
        Consumer<Character> charEventSink,
        Consumer<MouseEventData> mouseEventSink
    ) {
        this.nextProcessor = nextProcessor;
        this.wantCaptureKeyboard = wantCaptureKeyboard != null ? wantCaptureKeyboard : () -> {
            try {
                return ImGui.getIO().getWantCaptureKeyboard();
            } catch (Throwable t) {
                return false;
            }
        };
        this.wantCaptureMouse = wantCaptureMouse != null ? wantCaptureMouse : () -> {
            try {
                return ImGui.getIO().getWantCaptureMouse();
            } catch (Throwable t) {
                return false;
            }
        };
        this.keyEventSink = keyEventSink;
        this.charEventSink = charEventSink;
        this.mouseEventSink = mouseEventSink;
    }

    public ImGuiInputProcessor(InputProcessor nextProcessor) {
        this(
            nextProcessor,
            null,
            null,
            evt -> {
                try {
                    int imKey = evt.imGuiKey();
                    if (imKey != imgui.flag.ImGuiKey.None) {
                        ImGui.getIO().addKeyEvent(imKey, evt.down());
                    }
                } catch (Throwable ignored) {}
            },
            c -> {
                try {
                    ImGui.getIO().addInputCharacter(c);
                } catch (Throwable ignored) {}
            },
            m -> {
                try {
                    if (m.button() >= 0) {
                        ImGui.getIO().setMouseDown(m.button(), m.down());
                    }
                    if (m.scrollX() != 0f || m.scrollY() != 0f) {
                        ImGui.getIO().setMouseWheel(m.scrollY());
                    }
                    ImGui.getIO().setMousePos(m.screenX(), m.screenY());
                } catch (Throwable ignored) {}
            }
        );
    }

    @Override
    public boolean keyDown(int keycode) {
        int imKey = GdxKeyToImGuiKey.toImGuiKey(keycode);
        if (keyEventSink != null) {
            keyEventSink.accept(new KeyEventData(keycode, imKey, true));
        }

        boolean captured = wantCaptureKeyboard.getAsBoolean();
        if (!captured) {
            if (keycode >= 0 && keycode < MAX_KEYCODES) {
                gameKeysDown[keycode] = true;
            }
            if (nextProcessor != null) {
                return nextProcessor.keyDown(keycode);
            }
        }
        return false;
    }

    @Override
    public boolean keyUp(int keycode) {
        int imKey = GdxKeyToImGuiKey.toImGuiKey(keycode);
        if (keyEventSink != null) {
            keyEventSink.accept(new KeyEventData(keycode, imKey, false));
        }

        boolean wasForwarded = (keycode >= 0 && keycode < MAX_KEYCODES) && gameKeysDown[keycode];
        if (wasForwarded) {
            gameKeysDown[keycode] = false;
            // Always forward release if the game saw the press (stuck-key rule)
            if (nextProcessor != null) {
                return nextProcessor.keyUp(keycode);
            }
        }
        // If the game did NOT see the press (ImGui captured it), do NOT leak the release to the game!
        return false;
    }

    @Override
    public boolean keyTyped(char character) {
        if (charEventSink != null) {
            charEventSink.accept(character);
        }

        if (!wantCaptureKeyboard.getAsBoolean() && nextProcessor != null) {
            return nextProcessor.keyTyped(character);
        }
        return false;
    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        if (mouseEventSink != null) {
            mouseEventSink.accept(new MouseEventData(screenX, screenY, button, true, 0f, 0f));
        }

        boolean captured = wantCaptureMouse.getAsBoolean();
        if (!captured) {
            if (button >= 0 && button < gameButtonsDown.length) {
                gameButtonsDown[button] = true;
            }
            if (nextProcessor != null) {
                return nextProcessor.touchDown(screenX, screenY, pointer, button);
            }
        }
        return false;
    }

    @Override
    public boolean touchUp(int screenX, int screenY, int pointer, int button) {
        if (mouseEventSink != null) {
            mouseEventSink.accept(new MouseEventData(screenX, screenY, button, false, 0f, 0f));
        }

        boolean wasForwarded = (button >= 0 && button < gameButtonsDown.length) && gameButtonsDown[button];
        if (wasForwarded) {
            gameButtonsDown[button] = false;
            if (nextProcessor != null) {
                return nextProcessor.touchUp(screenX, screenY, pointer, button);
            }
        }
        return false;
    }

    @Override
    public boolean touchCancelled(int screenX, int screenY, int pointer, int button) {
        if (nextProcessor != null) {
            return nextProcessor.touchCancelled(screenX, screenY, pointer, button);
        }
        return false;
    }

    @Override
    public boolean touchDragged(int screenX, int screenY, int pointer) {
        if (mouseEventSink != null) {
            mouseEventSink.accept(new MouseEventData(screenX, screenY, -1, false, 0f, 0f));
        }

        if (!wantCaptureMouse.getAsBoolean() && nextProcessor != null) {
            return nextProcessor.touchDragged(screenX, screenY, pointer);
        }
        return false;
    }

    @Override
    public boolean mouseMoved(int screenX, int screenY) {
        if (mouseEventSink != null) {
            mouseEventSink.accept(new MouseEventData(screenX, screenY, -1, false, 0f, 0f));
        }

        if (!wantCaptureMouse.getAsBoolean() && nextProcessor != null) {
            return nextProcessor.mouseMoved(screenX, screenY);
        }
        return false;
    }

    @Override
    public boolean scrolled(float amountX, float amountY) {
        if (mouseEventSink != null) {
            mouseEventSink.accept(new MouseEventData(0, 0, -1, false, amountX, amountY));
        }

        if (!wantCaptureMouse.getAsBoolean() && nextProcessor != null) {
            return nextProcessor.scrolled(amountX, amountY);
        }
        return false;
    }

    public boolean isGameKeyDown(int keycode) {
        return keycode >= 0 && keycode < MAX_KEYCODES && gameKeysDown[keycode];
    }
}
