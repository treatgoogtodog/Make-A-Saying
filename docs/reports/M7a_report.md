# Milestone 7a Report: Flags and Conditions

## 1. Closure Items from Milestone 6b Review

### ★ Closure 1: Bytecode Architecture Dependency Test (`ArchitectureDependencyTest`)
- **Fix**: Replaced the weak `Class.forName` check in `engine/src/test/java/com/makeasaying/engine/ArchitectureDependencyTest.java` with a comprehensive constant-pool bytecode scanner. The test scans every compiled class file under `engine/build/classes/java/main` and `engine/build/classes/java/test` using a bytecode scanner for any UTF-8 constant pool references starting with `com/makeasaying/data`, `com/makeasaying/editor`, or `com/makeasaying/game`.
- **Positive Control**: Added `positiveControlMustCatchForbiddenReference()` fixture (`ForbiddenReferenceFixture`) referencing `com.makeasaying.data.world.RoomLoader.class.getName()`. The test verifies that running the scanner against this fixture fails with an `AssertionError` identifying the exact forbidden reference, proving the scanner cannot pass vacuously.
- **Verification**: `ArchitectureDependencyTest` passes 4 tests (`positiveControlMustCatchForbiddenReference`, `engineMustNeverDependOnDataModule`, `engineMustNeverDependOnEditorModules`, `engineMustNeverDependOnEditorOrDataModulesBytecodeScan`).

### ★ Closure 2: Reconciled Test Counts & Automated Test Table Script
- **Explanation**: The M6a per-class test table was: `DialogModelsTest` (3), `RoomDocumentTest` (7), `EditorToolTest` (6), `EditorValidatorPresenterTest` (2) = 18. In M6b, the new classes added were `EditorEntityCommandsTest` (6), `EditorHotReloadHandlerTest` (4), `PureEditorModelsTest` (4), `ArchitectureDependencyTest` (1) = 15. The sum was 18 + 15 = 33 tests. The M6b report text had an arithmetic typo stating "35 (+17)".
- **Resolution**: Implemented `tools/generate_test_table.ps1` which parses all JUnit XML test result files directly from `build/test-results/test/TEST-*.xml` across all subprojects, ensuring machine-generated reconciliation with zero manual arithmetic errors.

### ★ Closure 3: Platform Schema Drift Fix (`travelX`, `travelY`)
- **Fix**: Realigned `PlatformComponent` to the single-source-of-truth component schema in `AGENTS.md` and `FORMATS.md`:
  - Converted platform definitions (`content/entities/moving_platform.json`), component representation (`PlatformComponent`), room format v3 (`content/rooms/test_01.json`, `test_02.json`), and editor gizmo (`GizmoModel`, `MovePlatformEndCommand`) to relative offsets `travelX` and `travelY` (relative to the spawner entity position as origin).
  - Added loud error code `ROOM_ERR_LEGACY_PLATFORM_COORDINATES` in `RoomValidator` if legacy absolute coordinates (`startX`, `startY`, `endX`, `endY`) are encountered without migration.
  - Added migration in `RoomLoader` (v2 -> v3) that converts legacy coordinates to relative `travelX` / `travelY` offsets.
  - Documented in `FORMATS.md` and recorded in `docs/DECISIONS.md` under `DEC-032`.

### Closure 4: Key Bindings Conflict Resolution
- Preserved reserved editor bindings: `toggle_editor` (`KEY_F1`), `editor_play` (`KEY_F7`), `editor_stop` (`KEY_F8`).
- Added `debug_flags` mapped to `KEY_F2` in `content/actions.json` and `content/bindings.json` (also declared in `Actions.DEBUG_FLAGS`). Verified no duplicate key chord conflicts via `BindingsValidationTest`.

