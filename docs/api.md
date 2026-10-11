# API

The adapter's public surface (package `com.guardcore.spring`). Consumers
import from the adapter package, never from the engine coordinates directly.

## Auto-configuration

`GuardSecurityAutoConfiguration` wires the whole integration:

- a `SecurityConfig` bean built from the `guard.*` properties
- the facade `GuardMiddleware` (engine-facing `GuardMiddlewareProtocol`)
- the `GuardFilter` servlet filter that runs the 17-check pipeline per
  request (OncePerRequestFilter, pre-routing so unmatched paths are enveloped)
- the optional Redis option and the eager-init lifecycle

## GuardFilter

`SecurityFilter` behavior (the `SecurityMiddleware.dispatch` port): preflight
handling, exclude-path scoping, security bypass, pipeline block before the
handler, behavioral usage rules, and `processResponse` (behavioral return
rules + metrics + security headers + CORS) after it. The servlet protocol
impls (`ServletGuardRequest`, `ServletGuardResponse`,
`ServletGuardResponseFactory`) mirror the reference's Starlette shapes
byte-exact, including the bounded `read_body_prefix`.

## Decorator annotations

The facade `SecurityDecorator` factories 1:1 (same parameter surface, same
defaults) applied per handler method with method-identity route ids:

`@GuardRequireIp`, `@GuardBlockCountries`, `@GuardAllowCountries`,
`@GuardBlockClouds`, `@GuardBypass`, `@GuardRateLimit`, `@GuardGeoRateLimit`,
`@GuardUsageMonitor`, `@GuardReturnMonitor`, `@GuardBehaviorAnalysis`,
`@GuardSuspiciousFrequency`, `@GuardRequireHttps`, `@GuardRequireAuth`,
`@GuardApiKeyAuth`, `@GuardRequireAuthorizationHeader`, `@GuardRequireHeaders`,
`@GuardBlockUserAgents`, `@GuardContentTypeFilter`, `@GuardMaxRequestSize`,
`@GuardRequireReferrer`, `@GuardCustomValidation`, `@GuardDetectionExclusion`,
`@GuardTimeWindow`, `@GuardSuspiciousDetection`, `@GuardHoneypotDetection`.

Callable factory parameters (verifiers, validators) reference Spring beans by
name (`verifierBean` / `validatorBean`; bean types `RouteAuthVerifier` and
`CustomRouteValidator`).

## Properties

`guard.*` properties bind 122 of the 129 `SecurityConfig` fields (relaxed
binding maps `guard.redis-url` to `redisUrl`); the 7 object-typed fields
(geo_ip_handler, cloud_ip_store, custom_request_check, auth_verifier,
custom_response_modifier, on_error, on_block) wire through
`SecurityConfigCustomizer` beans, so all 129 are reachable. Field-by-field
semantics live in the engine's
[configuration reference](https://guard-core.github.io/guard-core-jvm/configuration/).

## Re-exports

`ReExports.kt` re-exports the engine's user-facing types (the fastapi-guard
`guard/__init__.py` convention): `SecurityConfig`, `SecurityConfigBuilder`,
`SecurityDecorator`, `RouteConfig`, the handler managers (`RedisManager`,
`RateLimitManager`, `IpBanManager`, `IpInfoManager`, `CloudManager`,
`SecurityHeadersManager`, `BehaviorTracker`), and the protocol types.

## Lifecycle

Initialization is lazy on the first request behind a lock, with a
`503 Service temporarily unavailable` + `Retry-After: 5` response when Redis
is unavailable and `guard.redis-fail-open` is false. Set
`guard.eager-init=true` to initialize at context startup.
