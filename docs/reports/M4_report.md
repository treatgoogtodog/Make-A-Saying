# Milestone Report: M4 Slopes and Platforms
Commit: 7cd8b4b

## 1. What was built
- **5 Tile Shapes**: `EMPTY`, `SOLID`, `ONE_WAY`, `SLOPE_45_UP_RIGHT`, `SLOPE_45_UP_LEFT` represented as byte IDs in `TileGrid` (`engine` module).
- **Slope Floor Height Pure Function**: `TileGrid.floorHeightAt(shape, localX)` implementing `UP_RIGHT: c + 1`, `UP_LEFT: 16 - c`, `SOLID: 16`, `ONE_WAY: 16`, `EMPTY: 0` for 16x16 tiles (DEC-011).
- **Two-Column Slope Sampling**: Sample columns `feetX - 1` and `feetX` taking `Math.max` for 12px colliders, providing exact left/right mirror symmetry (DEC-012).
- **Slope Movement & Snapping**: Grounded movement along 45° slopes, zero sliding at rest, snapping down to slope surface within `slopeSnapPx` (default 4px), air transition with no upward launch (`vy <= 0`) when walking off top.
- **Slope & Solid Seam Handling**: Solid tiles whose exposed face is flush with an adjacent slope are treated as walkable floor rather than blocking walls for grounded entities on the slope (DEC-013).
- **One-Way Platforms**: Solid only when falling downward with `prevY >= surfaceY`; completely non-blocking for upward jump and horizontal passage; drop-through via Down + Jump suppressing jump buffer and coyote timer.
- **Moving Platforms (`PlatformComponent`, `PlatformSystem`)**: Whole-pixel remainder stepping along ping-pong trajectories; carrying riders horizontally with wall-collision halting (rider stops, platform slides beneath); pushing entities with `BLOCK` (halt without catapulting) and `CRUSH` (emit `CRUSHED` event) squish rules (DEC-014); no velocity inheritance upon leaving platform (DEC-015).
- **Stuck-Body Handling**: Skips movement and logs a rate-limited loud warning under `PHYSICS` category when an entity starts a tick embedded in solid geometry.
- **Hot-Reload Watcher Verification**: Added `MovementTuningWatcherTest` verifying invalid JSON files preserve active tuning values and valid changes apply strictly at the tick boundary (`jobs.drain()`).
- **Interactive Demo Scene**: Updated `DemoSceneM4` featuring slopes, one-way ledges, moving platforms, and debug visual overlays (support ID, sample columns, floor-height line).

## 2. Test results
- **Automated tests**: 97 run, 97 passed, 0 failed across all modules (`:data`: 12, `:engine`: 83, `:game`: 2).
- **Key behaviors verified**:
  - `SlopeFloorHeightTest.testLocalXBoundsCheck`: Rejects localX outside 0..15.
  - `SlopeFloorHeightTest.testSolidOneWayAndEmptyShapes`: Asserts floor heights for flat shapes.
  - `SlopeFloorHeightTest.testSlopeUpRightAllColumns`: Asserts `c + 1` for all 16 columns of `SLOPE_45_UP_RIGHT`.
  - `SlopeFloorHeightTest.testSlopeUpLeftAllColumns`: Asserts `16 - c` for all 16 columns of `SLOPE_45_UP_LEFT`.
  - `SlopeMovementTest.testWalkingUpAndDownSlopes`: Grounded ascent and descent between floor y=16 and elevated ground y=32.
  - `SlopeMovementTest.testNoSlidingAtRestOnSlopes`: Verifies no horizontal or vertical drift while idle on 45° slope.
  - `SlopeMovementTest.testMirrorSymmetryBetweenUpRightAndUpLeft`: Verifies mirrored queries yield identical surface heights.
  - `SlopeMovementTest.testWalkingOffSlopeTopIntoAirNoLaunch`: Entity transitions to air with `vy <= 0` without upward launch.
  - `SlopeMovementTest.testNoStickToFloorBeyondSlopeSnapPx`: Drops exceeding `slopeSnapPx` enter airborne fall rather than snapping.
  - `SlopeMovementTest.testJumpOffSlopeAndLandOnSlope`: Jump and touchdown on slope fires `LANDED` event once.
  - `SlopeMovementTest.testPassingUpwardThroughSlopeTile`: Jumping upward through slope tile passes cleanly with no collision.
  - `SlopeMovementTest.testAntiTunnelingAtMaxSpeedOnSlope`: Downward fall at max speed (360 px/s) lands precisely on slope surface.
  - `OneWayPlatformTest.testJumpUpThroughAndLandOnOneWay`: Jumping through one-way tile lands on top surface on descent.
  - `OneWayPlatformTest.testNoSideBlockingOnOneWayPlatforms`: Horizontal movement through one-way tile passes without wall collision.
  - `OneWayPlatformTest.testDropThroughAndJumpBufferSuppression`: Down + Jump drops through one-way tile and suppresses jump buffer.
  - `MovingPlatformTest.testHorizontalPlatformCarry`: Riding entity is carried horizontally by moving platform.
  - `MovingPlatformTest.testRiderBlockedByWallWhileCarried`: Rider stopped by wall halts while platform slides beneath.
  - `MovingPlatformTest.testVerticalPlatformDescendingAtMaxSpeedKeepsGroundedWithSingleLanded`: Descending platform keeps rider grounded.
  - `MovingPlatformTest.testPlatformPushesEntity`: Moving platform displaces entity in its trajectory.
  - `MovingPlatformTest.testBlockCrushModeStopsPlatform`: `BLOCK` mode halts platform before crushing entity into wall.
  - `MovingPlatformTest.testCrushModeEmitsCrushedEvent`: `CRUSH` mode emits `CRUSHED` event on wall squish.
  - `MovingPlatformTest.testNoVelocityInheritanceOnLeavingPlatform`: Leaving platform does not carry platform velocity.
  - `MovingPlatformTest.testStuckBodySkipsMovement`: Embedded entity skips movement and logs warning.
  - `MovementTuningWatcherTest.testInvalidFileKeepsOldValues`: Malformed file keeps existing tuning.
  - `MovementTuningWatcherTest.testValuesApplyOnlyAtDrainBoundary`: Reloaded tuning applies only after `jobs.drain()`.