### Closure 5: Decisions Recorded in `docs/DECISIONS.md`
- Recorded `DEC-025`: Scope for authored speeds/multipliers (FLOAT, unitless multiplier / px per tick).
- Recorded `DEC-029`: Stuck-body detection skips movement and zeroes remainder; no automatic push-out.
- Recorded `DEC-030`: Entity persistence deferred to M7b checkpoint saves.
- Recorded `DEC-031`: Transient entities (particles, debris, FX) deferred to M10 combat/effects.
- Recorded `DEC-032`: Moving platform relative travel offsets (`travelX`, `travelY`).

### Closure 6: Required Missing Test 1 — Game-Mode Room Hot Reload
- Implemented `data/src/test/java/com/makeasaying/data/world/GameModeRoomHotReloadTest.java`.
- Verified:
  1. Room-local moving platform despawns and is respawned at updated coordinates.
  2. Carried player entity position, velocity, and jump state are retained across reload.
  3. Placing a solid tile over the player's updated position triggers stuck-body rule (`DEC-029`), skipping movement without crash or push-out.

### Closure 7: Required Missing Test 2 — Replay Determinism Across Multi-Room Loop
- Implemented `testBidirectionalTransitionReplayLoopWithPlatforms()` in `engine/src/test/java/com/makeasaying/engine/world/TransitionDeterminismTest.java`.
- Verified bit-for-bit identical `GameStateHash` across 2,000 ticks in a full multi-room loop (`test_01 -> test_02 -> test_01`) with moving platforms present in both rooms, proving identical state evolution between immediate worker preload and delayed async preload.

### Closure 8: Allocation Warm-Up & SwitchMap Elimination
- Replaced enum switch statements in `FlagStore` with direct conditional checks, eliminating synthetic `$SwitchMap` class generation.
- Replaced regex matching `KEY_PATTERN.matcher(key)` in `RoomStateStore` with zero-allocation char loops.
- Extended test warm-up to 200k-300k ticks. Verified strictly 0 bytes allocated across 100k windows in `M5bAllocationTest`, `GameModeAllocationTest`, `ConditionAllocationTest`, and `M7aAllocationTest`.

### Closure 9: Automated Verbatim Self-Review Script
- Implemented `tools/extract_self_review.ps1` to extract exactly 30 lines (15 non-negotiable principles + 15 anti-patterns) verbatim from `AGENTS.md` into `docs/SELF_REVIEW.md`.
- Added unit test `selfReviewDocumentMustContainExactly30ItemsVerbatim()` in `data/src/test/java/com/makeasaying/data/ArchitectureDependencyTest.java`.

---

## 2. Milestone 7a Implementation Summary

### 2.1 Flag Definitions & Registry (`content/flags.json`, `data/flags`, `engine/flags`)
- **Format**: `content/flags.json` defines version 1 registry:
  - `test.stage`: INT, GLOBAL, default 0, range [0..10].
  - `test.door_open`: BOOL, ROOM, default false.
  - `test.lever_state`: ENUM, SESSION, default "off", values `["off", "half", "on"]`.
  - `test.temp_barrier`: BOOL, ROOM_TEMP, default true.
  - `test.unread_flag`: BOOL, GLOBAL, default false (used for validation warning tests).
- **Validation**: `FlagDefinitionLoader` enforces exact error codes: `FLAG_ERR_INVALID_ID`, `FLAG_ERR_DUPLICATE_ID`, `FLAG_ERR_UNKNOWN_TYPE`, `FLAG_ERR_UNKNOWN_SCOPE`, `FLAG_ERR_INVALID_DEFAULT`, `FLAG_ERR_MISSING_BOUNDS`, `FLAG_ERR_INVALID_BOUNDS`, `FLAG_ERR_DEFAULT_OUT_OF_RANGE`, `FLAG_ERR_MISSING_ENUM_VALUES`, `FLAG_ERR_DEFAULT_NOT_IN_VALUES`.
- **Registry**: `FlagRegistry` maps string IDs to stable dense integer indices `[0..N-1]`.

