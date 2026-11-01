package com.makeasaying.game;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.ScreenUtils;
import com.makeasaying.data.input.ActionLoader;
import com.makeasaying.data.input.BindingsLoader;
import com.makeasaying.data.tuning.MovementTuningLoader;
import com.makeasaying.data.tuning.MovementTuningWatcher;
import com.makeasaying.engine.async.Jobs;
import com.makeasaying.engine.config.EngineConfig;
import com.makeasaying.engine.entity.MovementTuning;
import com.makeasaying.engine.input.ActionRegistry;
import com.makeasaying.engine.input.Actions;
import com.makeasaying.engine.input.ContextBindings;
import com.makeasaying.engine.input.InputManager;
import com.makeasaying.engine.input.InputState;
import com.makeasaying.engine.input.replay.InputRecorder;
import com.makeasaying.engine.input.replay.InputReplayer;
import com.makeasaying.engine.input.replay.ReplayData;
import com.makeasaying.engine.loop.FixedTimestep;
import com.makeasaying.engine.loop.FrameTimer;
import com.makeasaying.engine.render.VirtualScreen;
import com.makeasaying.engine.util.Log;
import com.makeasaying.engine.util.LogCategory;
import com.makeasaying.engine.world.GameStateHash;
import com.makeasaying.game.demo.DemoSceneM5b;

import java.nio.file.Path;

/**
 * Main game entry point managing the fixed-timestep loop, input layer,
 * background Jobs, hot-reloading tuning, and VirtualScreen integer-scaled rendering.
 */
public class PlatformerGame implements ApplicationListener {
    private final EngineConfig config;

    private FixedTimestep timestep;
    private VirtualScreen virtualScreen;
    private Jobs jobs;
    private FrameTimer frameTimer;
    private SpriteBatch sceneBatch;

    private ActionRegistry actionRegistry;
    private ContextBindings contextBindings;
    private InputManager inputManager;

    private MovementTuning movementTuning;
    private MovementTuningWatcher tuningWatcher;

    private InputRecorder recorder;
    private InputReplayer replayer;
    private ReplayData lastRecordedReplay;

    private DemoSceneM5b demoScene;

    public PlatformerGame(EngineConfig config) {
        this.config = config != null ? config : EngineConfig.defaultConfig();
    }

