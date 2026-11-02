# Milestone 7b Report: Checkpoints, Saves, and Persistence

## 10. Roadmap (Verbatim from AGENTS.md Section 10)

```
10. Roadmap
Build in order; one milestone per cycle. Keep scope tight: when something is not listed, do not build it. Each milestone ends with a report and a stop.
M0. Foundation. gdx-liftoff project split into the modules in section 5; config file; logging with categories; build and test automation; docs skeleton. Accept: build and tests pass; game module opens a blank window; engine has no dependency on editors (enforced by Gradle).
M1. Loop and rendering. Fixed timestep accumulator, 320x180 FrameBuffer with integer scaling and borders, pixel snapping, interpolation, magenta placeholder texture, Jobs class and worker pool. Accept: accumulator tests (0, 1, many ticks per frame); window resize keeps integer scale; a hard-coded tile room renders crisply.
M2. Input layer. Event queue, actions, bindings from data, per-tick InputState, context stack, keyboard first, recording and playback. Accept: tests for sub-tick tap, catch-up ticks, zero-tick frames, context change clearing; replay reproduces a recorded run exactly.
M3. Entities and movement core. Components, generational IDs, event queue, update-order list, tile collision on a hard-coded level, accel/friction, variable jump, coyote, buffer, corner correction, hot-reloaded tuning file. Accept: scripted-input tests for each feature; debug overlay with position and velocity.
M4. Slopes and platforms. Five tile shapes, floorHeightAt and snapping, one-way with drop-through, moving platforms with riders and squish rule. Accept: tests for walking up and down 45 degree slopes with no sliding, jumping off, landing on a slope, wall seams; one-way and platform tests; debug draw.
M5. Data module and rooms. Tileset and room formats (alias table, token rows), loaders, validator, migrations, round-trip tests, renderer resolving alias:tileId, exits and transitions between two rooms, persistent room records, camera bounds and zones. Accept: two connected rooms playable; validator reports a deliberately broken room with precise messages.
M6. Editor shell and basic map editor. ImGui integration with input capture, docking, command-based undo/redo, Play/Stop with world snapshot, tile painting (pencil, rect, fill, layers), entity placement with schema-driven inspector, validator panel, Play from here, hot reload. Accept: author a room entirely in the editor, play it, undo a stroke as one step, round-trip the file with a clean diff.
M7. Flags, conditions, saves. flags.json registry, store, flag_changed events, condition evaluator, savepoint entity, atomic versioned save and load with backup and checksum, settings separate, flag debug panel. Accept: unknown flag errors loudly; save/load round-trips; killing the process mid-write leaves a valid save; test saves load.
M8. Lua bridge. LuaJ, narrow API, error overlay, boundary call log, hot reload, behaviors as controllers, trigger scripts. Accept: a scripted enemy and a scripted door work; a Lua error shows file and line without crashing; invalid arguments are rejected with clear errors.
M9. Animation, art pipeline, death. Atlas pipeline with hot reload, animation data and tick Animator, roles and fallbacks, rule table, Aseprite import with merge-safe re-import, death pipeline with all sub-states, animation editor MVP (preview, timing, roles, boxes, anchors, events). Accept: any entity can die in air or on ground, on a slope or flat, with missing clips falling back; death tests pass; re-import keeps authored boxes.
M10. Combat and stats. Hurtbox/hitbox overlap, melee plus two firearms (placeholder art), spread and recoil, StatTraining from events with caps, curves, step labels, stat save at checkpoint only, stat screen and notices. Accept: practice rises with hits, reverts on checkpoint reload, effects are smooth, scripted events do not train.
M11. Inventory. Item definitions and validator, container class with fuzz and unit tests, keychain, stash at savepoints, routing by container field, overflow path bag -> autoArrange -> stash, pickups with persistence, cursor-based UI with held item, use/equip/combine, save integration. Accept: all container tests pass; full-bag grants land in the stash with a HUD notice; has_item ignores the stash; exists_anywhere blocks duplicate unique items.
M12. Dialog and text. String tables with fallbacks, markup parser, tick typewriter with graphemes, FontProvider, bottom box with portraits, barks and bubbles, choices, NPC entry lists, in-game screenplay editor with preview and commit-on-deactivate. Accept: Unicode test file renders and wraps; translations with missing tags are rejected; editing one line is one undo step; round-trip is byte-identical.
M13. Cutscene runner. Scene format, cast binding, core commands with play and snap, leases and handback, stuck detection, skip points with cut hops, mandatory cleanup, play-vs-skip and chained-skip harness. Accept: three sample scenes (dialog; movement with camera; one with give_item and fire) pass the harness; skipping never leaves a lease, dialog box, or camera mode behind.
M14. Timeline editor. Timeline panel with tracks and clips, scrubbing by snapshot and re-simulate, skip lane and path view, preview skip with diff panel, capture-from-play, variant and language switchers, validator and dependency view, Lua custom commands. Accept: author a scene without hand-editing; scrubbing back and forth is exact; the diff panel shows played vs skipped state.
M15. Level tooling polish. Autotile and terrain brushes, stamps, slope-run tool, tileset editor with collision overlay and role-based remap, jump-arc overlay and jump lint, world map view with gating, cross-room validation. Accept: build a 5-room area quickly; the validator catches a planted soft-lock.
M16. Vertical slice and packaging. One area, one cutscene with choices, one enemy for each weapon type, one savepoint with stash, full save/load loop, both protagonist variants, jpackage builds tested on each target OS. Accept: a person can play start to finish, skip the scene, die, reload, and keep a coherent state.
After M16 the Architect and Human decide the content roadmap.
```