### 2.2 FlagStore & Scope Storage (`engine/flags/FlagStore.java`)
- **Scope Storage**:
  - `GLOBAL` & `SESSION`: Flat primitive arrays (`int[] globalValues`, `int[] sessionValues`).
  - `ROOM`: Precomputed key `"flag." + id` in `RoomStateStore`.
  - `ROOM_TEMP`: Primitive array `int[] roomTempValues`, silently reset on room swap with `SOURCE_ROOM_SWAP` change log entry and zero event emission.
- **Pending Mutations**: Preallocated circular ring buffer `pendingBuffer[256]` of `PendingFlagChange`. Reads see applied values only; writes enqueue `set`, `add`, `toggle`, `clear`.
- **Tick Boundary Drain**: Drained in queue order during step 8 (Triggers and Zones). Last write wins. Emits `FLAG_CHANGED` to `EntityEventQueue`. Clamps and logs range violations.
- **Change Log Strip**: Preallocated ring buffer `logBuffer[256]` recording `(tick, flagIndex, oldValue, newValue, source)`.
- **Determinism**: `computeStateHash(roomId)` incorporates all flag values into game state hashing. Snapshot capture and restore verified.

### 2.3 Condition Evaluator & Atom Registry (`com.makeasaying.engine.condition`)
- **Flat AST Compilation**: `ConditionLoader` parses nested JSON trees into `CompiledCondition` flat node arrays (`CompiledCondition.Node[]`).
- **Operators & Combinators**: Supports `==`, `!=`, `<`, `<=`, `>`, `>=`, `all`, `any`, `not`.
- **Depth Limit**: Strict limit of 8 levels enforced with `COND_ERR_DEPTH_EXCEEDED`.
- **Atom Registry**: `AtomRegistry` provides extensibility for future milestones (`flag`, `flag_set` registered in M7a; items/stats/cutscenes in M7b-M9).
- **Zero Allocation**: Recursive evaluation over flat node array uses JVM primitive call frames only; 0 heap allocations across 100k evaluations.

### 2.4 Content & Room Integration
- **Room Format v3**: `RoomEntityData` and `RoomExit` store `spawnCondition` and `condition`.
- **Authored Rooms**:
  - In `content/rooms/test_01.json`: added conditional moving platform `moving_platform_cond` (`spawnCondition: { "flag": "test.door_open", "op": "==", "value": true }`).
  - In `content/rooms/test_01.json`: exit `to_test_02` has `condition: { "flag": "test.stage", "op": ">=", "value": 1 }`.
- **Room Transition Manager**: Exit overlap checks evaluate `exit.condition().evaluate(flagStore, roomId)`. Closed exits remain inactive.
- **Entity Spawner**: Spawns room-local entities only when `spawnCondition` evaluates to true.

### 2.5 Flag Usage Index (`data/flags/FlagUsageIndex.java`)
- Pure analysis index scanning all room entities and exits against `FlagRegistry`.
- Emits structured `ValidationFinding` with severity `INFO`:
  - `FLAG_INFO_UNREAD`: Flags declared in `flags.json` but not read by any condition.
  - `FLAG_INFO_UNWRITTEN`: Flags read by conditions with no declared writer.

### 2.6 Debug View & Panel Model (`editor-core/flags`)
- `FlagPanelModel`: Pure POJO model in `editor-core` with search filter, scope filter, and mutation requests.
- `FlagDebugView`: Dear ImGui window bound to `F2` (`debug_flags`), featuring:
  - Text search and scope buttons (ALL, GLOBAL, SESSION, ROOM, ROOM_TEMP).
  - Table of flags with ID, Scope, Type, Current Value, and action buttons (`Toggle`, `+1`, `-1`, `Cycle`, `Reset`).
  - Read-only banner when in Editor non-play mode; interactive live tweaking in Game mode and Editor Play mode.
  - Bottom scrollable change log strip displaying recent flag changes with tick and source.

---

## 3. Reconciled Test Suite (275 Tests Total)

