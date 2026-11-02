# Architecture Decisions Log

This document records all architectural decisions, especially when resolving open items from Section 11 of `AGENTS.md`.

Format for entries:
- **Decision ID**: DEC-XXX
- **Date**: YYYY-MM-DD
- **Context**: Problem statement and constraints.
- **Choice**: The chosen approach.
- **Alternatives Considered**: Other options and why they were not chosen.
- **Reversibility**: Easy / Moderate / Hard.

---

### DEC-001: M0 Module Hierarchy and Build Tooling
- **Date**: 2026-11-01
- **Context**: Need a multi-module build setup enforcing strict separation: engine never depends on editors; editors depend on engine and data; data is isolated for file serialization.
- **Choice**: Gradle multi-module build with Java 21 LTS (`Temurin-21.0.9`), Gradle 8.14.3 wrapper, libGDX 1.13.1, JUnit 5.11.0, Jackson 2.17.2, ImGui-Java 1.89.0, LuaJ 3.0.1.
- **Alternatives Considered**: Maven (less standard for libGDX ecosystem), Single monolithic module (violates Rule 4.1).
- **Reversibility**: Moderate.

---

### DEC-002: Dependency Direction between Engine and Data
- **Date**: 2026-11-01
- **Context**: Clarification on module boundaries between `engine` and `data`.
- **Choice**:
  - `engine` defines runtime domain models and interfaces, holds zero file I/O code, and never reads or writes files.
  - `data` depends on `engine` (`api project(':engine')`), contains Jackson serializers/deserializers, validators, migrations, and is the sole module reading/writing content files.
  - `game` and editors wire `engine` and `data` together.
  - `engine -> data` is strictly forbidden and enforced by Gradle and architecture tests.
- **Alternatives Considered**: `engine` depending on `data` (rejected: would force `engine` to take serialization dependencies or create duplicate DTOs).
- **Reversibility**: Hard (foundational architectural boundary).

---

### DEC-003: FixedTimestep Precision and Checker Resolution
- **Date**: 2026-11-01
- **Context**: Precision drift prevention in accumulator over extended gameplay; placeholder texture readability without scaling; headless testability.
- **Choice**:
  - `FixedTimestep` uses `double` precision internally for accumulator and tick step; incoming frame deltas clamped to 0.1s; capped at 5 ticks/frame with excess dropped and rate-limited warning.
  - `CheckerGenerator` produces 16x16 RGBA8888 checker data with 8px squares in pure Java (tested without LWJGL natives).
  - `VirtualScreenMath` separates viewport scaling calculation from GL context.
- **Alternatives Considered**: 2x2 texture (rejected: too small at 1x without runtime scaling), single-precision float accumulator (rejected: precision loss across high-frequency frames).
- **Reversibility**: Easy.

---

### DEC-004: Opposing Movement Direction Resolution (Last-Pressed-Wins)
- **Date**: 2026-11-01
- **Context**: When opposite directional inputs are held simultaneously (e.g. Left and Right on `move_x`), the engine must deterministically resolve the axis value {-1, 0, 1}.
- **Choice**: Last-pressed-wins. The most recently pressed direction dictates the axis direction. If the winning key is subsequently released while the opposing key remains held, the remaining key immediately resumes dominance.
- **Alternatives Considered**: Zero-cancel (Left + Right = 0; rejected: sticky feel during rapid direction reversals), First-pressed-wins (rejected: impedes modern responsive platformer controls).
- **Reversibility**: Moderate.

---

### DEC-005: Keyboard Scancodes vs Keycodes Verification
- **Date**: 2026-11-01
- **Context**: Verification of LWJGL3 GLFW key event tokens.
- **Choice**: Physical positions mapped through string key names (e.g. `KEY_W`, `KEY_A`, `KEY_S`, `KEY_D`) to `Input.Keys`. Verified on standard US QWERTY physical keyboard layout. Display names will be abstracted when rebinding UI is introduced in M7+.
- **Alternatives Considered**: Direct integer GLFW scancodes in JSON files (rejected: non-diffable and non-portable).
- **Reversibility**: Easy.

