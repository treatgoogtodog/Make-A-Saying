package com.makeasaying.editor.core.imgui;

import com.badlogic.gdx.utils.Disposable;
import com.makeasaying.engine.util.Log;
import com.makeasaying.engine.util.LogCategory;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiConfigFlags;
import imgui.gl3.ImGuiImplGl3;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Manages Dear ImGui lifecycle on LWJGL3 GL context per M6a brief.
 * Initializes docking, routes rendering via ImGuiImplGl3 (#version 150),
 * sets ini path to per-user data directory, and computes HiDPI framebuffer scale.
 */
public final class ImGuiHost implements Disposable {
    private final ImGuiImplGl3 imGuiGl3 = new ImGuiImplGl3();
    private boolean initialized = false;

    public ImGuiHost() {}

    public void init() {
        if (initialized) return;

        ImGui.createContext();
        ImGuiIO io = ImGui.getIO();
        io.addConfigFlags(ImGuiConfigFlags.DockingEnable);

        // Store layout in per-user directory (never in content/)
        try {
            Path userDir = Path.of(System.getProperty("user.home"), ".makeasaying");
            Files.createDirectories(userDir);
            io.setIniFilename(userDir.resolve("imgui.ini").toString());
        } catch (Exception e) {
            Log.warn(LogCategory.RENDER, "Failed setting user ini directory for ImGui, disabling persistence: %s", e.getMessage());
            io.setIniFilename(null);
        }

        imGuiGl3.init("#version 150");
        initialized = true;
        Log.info(LogCategory.RENDER, "ImGuiHost initialized with DockingEnable on GL3 #version 150");
    }

    public void newFrame(int logicalWidth, int logicalHeight, int backbufferWidth, int backbufferHeight, float delta) {
        if (!initialized) return;

        ImGuiIO io = ImGui.getIO();
        io.setDisplaySize(logicalWidth, logicalHeight);
        float scaleX = logicalWidth > 0 ? (float) backbufferWidth / logicalWidth : 1.0f;
        float scaleY = logicalHeight > 0 ? (float) backbufferHeight / logicalHeight : 1.0f;
        io.setDisplayFramebufferScale(scaleX, scaleY);
        io.setDeltaTime(Math.max(1f / 1000f, delta));

        ImGui.newFrame();
    }

    public void render() {
        if (!initialized) return;

        ImGui.render();
        imGuiGl3.renderDrawData(ImGui.getDrawData());
    }

    @Override
    public void dispose() {
        if (!initialized) return;
        imGuiGl3.shutdown();
        ImGui.destroyContext();
        initialized = false;
        Log.info(LogCategory.RENDER, "ImGuiHost disposed");
    }

    public boolean isInitialized() {
        return initialized;
    }
}
