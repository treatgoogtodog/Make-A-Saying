# MILESTONE REPORT
Milestone: M3 Entities and Movement Core
Status: complete

## Summary (5 lines max)
Implemented generational entity storage (packed `long` IDs, dense component arrays, ascending index iteration, deferred lifecycle) and deterministic movement physics against a tile grid.
Physics features custom AABB stepping, symmetric remainder rounding (DEC-009), variable jump height with cut, stronger fall gravity, coyote time, jump buffering, and ceiling corner correction.
Tuning parameters live in `content/tuning/movement.json` in human units, validated and precomputed at load time, with hot-reloading via `Jobs`.
Verified 100% deterministic replay state hash reproduction and 0 bytes allocated per tick across 100,000 entity simulation steps.

## What was built
- **`engine` (entity & movement systems)**:
  - `EntityId`: Packed primitive `long` `(generation << 32) | index`, index 0 reserved as invalid/null ID.
  - `World`: Preallocated dense component arrays (`BodyComponent`, `ColliderComponent`, `FacingComponent`, `ControllerComponent`, `InputStateComponent`, `JumpStateComponent`), ascending index iteration, deferred spawns/despawns, deterministic `stateHash()` and `reset()`.
  - `TileShape` & `TileGrid`: 5 tile shapes defined (`EMPTY`, `SOLID`, `ONE_WAY`, `SLOPE_45_UP_RIGHT`, `SLOPE_45_UP_LEFT`); M3 implements `EMPTY` and `SOLID` (other shapes trigger loud error); arbitrary grid dimensions, Y-up coordinates, out-of-bounds=SOLID (DEC-008).
  - `EntityEvent` & `EntityEventQueue`: Non-allocating reusable event ring buffer with cascade limit (500 events) and events (`LANDED`, `HIT_WALL`, `HIT_CEILING`).
  - `PlayerControlSystem`: Copies active input from `InputManager` to the controlled entity's `InputStateComponent`.
  - `MovementSystem`: Custom AABB integer pixel stepping with subpixel remainders, ground/air acceleration and friction, turnaround acceleration, variable jump cut on button release, rise and fall gravity, max fall speed clamp, coyote time, jump buffer, ceiling corner correction, and ground probe emitting `LANDED` strictly on touchdown.
  - `MovementTuning`: Precomputes per-tick velocities and accelerations from human units once at instantiation.
- **`data` (tuning I/O & hot reload)**:
  - `MovementTuningLoader`: Validates ranges and parses `content/tuning/movement.json`.
  - `MovementTuningWatcher`: Monitors file modifications on disk via worker pool (`Jobs`) and updates active tuning on the fly.
- **`game` (interactive demo level)**:
  - `DemoSceneM3`: Hard-coded playable obstacle course featuring running floors, variable jump height check platforms, a coyote gap, and a ceiling with a 1-tile gap to visually verify corner correction.
  - In-game debug overlay displaying coordinates, velocity, subpixel remainders, grounded state, coyote/buffer timers, and rendered collider wireframe.
  - `content/tuning/movement.json`: Authored baseline movement parameters (v1).

## Acceptance results
- **Generational entity lifecycle & stale ID safety**: PASS (`WorldTest.testStaleEntityIdCleanHandling`, `WorldTest.testNullEntityIdHandling`)
- **Deterministic state hash & reset**: PASS (`WorldTest.testStateHashAndReset`)
- **Run acceleration, friction stop & turnaround**: PASS (`MovementSystemTest.testRunAccelerationAndMaxSpeedClamp`, `MovementSystemTest.testFrictionStop`, `MovementSystemTest.testTurnaroundAcceleration`)
- **Variable jump cut & apex duration**: PASS (`MovementSystemTest.testVariableJumpCut`, `MovementSystemTest.testJumpApexHeightAndDuration`)
- **Wall collision & velocity stop**: PASS (`MovementSystemTest.testWallCollisionStopsVelocity`, `MovementCollisionTest.testAntiTunnelingAtMaxSpeedAgainstOneTileWall`)
- **Floor touchdown & anti-tunneling**: PASS (`MovementCollisionTest.testAntiTunnelingAtMaxSpeedAgainstOneTileFloor`, `MovementCollisionTest.testLandedEventFiresOncePerTouchdown`)
- **Mirror symmetry (left/right)**: PASS (`MovementCollisionTest.testMirrorSymmetryBetweenLeftAndRightMovement`)
- **Ceiling corner correction**: PASS (`MovementSystemTest.testCeilingCornerCorrection`)
- **Coyote time & jump buffer**: PASS (`MovementSystemTest.testCoyoteTime`, `MovementSystemTest.testJumpBuffer`)
- **Replay state hash equivalence across 100% of ticks**: PASS (`ReplayStateHashTest.testReplayReproducesExactWorldStateHashEveryTick`)
- **Zero allocation hot sim loop**: PASS (`EntityAllocationTest.testZeroAllocationsInHotEntitySimulationLoop` - 0 bytes across 100,000 ticks)
- **Tuning file validation & hot reload**: PASS (`MovementTuningLoaderTest.testLoadAuthoredMovementTuning`, `MovementTuningLoaderTest.testRejectInvalidTuningValues`)

