# Self-Review: Sections 4 and 12 from AGENTS.md (Verbatim)

## Section 4: Non-negotiable principles

1. Engine never depends on editors. Editors depend on engine and data. The data module is the only code that reads or writes content files.
2. Editors preview with the real runtime. No second renderer or second simulation, ever.
3. Determinism. Fixed timestep 1/60 s. Time is counted in ticks everywhere (animation, timers, dialog, cutscenes, coyote, buffers). No wall clock in simulation. One seeded RNG in world state; cosmetic effects use a separate RNG.
4. Data over code. Tuning, stats curves, entity definitions, items, clips, cutscenes live in hot-reloadable data files.
5. Stable IDs. Flags, items, entities, rooms, tiles, strings, clips, characters use stable ASCII IDs [a-z0-9_.]. Never reference by array index, display name, or atlas position. Renames go through a refactor tool that updates all references.
6. Version everything. Every file format has a version field and a migration path. Every save has a version.
7. Loud failure. Unknown flag, missing string, missing asset, bad script call: log with file, location, and context, show in an in-game overlay, and use a visible placeholder (magenta). Never crash silently, never fail silently.
8. Validate at boundaries. Data load, Lua bridge, editor edits, and save load all validate and report with precise messages (for example: cutscene intro, step 4: actor kazuma not found).
9. One pipeline per concern. One container class for inventory. One condition evaluator. One flag store. One string pipeline. One death pipeline. One event queue. No parallel implementations.
10. Systems hold no hidden state. All state is in components or world data so snapshots, replays, and editor Play/Stop work.
11. Components are data only. Components reference other entities by ID (generational), never by object.
12. No game rules in glue. The Lua bridge and UI translate only.
13. Every state-changing cutscene command has both play and snap behavior, and they must leave identical persistent state.
14. Allocation discipline. No per-frame allocation in the simulation loop (pool vectors, rects, lists).
15. Tests are part of the feature. See section 9.

## Section 12: Anti-patterns

• Reading Gdx.input outside the input layer; computing pressed from the render frame.
• Seconds or wall-clock time in simulation, animation, dialog, or cutscenes.
• Referencing content by index, display name, or atlas position.
• Components with logic or direct entity references; systems with private state outside snapshots.
• Special cases by variant (if short) or by player type; a special Player class.
• A second code path for inventory, flags, conditions, strings, or death.
• has_item or take_item reaching into the stash; the stash used as a gameplay check.
• Cutscene steps with inline text, missing snap behavior, or looping clips being waited on.
• Skip hops that apply state from the whole scene instead of the skipped segment, or that change results when fade length is 0.
• Unversioned formats; non-atomic saves; saving during cutscenes.
• Silent fallbacks (missing asset invisible, missing string blank, unknown flag defaulting to false).
• Gameplay logic reading the current animation frame instead of frame events.
• Per-frame allocation in the sim loop; sharing the Lua state across threads; touching GL, ImGui, or editor data from workers.
• Editor previews with a private renderer; hand-written inspector panels per component.
• Adding features not listed in the active milestone.