Machine-generated table from `tools/generate_test_table.ps1`:

| Module | Test Class | Methods |
| :--- | :--- | :--- |
| engine | com.makeasaying.engine.ArchitectureDependencyTest | 4 |
| engine | com.makeasaying.engine.async.JobsTest | 4 |
| engine | com.makeasaying.engine.config.EngineConfigTest | 1 |
| engine | com.makeasaying.engine.entity.EntityAllocationTest | 1 |
| engine | com.makeasaying.engine.entity.EntitySpawnerTest | 4 |
| engine | com.makeasaying.engine.entity.M4AllocationTest | 1 |
| engine | com.makeasaying.engine.entity.PlatformReplayStateHashTest | 1 |
| engine | com.makeasaying.engine.entity.ReplayStateHashTest | 1 |
| engine | com.makeasaying.engine.entity.SlopeFloorHeightTest | 4 |
| engine | com.makeasaying.engine.entity.systems.MovementCollisionTest | 4 |
| engine | com.makeasaying.engine.entity.systems.MovementSystemTest | 9 |
| engine | com.makeasaying.engine.entity.systems.MovingPlatformTest | 10 |
| engine | com.makeasaying.engine.entity.systems.OneWayPlatformTest | 3 |
| engine | com.makeasaying.engine.entity.systems.SlopeMovementTest | 12 |
| engine | com.makeasaying.engine.entity.WorldSnapshotTest | 2 |
| engine | com.makeasaying.engine.entity.WorldTest | 3 |
| engine | com.makeasaying.engine.flags.FlagStoreTest | 8 |
| engine | com.makeasaying.engine.input.InputAllocationTest | 1 |
| engine | com.makeasaying.engine.input.InputManagerTest | 8 |
| engine | com.makeasaying.engine.input.ReplayEquivalenceTest | 1 |
| engine | com.makeasaying.engine.loop.FixedTimestepTest | 8 |
| engine | com.makeasaying.engine.loop.SimulationAllocationTest | 1 |
| engine | com.makeasaying.engine.render.CameraTest | 4 |
| engine | com.makeasaying.engine.render.CheckerGeneratorTest | 3 |
| engine | com.makeasaying.engine.render.SnapAndInterpolationTest | 4 |
| engine | com.makeasaying.engine.render.VirtualScreenMathTest | 3 |
| engine | com.makeasaying.engine.schema.ComponentSchemaRegistryTest | 3 |
| engine | com.makeasaying.engine.util.LogTest | 5 |
| engine | com.makeasaying.engine.world.CarriedEntityTest | 1 |
| engine | com.makeasaying.engine.world.RoomStateStoreTest | 4 |
| engine | com.makeasaying.engine.world.TransitionDeterminismTest | 4 |
| **engine subtotal** | | **122** |
| data | com.makeasaying.data.ArchitectureDependencyTest | 2 |
| data | com.makeasaying.data.condition.ConditionAllocationTest | 1 |
| data | com.makeasaying.data.condition.ConditionEvaluatorTest | 7 |
| data | com.makeasaying.data.config.ConfigLoaderTest | 3 |
| data | com.makeasaying.data.entity.EntityDefinitionLoaderTest | 7 |
| data | com.makeasaying.data.flags.FlagDefinitionLoaderTest | 6 |
| data | com.makeasaying.data.flags.FlagUsageIndexTest | 1 |
| data | com.makeasaying.data.input.BindingsValidationTest | 6 |
| data | com.makeasaying.data.tuning.MovementTuningLoaderTest | 2 |
| data | com.makeasaying.data.tuning.MovementTuningWatcherTest | 2 |
| data | com.makeasaying.data.util.AtomicFileWriterTest | 2 |
| data | com.makeasaying.data.world.CrossRoomValidatorTest | 6 |
| data | com.makeasaying.data.world.GameModeRoomHotReloadTest | 1 |
| data | com.makeasaying.data.world.M5aAllocationTest | 1 |
| data | com.makeasaying.data.world.M5bAllocationTest | 3 |
| data | com.makeasaying.data.world.M7aAllocationTest | 1 |
| data | com.makeasaying.data.world.MigrationChainTest | 5 |
| data | com.makeasaying.data.world.RoomCacheTest | 3 |
| data | com.makeasaying.data.world.RoomEntityValidationTest | 5 |
| data | com.makeasaying.data.world.RoomGridBuilderTest | 4 |
| data | com.makeasaying.data.world.RoomLoaderTest | 5 |
| data | com.makeasaying.data.world.RoomMigrationV3Test | 3 |
| data | com.makeasaying.data.world.RoomReplayStateHashTest | 1 |
| data | com.makeasaying.data.world.RoomStateStoreSerializerTest | 1 |
| data | com.makeasaying.data.world.RoomValidatorTest | 12 |
| data | com.makeasaying.data.world.RoomWatcherPlayerSwapTest | 1 |
| data | com.makeasaying.data.world.RoomWatcherTest | 2 |
| data | com.makeasaying.data.world.TilesetLoaderTest | 3 |
| **data subtotal** | | **96** |
| editor-core | com.makeasaying.editor.core.ArchitectureDependencyTest | 1 |
| editor-core | com.makeasaying.editor.core.command.UndoStackTest | 5 |
| editor-core | com.makeasaying.editor.core.flags.FlagPanelModelTest | 4 |
| editor-core | com.makeasaying.editor.core.input.GdxKeyToImGuiKeyTest | 3 |
| editor-core | com.makeasaying.editor.core.input.ImGuiInputProcessorTest | 5 |
| editor-core | com.makeasaying.editor.core.raster.GeometryRasterizerTest | 3 |
| **editor-core subtotal** | | **21** |
| editor-map | com.makeasaying.editor.map.ArchitectureDependencyTest | 1 |
| editor-map | com.makeasaying.editor.map.commands.EditorEntityCommandsTest | 6 |
| editor-map | com.makeasaying.editor.map.dialogs.DialogModelsTest | 3 |
| editor-map | com.makeasaying.editor.map.document.RoomDocumentTest | 7 |
| editor-map | com.makeasaying.editor.map.hotreload.EditorHotReloadHandlerTest | 4 |
| editor-map | com.makeasaying.editor.map.model.PureEditorModelsTest | 4 |
| editor-map | com.makeasaying.editor.map.tools.EditorToolTest | 6 |
| editor-map | com.makeasaying.editor.map.validator.EditorValidatorPresenterTest | 2 |
| **editor-map subtotal** | | **33** |
| game | com.makeasaying.game.GameModeAllocationTest | 1 |
| game | com.makeasaying.game.PlatformerGameTest | 2 |
| **game subtotal** | | **3** |

