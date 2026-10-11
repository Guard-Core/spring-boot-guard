package com.example.demo

import com.guardcore.spring.GuardBypass
import com.guardcore.spring.GuardRateLimit
import com.guardcore.spring.GuardRequireIp
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@SpringBootApplication
public open class DemoApplication

public fun main(args: Array<String>) {
  runApplication<DemoApplication>(*args)
}

@RestController
public open class DemoRoutes {
  @GetMapping("/")
  public fun index(): Map<String, String> = mapOf("app" to "demo-boot", "guard" to "on")

  /** Skip-listed from every check (the python `@bypass(["all"])` idiom). */
  @GuardBypass(checks = ["all"])
  @GetMapping("/health")
  public fun health(): Map<String, String> = mapOf("status" to "ok")

  /** Global rate limiting from guard.endpoint-rate-limits (application.yml). */
  @GetMapping("/api/limited")
  public fun limited(): Map<String, String> = mapOf("limited" to "true")

  /** Route rate limit annotation on top of the global surface. */
  @GuardRateLimit(requests = 3, window = 60)
  @GetMapping("/api/tight")
  public fun tight(): Map<String, String> = mapOf("tight" to "true")

  /** Whitelist-only route. */
  @GuardRequireIp(whitelist = ["127.0.0.1", "::1"])
  @GetMapping("/api/admin")
  public fun admin(): Map<String, String> = mapOf("admin" to "true")
}
