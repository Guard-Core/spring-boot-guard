# demo-boot

A minimal Spring Boot 3.x application guarded by spring-boot-guard: guarded routes, open routes,
per-route decorator annotations, and the `guard.*` configuration surface, exactly like the
fastapi-guard example apps.

## Layout

| Route | Guard surface |
|---|---|
| `GET /` | plain route; passes the pipeline; guard security headers applied on the way out |
| `GET /health` | `@GuardBypass(checks = ["all"])` - every check skipped |
| `GET /api/limited` | global `guard.endpoint-rate-limits[/api/limited]` = 5 requests / 60s |
| `GET /api/tight` | `@GuardRateLimit(requests = 3, window = 60)` |
| `GET /api/admin` | `@GuardRequireIp(whitelist = ["127.0.0.1", "::1"])` |

Global config lives in `src/main/resources/application.yml` (`guard.*` properties mirror the
engine's `SecurityConfig` field names).

## Run it

Prerequisites:

1. Clone Guard-Core/guard-core-jvm at the pinned ref `5f4ea56` next to this repository
   (the engine builds from source via includeBuild until the artifacts publish):

   ```bash
   git clone https://github.com/Guard-Core/guard-core-jvm.git guard-core-jvm
   git -C guard-core-jvm checkout 5f4ea56
   ```

2. A Redis on `localhost:6379` (rate limiting), or set `guard.enable-redis: false` in
   `application.yml` to run without it.

Start the app from this directory (the parent repo's Gradle wrapper runs this project's own
settings):

```bash
cd examples/demo-boot
../../gradlew bootRun
```

Then try:

```bash
curl -s localhost:8080/                        # 200, guard security headers on the response
curl -s localhost:8080/health                  # 200, bypass route
curl -s localhost:8080/api/limited             # 200 ... 6th call: 429 Too many requests
for i in 1 2 3 4; do curl -s -o /dev/null -w "%{http_code}\n" localhost:8080/api/tight; done
curl -s -A "sqlmap/1.0" localhost:8080/        # 403, custom body "Blocked by guard (demo)"
```