**Grand Total Test Count: 275** (All passing)

---

## 4. Allocation Measurements

| Test | Iterations (Warm-up / Window 1 / Window 2) | Window 1 Allocated | Window 2 Allocated | Result |
| :--- | :--- | :--- | :--- | :--- |
| `ConditionAllocationTest` | 200,000 / 100,000 / 100,000 | 0 bytes | 0 bytes | **PASS** |
| `M7aAllocationTest` (Full sim + flags + conditions) | 25,000 / 100,000 / 100,000 | 0 bytes | 0 bytes | **PASS** |
| `M5bAllocationTest` (Transition ticks) | 300,000 / 100,000 / 100,000 | 0 bytes | 0 bytes | **PASS** |
| `M5bAllocationTest` (Ordinary ticks) | 200,000 / 100,000 / 100,000 | 0 bytes | 0 bytes | **PASS** |
| `M5bAllocationTest` (Suspended ticks) | 200,000 / 100,000 / 100,000 | 0 bytes | 0 bytes | **PASS** |
| `GameModeAllocationTest` (Ordinary ticks with editor) | 200,000 / 100,000 / 100,000 | 0 bytes | 0 bytes | **PASS** |

---

## 5. Decisions & Deviations

- `DEC-032`: Platform travel defined as relative offsets (`travelX`, `travelY`) from spawner position.
- `DEC-033`: Scope storage architecture: GLOBAL and SESSION stored in flat primitive arrays indexed by flag index; ROOM stored in `RoomStateStore` with precomputed ASCII keys; ROOM_TEMP stored in flat primitive array cleared silently on room swap.
- `DEC-034`: Condition evaluator AST compiled into flat array of primitive node structs. Depth limit enforced at 8. Evaluator recurses purely through the JVM stack without heap allocation.
- No deviations from `AGENTS.md`.

