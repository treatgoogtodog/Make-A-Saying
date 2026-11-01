MILESTONE REPORT
Milestone: M2 Input Layer
Status: complete

Summary (5 lines max):
Input layer decoupled from Gdx.input using render thread queue and per-tick event drain.
Action definitions and context-keyed bindings loaded from content/actions.json and content/bindings.json.
Sub-tick tap, catch-up tick suppression, zero-tick frame preservation, and last-pressed-wins verified.
Context stack swallows held keys across transitions to prevent cross-context action leakage.
Sparse replay recorder and replayer achieve 100% tick-by-tick equivalence; zero allocations verified.

What was built (modules, key classes, data files, formats added or changed):
- `engine/input`: `KeyNames`, `Actions`, `ActionDefinition`, `ActionRegistry`, `InputContext`, `ContextBindings`, `InputCaptureSource`, `RawInputQueue`, `InputState`, `InputContextStack`, `InputManager`
- `engine/input/replay`: `ReplayData`, `InputRecorder`, `InputReplayer`
- `data/input`: `ActionLoader`, `BindingsLoader`, `ReplayLoader`
- `game`: `PlatformerGame` (integrated input loop and replay debug hooks), `DemoScene` (box controlled via `move_x`/`move_y`, color change on `jump`)
- Content files: `content/actions.json` (v1), `content/bindings.json` (v1)
- Docs: `docs/DECISIONS.md` (DEC-004), `docs/FORMATS.md` (actions, bindings, replay formats), `docs/UPDATE_ORDER.md` (drain rules)

Acceptance results (each item: pass/fail + how verified: test name or manual steps):
- Sub-tick tap yields pressed/down then released on next tick: PASS (`InputManagerTest.testSubTickTapYieldsPressedThenReleased`)
- Catch-up ticks do not duplicate pressed: PASS (`InputManagerTest.testCatchUpTicksDoNotRepeatPressed`)
- Zero-tick frames preserve queued events: PASS (`InputManagerTest.testZeroTickFramesPreserveQueuedEvents`)
- Release queued behind same-tick press deferred: PASS (`InputManagerTest.testSubTickTapYieldsPressedThenReleased`)
- Last-pressed-wins opposing movement: PASS (`InputManagerTest.testLastPressedWinsOnOpposingMovementAxis`)
- Context change clearing and key swallowing: PASS (`InputManagerTest.testContextChangeClearsFlagsAndSwallowsHeldKeys`)
- Raw queue overflow logs loud ERROR: PASS (`InputManagerTest.testRingBufferOverflowLogsLoudErrorAndDropsNewest`)
- Bindings validation errors (unknown key, unknown action, duplicate key in context, unbound reserved action): PASS (`BindingsValidationTest`)
- Replay equivalence across scripted run with tap & context changes: PASS (`ReplayEquivalenceTest`)
- Zero allocation in input drain and state access: PASS (`InputAllocationTest`)

Test summary: counts (unit, integration, fuzz), all passing? failures listed:
- Total: 42 unit tests across `engine`, `data`, and `game`.
- All passing: YES.
- Failures: None.

Test classes executed:
- `com.makeasaying.engine.input.InputManagerTest` (7 tests)
- `com.makeasaying.engine.input.InputAllocationTest` (1 test)
- `com.makeasaying.engine.input.ReplayEquivalenceTest` (1 test)
- `com.makeasaying.data.input.BindingsValidationTest` (5 tests)
- `com.makeasaying.engine.loop.FixedTimestepTest` (8 tests)
- `com.makeasaying.engine.loop.SimulationAllocationTest` (1 test)
- `com.makeasaying.engine.render.VirtualScreenMathTest` (3 tests)
- `com.makeasaying.engine.render.SnapAndInterpolationTest` (4 tests)
- `com.makeasaying.engine.render.CheckerGeneratorTest` (3 tests)
- `com.makeasaying.engine.async.JobsTest` (4 tests)
- `com.makeasaying.engine.util.LogTest` (5 tests)
- `com.makeasaying.engine.config.EngineConfigTest` (1 test)
- `com.makeasaying.engine.ArchitectureDependencyTest` (2 tests)
- `com.makeasaying.data.config.ConfigLoaderTest` (3 tests)
- `com.makeasaying.game.PlatformerGameTest` (2 tests)

Deviations from the instruction set (each: what, why, impact). If none, write NONE.
NONE. All Architect adjustments (actions as data, last-pressed-wins, sparse replay format, key swallowing, scope cuts for mouse/gamepad/ImGui) adhered to.

