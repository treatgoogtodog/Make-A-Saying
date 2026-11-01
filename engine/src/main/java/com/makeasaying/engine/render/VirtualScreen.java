package com.makeasaying.engine.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.ScreenUtils;
import com.makeasaying.engine.config.EngineConfig;
import com.makeasaying.engine.util.Log;
import com.makeasaying.engine.util.LogCategory;

/**
 * Handles the 320x180 FrameBuffer rendering, integer scaling, and letterbox/pillarbox borders.
 * Uses backbuffer pixel size for accurate HiDPI display handling.
 */
public final class VirtualScreen implements Disposable {
    private final int virtualWidth;
    private final int virtualHeight;
    private final Color borderColor;

    private FrameBuffer fbo;
    private TextureRegion fboRegion;
    private SpriteBatch screenBatch;
    private VirtualScreenMath.ViewportCalculation viewportCalc;

    public VirtualScreen(EngineConfig config) {
        this.virtualWidth = config.virtualWidth();
        this.virtualHeight = config.virtualHeight();
        Color parsedColor;
        try {
            parsedColor = Color.valueOf(config.borderColor());
        } catch (Exception e) {
            Log.warn(LogCategory.RENDER, "Failed to parse border color '%s', falling back to black", config.borderColor());
            parsedColor = new Color(0f, 0f, 0f, 1f);
        }
        this.borderColor = parsedColor;
    }

    public void init() {
        // Create 320x180 RGBA8888 FBO with no depth or stencil attachment
        fbo = new FrameBuffer(Pixmap.Format.RGBA8888, virtualWidth, virtualHeight, false, false);
        Texture texture = fbo.getColorBufferTexture();
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        texture.setWrap(Texture.TextureWrap.ClampToEdge, Texture.TextureWrap.ClampToEdge);

        fboRegion = new TextureRegion(texture);
        fboRegion.flip(false, true); // Correct FBO Y-inversion

        screenBatch = new SpriteBatch();

        // Initial viewport calculation from current backbuffer size
        update(Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
    }

    public void update(int backbufferWidth, int backbufferHeight) {
        viewportCalc = VirtualScreenMath.calculate(virtualWidth, virtualHeight, backbufferWidth, backbufferHeight);
    }

    /**
     * Binds the 320x180 FrameBuffer for low-resolution scene rendering.
     */
    public void begin() {
        if (fbo == null) return;
        fbo.begin();
        Gdx.gl.glViewport(0, 0, virtualWidth, virtualHeight);
    }

    /**
     * Unbinds the FrameBuffer and renders it centered to the window backbuffer at integer scale.
     */
    public void end() {
        if (fbo == null) return;
        fbo.end();

        int backbufferW = Gdx.graphics.getBackBufferWidth();
        int backbufferH = Gdx.graphics.getBackBufferHeight();

        // 1. Clear whole window backbuffer with border color
        Gdx.gl.glViewport(0, 0, backbufferW, backbufferH);
        ScreenUtils.clear(borderColor.r, borderColor.g, borderColor.b, borderColor.a);

        // 2. Set projection matrix to backbuffer pixel dimensions
        screenBatch.getProjectionMatrix().setToOrtho2D(0, 0, backbufferW, backbufferH);

        // 3. Render scaled FBO region with nearest filtering
        screenBatch.begin();
        screenBatch.disableBlending();
        screenBatch.draw(
            fboRegion,
            viewportCalc.viewportX(),
            viewportCalc.viewportY(),
            viewportCalc.viewportWidth(),
            viewportCalc.viewportHeight()
        );
        screenBatch.end();
    }

    public VirtualScreenMath.ViewportCalculation getViewportCalculation() {
        return viewportCalc;
    }

    public int getVirtualWidth() {
        return virtualWidth;
    }

    public int getVirtualHeight() {
        return virtualHeight;
    }

    @Override
    public void dispose() {
        if (fbo != null) {
            fbo.dispose();
            fbo = null;
        }
        if (screenBatch != null) {
            screenBatch.dispose();
            screenBatch = null;
        }
    }
}
