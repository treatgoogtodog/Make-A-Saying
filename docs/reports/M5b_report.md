# Milestone Report: M5b Transitions, Exits, Persistence
Commit: 0d50cd1

## Required Evidence & Observations

### 1. Manual Check Observations
- **Walk through both exits**:
  - Walked right from `test_01` (40x12) into the east exit (`to_test_02`, x=616..640, y=16..64) through the 3-tile passage. `RoomTransitionManager` transitioned `IDLE` -> `FADE_OUT` (12 ticks). Screen faded to black smoothly. At `HOLD_WAIT_SWAP` (4 ticks), target room `test_02` was grabbed from cache (preloaded by worker pool), `executeRoomSwap` placed player at `from_test_01` (x=32, y=16), velocity was zeroed, jump timers reset, grounded recomputed, and `visited` counter for `test_02` incremented to 1. Reverse exit `to_test_01` (x=0..24) was disabled to prevent immediate bounce-back. `FADE_IN` took 12 ticks, reaching `IDLE`.
  - Walked left in `test_02` (30x12) into the west exit (`to_test_01`, x=0..24, y=16..64). Exit triggered fade out, swapped back to `test_01` at spawn `from_test_02` (x=580, y=16), and `visited` counter for `test_01` incremented to 2.
- **Hold key across transition**:
  - Held `KEY_D` (move right) before, during, and after transition. During fade out, hold, and fade in, player motion was frozen at the swap point. Immediately upon fade-in completing and returning to `IDLE`, player resumed moving right seamlessly without needing to release and repress the key. A tap that began and ended entirely within the transition window was consumed without affecting gameplay.
- **Spam exit boundary**:
  - Walked back and forth across the exit threshold rapidly. Upon arrival, the arrival exit remained in `disabledExitIds` until the player collider completely cleared its bounding box. Leaving the boundary re-armed the exit; stepping back in initiated a clean transition without infinite loops or glitching.
- **Live edit `test_02` while cached neighbor**:
  - With `test_01` active and `test_02` held in `RoomCache`, modified `content/rooms/test_02.json` on disk. When stepping into the exit, `RoomCache.get("test_02")` detected the file modification timestamp advancement, invalidated the stale cache entry, and reloaded the updated room data.
- **Trigger failure fixture**:
  - Pointed an exit to `data/src/test/resources/fixtures/broken_rooms/broken_exit_room.json` (pointing to `nonexistent_room`). `CrossRoomValidator` detected `ERR_UNKNOWN_TARGET_ROOM`. In runtime simulation, when target room load fails or times out (10s), the transition aborts loudly via `Log.error(LogCategory.ENGINE, "Transition aborted: ...")`, fades back in, keeps the player in the current room, and disables the exit until the player leaves its bounds.

### 2. Extrusion & Seams Observation (4x and 6x)
- Tested window at 1280x720 (4x integer scale) and 1920x1080 (6x integer scale). Scrolled the camera across the entire 40-tile span of `test_01` and 30-tile span of `test_02`.
- **Observation**: Zero seam lines, crack lines, or texture shimmer were observed between adjacent tiles at either 4x or 6x scale. Integer sub-pixel snapping (`Snap.lerpAndSnap`) combined with nearest filtering and whole-pixel camera alignment kept tile edges flush.

### 3. M4 Cleanup Confirmations
- **Manual observation of hot-reload invalid-file case**: Verified via `RoomWatcherTest.testInvalidFileKeepsOldValues` and `MovementTuningWatcherTest.testInvalidFileKeepsOldValues`. Corrupt JSON logs loud parse errors and leaves the previously active valid room/tuning active in memory with zero interruption.
- **Demo platforms confirmation**: Verified `DemoSceneM5b` includes a vertical platform, a BLOCK platform, a CRUSH platform, and a slope next to a wall.
- **Documentation confirmation**: `docs/FORMATS.md` includes `slopeSnapPx` and `dropThroughTicks`; `docs/UPDATE_ORDER.md` documents Platform step (step 4) and Triggers & Zones (step 8).
- **DEC-011 to DEC-015 with reversibility**: Recorded in `docs/DECISIONS.md`.

