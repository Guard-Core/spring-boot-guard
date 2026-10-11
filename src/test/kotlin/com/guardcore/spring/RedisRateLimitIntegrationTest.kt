package com.guardcore.spring

import java.net.Socket
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * Redis-backed integration: rate limiting and geo rate limiting through the facade RateLimitManager
 * against the live localhost:6379 (CI wires a redis service container). Skipped when no redis is
 * reachable (documented in README), mirroring the facade test convention.
 */
@SpringBootTest(
    classes = [RedisGuardTestApp::class],
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
)
@AutoConfigureMockMvc
@TestPropertySource(
    properties =
        [
            "guard.redis-url=redis://localhost:6379",
            "guard.redis-prefix=guard-sb-it-\${random.uuid}",
            "guard.redis-retries=1",
        ],
)
public class RedisRateLimitIntegrationTest {
  @Autowired public lateinit var mockMvc: MockMvc

  @Autowired public lateinit var guardMiddleware: GuardMiddleware

  @org.junit.jupiter.api.BeforeEach
  public fun resetRateLimitState() {
    assumeTrue(redisAvailable(), "live redis on localhost:6379 required")
    kotlinx.coroutines.runBlocking { guardMiddleware.reset() }
  }

  public companion object {
    public fun redisAvailable(): Boolean = runCatching {
      Socket("localhost", 6379).use { it.isConnected }
    }
        .getOrDefault(false)
  }

  private fun asClient(
      ip: String,
  ): org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder =
      get("/api/limited").with {
        it.remoteAddr = ip
        it
      }

  @Test
  public fun `rate limit annotation blocks past the threshold with the factory shape`() {
    assumeTrue(redisAvailable(), "live redis on localhost:6379 required")
    val ip = "192.0.2.201"
    mockMvc.perform(asClient(ip)).andExpect(status().isOk)
    mockMvc.perform(asClient(ip)).andExpect(status().isOk)
    mockMvc
        .perform(asClient(ip))
        .andExpect(status().isTooManyRequests)
        .andExpect(content().string("Too many requests"))
        .andExpect { result ->
          org.assertj.core.api.Assertions.assertThat(
                  result.response.getHeader("Retry-After"),
              )
              .isEqualTo("60")
        }
  }

  @Test
  public fun `geo rate limit annotation applies the country limit`() {
    assumeTrue(redisAvailable(), "live redis on localhost:6379 required")
    val ip = "8.8.8.8"
    mockMvc
        .perform(
            get("/api/geo-limited").with {
              it.remoteAddr = ip
              it
            },
        )
        .andExpect(status().isOk)
    mockMvc
        .perform(
            get("/api/geo-limited").with {
              it.remoteAddr = ip
              it
            },
        )
        .andExpect(status().isTooManyRequests)
        .andExpect(content().string("Too many requests"))
  }

  @Test
  public fun `middleware wires the redis handler when enabled`() {
    assumeTrue(redisAvailable(), "live redis on localhost:6379 required")
    // A request drives the lazy initialization (the fastapi-guard first-request contract).
    mockMvc.perform(asClient("192.0.2.205")).andExpect(status().isOk)
    val middleware = contextMiddleware()
    assertThat(middleware.redisHandler).isNotNull
    assertThat(middleware.isInitialized()).isTrue()
  }

  private fun contextMiddleware(): GuardMiddleware = guardMiddleware
}