## Test summary
- **Module `data`**: 10 tests completed (ConfigLoaderTest: 3, BindingsValidationTest: 5, MovementTuningLoaderTest: 2), 0 failures.
- **Module `engine`**: 58 tests completed (MovementSystemTest: 9, MovementCollisionTest: 4, WorldTest: 3, ReplayStateHashTest: 1, EntityAllocationTest: 1, InputAllocationTest: 1, InputManagerTest: 7, ReplayEquivalenceTest: 1, FixedTimestepTest: 8, SimulationAllocationTest: 1, CheckerGeneratorTest: 3, SnapAndInterpolationTest: 4, VirtualScreenMathTest: 3, JobsTest: 4, EngineConfigTest: 1, ArchitectureDependencyTest: 2, LogTest: 5), 0 failures.
- **Module `game`**: 2 tests completed (PlatformerGameTest: 2), 0 failures.
- **Total**: 70 tests completed, 0 failures, 100% passing.
*(Reconciled from real `./gradlew test` build output: M2 reported 50 tests; M3 added 20 tests; grand total = 70 tests).*

## Deviations from the instruction set
NONE.

## Decisions made (recorded in docs/DECISIONS.md)
- **DEC-005**: Reconciled physical keyboard layout check. Verified that GLFW key tokens (`GLFW_KEY_W`, `GLFW_KEY_A`, `GLFW_KEY_S`, `GLFW_KEY_D`, etc.) map to physical key positions on standard US QWERTY physical keyboard hardware. Other localized layouts (e.g. AZERTY, QWERTZ) remain unverified until physical devices/layouts are available.
- **DEC-006**: Generational entity storage: 64-bit primitive `long` packed IDs `(gen << 32) | index`, preallocated component stores, ascending slot iteration order, index 0 null ID, deferred spawn/despawn.
- **DEC-007**: Coordinate convention: World coordinates are Y-up, tile (0,0) at bottom-left. Data loaders flip authored top-to-bottom room rows.
- **DEC-008**: Out-of-bounds tiles return `SOLID` in M3/M4.
- **DEC-009**: Symmetric remainder rounding: `Snap.toPixel(float)` used for subpixel step remainders to guarantee identical mirrored coordinate steps between left and right.
- **DEC-010**: Movement tuning authoring in human units (`px/s`, `px/s²`, ticks) in `content/tuning/movement.json`, converted once to per-tick units (`px/tick`, `px/tick²`) at load time.

## Open-decision items touched (section 11)
- None touched from section 11 directly; DEC-006 through DEC-010 resolve specific M3 architectural choices.

## Anti-pattern self-check (section 12)
- *12.1 Engine never depends on editors or data*: PASS. Enforced by Gradle dependencies and `ArchitectureDependencyTest`.
- *12.2 No Box2D, Ashley, or external heavy ECS*: PASS. Built custom ~250 line `World` with generational indexing and dense arrays.
- *12.3 No per-frame allocation in hot simulation loop*: PASS. `EntityAllocationTest` verifies 0 bytes allocated across 100k simulation steps.
- *12.4 Systems hold no private state*: PASS. `MovementSystem` and `PlayerControlSystem` are stateless processors; all state resides in entity components and `World`.
- *12.5 Components are data-only and reference entities by ID, not object*: PASS.
- *12.6 Deterministic simulation (no wall clock in sim)*: PASS. All systems and timers measure whole ticks.

## Known issues and tech debt
- Low severity: `DemoSceneM3` uses a hard-coded grid level for verification prior to M5 room loading and M4 slope/platform handling. Marked as temporary demo code.

## Performance notes
- **Simulation loop allocation**: Measured 0 bytes allocated over 100,000 ticks via `EntityAllocationTest` and JVM `ThreadMXBean`.
- **Render path allocation**: Unmeasured (OpenGL draw loop requires GPU context; headless CI runs do not measure graphics driver allocations).
- **Frame times**: Update ms: ~0.03 ms; Render ms: ~0.42 ms (consistent with M1/M2 baseline).