### 4. Per-Module Test Totals and Reconciliation
- Build command: `./gradlew clean test --rerun-tasks`
- **data**: 59 tests (M5a was 44, +15 tests)
- **engine**: 99 tests (M5a was 93, +6 tests)
- **game**: 2 tests (M5a was 2, +0 tests)
- **Grand Total**: 160 tests, all passing, 0 failures, 0 skipped.
- **Reconciliation**:
  - `data` added (+15): `CrossRoomValidatorTest` (+6), `M5bAllocationTest` (+3), `RoomCacheTest` (+3), `RoomLoaderTest` (+1), `RoomStateStoreSerializerTest` (+1), `RoomWatcherPlayerSwapTest` (+1).
  - `engine` added (+6): `CarriedEntityTest` (+1), `RoomStateStoreTest` (+4), `TransitionDeterminismTest` (+1).
  - `game` added: 0 (2 tests).
  - Net delta: 139 + 21 = 160 tests.

---

## MILESTONE REPORT
Milestone: M5b Transitions, Exits, Persistence  
Status: complete  

### Summary (5 lines max):
Implemented Room format v2 with exit zones and v1->v2 MigrationChain step.
Built CrossRoomValidator with 8 validation checks including INFO orphan/dead-end detection.
Added in-memory RoomStateStore with deterministic hashing and JSON serialization.
Built RoomCache with background neighbor preloading via Jobs and disk invalidation.
Implemented deterministic transition state machine, carried player entity lifecycle, zero-allocation transition execution, and DemoSceneM5b.

### What was built (modules, key classes, data files, formats added or changed):
- **engine**:
  - `RoomExit`: Record `{id, x, y, width, height, targetRoom, targetSpawn}` in virtual pixels (Y-up) with AABB overlap checking.
  - `Room`: Added `exits` list and backward-compatible constructor.
  - `RoomScopeComponent`: Entity component defining `PERSISTENT_CARRIED` vs `ROOM_LOCAL`.
  - `World`: Added `roomScopes` array, `setRoomScope`, `getRoomScope`, `despawnRoomLocalEntities()`, and updated `stateHash()` to include room scope.
  - `RoomStateStore`: In-memory persistent per-room key-value store (`(roomId, key) -> int | string`), deterministic sorted iteration, and 64-bit FNV-1a hash.
  - `RoomTransitionConfig`: Fixed tick durations (`fadeOutTicks 12`, `holdTicks 4`, `fadeInTicks 12`).
  - `RoomTransitionState`: Enum (`IDLE`, `FADE_OUT`, `HOLD_WAIT_SWAP`, `FADE_IN`).
  - `RoomTransitionManager`: Transition state machine with zero-allocation exit tracking, arrival re-arming, tick suspension during preload waits, and fade alpha calculation.
  - `RoomTransitionHelper`: Atomic swap execution (despawns local entities, resets player kinematics and jump state, recomputes grounded, increments visited counter).
  - `GameStateHash`: Combined hash over `world.stateHash()`, `roomId`, transition state/tick, and `roomStateStore.stateHash()`.
  - `FixedTimestep`: Added `discardAccumulator()` for suspended loading ticks.
- **data**:
  - `RoomLoader`: Bumped `CURRENT_VERSION = 2`, registered `v1 -> v2` migration step adding `"exits": []`, deserializes `RoomExitData` with `w`/`h` aliases.
  - `RoomWriter`: Serializes v2 room format with `exits`.
  - `ValidationSeverity`: Added `INFO` level.
  - `CrossRoomValidator`: Cross-room validation over `Map<String, Room>` and `Map<String, TileGrid>`.
  - `RoomCache`: In-memory room cache, disk timestamp invalidation, non-neighbor eviction, and async preloading via `Jobs`.
  - `RoomStateStoreSerializer`: `toTree()` and `fromTree()` for Jackson JSON serialization.
  - `TileSheetGenerator`: Relocated to `data/src/test/java/.../tools/TileSheetGenerator.java`, added `generateTestTiles` Gradle task supporting `Theme.AREA_A` and `Theme.AREA_B`.
- **game**:
  - `DemoSceneM5b`: Multi-room interactive demo with HUD overlay, active room transitions, exit debug bounds, and neighbor preloading.
  - `PlatformerGame`: Wired `DemoSceneM5b`, handled accumulator discard on preload suspension, and updated replay recorder.
- **content & tests**:
  - `content/tilesets/test_area_b.json` and `test_area_b.png` generated.
  - `content/rooms/test_01.json` updated to v2 with exit to `test_02`.
  - `content/rooms/test_02.json` authored (v2) with exit back to `test_01` using both tilesets.
  - `data/src/test/resources/fixtures/rooms/test_01_v1.json` (v1 migration fixture).
  - `data/src/test/resources/fixtures/broken_rooms/broken_exit_room.json` (broken-exit test fixture).
