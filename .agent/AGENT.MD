Agent Instruction Set: Platformer Engine and Editors
Version 1. Roles: Human (proxy), Architect (Claude), Implementer (coding agent). This document is the single source of truth. Put it in the repo root as AGENTS.md (or CLAUDE.md) and split sections into docs/ as the project grows.
1. Roles and the report loop
• Implementer (you, the coding agent): build one milestone at a time, test it, then write a Milestone Report (section 13).
• Human (proxy): runs you, pastes your report to the Architect, pastes the Architect's reply back to you.
• Architect (Claude): reviews each report against this document, flags drift, answers questions, approves the milestone, and issues the next brief.
Loop: read milestone -> plan (short) -> implement -> test -> self-review against sections 4 and 12 -> write report -> STOP and wait for approval. Never start the next milestone without an explicit approval message.
When this document is silent or ambiguous: do NOT guess on anything in section 11 (open decisions). Ask in the report's Questions section, pick the simplest reversible default, and record it in docs/DECISIONS.md so it can be revisited.
When you must deviate from this document: stop, explain why, propose an alternative, and wait. Silent deviation is the worst failure mode.
2. Project goal
A 2D platformer (Cave Story and Resident Evil flavored: expressive character acting in cutscenes, grid inventory management, use-based gun skill) built as a real game plus in-game tools: map and tileset editor, animation editor, cutscene timeline editor, dialog editor. The developer must always be able to drop to code and fix problems deep inside. Therefore: small readable core, plain text data, loud errors, and debug views for everything.
Single-player only. Checkpoint saves only (no save-anywhere). English first, Unicode-ready.
3. Stack and hard constraints
• Java (modern LTS) + libGDX with the LWJGL3 desktop backend. Gradle multi-module.
• Lua via LuaJ for content scripting (behaviors, triggers, custom cutscene commands). One Lua state, on the simulation thread only.
• imgui-java for all editors, drawn inside the game window on the same GL context. ImGui input capture takes priority over game input.
• gdx-controllers for gamepads. gdx-freetype or bitmap font behind a FontProvider interface.
• No Box2D. No heavy ECS library. No Ashley. Write the thin entity layer ourselves (a few hundred lines).
• No runtime sprite rotation or non-integer scaling. Nearest filtering, no mipmaps.
• All content is plain text (JSON unless the Architect approves otherwise), versioned, diffable, hand-editable.
4. Non-negotiable principles
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
5. Repo layout
engine/       loop, rendering, input layer, entities/systems, physics, animation runtime, dialog runtime,
              inventory model, stats, cutscene runner, Lua bridge, event queue, jobs
