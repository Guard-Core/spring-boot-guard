# Contributing to Spring Boot Guard

Thanks for considering a contribution to spring-boot-guard, the Spring Boot
adapter of the guard-core-jvm family (conventions follow the Python baseline:
guard-core and fastapi-guard). The adapter contains no security logic of its
own: it implements the guard-core-jvm facade protocols over the servlet
envelope and drives the engine's pipeline.

## Development Setup

Requirements:

- JDK 17 (the build pins the JVM toolchain to 17)
- Docker (Redis runs as a CI service locally too, via `docker run -p 6379:6379 redis:7-alpine`)

## Engine dependency

The adapter builds the engine from source through a composite build: CI checks
out Guard-Core/guard-core-jvm at the pinned ref as the sibling directory
`./guard-core-jvm`. Locally, clone the engine next to this repository (side by
side in the same directory) at the pinned ref:

```bash
git clone https://github.com/Guard-Core/guard-core-jvm ../guard-core-jvm
git -C ../guard-core-jvm checkout 09dfddf
./gradlew build          # detekt + ktfmt check + tests (redis-backed tests skip when redis is down)
```

## Quality Gates

Run before pushing (CI enforces the same checks):

```bash
./gradlew detekt ktfmtCheck --no-daemon   # static analysis + formatting
./gradlew build --no-daemon              # the full gate incl. the Boot-slice test suite
```

The version catalog (`gradle/libs.versions.toml`) is the single source of
truth for every third-party pin; the committed `gradle.lockfile` is enforced
on every resolution (a dependency change without `./gradlew --write-locks`
fails the build) and scanned in CI with osv-scanner. Dependency additions
need justification in the PR adding them.

## Pull Requests

- Every PR closes an open issue ("Delivers issue: #N") or carries the `no-issue` label (chores and dependency bumps).
- Keep the CI green; one clean push per PR is preferred.
- Commit messages: lowercase, imperative, conventional style (`fix(scope): ...`, `feat(scope): ...`, `ci(scope): ...`). No attribution trailers.
- Adapter behavior changes need corpus evidence: the byte-exact response shapes and pipeline semantics are the engine's, proven by the Boot-slice tests against the facade.

## Security

Never open public issues for security vulnerabilities. Follow SECURITY.md and report via GitHub security advisories.

## Questions

Open a GitHub Discussion in this repository or ask in the Guard Discord (#help).
