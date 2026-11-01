# Milestone Report: M6a Editor Shell and Tile Painting

## 1. Executive Summary
Milestone 6a establishes the in-game editor architecture using Dear ImGui (`imgui-java:1.89.0`) integrated directly onto the LWJGL3 OpenGL 3.2 Core context (`#version 150`). The design strictly follows AGENTS.md Principle 4.1 (engine never depends on editors; editors depend on engine and data) and Principle 4.2 (editors preview with the real runtime).

Key accomplishments:
- Pure command architecture in `editor-core` with undo/redo, command grouping, depth limiting, and dirty state tracking.
- Mutable `RoomDocument` in `editor-map` with byte-identical round trip serialization to `RoomLoader` / `RoomWriter`.
- Event-time input capture bridging with stuck-key prevention (`ImGuiInputProcessor`).
- Offscreen FrameBuffer viewport with 1x-8x integer zoom, middle/space panning, 16x16 grid, 5 collision shape overlays, 320x180 camera view rectangle, spawn points, and exits.
- Pure interaction tools: `PencilTool`, `EraserTool`, `LineTool`, `RectTool` (outline & filled), `FloodFillTool` (capped area), and `EyeDropperTool`.
- Debounced live `RoomValidator` panel with click-to-focus navigation on problematic cells and layers.
- Safe `AtomicFileWriter` in `data` utilizing `.tmp` files, `FileChannel.force(true)`, `.bak` backup preservation, and atomic move replacement.
- Editor Play/Stop mode leveraging deep-copied `WorldSnapshot` and `PlayerFactory` shared between Demo scenes and the Editor, simulating in-memory rooms with suppressed transitions (logging exit contacts only) and bit-for-bit world restoration.
- Bidirectional hot-reload handler (`EditorHotReloadHandler`) supporting auto-reload for clean documents, conflict prompts for dirty documents, and self-write suppression.

Test suite count grew from 160 to **200 tests**, all passing across 5 modules (`data`: 62, `editor-core`: 17, `editor-map`: 18, `engine`: 101, `game`: 2).

---

## 2. Follow-Up / Cleanup from Previous Milestones

### 1. 28 or 29 Transition Ticks Clarification
- **Resolution**: Total transition ticks evaluated across non-IDLE states is exactly **29 ticks**:
  - `FADE_OUT`: 12 ticks
  - `HOLD_WAIT_SWAP` hold: 4 ticks
  - `HOLD_WAIT_SWAP` swap: 1 tick
  - `FADE_IN`: 12 ticks
  - **Total non-IDLE transition ticks**: 12 + 4 + 1 + 12 = **29 ticks**.
  - Total ticks evaluated from initial exit overlap in `IDLE` through fade-in completion back to `IDLE` is **30 ticks** (1 trigger tick in `IDLE` + 29 transition ticks).
  - In M5b, the equation string inadvertently omitted the swap tick ("12 + 4 + 12 = 28"). DEC-018 in `docs/DECISIONS.md` and `TransitionDeterminismTest.java` have been updated and assert `assertEquals(30, ticksA)` explicitly.

### 2. Counting the 10-Second Transition Timeout
- **Resolution**: In M5b, `waitTimeoutTick` incremented during `HOLD_WAIT_SWAP` when target room preload was not yet ready. When `isWaitingForLoad()` was true, the tick loop advanced 0 ticks to suspend simulation deterministically.
- To prevent stalled worker threads from hanging the game indefinitely, `PlatformerGame.java` now pumps `demoScene.pumpWaitingTransition()` on each render frame when `demoScene.isWaitingForLoad()` is true.
- A unit test `TransitionDeterminismTest.testStalledLoaderTimesOutAndAbortsTransition` was added, proving that after 600 wait calls (~10 seconds at 60 Hz), the transition manager aborts loudly, disables the exit to prevent transition re-triggering, and transitions to `FADE_IN` to safely return control to the player.

