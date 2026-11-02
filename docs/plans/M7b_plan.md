# Milestone 7b Plan: Saves, Checkpoints, and Persistence

## 1. Executive Summary & Scope

Milestone 7b implements crash-safe checkpoint saves, savepoint entity interaction, atomic disk persistence with backup and corruption quarantine, load validation with deterministic restoration, and startup continuation.

### Scope Boundaries:
- **Saved**: Format version, game version, playtime in ticks, ISO timestamp (display only, excluded from state hash), current room ID, savepoint instance ID, global flags (by ID, name, typed value), and full `RoomStateStore` records.
- **Not Saved**: Session flags, room-temp flags, live non-carried entities, raw player position (restored from savepoint origin), transition queues, event queues.
- **Reserved Sections**: Named extension points (`player` for M10, `inventory` for M11, `seen_cutscenes` for M13). Unknown sections on load are preserved and written back unchanged with a loud warning.
- **Settings**: Completely separate from save data (`DEC-035`). Save files never contain settings.
- **Out of Scope**: Slot UI, death pipeline (M9), persistent entities (M10), stat/health curves (M10), inventory storage (M11), cutscenes (M13).

---

## 2. Closure Items from Milestone 7a Review

All closure items will be verified and reported at the very top of the M7b Milestone Report:

1. **Spec Contradiction on Pending-Change Drain**:
   - Confirm code in `FlagStore.java` already rejects out-of-range adds (`candidate < def.min() || candidate > def.max()`) rather than clamping. Update report description.
   - Add explicit unit tests in `FlagStoreTest`:
     - `add()` at upper bound and lower bound (write rejected, old value stays).
     - `toggle()` on non-BOOL flag (loud error, no-op).
     - `flag_set` on ENUM flag in `ConditionLoaderTest` (must throw `COND_ERR_FLAG_SET_ON_ENUM`).
     - Two writes in one tick (last-write-wins).
     - Read before tick drain sees old value.
     - Pending buffer overflow (256 changes capacity; 257th logged loudly, dropped).
     - Event cascade limit.
2. **Manual Observations Backlog & M7a Demo**:
   - Provide concrete manual observations for M7a demo:
     - `test_01` conditional platform (`test.door_open`).
     - `test_01` conditional exit (`test.stage`).
     - Flag debug panel live edits and change log strip.
     - Flag panel read-only in Editor non-Play mode; snapshot restore on Stop.
   - Paste exact validator outputs for broken `flags.json` and broken conditions.
   - Complete M6a/M6b backlog items with concrete observations (`observed:` or `could not test:`).
3. **Missing Report Template Sections**:
   - Include `Files changed`, `Performance notes` (including editor frame times), `Known issues`, and `How to run`.
4. **Roadmap Correction & AGENTS.md Section 10**:
   - Paste Section 10 of `AGENTS.md` verbatim at the top of the report. Confirm roadmap milestones: M10 Combat & Stats, M11 Inventory, M13 Cutscenes.
5. **Key Bindings Full Pastes & Validation**:
   - Paste full `content/actions.json` and `content/bindings.json` and report `BindingsValidationTest` pass output.
6. **Per-Class Test Delta Table**:
   - Generate automated delta table (class, before: 242, after: 275) via test parsing script.
   - Explain `EntitySpawnerTest` method count (4 methods; explain why M6b listed 5).
7. **Missing Tests**:
   - `CompositeGameSnapshot`: snapshot, run, change flags, restore, verify hash equality.
   - Replay hash determinism across flag change and room swap every tick.
   - Spawn condition: false skips entity creation while instance ID sequence is preserved.
   - Platform migration: legacy absolute coordinates -> relative `travelX`/`travelY` and `ROOM_ERR_LEGACY_PLATFORM_COORDINATES`.
   - Flag panel change log ring buffer wraparound.
8. **ConditionLoader Module Location**:
   - Confirm `ConditionLoader` is located in `data` (`com.makeasaying.data.condition.ConditionLoader`), adhering strictly to DEC-002.
9. **Room Flag Allocation**:
   - Measure `RoomStateStore` read and write allocations separately in `ConditionAllocationTest`.
10. **DEC-029 Stuck-Body Remainder**:
    - Revert "zeroes remainder" in code and update `docs/DECISIONS.md` to specify skipping movement and logging only.

---

## 3. Milestone 7b Architecture & Component Design

