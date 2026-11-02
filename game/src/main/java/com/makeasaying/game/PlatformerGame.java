package com.makeasaying.game;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.ScreenUtils;
import com.makeasaying.data.input.ActionLoader;
import com.makeasaying.data.input.BindingsLoader;
import com.makeasaying.data.tuning.MovementTuningLoader;
import com.makeasaying.data.tuning.MovementTuningWatcher;
import com.makeasaying.data.world.RoomGridBuilder;
import com.makeasaying.editor.core.flags.FlagDebugView;
import com.makeasaying.editor.core.flags.FlagPanelModel;
import com.makeasaying.editor.core.imgui.ImGuiHost;
import com.makeasaying.editor.core.input.ImGuiInputProcessor;
import com.makeasaying.editor.map.RoomEditor;
import com.makeasaying.editor.map.document.RoomDocument;
import com.makeasaying.engine.async.Jobs;
import com.makeasaying.engine.config.EngineConfig;
import com.makeasaying.engine.entity.MovementTuning;
import com.makeasaying.engine.entity.World;
import com.makeasaying.engine.entity.components.BodyComponent;
import com.makeasaying.engine.entity.components.ColliderComponent;
import com.makeasaying.engine.input.ActionRegistry;
import com.makeasaying.engine.input.Actions;
import com.makeasaying.engine.input.ContextBindings;
import com.makeasaying.engine.input.InputContext;
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
import com.makeasaying.engine.world.RoomExit;
import com.makeasaying.engine.entity.TileGrid;
import com.makeasaying.game.demo.DemoSceneM5b;
import imgui.ImGui;

import java.nio.file.Path;

/**
 * Main game entry point managing the fixed-timestep loop, input layer,
 * background Jobs, hot-reloading tuning, VirtualScreen integer-scaled rendering,
 * and integrated in-game Dear ImGui Room Editor per M6a brief.
 */
public class PlatformerGame implements ApplicationListener {
    public enum GameMode {
        GAME,
        EDITOR
    }

    private final EngineConfig config;
    private GameMode mode = GameMode.GAME;

    private FixedTimestep timestep;
    private VirtualScreen virtualScreen;
    private Jobs jobs;
    private FrameTimer frameTimer;
    private SpriteBatch sceneBatch;

    private ActionRegistry actionRegistry;
    private ContextBindings contextBindings;
    private InputManager inputManager;
    private ImGuiInputProcessor imGuiInputProcessor;
    private ImGuiHost imGuiHost;
    private RoomEditor roomEditor;

    private MovementTuning movementTuning;
    private MovementTuningWatcher tuningWatcher;

    private InputRecorder recorder;
    private InputReplayer replayer;
    private ReplayData lastRecordedReplay;

    private final FlagPanelModel flagPanelModel = new FlagPanelModel();
    private final FlagDebugView flagDebugView = new FlagDebugView();

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

        // Initialize ImGuiHost and chain ImGuiInputProcessor ahead of InputManager
        this.imGuiHost = new ImGuiHost();
        this.imGuiHost.init();
        this.imGuiInputProcessor = new ImGuiInputProcessor(inputManager);
        Gdx.input.setInputProcessor(imGuiInputProcessor);

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
        this.demoScene.setSaveSlot(config.saveSlot());

        if (config.continueOnStart()) {
            this.demoScene.loadCheckpointImmediate(config.saveSlot());
        }

        Path r1Path = demoScene.getRoomsDir().resolve("test_01.json");
        this.roomEditor = new RoomEditor(
            RoomDocument.fromRoom(demoScene.getCurrentRoom()),
            r1Path,
            demoScene.getRoomRenderer(),
            demoScene.getLoadedTilesets()
        );
        this.roomEditor.getState().setFlagStore(demoScene.getFlagStore(), demoScene.getRoomStateStore());
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
            demoScene.pumpWaitingTransition();
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

            // Handle mode toggle (F1)
            // Mode and debug toggles
            if (globalState.isPressed(Actions.TOGGLE_EDITOR)) {
                if (mode == GameMode.GAME) {
                    mode = GameMode.EDITOR;
                    inputManager.getContextStack().push(InputContext.EDITOR);
                    Log.info(LogCategory.ENGINE, "Mode switched to EDITOR");
                } else {
                    if (roomEditor.getState().isPlaying()) {
                        roomEditor.stopPlay();
                    }
                    mode = GameMode.GAME;
                    inputManager.getContextStack().pop();
                    Log.info(LogCategory.ENGINE, "Mode switched to GAME");
                }
            }

            if (globalState.isPressed(Actions.DEBUG_FLAGS)) {
                flagPanelModel.toggleVisible();
                Log.info(LogCategory.ENGINE, "Toggled flags debug panel: visible=%s", flagPanelModel.isVisible());
            }

            if (globalState.isPressed(Actions.DEBUG_LOAD_CHECKPOINT)) {
                demoScene.startCheckpointLoad(config.saveSlot());
                Log.info(LogCategory.ENGINE, "Initiated checkpoint load for slot '%s'", config.saveSlot());
            }

