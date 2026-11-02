# Content and Data File Formats

This document defines all file formats used in the project, their schemas, units, and versioning rules per Section 8 of `AGENTS.md`.

## General Conventions
- All formats are UTF-8 JSON (no BOM).
- Two-space indentation.
- Stable key ordering.
- Every format has an explicit integer `version` field as the first property.
- Durations are always in integer ticks (60 ticks = 1 second).
- Pixel positions and offsets are integers.
- IDs are ASCII lowercase strings matching `^[a-z0-9_.]+$`.

---

## Configuration Format (`config.json`)
- **Location**: `content/config.json`
- **Current Version**: `1`
- **Fields**:
  - `version` (int, required): File format version.
  - `virtualWidth` (int, required): Internal render width in virtual pixels (default `320`).
  - `virtualHeight` (int, required): Internal render height in virtual pixels (default `180`).
  - `tileSize` (int, required): Tile size in pixels (default `16`).
  - `windowWidth` (int, optional): Window width on desktop launch (default `1280`).
  - `windowHeight` (int, optional): Window height on desktop launch (default `720`).
  - `windowTitle` (string, optional): Window title.
  - `borderColor` (string, optional): Hex color for integer scaling letterbox borders (default `"#000000"`).
  - `vsync` (bool, optional): Enable VSync (default `true`).
  - `targetFps` (int, optional): Target frame rate (default `60`).
  - `tickRate` (int, optional): Fixed-timestep simulation tick rate in ticks/second (default `60`).
  - `workerThreads` (int, optional): Background worker pool size, clamped between 2 and 4 (default `2`).
  - `saveSlot` (int, optional): Default save slot index to target (default `0`).
  - `continueOnStart` (bool, optional): If true and a valid checkpoint exists for `saveSlot`, the game launches directly into that checkpoint room and savepoint (default `false`).

---

## Action Definitions Format (`content/actions.json`)
- **Location**: `content/actions.json`
- **Current Version**: `1`
- **Fields**:
  - `version` (int, required): File format version.
  - `actions` (list of objects):
    - `id` (string, required): Stable ASCII action ID (e.g. `"jump"`, `"move_x"`).
    - `type` (string, required): `"button"` or `"axis"`.
    - `negativeButton` (string, optional, for axis): Action ID for negative deflection (-1).
    - `positiveButton` (string, optional, for axis): Action ID for positive deflection (+1).
    - `reserved` (bool, optional): Whether action is required to have a binding (e.g. `"pause"`, `"cancel"`).

---

## Bindings Format (`content/bindings.json`)
- **Location**: `content/bindings.json`
- **Current Version**: `1`
- **Fields**:
  - `version` (int, required): File format version.
  - `contexts` (map of string -> map of string -> list of strings):
    - Keys are contexts: `"global"`, `"gameplay"`, `"cutscene"`, `"menu"`, `"editor"`.
    - Values are maps of action IDs to lists of stable key names (e.g. `"KEY_W"`, `"KEY_SPACE"`).

---

## Replay Recording Format (`content/replays/*.json`)
- **Current Version**: `1`
- **Fields**:
  - `version` (int, required): File format version.
  - `tickRate` (int, required): Target simulation tick rate.
  - `initialSeed` (long, required): Seed for deterministic simulation RNG.
  - `bindingsHash` (string, required): Hash of bindings used during recording.
  - `events` (list of objects): Sparse action state changes:
    - `tick` (long, required): Simulation tick index.
    - `context` (string, optional): Context change if triggered on this tick.
    - `down` (list of strings, required): List of all action IDs currently held down on this tick.

---

