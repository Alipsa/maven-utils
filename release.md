# Maven Utils 3.9.11 release history

## Version history

### 1.1.0, Dec 19, 2025
- Added contributor guidelines (`AGENTS.md`) covering structure, commands, style, testing, and release workflow.
- Enhanced Maven invocation handling: parse goals/flags separately, support per-call Java home, profiles/projects/settings/toolchains, and keep flags out of goals.
- Improved environment utilities: broader flag parsing (`-Dkey`, `-q`, `-P`, `-pl`, etc.) and reliable Maven home discovery via `mvn help:evaluate`.

### 1.0.0, Oct 25, 2025
- Initial version based on maven-utils 3.9.4 v1.1.1
