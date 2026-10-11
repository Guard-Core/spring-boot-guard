package com.guardcore.spring

import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

/** Shared controller for the guard integration test apps. */
@RestController
public class GuardTestController {
  @GetMapping("/api/open") public fun open(): Map<String, String> = mapOf("ok" to "true")

  @GetMapping("/api/echo") public fun echo(): Map<String, String> = mapOf("echo" to "true")

  @PostMapping("/api/search")
  public fun search(@RequestBody body: Map<String, String>): Map<String, String> =
      mapOf("query" to (body["q"] ?: ""))

  @GuardBypass(checks = ["all"])
  @GetMapping("/api/bypassed")
  public fun bypassed(): Map<String, String> = mapOf("bypassed" to "true")

  @GuardRequireHttps
  @GetMapping("/api/https-only")
  public fun httpsOnly(): Map<String, String> = mapOf("secure" to "true")

  @GuardRequireIp(whitelist = ["10.0.0.1", "10.0.0.2"])
  @GetMapping("/api/admin")
  public fun admin(): Map<String, String> = mapOf("admin" to "true")

  @GuardMaxRequestSize(sizeBytes = 10)
  @PostMapping("/api/upload")
  public fun upload(@RequestBody body: ByteArray): Map<String, Int> = mapOf("size" to body.size)

  @GuardApiKeyAuth(headerName = "X-API-Key", verifierBean = "allowAllVerifier")
  @GetMapping("/api/keyed")
  public fun keyed(): Map<String, String> = mapOf("keyed" to "true")

  @GuardRequireHeaders(
      headers = [GuardRequiredHeader(name = "X-Tenant", value = "acme")],
  )
  @GetMapping("/api/tenant")
  public fun tenant(): Map<String, String> = mapOf("tenant" to "true")

  @GuardBlockUserAgents(patterns = ["InternalScanner.*"])
  @GetMapping("/api/no-scanners")
  public fun noScanners(): Map<String, String> = mapOf("internal" to "true")

  @GuardUsageMonitor(maxCalls = 2, window = 3600)
  @GetMapping("/api/monitored")
  public fun monitored(): Map<String, String> = mapOf("monitored" to "true")

  @GuardTimeWindow(startTime = "00:00", endTime = "23:59")
  @GetMapping("/api/anytime")
  public fun anytime(): Map<String, String> = mapOf("time" to "ok")

  @GuardHoneypotDetection(trapFields = ["website_url"])
  @PostMapping("/api/contact")
  public fun contact(@RequestBody body: Map<String, String>): Map<String, String> =
      mapOf("received" to "true")
}

/** The default no-redis guard test app (pipeline + annotations + error shapes). */
@SpringBootConfiguration
@EnableAutoConfiguration
@Import(GuardTestController::class)
public open class GuardTestApp {
  @Bean
  public open fun allowAllVerifier(): SpringRouteAuthVerifier = SpringRouteAuthVerifier { _, _ ->
    mapOf("user" to "test")
  }
}

/** The passive-mode variant: violations are logged, never blocked. */
@SpringBootConfiguration
@EnableAutoConfiguration
@Import(GuardTestController::class)
public open class PassiveGuardTestApp

/** The redis-backed variant: rate limiting through the facade managers. */
@SpringBootConfiguration
@EnableAutoConfiguration
@Import(GuardTestController::class, RateLimitedController::class)
public open class RedisGuardTestApp {
  /** Fake geo resolver (geo country "US") so the geo rate limit annotation can resolve. */
  @Bean
  public open fun fakeGeoCustomizer(): SecurityConfigCustomizer =
      SecurityConfigCustomizer { builder ->
        builder.geoIpHandler = FixedCountryGeoHandler
      }
}

/** Test geo resolver answering "US" for every IP. */
public object FixedCountryGeoHandler : com.guardcore.protocols.GeoIPHandler {
  override val isInitialized: Boolean = true

  override suspend fun initialize() {}

  override suspend fun initializeRedis(
      redisHandler: com.guardcore.protocols.RedisHandlerProtocol,
  ) {}

  override suspend fun initializeAgent(
      agentHandler: com.guardcore.protocols.AgentHandlerProtocol,
  ) {}

  override fun getCountry(ip: String): String? = "US"

  override suspend fun refresh() {}

  override fun close() {}
}

/** Rate-limited routes for the redis integration (top level, @Import-registered). */
@RestController
public open class RateLimitedController {
  @GuardRateLimit(requests = 2, window = 60)
  @GetMapping("/api/limited")
  public fun limited(): Map<String, String> = mapOf("limited" to "true")

  @GuardGeoRateLimit(
      limits = [GuardGeoRateLimit.Entry(country = "US", requests = 1, window = 60)],
  )
  @GetMapping("/api/geo-limited")
  public fun geoLimited(): Map<String, String> = mapOf("geo" to "true")
}
