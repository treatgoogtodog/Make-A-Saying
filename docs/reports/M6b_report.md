# Milestone Report: M6b Entity Definitions, Spawning, Placement
Implementer: Antigravity
Date: 2026-11-01
Brief: Introduce the component schema registry, entity definition file format, room format v3, single-source entity spawner, and editor entity tools with pure models and commands.

---

## 0. Closure Pass Results from M6a Review

### 1. Dear ImGui Spike Evidence & Coordinates
- **Resolved Maven Coordinates**:
  - `io.github.spair:imgui-java-binding:1.89.0`
  - `io.github.spair:imgui-java-lwjgl3:1.89.0`
  - `io.github.spair:imgui-java-natives-windows:1.89.0`
- **OpenGL Profile & Version**:
  - Application requests LWJGL3 GL 3.2 Core profile.
  - GLSL shader version initialized: `#version 150`.
  - Actual OpenGL version string reported at runtime startup: `4.6.0 NVIDIA 576.88`.
- **GL30 Emulation Verification**:
  - Existing engine render paths (tile maps, checkerboard placeholder textures, scaled FrameBuffer blits, nearest filtering) render identically with zero visual distortion, clipping, or color alteration.

### 2. ImGui Input & Coordinate Corrections
- **Correction 1 (Mouse Y)**: Passed through directly from libGDX `screenY` to ImGui without inversion (libGDX window origin is top-left, matching ImGui expectations).
- **Correction 2 (HiDPI Scaling)**: Display size is set from logical window dimensions (`Gdx.graphics.getWidth()`, `getHeight()`) and framebuffer scale is dynamically calculated on every frame from the backbuffer-to-logical ratio (`getBackBufferWidth() / getWidth()`).
- **Correction 3 (Key Mapping & Modifiers)**: Key events are dispatched exclusively via Dear ImGui 1.89's `ImGuiIO.addKeyEvent(int imGuiKey, boolean down)`. Modifier keys (`KEY_CONTROL_LEFT`, `KEY_SHIFT_LEFT`, etc.) are mapped to explicit `ImGuiKey.ModCtrl`, `ImGuiKey.ModShift`, etc. Tested in `InputManagerTest.testCtrlZModifierChordAndImGuiCapturePreemption`.

### 3. Allocation Measurements (Strictly 0 Bytes over 100k Iterations)
- **Game-Mode Allocation (`GameModeAllocationTest`)**:
  - Tested running ordinary ticks in `Game` mode with the editor modules present on the classpath.
  - Measured over 100,000 simulation ticks via `ThreadMXBean.getThreadAllocatedBytes`: **0 bytes**.
- **M5b Transition Allocation (`M5bAllocationTest`)**:
  - Replaced JVM `switch(enum)` statements in `RoomTransitionManager` (which allocate internal `$SwitchMap` synthetic classes) with direct reference equality checks (`if-else`).
  - Tested at 10,000 and 100,000 iterations:
    - **Transition ticks** (`FADE_OUT` / `FADE_IN`): 10k: **0 bytes**, 100k: **0 bytes**.
    - **Ordinary ticks** (`IDLE` exit evaluation): 10k: **0 bytes**, 100k: **0 bytes**.
    - **Suspended ticks** (`HOLD_WAIT_SWAP` waiting for preload): 10k: **0 bytes**, 100k: **0 bytes**.
  - All hot paths now demonstrate bit-for-bit strictly zero heap allocation.

### 4. Transition Duration & Monotonic Clock Timeout
- **Duration Reconciliation**: Total non-IDLE transition ticks: **29 ticks** (12 `FADE_OUT` + 4 hold + 1 room swap + 12 `FADE_IN`). First active tick in the new room is tick 30.
- **Monotonic Clock**: The 10.0-second safety timeout is counted exclusively via monotonic wall-clock time (`System.nanoTime()`) during the waiting path (`isWaitingForLoad() == true`). It does not run during tick advancement and is outside the replay determinism guarantee (documented in DEC-020).
- **Verification**: `TransitionDeterminismTest.testStalledLoaderTimesOutAndAbortsTransition` proves an aborted load cleanly recovers control, and `TransitionDeterminismTest.testLoaderFinishingJustBeforeDeadlineSucceedsWithoutAbort` proves completions just before the 10.0s deadline succeed without aborting.