## Movement Tuning Format (`content/tuning/movement.json`)
- **Location**: `content/tuning/movement.json`
- **Current Version**: `1`
- **Fields** (Human units converted to per-tick at load):
  - `version` (int, required): File format version.
  - `groundAccel` (float, px/s²): Ground acceleration rate.
  - `groundFriction` (float, px/s²): Ground deceleration rate when no input is held.
  - `groundTurnaroundAccel` (float, px/s²): Ground deceleration when reversing direction.
  - `airAccel` (float, px/s²): Air acceleration rate.
  - `airFriction` (float, px/s²): Air deceleration rate.
  - `airTurnaroundAccel` (float, px/s²): Air turnaround deceleration rate.
  - `maxRunSpeed` (float, px/s): Maximum horizontal running speed.
  - `jumpSpeed` (float, px/s): Initial upward jump velocity.
  - `jumpCutMultiplier` (float, ratio [0.0, 1.0]): Upward velocity multiplier on early jump release.
  - `riseGravity` (float, px/s²): Downward acceleration while rising.
  - `fallGravity` (float, px/s²): Downward acceleration while falling.
  - `maxFallSpeed` (float, px/s): Maximum downward velocity clamp.
  - `coyoteTicks` (int, ticks): Number of ticks after leaving a ledge where jump is permitted.
  - `jumpBufferTicks` (int, ticks): Number of ticks an early jump press is remembered.
  - `cornerCorrectionPx` (int, pixels): Maximum ceiling corner overhang pushed around tile edges.
  - `slopeSnapPx` (int, pixels): Maximum downward vertical step snapped to keep grounded on descending slopes. Must be >= ceil(maxRunSpeed/tickRate) + 1.
  - `dropThroughTicks` (int, ticks): Number of ticks one-way platforms are ignored after pressing Down + Jump.

---

## Tileset Format (`content/tilesets/<id>.json`)
- **Location**: `content/tilesets/<id>.json`
- **Current Version**: `1`
- **Fields**:
  - `version` (int, required): File format version.
  - `id` (string, required): Stable ASCII tileset ID (`^[a-z0-9_.]+$`).
  - `area` (string, required): Area identifier this tileset belongs to (e.g. `"test_area"`, `"lab"`, `"outdoor"`).
  - `tileSize` (int, required): Tile pixel size, must match engine virtual config (default `16`).
  - `image` (string, required): Relative path to tile sheet PNG within `content/tilesets/`.
  - `tiles` (list of objects, required):
    - `id` (string, required): Stable tile ID within tileset (e.g. `"solid_01"`, `"slope_r"`).
    - `col` (int, required): Zero-based horizontal column in tile sheet texture.
    - `row` (int, required): Zero-based vertical row in tile sheet texture (row 0 at top).
    - `shape` (string, required): Global collision shape: `"EMPTY"`, `"SOLID"`, `"ONE_WAY"`, `"SLOPE_45_UP_RIGHT"`, `"SLOPE_45_UP_LEFT"`.
    - `role` (string, required): Functional tag: `"fill"`, `"wall"`, `"slope_r45"`, `"slope_l45"`, `"slope_fill"`, `"platform"`, `"ceiling"`, `"corner"`.
    - `tags` (list of strings, optional): Authoring/decorative tags.

### Example Tileset JSON
```json
{
  "version": 1,
  "id": "test_area",
  "area": "test_area",
  "tileSize": 16,
  "image": "test_area.png",
  "tiles": [
    {
      "id": "solid_01",
      "col": 0,
      "row": 0,
      "shape": "SOLID",
      "role": "fill"
    },
    {
      "id": "slope_r",
      "col": 1,
      "row": 0,
      "shape": "SLOPE_45_UP_RIGHT",
      "role": "slope_r45"
    }
  ]
}
```

---