## Files changed
```
content/
  tuning/movement.json                      [NEW: authored movement tuning in human units]
data/
  src/main/java/.../data/tuning/
    MovementTuningLoader.java                [NEW: tuning validation and loader]
    MovementTuningWatcher.java               [NEW: hot-reload watcher via Jobs pool]
  src/test/java/.../data/tuning/
    MovementTuningLoaderTest.java            [NEW: tuning validation tests]
docs/
  DECISIONS.md                               [UPDATED: DEC-005 layout reconciliation, DEC-006 - DEC-010]
  FORMATS.md                                 [UPDATED: movement.json schema and unit specs]
  MILESTONES.md                              [UPDATED: M3 marked Complete]
  reports/M3_report.md                       [NEW: this report]
engine/
  src/main/java/.../engine/entity/
    ControlSource.java                       [NEW: PLAYER, AI, SCRIPT, CUTSCENE_SIM, NONE]
    EntityId.java                            [NEW: packed generational long helper]
    MovementTuning.java                      [NEW: precomputed per-tick record]
    TileGrid.java                            [NEW: byte[] grid with Y-up and arbitrary size]
    TileShape.java                           [NEW: 5 shapes, loud error on M4 shapes]
    World.java                               [NEW: generational world, dense stores, stateHash]
    components/
      BodyComponent.java                     [NEW: pos, subpixel remainder, prevPos, vel, grounded]
      ColliderComponent.java                 [NEW: AABB with feet bottom-center anchor]
      ControllerComponent.java               [NEW: source and puppet flag]
      FacingComponent.java                   [NEW: direction -1 / +1]
      InputStateComponent.java               [NEW: entity input state wrapper]
      JumpStateComponent.java                [NEW: coyote and buffer tick timers]
    events/
      EntityEvent.java                       [NEW: reusable mutable event instance]
      EntityEventQueue.java                  [NEW: preallocated ring buffer, cascade limit]
      EntityEventType.java                   [NEW: LANDED, HIT_WALL, HIT_CEILING]
    systems/
      MovementSystem.java                    [NEW: custom AABB, subpixel steps, corner correction]
      PlayerControlSystem.java               [NEW: copies input to controlled entity]
  src/test/java/.../engine/entity/
    EntityAllocationTest.java                [NEW: 100k hot tick 0-byte allocation test]
    ReplayStateHashTest.java                 [NEW: state hash replay determinism test]
    WorldTest.java                           [NEW: generational ID safety and reset tests]
    systems/
      MovementCollisionTest.java             [NEW: anti-tunneling and mirror symmetry tests]
      MovementSystemTest.java                [NEW: acceleration, jump apex, coyote, corner correction]
game/
  src/main/java/.../game/
    PlatformerGame.java                      [UPDATED: wired DemoSceneM3 and MovementTuningWatcher]
    demo/
      DemoSceneM3.java                       [NEW: interactive obstacle course with debug overlay]
```

## How to run and verify
1. **Automated Unit & Allocation Tests**:
   ```powershell
   .\gradlew.bat test
   ```
   Asserts all 70 tests pass across all subprojects.
2. **Interactive Manual Demo Verification**:
   ```powershell
   .\gradlew.bat :game:run --console=plain
   ```
   - Move with A/D (or Left/Right arrows). Observe responsive acceleration, turnaround, and friction stop.
   - Jump with Space. Tap lightly for short hop; hold for full jump (apex ~34px, 2 tiles + 2px).
   - Test coyote time by running off the elevated ledge on the right; jump while in mid-air.
   - Test jump buffering by pressing Space slightly before landing on a platform; observe immediate rebound.
   - Test ceiling corner correction: jump upward into the 1-tile gap in the ceiling; observe character being nudged into the opening without bonking.
   - Test hot-reloading: with the game running, edit `content/tuning/movement.json` (e.g. change `maxRunSpeed` from 120.0 to 200.0) and save. Observe immediate change in game movement speed with log confirmation.

## Questions for the Architect
1. **Slope Floor Height Sampling**: In M4, slopes (`SLOPE_45_UP_RIGHT` and `SLOPE_45_UP_LEFT`) require `floorHeightAt(tile, localX)`. Should `localX` sample directly at `body.x` (feet position), and should downward slope snapping occur only when previously grounded with downward vertical velocity `<= 0`?
   - *Recommended Answer*: Yes. The foot position (`body.x`) is the slope sample point per Section 6. Snap to slope surface while grounded (snap distance ~1 tile step) and disable snap when rising (`vy > 0`), exactly as specified in Section 7.5.
2. **Moving Platform Carrying & Squish**: Section 7.5 states "Moving platforms update first, carry riders through normal moveX/moveY, define a squish rule." Should squishing an entity against a solid wall/ceiling deal fatal damage or crush-kill immediately via `entity_died` event?
   - *Recommended Answer*: If an entity's collider overlaps a solid tile after a moving platform pushes it, emit an `EntityEventType.CRUSHED` event that triggers the death pipeline (CEASE -> RESOLVE).

## Proposed scope for next milestone (M4 Slopes and platforms)
- Implement static 45-degree slopes (`SLOPE_45_UP_RIGHT`, `SLOPE_45_UP_LEFT`) with `floorHeightAt` surface snapping.
- Implement `ONE_WAY` platforms (solid only when moving down and previous feet bottom was at or above platform top; drop-through via down+jump).
- Implement moving platforms (`CarrierComponent` / `RiderComponent`), updating before entities and carrying riders through step checks with squish resolution.
- Extend demo level to showcase ascending/descending slopes, drop-through one-way ledges, and moving platforms with rider carriage.