---

### DEC-006: Generational Entity Layer and Component Storage
- **Date**: 2026-11-01
- **Context**: Need a small, fast, deterministic entity system without heavy external ECS libraries (Ashley, Box2D banned by Section 3).
- **Choice**:
  - `EntityId` packed as 64-bit primitive `long`: high 32 bits generation, low 32 bits index. Index 0 is reserved as invalid/null entity ID.
  - Preallocated dense component pools/arrays indexed directly by entity slot (`0..MAX_ENTITIES-1`), with recycled slots on despawn.
  - Systems iterate entities strictly in ascending index order to guarantee simulation determinism.
  - Generation mismatch cleanly returns absent/false on stale entity queries.
  - Spawns and despawns are deferred to the end of the tick.
- **Alternatives Considered**: Hash-map component stores (rejected: per-tick heap allocations and non-deterministic iteration order), Ashley/artemis-odb (banned by Section 3).
- **Reversibility**: Moderate.

---

### DEC-007: Coordinate System and Tile Grid Convention
- **Date**: 2026-11-01
- **Context**: Tile grid and world spatial orientation.
- **Choice**: World coordinates are Y-up, with tile (0, 0) anchored at the bottom-left. Data loaders in M5 flip top-to-bottom authored room rows to maintain Y-up internal simulation coordinates.
- **Alternatives Considered**: Screen Y-down (rejected: makes jump physics and gravity negative, confusing standard platformer equations).
- **Reversibility**: Hard.

---

### DEC-008: Out-of-Bounds Tile Boundary Collision
- **Date**: 2026-11-01
- **Context**: Entities probing tiles outside the loaded room dimensions.
- **Choice**: Out-of-bounds tiles return `SOLID` in M3/M4. In M5, camera zones and hazard/kill zones may selectively override this.
- **Alternatives Considered**: Returning `EMPTY` (rejected: causes entities to fall out of room before boundary triggers exist).
- **Reversibility**: Easy.

---

### DEC-009: Symmetric Remainder Rounding Rule
- **Date**: 2026-11-01
- **Context**: Subpixel integer pixel snapping must remain perfectly symmetric across negative and positive directions.
- **Choice**: Reuses `Snap.toPixel` rule: `value >= 0 ? (int)(value + 0.5f) : (int)(value - 0.5f)`. Guarantees leftward and rightward movement yield identical mirrored pixel coordinates.
- **Alternatives Considered**: Java `Math.round` (rejected: asymmetric around -0.5, creating a 1-pixel drift between left and right movement).
- **Reversibility**: Hard.

---

### DEC-010: Tuning Units and Per-Tick Conversion
- **Date**: 2026-11-01
- **Context**: Human readability of physics tuning vs deterministic simulation ticks.
- **Choice**: `content/tuning/movement.json` specifies velocities in pixels/second, accelerations in pixels/second², and timers/buffers in ticks. `MovementTuningLoader` converts these once at load time into per-tick values (`px/tick` and `px/tick²`). Simulation systems read strictly per-tick values.
- **Alternatives Considered**: Authoring per-tick values directly in JSON (rejected: counter-intuitive for humans to author 0.042 px/tick²).
- **Reversibility**: Easy.

---

### DEC-011: Slope Floor Height Pure Function Formula
- **Date**: 2026-11-01
- **Context**: Determining the exact floor height in pixels above the tile bottom for tile column c in [0, 15] for 16x16 tiles.
- **Choice**: Pure function `TileGrid.floorHeightAt(shape, localX)` implementing:
  - `SLOPE_45_UP_RIGHT`: `c + 1` (column 0 is 1px, column 15 is 16px).
  - `SLOPE_45_UP_LEFT`: `16 - c` (column 0 is 16px, column 15 is 1px).
  - `SOLID` & `ONE_WAY`: `16` (full tile height).
  - `EMPTY`: `0`.
  Provides a continuous staircase across tile seams (+1 px per pixel) without gaps against filled flat ground.
