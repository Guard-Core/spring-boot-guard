# Examples

`examples/demo-boot` is a runnable Boot application with guarded and open
routes, live Redis-backed rate limiting, and per-route decorator configs.

```bash
git clone https://github.com/Guard-Core/spring-boot-guard.git
cd spring-boot-guard
git clone https://github.com/Guard-Core/guard-core-jvm.git guard-core-jvm
git -C guard-core-jvm checkout 5f4ea56
docker run -d -p 6379:6379 redis:7-alpine
./gradlew :examples:demo-boot:bootRun
```

Quick start in your own app (the whole integration):

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

`GuardSecurityAutoConfiguration` wires the `SecurityConfig` bean, the facade
`GuardMiddleware`, and the `GuardFilter` that runs the 17-check pipeline per
request. See [API](api.md) for the annotation set and the property surface.