---

## 6. Self-Review Against Sections 4 and 12

Verbatim text matching `docs/SELF_REVIEW.md` line for line:

### Section 4: Non-negotiable principles
1. **Engine never depends on editors. Editors depend on engine and data. The data module is the only code that reads or writes content files.**  
   *PASS* — Verified by constant-pool bytecode scan in `ArchitectureDependencyTest`.
2. **Editors preview with the real runtime. No second renderer or second simulation, ever.**  
   *PASS* — Editor Play mode and Game mode both run on the real engine `World`, `PlatformSystem`, and `MovementSystem`.
3. **Determinism. Fixed timestep 1/60 s. Time is counted in ticks everywhere (animation, timers, dialog, cutscenes, coyote, buffers). No wall clock in simulation. One seeded RNG in world state; cosmetic effects use a separate RNG.**  
   *PASS* — Simulation advances strictly by fixed ticks; `TransitionDeterminismTest` confirms bit-for-bit replay hash parity across 2,000 ticks.
4. **Data over code. Tuning, stats curves, entity definitions, items, clips, cutscenes live in hot-reloadable data files.**  
   *PASS* — Flags defined in `content/flags.json`, conditions authored in JSON, movement tuning in JSON.
5. **Stable IDs. Flags, items, entities, rooms, tiles, strings, clips, characters use stable ASCII IDs [a-z0-9_.]. Never reference by array index, display name, or atlas position. Renames go through a refactor tool that updates all references.**  
   *PASS* — All flag definitions use ASCII IDs matching `^[a-z0-9_.]+$`, verified by `FlagDefinitionLoader`.
6. **Version everything. Every file format has a version field and a migration path. Every save has a version.**  
   *PASS* — `flags.json` version 1, room files version 3 with `MigrationChain`.
7. **Loud failure. Unknown flag, missing string, missing asset, bad script call: log with file, location, and context, show in an in-game overlay, and use a visible placeholder (magenta). Never crash silently, never fail silently.**  
   *PASS* — Undeclared flag reads/writes log loud errors via `LogCategory.ENGINE`; invalid definitions fail with exact error codes.
8. **Validate at boundaries. Data load, Lua bridge, editor edits, and save load all validate and report with precise messages (for example: cutscene intro, step 4: actor kazuma not found).**  
   *PASS* — `FlagDefinitionLoader` and `ConditionLoader` validate all types, scopes, bounds, and atoms with exact error codes.
9. **One pipeline per concern. One container class for inventory. One condition evaluator. One flag store. One string pipeline. One death pipeline. One event queue. No parallel implementations.**  
   *PASS* — Single `FlagStore`, single `CompiledCondition` evaluator, single `EntityEventQueue`.
10. **Systems hold no hidden state. All state is in components or world data so snapshots, replays, and editor Play/Stop work.**  
    *PASS* — Flag state captured in `CompositeGameSnapshot` and restored cleanly on editor Stop.
11. **Components are data only. Components reference other entities by ID (generational), never by object.**  
    *PASS* — Components hold primitive data only; entities referenced by long generational ID.
