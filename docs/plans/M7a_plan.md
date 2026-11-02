# Milestone 7a Plan: Flags and Conditions

## Overview
Milestone 7a implements a unified, data-driven flag store and compiled condition evaluator. It provides data-driven gating for entity spawning and exit triggers, integrated with snapshots, replay hashing, zero-allocation evaluation, and an in-game debug panel.

---

## 1. Closure Pass Items (to be executed & verified first)
1. **Engine -> Data Dependency Resolution**:
   - Confirm and document in the report that `engine` has strictly zero dependency on `data`, backed by `engine/build.gradle` and `engine/src/test/java/com/makeasaying/engine/ArchitectureDependencyTest.java` (`engineMustNeverDependOnDataModule()`).
2. **`docs/SELF_REVIEW.md` Verification**:
   - Generated directly from `AGENTS.md` (lines 22-36 and 221-236) containing all 30 verbatim lines.
3. **Decisions Recorded in `docs/DECISIONS.md`**:
   - `DEC-029`: Room Hot-Reload Respawn Policy (player position preserved, room-local entities respawned, stuck-body resolution).
   - `DEC-030`: Persistence & Respawn Policy (answer to Q1: `persistence: NONE | PERSISTENT` stored in `RoomStateStore`, deferred to M7b).
   - `DEC-031`: Transient Entity Spawning Policy (answer to Q2: shared `EntitySpawner` without string `InstanceIdComponent`, deferred to M10).
4. **Replay Hash Multi-Room Loop Test**:
   - Add `TransitionReplayLoopTest` verifying bit-for-bit replay hash parity across a bidirectional room transition loop (`test_01 -> test_02 -> test_01`) with moving platforms present on every tick.

---

## 2. Planned M7a Implementation

### 2.1 Flag Registry & Content (`data` & `engine`)
- **File**: `content/flags.json` (Format v1).
  - Schema: `version: 1`, `flags: { "<id>": { "type": "bool|int|enum", "scope": "global|session|room|room_temp", "default": ..., "min": ..., "max": ..., "values": [...], "doc": "..." } }`.
  - Types: `BOOL`, `INT`, `ENUM`. (No free strings).
  - Scopes: `GLOBAL`, `SESSION`, `ROOM`, `ROOM_TEMP`.
- **Runtime Registry (`com.makeasaying.engine.flags.FlagRegistry`)**:
  - Compiles stable flag IDs to integer indices `[0..N-1]` at load time.
  - Stores metadata: `FlagType`, `FlagScope`, integer default value, min/max bounds, enum value string-to-int mapping.
- **Loader & Validator (`com.makeasaying.data.flags.FlagDefinitionLoader`)**:
  - Validates IDs (`^[a-z0-9_.]+$`), duplicate IDs, type/scope validity, default within min/max or enum value list.
  - Emits loud validation errors (`FLAG_ERR_*`).

### 2.2 FlagStore (`com.makeasaying.engine.flags.FlagStore`)
- **Single Pipeline Storage**:
  - `GLOBAL` and `SESSION`: Stored in flat primitive `int[]` arrays indexed by flag index.
  - `ROOM`: Stored in the existing `RoomStateStore` keyed by `(roomId, "flag." + flagId)`.
  - `ROOM_TEMP`: Stored in flat primitive `int[]` array; cleared during `RoomTransitionManager` swap tick.
- **Operations & Validation**:
  - `get(int index)`, `set(int index, int value)`, `add(int index, int delta)`, `toggle(int index)`.
  - Loud error logging on undeclared flag index, type mismatch, or value exceeding min/max bounds.
  - Changes queued and applied deterministically at tick boundary (triggers & zones phase).
  - Successful changes emit `flag_changed(flagIndex, oldValue, newValue)` through the event queue.
  - Cascade limit enforced with loud error.
- **Snapshots & Replay**:
  - Global, session, and room_temp flag arrays included in `WorldSnapshot` and `GameStateHash`.

### 2.3 Condition Evaluator & Atom Registry (`engine` & `data`)
- **Compiled Node Representation (`com.makeasaying.engine.condition.CompiledCondition`)**:
  - Compiled into flat primitive arrays (`int[] opcodes`, `int[] flagIndices`, `int[] values`, `int[] jumpOffsets`) avoiding object graphs or runtime allocations during evaluation.
  - Opcodes: `EQUAL`, `NOT_EQUAL`, `LESS_THAN`, `LESS_EQUAL`, `GREATER_THAN`, `GREATER_EQUAL`, `FLAG_SET`, `ALL`, `ANY`, `NOT`.
  - Empty condition evaluates to `true`; empty `ALL` evaluates to `true`; empty `ANY` evaluates to `false`.
  - Zero allocation during evaluation verified over 100,000 evaluations.