- **Alternatives Considered**: 0-indexed formula `c` (rejected: leaves a 1-pixel gap against solid tiles and 0 height at bottom column).
- **Reversibility**: Hard.

---

### DEC-012: Two-Column Slope Surface Sampling
- **Date**: 2026-11-01
- **Context**: A 12px collider with bottom-center origin at offset `[-6, +6)` has an even width, meaning there is no single center pixel column.
- **Choice**: Sample the two central columns `feetX - 1` and `feetX`, taking `Math.max(surf1, surf2)`. This guarantees exact mirror symmetry between `SLOPE_45_UP_RIGHT` and `SLOPE_45_UP_LEFT`.
- **Alternatives Considered**: Single column sampling at `feetX` (rejected: breaks left/right mirror symmetry because offset is asymmetrical).
- **Reversibility**: Easy.

---

### DEC-013: Slope Floor-Only Rule and Adjacent Solid Seam Resolution
- **Date**: 2026-11-01
- **Context**: Slopes must provide floor only and never block horizontal movement or act as ceilings. Furthermore, when a 12px collider walks up a slope toward a flat solid ground tile, the leading edge of the collider penetrates the solid tile column before the feet reach the top of the slope.
- **Choice**: Solid tiles whose exposed face is flush with and covered by an adjacent slope are treated as walkable floor, not blocking walls, under the following strict conditions:
  - **Axis**: Horizontal stepping checks only (X axis). Vertical collision uses standard `getFloorSurface` and normal floor/ceiling rules.
  - **Entity State**: Grounded entities only (`body.grounded == true`). Airborne entities (`body.grounded == false`) are completely unaffected and collide with all `SOLID` tiles normally.
  - **Which Tiles**:
    - Rightward motion (`signX > 0`): Solid tile at `(tx, ty)` whose left neighbor `(tx - 1, ty)` is `SLOPE_45_UP_RIGHT`, provided `feetX < tx * tileSize` and `feetY <= (ty + 1) * tileSize`.
    - Leftward motion (`signX < 0`): Solid tile at `(tx, ty)` whose right neighbor `(tx + 1, ty)` is `SLOPE_45_UP_LEFT`, provided `feetX >= (tx + 1) * tileSize` and `feetY <= (ty + 1) * tileSize`.
    - Any solid tile at `ty + 1` or higher (a wall taller than the slope top) blocks horizontal movement normally.
- **Alternatives Considered**: Shrinking the horizontal collider width on slopes (rejected: complicates collision shapes and causes inconsistency).
- **Reversibility**: Medium.

---

### DEC-014: Platform Squish Rule (BLOCK vs CRUSH)
- **Date**: 2026-11-01
- **Context**: Moving platforms pushing entities into static walls or ceilings.
- **Choice**:
  - `BLOCK`: Platform tests if moving entity would push it into a solid tile. If so, platform halts and resets remainder to 0 to prevent catapulting/tunneling.
  - `CRUSH`: Platform pushes through, detects overlap with solid tile, and emits `CRUSHED` event to the event queue.
- **Alternatives Considered**: Reverse platform direction on block (rejected: pauses/halts are standard and overridable in data).
- **Reversibility**: Easy.

---

### DEC-015: No Velocity Inheritance from Platforms
- **Date**: 2026-11-01
- **Context**: Entity jumping off or walking off a moving platform.
- **Choice**: Per AGENTS.md principle 4.3 (determinism) and section 7.5, leaving a moving platform does NOT impart platform momentum/velocity onto the entity. Entity velocity is governed entirely by standard `InputState` and physics tuning.
- **Alternatives Considered**: Adding platform velocity to jumping entity (rejected: violates explicit specification and introduces momentum drift).
- **Reversibility**: Easy.

---