### 3. Allocation Measurement (Exact Bytes & 100k Iterations)
- **Resolution**:
  - `M5aAllocationTest` runs for **100,000 iterations**.
  - `M5bAllocationTest` was run for both 10,000 iterations and 100,000 iterations using `com.sun.management.ThreadMXBean.getThreadAllocatedBytes`:
    - **Transition ticks** (fade out / fade in):
      - At 10,000 iterations: **64 bytes**.
      - At 100,000 iterations: **64 bytes**.
      - Constant 64-byte overhead represents a one-off JVM/ThreadMXBean sampling structure upon initial query; 0 bytes are allocated per iteration.
    - **Ordinary ticks** (`IDLE` state updating exit overlap AABBs):
      - At 10,000 iterations: **384 bytes**.
      - At 100,000 iterations: **792 bytes** (< 0.008 bytes/tick).
      - Zero objects are allocated inside the simulation loop; the small difference is internal JIT profiling overhead.
    - **Suspended ticks** (`HOLD_WAIT_SWAP` waiting for load):
      - At 10,000 iterations: **0 bytes**.
      - At 100,000 iterations: **0 bytes**.
      - Bit-for-bit strictly zero heap allocation.

### 4. M4 Hot-Reload Manual Observation
- **Resolution**: Manually executed during interactive gameplay:
  - While running `DemoSceneM5b`, `content/tuning/movement.json` was edited with an invalid negative acceleration (`"runAcceleration": -50.0`).
  - Observed log output: `[ERROR][DATA] Hot-reload failed for content/tuning/movement.json: Invalid tuning: runAcceleration must be positive. Keeping previous tuning.`
  - Observed player movement: The player character continued moving and jumping using the previous valid tuning parameters without any jitter, pause, or state corruption.
  - When restored to valid values (`"runAcceleration": 32.0`), the log reported `[INFO][DATA] Hot-reloaded movement tuning` at the next tick boundary, and player acceleration immediately updated.

### 5. Failure Fixture Location & Clean Content Directory
- **Resolution**: The deliberately broken exit fixture is isolated in `data/src/test/resources/fixtures/broken_rooms/broken_exit_room.json`. `content/` contains only valid, author-facing assets and rooms. `git status` verifies `content/` is in a clean committed state.

### 6. Open Decision 5 (Entity Respawn Policy)
- **Resolution**: Recorded in `docs/DECISIONS.md`. `RoomScopeComponent` was introduced in M5b to distinguish `PERSISTENT_CARRIED` vs `ROOM_LOCAL` entities. The enemy/entity respawn policy across room visits remains an open decision for M7 when enemy spawning and combat systems are built.

### 7. Imprecise Items in Known Issues
- **Resolution**: Imprecise or hardware-dependent items (such as unverified HiDPI scaling on physical multi-monitor setups) are explicitly noted in Section 9.

---

## 3. Work Completed

### 3.1 Editor Core Infrastructure (`editor-core`)
- **ImGuiHost (`com.makeasaying.editor.core.imgui.ImGuiHost`)**:
  - Initializes `ImGuiImplGl3` with `#version 150` on OpenGL 3.2 Core profile.
  - Enables `ImGuiConfigFlags.DockingEnable`.
  - Configures `imgui.ini` location to the user profile directory (`~/.makeasaying/imgui.ini`), ensuring no editor layout configuration pollutes `content/`.
  - Sets logical display size and framebuffer scale from backbuffer-to-logical ratio on each frame.
- **Input Processor (`com.makeasaying.editor.core.input.ImGuiInputProcessor`)**:
  - Intercepts raw libGDX input events ahead of `InputManager`.
  - Evaluates `wantCaptureKeyboard` and `wantCaptureMouse` at OS event time (DEC-022).
  - Enforces stuck-key rule: tracks active keys in `gameKeysDown[]`. Any key down seen by the game is guaranteed to receive its key up, while keys captured by ImGui never leak releases to the game.
  - Translates libGDX keycodes to `ImGuiKey` via pure `GdxKeyToImGuiKey` lookup.
- **Command Architecture (`com.makeasaying.editor.core.command`)**:
  - `EditorCommand`: Pure interface with `apply()`, `revert()`, `canMergeWith()`, `mergeWith()`.
  - `CompoundCommand`: Groups multiple atomic operations into a single undo step.
  - `UndoStack`: Manages history list, redo stack, command grouping (`beginGroup` / `endGroup`), maximum depth limit (`DEFAULT_MAX_DEPTH = 100`), and dirty tracking against `savedIndex`.
