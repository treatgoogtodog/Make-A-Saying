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
  - `vsync` (bool, optional): Enable VSync (default `true`).
  - `targetFps` (int, optional): Target frame rate (default `60`).