### 3.1 Savepoint Entity & Interaction
- **Component**: `SavepointComponent` in `engine/entity/components/SavepointComponent.java` (pure data POJO, schema-registered).
- **Entity Definition**: `content/entities/savepoint.json` (trigger box: 16x24, color `#00FFAA`, default `room_scope: ROOM_LOCAL`).
- **Placement**: Place savepoint instances in `content/rooms/test_01.json` (`savepoint_1`) and `content/rooms/test_02.json` (`savepoint_2`).
- **Interaction System (`SavepointSystem`)**:
  - Runs in Step 8 ("Triggers and Zones") immediately after flag drain.
  - Checks overlap between player collider and savepoint trigger collider.
  - Checks if `interact` action is `pressed` this tick.
  - If triggered:
    - Consumes the `interact` press so no other system sees it.
    - If in Editor Play mode: logs rate-limited warning `"Saving is disabled in Editor Play mode"` and does not save.
    - If a room transition, load, or save is already in flight: refuses save and logs warning.
    - Otherwise: initiates save request to `SaveManager`.
- **Validation**: `RoomValidator` checks that savepoint entities are not embedded inside solid geometry, and reports `ROOM_INFO_NO_SAVEPOINT` for rooms lacking any savepoints.

### 3.2 Save Format & Serialization (`engine/save` and `data/save`)
- **Engine Data Models (`engine/save`)**:
  - `SaveEnvelope`: `version`, `gameVersion`, `checksum`, `data` (`SavePayload`).
  - `SavePayload`:
    - `playtimeTicks` (long)
    - `timestamp` (ISO-8601 string, ignored in game state hash)
    - `currentRoomId` (String)
    - `savepointInstanceId` (String)
    - `globalFlags` (Map<String, Object> or List of typed entries)
    - `roomRecords` (Map<String, Map<String, Object>> from `RoomStateStore`)
    - `reservedSections` (Map<String, JsonNode> for preserving unknown future sections)
- **Checksum Calculation**:
  - SHA-256 computed over the canonical UTF-8 bytes of the `data` payload.
- **Data Serialization & Validation (`data/save`)**:
  - `SaveWriter`: serializes `SavePayload`, computes checksum, wraps in `SaveEnvelope`, produces stable JSON.
  - `SaveLoader`:
    - Validates envelope schema, checks version.
    - Computes and verifies SHA-256 checksum. Rejects on tamper.
    - Rejects unknown newer versions.
    - Validates global flags against `FlagRegistry`:
      - Unknown flag: dropped with loud warning (`DEC-036`).
      - Wrong type / out of range: reset to registry default with loud warning.
      - Missing flag: reset to registry default.
    - Missing room or savepoint: load aborted, current game state kept, loud error logged.
    - Preserves unknown JSON sections in `reservedSections` map.
- **Migration Pipeline (`SaveMigrationChain`)**:
  - Supports version migrations (tested with synthetic v1 -> v2 migration fixture).
- **Test Fixtures**:
  - `content/test_saves/fresh_save.json`: pristine checkpoint save.
  - `content/test_saves/mid_progress_save.json`: checkpoint save with multiple global flags set and visited room state records.

### 3.3 Safe Disk I/O & Atomic Storage Pipeline
- **Storage Abstraction (`SaveLocation`)**:
  - `SaveLocation` interface: `readMain()`, `readBak()`, `writeTemp()`, `atomicMove()`, `copyToBak()`, `quarantineCorrupt()`.
  - `PerUserSaveLocation`: Resolves per-user OS data directory (`%APPDATA%/MakeASaying/saves` on Windows, `~/.local/share/makeasaying/saves` on Linux).
  - `MemorySaveLocation`: In-memory implementation for determinism and unit tests.
- **Safe Write Protocol**:
  1. Main thread captures snapshot of `SavePayload`, serializes JSON string, and computes checksum synchronously (zero allocation in hot sim).
  2. Dispatches disk I/O to background worker via `Jobs.submit()`. Only one save may be in flight; concurrent requests are rejected with a log.
  3. Worker executes backup quarantine check:
     - If main file exists: validates its checksum and envelope.
     - If existing main is valid: copies to `.bak`.
     - If existing main is corrupt: keeps existing `.bak` intact and moves corrupt file to `<slot>.corrupt`.
  4. Worker writes new payload to `<slot>.tmp`, forces sync (`FileChannel.force(true)`), and atomically renames to `<slot>.json`.
  5. Main thread receives completion callback via `Jobs.drain()` and updates save status.