- **Atom Registry (`com.makeasaying.engine.condition.AtomRegistry`)**:
  - Extensible registry for condition atom handlers. M7a registers `flag` and `flag_set`.
  - Rejects unknown atom types (`COND_ERR_UNKNOWN_ATOM`).
- **Loader & Compiler (`com.makeasaying.data.condition.ConditionLoader`)**:
  - Parses JSON AST tree into flat `CompiledCondition`.
  - Validates unknown flags (`COND_ERR_UNKNOWN_FLAG`), type/value mismatches, unknown enum strings, and nesting depth exceeding limit (max 8).

### 2.4 Room Entity & Exit Integration
- **Spawn Conditions (`content/rooms/*.json`)**:
  - Optional `spawnCondition` object on entity instances in room format v3.
  - Evaluated once upon room load / entity spawning. If false, entity is not spawned into the world, but instance ID is reserved and definition remains validated.
- **Exit Conditions**:
  - Optional `condition` object on room exits.
  - Evaluated every tick the player overlaps the exit AABB. If false, exit trigger is blocked and logs once at `DEBUG` level.

### 2.5 Validation & Usage Index
- **`FlagUsageIndex` (`com.makeasaying.data.flags.FlagUsageIndex`)**:
  - Scans `content/flags.json`, `content/rooms/*.json`, and definitions.
  - Tracks all readers (spawn conditions, exit conditions) and writers (debug panel, future scripts).
  - Emits `FLAG_INFO_UNREAD` for declared flags never read, and `FLAG_INFO_UNWRITTEN` for flags read without writers.
  - Integrates into editor `RoomValidator` panel.

### 2.6 Debug Panel (`FlagPanelModel` + ImGui Window)
- **Pure Model (`com.makeasaying.editor.map.model.FlagPanelModel`)**:
  - Text search filter, type filter, scope filter.
  - Ring buffer of recent changes: `(tick, flagIndex, oldValue, newValue, sourceCode)` with preallocated fixed capacity (e.g., 256 entries).
- **ImGui View**:
  - Available in Map Editor and Game mode (toggleable via action `debug_overlay` / F1).
  - Interactive value modification (invoking `FlagStore.set()`, triggering events and change log).

### 2.7 Content & Verification Demo
- Authored flags in `content/flags.json`:
  - `test.door_open` (bool, room scope).
  - `test.stage` (int, global scope, min: 0, max: 10, default: 0).
  - `test.lever_state` (enum: ["off", "half", "on"], session scope).
  - `test.temp_barrier` (bool, room_temp scope).
- Add conditional entity in `test_01.json`: moving platform with `spawnCondition` requiring `test.door_open == true`.
- Add conditional exit in `test_01.json` requiring `test.stage >= 1`.
- Manual verification: enter `test_01`, toggle `test.door_open` via debug panel, reload/re-enter room to observe platform spawn.

---

## 3. Testing & Verification Plan
1. **Unit Tests**:
   - `FlagRegistryTest`: Validation rejections (duplicate IDs, invalid chars, bad bounds, unknown types).
   - `FlagStoreTest`: Get, set, add, toggle across all 4 scopes; `room_temp` cleared on swap; `room` stored in `RoomStateStore`; cascade limit; snapshot save/restore parity; state hash integration.
   - `ConditionEvaluatorTest`: All operators (`==`, `!=`, `<`, `<=`, `>`, `>=`), `flag_set`, combinators (`all`, `any`, `not`), empty conditions, depth limit rejection, atom registry extensibility.
   - `FlagUsageIndexTest`: Detection of unread and unwritten flags.
   - `RoomConditionIntegrationTest`: Entity `spawnCondition` true vs false; Exit `condition` true vs false.
   - `FlagPanelModelTest`: Text filtering, editing, ring buffer wraparound.
2. **Determinism & Performance Tests**:
   - `ConditionAllocationTest`: 100,000 compiled condition evaluations measuring strictly 0 bytes.
   - `M7aAllocationTest`: 100,000 ordinary simulation ticks with active flags, conditions, and moving platforms measuring strictly 0 bytes.
   - `TransitionReplayLoopTest`: Replay hash identical across multi-room transition loop (`test_01 -> test_02 -> test_01`).