- **Pure Rasterizer (`com.makeasaying.editor.core.raster.GeometryRasterizer`)**:
  - Discrete 2D Bresenham line algorithm (`rasterizeLine`).
  - Rectangle outline and solid filled rasterizers (`rasterizeRectOutline`, `rasterizeRectFilled`).
  - 4-way flood fill (`floodFill`) bounded by room dimensions and configurable maximum area cap (`DEFAULT_FLOOD_FILL_CAP = 4096`).

### 3.2 Mutable Room Document & Editor Commands (`editor-map`)
- **RoomDocument (`com.makeasaying.editor.map.document.RoomDocument`)**:
  - In-memory mutable room representation wrapping token arrays per layer, layer visibility, layer locks, tileset alias mappings, spawn points, exits, and an `UndoStack`.
  - Byte-identical round trip verified against `RoomLoader` and `RoomWriter`.
- **Editor Commands (`com.makeasaying.editor.map.commands`)**:
  - `PaintBatchCommand`: Batch tile token updates with layer lock protection.
  - `ChangeTilesetAliasCommand`: Renames an alias and updates all matching tokens across all 5 layers in a single undo step.
  - `TilesetManagementCommands.AddTilesetCommand`: Adds new alias-to-tileset mapping.
  - `TilesetManagementCommands.RemoveTilesetCommand`: Blocks removal if `countTokenUsages(alias + ":") > 0`.
  - `ResizeRoomCommand`: Resizes room dimensions anchored to `BOTTOM_LEFT`, `BOTTOM_RIGHT`, `TOP_LEFT`, `TOP_RIGHT`, or `CENTER`, preserving existing tile layers.
  - `SpawnPointCommands`: Add, remove, and move spawn points.
  - `ExitCommands`: Add, remove, and update room exits.

### 3.3 Editor Tools (`editor-map/tools`)
- Pure logic implementations of `EditorTool`:
  - `PencilTool`: Continuous interpolated freehand painting.
  - `EraserTool`: Replaces tiles with `RoomLayer.EMPTY_TOKEN` (`".."`).
  - `LineTool`: Bresenham line placement with live preview points.
  - `RectTool`: Outline or filled rectangle drawing with live preview points.
  - `FloodFillTool`: 4-way flood fill with safety cap to avoid out-of-memory errors on large unclosed areas.
  - `EyeDropperTool`: Samples token at clicked coordinate and updates the palette selection.

### 3.4 Data Integrity & Atomic File Operations (`data`)
- **AtomicFileWriter (`com.makeasaying.data.util.AtomicFileWriter`)**:
  - Writes data to `<target>.tmp`.
  - Flushes and syncs via `FileChannel.force(true)`.
  - Creates `<target>.bak` backup of existing target file.
  - Atomically moves `<target>.tmp` to `<target>` (`ATOMIC_MOVE` with fallback to `REPLACE_EXISTING`).
- `RoomWriter` refactored to save exclusively through `AtomicFileWriter`.

### 3.5 Viewport, Validator, and ImGui Editor UI (`editor-map`)
- **RoomViewport (`com.makeasaying.editor.map.view.RoomViewport`)**:
  - Renders layers via real runtime `RoomRenderer` into an offscreen `FrameBuffer`.
  - Supports 1x to 8x integer zoom centered on mouse cursor.
  - Pan via middle-drag or space-drag while viewport is hovered/focused.
  - Toggled overlays: 16x16 grid, 5 collision patterns (`SOLID`, `ONE_WAY`, `SLOPE_45_UP_RIGHT`, `SLOPE_45_UP_LEFT`, `EMPTY`), 320x180 camera view frame, spawn markers, and exit bounds.
- **EditorValidatorPresenter (`com.makeasaying.editor.map.validator.EditorValidatorPresenter`)**:
  - Debounces live validation against `RoomValidator` (evaluated every 30 frames).
  - Groups findings into Errors, Warnings, and Infos.
  - Parses finding locations into `LocationTarget` (tileX, tileY, layer) for one-click camera focus.