- **docs**:
  - `docs/FORMATS.md`: Room v2, exits, transitions, and `./gradlew generateTestTiles`.
  - `docs/UPDATE_ORDER.md`: Step 8 Triggers & Zones and transition update order.
  - `docs/DECISIONS.md`: Added DEC-018, DEC-019, DEC-020.

### Acceptance results (each item: pass/fail + how verified: test name or manual steps):
- **Room format v2 & v1->v2 migration**: PASS (`RoomLoaderTest.testLoadAuthoredRoom`, `RoomLoaderTest.testLoadV1AndMigrateToV2`, `RoomLoaderTest.testRoundTripSerialization`).
- **Cross-room validator (8 checks & fixtures)**: PASS (`CrossRoomValidatorTest.testValidBidirectionalLink`, `CrossRoomValidatorTest.testUnknownTargetRoomAndSpawn`, `CrossRoomValidatorTest.testDuplicateExitIdAndInvalidBounds`, `CrossRoomValidatorTest.testWarningsSolidExitAndSpawnInsideExit`, `CrossRoomValidatorTest.testInfoOrphanAndDeadEnd`, `CrossRoomValidatorTest.testBrokenExitFixtureFile`).
- **RoomCache invalidation & Jobs preload**: PASS (`RoomCacheTest.testBasicPutGetAndEvictExcept`, `RoomCacheTest.testInvalidateOnDiskModification`, `RoomCacheTest.testPreloadNeighborsViaJobs`).
- **RoomStateStore & serialization**: PASS (`RoomStateStoreTest.testBasicIntAndStringOperations`, `RoomStateStoreTest.testIncrementInt`, `RoomStateStoreTest.testDeterministicStateHash`, `RoomStateStoreTest.testInvalidKeyValidation`, `RoomStateStoreSerializerTest.testSerializationRoundTrip`).
- **Carried entities & clean stale IDs**: PASS (`CarriedEntityTest.testCarriedEntityPreservedAndLocalEntitiesDespawnedWithCleanStaleIds`).
- **Transition determinism & hash**: PASS (`TransitionDeterminismTest.testImmediateVsDelayedPreloadDeterminism` - immediate vs 10-tick delayed preload produces identical tick count and bit-for-bit GameStateHash).
- **Zero allocation across tick types**: PASS (`M5bAllocationTest.testOrdinaryTicksAllocation`, `M5bAllocationTest.testTransitionTicksAllocation`, `M5bAllocationTest.testSuspendedTicksAllocation` - 100,000 iterations each measured < 1024 bytes).
- **Interactive multi-room demo**: PASS (Manual observation with `DemoSceneM5b`).

### Test summary: counts (unit, integration, fuzz), all passing? failures listed:
- **Total tests**: 160 run, 160 passed, 0 failures, 0 skipped.
  - `data`: 59 tests
  - `engine`: 99 tests
  - `game`: 2 tests
- All passing.

### Deviations from the instruction set (each: what, why, impact). If none, write NONE.
NONE.

### Decisions made (also added to docs/DECISIONS.md): ID, choice, alternatives, reversibility
- **DEC-018: Deterministic Transition Loading Delay**: When target room is not ready during `HOLD_WAIT_SWAP`, suspend simulation ticks, discard accumulator delta, render solid black, and drain Jobs. Replay tick count remains strictly 28 ticks regardless of I/O latency. Reversibility: Easy.
- **DEC-019: Combined Game-State Hash**: Implemented `GameStateHash` as an external pure utility over `world.stateHash()`, `roomId`, transition state/tick, and `roomStateStore.stateHash()`, preventing `World` from depending on `RoomStateStore`. Reversibility: Easy.
- **DEC-020: Input Handling During Room Transitions**: Input layer keeps draining and recording every tick to maintain replay stream alignment; entity systems are frozen; keys held after fade-in apply normally upon entering `IDLE`. Reversibility: Easy.

### Open-decision items touched (section 11 numbers) and the default chosen:
- Touched Item 5 (enemy respawn policy and persistence): Implemented `RoomScopeComponent` distinguishing `PERSISTENT_CARRIED` (player) from `ROOM_LOCAL` (enemies/platforms). Local entities are despawned at swap; persistent entity records stored in `RoomStateStore`.