            if (mode == GameMode.GAME) {
                demoScene.tick(currentTick, activeState);
            } else {
                // In EDITOR mode: check F7 (Play), F8 (Stop), Ctrl+Z (Undo), Ctrl+Y (Redo)
                if (globalState.isPressed(Actions.EDITOR_PLAY)) {
                    roomEditor.startPlay();
                }
                if (globalState.isPressed(Actions.EDITOR_STOP)) {
                    roomEditor.stopPlay();
                }
                if (activeState.isPressed(Actions.EDITOR_UNDO)) {
                    if (roomEditor.getState().getDocument().getUndoStack().canUndo()) {
                        roomEditor.getState().getDocument().getUndoStack().undo();
                    }
                }
                if (activeState.isPressed(Actions.EDITOR_REDO)) {
                    if (roomEditor.getState().getDocument().getUndoStack().canRedo()) {
                        roomEditor.getState().getDocument().getUndoStack().redo();
                    }
                }

                if (roomEditor.getState().isPlaying()) {
                    World playWorld = roomEditor.getState().getPlayWorld();
                    long playPlayerId = roomEditor.getState().getPlayPlayerId();
                    if (playWorld != null && playPlayerId != 0L) {
                        if (demoScene.getFlagStore() != null) {
                            demoScene.getFlagStore().applyPendingChanges(
                                roomEditor.getState().getDocument().getId(),
                                playWorld.getEventQueue(),
                                currentTick
                            );
                        }

                        TileGrid playGrid = RoomGridBuilder.build(
                            roomEditor.getState().getDocument().toRoom(),
                            demoScene.getLoadedTilesets(),
                            16
                        );

                        demoScene.getControlSystem().update(playWorld, activeState);
                        demoScene.getPlatformSystem().update(playWorld, playGrid);
                        demoScene.getMovementSystem().update(playWorld, playGrid);

                        // Check exits (logging only, transitions disabled in editor per brief)
                        BodyComponent pb = playWorld.getBody(playPlayerId);
                        ColliderComponent pc = playWorld.getCollider(playPlayerId);
                        if (pb != null && pc != null) {
                            int pl = pc.getLeft(pb.x);
                            int pr = pc.getRight(pb.x);
                            int pbY = pc.getBottom(pb.y);
                            int pt = pc.getTop(pb.y);
                            for (RoomExit ex : roomEditor.getState().getDocument().getExits()) {
                                if (pl < ex.x() + ex.width() && pr > ex.x() &&
                                    pbY < ex.y() + ex.height() && pt > ex.y()) {
                                    boolean allowed = (ex.condition() == null) ||
                                        ex.condition().evaluate(demoScene.getFlagStore(), roomEditor.getState().getDocument().getId());
                                    Log.info(LogCategory.ENGINE, "Editor Play mode: Player touched exit %s (conditionMet=%s, transitions disabled)",
                                        ex.id(), allowed);
                                }
                            }
                        }
                    }
                }
            }
        }
        long updateEnd = System.nanoTime();

        long renderStart = System.nanoTime();
        if (mode == GameMode.GAME) {
            // 3. Render 320x180 virtual canvas to FrameBuffer
            virtualScreen.begin();
            ScreenUtils.clear(0f, 0f, 0f, 1f);

            sceneBatch.begin();
            demoScene.render(sceneBatch, timestep.getAlpha());
            sceneBatch.end();

            // Unbind and draw FrameBuffer centered with integer scaling to backbuffer
            virtualScreen.end();

            // Render Dear ImGui debug windows if open (e.g. F2 flags)
            if (flagPanelModel.isVisible()) {
                int backbufferW = Gdx.graphics.getBackBufferWidth();
                int backbufferH = Gdx.graphics.getBackBufferHeight();
                int logicalW = Gdx.graphics.getWidth();
                int logicalH = Gdx.graphics.getHeight();

                imGuiHost.newFrame(logicalW, logicalH, backbufferW, backbufferH, frameDelta);
                flagPanelModel.setEditable(true);
                flagDebugView.render(
                    flagPanelModel,
                    demoScene.getFlagRegistry(),
                    demoScene.getFlagStore(),
                    demoScene.getCurrentRoom() != null ? demoScene.getCurrentRoom().id() : ""
                );
                imGuiHost.render();
            }
        } else {
            // Render Dear ImGui room editor directly on GL context
            ScreenUtils.clear(0.12f, 0.12f, 0.14f, 1.0f);

            int backbufferW = Gdx.graphics.getBackBufferWidth();
            int backbufferH = Gdx.graphics.getBackBufferHeight();
            int logicalW = Gdx.graphics.getWidth();
            int logicalH = Gdx.graphics.getHeight();

            imGuiHost.newFrame(logicalW, logicalH, backbufferW, backbufferH, frameDelta);
            roomEditor.updateAndRender(frameDelta);
            if (flagPanelModel.isVisible()) {
                flagPanelModel.setEditable(roomEditor.getState().isPlaying());
                flagDebugView.render(
                    flagPanelModel,
                    demoScene.getFlagRegistry(),
                    demoScene.getFlagStore(),
                    roomEditor.getState().getDocument().getId()
                );
            }
            imGuiHost.render();
        }
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
        if (demoScene != null && demoScene.getSaveManager() != null) {
            demoScene.getSaveManager().shutdown(2000L);
        }
        if (jobs != null) jobs.shutdown();
        if (roomEditor != null) roomEditor.dispose();
        if (imGuiHost != null) imGuiHost.dispose();
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
