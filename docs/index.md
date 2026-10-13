# spring-boot-guard

Spring Boot adapter for the [guard-core-jvm](https://github.com/Guard-Core/guard-core-jvm)
engine for the JVM. The adapter is a true port of the fastapi-guard adapter
behavior onto the Spring WebMVC/Boot 3.x envelope: the engine's 17-check
pipeline runs per request, error responses come byte-exact from the facade
response factory, and the `guard.*` property surface reaches every
`SecurityConfig` field.

- **Status**: 1.0.0-SNAPSHOT, pre-release (the artifact ships with the first
  synchronized release)
- **Engine**: `com.guard-core:guard-core:4.3.3` (built from source through a
  composite build, see [Installation](installation.md))
- **Parity**: the engine's
  [every-feature matrix](https://github.com/Guard-Core/guard-core-jvm/blob/master/docs/every-feature-matrix.md)
  and the spec section table carry the family-level status

The adapter contains no security logic of its own - every decision is the
engine's.

## Documentation map

- [Installation](installation.md) - coordinates, engine dependency, Redis
- [API](api.md) - the auto-configuration, filter, annotations, and re-exports
- [Examples](examples.md) - the runnable demo app

Boot 3.x is the current lane; the 4.x lane joins the test matrix next.