---

## 1. What Was Built

### 1.1 Component Schema Registry & Entity Definitions
- **Component Schema Architecture (`com.makeasaying.engine.schema`)**:
  - Instantiated `ComponentSchemaRegistry` created at startup and passed down (no global singleton).
  - Strongly typed field metadata (`ComponentField`, `FieldType`: `INT`, `FLOAT`, `STRING`, `BOOLEAN`, `ENUM`) with validation (`isValidValue()`), default values, min/max ranges, and UI tooltips.
  - Built-in schemas for core components: `body`, `collider`, `facing`, `controller`, `room_scope`, `jump_state`, and `platform`.
  - Schema-driven entity spawning (`applyToEntity()`) and property reading (`readFromEntity()`).
  - Strict numeric typing convention (DEC-025) differentiating integral discrete coordinates from floating-point velocities.
  - Extensibility verified by unit test registering a synthetic component.
- **Entity Definition Format v1 (`content/entities/*.json`)**:
  - Implemented `EntityDefinitionLoader` with validation: rejects unknown component types (`DEF_ERR_UNKNOWN_COMPONENT`), missing required components (`DEF_ERR_MISSING_REQUIRED_COMPONENT`), unknown fields (`DEF_ERR_UNKNOWN_FIELD`), and out-of-range bounds (`DEF_ERR_FIELD_OUT_OF_RANGE`).
  - Authored definitions:
    - `content/entities/player.json` (`placeable: false`).
    - `content/entities/moving_platform.json` (`placeable: true`).

### 1.2 Room Format v3 & Entity Spawner
- **Room Format v3 (`content/rooms/*.json`)**:
  - Added monotonic `entitySeq` counter for deterministic, non-reusable room-scoped instance ID generation (`<def>_<entitySeq>`).
  - Component-nested override structure (`overrides: { "<componentId>": { "<field>": value } }`).
  - Registered migration v2 -> v3 in `MigrationChain`: automatically scans existing entities, computes `entitySeq = max(suffixes) + 1`, and migrates flat legacy overrides into component-nested structures. Rejects unknown version v4 (`ROOM_ERR_NEWER_VERSION`).
  - Authored rooms `test_01.json` and `test_02.json` migrated to format v3 and populated with moving platform entities, eliminating hardcoded platform instantiation from demo scenes.
  - Byte-identical round trip serialization verified in `RoomLoaderTest`.
- **Entity Spawner (`EntitySpawner`)**:
  - Single spawn pipeline for all entities (player, moving platforms, and future NPCs/props).
  - Explicitly resets previous interpolation coordinates (`prevX = x`, `prevY = y`, `subpixelX = 0`, `subpixelY = 0`) to eliminate first-frame visual smearing.
  - Attaches `InstanceIdComponent` to all spawned entities, ensuring presence in snapshots and inclusion in `GameStateHash`.
  - `PlayerFactory` converted into a thin, allocation-free convenience wrapper over `EntitySpawner`.
  - Verified 600-tick platform stepping parity against the M4 baseline in both `BLOCK` and `CRUSH` rider modes (`EntitySpawnerTest`).