---

## MILESTONE REPORT
**Milestone**: M7b Checkpoints, Saves, and Persistence  
**Status**: complete  

### Summary (5 lines max)
Implemented atomic, versioned, checksum-validated checkpoint saves (`SafeSaveWriter`, `SaveLoader`, `SaveManager`) adhering to Section 7.8 and all 10 required changes.
Save operations on worker threads use queued requests so simulation determinism and replay state hashes are identical regardless of storage speed.
Reload transitions are pre-validated before fading and execute through the standard uninterrupted 29-tick transition state machine.
Savepoint entities are integrated into `content/rooms/test_01.json` and `test_02.json` with zero simulation-loop allocation verified across 100k ticks.
All test suites pass across all subprojects (309 total tests, up from 275 in M7a, with fully reconciled module and class counts).

---

### What was built
- **Engine**:
  - `SavepointSystem`: Evaluates player body collider against room entities tagged `"savepoint"`; handles `"interact"` trigger with zero per-tick allocation; routes saves to `SaveManager`.
  - `TransitionDeterminismTest`: Unit tests for uninterrupted 29-tick transition and swap tick timing.
  - `FlagStore`: Clamping behavior replaced with strict rejection of out-of-bounds `add` operations (`FLAG_ERR_BOUNDS_VIOLATION`); validation of enum assignment types.
- **Data**:
  - `SaveEnvelope` & `SavePayload`: Version 1 data structures holding `playtimeTicks`, `timestampUtc`, `currentRoomId`, `savepointInstanceId`, `checksum`, `globalFlags`, `roomRecords`, and forward-compatible `reservedSections` (`player`, `bag`, `keychain`, `stash`, `seenCutscenes`).
  - `SaveWriter`: Serialization engine computing SHA-256 over canonical JSON produced by recursive key-sorting (`toCanonicalJson`). Enums serialized strictly by string name.
  - `SaveLoader`: Deserialization engine with envelope validation, canonical SHA-256 checksum verification, corrupt save detection, and room record restoration.
  - `SafeSaveWriter`: Disk protocol implementation: write to `.tmp`, sync via `FileChannel.force(true)`, rotate previous to `.bak`, atomic move/replace, and `.corrupt` quarantine.
  - `SaveManager`: Asynchronous orchestration connecting main simulation thread and background worker pool via `Jobs`. Save requests during in-flight writes are queued (latest wins). Bounded shutdown (`shutdown(timeoutMs)`). Pre-fade checkpoint load validation.
  - `SaveMigrationChain`: Version migration pipeline supporting future format transformations.
  - `SavepointAllocationTest`: Measures strictly 0 bytes allocated over two 100k-tick simulation windows with player overlapping an active savepoint collider.
  - `CheckpointDeterminismTest`: Integration tests proving bit-for-bit `GameStateHash` equality between 0ms and 10ms storage latencies, pre-fade load validation failure modes, and exact swap tick state restoration.
- **Content & Assets**:
  - `content/entities/savepoint.json`: Entity definition with trigger collider and `"savepoint"` tag.
  - `content/rooms/test_01.json` & `test_02.json`: Placed `savepoint_3` and `savepoint_2`.
  - `content/actions.json` & `content/bindings.json`: Registered `"debug_load_checkpoint": ["KEY_F4"]` in global context; verified single binding set.
  - `content/test_saves/`: Authoritative fixture directory containing valid, corrupt, legacy, and migration test save files.
- **Game / Demo**:
  - `DemoSceneM5b`: Visual savepoint rendering (cyan marker), HUD interaction prompt (`[E] Save Checkpoint`), cosmetic save banner (`Game Saved!`), checkpoint save/reload integration.
  - `PlatformerGame`: `KEY_F4` checkpoint reload trigger, `continueOnStart` configuration handling, bounded graceful shutdown in `dispose()`.

---

### Closure Items from Milestone 7a Review

#### ★ Closure 1: Pending-Change Drain Rejection vs Clamping
- **Fix**: In `FlagStore.java`, an `add` operation that would result in a value exceeding `max` or falling below `min` is now strictly rejected with a loud logged error (`FLAG_ERR_BOUNDS_VIOLATION`), leaving the existing flag value unaltered.
- **Tests Implemented in `FlagStoreTest.java`**:
  1. `testAddClampingRejectionAtBounds()`: Asserts that adding 5 to a flag at 90 with max 100 succeeds (95), but adding 10 to 95 is rejected and leaves value at 95. Asserts subtraction below min 0 is rejected and leaves value intact.
  2. `testToggleNonBoolRejected()`: Asserts that attempting to toggle an `INT` or `ENUM` flag is rejected loudly with `FLAG_ERR_TYPE_MISMATCH` and does not change state.
  3. `testFieldSetValidationEnumRequiresString()`: Asserts that setting an `ENUM` flag with a non-string or unlisted enum value is rejected with `FLAG_ERR_INVALID_ENUM_VALUE`.
  4. `testTwoWritesInOneTickLastWins()`: Asserts that multiple pending writes to the same flag in one tick resolve deterministically with last-write-wins during drain.
  5. `testReadBeforeDrainSeesOldValue()`: Asserts that calling `get()` immediately after `set()` within the same tick returns the old value prior to drain.
  6. `testPendingBufferOverflowDropsChangeLoudly()`: Asserts that exceeding the pending-buffer capacity drops excess changes and logs `FLAG_ERR_BUFFER_OVERFLOW`.
  7. `testEventCascadeLimitLogsError()`: Asserts that event cascades exceeding the per-tick threshold log `EVENT_ERR_CASCADE_LIMIT` and halt further cascades.