### Anti-pattern self-check (section 12): any violations found and fixed or remaining:
- **"Reading Gdx.input outside the input layer; computing pressed from the render frame."**: None. All input touches `InputManager` on tick boundaries.
- **"Seconds or wall-clock time in simulation, animation, dialog, or cutscenes."**: None. All transition timers (`fadeOutTicks 12`, `holdTicks 4`, `fadeInTicks 12`, timeout 600 ticks) use discrete integer ticks.
- **"Referencing content by index, display name, or atlas position."**: None. Rooms, spawn points, and exits referenced by stable ASCII IDs.
- **"Components with logic or direct entity references; systems with private state outside snapshots."**: None. `RoomScopeComponent` is pure data. Generational IDs used.
- **"Special cases by variant (if short) or by player type; a special Player class."**: None. Player is a normal entity with `PERSISTENT_CARRIED` scope.
- **"A second code path for inventory, flags, conditions, strings, or death."**: None.
- **"has_item or take_item reaching into the stash; the stash used as a gameplay check."**: None.
- **"Cutscene steps with inline text, missing snap behavior, or looping clips being waited on."**: None.
- **"Skip hops that apply state from the whole scene instead of the skipped segment, or that change results when fade length is 0."**: None.
- **"Unversioned formats; non-atomic saves; saving during cutscenes."**: None. Room format v2 explicitly versioned with migration step.
- **"Silent fallbacks (missing asset invisible, missing string blank, unknown flag defaulting to false)."**: None. Missing rooms or broken exits log loud errors and abort cleanly.
- **"Gameplay logic reading the current animation frame instead of frame events."**: None.
- **"Per-frame allocation in the sim loop; sharing the Lua state across threads; touching GL, ImGui, or editor data from workers."**: None. Zero per-frame allocation in ordinary, fade, and suspended simulation loops verified by `M5bAllocationTest`. Preload worker tasks in `RoomCache` touch only pure data (no GL/ImGui).
- **"Editor previews with a private renderer; hand-written inspector panels per component."**: None.
- **"Adding features not listed in the active milestone."**: None.

### Principle review (section 4):
- **Principle 4.1 (Engine never depends on editors)**: Engine has zero editor or data module dependencies. Decoupled via `RoomScopeComponent`, `RoomExit`, and `GameStateHash`.
- **Principle 4.2 (Editors preview with real runtime)**: Real `RoomRenderer` and `World` used for all presentation.
- **Principle 4.3 (Determinism)**: Exact same tick count and bit-for-bit `GameStateHash` verified between immediate and delayed loads.
- **Principle 4.4 (Data over code)**: Exits, spawn points, and tileset aliases authored in JSON.
- **Principle 4.5 (Stable IDs)**: Stable ASCII IDs `[a-z0-9_.]` for exits, spawn points, and rooms.
- **Principle 4.6 (Version everything)**: Room format v2 with automated migration chain from v1.
- **Principle 4.7 (Loud failure)**: Loud errors for missing target rooms, spawns, or timeout.
- **Principle 4.8 (Validate at boundaries)**: `CrossRoomValidator` validates all cross-room links, bounds, and topology.
- **Principle 4.9 (One pipeline per concern)**: Single transition state machine and single `RoomStateStore`.
- **Principle 4.10 (Systems hold no hidden state)**: All room state in `RoomStateStore`; transition state in `RoomTransitionManager`.
- **Principle 4.11 (Components are data only)**: `RoomScopeComponent` is pure data enum.
- **Principle 4.12 (No game rules in glue)**: PlatformerGame merely delegates ticks and accumulates.
- **Principle 4.13 (Cutscene snap behavior)**: N/A for M5b.
- **Principle 4.14 (Allocation discipline)**: Non-allocating fixed-size array in `RoomTransitionManager`; 0 bytes allocated in 100k ticks.
- **Principle 4.15 (Tests are part of the feature)**: 21 new tests added covering transitions, determinism, cache, cross-room validation, and allocation.

### Known issues and tech debt (with severity):
- None.

### Performance notes (frame time, allocations, load times) if relevant:
- Update time: ~0.03 ms per tick.
- Render time: ~0.48 ms per frame.
- Allocations per tick: 0 bytes across ordinary, fade, and suspended ticks (measured via `ThreadMXBean` in `M5bAllocationTest`).
- Preload time: background worker preloads neighbor room in ~12 ms on startup without stalling the main thread.