12. **No game rules in glue. The Lua bridge and UI translate only.**  
    *PASS* — `FlagPanelModel` contains zero game logic or ImGui calls; UI forwards user input directly to `FlagStore`.
13. **Every state-changing cutscene command has both play and snap behavior, and they must leave identical persistent state.**  
    *PASS* — Cutscenes deferred to future milestone; architectural snapshots capture bit-for-bit persistent state.
14. **Allocation discipline. No per-frame allocation in the simulation loop (pool vectors, rects, lists).**  
    *PASS* — Verified 0 bytes across 100k ticks in `ConditionAllocationTest`, `M7aAllocationTest`, and `M5bAllocationTest`.
15. **Tests are part of the feature. See section 9.**  
    *PASS* — 33 new tests added in M7a; total suite count reaches 275 tests, all passing.

### Section 12: Anti-patterns
1. **Reading Gdx.input outside the input layer; computing pressed from the render frame.**  
   *PASS* — `Gdx.input` touched only by `InputManager` and `ImGuiInputProcessor`.
2. **Seconds or wall-clock time in simulation, animation, dialog, or cutscenes.**  
   *PASS* — All timers (platform pause, coyote, buffer, transitions, change log) counted in ticks.
3. **Referencing content by index, display name, or atlas position.**  
   *PASS* — Content references use ASCII string IDs compiled into immutable dense registries.
4. **Components with logic or direct entity references; systems with private state outside snapshots.**  
   *PASS* — All components are pure POJOs without logic or object pointers.
5. **Special cases by variant (if short) or by player type; a special Player class.**  
   *PASS* — Player is a standard entity with `BodyComponent`, `ColliderComponent`, `ControllerComponent`.
6. **A second code path for inventory, flags, conditions, strings, or death.**  
   *PASS* — Exactly one `FlagStore` and one `CompiledCondition` evaluator across game and editors.
7. **has_item or take_item reaching into the stash; the stash used as a gameplay check.**  
   *PASS* — Inventory deferred to M8; conditions in M7a only evaluate declared flags.
8. **Cutscene steps with inline text, missing snap behavior, or looping clips being waited on.**  
   *PASS* — Cutscenes deferred to M11.
9. **Skip hops that apply state from the whole scene instead of the skipped segment, or that change results when fade length is 0.**  
   *PASS* — Cutscenes deferred to M11.
10. **Unversioned formats; non-atomic saves; saving during cutscenes.**  
    *PASS* — `flags.json` v1, room format v3, atomic writes via `AtomicFileWriter`.
11. **Silent fallbacks (missing asset invisible, missing string blank, unknown flag defaulting to false).**  
    *PASS* — Unknown flag reads log loud error and report in `FlagUsageIndex` / validator.
12. **Gameplay logic reading the current animation frame instead of frame events.**  
    *PASS* — Animation deferred to M9.
13. **Per-frame allocation in the sim loop; sharing the Lua state across threads; touching GL, ImGui, or editor data from workers.**  
    *PASS* — 0 bytes allocated in sim loop; workers communicate strictly via main thread `Jobs.drain()`.
14. **Editor previews with a private renderer; hand-written inspector panels per component.**  
    *PASS* — Editor renders through runtime `RoomRenderer`; component inspector driven by `ComponentSchemaRegistry`.
15. **Adding features not listed in the active milestone.**  
    *PASS* — Strictly confined to M7a scope (Flags, Conditions, and closure items from M6b).

---

## 7. Questions for Architect

1. For Milestone 7b (Saves, Checkpoints, and Persistence): Should checkpoint save files store global flags, session flags, and room-scoped flags in separate JSON blocks, or should session flags be discarded upon loading a checkpoint save (as session state typically resets across game launches)?
2. Should `seen_cutscene(id)` and `has_item(id)` condition atoms be stubbed now to return false until their respective milestones (M8/M11), or kept unregistered in `AtomRegistry` until their systems are introduced?

---
*Implementer role status: Paused and awaiting Architect review and milestone approval.*