data/         file formats, loaders, savers, validators, migrations, string tables, registries
game/         launcher, game flow state machine, HUD, menus, content glue
editor-core/  docking, undo/redo commands, selection, schema-driven inspector, asset browser, validator panel
editor-map/   tileset + room + world map editors
editor-anim/  animation editor
editor-cut/   cutscene timeline + screenplay/dialog editor
assets/       source art (Aseprite), audio, fonts
content/      generated or authored data: rooms, tilesets, entities, items, strings, cutscenes, flags.json, tuning/
docs/         DECISIONS.md, FORMATS.md, UPDATE_ORDER.md, MILESTONES.md, reports/
One executable with modes: Game, Map Editor, Animation Editor, Cutscene Editor. Play/Stop in an editor runs the real game loop on in-memory data and restores a world snapshot on Stop.
6. Global constants (config file, read by code and tools)
• virtualWidth 320, virtualHeight 180, tileSize 16 (view is 20 x 11.25 tiles). Render to a FrameBuffer, scale by the largest integer factor, border otherwise.
• Snap sprites and camera to whole virtual pixels at draw time. Interpolate between previous and current sim state, then snap.
• cmPerPixel 4.5, headUnit 6 px (authoring guide only). Protagonist variants: 163 cm = 36 px, 175 cm = 39 px. Other playable characters 179-183 cm = 40-41 px.
• Sprite origin: bottom-center (feet). It is the ground contact and slope sample point.
• Body collider: 12 px wide, 36 px tall standing, about 22 px crouched (shared by all protagonist variants and similar characters). Sprites may overhang the collider by a few pixels.
• Doors 3 tiles tall. Standing passages at least 3 tiles. Crawl passages 2 tiles.
• Portraits 48x48 per character per emotion. UI font: one pixel font about 8 px, behind FontProvider.
• Atlas: 1-2 px extrusion, trimmed regions store offsets (anchors and boxes must account for the offset), stable region names.
7. System specifications
7.1 Loop and threading
• Single main thread for GL, ImGui, simulation, and Lua. Fixed-timestep accumulator; input events are queued on render frames and drained per tick.
• Worker pool (2 threads, cores-1 capped at 4) for file loading, decoding, hot-reload watching, validation, autosave. All routed through one Jobs class: submit(task, onDoneOnMainThread). Workers never touch GL, ImGui, editor data, or Lua.
• Frame-time panel (update / render / ImGui) from the start.
7.2 Entities
• Entity = generational ID + components. Systems run in a fixed documented order (docs/UPDATE_ORDER.md). Spawn and despawn are deferred to end of tick.
• Components (start set): Body (pos, subpixel remainder, previous pos, velocity), Collider, Facing, Animator, Health, Stats, InputState, Controller, Hurtbox/Hitbox, Carrier/Rider, DeathState, Script, Tags, PersistentId.
• Control source per entity: PLAYER, AI, SCRIPT, CUTSCENE_SIM, NONE. A Puppet flag makes movement skip the entity. Cutscenes take entities through a control lease that records and restores the previous controller; conflicts are visible in the debug panel. DEAD outranks everything and cannot be released.
• The player is a normal entity (no special type). Never use a global player singleton; look up by tag or controlled-entity reference. The camera follows a target.
• Entity definitions (data) hold default components; room instances hold definition ID, stable instance ID, position, overrides. Component schemas (name, type, range, tooltip) drive serializer, validator, and the editor inspector. Do not hand-write inspector panels per component.
• Event queue with deterministic ordering: entity_died, damaged, trigger_entered, flag_changed, item_added, item_removed, item_used, stat_changed, and so on. Systems and Lua react to events rather than calling each other. Add a per-tick cascade limit with a logged error.
• Uniform grid for overlap queries. No entity sleeping: all entities in a loaded room simulate.
7.3 Update order (starting point; document and keep current)
1 cutscene runner, 2 input collection, 3 AI and Lua behaviors, 4 moving platforms, 5 movement and collision then carry riders, 6 combat, 7 death system, 8 triggers and zones, 9 event dispatch, 10 animation advance, 11 deferred spawn/despawn.
7.4 Input
• Layers: devices -> bindings (data) -> actions (button with down/pressed/released, axis) -> InputState (per entity) -> systems. Nothing outside the input layer touches Gdx.input.
• Raw events are queued on the render thread and drained at the start of each tick, so a tap shorter than a tick still yields pressed for one tick, catch-up ticks do not repeat pressed, and zero-tick frames keep the press.
• Context stack (gameplay, cutscene/dialog, menu/pause, editor). Only the top context receives input. On context change, clear stale pressed states; consume the press that caused the change.
• Movement is digital by default (-1, 0, 1). One bindings set, single player. Bind movement by physical key position (scancode), display real labels. Multiple bindings per action, conflict detection, reserved actions cannot be unbound.
• Gamepad: hot-plug, dead zone about 0.2 with hysteresis, pause on disconnect, prompts match the last-used device. Prompts use {action:name}, never key names.
• ImGui WantCaptureKeyboard/Mouse is checked first.
• Record per-tick action snapshots for replay and automated tests. Input debug panel (live actions, context stack, tick history strip).
7.5 Movement and collision
• Custom AABB against the tile grid, one axis at a time, integer pixel steps with subpixel remainder. onHitWall, onLand, onHitCeiling events.
• Acceleration and friction with separate ground/air and accelerate/turnaround values. Variable jump height (cut on release), stronger fall gravity, max fall speed, optional apex hang. Coyote time and jump buffer counted in ticks (zero the coyote timer on jump). Ceiling corner correction and ledge forgiveness.
• Tile shapes (global meaning, tilesets only assign them): EMPTY, SOLID, ONE_WAY, SLOPE_45_UP_RIGHT, SLOPE_45_UP_LEFT. Slopes are static walkable diagonals: no sliding, no slope momentum. Use floorHeightAt(tile, localX), snap to the surface while grounded (snap distance about one tile step), disabled while rising. Sample point is the foot position.
• One-way: solid only when moving down and previous bottom was above the top. Drop-through via a short ignore flag. Keep previous position on the body.
• Moving platforms update first, carry riders through normal moveX/moveY, define a squish rule.
• All tuning in content/tuning/*.json, hot-reloadable, with ImGui sliders that write back. Movement reads InputState only. AI, cutscene walk_to, replays, and the player all use the same movement code.
• Debug draw: collision shapes, sample point, floor height line.
7.6 Death (shared by every entity, player included)
• On death: CEASE (zero velocity, clear input and AI, drop puppet or cutscene order, stop attacks, release lease). Gravity still applies if airborne.
• Grounded: COLLAPSE plays death_collapse. Airborne: FALLING plays death_air, then LANDED plays death_land on touchdown. Both converge on RESOLVE.
• RESOLVE: if more than a tunable threshold (default 70 percent of body width) overlaps slope tiles, play death_roll along the slope using floorHeightAt toward the low edge; otherwise death_rest or hold the last frame. Then the end rule (stop at bottom or wall, fall off an edge, or fade out; default in data, per-entity override).
• Clip roles: death_collapse (required), death_air, death_land, death_roll, death_rest. Fallbacks: no air -> collapse held; no land -> go to resolve; no roll -> rest; no rest -> last collapse frame; no collapse -> flash and fade. Optional per-entity deathStyle: fall | roll | auto.
• Death emits entity_died; reactions (game over, loot, boss hooks) subscribe to the event. The player has no separate death code.
• Snap-to-final computes the resting position by simulating the fall and the roll walk without playing animation.
7.7 Rooms, transitions, persistence
• A room is the unit of loading, simulation, and snapshot. Bounded size (can exceed the screen, not huge; flag rooms above an agreed maximum). One file per room, stable room ID.
• Exits (trigger or door) name target room ID and target spawn point ID. Transition: lock input, fade, load on workers, apply on main thread, place entity, fade in. Preload neighbors from exit data.
• Carried across rooms: player and attached entities. Discarded: other entities. Persistent per-room records keyed by room ID and entity persistent ID (broken walls, picked-up items, defeated bosses). Enemy respawn policy is a per-definition or per-instance setting.
• Instance IDs are never reused within a room.
• Camera: bounds per room, camera zones as data, cutscene camera overrides then hands back.
• Cutscene runner is owned by game flow, not the room, so it survives room swaps.
7.8 Flags, conditions, saves
• flags.json registry: name, type (bool, int, enum/string), scope (global, room, session, room-temporary), default, min/max, doc. Reading or writing an undeclared flag is a loud error. Flag changes emit flag_changed. Apply changes at defined points; deterministic order.
• One condition format (data) with one evaluator: flag compare, flag set, seen_cutscene(id), has_item(id), item_count(id), stat_step(stat), all/any/not. Used by triggers, spawns, dialog, NPC entries, doors, cutscenes. has_item takes an optional scope parameter (default searches bag, keychain, held, equipped; the stash is never searched).
• Saves happen only at savepoints (checkpoints). Never save during a cutscene or mid-operation. A save holds: versions, global flags, per-room records, current room and spawn, player data (variant, health, stats practice), bag, keychain, stash, seen-cutscene set, playtime, label. Not saved: live enemy positions, particles, Lua coroutines, runner state.
• Atomic write (temp file, flush, rename), one backup, checksum, migrations, readable JSON in development. Settings (volume, bindings, display) are stored separately from saves. Use the OS per-user data directory. Missing flags take defaults; unknown flags are kept or dropped by a documented rule with a warning.
• Death reloads the last checkpoint, so progress since then (including stat practice) is lost.
7.9 Inventory
• Containers: Bag (2D grid, rectangular items, rotation allowed per item, stacks occupy one footprint), Keychain (ordered list with a hard capacity above the number of key items that can exist), Stash (larger grid, bag items only, reachable only at savepoints, pure storage).
• Every item definition has a required container: BAG or KEYCHAIN, fixed for life. Wrong placement returns WRONG_CONTAINER. Definition fields: id, name/description string IDs, width, height, rotatable, stackable, maxStack, category, icon, useAction, combinesWith, droppable, unique, required, consumedOnUse.
• One container class, no libGDX inside, pure cell coordinates. Source of truth is the instance list with positions; the occupancy map is derived and verified in debug builds. Operations are atomic and return results (OK, NO_SPACE, OUT_OF_BOUNDS, BLOCKED, NOT_ALLOWED, WRONG_CONTAINER): canPlace, place, remove, move, rotate, swap, stack, split, findSpace (deterministic scan, default orientation first, then rotated), autoArrange (required), combine (recipe table; check the result fits after removing inputs).
• has_item and item_count and take_item look at bag, keychain, held item, and equipped references. They never look in the stash. A separate exists_anywhere(id) (all containers) is used only for uniqueness checks and messaging.
• Mandatory grants (cutscene, script) follow one path: bag findSpace, then autoArrange if it would fit, then stash findSpace, then emit item_added with the container and show 'Bag full. Sent to stash.' Skipping a cutscene follows the identical path. Show a persistent HUD notice while a required item waits in the stash. Fetching needs bag space.
• Held item is part of inventory state and returns to origin when a screen closes. Equipped is a reference to an instance. Equipped items cannot be stashed.
• Load-time repair: if a footprint changed, re-place with findSpace, overflow to stash with a warning. Capacity upgrades only grow the bag.
• UI is cursor-based first (gamepad and keyboard), mouse drag later. Inventory screen pauses the simulation.
7.10 Stats and combat
• One global stat set: Accuracy, Handling, Recoil control, Endurance, Resilience. Only practice values are saved; derived values come from data curves (diminishing returns, min/max clamps). Modifiers (equipment) are {source, add, multiply} applied in fixed order.
• A StatTraining system listens to gameplay events (shot_hit, shot_fired, damaged, and so on) with rules in data, per weapon training multipliers, and per-room or per-minute caps. Scripted (cutscene) events carry a scripted flag and are ignored.
• Effects are smooth: derived values scale spread, aim settle, reload and draw ticks (round to whole ticks, minimum 1), recoil impulse (applied to velocity, capped), endurance, knockback resistance. Step labels (Poor, Fair, Intermediate, Good) are computed from provisional thresholds in the tuning file for display and for stat_step conditions only; the step is never saved.
• Protagonist variants (163 and 175 cm) share body, collider, movement, animation timing, and clip roles. They differ only in stats data, sprite and portrait sets, blips, and display name. Code must never branch on variant.
• Weapons: 1 melee and 2-3 firearms. Weapon data holds base spread, recoil, reload ticks, damage, training table. Spread uses the seeded sim RNG.
• Scripted cutscene fire: authored outcome (hit, miss, kill), no spread, no stats, no practice. It always plays; it spends min(ammoCost, loaded rounds) from the player's loaded magazine (default cost 1, overridable, 0 allowed), never from reserve, never negative. NPC actors spend nothing. A scripted kill goes through the normal death pipeline.
• UI: stat screen with step label, progress bar, an unsaved-progress marker, promotion notices, and a death-screen note about lost progress.
7.11 Dialog and text
• Three layers: strings (tables per language, keyed by ID), structure (dialog and cutscene data referencing IDs), presentation (styles in data).
• Files are UTF-8 (explicit charset, no BOM written), NFC-normalized at load and at the edit boundary. IDs are ASCII. Named placeholders only; plurals as a keyed map (one, other, room for few, many). Missing string falls back to the default language with a warning, never a blank box.
• Inline markup parsed once at load into runs: {pause=N ticks}, {speed=N}, {color=..}, {shake}, {wave}, {action:name}, {var:name}, {item:id}. Tags are not translatable; validate that translations keep tags and placeholders.
• Typewriter is tick-based and reveals grapheme clusters (BreakIterator). Wrap and paginate at load, never while typing. Blips use Unicode categories and a deterministic or seeded pitch pattern. First confirm completes the line, second advances.
• FontProvider with per-style fallback chains from day one. English first: pixel font rendered at the low-res scale. RTL and complex-script shaping are out of scope. Never use toUpperCase/toLowerCase without Locale.ROOT; iterate by code point or grapheme, never char.
• Characters are definitions (display name string ID, name color, portraits, blips, style). Portraits optional with fallback. World-space bubbles are for barks and short lines; real conversation uses the bottom box with portrait. Speaker animator maps a speaking flag to the talk clip.
• NPC dialog is a prioritized list of {condition, dialog}; first match wins; validator warns on unreachable entries and missing fallback.
• Choices: options with optional conditions and outcomes (jump label, set flags). Record choices as declared flags. Skip stops before a choice unless a default is declared.
• Lua passes string IDs, never literal text.
• Dialog editor (in game): screenplay view, live preview with the real font chain, edit into a buffer and commit one undo step on deactivate-after-edit, inline warnings, tag helpers, language switcher with side-by-side source, CSV import/export on the same loader, external-change conflict prompt, pseudo-localization mode. Editor font is separate from game font; the preview always uses the game font.
7.12 Animation and art
• Per-sprite data file: frames (atlas region, duration in ticks, offset), clips (frames, loop mode LOOP/ONCE/HOLD_LAST/PINGPONG, role), frame events, named anchors per frame (hand, muzzle, head, torso, bubble), hurtbox and hitbox per frame. Frame lookup is a pure function of (clip, ticksSinceStart).
• Entities reference clips by role, with fallback chains and a validator that reports missing roles. Gameplay drives animation through a small rule table (state -> role) with minimum play times and priorities; animation drives gameplay only through frame events. Attack frame data (startup, active, recovery, hit-stop) lives in animation data.
• Movement collider is fixed per posture, not per frame. Hurtboxes and hitboxes may change per frame. Facing is mirroring around the origin, with a per-sprite no-flip override.
• Import from Aseprite (JSON + PNG): tags -> clips, durations, slices -> boxes and anchors. Re-import merges by stable keys and never wipes authored data; removed frames are reported. Do not build a pixel editor.
• Sprite variants (for example area skins of a door) are chosen per instance or area default; behavior definitions are shared.
• Hit flash and palette swap via a tiny optional shader. Effects are small entities with animations; no libGDX ParticleEffect in simulation.
7.13 Tilesets and maps
• Tilesets belong to areas. A room lists the tilesets it uses with room-local aliases (for example a=lab, b=outdoor). Tile reference = alias:tileId. There is no shared common tileset; terrain is per area.
• Objects (doors, savepoints, props) are entities with art from a global sprite library packed into every area atlas. They never appear in tile layers.
• Tile properties: shape, tags, optional animation (global tick), terrainGroup, role tag (fill, floor, ceiling, wall_l, wall_r, corners, platform, slope_r45, slope_l45, slope fill tiles). Collision comes from the tileset shape; the room may have a sparse collision override layer.
• Room file: header (size, tileset alias table, music, background), layers (far background, near background, main, decoration, foreground) stored as rows of whitespace-separated tokens, collision override, entity instances, trigger zones, camera zones, spawn points, exits.
• Deleting or renaming a tile, tileset, or sprite is a refactor operation that lists and updates all users. Never leave silent holes.
• Editor: paint tools (pencil, line, rect, fill, eraser, eyedropper), terrain brush with autotile rules, selection, stamps, slope-run tool, layer visibility and lock, overlays (collision, camera bounds, 320x180 view rectangle, 3-tile clearance), entity palette with schema inspector, gizmos for triggers, zones, exits, paths. A paint stroke with its autotile side effects is one undo step.
• Validator: missing tiles or tilesets, exits to missing rooms or spawns, spawns inside solid, entities in solid, clearance under 3 tiles, slope seams, one-way blocked above, unreachable areas (jump lint as warning), duplicate IDs, required gates without a reachable savepoint.
• Tools: Play from here (spawn, flag preset, loadout preset, stats preset, variant), jump-arc overlay from tuning values, live reload, world map view with reachability and gating.
7.14 Cutscenes
• A scene declares a cast (protagonist, kazuma, and so on) bound by tag or persistent ID, or spawned by the scene. protagonist resolves to the chosen variant.
• Structure: ordered blocks; each block has parallel tracks (one track per actor, camera, dialog, audio, screen, logic); clips have absolute start ticks inside the block (no relative timing). Waits and choices are block boundaries. walk_to is simulated and has a bounded window with timeout and snap-to-target on failure, logged loudly.
• Commands (each defines play and snap): walk_to, move_to, face, teleport, jump, transfer_actor, play_clip, emote, look_at, set_pose, say, choose, bark, pan_to, follow, zoom (integer), shake (whole pixels), letterbox, fade, flash, tint, music, sfx, duck, fire, kill, hurt, set_flag, add_flag, give_item, take_item, wait, wait_for, label, goto, if, lua:name.
• Skipping: the scene has skip points (waypoints) forming a path; skip jumps to the next waypoint reachable along the path (branches tracked, choices block skipping unless a default is declared). Transition is a cut: fade out, run the hop in a single tick while black, fade in; fade length and color are data and overridable per waypoint; the result must be identical with fade length 0.
• A hop: mandatory cleanup (close dialog, stop blips, stop owned sounds and effects, release or hand over leases, restore camera mode and input context); apply state-changing steps of every skipped clip in the segment [now, W) in tick order (flags, items, ammo clamp, deaths, transfers); run W's apply block (authored move_to, facing, camera, music) or automatic snaps; jump to W's label. Hold-to-skip handles one hop at a time with a cooldown of about 20 ticks; skip input is ignored during fades.
• Mark seen_cutscene when finished or skipped. Disable saving during scenes. Scenes never resume mid-way after load. Push the cutscene input context at start; consume the triggering press.
• Runner state is plain data and snapshotable. Scrub in the editor = restore scene-start snapshot and re-simulate (periodic snapshots about every 60 ticks).
• Lua custom commands must register play, snap, and a parameter schema. Coroutine-style scene scripts are restart-on-restore and not scrubbable.
• Tests (mandatory): for every scene, compare persistent state of a full playthrough vs skip from each earlier waypoint and chained skips; test every choice branch.
7.15 Lua
• LuaJ embedded in engine behind a narrow explicit API (entity, flags, inventory, dialog, camera, events). Lua sees entity handles and IDs, never Java objects or raw text.
• Errors show file, line, and stack in an in-game overlay. A toggleable log records every call across the boundary. Arguments are validated at the boundary with clear errors.
• Lua behaviors act as another controller: they write InputState like AI. Gameplay-relevant script state is kept in explicit data (a state component), not coroutine locals.
• Hot reload of scripts. Edit scripts in an external IDE; the in-game editor does not need a code editor.
7.16 Editor framework
• Every edit is a command with do() and undo(). Data model is separate from view and is the same plain objects the runtime loads. Drags are one undo step.
• Docking layout, asset browser, validator panel with click-to-jump, schema-driven inspector, hot reload both ways, autosave with backups and crash recovery, atomic writes, stable key order in written files so diffs show only real changes.
• Cutscene timeline panel drawn with the ImGui draw-list: tracks, clips with drag and resize, playhead, labels, skip lane, variant and language switchers, ghost previews, inspector generated from command schemas, preview skip with diff panel, capture-from-play.
7.17 Debug tooling (build alongside features, not after)
Entity list and inspector (control owner, death sub-state, current clip), flag panel with change log and who-set-this, inventory panel (fill, simulate full bag, show all three containers), stats panel (practice, derived, modifiers, simulate N shots), input panel, frame-time panel, update-order display, frame-advance and pause, deterministic replay record and playback, category-toggle logging, in-game console.
8. Data format conventions
• JSON, UTF-8, stable key order, two-space indent, version field first. Each format documented in docs/FORMATS.md when introduced (fields, units, examples, migration notes).
• IDs ASCII lowercase with underscores and dots for namespaces. Durations in ticks. Positions in integer pixels (rooms in tiles). No floats where integers suffice.
• Round-trip test for every format: load, save, compare byte for byte.
• Keep a content/test_saves/ folder with saves from several story points and one with a changed item footprint.
9. Testing and definition of done
A milestone is done only when all of these hold:
1. Every acceptance item in the milestone is demonstrated by an automated test or a documented manual check.
2. Unit tests exist for pure logic (data loaders, validators, migrations, container class, condition evaluator, flag store, stats curves, movement via scripted input, cutscene runner play-vs-skip).
3. Inventory container has a random fuzz test (thousands of ops; after each op occupancy map matches the item list and nothing overlaps; failed ops leave state unchanged).
4. Movement tests feed scripted per-tick input and assert positions (jump arcs, coyote, buffer, corner correction, slopes, one-way, platforms).
5. The build passes with ./gradlew build test. No new warnings of note. No per-frame allocation in hot paths (verify with a simple profiler check for the sim loop).
6. docs/ updated: FORMATS.md, UPDATE_ORDER.md, DECISIONS.md, and the milestone report saved to docs/reports/.
7. Self-review against sections 4 and 12 completed and stated in the report.
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
11. Open decisions (do not decide silently; ask, then record in DECISIONS.md)
1. Palette: free palette vs limited shared palette (32-48 colors).
2. UI font technology for English: gdx-freetype at exact pixel size vs hand-made bitmap font.
3. Bag and stash grid sizes; keychain capacity; item footprints and the first item list.
4. Weapon list and numbers (one melee, two to three firearms); first stat curves and thresholds.
5. Default enemy respawn policy and which definitions are persistent.
6. Maximum room size; single-screen room handling of the extra 4 px of height.
7. Number of manual save slots and whether an autosave slot exists.
8. Whether the playable character crouches and has a ladder or climb mechanic.
9. Maximum simultaneous barks; bark priority rule.
10. Default transition lengths (room fade, skip fade).
11. How stash fetch and has_item messaging looks to the player.
12. Anti-patterns (reject your own work if it contains any)
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
13. Milestone Report template (the Human pastes this to the Architect)
MILESTONE REPORT
Milestone: M#  Title
Status: complete | partial | blocked
Summary (5 lines max):
What was built (modules, key classes, data files, formats added or changed):
Acceptance results (each item: pass/fail + how verified: test name or manual steps):
Test summary: counts (unit, integration, fuzz), all passing? failures listed:
Deviations from the instruction set (each: what, why, impact). If none, write NONE.
Decisions made (also added to docs/DECISIONS.md): ID, choice, alternatives, reversibility
Open-decision items touched (section 11 numbers) and the default chosen:
Anti-pattern self-check (section 12): any violations found and fixed or remaining:
Known issues and tech debt (with severity):
Performance notes (frame time, allocations, load times) if relevant:
Files changed (short tree or list) and where docs were updated:
How to run and verify (exact commands and steps):
Questions for the Architect (numbered, each with the agent's recommended answer):
Proposed scope for next milestone (optional, one paragraph):
If the work is large, attach the key interface signatures (not full source) and the relevant format examples. Paste real error text and test output, not paraphrases.
14. Kickoff prompt (the Human sends this first)
You are the Implementer for the project described in AGENTS.md. Read it fully before doing anything.
Follow section 1 (roles and loop) strictly: one milestone at a time, report, then stop.
Start with M0. Before coding, reply with: (a) a 10-line plan for M0, (b) any questions about section 11 that block M0, (c) the exact tool and library versions you intend to use (libGDX, LuaJ, imgui-java, gdx-controllers, JUnit).
Do not start coding until the Architect approves the plan.
15. How the Architect responds
For each report the Architect returns: verdict (approved, approved with changes, or rework), a list of drift or risk items with required fixes, answers to the numbered questions, any updates to this document (new decisions or corrections), and the brief for the next milestone. The Human pastes the reply back to the Implementer. If the Architect updates this document, replace the file in the repo before continuing.