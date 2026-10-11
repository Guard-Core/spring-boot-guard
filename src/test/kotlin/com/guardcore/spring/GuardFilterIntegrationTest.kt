package com.guardcore.spring

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * The adapter test surface: pipeline invoked per request through MockMvc, error response shapes
 * byte-matched against the facade factory (status/body/headers), bypass routes, exclude paths,
 * decorator annotation resolution, and the response-exit path (security headers on 200s).
 */
@SpringBootTest(
    classes = [GuardTestApp::class],
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
)
@AutoConfigureMockMvc
@TestPropertySource(
    properties =
        [
            "guard.enable-redis=false",
            "guard.blocked-user-agents[0]=BadBot",
            "guard.exclude-paths[0]=/public",
            "guard.custom-error-responses.403=Denied by guard policy",
        ],
)
public class GuardFilterIntegrationTest {
  @Autowired public lateinit var mockMvc: MockMvc

  @Autowired public lateinit var middleware: GuardMiddleware

  private fun uniqueRequest(
      ip: String,
  ): org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder =
      get("/api/open").with {
        it.remoteAddr = ip
        it
      }

  @Test
  public fun `open route passes with security headers applied on the way out`() {
    mockMvc
        .perform(uniqueRequest("192.0.2.11"))
        .andExpect(status().isOk)
        .andExpect(content().json("""{"ok":"true"}"""))
        .andExpect(header().string("X-Content-Type-Options", "nosniff"))
        .andExpect(header().string("X-Frame-Options", "SAMEORIGIN"))
        .andExpect(header().string("X-XSS-Protection", "1; mode=block"))
        .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
  }

  @Test
  public fun `blocked user agent produces the facade factory error shape`() {
    mockMvc
        .perform(get("/api/echo").header("User-Agent", "BadBot/1.0"))
        .andExpect(status().isForbidden)
        .andExpect(content().string("Denied by guard policy"))
        .andExpect(content().contentType("text/plain; charset=utf-8"))
        .andExpect(header().string("X-Content-Type-Options", "nosniff"))
  }

  @Test
  public fun `suspicious payload is blocked by penetration detection with the factory shape`() {
    mockMvc
        .perform(
            get("/api/echo")
                .queryParam("q", "1 UNION SELECT username, password FROM users")
                .header("User-Agent", "Mozilla/5.0"),
        )
        .andExpect(status().isBadRequest)
        .andExpect(content().string("Suspicious activity detected"))
  }

  @Test
  public fun `excluded path is passthrough - violations logged not blocked`() {
    mockMvc
        .perform(
            get("/public/anything").header("User-Agent", "BadBot/1.0"),
        )
        .andExpect(status().isNotFound)
  }

  @Test
  public fun `route-level bypass annotation skips every check`() {
    mockMvc
        .perform(
            get("/api/bypassed").header("User-Agent", "BadBot/1.0"),
        )
        .andExpect(status().isOk)
        .andExpect(content().json("""{"bypassed":"true"}"""))
  }

  @Test
  public fun `require https annotation redirects http requests with a scheme-swapped location`() {
    mockMvc
        .perform(get("/api/https-only"))
        .andExpect(status().isMovedPermanently)
        .andExpect(header().string("Location", "https://localhost/api/https-only"))
  }

  @Test
  public fun `require ip annotation denies clients outside the whitelist`() {
    mockMvc
        .perform(
            get("/api/admin").with {
              it.remoteAddr = "203.0.113.9"
              it
            },
        )
        .andExpect(status().isForbidden)
        .andExpect(content().string("Denied by guard policy"))
  }

  @Test
  public fun `require ip annotation allows whitelisted clients`() {
    mockMvc
        .perform(
            get("/api/admin").with {
              it.remoteAddr = "10.0.0.1"
              it
            },
        )
        .andExpect(status().isOk)
  }

  @Test
  public fun `max request size annotation rejects oversized bodies with 413`() {
    mockMvc
        .perform(
            post("/api/upload")
                .contentType("application/json")
                .content("""{"padding":"${"x".repeat(64)}"}"""),
        )
        .andExpect(status().isPayloadTooLarge)
        .andExpect(content().string("Request too large"))
  }

