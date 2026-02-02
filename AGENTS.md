# Repository Guidelines

## Project Structure & Module Organization
- `pom.xml` defines the single-module Java 11 library and enforces Maven ≥3.9.4.
- `src/main/java/` holds library code shipped in the JAR (module name `se.alipsa.mavenutils`).
- `src/test/java/` contains JUnit tests; `src/test/resources/` stores fixture POMs and other test assets.
- `src/test/groovy/ResolvePom.groovy` is an exploratory dependency-resolution script.
- `target/` is Maven output; `build/` collects signed release JARs produced by `release.sh`.

## Build, Test, and Development Commands
- `mvn clean verify` – compile, test, and package; run before any PR.
- `mvn test` – quickest way to re-run tests while iterating.
- `mvn package` – build the JAR in `target/` without install/deploy.
- `mvn -Prelease clean site deploy` – sign and publish to Central (needs GPG keys and `settings.xml` credentials). `./release.sh` wraps this, requires a clean git state, and copies signed JARs to `build/`.
- `mvn versions:display-dependency-updates` – optional dependency check honoring `version-plugin-rules.xml`.

## Coding Style & Naming Conventions
- Java 11, standard Maven layout, UTF-8 encoding.
- Classes PascalCase, methods/fields camelCase, constants UPPER_SNAKE_CASE; packages lowercase (e.g., `se.alipsa.maven`).
- Use SLF4J APIs for logging; avoid System.out in production code. Add SpotBugs nullability annotations when clarity helps.
- Keep public APIs small and documented; align naming with Maven vocabulary (artifact, repository, resolver).

## Testing Guidelines
- Framework: JUnit Jupiter (6.x) with SLF4J Simple for test logging.
- Place tests in `src/test/java/` named `*Test.java`, mirroring package paths.
- Run `mvn clean verify` for full coverage; use `mvn -Dtest=ClassNameTest test` to target a specific test.
- Store fixture POMs in `src/test/resources/pom/`; favour realistic coordinates to catch resolution issues early.
- Always run `mvn test` once a feature is implemented or a bug is fixed to ensure there are no regressions.

## Commit & Pull Request Guidelines
- Use short, imperative commit messages as seen in history (e.g., `improve release script`, `change deployment name`); keep commits focused.
- PRs should describe intent, list commands run (e.g., `mvn clean verify`), and link related issues; add logs or screenshots only when useful.
- Keep the working tree clean before release or deploy; do not commit `target/` contents—only the release artifacts copied to `build/` when needed.

## Security & Configuration Tips
- Ensure Maven can locate `MAVEN_HOME` or `mvn` on PATH; configure `~/.m2/settings.xml` with deployment credentials.
- For signing, set up a GPG key and export the passphrase as needed; verify key fingerprints before running the release profile.
