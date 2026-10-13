# Changelog

## Unreleased

### Changed

- Telemetry agent support: an `AgentHandlerProtocol` bean (for example guard-agent-jvm's `GuardAgentTelemetry`) is now injected into `GuardMiddleware` by the auto-configuration, adopted into the engine composite on initialization, and stopped through the new `GuardMiddleware.shutdown()` when the context closes. Before this, `guard.enable-agent=true` always degraded with a "W7 artifact" message, because the middleware had no way to receive a handler. The degrade path now only applies when no handler bean is registered.
- Dependencies: netty is pinned to 4.1.139.Final through `platform(io.netty:netty-bom)`. lettuce-core 6.8.2 declares netty 4.1.125, which carries twelve advisories fixed by 4.1.137 at the latest. The pin is declared here as well as in the guard-core-jvm facade, so this lockfile stays the same whichever engine revision the build resolves. The four netty osv-scanner suppressions are removed; the Spring Boot test-scope suppressions stay.

## [1.0.0] - 2026-10-13

### Changed (release train)

- First Maven Central release (`com.guard-core:spring-boot-guard:1.0.0`) against guard-core-jvm 4.3.3.
- Dependencies: the facade's lettuce-core moves to 6.8.2.RELEASE and slf4j-api to 2.0.20 (netty stays on the Spring Boot BOM's 4.1.135), lockfile regenerated.
- Dependency locking ignores the family's own `com.guard-core:*` artifacts. The release publish job resolves the engine from Maven Central instead of the composite build, and those modules were absent from the lock state, so the publish build failed lock validation.
- The pinned guard-core-jvm engine ref moves to the 4.3.3 release commit.

### Changed (W10, the 8.1.0 parity assessment + the engine-pin sync; nothing released)

- fastapi-guard 8.1.0 parity: the PureASGI idiom maps to this adapter's native `OncePerRequestFilter` envelope (`GuardFilter`); assessment in docs/adapter-parity-8.1.0.md. Verdict: the no-downstream-cancel and replaying-receive properties hold natively; unbuffered response streaming does not (the `ContentCachingResponseWrapper` buffers by design so the classic post-response features work), reported as a documented trade, zero code changes.
- The lockstep engine pin moves with the train: `guardCore` 4.3.2 -> 4.3.3 (gradle/libs.versions.toml + the coordinate mentions), mirroring the guard-core-jvm 4.3.3 parity release. Composite builds substitute the included checkout; the declared coordinate matters only at composite-OFF release time, when the engine 4.3.3 artifacts will be live (the pre-flight gate enforces engine-first).

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