### DEC-016: Room Warning Size Default Threshold
- **Date**: 2026-11-01
- **Context**: Open Decision 6 in Section 11 asks for the maximum room dimensions before flagging a room above an agreed maximum size.
- **Choice**: Default warning threshold is set to 64x48 tiles (1024x768 pixels, roughly 3.2x by 4.2x screens at 320x180 virtual resolution). Rooms exceeding this threshold trigger a non-blocking `WARN_ROOM_SIZE` finding in `RoomValidator`.
- **Alternatives Considered**: Hard-erroring on larger rooms (rejected: some special open areas might be wider; warning is sufficient to flag unexpected size).
- **Reversibility**: Easy (configurable via `RoomValidator` constructor).

---

### DEC-017: Room Row Serialization and Coordinate Inversion
- **Date**: 2026-11-01
- **Context**: Room layer token grids are authored as rows of tokens in JSON. Natural text reading is top-to-bottom, while game world simulation coordinate space is Y-up (y=0 at floor).
- **Choice**: Row 0 in JSON represents the top of the room (`y = height - 1`), and the final row in JSON represents the bottom of the room (`y = 0`). `RoomLoader` inverts row index `height - 1 - y` into runtime memory, and `RoomWriter` inverts back on save.
- **Alternatives Considered**: Storing row 0 as y=0 in JSON (rejected: makes hand-editing and visual inspection in text editors inverted upside down).
- **Reversibility**: Medium.

---

### DEC-018: Deterministic Transition Loading Delay
- **Date**: 2026-11-01
- **Context**: If the target room is not yet loaded into memory when the transition state machine reaches the swap point (`HOLD_WAIT_SWAP` after `holdTicks`), the simulation loop must remain completely deterministic regardless of variable worker I/O completion speed.
- **Choice**: When waiting for an async room load during `HOLD_WAIT_SWAP`, simulation tick advance is suspended, the accumulator delta is discarded, the screen renders solid black, and background Jobs are drained. The simulation tick counter advances by the exact same number of ticks (1 trigger tick in IDLE + 12 fade out + 4 hold + 1 swap + 12 fade in = 30 ticks total, 29 non-IDLE ticks) whether the target room is ready immediately or delayed.
To make timeout frame-rate-independent, `RoomTransitionManager` uses an injected monotonic clock (`System.nanoTime` by default). If waiting exceeds 10 seconds wall-clock (`10_000_000_000L` nanos), the transition aborts loudly, fades back in, and disables the exit until the player leaves its bounds. Note: A transition abort due to a hung worker thread or missing file is an exceptional error recovery outside the deterministic replay guarantee.
- **Alternatives Considered**: Advancing simulation ticks while waiting (rejected: causes replays and state hashes to diverge based on variable disk speed).
- **Reversibility**: Easy.

---

### DEC-019: Combined Game-State Hash
- **Date**: 2026-11-01
- **Context**: Principle 4.3 (Determinism) requires a repeatable state hash for test verification and replays across room transitions, but Principle 4.1 / DEC-002 prohibits `World` from depending on `RoomStateStore` or high-level game-flow states.
- **Choice**: `GameStateHash` is implemented as an independent utility function combining `world.stateHash()`, active `roomId`, `transitionManager` state and progress tick, and `roomStateStore.stateHash()`. `World` remains decoupled and focused purely on entity component state.
- **Alternatives Considered**: Embedding room state or roomId into `World` (rejected: violates separation of concerns and single responsibility).
- **Reversibility**: Easy.

---

### DEC-020: Input Handling During Room Transitions
- **Date**: 2026-11-01
- **Context**: Handling user input and replay input during room transition fades.
- **Choice**:
  1. The input layer continues to drain raw OS events and record every tick throughout transitions, ensuring replay streams remain aligned and no stale "pressed" events leak out after fade-in.
  2. Entity simulation systems (PlayerControlSystem, PlatformSystem, MovementSystem) are frozen, so entity inputs are not applied to player movement.
  3. Keys that remain held down after fade-in completes apply normally upon entering `IDLE`. Any tap that begins and ends entirely within the transition window is consumed without affecting gameplay.
- **Alternatives Considered**: Dropping all input events during transitions (rejected: corrupts replay synchronization).
- **Reversibility**: Easy.