## Room Format (`content/rooms/<id>.json`)
- **Location**: `content/rooms/<id>.json`
- **Current Version**: `3`
- **Fields**:
  - `version` (int, required): File format version (`3`).
  - `id` (string, required): Stable ASCII room ID (`^[a-z0-9_.]+$`).
  - `width` (int, required): Width of room in tiles.
  - `height` (int, required): Height of room in tiles.
  - `tilesets` (list of objects, required): Tileset alias table for room:
    - `alias` (string, required): 1-2 lowercase letters (`^[a-z]{1,2}$`, e.g. `"a"`, `"lab"`).
    - `id` (string, required): Referenced tileset ID.
  - `layers` (map of string -> list of strings, required): Layer map. Mandatory layer is `"main"`. Optional layers include `"far_background"`, `"near_background"`, `"decoration"`, `"foreground"`.
    - Each layer contains `height` rows.
    - Each row contains `width` whitespace-separated tokens.
    - Tokens are either `.` (empty tile) or `alias:tileId` (e.g. `a:solid_01`).
    - Row order in JSON is top-to-bottom: row 0 is top of room (`y = height - 1`), row `height - 1` is floor (`y = 0`). Loaded inverted to Y-up in runtime memory.
  - `spawnPoints` (list of objects, required):
    - `id` (string, required): Stable spawn ID (`^[a-z0-9_.]+$`).
    - `x` (int, required): Horizontal position in virtual pixels (feet).
    - `y` (int, required): Vertical position in virtual pixels (feet).
  - `entitySeq` (int, required, added in v3): Monotonic instance ID sequence counter. Next allocated instance ID within the room uses `<def>_<entitySeq>`, then increments `entitySeq`.
  - `entities` (list of objects, required):
    - `instanceId` (string, required): Stable entity instance ID (`^[a-z0-9_.]+$`).
    - `definition` (string, required): Entity definition ID (`^[a-z0-9_.]+$`).
    - `x` (int, required): Spawn pixel X.
    - `y` (int, required): Spawn pixel Y.
    - `overrides` (map of string -> object, optional): Component property overrides, structured by component ID: `{ "<componentId>": { "<field>": value } }`.
  - `exits` (list of objects, added in v2):
    - `id` (string, required): Stable exit zone ID (`^[a-z0-9_.]+$`).
    - `x` (int, required): Bottom-left pixel X in room space (Y-up).
    - `y` (int, required): Bottom-left pixel Y in room space (Y-up).
    - `width` / `w` (int, required): Width of trigger zone in pixels.
    - `height` / `h` (int, required): Height of trigger zone in pixels.
    - `targetRoom` (string, required): Stable ID of destination room.
    - `targetSpawn` (string, required): Stable ID of spawn point in destination room.

### Migration Path
- `v1 -> v2`: If the `"exits"` array is missing, `MigrationChain` automatically inserts an empty array `"exits": []`.
- `v2 -> v3`: Computes `entitySeq = max(suffixes) + 1` across existing `entities` (defaults to 1 if empty). Migrates any legacy flat overrides in `entities` into structured component-nested maps (e.g. `{ "speed": 60 }` -> `{ "platform": { "speed": 60 } }`).

### Example Room JSON (v3)
```json
{
  "version": 3,
  "id": "test_01",
  "width": 40,
  "height": 12,
  "tilesets": [
    {
      "alias": "a",
      "id": "test_area"
    }
  ],
  "layers": {
    "main": [
      "a:solid_01 a:solid_01 ...",
      ". . . a:slope_r a:fill_01 ..."
    ]
  },
  "spawnPoints": [
    {
      "id": "player_start",
      "x": 32,
      "y": 16
    }
  ],
  "entitySeq": 3,
  "entities": [
    {
      "instanceId": "moving_platform_1",
      "definition": "moving_platform",
      "x": 160,
      "y": 64,
      "overrides": {
        "platform": {
          "travelX": 96,
          "travelY": 0,
          "speed": 40.0,
          "pauseTicks": 30
        }
      }
    }
  ],
  "exits": [
    {
      "id": "to_test_02",
      "x": 616,
      "y": 16,
      "width": 24,
      "height": 48,
      "targetRoom": "test_02",
      "targetSpawn": "from_test_01"
    }
  ]
}
```

---

## Entity Definition Format (`content/entities/<id>.json`)
- **Location**: `content/entities/<id>.json`
- **Current Version**: `1`
- **Fields**:
  - `version` (int, required): File format version (`1`).
  - `id` (string, required): Stable ASCII definition ID (`^[a-z0-9_.]+$`).
  - `placeable` (bool, required): Whether editor palette exposes this definition for level placement.
  - `components` (map of string -> object, required): Default component payloads keyed by component schema ID (e.g. `"body"`, `"collider"`, `"platform"`).

### Example Entity Definition JSON
```json
{
  "version": 1,
  "id": "moving_platform",
  "placeable": true,
  "components": {
    "body": {
      "x": 0.0,
      "y": 0.0,
      "vx": 0.0,
      "vy": 0.0
    },
    "collider": {
      "width": 32,
      "height": 8,
      "offsetX": 0,
      "offsetY": 0
    },
    "platform": {
      "travelX": 64,
      "travelY": 0,
      "speed": 30.0,
      "pauseTicks": 30,
      "riderBehavior": "BLOCK"
    }
  }
}
```