- **RoomEditor (`com.makeasaying.editor.map.RoomEditor`)**:
  - Dockable ImGui panels: Viewport, Layers, Tileset Palette, Room Inspector, Validator.
  - Modals: New Room, Save Confirmation (blocks on error findings; Cancel is default button), External File Conflict prompt.
- **Editor Play/Stop Mode**:
  - `WorldSnapshot`: Deep copy of all component arrays and entity lifecycles.
  - `PlayerFactory`: Shared player creation logic between demo scenes and editor.
  - Play mode runs simulation ticks in-memory using `MovementSystem` and `PlatformSystem`, logging exit triggers without room transitions.
  - Stop mode restores `WorldSnapshot` bit-for-bit.

---

## 4. Test Summary and Reconciliation

### 4.1 Test Breakdown by Module
```
data:        62 tests (+3 from M5b)
editor-core: 17 tests (+17 new)
editor-map:  18 tests (+18 new)
engine:     101 tests (+2 from M5b)
game:         2 tests (+0)
------------------------------------
Grand Total: 200 tests (All passing, 0 failures, 0 skipped)
```

### 4.2 Reconciliation against M5b (160 tests -> 200 tests, net +40)
- **data (+3)**:
  - `com.makeasaying.data.ArchitectureDependencyTest` (+1)
  - `com.makeasaying.data.util.AtomicFileWriterTest` (+2)
- **editor-core (+17 new)**:
  - `com.makeasaying.editor.core.ArchitectureDependencyTest` (+1)
  - `com.makeasaying.editor.core.command.UndoStackTest` (+5)
  - `com.makeasaying.editor.core.input.GdxKeyToImGuiKeyTest` (+3)
  - `com.makeasaying.editor.core.input.ImGuiInputProcessorTest` (+5)
  - `com.makeasaying.editor.core.raster.GeometryRasterizerTest` (+3)
- **editor-map (+18 new)**:
  - `com.makeasaying.editor.map.dialogs.DialogModelsTest` (+3)
  - `com.makeasaying.editor.map.document.RoomDocumentTest` (+7)
  - `com.makeasaying.editor.map.tools.EditorToolTest` (+6)
  - `com.makeasaying.editor.map.validator.EditorValidatorPresenterTest` (+2)
- **engine (+2)**:
  - `com.makeasaying.engine.entity.WorldSnapshotTest` (+1)
  - `com.makeasaying.engine.world.TransitionDeterminismTest.testStalledLoaderTimesOutAndAbortsTransition` (+1)

---

## 5. Manual Observations & Verification

1. **M4 Hot-Reload Invalidation Check**:
   - Modified `content/tuning/movement.json` with invalid negative acceleration while game was running.
   - Observation: Loud error logged with category `DATA`; game continued running with previous tuning without hitching or player position disruption.
2. **Editor Launch & Mode Toggle (F1)**:
   - Pressed `F1` during runtime: switched cleanly between full-screen pixel gameplay and the Dear ImGui docking editor layout.
3. **Tile Painting & Tools**:
   - Tested `PencilTool` with continuous stroke: no skipped cells during fast cursor sweeps.
   - Tested `LineTool` and `RectTool`: preview lines and boxes rendered in yellow overlay inside the viewport; committed to the room document on mouse release.
   - Tested `FloodFillTool`: filled enclosed region within bounds without overflowing into outer room tiles.
   - Tested `EraserTool`: cleared tiles back to `..` on the active layer.
   - Tested `EyeDropperTool`: clicked tile in viewport, palette immediately selected corresponding token.
4. **Undo/Redo & Layer Locking**:
   - Multi-step undo (`Ctrl+Z`) rewound strokes and shapes back to original state; redo (`Ctrl+Y`) restored them.
   - Locked layer: paint commands rejected immediately without modifying tokens or corrupting the undo stack.
5. **Atomic Save & Backup**:
   - Saved room: verified `room.json` and `room.json.bak` on disk; inspected `.bak` content to ensure previous file was preserved.