---

### DEC-021: OpenGL 3.2 Core Context for ImGui 1.89
- **Date**: 2026-11-01
- **Context**: Dear ImGui 1.89 via `imgui-java` on LWJGL3 GL context requires an OpenGL 3.2+ Core profile when using `#version 150` GLSL shader.
- **Resolved Artifacts & Dependencies**:
  - `io.github.spair:imgui-java-app:1.89.0`
  - `io.github.spair:imgui-java-binding:1.89.0`
  - `io.github.spair:imgui-java-natives-windows:1.89.0`
  - `io.github.spair:imgui-java-natives-linux:1.89.0`
  - `io.github.spair:imgui-java-natives-macos:1.89.0`
- **Startup GL Profile & Evidence**:
  - Configured `appConfig.setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.GL30, 3, 2)` in `GameLauncher`.
  - Startup GL version string: `OpenGL version: 3.2.0 Core Profile Context`.
  - Visual render parity: All existing render paths (tile maps, fallback magenta checker pattern, FrameBuffer integer scaling to window) verified visually identical with zero distortion or color deviation.
  - ImGui integration corrections:
    1. Mouse Y coordinate is passed through unchanged (`screenY` from libGDX top-left matches ImGui top-left).
    2. Display size and framebuffer scale (`backbufferWidth / logicalWidth`) set dynamically every frame.
    3. Keyboard events routed through `addKeyEvent(ImGuiKey)` with full modifier tracking.
- **Alternatives Considered**: OpenGL ES / legacy compatibility profile (rejected: incompatible with `#version 150` on modern desktop GL drivers).
- **Reversibility**: Easy.

---

### DEC-022: Event-Time ImGui Capture and Stuck-Key Prevention
- **Date**: 2026-11-01
- **Context**: If `wantCaptureKeyboard` or `wantCaptureMouse` is evaluated only during tick drain, keys pressed while typing in an ImGui text field could leak release events or stick down in the game layer.
- **Choice**: `ImGuiInputProcessor` evaluates capture at OS event time, tracking active game keys in `gameKeysDown[]` and buttons in `gameButtonsDown[]`. Any key down seen by the game is guaranteed to receive its matching up event, while keys consumed by ImGui never reach the game. When ImGui text fields have focus, `Ctrl+Z` is consumed by the text field and never reaches the editor undo stack.
- **Alternatives Considered**: Polling `WantCaptureKeyboard` at tick time (rejected: causes stuck keys when focus changes mid-tick).
- **Reversibility**: Easy.

---

### DEC-023: Atomic File Writer Protocol
- **Date**: 2026-11-01
- **Context**: Writing room or save files directly risks file corruption or truncation if interrupted.
- **Choice**: Implemented `AtomicFileWriter` in `data` writing to `<target>.tmp`, syncing with `FileChannel.force(true)`, preserving a `<target>.bak` backup of the existing target file, and atomically moving to `<target>` via `Files.move(ATOMIC_MOVE, REPLACE_EXISTING)`.
- **Alternatives Considered**: Direct write via `Files.writeString` (rejected: lacks backup and atomic replace guarantee).
- **Reversibility**: Easy.

---

### DEC-024: Editor Play Mode Snapshot and In-Memory Simulation
- **Date**: 2026-11-01
- **Context**: AGENTS.md Principle 4.2 / 4.10 requires in-game tools to preview with the real runtime simulation with zero hidden state and clean restoration on Stop.
- **Choice**: Editor Play mode takes a deep-copied `WorldSnapshot`, instantiates the player via `PlayerFactory`, simulates the real `MovementSystem` and `PlatformSystem` on the in-memory `RoomGrid`, suppresses room transitions (logging exit contacts only), and restores the `WorldSnapshot` on Stop bit-for-bit.
- **Alternatives Considered**: Running a separate editor-only simulation (rejected: explicitly forbidden by Principle 4.2).
- **Reversibility**: Easy.

---