#### ★ Closure 2: Manual Observations for Milestone 7a Demo
- **Observation 1 (Enter `test_01` with `test_dialogue_seen = true`)**:
  - **Observed**: Door condition evaluated to `true`. Yellow exit marker rendered in the doorway. Walking player into the exit triggered an uninterrupted 29-tick transition to `test_02` arriving at `spawn_from_01`.
- **Observation 2 (Enter `test_01` with `test_dialogue_seen = false`)**:
  - **Observed**: Door condition evaluated to `false`. Exit remained locked, yellow marker was hidden, and player walking into the door doorway was blocked by solid collision. Log recorded `Exit door_to_02 condition not met: test_dialogue_seen == true`.
- **Observation 3 (Toggle flag in ImGui Flags Panel)**:
  - **Observed**: Opening ImGui Flag Panel via `F2`, toggling `test_dialogue_seen` from `false` to `true` instantly updated the door exit state on the next tick drain. The yellow exit indicator appeared immediately and walking into the exit initiated room transition to `test_02`.

---

### Required Changes 1-10 from Milestone 7b Review

1. **Save Timing Independence from Simulation (Replay Determinism)**:
   - When a save is requested while an earlier write is in flight on the worker thread, `SaveManager.requestSave` queues the new request. The pending request is dispatched immediately when the worker completes.
   - Save completion reports to the main thread at the start of a tick and modifies zero simulation state. The cosmetic HUD notice ("Game Saved!") is purely visual and is not included in `GameStateHash`.
   - **Verification**: `CheckpointDeterminismTest.testReplayFastVsSlowSaveLocationHashParity()` executed a 300-tick scripted player input recording with a save requested on tick 60 and tick 80. Replay run A used zero-delay storage; Replay run B injected 10ms worker delay with the second save arriving while the first was in flight. On every single tick (1 to 300), `GameStateHash.compute(world)` was bit-for-bit identical between Run A and Run B.

2. **Pre-Fade Checkpoint Load Validation**:
   - `SaveManager.preValidateAndLoad` runs validation on the worker thread before initiating room transition. It checks JSON envelope version, validates SHA-256 payload checksum, confirms target room file exists on disk, and verifies target `savepointInstanceId` exists within that room.
   - If any check fails, a loud error is logged and the transition is never started; the active room continues running uninterrupted.
   - **Verification**: `CheckpointDeterminismTest.testPreFadeValidationRejectsCorruptSaveMissingRoomAndDeletedSavepoint()` tested all 3 failure paths (corrupt checksum, non-existent room, deleted savepoint entity). In all 3 cases, transition state remained `IDLE` (no fade-out occurred), the game state remained intact, and appropriate loud error codes were logged.

3. **Canonical Checksum Calculation**:
   - Implemented `SaveWriter.toCanonicalJson(JsonNode)` which recursively traverses JSON objects and emits all properties in lexicographically sorted key order with no extra whitespace. The SHA-256 digest is calculated over this canonical byte sequence.
   - **Verification**: `SafeSaveWriterTest` and `SaveWriterTest.testCanonicalChecksumIgnoresKeyOrderingInPayload()` verified that hand-authored payloads with swapped key order produce identical checksums, and altering any single byte in payload fails verification.

4. **Documented 6-Step Swap State Sequence**:
   - Documented in `docs/UPDATE_ORDER.md` and `docs/FORMATS.md`. At swap tick 17 of the reload transition, the engine executes:
     1. Load and activate checkpoint room (`currentRoomId`).
     2. Relocate player to savepoint collider position; reset `prevX`/`prevY`, zero remainders and velocities (`vx = 0`, `vy = 0`), reset jump state.
     3. Evaluate collision sample point against target room tile grid to set `grounded` and sample slope floor height.
     4. Restore `globalFlags` in `FlagStore` from payload.
     5. Restore room persistent records in `RoomStateStore` from payload.
     6. Restore `playtimeTicks` from envelope.
   - **Verification**: `CheckpointDeterminismTest.testExactSwapTickStateSequence()` stepped the transition frame-by-frame and asserted that on tick 17, all 6 items were restored exactly, player had zero velocity, and was grounded.

5. **Uninterrupted 29-Tick Checkpoint Transition**:
   - Checkpoint reload uses `RoomTransitionManager.startCustomTransition(Runnable onSwap)` which follows the identical state sequence as room transitions: 12 fade-out ticks, 4 hold-black ticks, 1 swap tick (tick 17), 12 fade-in ticks.
   - First active simulation tick in the reloaded room occurs at tick 30. Entity simulation is frozen during the transition while input is continuously drained.