### 1.3 Room Validation
- Extended `RoomValidator` with complete entity validation rules:
  - `ROOM_ERR_UNKNOWN_DEF`: Referenced entity definition does not exist.
  - `ROOM_ERR_NON_PLACEABLE_DEF`: Entity definition has `placeable: false`.
  - `ROOM_ERR_NON_OVERRIDABLE_FIELD`: Attempting to override an immutable schema field.
  - `ROOM_ERR_OVERRIDE_OUT_OF_RANGE`: Override value violates field constraints.
  - `ROOM_ERR_DUPLICATE_INSTANCE_ID`: Multiple entities share the same instance ID.
  - `ROOM_ERR_ENTITY_SEQ_INVALID`: `entitySeq` is less than or equal to an existing suffix.
  - `ROOM_ERR_ENTITY_OUT_OF_BOUNDS`: Entity origin is outside the room boundaries.
  - `ROOM_WARN_SOLID_INSIDE_SOLID`: Solid entity overlaps solid tile collision.
  - `ROOM_WARN_PLATFORM_CROSSES_SOLID`: Moving platform trajectory intersects solid level geometry.

### 1.4 Editor Pure Models, Commands & Inspector
- **Pure Models (`editor-map/model`)**:
  - `EntityPaletteModel`: Case-insensitive search filtering, excludes `placeable: false` definitions.
  - `SelectionModel`: Single selection with deterministic tie-breaking (smallest bounding box area first, then lowest instance sequence suffix; 16x16 fallback for entities without colliders).
  - `GizmoModel`: Pure coordinate math for collider bounding boxes, trajectory lines, and endpoint drag handles.
  - `InspectorModel`: Builds component tree from schema, detects modified override values, and provides one-click reset to definition defaults.
- **Editor Commands (`editor-map/commands`)**:
  - `PlaceEntityCommand`: Allocates next `entitySeq`, places entity, updates `savedFloor`.
  - `MoveEntityCommand`: Updates entity (x, y) with mergeable dragging support.
  - `DeleteEntityCommand`: Removes entity from room document.
  - `EditOverrideFieldCommand`: Mutates component override values in document.
  - `MovePlatformEndCommand`: Updates platform trajectory endpoint (`endX`, `endY`).
  - **`savedFloor` Protection (DEC-028)**: Ensures undo/redo can safely decrement `entitySeq` for uncommitted actions while strictly preventing reuse of any instance ID that has already reached disk.
- **ImGui Editor Integration**:
  - Entity Palette panel with search bar and item selection.
  - Schema-driven Inspector panel rendering custom controls per `FieldType`.
  - Viewport gizmo overlays and interactive endpoint handle dragging.
  - Grid snap toggle with 8px and 16px snap increments.
  - Editor Play mode (F7) with snapshot save/restore, simulating dynamic entities in real-time.
  - Validator navigation: clicking validation errors jumps viewport focus directly to the target entity.
- **Temporary Entity Renderer (`EntityDebugRenderer`)**:
  - Debug renderer for entity colliders and trajectories, marked with `// TEMPORARY(M9)` awaiting the M9 animation runtime.

---

## 2. Deviations from the Brief or AGENTS.md
None. All implementations follow the brief, architectural constraints, and `AGENTS.md` specifications.

---

## 3. Architecture Check
- Engine -> data dependency? **Yes** (Loads definitions, room formats, tuning).
- Engine -> editor dependency? **No** (Strictly verified by `ArchitectureDependencyTest`).
- Editor -> engine dependency? **Yes** (`editor-map` uses engine schemas, models, entities).
- Editor -> data dependency? **Yes** (`editor-map` uses data loaders and validators).
- Any parallel implementations? **No** (Single `EntitySpawner`, single `ComponentSchemaRegistry`, single `RoomLoader`/`RoomWriter`).
- Any hidden state in systems? **No** (All state resides in components, captured in `WorldSnapshot`).
- Components contain data only? **Yes** (Generational IDs and primitives only; no system logic).
- One Lua state on simulation thread only? **N/A** (Lua not integrated yet).

---

## 4. Test Report
- **Total tests**: **242** (all passing: **Yes**)
- **New tests added this milestone**: **42**
- **Arithmetic check**:
  - Previous total (M6a): 200
  - New tests added: 42
  - Current total: 200 + 42 = **242**
- **Breakdown by area**:
  - `engine`: 111 (was 101, +10 new)
  - `data`: 78 (was 62, +16 new)
  - `editor-map`: 33 (was 18, +15 new)
  - `editor-core`: 17 (was 17, +0 new)
  - `game`: 3 (was 2, +1 new)
  - `editor-anim`: 0
  - `editor-cut`: 0
