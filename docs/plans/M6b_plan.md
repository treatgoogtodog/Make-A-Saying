# Milestone 6b Plan: Entity Definitions, Spawning, and Placement

## 1. M6a Closure Pass (First Priority)
Before starting M6b feature code, execute and verify all required closure items:

1. **DECISIONS.md & Startup Evidence**:
   - Record exact resolved Maven coordinates (`imgui-java:1.89.0` with `imgui-java-natives-windows`, `linux`, `macos`), startup GL profile (`OpenGL 3.2 Core`, `#version 150`), and GL version string in `docs/DECISIONS.md`.
   - Confirm GL30 visual parity: render paths (tiles, fallback magenta checker, integer-scaled framebuffer) are identical with no visual regressions.
   - Confirm ImGui corrections: pass-through mouse Y, logical display size + backbuffer ratio scale set per frame, key events via `addKeyEvent(ImGuiKey)` including modifiers.

2. **Actions & Bindings Cleanliness**:
   - Add `editor_undo` (`Ctrl+Z`) and `editor_redo` (`Ctrl+Y`) to `content/actions.json` and `content/bindings.json` under the `"editor"` context.
   - Ensure all editor actions (`toggle_editor`, `editor_play`, `editor_stop`, `editor_undo`, `editor_redo`) go strictly through `InputManager` and `InputState`. Zero hard-coded keys.

3. **Transition Timeout (Monotonic Clock)**:
   - Refactor `RoomTransitionManager` to take a monotonic `LongSupplier nanoTimeSupplier` (defaulting to `System::nanoTime`).
   - Define `TIMEOUT_NANOS = 10_000_000_000L` (10 seconds wall-clock).
   - Update `TransitionDeterminismTest.testStalledLoaderTimesOutAndAbortsTransition` using synthetic time advancement to prove timeout independence from render frame rate.

4. **100k-Tick Allocation Test with Editor Modules**:
   - Add `GameModeAllocationTest`: runs Game mode with editor modules on classpath.
   - Warm-up for 25,000 ticks, then measure two consecutive 100,000-tick windows using `ThreadMXBean.getThreadAllocatedBytes`. Target: strictly 0 bytes in both windows.

5. **Missing M6a Tests**:
   - `EditorHotReloadHandlerTest`: clean reload, dirty conflict, self-write ignored.
   - `SnapshotDeterminismTest`: populated world with stale IDs, pending spawns, reused free list; snapshot -> run N ticks -> restore -> assert hash matches; replay same input -> assert identical hash.
   - `ArchitectureDependencyTest` in `editor-map`: no dependencies on `game`; verify nothing outside `game` depends on `editor-core` or `editor-map`.
   - `RoomDocumentRoundTripTest`: byte-identical round-trip verification on `test_01.json` and `test_02.json`.

6. **Configuration & Cleanliness**:
   - Confirm Dear ImGui ini file is disabled/redirected during automated tests (pass `null` or test temp dir).
   - Confirm `.gitignore` ignores `~/.makeasaying` and local editor caches.

---

## 2. Milestone 6b Architecture & Scope

```
                      +-----------------------------+
                      |   content/entities/*.json   |
                      +--------------+--------------+
                                     |
                                     v
+--------------------+      +------------------+      +---------------------+
|    engine: Schema  | ---> |   data: Entity   | ---> | engine: Spawner &   |
|      Registry      |      | Loader/Validator |      |    PlayerFactory    |
+---------+----------+      +------------------+      +----------+----------+
          |                                                      |
          v                                                      v
+--------------------+                                +---------------------+
| editor-map: Pure   |                                | engine: Temporary   |
|  InspectorModel    |                                | EntityDebugRenderer |
+---------+----------+                                +---------------------+
          |
          v
+--------------------+
| editor-map: ImGui  |
| UI & Viewport Draw |
+--------------------+
```

### 2.1 Entity Definitions (`data` & `content/entities/`)
- Format: `version`, `id`, `tags` (optional list), `debugColor` (optional hex string `"#RRGGBB"` or `"#RRGGBBAA"`), `components`: map from component ID to field-value map.
- Create `content/entities/player.json` (Body, Collider 12x36, Facing, Controller: PLAYER, RoomScope: PERSISTENT_CARRIED, JumpState defaults).
- Create `content/entities/moving_platform.json` (Body, Collider 48x16, Platform: travelX, travelY, speed, pauseTicks, crushMode, RoomScope: ROOM_LOCAL).
- Loader and validator in `data`, runtime data representations in `engine` (DEC-002).

