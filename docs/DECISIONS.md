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
