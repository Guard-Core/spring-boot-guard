<p align="center">
    <a href="https://guard-core.github.io/guard-core/latest/">
        <img src="https://guard-core.github.io/guard-core/latest/assets/guard_core_legend.svg" alt="Guard Core">
    </a>
</p>

___

<p align="center">
    <strong>Spring Boot adapter for the [guard-core](https://github.com/Guard-Core/guard-core-jvm) engine for the JVM.</strong>
</p>

<p align="center">
    <a href="https://github.com/Guard-Core/spring-boot-guard/actions/workflows/ci.yml">
        <img src="https://github.com/Guard-Core/spring-boot-guard/actions/workflows/ci.yml/badge.svg" alt="CI">
    </a>
    <a href="https://github.com/Guard-Core/spring-boot-guard/actions/workflows/code-ql.yml">
        <img src="https://github.com/Guard-Core/spring-boot-guard/actions/workflows/code-ql.yml/badge.svg" alt="CodeQL">
    </a>
    <a href="https://opensource.org/licenses/MIT">
        <img src="https://img.shields.io/badge/License-MIT-yellow.svg" alt="License">
    </a>
</p>

___

## Status

W6 real implementation. The adapter is a true port of the fastapi-guard adapter behavior onto the
Spring WebMVC/Boot 3.x envelope: the 17-check pipeline runs per request through the facade's
block bridge, error responses come byte-exact from the facade response factory, bypass/passive
semantics and per-route config match the reference, and the `guard.*` property surface reaches
every `SecurityConfig` field. Boot 3.x is the current lane; the 4.x lane joins the test matrix
next.

## Install

Published on Maven Central:

```kotlin
dependencies {
    implementation("com.guard-core:spring-boot-guard:1.1.0")
}
```

It brings in the engine facade `com.guard-core:guard-core:4.3.3`. To build from source, see "Engine dependency" below, or consume the composite from a sibling checkout.

## Kotlin and coroutines versions under Spring Boot dependency management

Spring Boot 3.5 manages `kotlin.version` 1.9.25, `kotlin-coroutines.version` 1.8.1 and `kotlin-serialization.version` 1.6.3, and dependency management overrides the versions this library was built with (Kotlin 2.4.21, coroutines 1.11.0). A Java application that imports the `spring-boot-dependencies` BOM, or uses the Spring Boot Gradle plugin with `io.spring.dependency-management`, therefore fails at startup with `NoSuchMethodError: kotlinx.coroutines.BuildersKt.runBlockingK$default` (coroutines 1.11.0 added `runBlockingK`), and with coroutines 1.11.0 on a Kotlin 1.9 stdlib every request fails with `ClassNotFoundException: kotlin.coroutines.jvm.internal.SpillingKt`. Align both versions. Serialization can stay on the managed 1.6.3.

Gradle with the Spring Boot plugin and `io.spring.dependency-management`:

```groovy
ext['kotlin.version'] = '2.4.21'
ext['kotlin-coroutines.version'] = '1.11.0'
```

Maven with the BOM imported (declare these imports before `spring-boot-dependencies`, the first declaration wins):

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>org.jetbrains.kotlin</groupId><artifactId>kotlin-bom</artifactId>
      <version>2.4.21</version><type>pom</type><scope>import</scope>
    </dependency>
    <dependency>
      <groupId>org.jetbrains.kotlinx</groupId><artifactId>kotlinx-coroutines-bom</artifactId>
      <version>1.11.0</version><type>pom</type><scope>import</scope>
    </dependency>
    <!-- then the spring-boot-dependencies import -->
  </dependencies>
</dependencyManagement>
```

With `spring-boot-starter-parent` as the parent, set `<kotlin.version>2.4.21</kotlin.version>` and `<kotlin-coroutines.version>1.11.0</kotlin-coroutines.version>` as properties instead.

## Quick start

```kotlin
@SpringBootApplication
class App

@RestController
class Routes {
    @GuardBypass(checks = ["all"])
    @GetMapping("/health")
    fun health() = mapOf("status" to "ok")

    @GuardRateLimit(requests = 5, window = 60)
    @GetMapping("/api/tight")
    fun tight() = mapOf("ok" to true)
}
```

```yaml
guard:
  enable-redis: true
  redis-url: redis://localhost:6379
  blocked-user-agents:
    - "BadBot"
  endpoint-rate-limits:
    "[/api/limited]":
      requests: 5
      window: 60
```

That is the whole integration: `GuardSecurityAutoConfiguration` wires the `SecurityConfig` bean,
the facade `GuardMiddleware` (engine-facing `GuardMiddlewareProtocol`), and the `GuardFilter`
servlet filter that runs the 17-check pipeline per request.

## What the adapter provides

| Surface | Behavior (fastapi-guard reference anchors in parentheses) |
|---|---|
| `GuardFilter` | `SecurityMiddleware.dispatch` (middleware.py:578): preflight handling, exclude-path scoping, security bypass, pipeline block before the handler, behavioral usage rules, `processResponse` (behavioral return rules + metrics + security headers + CORS) after it |
| `ServletGuardRequest` | `StarletteGuardRequest` (adapters.py:35): context-path-stripped path, case-insensitive joined headers, query params, repeatable cached body, bounded `read_body_prefix` |
| `ServletGuardResponseFactory` | `StarletteResponseFactory` (adapters.py:164): `text/plain; charset=utf-8` + content-length, redirect `Location` + content-length 0 |
| `GuardMiddleware` | `SecurityMiddleware` (middleware.py:46): rate limit/redis/geo/agent handlers, event bus + metrics, `HandlerInitializer`, `RouteConfigResolver`, `ErrorResponseFactory`, `RequestValidator`, `BypassHandler`, `BehavioralProcessor`, `CorsHandler`, lazy first-request init with the 503 + `Retry-After: 5` Redis path |
| `Guard*` annotations | the facade `SecurityDecorator` factories 1:1 (same parameter surface, same defaults) applied per handler method with method-identity route ids (the `StampedRoute` analog; the python `module.qualname` collision class cannot occur) |
| `guard.*` properties | 122 of the 129 `SecurityConfig` fields bind from properties; the 7 object-typed fields (geo_ip_handler, cloud_ip_store, custom_request_check, auth_verifier, custom_response_modifier, on_error, on_block) wire through `SecurityConfigCustomizer` beans, so all 129 are reachable |

### Decorator annotations

`@GuardRequireIp`, `@GuardBlockCountries`, `@GuardAllowCountries`, `@GuardBlockClouds`,
`@GuardBypass`, `@GuardRateLimit`, `@GuardGeoRateLimit`, `@GuardUsageMonitor`,
`@GuardReturnMonitor`, `@GuardBehaviorAnalysis`, `@GuardSuspiciousFrequency`, `@GuardRequireHttps`,
`@GuardRequireAuth`, `@GuardApiKeyAuth`, `@GuardRequireAuthorizationHeader`, `@GuardRequireHeaders`,
`@GuardBlockUserAgents`, `@GuardContentTypeFilter`, `@GuardMaxRequestSize`, `@GuardRequireReferrer`,
`@GuardCustomValidation`, `@GuardDetectionExclusion`, `@GuardTimeWindow`,
`@GuardSuspiciousDetection`, `@GuardHoneypotDetection`.

Callable factory parameters (`require_auth`/`api_key_auth` verifiers, `custom_validation`
validators) cannot be annotation attributes, so they reference Spring beans by name
(`verifierBean` / `validatorBean`; bean types `RouteAuthVerifier` and `CustomRouteValidator`).

### Re-exports

`com.guardcore.spring` re-exports the engine's user-facing types (the fastapi-guard
`guard/__init__.py` convention): `SecurityConfig`, `SecurityConfigBuilder`, `SecurityDecorator`,
`RouteConfig`, the handler managers (`RedisManager`, `RateLimitManager`, `IpBanManager`,
`IpInfoManager`, `CloudManager`, `SecurityHeadersManager`, `BehaviorTracker`), and the protocol
types - import from the adapter package, never from the engine coordinates directly.

### JSON converter ordering

The engine requires kotlinx-serialization at runtime, and Spring Framework 6.2 registers the
KotlinSerialization JSON message converter before Jackson in its default list - which would
silently flip JSON handling for consuming apps. The auto-configuration registers
`guardJsonConverterOrdering` (a `WebMvcConfigurer`) that restores the standard Jackson-first order
whenever both converters are present. Override that bean if you explicitly want the kotlinx order.

### Lifecycle

Initialization follows the fastapi-guard contract: lazy on the first request behind a lock, with a
`503 Service temporarily unavailable` + `Retry-After: 5` response when Redis is unavailable and
`guard.redis-fail-open` is false. Set `guard.eager-init=true` to initialize at context startup
(the `guard.lifespan` analog); with `guard.lazy-init=false` and no eager init, the first request
logs the same "eager init not honored" warning as fastapi-guard.

## Engine dependency (includeBuild + dependency substitution)

The engine artifact `com.guard-core:guard-core` (groupId is the Central Portal namespace; Kotlin
packages stay `com.guardcore.*`) is on Maven Central for consumers. This repository's own build pins the engine from source instead: CI and local builds check out `Guard-Core/guard-core-jvm` at the
pinned ref `7711606` as the sibling directory `./guard-core-jvm`, and `settings.gradle.kts`
substitutes the declared coordinates `com.guard-core:guard-core:4.3.3` /
`com.guard-core:guard-core-engine:4.3.3` with the included build's projects (the Gradle analog of
the Rust path+version pattern). The declared dependency coordinates stay
`com.guard-core:guard-core:4.3.3` (lockstep version per the family versioning law); consumers of a
published artifact resolve the real Maven artifact with no composite involved.

Set `GUARD_CORE_USE_COMPOSITE=false` to build without the composite: the declared coordinates then
resolve from the repositories (mavenLocal first, then Maven Central) - this is how the release
workflow proves the published artifact graph (docs/release.md).

Adapter parity with the reference middleware releases is assessed per upstream feature release:
[docs/adapter-parity-8.1.0.md](docs/adapter-parity-8.1.0.md) maps fastapi-guard 8.1.0's
`PureASGISecurityMiddleware` onto this adapter's `OncePerRequestFilter` idiom. Responses stream unbuffered (SSE, `StreamingResponseBody`, file downloads): security headers, CORS, metrics and status based return rules apply just before the response commits. The response is buffered only on requests where a `custom_response_modifier` is configured or a route or global return-pattern rule inspects the body (`guard.behavior-scan-response-body=true` with a pattern that is not `status:`).

Local checkout:

```bash
git clone https://github.com/Guard-Core/guard-core-jvm.git guard-core-jvm
git -C guard-core-jvm checkout 7711606
./gradlew build
```

The engine directory resolves in order: `-PguardCoreJvmDir=<path>`, `GUARD_CORE_JVM_DIR`,
`./guard-core-jvm`, `../guard-core-jvm`.

## Redis

Rate limiting, IP banning, and behavioral tracking run through the facade managers against any
Redis 6+ (`guard.redis-url`). The test suite uses the live `localhost:6379` (CI wires a redis
service container); redis-backed tests skip when no server answers.

## Telemetry agent

The adapter never builds the agent itself. Register a guard-agent-jvm handler as an `AgentHandlerProtocol` bean and set `guard.enable-agent=true` (with `guard.agent-api-key`, which the engine config requires when the agent is enabled). The auto-configuration hands the bean to `GuardMiddleware`, which starts it on initialization and stops it, flushing its buffer, when the application context closes. Exactly one `AgentHandlerProtocol` bean is used: two beans fail startup with `NoUniqueBeanDefinitionException` unless one is `@Primary`. Add the agent to your own build:

```kotlin
implementation("com.guard-core:guard-agent-jvm:3.2.3")
```

```kotlin
import com.guardcore.agent.GuardAgent
import com.guardcore.agent.GuardAgentTelemetry
import com.guardcore.agent.defaultConfig
import com.guardcore.protocols.AgentHandlerProtocol
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class GuardAgentConfig {
    @Bean
    fun guardAgentTelemetry(): AgentHandlerProtocol {
        val cfg = defaultConfig()
        cfg.apiKey = "YOUR_API_KEY"
        cfg.projectId = "YOUR_PROJECT_ID"
        return GuardAgentTelemetry(GuardAgent.create(cfg))
    }
}
```

With `guard.enable-agent=true` and no such bean, the middleware degrades like the reference adapter: it logs the missing handler and continues without agent functionality (or fails startup when `guard.agent-strict=true`).

## Example

`examples/demo-boot` is a runnable Boot app with guarded + open routes and the README run
instructions (family convention: adapters carry examples).

## Boundary

This adapter contains no security logic - only framework wiring over the facade surfaces
(`guard-core.protocols`, `guard-core.core`, `guard-core.handlers`, `guard-core.decorators`,
`guard-core.java`). Every decision comes from the engine.

## License

[MIT](LICENSE)
