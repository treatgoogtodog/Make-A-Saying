# System Update Order

Per Section 7.2 and 7.3 of `AGENTS.md`, systems in the engine run in a fixed, documented, deterministic sequence. Systems hold no private state; all tick advances and state transitions happen strictly within this order.

## Main Thread Frame Pipeline (Per Render Frame)
1. **Drain Jobs**: Worker pool results and error callbacks dispatched to main thread queue are drained before simulation advances.
2. **Accumulator Advance (`FixedTimestep`)**:
   - Wall-clock frame delta clamped to `MAX_DELTA = 0.1s`.
   - Accumulates delta; determines tick count `N` (capped at 5 ticks/frame, excess dropped with warning).
   - Computes interpolation factor `alpha` in `[0.0, 1.0)`.
3. **Simulation Ticks**: Executes `N` ticks of the tick pipeline below.
4. **Render Virtual Screen**:
   - 320x180 FrameBuffer bound; scene rendered with sub-tick position interpolation and whole-pixel snapping (`Snap.lerpAndSnap`).
   - FrameBuffer unbound; centered integer scaling applied to window backbuffer with letterbox/pillarbox `borderColor`.
5. **FrameTimer**: Non-allocating update and render ms timing recorded; window title stats updated once per second.

## Simulation Tick Pipeline (Fixed Timestep 1/60s)
1. **Cutscene Runner**: Advances active cutscene blocks and hops.
2. **Input Collection**: In first tick of frame, drains raw queue (at most 1 transition per button per tick, trailing releases deferred to next tick); catch-up ticks suppress 'pressed'; updates digital axes with last-pressed-wins (DEC-004); swallows held keys on context change.
3. **AI and Lua Behaviors**: Translates entity decisions into `InputState`.
4. **Moving Platforms (`PlatformSystem`)**: Updates ping-pong platform positions first using whole-pixel remainder stepping; carries riders with wall-blocking checks; pushes displaced entities with `BLOCK` (halt) or `CRUSH` (emit `CRUSHED` event) squish rules.
5. **Movement and Collision (`MovementSystem`)**: Step custom AABB against tile grid (one axis at a time, integer pixel steps with subpixel remainder); stuck-body detection; drop-through on one-way platforms (down + jump suppresses jump buffer); grounded slope surface sampling (`feetX - 1` and `feetX`) and slope snapping (`slopeSnapPx`); emits `HIT_WALL`, `LANDED`, `HIT_CEILING`.
6. **Combat**: Hurtbox / Hitbox overlap, damage calculations, recoil impulses.
7. **Death System**: State machine transitions (`CEASE` -> `COLLAPSE` / `FALLING` -> `LANDED` -> `RESOLVE`), slope checks, emits `entity_died`.
8. **Triggers and Zones**:
   - Overlap checks for triggers, camera bounds, and room exits (`RoomExit`).
   - Re-arms disabled exits once player collider clears their bounds.
   - Advances `RoomTransitionManager` (`IDLE` -> `FADE_OUT` -> `HOLD_WAIT_SWAP` -> `FADE_IN`).
   - When transition is active, entity systems (AI, platforms, movement) are frozen while input layer keeps recording and draining.
   - At swap point in `HOLD_WAIT_SWAP`, if target room is ready, executes atomic room swap: despawns room-local entities, resets player kinematics and jump state, recomputes grounded on target grid, disables arrival exit, and increments `visited` in `RoomStateStore`. If target room is not ready, tick advance is suspended, delta discarded, and Jobs drained until ready.
9. **Event Dispatch**: Deterministic processing of tick event queue with cascade limit.
10. **Animation Advance**: Advances tick-based `Animator` components and produces frame events.
11. **Deferred Spawn / Despawn**: Entity lifecycle changes deferred to the very end of the tick.


---

## Editor Mode Update Order (M6a / DEC-024)
When running in `EDITOR` mode:
1. Input drained and polled at event time via `ImGuiInputProcessor`. If ImGui captures keyboard/mouse, events are consumed by ImGui.
2. In `EDITING` mode: simulation systems are paused. Viewport renders room layers via `RoomRenderer` into an FBO.
3. In `PLAY` mode:
   - System 1: `PlayerControlSystem.update(playWorld, activeState)`
   - System 2: `PlatformSystem.update(playWorld, playGrid)`
   - System 3: `MovementSystem.update(playWorld, playGrid)`
   - Exits: Checked for overlap, but room transitions are suppressed; exit contact is logged to `LogCategory.ENGINE`.
   - On Stop (F8): `WorldSnapshot` restored bit-for-bit to original pre-play state.