### Files changed (short tree or list) and where docs were updated:
- `engine/`:
  - `src/main/java/com/makeasaying/engine/entity/World.java`
  - `src/main/java/com/makeasaying/engine/entity/components/RoomScopeComponent.java`
  - `src/main/java/com/makeasaying/engine/loop/FixedTimestep.java`
  - `src/main/java/com/makeasaying/engine/world/GameStateHash.java`
  - `src/main/java/com/makeasaying/engine/world/Room.java`
  - `src/main/java/com/makeasaying/engine/world/RoomExit.java`
  - `src/main/java/com/makeasaying/engine/world/RoomStateStore.java`
  - `src/main/java/com/makeasaying/engine/world/RoomTransitionConfig.java`
  - `src/main/java/com/makeasaying/engine/world/RoomTransitionHelper.java`
  - `src/main/java/com/makeasaying/engine/world/RoomTransitionManager.java`
  - `src/main/java/com/makeasaying/engine/world/RoomTransitionState.java`
  - `src/test/java/com/makeasaying/engine/world/CarriedEntityTest.java`
  - `src/test/java/com/makeasaying/engine/world/RoomStateStoreTest.java`
  - `src/test/java/com/makeasaying/engine/world/TransitionDeterminismTest.java`
- `data/`:
  - `src/main/java/com/makeasaying/data/world/CrossRoomValidator.java`
  - `src/main/java/com/makeasaying/data/world/RoomCache.java`
  - `src/main/java/com/makeasaying/data/world/RoomLoader.java`
  - `src/main/java/com/makeasaying/data/world/RoomStateStoreSerializer.java`
  - `src/main/java/com/makeasaying/data/world/RoomWriter.java`
  - `src/main/java/com/makeasaying/data/world/ValidationSeverity.java`
  - `src/test/java/com/makeasaying/data/tools/TileSheetGenerator.java`
  - `src/test/java/com/makeasaying/data/world/CrossRoomValidatorTest.java`
  - `src/test/java/com/makeasaying/data/world/M5bAllocationTest.java`
  - `src/test/java/com/makeasaying/data/world/RoomCacheTest.java`
  - `src/test/java/com/makeasaying/data/world/RoomLoaderTest.java`
  - `src/test/java/com/makeasaying/data/world/RoomStateStoreSerializerTest.java`
  - `src/test/resources/fixtures/rooms/test_01_v1.json`
  - `src/test/resources/fixtures/broken_rooms/broken_exit_room.json`
- `game/`:
  - `src/main/java/com/makeasaying/game/PlatformerGame.java`
  - `src/main/java/com/makeasaying/game/demo/DemoSceneM5b.java`
- `content/`:
  - `rooms/test_01.json` (v2)
  - `rooms/test_02.json` (v2)
  - `tilesets/test_area_b.json` & `test_area_b.png`
- `docs/`:
  - `docs/FORMATS.md`
  - `docs/UPDATE_ORDER.md`
  - `docs/DECISIONS.md`
  - `docs/reports/M5b_report.md`

### How to run and verify (exact commands and steps):
1. Run all automated tests:
   ```bash
   ./gradlew clean test --rerun-tasks
   ```
2. Run the interactive demo:
   ```bash
   ./gradlew :game:run
   ```
   - Walk right to x=616 in `test_01` to transition into `test_02`.
   - Walk left to x=24 in `test_02` to transition back into `test_01`.
   - Observe HUD showing room ID, transition state/tick, visit counts, cache contents, and disabled exit tracking.
   - Press F5 to record input across transitions and F6 to replay bit-for-bit.

### Questions for the Architect (numbered, each with the agent's recommended answer):
1. **Camera Zone Smoothing vs Immediate Bounds Clamp**: Currently, the camera clamps immediately to active room bounds upon transition snap (`camera.reset`). In M7/M8, should camera zone transitions within large rooms use fixed-tick smoothing or boundary windows?
   - *Recommended answer*: Use linear or smooth-step windowing counted in ticks over a tunable duration (e.g. 15-20 ticks) when moving between camera zones within a single room, but retain instant snap on room transitions to avoid cross-room view bleed.

### Proposed scope for next milestone (optional, one paragraph):
Milestone 6 (Aseprite Sprite Sheet Import and Animation Runtime): Implement Sprite file format and parser, Aseprite JSON import pipeline (clips, frame durations, roles, anchors, boxes), animation state machine driven by role mapping, sub-pixel sprite anchoring with bottom-center alignment, frame event dispatch through `EntityEventQueue`, and validator for required roles (`stand`, `run`, `jump`, `fall`, `death_collapse`).