  @Test
  public fun `api key auth annotation requires the configured header`() {
    // required-headers presence gate runs before authentication (python check order) -> 400.
    mockMvc.perform(get("/api/keyed")).andExpect(status().isBadRequest)

    mockMvc.perform(get("/api/keyed").header("X-API-Key", "secret")).andExpect(status().isOk)
  }

  @Test
  public fun `require headers annotation enforces the declared header pair`() {
    mockMvc.perform(get("/api/tenant")).andExpect(status().isBadRequest)

    mockMvc.perform(get("/api/tenant").header("X-Tenant", "other")).andExpect(status().isBadRequest)

    mockMvc.perform(get("/api/tenant").header("X-Tenant", "acme")).andExpect(status().isOk)
  }

  @Test
  public fun `block user agents annotation blocks the declared pattern on its route only`() {
    mockMvc
        .perform(get("/api/no-scanners").header("User-Agent", "InternalScanner/2.0"))
        .andExpect(status().isForbidden)

    mockMvc
        .perform(
            get("/api/open").header("User-Agent", "InternalScanner/2.0").with {
              it.remoteAddr = "192.0.2.12"
              it
            },
        )
        .andExpect(status().isOk)
  }

  @Test
  public fun `usage monitor annotation bans the client after the threshold and the ban blocks`() {
    // Unique IP per run: the in-memory ban singleton outlives individual reruns in one JVM.
    val ip = "192.0.2.${(System.nanoTime() % 200) + 10}"
    for (call in 1..3) {
      mockMvc
          .perform(
              get("/api/monitored").with {
                it.remoteAddr = ip
                it
              },
          )
          .andExpect(status().isOk)
      assertThat(call).isLessThanOrEqualTo(3)
    }
    mockMvc
        .perform(
            get("/api/monitored").with {
              it.remoteAddr = ip
              it
            },
        )
        .andExpect(status().isForbidden)
        .andExpect(content().string("Denied by guard policy"))
  }

  @Test
  public fun `time window annotation covering the whole day allows requests`() {
    mockMvc
        .perform(
            get("/api/anytime").with {
              it.remoteAddr = "192.0.2.31"
              it
            },
        )
        .andExpect(status().isOk)
  }

  @Test
  public fun `honeypot detection annotation rejects filled trap fields with 403`() {
    mockMvc
        .perform(
            post("/api/contact")
                .contentType("application/json")
                .content("""{"name":"a","website_url":"http://spam.example"}"""),
        )
        .andExpect(status().isForbidden)
        .andExpect(content().string("Forbidden"))
  }

  @Test
  public fun `honeypot detection annotation allows clean payloads`() {
    mockMvc
        .perform(
            post("/api/contact")
                .contentType("application/json")
                .content("""{"name":"a","message":"hi"}"""),
        )
        .andExpect(status().isOk)
  }

  @Test
  public fun `request body reaches the handler after the pipeline scanned it`() {
    mockMvc
        .perform(
            post("/api/search").contentType("application/json").content("""{"q":"safe value"}"""),
        )
        .andExpect(status().isOk)
        .andExpect(content().json("""{"query":"safe value"}"""))
  }

  @Test
  public fun `middleware initializes through the pipeline with the fixed check order`() {
    val pipeline = middleware.securityPipeline
    assertThat(pipeline).isNotNull
    assertThat(pipeline!!.getCheckNames().first()).isEqualTo("route_config")
  }
}

/** The passive-mode variant: violations are logged, requests pass through. */
@SpringBootTest(
    classes = [PassiveGuardTestApp::class],
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
)
@AutoConfigureMockMvc
@TestPropertySource(
    properties =
        [
            "guard.enable-redis=false",
            "guard.passive-mode=true",
            "guard.blocked-user-agents[0]=BadBot",
            "guard.log-suspicious-level=CRITICAL",
        ],
)
public class PassiveModeIntegrationTest {
  @Autowired public lateinit var mockMvc: MockMvc

  @Test
  public fun `passive mode logs blocked user agents but lets the request through`() {
    mockMvc
        .perform(get("/api/echo").header("User-Agent", "BadBot/1.0"))
        .andExpect(status().isOk)
        .andExpect(content().json("""{"echo":"true"}"""))
  }
}