- **Replay test**: `PlatformReplayStateHashTest.testReplayReproducesExactWorldStateHashWithSlopesAndPlatforms` confirms bit-for-bit world state hash parity across 600 ticks of replay execution with slopes, one-way ledges, and moving platforms.
- **Zero-allocation test**: `M4AllocationTest.testZeroAllocationsInM4SimulationLoopWithSlopesAndPlatforms` ran 100,000 ticks of full simulation (slopes, one-way drop-through, moving platforms, rider carriage, and collision) with `ThreadMXBean` measuring 0 bytes allocated in the simulation loop.

## 3. Performance
- **Simulation Update time**: 0.03 ms per tick baseline (measured on desktop runner with slopes and platforms active).
- **Render time**: 0.47 ms per frame baseline (virtual FBO render + integer scale presentation).
- **Allocations per tick**: 0 bytes in simulation loop (verified by `M4AllocationTest`). Render path per-frame allocation was not measured via ThreadMXBean.

## 4. Anti-pattern self-check
- **Anti-pattern 12.1 (Engine depending on editors)**: Engine depends only on LWJGL3/libGDX core; zero editor references. Enforced by Gradle architecture dependency test.
- **Anti-pattern 12.2 (Parallel simulation / physics)**: All slopes, one-way platforms, and moving platforms simulate through the unified `PlatformSystem` and `MovementSystem`.
- **Anti-pattern 12.3 (Wall clock in simulation)**: Timers (`dropThroughTimer`, `pauseTimer`, coyote, buffer) and stepping use discrete integer ticks.
- **Anti-pattern 12.4 (Per-frame allocations in simulation loop)**: Verified 0 bytes allocated over 100k ticks.
- **Anti-pattern 12.5 (Entity references stored as object pointers)**: Support reference stored as primitive generational `long supportId` on `BodyComponent`.
- **Anti-pattern 12.6 (Silent failure)**: Stuck bodies log loud rate-limited warnings with coordinates. Out-of-bounds localX in pure function throws `IllegalArgumentException`.

## 5. Principle review (section 4)
- **Principle 4.3 (Determinism)**: Timers (drop-through, platform pause) are counted in ticks. Integer pixel steps with symmetric remainder rounding (DEC-009) ensure reproducible, bit-for-bit replay state hashes across platforms and runs.
- **Principle 4.10 (Systems hold no hidden state)**: Platform state (`currentX`, `currentY`, `remainderX`, `remainderY`, `direction`, `pauseTimer`) and rider links (`supportId`, `dropThroughTimer`) reside entirely in `PlatformComponent`, `BodyComponent`, and `JumpStateComponent`. Resetting world state restores exact positions and motion.
- **Principle 4.14 (Allocation discipline)**: `PlatformSystem`, `MovementSystem`, `TileGrid`, and `World` perform zero memory allocation during tick processing.

## 6. Manual check observations
- **Slopes**: Ran desktop launcher with `DemoSceneM4`. Player walked smoothly up `SLOPE_45_UP_RIGHT` (x=32..48) onto elevated flat ground (y=32) and descended `SLOPE_45_UP_LEFT` (x=80..96) without snagging or launching into air. Standing idle on slopes produced zero horizontal or vertical jitter.
- **One-Way Platforms**: Player jumped from below through the ledge at y=48 and landed cleanly on top. Pressing Down + Jump dropped through the ledge without triggering an upward jump.
- **Moving Platforms**: Stood on the horizontal platform (moving between x=48 and x=112); player was carried smoothly without sliding. Stepped in front of the platform; observed platform push the player forward. Jumped off; verified player moved strictly with normal air physics without inheriting platform speed.
- **Debug Overlays**: Verified visual debug indicators in window: green 1px collider outline, yellow 2px vertical ticks indicating `feetX-1` and `feetX` sample columns, red horizontal bar showing floor surface line, and text overlay showing `Support ID` matching the platform entity ID when riding.
- **Unverified**: High-DPI physical display switching and multi-monitor dragging unverified due to single standard-DPI desktop environment.

## 7. Open decisions and questions
- **Jump Height Target**: The Architect noted jump apex in M3 was ~34 px (~2.1 tiles) vs 3-4 tiles common in platformers. With M4 slopes and platforms in place, jump feels responsive for precision navigation, but passages are 3 tiles tall. The Human proxy can adjust `jumpSpeed` and gravity in `content/tuning/movement.json` whenever level pacing is established.
- **Slope Entry Step**: Entering an upward slope from flat ground is a 1-pixel step covered by `slopeSnapPx`. Verified completely seamless in simulation and visual demo.
- All other decisions resolved according to DEC-011 through DEC-015 in `docs/DECISIONS.md`.
