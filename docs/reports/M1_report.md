MILESTONE REPORT
Milestone: M1 Loop and Rendering
Status: complete

Summary (5 lines max):
Deterministic 60 Hz fixed-timestep loop with delta clamp, tick cap, and sub-tick alpha implemented.
VirtualScreen renders 320x180 RGBA8888 FrameBuffer with integer scaling and borderColor letterboxing.
Jobs worker pool (daemon threads, 2-4 capacity) drains task completions safely onto the main thread.
16x16 magenta/black checker placeholder texture created with headless pure pixel generator.
Zero per-frame allocation in hot simulation loop verified via ThreadMXBean over 100k frames.

What was built (modules, key classes, data files, formats added or changed):
- `engine/render`: `Snap`, `PositionState`, `VirtualScreenMath`, `VirtualScreen`, `CheckerGenerator`, `TextureProvider`, `DefaultTextureProvider`
- `engine/loop`: `FixedTimestep`, `Simulation`, `FrameTimer`
- `engine/async`: `Jobs`
- `game`: `PlatformerGame` (loop integration), `GameLauncher` (window limits), `DemoScene` (temporary throwaway)
- Docs: `docs/DECISIONS.md` (DEC-002, DEC-003), `docs/FORMATS.md` (optional config fields), `docs/UPDATE_ORDER.md` (frame pipeline)

Acceptance results (each item: pass/fail + how verified: test name or manual steps):
- FixedTimestep (ticks, carry, alpha [0,1), clamp, cap, pause, step): PASS (`FixedTimestepTest`)
- VirtualScreen integer scales (4x, 6x, 8x, 12x, 3x letterbox, 1x cropping with warning): PASS (`VirtualScreenMathTest`)
- Snap (positive, negative, .5 symmetry): PASS (`SnapAndInterpolationTest`)
- Interpolation with reset: PASS (`SnapAndInterpolationTest`)
- Jobs (drain on main thread, onError dispatch, onDone exception caught, shutdown rejection): PASS (`JobsTest`)
- Placeholder fallback returns checker and logs loud ERROR under ASSETS: PASS (`CheckerGeneratorTest`)
- Zero allocations in hot simulation loop: PASS (`SimulationAllocationTest`)
- Refresh rate parity at 60/120/144 Hz: PASS (`FixedTimestepTest.testSyntheticRefreshRateParity60_120_144Hz`)
- Crisp integer scaling & letterboxing: PASS (Manual verification via `./gradlew :game:run`)

Test summary: counts (unit, integration, fuzz), all passing? failures listed:
- Total: 31 unit tests across all modules.
- All passing: YES.
- Failures: None.

Deviations from the instruction set (each: what, why, impact). If none, write NONE.
NONE. All Architect adjustments (16x16 checker, delta clamp to 0.1s, RGBA8888 FBO, zero-allocation MXBean test, DEC-002 enforcement) incorporated.

Decisions made (also added to docs/DECISIONS.md): ID, choice, alternatives, reversibility
- DEC-002: Dependency direction between Engine and Data (Data depends on Engine; Engine has zero file I/O). Reversibility: Hard.
- DEC-003: FixedTimestep precision (double accumulator) and 16x16 checker generator. Reversibility: Easy.

Open-decision items touched (section 11 numbers) and the default chosen:
- None.

Anti-pattern self-check (section 12): any violations found and fixed or remaining:
- Reading Gdx.input outside input layer: None.
- Seconds or wall-clock time in simulation: None (simulation code reads strictly ticks).
- Referencing content by index: None.
- Components with logic: None.
- Special cases by variant: None.
- Second code path: None.
- Unversioned formats: None.
- Silent fallbacks: None (missing assets log loud ERROR under ASSETS category with placeholder).
- Per-frame allocation in hot paths: None (verified zero bytes allocated over 100k frames via ThreadMXBean).
- Adding unlisted features: None.

Known issues and tech debt (with severity):
- None. Temporary demo scene in `game/src/main/java/com/makeasaying/game/demo/DemoScene.java` is isolated with a single deletion point for M3.

Performance notes (frame time, allocations, load times) if relevant:
- Zero heap allocations in simulation loop (`SimulationAllocationTest` measured 0 bytes allocated over 100,000 frames).
- Frame time in demo scene averages 0.02ms update, 0.45ms render at 60 FPS (VSync).

Files changed (short tree or list) and where docs were updated:
- `engine/src/main/java/com/makeasaying/engine/async/Jobs.java`
- `engine/src/main/java/com/makeasaying/engine/config/EngineConfig.java`
- `engine/src/main/java/com/makeasaying/engine/loop/FixedTimestep.java`
- `engine/src/main/java/com/makeasaying/engine/loop/FrameTimer.java`
- `engine/src/main/java/com/makeasaying/engine/loop/Simulation.java`
- `engine/src/main/java/com/makeasaying/engine/render/CheckerGenerator.java`
- `engine/src/main/java/com/makeasaying/engine/render/DefaultTextureProvider.java`
- `engine/src/main/java/com/makeasaying/engine/render/PositionState.java`
- `engine/src/main/java/com/makeasaying/engine/render/Snap.java`
- `engine/src/main/java/com/makeasaying/engine/render/TextureProvider.java`
- `engine/src/main/java/com/makeasaying/engine/render/VirtualScreen.java`
- `engine/src/main/java/com/makeasaying/engine/render/VirtualScreenMath.java`
- `engine/src/main/java/com/makeasaying/engine/util/LogCategory.java`
- `engine/src/test/java/com/makeasaying/engine/...` (7 test classes)
- `game/src/main/java/com/makeasaying/game/GameLauncher.java`
- `game/src/main/java/com/makeasaying/game/PlatformerGame.java`
- `game/src/main/java/com/makeasaying/game/demo/DemoScene.java`
- `content/config.json`
- `docs/DECISIONS.md`, `docs/FORMATS.md`, `docs/UPDATE_ORDER.md`, `docs/MILESTONES.md`

How to run and verify (exact commands and steps):
1. Automated unit test suite:
   Run `./gradlew test` (or `./gradlew :engine:test`).
   Assert all 31 tests pass cleanly across `engine`, `data`, and `game`.
2. Interactive game launch and visual check:
   Run `./gradlew :game:run`.
   - Window opens at 1280x720 (4x integer scale of 320x180).
   - Tile room (20x12) and moving test box render crisply with nearest filtering.
   - Resize window to various sizes: pixels remain sharp whole integers; black letterbox/pillarbox borders appear to maintain aspect ratio.
   - Window title updates once per second with average update/render timings and current FPS.

Questions for the Architect (numbered, each with the agent's recommended answer):
1. For M2 (Input layer), should input action bindings in data use physical scancodes (e.g., QWERTY positions) with localized display name translation, or LibGDX `Input.Keys` keycodes?
   - Recommended answer: Follow section 7.4 ("Bind movement by physical key position (scancode), display real labels"), mapping physical scancodes to actions in `content/bindings.json`.
2. Should input recording/playback use a text-based format (JSON) or a compact binary format?
   - Recommended answer: UTF-8 JSON formatted per tick for diffability and human editability per principle 4.20.

Proposed scope for next milestone (M2: Input layer):
M2 (Input layer): Implement the input event queue on the render thread drained at the start of each tick; action mapping and bindings from `content/bindings.json`; per-entity `InputState` with digital movement; context stack (`GAMEPLAY`, `DIALOG`, `MENU`, `EDITOR`) with stale state clearing; ImGui WantCapture integration; and deterministic per-tick action recording and playback for automated testing.