- **Key scenarios covered**:
  - `EntityDefinitionLoaderTest`: Rejects invalid IDs, unknown components, unknown fields, missing required components, and out-of-range values.
  - `RoomEntityValidationTest`: Comprehensive validation of `ROOM_ERR_*` and `ROOM_WARN_*` codes.
  - `RoomMigrationV3Test`: Migrates v1->v3 and v2->v3, calculates `entitySeq`, converts overrides, and rejects v4.
  - `EntitySpawnerTest`: Verifies `prevX`/`prevY` coordinate reset, `PlayerFactory` parity, and 600-tick platform stepping parity in `BLOCK` and `CRUSH` modes.
  - `PureEditorModelsTest`: Search filtering, palette placeable exclusions, deterministic smallest-area selection tie-breaking, gizmo hit-testing.
  - `EditorEntityCommandsTest`: Verifies place, move, delete, field edit, endpoint move, and `savedFloor` monotonic instance ID safety.
  - `ComponentSchemaRegistryTest`: Single-source schema registration, platform component application/read parity, and synthetic component extensibility.
- **Replay-hash check**:
  - Test name: `TransitionDeterminismTest.testImmediateVsDelayedPreloadDeterminism` & `RoomReplayStateHashTest.testReplayDeterminismOnLoadedRoom`
  - Result: **PASS**
  - Hash across runs: **identical**
- **Zero-allocation check**:
  - Tests:
    - `GameModeAllocationTest.testGameModeOrdinaryTicksZeroAllocationWithEditorOnClasspath`: **0 bytes** over 100k ticks.
    - `M5bAllocationTest.testOrdinaryTicksAllocation`: **0 bytes** over 100k ticks.
    - `M5bAllocationTest.testTransitionTicksAllocation`: **0 bytes** over 100k ticks.
    - `M5bAllocationTest.testSuspendedTicksAllocation`: **0 bytes** over 100k ticks.
    - `SimulationAllocationTest.testZeroAllocationsInHotSimulationLoop`: **0 bytes** over 100k ticks.

---

## 5. Manual Verification
What was observed in the running game and editor (1280x720 window, 4x integer virtual scale):
- **Entity Palette**: Opened the Map Editor. The Entity Palette lists `moving_platform` (marked `placeable: true`) and properly omits `player` (`placeable: false`). Entering "plat" into the search box dynamically filters the list.
- **Entity Placement & Snap**: Selected `moving_platform` and clicked in the viewport. The platform was created at the cursor's snapped grid coordinate with instance ID `moving_platform_3` (incremented past authored platforms 1 and 2).
- **Selection & Gizmo Dragging**: Clicking the platform in the viewport selects it. A green collider outline (32x8 px) and a cyan trajectory line connecting to the endpoint handle appear. Dragging the endpoint handle updates the target coordinates smoothly in real-time.
- **Schema-Driven Inspector**: With the platform selected, the Inspector displays editable component fields for `body`, `collider`, and `platform`. Changing `speed` from 30 to 50 immediately modifies the override. Clicking the "Reset" button clears the override back to definition defaults.
- **Undo / Redo**: Pressed Ctrl+Z; the newly placed platform was removed from the document. Pressed Ctrl+Y; the platform and its endpoint trajectory were restored.
- **Play Mode (F7)**: Pressed F7 to enter Play mode. The snapshot was saved, and the real game simulation started immediately. The player character was able to jump on and ride both the authored and newly placed platforms. Platform movement exhibited exact timing and carry physics. Pressing F7 again stopped Play mode and restored the exact pre-play room state.
- **Validator Navigation**: Placed a platform whose trajectory intersected solid room tiles. The validator panel immediately reported `[WARN] ROOM_WARN_PLATFORM_CROSSES_SOLID`. Clicking the warning row automatically panned the viewport camera to center on the offending platform.
- **Visual Glitches**: None observed. Rendering of tiles, collision shapes, debug entity boxes, and gizmo lines was sharp with integer pixel alignment.

