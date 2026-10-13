package com.guardcore.spring

import com.guardcore.config.SecurityConfig
import com.guardcore.config.SecurityConfigBuilder
import com.guardcore.core.events.AgentTelemetryMember
import com.guardcore.protocols.AgentHandlerProtocol
import com.guardcore.protocols.RedisHandlerProtocol
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/** The guard-agent-jvm GuardAgentTelemetry shape: a facade protocol and a composite member. */
public class RecordingTelemetry : AgentHandlerProtocol, AgentTelemetryMember {
  public var starts: Int = 0
  public var stops: Int = 0
  public val events: MutableList<Map<String, Any?>> = mutableListOf()
  public val metrics: MutableList<Map<String, Any?>> = mutableListOf()

  override suspend fun initializeRedis(redisHandler: RedisHandlerProtocol) {}

  override suspend fun initializeRedis(redisHandler: Any?) {}

  override suspend fun sendEvent(event: Map<String, Any?>) {
    events.add(event)
  }

  override suspend fun sendMetric(metric: Map<String, Any?>) {
    metrics.add(metric)
  }

  @Suppress("UNCHECKED_CAST")
  override suspend fun sendEvent(event: Any?) {
    (event as? Map<String, Any?>)?.let { events.add(it) }
  }

  @Suppress("UNCHECKED_CAST")
  override suspend fun sendMetric(metric: Any?) {
    (metric as? Map<String, Any?>)?.let { metrics.add(it) }
  }

  override suspend fun start() {
    starts++
  }

  override suspend fun stop() {
    stops++
  }

  override suspend fun flushBuffer() {}

  override suspend fun getDynamicRules(): Any? = null

  override suspend fun healthCheck(): Boolean = true
}

public class GuardAgentWiringTest {
  private fun agentConfig(): SecurityConfig =
      SecurityConfigBuilder()
          .apply {
            enableRedis = false
            enableAgent = true
            agentApiKey = "test-key"
          }
          .build()

  @Test
  public fun `enable_agent without a registered handler degrades`() {
    val middleware = GuardMiddleware(agentConfig())
    assertThat(middleware.agentDegraded).isTrue()
    assertThat(middleware.agentHandler).isNull()
  }

  @Test
  public fun `an injected handler is started, receives events and metrics, and stops on shutdown`() {
    val telemetry = RecordingTelemetry()
    val middleware = GuardMiddleware(agentConfig(), injectedAgentHandler = telemetry)
    assertThat(middleware.agentDegraded).isFalse()
    assertThat(middleware.agentHandler).isSameAs(telemetry)

    runBlocking {
      middleware.initialize()
      middleware.agentHandler!!.sendEvent(linkedMapOf<String, Any?>("event_type" to "ip_banned"))
      middleware.metricsCollector.sendMetric("response_time", 1.0)
    }
    assertThat(telemetry.starts).isEqualTo(1)
    assertThat(telemetry.events.map { it["event_type"] }).containsExactly("ip_banned")
    assertThat(telemetry.metrics.map { it["metric_type"] }).containsExactly("response_time")

    middleware.shutdown()
    assertThat(telemetry.stops).isEqualTo(1)
  }
}

@TestConfiguration
public open class AgentBeanConfig {
  @Bean public open fun recordingTelemetry(): RecordingTelemetry = RecordingTelemetry()
}

@SpringBootTest(
    classes = [GuardTestApp::class, AgentBeanConfig::class],
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
)
@AutoConfigureMockMvc
@TestPropertySource(
    properties =
        [
            "guard.enable-redis=false",
            "guard.enable-agent=true",
            "guard.agent-api-key=test-key",
            "guard.blocked-user-agents[0]=BadBot",
        ],
)
public class GuardAgentBeanIntegrationTest {
  @Autowired public lateinit var mockMvc: MockMvc

  @Autowired public lateinit var middleware: GuardMiddleware

  @Autowired public lateinit var telemetry: RecordingTelemetry

  @Test
  public fun `a registered AgentHandlerProtocol bean receives the events of blocked requests`() {
    mockMvc
        .perform(get("/api/echo").header("User-Agent", "BadBot/1.0"))
        .andExpect(status().isForbidden)

    assertThat(middleware.injectedAgentHandler).isSameAs(telemetry)
    assertThat(middleware.agentDegraded).isFalse()
    assertThat(telemetry.starts).isEqualTo(1)
    assertThat(telemetry.events.map { it["event_type"] as? String }).anyMatch {
      !it.isNullOrBlank()
    }
  }
}