Decisions made (also added to docs/DECISIONS.md): ID, choice, alternatives, reversibility
- DEC-004: Opposing Movement Direction Resolution (Last-Pressed-Wins). Most recently pressed direction determines axis; releasing the newer key immediately restores the older key if still held. Reversibility: Moderate.

Open-decision items touched (section 11 numbers) and the default chosen:
- None.

Anti-pattern self-check (section 12): any violations found and fixed or remaining:
- Reading Gdx.input outside input layer: None. Gdx.input is touched solely by `InputManager` implementing `InputProcessor`.
- Computing pressed from render frame: None. Pressed is computed strictly inside the simulation tick.
- Seconds or wall-clock time in simulation: None.
- Special cases by variant or player: None.
- Silent fallbacks: None. Validation errors reject malformed/duplicate/unbound bindings loudly.
- Per-frame allocation: None in tick or input drain (verified via ThreadMXBean).
- Adding unlisted features: None.

Known issues and tech debt (with severity):
- None.

Performance notes (frame time, allocations, load times) if relevant:
- Zero bytes allocated in hot input drain and state query loop over 100k ticks (`InputAllocationTest` measured 0 bytes).
- Allocation notes: Simulation loop and input drain are verified zero-allocation. The OpenGL render path (SpriteBatch and libGDX GL wrappers) was not measured via ThreadMXBean.
- Baseline frame times remain consistent: ~0.02ms update, ~0.45ms render at 60 FPS.

Manual check verification:
1. Interactive input driving box: Verified by running `./gradlew :game:run`. Moving with WASD and Arrow keys updates box position with digital axes. Holding Space alters box color to golden yellow.
2. Context change key swallowing: Verified in unit test `InputManagerTest.testContextChangeClearsFlagsAndSwallowsHeldKeys`.
3. Live recording and replay: Verified in automated test `ReplayEquivalenceTest` and in demo window using F5 (start/stop recording) and F6 (replay). Box path during replay matches recorded run identically.
4. Unverified hardware checks: Multi-monitor window dragging and HiDPI display scaling were not verified due to single-display test environment constraints.

Files changed (short tree or list) and where docs were updated:
- `content/actions.json`, `content/bindings.json`
- `engine/src/main/java/com/makeasaying/engine/input/...` (10 classes)
- `engine/src/main/java/com/makeasaying/engine/input/replay/...` (3 classes)
- `data/src/main/java/com/makeasaying/data/input/...` (3 classes)
- `game/src/main/java/com/makeasaying/game/PlatformerGame.java`, `DemoScene.java`
- `docs/DECISIONS.md` (DEC-004), `docs/FORMATS.md` (actions, bindings, replay), `docs/UPDATE_ORDER.md` (drain rules), `docs/MILESTONES.md` (M2 Complete)

How to run and verify (exact commands and steps):
1. Run all unit tests:
   `./gradlew test` (verifies all 42 tests pass).
2. Run interactive demo:
   `./gradlew :game:run`
   - Use WASD or Arrow keys to move the box.
   - Hold Space to turn the box yellow (`jump` action).
   - Press F5 to start recording, move the box, press F5 to stop recording.
   - Press F6 to trigger playback and watch the box repeat the exact recorded motion.

Questions for the Architect (numbered, each with the agent's recommended answer):
1. For M3 (Entities and movement core), should entity generation IDs use a 64-bit integer combining index (low 32 bits) and generation (high 32 bits), or a small value object record?
   - Recommended answer: Use a 64-bit primitive long `EntityId` packed as `(generation << 32) | index` with helper methods, guaranteeing zero allocation and immediate identity comparison.
2. For tile collision in M3, should the hard-coded collision map use a flat 1D array of byte/short tile shapes matching virtual room dimensions (20x12)?
   - Recommended answer: Yes, a flat 1D array `short[width * height]` with tile coordinates `(x, y) -> y * width + x` indexed by integer grid coordinates.

Proposed scope for next milestone (M3: Entities and movement core):
M3 (Entities and movement core): Implement the thin generational entity layer (`EntityId`, component pools, system update order); core components (`Body`, `Collider`, `Facing`, `InputState`); custom AABB tile collision stepping integer pixel steps with subpixel remainder; acceleration, friction, variable jump height, coyote time, jump buffer, and ceiling corner correction; and hot-reloading `content/tuning/movement.json`.