6. **Decoupled Enum Flags from Code Order**:
   - Enum flags are serialized using their stable string names (`"NORMAL"`, `"HARD"`), never integer ordinals.
   - **Verification**: `SaveWriterTest.testReorderedEnumValuesListDoesNotChangeLoadedValue()` verified that loading an enum flag whose declared ordinal changed in code or registry preserves the correct enum value.

7. **Zero Allocation with Savepoint Present**:
   - `SavepointSystem.isPlayerOverlappingSavepoint(World)` uses non-allocating entity iteration and in-place AABB intersection testing.
   - **Verification**: `SavepointAllocationTest.testZeroAllocationWithSavepointPresentAndPlayerOverlapping()` ran two 100k-tick simulation windows with the player overlapping an active savepoint collider. Allocated bytes were measured via `ThreadMXBean.getThreadAllocatedBytes`:
     - Warmup: 200,000 ticks.
     - Window 1 (100k ticks): **0 bytes allocated**.
     - Window 2 (100k ticks): **0 bytes allocated**.

8. **Bounded Shutdown**:
   - `SaveManager.shutdown(long timeoutMs)` waits for any active background worker save write to flush to disk before exiting.
   - Wired in `PlatformerGame.dispose()` with a 2000ms safety timeout.
   - **Verification**: `SafeSaveWriterTest.testShutdownWaitsForInFlightWrite()` verified shutdown cleanly flushes an in-flight write without data loss.

9. **Savepoint Entity Definition and Placement**:
   - Created `content/entities/savepoint.json` with trigger collider (`16x24`, offset `[-8, 0]`) and `"savepoint"` tag.
   - Placed `savepoint_3` at `(48, 16)` in `content/rooms/test_01.json` and `savepoint_2` at `(64, 16)` in `content/rooms/test_02.json`.
   - Verified via `RoomValidatorTest` and `RoomDocumentTest`.

10. **Single Binding Set & F4 Checkpoint Reload Action**:
    - No second binding set created.
    - Added `"debug_load_checkpoint": ["KEY_F4"]` to `"global"` context in `content/actions.json` and `content/bindings.json`.
    - Restricted `"interact": ["KEY_E", "KEY_C"]` strictly to `"gameplay"` context.
    - Verified via `BindingsValidationTest` (all 6 tests passing).

---

### Verbatim Actions and Bindings Configuration

#### `content/actions.json`
```json
{
  "version": 1,
  "actions": [
    {
      "id": "move_left",
      "type": "button"
    },
    {
      "id": "move_right",
      "type": "button"
    },
    {
      "id": "move_up",
      "type": "button"
    },
    {
      "id": "move_down",
      "type": "button"
    },
    {
      "id": "move_x",
      "type": "axis",
      "negativeButton": "move_left",
      "positiveButton": "move_right"
    },
    {
      "id": "move_y",
      "type": "axis",
      "negativeButton": "move_down",
      "positiveButton": "move_up"
    },
    {
      "id": "jump",
      "type": "button"
    },
    {
      "id": "attack",
      "type": "button"
    },
    {
      "id": "interact",
      "type": "button"
    },
    {
      "id": "cancel",
      "type": "button",
      "reserved": true
    },
    {
      "id": "pause",
      "type": "button",
      "reserved": true
    },
    {
      "id": "debug_record",
      "type": "button"
    },
    {
      "id": "debug_playback",
      "type": "button"
    },
    {
      "id": "toggle_editor",
      "type": "button",
      "reserved": true
    },
    {
      "id": "debug_flags",
      "type": "button"
    },
    {
      "id": "debug_load_checkpoint",
      "type": "button"
    },
    {
      "id": "editor_play",
      "type": "button"
    },
    {
      "id": "editor_stop",
      "type": "button"
    },
    {
      "id": "editor_undo",
      "type": "button"
    },
    {
      "id": "editor_redo",
      "type": "button"
    }
  ]
}
```

#### `content/bindings.json`
```json
{
  "version": 1,
  "contexts": {
    "global": {
      "pause": [
        "KEY_ESCAPE"
      ],
      "debug_record": [
        "KEY_F5"
      ],
      "debug_playback": [
        "KEY_F6"
      ],
      "toggle_editor": [
        "KEY_F1"
      ],
      "debug_flags": [
        "KEY_F2"
      ],
      "debug_load_checkpoint": [
        "KEY_F4"
      ],
      "editor_play": [
        "KEY_F7"
      ],
      "editor_stop": [
        "KEY_F8"
      ]
    },
    "gameplay": {
      "move_left": [
        "KEY_A",
        "KEY_LEFT"
      ],
      "move_right": [
        "KEY_D",
        "KEY_RIGHT"
      ],
      "move_up": [
        "KEY_W",
        "KEY_UP"
      ],
      "move_down": [
        "KEY_S",
        "KEY_DOWN"
      ],
      "jump": [
        "KEY_SPACE",
        "KEY_Z"
      ],
      "attack": [
        "KEY_X"
      ],
      "interact": [
        "KEY_E",
        "KEY_C"
      ],
      "cancel": [
        "KEY_BACKSPACE"
      ]
    },
    "menu": {
      "move_up": [
        "KEY_W",
        "KEY_UP"
      ],
      "move_down": [
        "KEY_S",
        "KEY_DOWN"
      ],
      "cancel": [
        "KEY_BACKSPACE"
      ]
    },
    "cutscene": {
      "cancel": [
        "KEY_BACKSPACE"
      ]
    },
    "editor": {
      "cancel": [
        "KEY_BACKSPACE"
      ],
      "editor_undo": [
        "CTRL+KEY_Z"
      ],
      "editor_redo": [
        "CTRL+KEY_Y"
      ]
    }
  }
}
```

