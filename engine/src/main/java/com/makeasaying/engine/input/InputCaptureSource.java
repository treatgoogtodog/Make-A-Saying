package com.makeasaying.engine.input;

/**
 * Interface to check if an external overlay (such as ImGui in M6) is capturing input.
 */
public interface InputCaptureSource {
    boolean wantsKeyboard();
    boolean wantsMouse();

    static InputCaptureSource none() {
        return new InputCaptureSource() {
            @Override
            public boolean wantsKeyboard() {
                return false;
            }

            @Override
            public boolean wantsMouse() {
                return false;
            }
        };
    }
}
