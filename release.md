# Maven Utils release history

## Version history

### 1.3.1, in progress
- Fixed `resolveDependencies()` producing duplicate dependencies of different versions.
  The previous implementation resolved each direct dependency independently, bypassing
  Maven's nearest-wins mediation. Dependencies are now resolved as a single graph, so
  the effective dependency set matches what Maven produces (one version per artifact,
  proper scope filtering, exclusion handling, and BOM/import resolution).

### 1.3.0, Feb 7, 2026
Created from maven-utils 3.9.4 v1.2.0 with the following changes:
- upgrade to maven 3.9.12 and resolver 1.9.25
- Added Maven distribution selection modes: `WRAPPER`, `HOME`, `DEFAULT`.
- Added deterministic precedence for `runMaven(...)` and `resolveDependencies(...)`:
  `WRAPPER -> configuredMavenHome -> DEFAULT`.
- Added per-call options (`projectDir`, `configuredMavenHome`, `preferWrapper`) and
  metadata APIs exposing which mode was used.
- Existing APIs remain compatible and are now wrapper-preferred by default.
- Added tests for precedence and cross-platform wrapper detection (`mvnw` and `mvnw.cmd`).