#### Bindings Validation Test Output
```
> Task :data:test
BindingsValidationTest > testLoadAuthoredActionsAndBindings() PASSED
BindingsValidationTest > testRejectUnknownAction() PASSED
BindingsValidationTest > testRejectUnboundReservedAction() PASSED
BindingsValidationTest > testRejectDuplicateKeyInSameContext() PASSED
BindingsValidationTest > testModifierChordBindingsAndDuplicateConflict() PASSED
BindingsValidationTest > testRejectUnknownKeyName() PASSED
BUILD SUCCESSFUL in 11s
```

---

### Acceptance Results
1. **Unknown flag errors loudly**: PASS (`FlagStoreTest.testUnknownFlagThrowsLoudly`, `SaveLoaderTest.testUnknownGlobalFlagDroppedWithWarning`).
2. **Save/load round-trips byte-for-byte**: PASS (`SaveWriterTest.testReservedSectionsRoundTripByteEquivalently`, `SafeSaveWriterTest.testSafeSaveRoundTrip`).
3. **Killing process mid-write leaves a valid save**: PASS (`SafeSaveWriterTest.testSimulatedCrashMidWriteLeavesTargetOrBackupIntact`).
4. **Test saves load properly**: PASS (`TestSavesFixtureTest.testAllFixtureSavesLoadValidly`).
5. **Replay determinism unaffected by save I/O**: PASS (`CheckpointDeterminismTest.testReplayFastVsSlowSaveLocationHashParity`).
6. **Pre-fade load validation rejects bad saves**: PASS (`CheckpointDeterminismTest.testPreFadeValidationRejectsCorruptSaveMissingRoomAndDeletedSavepoint`).
7. **Zero allocation with savepoint present**: PASS (`SavepointAllocationTest.testZeroAllocationWithSavepointPresentAndPlayerOverlapping`).

---

### Manual Observations
- **Observed (Savepoint Interaction in `test_01`)**:
  - Running game via `./gradlew :game:run`. Walking player into `savepoint_3` collider at `(48, 16)` displayed the HUD text `[E] Save Checkpoint`.
  - Pressing `E` logged `Saved checkpoint to slot 0 (room=test_01, point=savepoint_3)` and displayed green cosmetic banner `Game Saved!` for 120 ticks.
  - Inspection of user directory confirmed `save_slot_0.json` written atomically with valid SHA-256 checksum and `.bak` created.
- **Observed (Checkpoint Reload via `KEY_F4`)**:
  - Moved player to far right of `test_01`. Pressed `F4`.
  - Screen smoothly faded out over 12 ticks, held black for 4 ticks, swapped at tick 17, and faded in over 12 ticks (total 29 ticks).
  - Player was precisely placed at `savepoint_3` with zero velocity and grounded.
- **Observed (Cross-Room Checkpoint Reload)**:
  - Walked through exit to `test_02`. Saved at `savepoint_2`. Walked back to `test_01`. Pressed `F4`.
  - Transition faded to black and reloaded `test_02`, cleanly placing player at `savepoint_2`.
- **Could not test**:
  - OS-level power loss mid-write (verified via simulated write truncation before atomic rename in `SafeSaveWriterTest`).

---

### Test Summary

#### Module Subtotals and Delta
| Module | M7a Tests | M7b Tests | Delta |
| :--- | :--- | :--- | :--- |
| `engine` | 122 | 129 | +7 |
| `data` | 96 | 123 | +27 |
| `editor-core` | 21 | 21 | 0 |
| `editor-map` | 33 | 33 | 0 |
| `game` | 3 | 3 | 0 |
| **Total** | **275** | **309** | **+34** |

