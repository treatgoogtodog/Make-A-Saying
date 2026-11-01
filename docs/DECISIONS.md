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
