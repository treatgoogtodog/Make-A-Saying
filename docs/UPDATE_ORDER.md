# System Update Order

Per Section 7.2 and 7.3 of `AGENTS.md`, systems in the engine run in a fixed, documented, deterministic sequence. Systems hold no private state; all tick advances and state transitions happen strictly within this order.

## Simulation Tick Pipeline (Fixed Timestep 1/60s)
1. **Cutscene Runner**: Advances active cutscene blocks and hops.
2. **Input Collection**: Drains raw events from render thread queue into active context, updates per-entity `InputState`.
3. **AI and Lua Behaviors**: Translates entity decisions into `InputState`.
4. **Moving Platforms**: Updates positions of platforms first before carrying riders.
5. **Movement and Collision**: Step custom AABB against tile grid (one axis at a time, integer pixel steps with subpixel remainder); carries riders; emits `onHitWall`, `onLand`, `onHitCeiling`.
6. **Combat**: Hurtbox / Hitbox overlap, damage calculations, recoil impulses.
7. **Death System**: State machine transitions (`CEASE` -> `COLLAPSE` / `FALLING` -> `LANDED` -> `RESOLVE`), slope checks, emits `entity_died`.
8. **Triggers and Zones**: Overlap checks for triggers, camera bounds, room exits.
9. **Event Dispatch**: Deterministic processing of tick event queue with cascade limit.
10. **Animation Advance**: Advances tick-based `Animator` components and produces frame events.
11. **Deferred Spawn / Despawn**: Entity lifecycle changes deferred to the very end of the tick.