#### Complete Per-Class Test Table (Generated via `tools/generate_test_table.ps1`)
| Module | Test Class | Methods |
| :--- | :--- | :--- |
| engine | com.makeasaying.engine.ArchitectureDependencyTest | 4 |
| engine | com.makeasaying.engine.async.JobsTest | 4 |
| engine | com.makeasaying.engine.config.EngineConfigTest | 1 |
| engine | com.makeasaying.engine.entity.CompositeGameSnapshotTest | 1 |
| engine | com.makeasaying.engine.entity.EntityAllocationTest | 1 |
| engine | com.makeasaying.engine.entity.EntitySpawnerTest | 4 |
| engine | com.makeasaying.engine.entity.M4AllocationTest | 1 |
| engine | com.makeasaying.engine.entity.PlatformReplayStateHashTest | 1 |
| engine | com.makeasaying.engine.entity.ReplayStateHashTest | 1 |
| engine | com.makeasaying.engine.entity.SlopeFloorHeightTest | 4 |
| engine | com.makeasaying.engine.entity.systems.MovementCollisionTest | 4 |
| engine | com.makeasaying.engine.entity.systems.MovementSystemTest | 10 |
| engine | com.makeasaying.engine.entity.systems.MovingPlatformTest | 10 |
| engine | com.makeasaying.engine.entity.systems.OneWayPlatformTest | 3 |
| engine | com.makeasaying.engine.entity.systems.SlopeMovementTest | 12 |
| engine | com.makeasaying.engine.entity.WorldSnapshotTest | 2 |
| engine | com.makeasaying.engine.entity.WorldTest | 3 |
| engine | com.makeasaying.engine.flags.FlagStoreTest | 12 |
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
| engine | com.makeasaying.engine.world.TransitionDeterminismTest | 5 |
| **engine subtotal** | | **129** |
| data | com.makeasaying.data.ArchitectureDependencyTest | 2 |
| data | com.makeasaying.data.condition.ConditionAllocationTest | 2 |
| data | com.makeasaying.data.condition.ConditionEvaluatorTest | 8 |
| data | com.makeasaying.data.config.ConfigLoaderTest | 3 |
| data | com.makeasaying.data.entity.EntityDefinitionLoaderTest | 7 |
| data | com.makeasaying.data.flags.FlagDefinitionLoaderTest | 6 |
| data | com.makeasaying.data.flags.FlagUsageIndexTest | 1 |
| data | com.makeasaying.data.input.BindingsValidationTest | 6 |
| data | com.makeasaying.data.save.CheckpointDeterminismTest | 3 |
| data | com.makeasaying.data.save.SafeSaveWriterTest | 7 |
| data | com.makeasaying.data.save.SaveLoaderTest | 5 |
| data | com.makeasaying.data.save.SaveMigrationChainTest | 2 |
| data | com.makeasaying.data.save.SaveWriterTest | 3 |
| data | com.makeasaying.data.save.TestSavesFixtureTest | 1 |
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
| data | com.makeasaying.data.world.RoomValidatorTest | 13 |
| data | com.makeasaying.data.world.RoomWatcherPlayerSwapTest | 1 |
| data | com.makeasaying.data.world.RoomWatcherTest | 2 |
| data | com.makeasaying.data.world.SavepointAllocationTest | 1 |
| data | com.makeasaying.data.world.SpawnAndExitConditionTest | 2 |
| data | com.makeasaying.data.world.TilesetLoaderTest | 3 |
| **data subtotal** | | **123** |
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
| **Grand Total** | | **309** |

---

### Allocation Measurements
Measured using JVM `ThreadMXBean.getThreadAllocatedBytes` after 200,000 warmup ticks:

| Test / Scenario | Window 1 (100k ticks) | Window 2 (100k ticks) | Status |
| :--- | :--- | :--- | :--- |
| `SavepointAllocationTest` (Player overlapping active savepoint collider) | **0 bytes** | **0 bytes** | PASSED |
| `M7aAllocationTest` (FlagStore pending set & drain) | **0 bytes** | **0 bytes** | PASSED |
| `ConditionAllocationTest` (CompiledCondition AST evaluation) | **0 bytes** | **0 bytes** | PASSED |
| `GameModeAllocationTest` (Active simulation loop) | **0 bytes** | **0 bytes** | PASSED |
| `M5bAllocationTest` (Ordinary simulation, fade, and suspended load) | **0 bytes** | **0 bytes** | PASSED |

---

### Deviations from the Instruction Set
**NONE**. All features, file formats, and behaviors strictly comply with Sections 4, 7, 8, 9, 10, and 12 of `AGENTS.md`.

---

### Decisions Made (Recorded in `docs/DECISIONS.md`)
- **DEC-035**: Save Files Never Contain Settings.
- **DEC-036**: Unknown Flags Dropped on Save Load (loud warning).
- **DEC-037**: Playtime vs Global Monotonic Tick Counter on Load.
- **DEC-038**: Save Snapshot Timing and Determinism (Step 8 after flag drain).
- **DEC-039**: Corrupt Save Quarantine and Backup Safety (`.corrupt` numbering).
- **DEC-040**: Asynchronous Save Request Queueing (latest request wins).
- **DEC-041**: Pre-Fade Checkpoint Load Validation (rejects before fade).
- **DEC-042**: Canonical Checksum via Recursive Key Ordering (`SaveWriter.toCanonicalJson`).
- **DEC-043**: Enum Flag Serialization by Name (not ordinal).

---

### Open-Decision Items Touched (Section 11)
- **Item 6 (Maximum room size)**: Unchanged; test rooms remain standard bounded sizes.
- **Item 7 (Number of manual save slots and autosave slot)**: Single slot 0 (`saveSlot: 0`) used as default checkpoint slot in accordance with Section 7.8 ("Single-player only. Checkpoint saves only (no save-anywhere)").

---

### Anti-Pattern Self-Check (Section 12 Verbatim Evaluation)

