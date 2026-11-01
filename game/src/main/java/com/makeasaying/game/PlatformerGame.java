package com.makeasaying.game;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.ScreenUtils;
import com.makeasaying.engine.async.Jobs;
import com.makeasaying.engine.config.EngineConfig;
import com.makeasaying.engine.loop.FixedTimestep;
import com.makeasaying.engine.loop.FrameTimer;
import com.makeasaying.engine.render.VirtualScreen;
import com.makeasaying.engine.util.Log;
import com.makeasaying.engine.util.LogCategory;
import com.makeasaying.game.demo.DemoScene;

/**
 * Main game entry point managing the 1/60s fixed-timestep loop,
 * background Jobs queue, VirtualScreen integer-scaled rendering, and FrameTimer.
 */
public class PlatformerGame implements ApplicationListener {
    private final EngineConfig config;

    private FixedTimestep timestep;
    private VirtualScreen virtualScreen;
    private Jobs jobs;
    private FrameTimer frameTimer;
    private SpriteBatch sceneBatch;

    // Temporary demo scene - single deletion point for M3
    private DemoScene demoScene;

    public PlatformerGame(EngineConfig config) {
        this.config = config != null ? config : EngineConfig.defaultConfig();
    }

    @Override
    public void create() {
        Log.info(LogCategory.ENGINE,
            "Initializing game loop and rendering. Virtual: %dx%d, TickRate: %d, Workers: %d, Border: %s",
            config.virtualWidth(), config.virtualHeight(), config.tickRate(), config.workerThreads(), config.borderColor());

        this.timestep = new FixedTimestep(config.tickRate());
        this.virtualScreen = new VirtualScreen(config);
        this.virtualScreen.init();

        this.jobs = new Jobs(config.workerThreads());
        this.frameTimer = new FrameTimer();
        this.sceneBatch = new SpriteBatch();
        this.sceneBatch.getProjectionMatrix().setToOrtho2D(0, 0, config.virtualWidth(), config.virtualHeight());

        this.demoScene = new DemoScene();
    }

    @Override
    public void resize(int width, int height) {
        int backbufferW = Gdx.graphics.getBackBufferWidth();
        int backbufferH = Gdx.graphics.getBackBufferHeight();
        Log.info(LogCategory.RENDER, "Window backbuffer resized to %dx%d", backbufferW, backbufferH);
        if (virtualScreen != null) {
            virtualScreen.update(backbufferW, backbufferH);
        }
    }

    @Override
    public void render() {
        long frameStart = System.nanoTime();

        // 1. Drain background worker job completions onto main thread
        jobs.drain();

        // 2. Accumulate delta and advance fixed ticks
        float frameDelta = Gdx.graphics.getDeltaTime();
        int ticks = timestep.advance(frameDelta);

        long updateStart = System.nanoTime();
        for (int i = 0; i < ticks; i++) {
            demoScene.tick(timestep.getCurrentTick());
        }
        long updateEnd = System.nanoTime();

        // 3. Render 320x180 virtual canvas to FrameBuffer
        long renderStart = System.nanoTime();
        virtualScreen.begin();
        ScreenUtils.clear(0f, 0f, 0f, 1f);

        sceneBatch.begin();
        demoScene.render(sceneBatch, timestep.getAlpha());
        sceneBatch.end();

        // Unbind and draw FrameBuffer centered with integer scaling to backbuffer
        virtualScreen.end();
        long renderEnd = System.nanoTime();

        // 4. Record frame times with zero heap allocation
        float updateMs = (updateEnd - updateStart) / 1_000_000f;
        float renderMs = (renderEnd - renderStart) / 1_000_000f;
        frameTimer.recordFrame(updateMs, renderMs);

        // 5. Update window title once per second
        if (frameTimer.shouldUpdateTitle(frameStart)) {
            Gdx.graphics.setTitle(String.format("%s [Upd: %.2fms | Rnd: %.2fms | FPS: %d]",
                config.windowTitle(),
                frameTimer.getCachedAvgUpdate(),
                frameTimer.getCachedAvgRender(),
                Gdx.graphics.getFramesPerSecond()));
        }
    }

    @Override
    public void pause() {
        Log.info(LogCategory.ENGINE, "Game paused");
        if (timestep != null) {
            timestep.setPaused(true);
        }
    }

    @Override
    public void resume() {
        Log.info(LogCategory.ENGINE, "Game resumed");
        if (timestep != null) {
            timestep.setPaused(false);
        }
    }

    @Override
    public void dispose() {
        Log.info(LogCategory.ENGINE, "Disposing game and worker resources");
        if (jobs != null) jobs.shutdown();
        if (virtualScreen != null) virtualScreen.dispose();
        if (sceneBatch != null) sceneBatch.dispose();
        if (demoScene != null) demoScene.dispose();
    }

    public EngineConfig getConfig() {
        return config;
    }

    public FixedTimestep getTimestep() {
        return timestep;
    }

    public VirtualScreen getVirtualScreen() {
        return virtualScreen;
    }

    public Jobs getJobs() {
        return jobs;
    }

    public FrameTimer getFrameTimer() {
        return frameTimer;
    }
}