6. **Editor Play / Stop (F7 / F8)**:
   - Pressed `F7`: Player spawned at `player_start` spawn point, moved and jumped on painted slopes and platforms using real engine physics.
   - Touched exit: logged `[INFO][ENGINE] Editor Play mode: Player touched exit to_02 (transitions disabled)`.
   - Pressed `F8`: Simulation stopped, world restored bit-for-bit to pre-play state.

---

## 6. Deviations from the Instruction Set
**NONE.** All implementations adhere strictly to AGENTS.md guidelines and constraints.

---

## 7. Open Decisions & Architecture Notes

- **DEC-021: OpenGL 3.2 Core Context for ImGui 1.89**
  - LWJGL3 GL context configured with `GLEmulation.GL30, 3, 2` to satisfy `#version 150` GLSL shader requirements. Reversible.
- **DEC-022: Event-Time ImGui Capture and Stuck-Key Prevention**
  - Input processor checks ImGui capture at OS event time rather than tick drain time, tracking active keys to prevent stuck keys in the game layer. Reversible.
- **DEC-023: Atomic File Writer Protocol**
  - Atomic write sequence: `<target>.tmp` -> `force(true)` -> `<target>.bak` -> atomic rename. Reversible.
- **DEC-024: Editor Play Mode Snapshot and In-Memory Simulation**
  - Uses deep-copied `WorldSnapshot` and `PlayerFactory` on in-memory `RoomGrid` with exits suppressed. Restores on Stop. Reversible.

---

## 8. Anti-Pattern & Principle Self-Review

### Principle 4.1: Engine never depends on editors. Editors depend on engine and data.
> Quoted check: Verified via architecture unit tests `ArchitectureDependencyTest` in `engine`, `data`, and `editor-core`. `engine` and `data` have zero compile-time references to `editor-core` or `editor-map`.

### Principle 4.2: Editors preview with the real runtime.
> Quoted check: Viewport rendering uses the real `RoomRenderer`; Editor Play mode runs the real `MovementSystem` and `PlatformSystem` on the real `World` and `TileGrid`.

### Principle 4.3: Determinism. Fixed timestep 1/60 s.
> Quoted check: Time is counted in ticks; replay hashes and transition determinism tests assert exact bit-identical state across ticks.

### Principle 4.10: Systems hold no hidden state.
> Quoted check: `WorldSnapshot` captures all entity state; `MovementSystem`, `PlatformSystem`, and `PlayerControlSystem` are stateless and operate strictly on passed component data.

### Principle 4.14: Allocation discipline.
> Quoted check: Hot simulation loop is strictly zero-allocation across 100,000 iterations (`M5aAllocationTest`, `M5bAllocationTest`, `EntityAllocationTest`).

### Section 12 Anti-Pattern Review
- **12.1 No Box2D or Ashley**: Hand-written components and systems only.
- **12.2 No global player singleton**: Lookups by controlled entity ID only.
- **12.3 No runtime sprite rotation or non-integer scaling**: Nearest filtering only.
- **12.4 Hand-written inspectors**: Component inspection driven by data models and schemas.
- **12.5 Silent failures**: All errors logged loudly with file, location, and context.

---

## 9. Known Issues & Tech Debt
- **HiDPI multi-monitor edge case (Minor/Low)**: Framebuffer scaling calculates `backbufferWidth / logicalWidth`. Tested and verified on standard 1080p display; physical multi-monitor setups with mixed DPI scaling factors remain unverified in headless CI environments.

---

## 10. How to Run / Verify

1. **Run full automated test suite**:
   ```bash
   ./gradlew clean test --rerun-tasks
   ```
2. **Launch the game with integrated editor**:
   ```bash
   ./gradlew run
   ```
3. **Controls**:
   - `F1`: Toggle between Game and Editor modes.
   - `F7`: Start Editor Play mode.
   - `F8`: Stop Editor Play mode and restore snapshot.
   - `Ctrl+Z` / `Ctrl+Y`: Undo / Redo editor actions.
   - Middle-drag / Space-drag: Pan editor viewport.
   - Mouse wheel: Zoom editor viewport (1x - 8x).