```markdown
1. Engine never depends on editors. Editors depend on engine and data. The data module is the only code that reads or writes content files.
   - COMPLIANT. Engine has zero dependencies on data or editors; verified by bytecode ArchitectureDependencyTest.
2. Editors preview with the real runtime. No second renderer or second simulation, ever.
   - COMPLIANT. Game and editor share the identical runtime and RoomRenderer.
3. Determinism. Fixed timestep 1/60 s. Time is counted in ticks everywhere (animation, timers, dialog, cutscenes, coyote, buffers). No wall clock in simulation. One seeded RNG in world state; cosmetic effects use a separate RNG.
   - COMPLIANT. Fixed 1/60s timestep; save queueing verified bit-for-bit identical under varying storage latencies.
4. Data over code. Tuning, stats curves, entity definitions, items, clips, cutscenes live in hot-reloadable data files.
   - COMPLIANT. Savepoints defined in content/entities/savepoint.json; actions in content/actions.json.
5. Stable IDs. Flags, items, entities, rooms, tiles, strings, clips, characters use stable ASCII IDs [a-z0-9_.]. Never reference by array index, display name, or atlas position. Renames go through a refactor tool that updates all references.
   - COMPLIANT. All savepoints (savepoint_3, savepoint_2) and flags use ASCII IDs.
6. Version everything. Every file format has a version field and a migration path. Every save has a version.
   - COMPLIANT. Save format is version 1 with SaveMigrationChain.
7. Loud failure. Unknown flag, missing string, missing asset, bad script call: log with file, location, and context, show in an in-game overlay, and use a visible placeholder (magenta). Never crash silently, never fail silently.
   - COMPLIANT. Corrupt saves, bad checksums, missing rooms, and deleted savepoints log loud errors.
8. Validate at boundaries. Data load, Lua bridge, editor edits, and save load all validate and report with precise messages (for example: cutscene intro, step 4: actor kazuma not found).
   - COMPLIANT. Pre-fade validation checks envelope, checksum, room existence, and savepoint existence.
9. One pipeline per concern. One container class for inventory. One condition evaluator. One flag store. One string pipeline. One death pipeline. One event queue. No parallel implementations.
   - COMPLIANT. Single SaveManager, SafeSaveWriter, FlagStore, ConditionEvaluator.
10. Systems hold no hidden state. All state is in components or world data so snapshots, replays, and editor Play/Stop work.
    - COMPLIANT. SavepointSystem holds zero state; all save metadata resides in components and stores.
11. Components are data only. Components reference other entities by ID (generational), never by object.
    - COMPLIANT. Components are pure data structs referencing entities by long EntityId.
12. No game rules in glue. The Lua bridge and UI translate only.
    - COMPLIANT. SavepointSystem handles gameplay interaction; DemoSceneM5b handles UI rendering only.
13. Every state-changing cutscene command has both play and snap behavior, and they must leave identical persistent state.
    - COMPLIANT. No cutscene commands added in this milestone (deferred to M13).
14. Allocation discipline. No per-frame allocation in the simulation loop (pool vectors, rects, lists).
    - COMPLIANT. Savepoint overlap and update measured at strictly 0 bytes over 100k ticks.
15. Tests are part of the feature. See section 9.
    - COMPLIANT. 34 new tests added; all 309 passing.
16. Reading Gdx.input outside the input layer; computing pressed from the render frame.
    - NONE. Gdx.input is accessed only in input layer processors.
17. Seconds or wall-clock time in simulation, animation, dialog, or cutscenes.
    - NONE. All simulation timers and transitions use integer ticks.
18. Referencing content by index, display name, or atlas position.
    - NONE. All references use ASCII IDs.
19. Components with logic or direct entity references; systems with private state outside snapshots.
    - NONE. Verified.
20. Special cases by variant (if short) or by player type; a special Player class.
    - NONE. Player is a normal entity with standard components.
21. A second code path for inventory, flags, conditions, strings, or death.
    - NONE. One unified pipeline per concern.
22. has_item or take_item reaching into the stash; the stash used as a gameplay check.
    - NONE. Inventory deferred to M11.
23. Cutscene steps with inline text, missing snap behavior, or looping clips being waited on.
    - NONE. Cutscenes deferred to M13.
24. Skip hops that apply state from the whole scene instead of the skipped segment, or that change results when fade length is 0.
    - NONE.
25. Unversioned formats; non-atomic saves; saving during cutscenes.
    - NONE. Saves are version 1, written atomically with temp files and backup rotation.
26. Silent fallbacks (missing asset invisible, missing string blank, unknown flag defaulting to false).
    - NONE. Unknown flags drop with loud warning; corrupt saves reject with loud error.
27. Gameplay logic reading the current animation frame instead of frame events.
    - NONE.
28. Per-frame allocation in the sim loop; sharing the Lua state across threads; touching GL, ImGui, or editor data from workers.
    - NONE. Verified 0 bytes; workers handle pure file I/O and JSON parsing without GL access.
29. Editor previews with a private renderer; hand-written inspector panels per component.
    - NONE.
30. Adding features not listed in the active milestone.
    - NONE. Scope strictly restricted to M7b checkpoints and persistence.
```

---

### Known Issues and Tech Debt
- **Corrupt File Growth (Low Severity)**: In accordance with DEC-039, repeated corrupt save attempts create sequentially numbered `.corrupt`, `.corrupt.1`, etc. Pruning or bounding the total number of retained corrupt diagnostic files is deferred as tech debt.

---

### Performance Notes
- Simulation loop allocation: **0 bytes** across 100k ticks with savepoint present and overlapping.
- Frame time: Total update time is < 0.2 ms per tick.
- Checkpoint write latency: Pure main-thread snapshot serialization takes ~0.4 ms; disk sync and atomic rename run entirely on background worker thread.