---

## Room Transition Configuration (`RoomTransitionConfig`)
- `fadeOutTicks` (int, default `12`): Duration in simulation ticks for fading out to black.
- `holdTicks` (int, default `4`): Minimum duration in simulation ticks to hold black before swap.
- `swapTick` (int, default `1`): Exact tick at hold expiration where room data is swapped and player is relocated.
- `fadeInTicks` (int, default `12`): Duration in simulation ticks for fading back in.
- **Total Standard Duration**: 29 simulation ticks (12 + 4 + 1 + 12).
- First active simulation tick in new room occurs at tick 30.
- Safe-valve timeout: Monotonic clock with 10.0-second timeout in waiting path.

---

## Test Tile Sheet Generation
Test tilesets (`test_area.png` and `test_area_b.png`) can be regenerated deterministically via the dedicated Gradle task:
```bash
./gradlew generateTestTiles
```
The generator source code is maintained in `data/src/test/java/com/makeasaying/data/tools/TileSheetGenerator.java`.


---

## Atomic Save File Format Protocol (M6a / DEC-023)
- Path: `content/rooms/*.json` and checkpoint saves in OS per-user data directory.
- Protocol:
  1. Writes serialized JSON payload with 2-space indentation and trailing LF to `<target>.tmp`.
  2. Forces disk sync via `FileChannel.force(true)`.
  3. If previous file exists at `<target>`, copies it to `<target>.bak` (`REPLACE_EXISTING`).
  4. Atomically renames `<target>.tmp` to `<target>` (`ATOMIC_MOVE` with fallback to `REPLACE_EXISTING`).
  5. Cleans up temporary file on failure.

---

## Checkpoint Save File Format (`save_slot_<slot>.json`)
- **Location**: OS per-user data directory (`<user.home>/.makeasaying/saves/save_slot_<slot>.json` or configurable directory)
- **Current Version**: `1`
- **Envelope Structure**:
  - `version` (int, required): Envelope and data format version (`1`).
  - `playtimeTicks` (long, required): Total simulation ticks elapsed up to save time.
  - `timestampUtc` (string, required): ISO-8601 UTC timestamp string (e.g. `"2026-11-02T09:00:00Z"`).
  - `currentRoomId` (string, required): Stable ASCII ID of active room.
  - `savepointInstanceId` (string, required): Stable ASCII instance ID of savepoint entity in active room where save was made.
  - `checksum` (string, required): SHA-256 hexadecimal digest computed over canonical JSON representation of `payload`.
  - `payload` (object, required): Payload data subject to checksum validation.
- **Payload Structure**:
  - `globalFlags` (map of string -> object): Map of registered global flag IDs to their primitive (bool, int) or enum string values. Enums are serialized as names, never ordinals.
  - `roomRecords` (map of string -> map of string -> object): Map of room IDs to their persistent room records (`type` string and `value`).
  - `reservedSections` (object): Forward-compatibility sections (`player`, `bag`, `keychain`, `stash`, `seenCutscenes`). Preserved byte-for-byte during round-trip loads/saves.

### Example Checkpoint Save JSON
```json
{
  "version": 1,
  "playtimeTicks": 7200,
  "timestampUtc": "2026-11-02T09:00:00Z",
  "currentRoomId": "test_01",
  "savepointInstanceId": "savepoint_3",
  "checksum": "a8f3b...",
  "payload": {
    "globalFlags": {
      "boss_defeated": false,
      "chapter_number": 1,
      "difficulty": "NORMAL"
    },
    "roomRecords": {
      "test_01": {
        "chest_opened": {
          "type": "BOOL",
          "value": true
        }
      }
    },
    "reservedSections": {
      "player": {},
      "bag": {},
      "keychain": {},
      "stash": {},
      "seenCutscenes": []
    }
  }
}
```

---

## Savepoint Entity Definition (`content/entities/savepoint.json`)
- **Location**: `content/entities/savepoint.json`
- **Current Version**: `1`
- **Fields**:
  - `id`: `"savepoint"`
  - `placeable`: `true`
  - `components`:
    - `collider`: `{ "width": 16, "height": 24, "offsetX": -8, "offsetY": 0, "isTrigger": true }`
    - `tags`: `{ "tags": ["savepoint"] }`