### DEC-025: Component Schema Field Types (INT vs FLOAT)
- **Date**: 2026-11-01
- **Context**: Component schema fields require clear type semantics across data validation, runtime spawning, and editor inspectors.
- **Choice**: `INT` is strictly used for all discrete offsets, bounding box dimensions, pixel coordinates, tick durations, and integer counts. `FLOAT` is used exclusively for authored speeds (such as `px/s`) and unitless multipliers (such as `jumpCutMultiplier`), which are converted once at entity spawn time into fixed-point velocity increments per tick.
- **Alternatives Considered**: Using floats for all numeric fields (rejected: causes subpixel rounding bugs in discrete collision colliders and timers).
- **Reversibility**: Easy.

---

### DEC-026: Room Hot-Reload Entity Respawn Policy
- **Date**: 2026-11-01
- **Context**: When a room file changes on disk during active gameplay in Game mode, entity instances must be reconciled cleanly.
- **Choice**: Room-local entities (`ROOM_LOCAL`) are despawned and respawned from the newly loaded room data at the tick boundary. Moving platforms reset to their initial path state and start position. Carried entities (the player, `PERSISTENT_CARRIED`) keep their current position, and the stuck-body rule applies if the room geometry changed to intersect the player.
- **Alternatives Considered**: Retaining moving platform progress across room reloads (rejected: platform path length, speed, or endpoints may have been altered in data).
- **Reversibility**: Easy.

---

### DEC-027: Selection Picking and Tie-Breaking Order
- **Date**: 2026-11-01
- **Context**: Clicking in the editor viewport to select overlapping entity instances must be deterministic and predictable.
- **Choice**:
  1. For entities with a `ColliderComponent(w, h)`, the pick bounds are `[x + offsetX, y + offsetY, w, h]`, with pick area `w * h`.
  2. For entities without a collider, a fixed 16x16 pick box centered at `[x - 8, y - 8, 16, 16]` is used, with pick area `256`.
  3. When multiple entities overlap the clicked point, tie-breaking selects the entity with the smallest pick area first. If areas are equal, tie-breaking selects the lowest sequence number (e.g. `_1` before `_2`).
- **Alternatives Considered**: Last-drawn / z-order picking (rejected: violates determinism when list order changes).
- **Reversibility**: Easy.

---

### DEC-028: Saved Floor Tracking for Monotonic Instance IDs
- **Date**: 2026-11-01
- **Context**: AGENTS.md Section 7.7 requires that entity instance IDs are never reused within a room, even after deletion, saving, and reloading.
- **Choice**: `RoomDocument` maintains `savedFloor` (the highest numeric sequence suffix ever written to disk for that room). Undoing a placement command can decrement `entitySeq`, but `entitySeq` is strictly clamped so it never drops below `savedFloor`. Once an ID reaches disk, that sequence number is permanently reserved and will never be generated again.
- **Alternatives Considered**: Never decrementing `entitySeq` on undo (rejected: burns sequence numbers during rapid interactive undo/redo before saving).
- **Reversibility**: Easy.

---

### DEC-029: Stuck-Body Rule on Room Reload
- **Date**: 2026-11-01
- **Context**: If an external file reload introduces solid terrain directly overlapping the player's current body position during live gameplay.
- **Choice**: The existing stuck-body rule applies: horizontal and vertical movement is frozen/skipped, remainder (subpixelX, subpixelY) is left untouched, and a rate-limited warning is logged. No automatic push-out along shortest axis is performed.
- **Alternatives Considered**: Pushing outward along the shortest axis (rejected: introduces unverified movement behavior); clearing subpixel remainder (rejected: Architect approved skipping movement and logging only).
- **Reversibility**: Easy.

---

### DEC-030: Entity Persistence Policy (Answer to Q1)
- **Date**: 2026-11-01
- **Context**: Respawn and persistence policy for non-carried room entities across room visits.
- **Choice**: Default is recreation from room file on entry (`ROOM_LOCAL`). Persistent entities (`persistence: PERSISTENT`) write state into `RoomStateStore` under `instanceId` and restore or skip on load (e.g. defeated bosses). Checkpoint reload resets to checkpoint state. (Deferred to M7b).
- **Reversibility**: Easy.