    @Override
    public void create() {
        Log.info(LogCategory.ENGINE,
            "Initializing game. Virtual: %dx%d, TickRate: %d, Workers: %d, Border: %s",
            config.virtualWidth(), config.virtualHeight(), config.tickRate(), config.workerThreads(), config.borderColor());

        this.timestep = new FixedTimestep(config.tickRate());
        this.virtualScreen = new VirtualScreen(config);
        this.virtualScreen.init();

        this.jobs = new Jobs(config.workerThreads());
        this.frameTimer = new FrameTimer();
        this.sceneBatch = new SpriteBatch();
        this.sceneBatch.getProjectionMatrix().setToOrtho2D(0, 0, config.virtualWidth(), config.virtualHeight());

        // Load input actions and bindings
        try {
            this.actionRegistry = ActionLoader.load(Path.of("content/actions.json"));
            this.contextBindings = BindingsLoader.load(Path.of("content/bindings.json"), actionRegistry);
        } catch (Exception e) {
            Log.error(LogCategory.DATA, "Failed to load actions or bindings: %s", e, e.getMessage());
            throw new RuntimeException("Input setup failed: " + e.getMessage(), e);
        }

        this.inputManager = new InputManager(actionRegistry, contextBindings);
        Gdx.input.setInputProcessor(inputManager);

        // Load movement tuning with hot-reloading watcher
        Path tuningPath = Path.of("content/tuning/movement.json");
        try {
            this.movementTuning = MovementTuningLoader.load(tuningPath, config.tickRate());
        } catch (Exception e) {
            Log.error(LogCategory.DATA, "Failed loading movement tuning from %s. Using defaults.", e, tuningPath);
            this.movementTuning = MovementTuning.defaultTuning(config.tickRate());
        }

        this.tuningWatcher = new MovementTuningWatcher(tuningPath, config.tickRate(), jobs, reloaded -> {
            this.movementTuning = reloaded;
            if (demoScene != null) {
                demoScene.getMovementSystem().setTuning(reloaded);
            }
        });

        this.recorder = new InputRecorder(config.tickRate(), 12345L, "m5b_hash");
        this.demoScene = new DemoSceneM5b(movementTuning, jobs);
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
        if (demoScene.isWaitingForLoad()) {
            timestep.discardAccumulator();
        }
        int ticks = demoScene.isWaitingForLoad() ? 0 : timestep.advance(frameDelta);

        long updateStart = System.nanoTime();
        for (int i = 0; i < ticks; i++) {
            boolean isFirstTickOfFrame = (i == 0);
            long currentTick = timestep.getCurrentTick();

            // Tick input manager
            inputManager.tick(currentTick, isFirstTickOfFrame);

            // Handle global debug record/playback commands
            InputState globalState = inputManager.getGlobalState();
            if (globalState.isPressed(Actions.DEBUG_RECORD)) {
                if (recorder.isRecording()) {
                    recorder.stop();
                    lastRecordedReplay = recorder.toReplayData();
                    Log.info(LogCategory.INPUT, "Replay recording stopped. Recorded %d transition frames.",
                        lastRecordedReplay.frames().size());
                } else {
                    recorder.start();
                    Log.info(LogCategory.INPUT, "Replay recording started.");
                }
            }

            if (globalState.isPressed(Actions.DEBUG_PLAYBACK)) {
                if (lastRecordedReplay != null) {
                    replayer = new InputReplayer(lastRecordedReplay, actionRegistry);
                    replayer.start();
                    Log.info(LogCategory.INPUT, "Replay playback started.");
                } else {
                    Log.warn(LogCategory.INPUT, "No replay recorded yet. Press F5 first.");
                }
            }

            // Determine active input state
            InputState activeState;
            if (replayer != null && replayer.isPlaying()) {
                replayer.tick(currentTick);
                activeState = replayer.getState();
            } else {
                activeState = inputManager.getState();
                if (recorder.isRecording()) {
                    recorder.recordTick(currentTick, inputManager.getContextStack().getActiveContext(), activeState);
                }
            }

            demoScene.tick(currentTick, activeState);
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

        // 5. Update window title and poll hot reloader once per second
        if (frameTimer.shouldUpdateTitle(frameStart)) {
            if (tuningWatcher != null) {
                tuningWatcher.poll();
            }
            if (demoScene != null) {
                demoScene.pollWatcher();
            }
            String recordStatus = recorder.isRecording() ? " [REC]" : (replayer != null && replayer.isPlaying() ? " [PLAY]" : "");
            Gdx.graphics.setTitle(String.format("%s%s [Upd: %.2fms | Rnd: %.2fms | FPS: %d]",
                config.windowTitle(),
                recordStatus,
                frameTimer.getCachedAvgUpdate(),
                frameTimer.getCachedAvgRender(),
                Gdx.graphics.getFramesPerSecond()));
        }
    }

    @Override
    public void pause() {
        Log.info(LogCategory.ENGINE, "Game paused");
        if (timestep != null) timestep.setPaused(true);
    }

    @Override
    public void resume() {
        Log.info(LogCategory.ENGINE, "Game resumed");
        if (timestep != null) timestep.setPaused(false);
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

    public InputManager getInputManager() {
        return inputManager;
    }

    public ActionRegistry getActionRegistry() {
        return actionRegistry;
    }

    public ContextBindings getContextBindings() {
        return contextBindings;
    }

    public MovementTuning getMovementTuning() {
        return movementTuning;
    }
}
