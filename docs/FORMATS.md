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