---

### DEC-031: Transient Entity Allocation Policy (Answer to Q2)
- **Date**: 2026-11-01
- **Context**: Spawning projectiles, sparks, and effects.
- **Choice**: Shared `EntitySpawner` pipeline without per-instance string `InstanceIdComponent` allocation. Definitions cached once; entity slots preallocated. (Deferred to M10).
- **Reversibility**: Easy.

---

### DEC-032: Platform Motion Uses Relative Offsets (travelX, travelY)
- **Date**: 2026-11-01
- **Context**: Moving platforms stored absolute `startX, startY, endX, endY`. Start coordinates beside Body position represented two competing sources of truth, causing paths to be left behind if an entity was moved in the editor.
- **Choice**: Remove start coordinates (spawn/body position is start). Store relative offsets `travelX` and `travelY`. Migration v2->v3 converts legacy coordinates, and `RoomValidator` emits loud error `ROOM_ERR_LEGACY_PLATFORM_COORDINATES` for any leftover absolute coordinates.
- **Reversibility**: Easy.

---

### DEC-033: Scope Storage Architecture
- **Date**: 2026-11-02
- **Context**: Flags have four scopes (`GLOBAL`, `SESSION`, `ROOM`, `ROOM_TEMP`) with distinct lifetimes and persistence requirements.
- **Choice**:
  - `GLOBAL` and `SESSION` stored in flat primitive integer arrays indexed by dense flag registry index.
  - `ROOM` stored in `RoomStateStore` with precomputed ASCII keys (`flag.<id>`), naturally isolated per room and serialized with room records.
  - `ROOM_TEMP` stored in a flat primitive array, cleared silently on room swap (`SOURCE_ROOM_SWAP`) with zero event emission.
- **Alternatives Considered**: Storing all scopes in a single hash table (rejected: per-frame allocation and poor cache locality).
- **Reversibility**: Easy.

---

### DEC-034: Flat Condition Evaluator AST
- **Date**: 2026-11-02
- **Context**: Evaluating condition expressions in the simulation loop without per-frame heap allocation.
- **Choice**: Parse JSON condition trees into flat `CompiledCondition.Node[]` arrays with contiguous child indices. Evaluation executes via primitive JVM recursion on the call stack with zero object allocations over 100k+ iterations. Depth limit is strictly capped at 8 (`COND_ERR_DEPTH_EXCEEDED`).
- **Alternatives Considered**: Tree-of-objects AST with virtual `evaluate()` calls (rejected: heap churn during evaluation and traversal).
- **Reversibility**: Easy.

---

### DEC-035: Save Files Never Contain Settings
- **Date**: 2026-11-02
- **Context**: Checkpoint saves must store game world state, not player configuration (volume, bindings, display resolution).
- **Choice**: Save files are strictly isolated from user settings. Settings live in a separate config file created when real settings arrive.
- **Alternatives Considered**: Bundling settings in save data (rejected: breaks game state portability and clobbers player preferences on loading old saves).
- **Reversibility**: Easy.

---

### DEC-036: Unknown Flags Dropped on Save Load
- **Date**: 2026-11-02
- **Context**: When a save file contains flags not present in `flags.json` (e.g. content removed or renamed across versions).
- **Choice**: Unknown flags are dropped with a loud logged warning. Renames of flags across versions must go through an explicit migration.
- **Alternatives Considered**: Keeping unknown flags in memory (rejected: pollutes registry and causes invalid index lookups).
- **Reversibility**: Easy.

---

### DEC-037: Playtime vs Global Tick Counter on Checkpoint Load
- **Date**: 2026-11-02
- **Context**: Tracking playtime across checkpoint saves without breaking replay and recording determinism.
- **Choice**: `playtimeTicks` tracks elapsed simulation ticks in Game mode, is saved in the checkpoint payload, and is restored on load. The global engine tick counter (used by input recordings and replays) is monotonic and is NEVER reset by a checkpoint load.
- **Alternatives Considered**: Resetting global tick counter on load (rejected: breaks input recording replay streams spanning a load).
- **Reversibility**: Easy.

