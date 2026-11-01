# Project Milestones Roadmap

Tracking milestone status against Section 10 of `AGENTS.md`.

| Milestone | Title | Status | Notes |
|---|---|---|---|
| M0 | Foundation | Complete | Multi-module build, config reader, category logging, empty window launcher, tests pass |
| M1 | Loop and rendering | Complete | FixedTimestep, 320x180 FBO integer scale, pixel snap/lerp, Jobs pool, zero-alloc loop, demo scene |
| M2 | Input layer | Complete | Actions, bindings per context, raw event queue, per-tick drain, replay recording/playback, zero-alloc |
| M3 | Entities and movement core | Complete | Generational IDs, components, update order, tile collision, coyote/buffer, hot reload, zero-alloc |
| M4 | Slopes and platforms | Complete | 5 tile shapes, pure floorHeightAt, 2-column sampling, slope snapping, one-way drop-through, moving platforms (BLOCK/CRUSH), zero-alloc |
| M5a | Tilesets, rooms, loading, rendering | Complete | Tileset/Room data formats, loaders, writers, validator, RoomGridBuilder, RoomRenderer, Camera follow/clamping/centering, RoomWatcher hot reload, zero-alloc |
| M5b | Transitions, exits, persistence | Planned | Exits, spawn points, async preload/transition via Jobs, persistent per-room records, camera zones |
| M6 | Editor shell and basic map editor | Planned | ImGui docking, undo/redo, Play/Stop snapshots, tile tools |
| M7 | Flags, conditions, saves | Planned | flags.json registry, condition evaluator, atomic save/load |
| M8 | Lua bridge | Planned | LuaJ integration, error overlay, controller behaviors |
| M9 | Animation, art pipeline, death | Planned | Atlas pipeline, animator, death state machine |
| M10 | Combat and stats | Planned | Hitbox/hurtbox, weapon data, stats curves, StatTraining |
| M11 | Inventory | Planned | Bag grid, keychain, stash, auto-arrange, fuzz tests |
| M12 | Dialog and text | Planned | String tables, typewriter, FontProvider, screenplay editor |
| M13 | Cutscene runner | Planned | Timeline tracks, leases, skip points, play-vs-skip harness |
| M14 | Timeline editor | Planned | Timeline UI, scrubbing, diff panel |
| M15 | Level tooling polish | Planned | Autotile, slope-run, gating analysis |
| M16 | Vertical slice and packaging | Planned | Playable vertical slice, both variants, jpackage |