### 2.2 Component Schema Registry (`engine`)
- `ComponentSchemaRegistry`:
  - Static/singleton registry mapping component IDs to `ComponentSchema`.
  - `ComponentField`: `name`, `type` (`INT`, `FLOAT`, `BOOL`, `ENUM`, `STRING`), `defaultValue`, `min`, `max`, `tooltip`, `overridable`, `enumConstants`.
  - Schema rule (DEC-025): **`INT` is used for discrete counts, ticks, offsets, and pixel dimensions; `FLOAT` is used exclusively for authored speeds (px/s)**.
  - Accessors: `apply(World world, long entity, Map<String, Object> values)` and `read(World world, long entity)`. Maps to existing component storage without refactoring components.
  - Registered components: `Body` (spawn position only), `Collider`, `Facing`, `Controller`, `RoomScope`, `JumpState`, `Platform`.
  - Single source of truth: Loader validation, Spawner, and Inspector are driven by this schema. Adding a new field shows up across all three automatically. Verified via synthetic component unit test.

### 2.3 Spawning Pipeline (`engine`)
- `InstanceIdComponent`: Stores the room instance ID string (`<definition>_<n>`), included in `GameStateHash`.
- `EntitySpawner`:
  - Input: `EntityDefinition`, overrides map, position `(x, y)` (feet origin, integer pixels).
  - Validates overrides against schema.
  - Allocates entity via deferred spawn queue (`World.spawnDeferred`), applying definition values followed by validated overrides.
- `PlayerFactory`: Refactored into a thin wrapper around `EntitySpawner` using `content/entities/player.json`. Collider size comes from data. Spawn parity test verifies state hash matches hand-built player.
- Deterministic Room Spawning:
  - Swapping rooms spawns the room's entities during the swap tick.
  - Ordinary ticks remain strictly zero-allocation.
  - Hot-reload of room in Game mode despawns room-local entities and respawns them from updated data at tick boundary. Player retains position (stuck-body rule applies). Platform state reset is accepted (DEC-026).
  - Editor Play spawns the room's entities into the in-memory simulation.

### 2.4 Room Format v3 & Migrations (`data`)
- Room schema v3:
  - Add `entitySeq`: monotonic integer for generating instance IDs (`<definition>_<entitySeq>`).
  - Structured component overrides: `{ "<componentId>": { "<field>": value } }`.
  - Reject unknown format v4+.
- Migrations:
  - Real migration v2 -> v3: calculates `entitySeq = max(numeric suffix of existing IDs) + 1` (or 1 if none).
  - Chain migration v1 -> v2 -> v3 preserves backward compatibility.
  - Dedicated fixtures: `test_room_v1.json`, `test_room_v2.json`.
- Populate real entities in `content/rooms/test_01.json` (platforms) and `test_02.json`, eliminating all hardcoded demo platform code.

### 2.5 Validation with Stable Codes & Exact Locations (`data`)
- Definition validations:
  - `DEF_ERR_UNKNOWN_COMPONENT`, `DEF_ERR_UNKNOWN_FIELD`, `DEF_ERR_TYPE_MISMATCH`, `DEF_ERR_OUT_OF_RANGE`, `DEF_ERR_MISSING_REQUIRED`, `DEF_ERR_DUPLICATE_ID`, `DEF_ERR_INVALID_ID`.
- Room validations:
  - `ROOM_ERR_UNKNOWN_DEF`, `ROOM_ERR_NON_OVERRIDABLE_FIELD`, `ROOM_ERR_OVERRIDE_OUT_OF_RANGE`, `ROOM_ERR_DUPLICATE_INSTANCE_ID`, `ROOM_ERR_ENTITY_SEQ_INVALID`, `ROOM_ERR_ENTITY_OUT_OF_BOUNDS`.
  - Warnings: `ROOM_WARN_SOLID_INSIDE_SOLID` (entity solid collider intersecting solid tile), `ROOM_WARN_PLATFORM_CROSSES_SOLID` (travel line leaves room or intersects solid).
