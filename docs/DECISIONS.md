# Architecture Decisions Log

This document records all architectural decisions, especially when resolving open items from Section 11 of `AGENTS.md`.

Format for entries:
- **Decision ID**: DEC-XXX
- **Date**: YYYY-MM-DD
- **Context**: Problem statement and constraints.
- **Choice**: The chosen approach.
- **Alternatives Considered**: Other options and why they were not chosen.
- **Reversibility**: Easy / Moderate / Hard.

---

### DEC-001: M0 Module Hierarchy and Build Tooling
- **Date**: 2026-11-01
- **Context**: Need a multi-module build setup enforcing strict separation: engine never depends on editors; editors depend on engine and data; data is isolated for file serialization.
- **Choice**: Gradle multi-module build with Java 21 LTS (`Temurin-21.0.9`), Gradle 8.14.3 wrapper, libGDX 1.13.1, JUnit 5.11.0, Jackson 2.17.2, ImGui-Java 1.89.0, LuaJ 3.0.1.
- **Alternatives Considered**: Maven (less standard for libGDX ecosystem), Single monolithic module (violates Rule 4.1).
- **Reversibility**: Moderate.

---

### DEC-002: Dependency Direction between Engine and Data
- **Date**: 2026-11-01
- **Context**: Clarification on module boundaries between `engine` and `data`.
- **Choice**:
  - `engine` defines runtime domain models and interfaces, holds zero file I/O code, and never reads or writes files.
  - `data` depends on `engine` (`api project(':engine')`), contains Jackson serializers/deserializers, validators, migrations, and is the sole module reading/writing content files.
  - `game` and editors wire `engine` and `data` together.
  - `engine -> data` is strictly forbidden and enforced by Gradle and architecture tests.
- **Alternatives Considered**: `engine` depending on `data` (rejected: would force `engine` to take serialization dependencies or create duplicate DTOs).
- **Reversibility**: Hard (foundational architectural boundary).