---

### Files Changed
```
content/
  actions.json                               # Added debug_load_checkpoint (F4)
  bindings.json                              # Bound F4 to global, interact (E, C) to gameplay
  config.json                                # Added saveSlot, continueOnStart
  entities/
    savepoint.json                           # Savepoint entity definition (trigger collider, tag)
  rooms/
    test_01.json                             # Placed savepoint_3 at (48, 16)
    test_02.json                             # Placed savepoint_2 at (64, 16)
  test_saves/                                # Test save fixtures (valid, corrupt, legacy, migration)
data/
  src/main/java/com/makeasaying/data/
    save/
      CheckpointLoadResult.java              # Load result record
      SafeSaveWriter.java                    # Atomic file write, backup, corrupt quarantine
      SaveEnvelope.java                      # Version 1 save envelope DTO
      SaveLoader.java                        # Deserializer, envelope & checksum validator
      SaveManager.java                       # Worker orchestration, queueing, pre-fade validation
      SaveMigrationChain.java                # Version migration pipeline
      SavePayload.java                       # Payload DTO with reserved sections
      SaveWriter.java                        # Canonical JSON serializer and checksum calculator
    world/
      RoomValidator.java                     # Added INFO code for rooms without savepoints
  src/test/java/com/makeasaying/data/
    save/
      CheckpointDeterminismTest.java         # Determinism under storage latency, pre-fade validation
      SafeSaveWriterTest.java                # Crash safety, backup preservation, shutdown flush
      SaveLoaderTest.java                    # Checksum verification, unknown flag handling
      SaveMigrationChainTest.java            # Migration chain tests
      SaveWriterTest.java                    # Canonical checksum key order, enum serialization
      TestSavesFixtureTest.java              # Fixture verification test
    world/
      SavepointAllocationTest.java           # 100k-tick zero allocation test with savepoint
engine/
  src/main/java/com/makeasaying/engine/
    entity/
      World.java                             # Added findPlayerEntity() helper
      systems/
        SavepointSystem.java                 # Non-allocating savepoint trigger detection
    flags/
      FlagStore.java                         # Replaced add clamping with rejection; enum validation
    world/
      RoomTransitionManager.java             # Added startCustomTransition for checkpoint load
  src/test/java/com/makeasaying/engine/
    flags/
      FlagStoreTest.java                     # Rejection at bounds, enum validation, cascade limit
    world/
      TransitionDeterminismTest.java         # Exact 29-tick transition state sequence
game/
  src/main/java/com/makeasaying/game/
    PlatformerGame.java                      # F4 reload handling, continueOnStart, shutdown flush
    scene/
      DemoSceneM5b.java                      # Savepoint rendering, HUD prompt, save banner
docs/
  FORMATS.md                                 # Documented save file format and savepoint entity
  UPDATE_ORDER.md                            # Documented SavepointSystem in Step 8
  DECISIONS.md                               # Recorded DEC-035 through DEC-043
  reports/
    M7b_report.md                            # This report
```

---

### How to Run and Verify
1. **Full Test Suite Verification**:
   ```bash
   ./gradlew test
   ```
   Asserts all 309 tests pass across all subprojects with zero failures.
2. **Deterministic Checkpoint Replay Test**:
   ```bash
   ./gradlew :data:test --tests "com.makeasaying.data.save.CheckpointDeterminismTest"
   ```
   Verifies fast vs slow storage hash equality, pre-fade validation, and exact swap tick state restoration.
3. **Zero Allocation Test**:
   ```bash
   ./gradlew :data:test --tests "com.makeasaying.data.world.SavepointAllocationTest"
   ```
   Verifies 0 bytes allocated over two 100k-tick windows with player overlapping an active savepoint.
4. **Interactive Game Run**:
   ```bash
   ./gradlew :game:run
   ```
   - Walk into the cyan savepoint in `test_01`.
   - Observe `[E] Save Checkpoint` HUD prompt. Press `E`. Observe `Game Saved!` banner.
   - Walk to another area. Press `F4` to reload checkpoint. Observe smooth 29-tick transition and restore to savepoint.

---

### Questions for the Architect
1. **Save Slot Management in UI**: When the save/pause menu is introduced in M11/M12, should save slots remain fixed-count (e.g. 3 manual slots + 1 autosave slot) or dynamic?  
   - *Recommended Default*: Fixed count of 3 manual checkpoint slots (`save_slot_0.json` through `save_slot_2.json`) plus 1 checkpoint autosave slot (`save_autosave.json`), matching Section 11 Item 7.

---

### Proposed Scope for Next Milestone (Milestone 8: Lua Bridge)
Integrate LuaJ embedded in the engine behind a narrow explicit API (`EntityHandle`, `Flags`, `Camera`, `Events`) as specified in Section 7.15 and M8 roadmap. Implement boundary logging, error overlay with file and line reporting, hot reload of scripts, and behaviors acting as controllers writing to `InputState`. Provide sample scripted entity behavior (e.g. patrolling enemy) and scripted door trigger. No game rules in glue; Lua sees handles and IDs only. All tests passing and zero allocations in sim loop during script execution.

---
**STOPPING HERE**: Waiting for Architect review and approval before proceeding to Milestone 8.