- Integrated into `EditorValidatorPresenter` with one-click viewport navigation.

### 2.6 Editor: Pure Models, Commands & Inspector (`editor-map`)
- **Pure Models**:
  - `EntityPaletteModel`: Search/filter definitions, track selected definition.
  - `SelectionModel`: Deterministic picking order: smallest collider area first, then lowest sequence number.
  - `GizmoModel`: Hit-testing for bounding boxes and platform travel endpoint handles.
  - `InspectorModel`: Builds UI property trees from schema. Tracks overridden state, generates field edits, handles "Reset to Default".
- **Editor Commands**:
  - `PlaceEntityCommand`: Allocates next `entitySeq` (undo restores counter; disk saves never reuse).
  - `MoveEntityCommand`: Drag entity position, grouped into a single undo step.
  - `DeleteEntityCommand`: Removes entity from document.
  - `EditOverrideFieldCommand`: Updates or resets component field override in one undo step on deactivate.
  - `MovePlatformEndCommand`: Adjusts `travelX` / `travelY` platform offset via end marker gizmo drag.
- **Viewport Gizmos & UI**:
  - Entity Palette window with search and color swatches.
  - Place Tool: grid-snap (16x16) vs pixel-snap toggle.
  - Select Tool: click to select, drag to move, `Delete` key to delete.
  - Schema-driven Inspector: integer sliders/inputs, float inputs, checkboxes, combo boxes for enums, string inputs, reset buttons. Overridden fields highlighted. Read-only display for non-overridable fields.
  - Viewport Gizmos (ImGui draw list): collider bounds rect, platform path vector with draggable endpoint handle, selected entity instance ID label.

### 2.7 Temporary Entity Rendering (`engine`)
- `EntityDebugRenderer`:
  - Renders filled bounding box with `debugColor` at collider position.
  - Single draw path for entities in both Game and Editor modes.
  - Clearly marked with `@Deprecated` / Javadoc for deletion in M9 when sprite rendering is introduced.

---

## 3. Verification & Testing Strategy
- **Unit & Integration Tests**:
  - `ComponentSchemaRegistryTest`: registration, read/apply, synthetic component addition single-source verification.
  - `EntityDefinitionLoaderTest`: valid loading, all error codes with exact messages.
  - `RoomMigrationV3Test`: v1->v3, v2->v3, v4 rejection, entitySeq uniqueness across delete/save/reload.
  - `RoomWriterV3Test`: byte-identical round trip on v3 files.
  - `EntitySpawnerTest`: definition + overrides, PlayerFactory parity, spawn determinism.
  - `ReplayDeterminismTest`: replay hash across `test_01` -> `test_02` -> `test_01` with platforms and player.
  - `PureEditorModelsTest`: `SelectionModel` picking order, `GizmoModel` hit-testing, `InspectorModel` reset/override logic.
  - `EditorEntityCommandsTest`: place, move, delete, field edit, platform drag with undo/redo grouping.
  - `GameModeAllocationTest`: 25k tick warm-up, two consecutive 100k windows asserting strictly 0 bytes.
- **Manual Checklist**:
  - Place player and moving platforms; edit platform `travelX`, `travelY`, `speed`, and `crushMode`.
  - Drag platform end gizmo; test undo/redo repeatedly.
  - Save room, reload, and verify persistence.
  - Enter Play mode (F7): verify player rides moving platforms and squish/crush rules operate in-memory.
  - Trigger deliberate definition and room errors: verify validator output and click-to-focus navigation.

---

## 4. Questions & Open Decisions for Architect
1. **Decision Record DEC-025**:
   - Proposed: Component schema registry enforces that `INT` is used for all discrete offsets, dimensions, ticks, and states; `FLOAT` is used strictly for authored speeds (e.g. `px/s`), which are converted once at spawn time to fixed-point velocity.
2. **Decision Record DEC-026**:
   - Proposed: Hot-reloading a room in Game mode despawns room-local entities and respawns them at initial positions; player retains position with stuck-body checking. Platforms reset to their initial path state.