- **Load Fallback Protocol**:
  1. Attempt to load `<slot>.json`.
  2. If missing or invalid/corrupt: quarantine corrupt file to `.corrupt`, log loud warning, attempt to load `<slot>.bak`.
  3. If `.bak` also missing or corrupt: log loud error, start fresh new game. Never delete `.corrupt`.
- **Crash Simulation**:
  - Tests simulating failure after temp write, before atomic rename, and mid-write truncation, verifying previous valid save or backup is always preserved.

### 3.4 Checkpoint Load Game Flow
- **Load Transition Sequence**:
  - Reuses transition timing: 12 ticks fade out, 4 ticks hold, 1 tick swap, 12 ticks fade in (29 ticks total).
  - Swap tick operations:
    1. Despawn all existing entities (including player).
    2. Replace `RoomStateStore` with loaded state records.
    3. Apply loaded global flags to `FlagStore`.
    4. Reset session and room-temp flags to defaults.
    5. Load target room (`currentRoomId`).
    6. Spawn room entities, evaluating `spawnCondition` against newly loaded flags.
    7. Find savepoint entity matching `savepointInstanceId`, spawn player at savepoint's position.
    8. Reset camera and interpolation state.
    9. Clear event queue and pending flag mutations.
- **Triggering Checkpoint Load**:
  - `debug_load_checkpoint` action added to `content/actions.json` and mapped to `KEY_F4` in `content/bindings.json` (gameplay context).
  - Start of game config: `continueOnStart` (boolean, default false in `engine.json`). If true and save exists, loads checkpoint on launch.

---

## 4. Test Strategy

1. **Unit & Logic Tests**:
   - `SaveWriterTest`: Stable JSON key order, SHA-256 calculation, envelope structure.
   - `SaveLoaderTest`: Checksum validation, tamper rejection, load validation cases (unknown flag dropped, invalid value defaulted, missing room aborted, unknown sections preserved).
   - `SaveMigrationChainTest`: Synthetic v1 -> v2 schema migration.
   - `TestSavesFixtureTest`: Ensures both `fresh_save.json` and `mid_progress_save.json` load cleanly.
   - `SavepointInteractionTest`: Player trigger overlap, button press consumed, transition/in-flight lockout, editor play disable.
2. **Safe Write & Crash Simulation Tests**:
   - `SafeSaveWriterTest`: Corrupt main quarantine to `.corrupt`, `.bak` preservation, fallback load from `.bak`, new game fallback.
   - Crash test after temp write, crash test before rename, crash test during mid-write truncation.
3. **Determinism & Round-Trip Tests**:
   - `CheckpointRoundTripTest`: Save game state -> mutate flags/rooms -> load checkpoint -> assert bit-for-bit state hash equality of saved subset.
   - `CheckpointReplayDeterminismTest`: Replay run containing savepoint save and checkpoint load; verify hash parity across all ticks.
4. **Allocation Tests**:
   - `SavepointAllocationTest`: Measure ordinary simulation ticks with savepoint present; assert strictly 0 bytes allocated across two 100k-tick windows after warm-up.

---

## 5. Deliverables & Documentation Updates

- **Code**:
  - `engine`: `SavepointComponent`, `SaveLocation`, `PerUserSaveLocation`, `SaveData`, `SaveManager`, `SavepointSystem`.
  - `data`: `SaveLoader`, `SaveWriter`, `SaveValidator`, `SaveMigrationChain`.
  - `game`: Wiring for `continueOnStart` and `debug_load_checkpoint`.
  - `editor-core` / `editor-map`: Savepoint debug rendering, `ROOM_INFO_NO_SAVEPOINT` validator finding.
- **Content**:
  - `content/entities/savepoint.json`.
  - Savepoints placed in `content/rooms/test_01.json` and `test_02.json`.
  - `content/test_saves/fresh_save.json` and `mid_progress_save.json`.
  - Updated `content/actions.json` and `content/bindings.json` (`interact`, `debug_load_checkpoint`).
- **Docs**:
  - `FORMATS.md`: Checkpoint save format schema, `savepoint` entity definition, `saveSlot`, `continueOnStart`.
  - `UPDATE_ORDER.md`: Document Step 8 trigger and zone order (flag drain -> savepoint interaction -> triggers/zones).
  - `docs/DECISIONS.md`: `DEC-033` (Flag storage architecture), `DEC-034` (Flat condition evaluator), `DEC-035` (Settings never in save files), `DEC-036` (Unknown flags dropped on load with warning), clarification for `DEC-029` (stuck-body remainder).