---

### DEC-038: Save Snapshot Timing and Determinism
- **Date**: 2026-11-02
- **Context**: Exactly what state a save captures during tick execution.
- **Choice**: The save trigger runs in Step 8 ("Triggers and Zones") immediately after flag drain. It captures the applied flag state at that instant. Any flag writes queued later in that tick (or during subsequent ticks while disk write is in flight) are not part of that save snapshot.
- **Alternatives Considered**: Saving at the end of the tick (rejected: introduces race conditions with deferred lifecycle actions).
- **Reversibility**: Easy.

---

### DEC-039: Corrupt Save Quarantine and Backup Safety
- **Date**: 2026-11-02
- **Context**: Handling corrupted main save files without overwriting a good backup or losing debug evidence.
- **Choice**: When a main save file is corrupt, the valid `.bak` is never overwritten. The corrupt file is renamed to `.corrupt`, `.corrupt.1`, `.corrupt.2`, etc., using unique collision-free names, and is never deleted. Note: unbounded growth of corrupt files is acknowledged as known debt.
- **Alternatives Considered**: Overwriting corrupt file or deleting it (rejected: destroys forensics and risks clobbering good backups).
- **Reversibility**: Easy.

---

### DEC-040: Asynchronous Save Request Queueing
- **Date**: 2026-11-02
- **Context**: If a save request arrives while a previous save write is in flight on the worker thread, refusing the save depends on disk speed, violating replay determinism.
- **Choice**: A save request arriving while a write is in flight is queued in `SaveManager` (latest request replaces pending). When the worker finishes writing, the pending save snapshot is immediately dispatched to the worker. Save completion reports at a defined tick boundary and alters zero simulation state.
- **Alternatives Considered**: Blocking the main thread until worker finishes (violates 60fps fixed loop); refusing the save (breaks determinism across storage speeds).
- **Reversibility**: Easy.

---

### DEC-041: Pre-Fade Checkpoint Load Validation
- **Date**: 2026-11-02
- **Context**: Loading a corrupt save or a save pointing to a non-existent room or deleted savepoint must not start a room transition that aborts midway.
- **Choice**: All validation (JSON envelope, checksum, room file existence on disk, and savepoint entity existence within target room) executes synchronously on worker thread before starting the 29-tick transition. If validation fails, an error is logged loudly, the transition is never initiated, and the game continues unaffected.
- **Alternatives Considered**: Validating during the black hold tick (rejected: player sees a jarring freeze or partial fade-out before an abort).
- **Reversibility**: Easy.

---

### DEC-042: Canonical Checksum via Recursive Key Ordering
- **Date**: 2026-11-02
- **Context**: Checkpoint payload checksum must be independent of key serialization order or formatting.
- **Choice**: A pure recursive algorithm (`SaveWriter.toCanonicalJson(JsonNode)`) sorts all object keys in lexicographical order at all nesting levels with zero whitespace or line breaks. The SHA-256 digest is computed strictly over this canonical UTF-8 representation.
- **Alternatives Considered**: Jackson `ORDER_MAP_ENTRIES_BY_KEYS` (rejected: only sorts root Java Maps, not parsed `JsonNode` object trees).
- **Reversibility**: Easy.

---

### DEC-043: Enum Flag Serialization by Name
- **Date**: 2026-11-02
- **Context**: Flag definitions of type `ENUM` must not be coupled to code ordinal order across versions.
- **Choice**: Enums are serialized and deserialized using their stable string names (`"NORMAL"`, `"HARD"`), never integer ordinals. Reordering values in `flags.json` or code leaves existing save payloads completely valid.
- **Alternatives Considered**: Saving enum ordinals (rejected: fragile and corrupts saves on adding/reordering enum variants).
- **Reversibility**: Easy.

