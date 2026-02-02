# Maven Utils 3.9.11 release history

## Version history

### 1.2.0, Feb 2, 2026
- **Breaking change**: Upgraded to Java 17 (from Java 11)
- **Breaking change**: Requires Maven 3.9.9 or higher (from 3.9.4)
- Migrated to RepositorySystemSupplier for improved Aether repository system setup
- Added new `runMaven` overload with Consumer-based output handlers for cleaner API
- Fixed bug in `runMaven` where output/error handlers were set on invoker instead of request
- Enhanced `parsePom` Javadoc to clarify repository discovery mechanism
- Removed outdated TODO comments that incorrectly suggested incomplete implementation
- Dependency updates:
  - commons-io: 2.20.0 → 2.21.0
  - junit-jupiter: 6.0.0 → 6.0.2
  - maven-source-plugin: 3.3.1 → 3.4.0
  - maven-jar-plugin: 3.4.2 → 3.5.0
  - versions-maven-plugin: 2.19.1 → 2.21.0
  - central-publishing-maven-plugin: 0.9.0 → 0.10.0
- Added maven-resolver-supplier dependency (1.9.24)

### 1.1.0, Dec 19, 2025
- Added contributor guidelines (`AGENTS.md`) covering structure, commands, style, testing, and release workflow.
- Enhanced Maven invocation handling: parse goals/flags separately, support per-call Java home, profiles/projects/settings/toolchains, and keep flags out of goals.
- Improved environment utilities: broader flag parsing (`-Dkey`, `-q`, `-P`, `-pl`, etc.) and reliable Maven home discovery via `mvn help:evaluate`.

### 1.0.0, Oct 25, 2025
- Initial version based on maven-utils 3.9.4 v1.1.1
