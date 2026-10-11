# Changelog

## [Unreleased]

### Added (W6, pre-1.0.0 development on 1.0.0-SNAPSHOT; nothing released)

- Real adapter implementation over the guard-core-jvm facade (engine 4.3.2, pinned ref 5f4ea56
  via includeBuild + dependency substitution): `GuardFilter` running the 17-check
  `SecurityCheckPipeline` per request through the facade blocking bridge, `GuardMiddleware`
  (the `GuardMiddlewareProtocol` port of fastapi-guard's SecurityMiddleware),
  `ServletGuardRequest`/`ServletGuardResponse` protocol impls with cached/bounded body reads,
  and the facade error-response factory as the only response builder (byte-exact error shapes).
- `GuardSecurityAutoConfiguration`: `guard.*` configuration properties reaching 122 of the 129
  `SecurityConfig` fields plus `SecurityConfigCustomizer` beans for the 7 object-typed fields;
  conditional beans; Redis auto-wiring through the facade `RedisManager` when
  `guard.enable-redis`/`guard.redis-url` are set; opt-in `guard.eager-init` startup hook.
- 25 Guard* decorator annotations mapping the facade `SecurityDecorator` factory surface 1:1
  (same parameters and defaults), resolved per handler method with method-identity route ids
  (`StampedRoute` analog).
- Public API re-exports of the engine's user-facing types (the fastapi-guard convention).
- Test surface: MockMvc integration tests (pipeline invocation, factory-exact error shapes,
  bypass/exclude/passive semantics, annotation resolution), redis-backed rate limiting against
  a live localhost:6379, properties binding, and servlet protocol unit tests (36 tests).
- `examples/demo-boot`: runnable Boot app with guarded + open routes.

### Changed (W8b, release engineering; nothing released)

- Maven coordinates move to groupId `com.guard-core` (the Central Portal namespace; guardcore.com
  proved unverifiable and is not owned by the operator; nothing was ever published under
  `com.guardcore`). Kotlin/JVM packages stay `com.guardcore.*` (hyphens are illegal in JVM
  packages).
- `maven-publish` + `signing` with sources/javadoc jars and the Portal-required POM (MIT license,
  developers, scm); repositories move to `settings.gradle.kts`; `GUARD_CORE_USE_COMPOSITE` env
  toggle (default composite ON; `false` = resolve the engine from mavenLocal, then Maven Central,
  the release-workflow mode).
- `.github/workflows/release.yml`: engine-artifact pre-flight on Maven Central (engine before
  adapters), tests at tag, tag-consistency audit (bare vX.Y.z, tag equals the in-file version,
  SNAPSHOT flip enforced), publish job with per-artifact repo1 existence check and a Central
  Portal deployment. See docs/release.md.