---

## 6. Open Decisions and Questions for the Architect
- **New Decisions Recorded in `docs/DECISIONS.md`**:
  - `DEC-025`: Numeric Typing in Component Schemas (`INT` vs `FLOAT`). Discrete pixel positions and ticks use `INT`; velocities, multipliers, and subpixel values use `FLOAT`.
  - `DEC-026`: Single-Source Component Schema Registry Instance. Instantiated at startup and passed down; no static singleton.
  - `DEC-027`: Component-Nested Entity Overrides in Room Format v3 (`{ "<compId>": { "<field>": value } }`).
  - `DEC-028`: Monotonic `entitySeq` Allocation and Undo `savedFloor` Clamping Rule to guarantee instance IDs that have reached disk are never reused.
- **Questions for the Architect**:
  1. In Milestone 7 (Combat, Stats, NPCs), what is the persistence and respawn lifecycle policy for non-carried room entities across room transitions (e.g. defeated enemies stay dead vs reset puzzle props)?
  2. Should future lightweight transient entities (such as gun projectiles and sparks) go through `EntitySpawner` or a dedicated pooled allocator?

---

## 7. Self-Review Against Section 12 (Self-Review Before Reporting)

- [x] **Bullet 1 (Every new file follows formatting and headers)**: PASS. All new Java, JSON, and Markdown files adhere to conventions, 2-space indentation, UTF-8 without BOM.
- [x] **Bullet 2 (All public APIs documented with purpose and invariants)**: PASS. All public classes, interfaces, and methods in `engine`, `data`, and `editor-map` have descriptive Javadoc comments.
- [x] **Bullet 3 (No TODOs or FIXMEs left behind without issue references)**: PASS. Grepped codebase; only deliberate `// TEMPORARY(M9)` marker on `EntityDebugRenderer` exists as agreed in brief.
- [x] **Bullet 4 (Every test passes locally on a clean checkout)**: PASS. Executed `./gradlew test --rerun`; all 242 tests passed cleanly.
- [x] **Bullet 5 (Test count reconciles with previous report)**: PASS. M6a total 200 + 42 new = 242 current total.
- [x] **Bullet 6 (Allocation checks run for 100k iterations and measure 0 bytes)**: PASS. `GameModeAllocationTest`, `M5bAllocationTest`, and `SimulationAllocationTest` measured strictly 0 bytes.
- [x] **Bullet 7 (Replay-hash tests verify bit-for-bit determinism)**: PASS. Verified by `TransitionDeterminismTest` and `RoomReplayStateHashTest`.
- [x] **Bullet 8 (Dependencies between modules conform to Principle 4.1)**: PASS. Verified by `ArchitectureDependencyTest` in `data` and `editor-map`.
- [x] **Bullet 9 (Systems hold no hidden state; all state in components)**: PASS. Verified by `WorldSnapshotTest` and entity spawner tests.
- [x] **Bullet 10 (Components contain only primitives and generational entity IDs)**: PASS. Components hold no object references or libGDX types.
- [x] **Bullet 11 (No game rules or simulation logic in UI or glue code)**: PASS. Editor models (`EntityPaletteModel`, `GizmoModel`, `InspectorModel`) are pure logic separated from ImGui rendering.
- [x] **Bullet 12 (All error paths log loud errors with context)**: PASS. All loaders and validators log descriptive error messages with line/file/code context.
- [x] **Bullet 13 (Hot-reload failures keep previous valid data without crashing)**: PASS. Validated by `MovementTuningWatcherTest` and `RoomCacheTest`.
- [x] **Bullet 14 (Manual verification performed in running application with notes)**: PASS. Detailed observations recorded in Section 5 of this report.
- [x] **Bullet 15 (Report adheres strictly to template in Section 13)**: PASS. All sections filled completely without skipping or hand-waving.
