package com.guardcore.spring

import com.guardcore.config.SecurityConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.WebApplicationContextRunner
import org.springframework.boot.web.servlet.context.AnnotationConfigServletWebApplicationContext

/**
 * The properties-binding surface: `guard.*` -> SecurityConfig across the full field inventory
 * (ConfigFieldRegistry.kt: 129 fields; 122 property-bindable + 7 programmatic through
 * SecurityConfigCustomizer, all reachable).
 */
public class GuardPropertiesBindingTest {
  private val runner: WebApplicationContextRunner = WebApplicationContextRunner {
    AnnotationConfigServletWebApplicationContext()
  }
      .withConfiguration(AutoConfigurations.of(GuardSecurityAutoConfiguration::class.java))

  @Test
  public fun `defaults match the python defaults when no properties are set`() {
    runner.run { context ->
      assertThat(context).hasSingleBean(SecurityConfig::class.java)
      val config = context.getBean(SecurityConfig::class.java)
      assertThat(config.enableRedis).isTrue()
      assertThat(config.redisUrl).isEqualTo("redis://localhost:6379")
      assertThat(config.redisPrefix).isEqualTo("guard_core:")
      assertThat(config.rateLimit).isEqualTo(10)
      assertThat(config.rateLimitWindow).isEqualTo(60)
      assertThat(config.failSecure).isTrue()
      assertThat(config.lazyInit).isTrue()
      assertThat(config.logFormat.toString()).isEqualTo("TEXT")
      assertThat(config.excludePaths)
          .containsExactly(
              "/docs",
              "/redoc",
              "/openapi.json",
              "/openapi.yaml",
              "/favicon.ico",
              "/static",
          )
      assertThat(config.securityHeaders).isNotNull
      assertThat(config.securityHeaders!!["enabled"]).isEqualTo(true)
    }
  }

  @Test
  public fun `a wide properties binding reaches every bindable field kind`() {
    runner
        .withPropertyValues(
            // IP_LIST / IP_LIST_NULLABLE
            "guard.trusted-proxies[0]=10.0.0.0/8",
            "guard.blacklist[0]=192.0.2.4",
            "guard.exempt-ips[0]=192.0.2.9",
            "guard.whitelist[0]=203.0.113.6",
            // INT / INT_NULLABLE / FLOAT / FLOAT_NULLABLE
            "guard.trusted-proxy-depth=3",
            "guard.redis-health-check-interval=15",
            "guard.redis-retries=2",
            "guard.auto-ban-threshold=7",
            "guard.auto-ban-duration=1200",
            "guard.behavior-max-response-body-inspect-bytes=1024",
            "guard.sync-body-read-max-concurrent=8",
            "guard.rate-limit=25",
            "guard.rate-limit-window=120",
            "guard.cors-max-age=300",
            "guard.cloud-ip-refresh-interval=600",
            "guard.geo-ip-db-max-age=3600",
            "guard.agent-buffer-size=42",
            "guard.agent-flush-interval=10",
            "guard.agent-timeout=5",
            "guard.agent-retry-attempts=2",
            "guard.dynamic-rule-interval=60",
            "guard.agent-status-interval=120",
            "guard.detection-max-content-length=2048",
            "guard.detection-max-body-inspect-bytes=4096",
            "guard.detection-max-scan-values=64",
            "guard.detection-max-scan-chars=8192",
            "guard.detection-max-json-depth=8",
            "guard.detection-binary-min-run-length=12",
            "guard.detection-monitor-history-size=100",
            "guard.detection-max-tracked-patterns=100",
            "guard.detection-min-samples-for-anomaly=10",
            "guard.redis-max-connections=4",
            "guard.agent-max-concurrent-flushes=2",
            "guard.agent-max-payload-size=2048",
            "guard.agent-compression-threshold=128",
            "guard.body-read-timeout=1.5",
            "guard.detection-compiler-timeout=1.0",
            "guard.detection-semantic-threshold=0.5",
            "guard.detection-anomaly-threshold=2.5",
            "guard.detection-slow-pattern-threshold=0.2",
            "guard.detection-anomaly-emission-cooldown=30.0",
            "guard.detection-threat-score-threshold=0.9",
            "guard.redis-socket-connect-timeout=1.25",
            "guard.redis-socket-timeout=1.75",
            "guard.agent-high-watermark-ratio=0.8",
            "guard.agent-backoff-factor=1.5",
            // BOOL / BOOL_NULLABLE
            "guard.trust-x-forwarded-proto=true",
            "guard.passive-mode=true",
            "guard.enable-redis=false",
            "guard.enable-rate-limit-auto-ban=true",
            "guard.behavior-scan-response-body=true",
            "guard.enforce-https=true",
            "guard.enable-cors=true",
            "guard.cors-allow-credentials=true",
            "guard.lazy-init=false",
            "guard.enable-ip-banning=false",
            "guard.enable-rate-limiting=false",
            "guard.enable-penetration-detection=false",
            "guard.fail-secure=false",
            "guard.redis-fail-open=true",
            "guard.route-resolution-strict=true",
            "guard.enable-agent=true",
            "guard.agent-strict=false",
            "guard.agent-enable-events=false",
            "guard.agent-enable-metrics=false",
            "guard.enable-dynamic-rules=true",
            "guard.emergency-mode=true",
            "guard.detection-preserve-attack-patterns=false",
            "guard.enable-otel=true",
            "guard.enable-logfire=true",
            "guard.enable-enrichment=true",
            "guard.detection-scan-body=false",
            "guard.agent-compression-enabled=false",
            // STR / STR_NULLABLE / PATH_NULLABLE
            "guard.redis-url=redis://cache:6380",
            "guard.custom-log-file=/tmp/guard-test.log",
            "guard.ipinfo-token=token123",
            "guard.agent-api-key=key",
            "guard.agent-project-id=proj",
            "guard.agent-project-encryption-key=enc",
            "guard.agent-guard-version=1.0.0",
            "guard.agent-install-id=install",
            "guard.agent-payload-signing-secret=sign",
            "guard.detection-pattern-validation-cache-path=/tmp/cache",
            "guard.otel-exporter-endpoint=http://localhost:4317",
            "guard.agent-endpoint=https://agent.example",
            "guard.otel-service-name=guard-sb",
            "guard.logfire-service-name=guard-sb-lf",
            "guard.ipinfo-db-path=/tmp/geo.mmdb",
            "guard.dynamic-rules-cache-path=/tmp/rules.json",
            // Enum-by-name
            "guard.log-suspicious-level=ERROR",
            "guard.log-request-level=DEBUG",
            "guard.log-country-check-level=WARNING",
            "guard.log-format=json",
            "guard.agent-buffer-overflow-policy=block",
            // STR_LIST / STR_SET / COUNTRY_SET / CLOUD_PROVIDERS_NULLABLE
            "guard.blocked-user-agents[0]=EvilBot",
            "guard.cors-allow-origins[0]=https://app.example",
            "guard.cors-allow-methods[0]=GET",
            "guard.cors-allow-headers[0]=X-Custom",
            "guard.cors-expose-headers[0]=X-Trace",
            "guard.emergency-whitelist[0]=203.0.113.8",
            "guard.agent-sensitive-headers[0]=X-Secret",
            "guard.muted-event-types[0]=ip_banned",
            "guard.muted-metric-types[0]=request_count",
            "guard.muted-check-logs[0]=user_agent",
            "guard.log-sensitive-headers[0]=authorization",
            "guard.log-sensitive-params[0]=password",
            "guard.log-sensitive-body-fields[0]=ssn",
            "guard.excluded-detection-headers[0]=x-internal",
            "guard.excluded-detection-params[0]=token",
            "guard.excluded-detection-body-fields[0]=secret",
            "guard.enabled-detection-categories[0]=sqli",
            "guard.whitelist-countries[0]=US",
            "guard.blocked-countries[0]=KP",
            "guard.block-cloud-providers[0]=AWS",
            // Dicts and nested config
            "guard.custom-error-responses.403=custom-forbidden",
            "guard.otel-resource-attributes.env=test",
            "guard.security-headers.frame_options=DENY",
            "guard.security-headers.hsts.max_age=63072000",
            "guard.endpoint-rate-limits[/api-rl].requests=5",
            "guard.endpoint-rate-limits[/api-rl].window=30",
            "guard.threat-ban-config.sqli.threshold=2",
            "guard.threat-ban-config.sqli.duration=600",
            "guard.global-behavior-rules[0].rule-type=usage",
            "guard.global-behavior-rules[0].threshold=9",
            "guard.global-behavior-rules[0].window=1800",
            "guard.global-behavior-rules[0].action=throttle",
        )
        .run { context ->
          assertThat(context).hasSingleBean(SecurityConfig::class.java)
          val c = context.getBean(SecurityConfig::class.java)

          // Lists / sets
          assertThat(c.trustedProxies).containsExactly("10.0.0.0/8")
          assertThat(c.blacklist).containsExactly("192.0.2.4")
          assertThat(c.exemptIps).containsExactly("192.0.2.9")
          assertThat(c.whitelist).containsExactly("203.0.113.6")
          assertThat(c.blockedUserAgents).containsExactly("EvilBot")
          assertThat(c.corsAllowOrigins).containsExactly("https://app.example")
          assertThat(c.corsAllowMethods).containsExactly("GET")
          assertThat(c.corsAllowHeaders).containsExactly("X-Custom")
          assertThat(c.corsExposeHeaders).containsExactly("X-Trace")
          assertThat(c.emergencyWhitelist).containsExactly("203.0.113.8")
          assertThat(c.agentSensitiveHeaders).containsExactly("X-Secret")
          assertThat(c.mutedEventTypes).containsExactly("ip_banned")
          assertThat(c.mutedMetricTypes).containsExactly("request_count")
          assertThat(c.mutedCheckLogs).containsExactly("user_agent")
          assertThat(c.logSensitiveHeaders).containsExactly("authorization")
          assertThat(c.logSensitiveParams).containsExactly("password")
          assertThat(c.logSensitiveBodyFields).containsExactly("ssn")
          assertThat(c.excludedDetectionHeaders).containsExactly("x-internal")
          assertThat(c.excludedDetectionParams).containsExactly("token")
          assertThat(c.excludedDetectionBodyFields).containsExactly("secret")
          assertThat(c.enabledDetectionCategories).containsExactly("sqli")
          assertThat(c.whitelistCountries).containsExactly("US")
          assertThat(c.blockedCountries).containsExactly("KP")
          assertThat(c.blockCloudProviders).containsExactly("AWS")

          // Ints
          assertThat(c.trustedProxyDepth).isEqualTo(3)
          assertThat(c.redisHealthCheckInterval).isEqualTo(15)
          assertThat(c.redisRetries).isEqualTo(2)
          assertThat(c.autoBanThreshold).isEqualTo(7)
          assertThat(c.autoBanDuration).isEqualTo(1200)
          assertThat(c.behaviorMaxResponseBodyInspectBytes).isEqualTo(1024)
          assertThat(c.syncBodyReadMaxConcurrent).isEqualTo(8)
          assertThat(c.rateLimit).isEqualTo(25)
          assertThat(c.rateLimitWindow).isEqualTo(120)
          assertThat(c.corsMaxAge).isEqualTo(300)
          assertThat(c.cloudIpRefreshInterval).isEqualTo(600)
          assertThat(c.geoIpDbMaxAge).isEqualTo(3600)
          assertThat(c.agentBufferSize).isEqualTo(42)
          assertThat(c.agentFlushInterval).isEqualTo(10)
          assertThat(c.agentTimeout).isEqualTo(5)
          assertThat(c.agentRetryAttempts).isEqualTo(2)
          assertThat(c.dynamicRuleInterval).isEqualTo(60)
          assertThat(c.agentStatusInterval).isEqualTo(120)
          assertThat(c.detectionMaxContentLength).isEqualTo(2048)
          assertThat(c.detectionMaxBodyInspectBytes).isEqualTo(4096)
          assertThat(c.detectionMaxScanValues).isEqualTo(64)
          assertThat(c.detectionMaxScanChars).isEqualTo(8192)
          assertThat(c.detectionMaxJsonDepth).isEqualTo(8)
          assertThat(c.detectionBinaryMinRunLength).isEqualTo(12)
          assertThat(c.detectionMonitorHistorySize).isEqualTo(100)
          assertThat(c.detectionMaxTrackedPatterns).isEqualTo(100)
          assertThat(c.detectionMinSamplesForAnomaly).isEqualTo(10)
          assertThat(c.redisMaxConnections).isEqualTo(4)
          assertThat(c.agentMaxConcurrentFlushes).isEqualTo(2)
          assertThat(c.agentMaxPayloadSize).isEqualTo(2048)
          assertThat(c.agentCompressionThreshold).isEqualTo(128)

          // Floats
          assertThat(c.bodyReadTimeout).isEqualTo(1.5)
          assertThat(c.detectionCompilerTimeout).isEqualTo(1.0)
          assertThat(c.detectionSemanticThreshold).isEqualTo(0.5)
          assertThat(c.detectionAnomalyThreshold).isEqualTo(2.5)
          assertThat(c.detectionSlowPatternThreshold).isEqualTo(0.2)
          assertThat(c.detectionAnomalyEmissionCooldown).isEqualTo(30.0)
          assertThat(c.detectionThreatScoreThreshold).isEqualTo(0.9)
          assertThat(c.redisSocketConnectTimeout).isEqualTo(1.25)
          assertThat(c.redisSocketTimeout).isEqualTo(1.75)
          assertThat(c.agentHighWatermarkRatio).isEqualTo(0.8)
          assertThat(c.agentBackoffFactor).isEqualTo(1.5)

          // Bools
          assertThat(c.trustXForwardedProto).isTrue()
          assertThat(c.passiveMode).isTrue()
          assertThat(c.enableRedis).isFalse()
          assertThat(c.enableRateLimitAutoBan).isTrue()
          assertThat(c.behaviorScanResponseBody).isTrue()
          assertThat(c.enforceHttps).isTrue()
          assertThat(c.enableCors).isTrue()
          assertThat(c.corsAllowCredentials).isTrue()
          assertThat(c.lazyInit).isFalse()
          assertThat(c.enableIpBanning).isFalse()
          assertThat(c.enableRateLimiting).isFalse()
          assertThat(c.enablePenetrationDetection).isFalse()
          assertThat(c.failSecure).isFalse()
          assertThat(c.redisFailOpen).isTrue()
          assertThat(c.routeResolutionStrict).isTrue()
          assertThat(c.enableAgent).isTrue()
          assertThat(c.agentStrict).isFalse()
          assertThat(c.agentEnableEvents).isFalse()
          assertThat(c.agentEnableMetrics).isFalse()
          assertThat(c.enableDynamicRules).isTrue()
          assertThat(c.emergencyMode).isTrue()
          assertThat(c.detectionPreserveAttackPatterns).isFalse()
          assertThat(c.enableOtel).isTrue()
          assertThat(c.enableLogfire).isTrue()
          assertThat(c.enableEnrichment).isTrue()
          assertThat(c.detectionScanBody).isFalse()
          assertThat(c.agentCompressionEnabled).isFalse()

          // Strings / paths
          assertThat(c.redisUrl).isEqualTo("redis://cache:6380")
          assertThat(c.customLogFile).isEqualTo("/tmp/guard-test.log")
          assertThat(c.ipinfoToken).isEqualTo("token123")
          assertThat(c.agentApiKey).isEqualTo("key")
          assertThat(c.agentProjectId).isEqualTo("proj")
          assertThat(c.agentProjectEncryptionKey).isEqualTo("enc")
          assertThat(c.agentGuardVersion).isEqualTo("1.0.0")
          assertThat(c.agentInstallId).isEqualTo("install")
          assertThat(c.agentPayloadSigningSecret).isEqualTo("sign")
          assertThat(c.detectionPatternValidationCachePath).isEqualTo("/tmp/cache")
          assertThat(c.otelExporterEndpoint).isEqualTo("http://localhost:4317")
          assertThat(c.agentEndpoint).isEqualTo("https://agent.example")
          assertThat(c.otelServiceName).isEqualTo("guard-sb")
          assertThat(c.logfireServiceName).isEqualTo("guard-sb-lf")
          assertThat(c.ipinfoDbPath.toString()).isEqualTo("/tmp/geo.mmdb")
          assertThat(c.dynamicRulesCachePath.toString()).isEqualTo("/tmp/rules.json")

          // Enums by python name
          assertThat(c.logSuspiciousLevel).isEqualTo(com.guardcore.config.LogLevel.ERROR)
          assertThat(c.logRequestLevel).isEqualTo(com.guardcore.config.LogLevel.DEBUG)
          assertThat(c.logCountryCheckLevel).isEqualTo(com.guardcore.config.LogLevel.WARNING)
          assertThat(c.logFormat).isEqualTo(com.guardcore.config.LogFormat.JSON)
          assertThat(c.agentBufferOverflowPolicy)
              .isEqualTo(com.guardcore.config.BufferOverflowPolicy.BLOCK)

          // Dicts / nested
          assertThat(c.customErrorResponses[403]).isEqualTo("custom-forbidden")
          assertThat(c.otelResourceAttributes["env"]).isEqualTo("test")
          assertThat(c.securityHeaders!!["frame_options"]).isEqualTo("DENY")
          val hsts = c.securityHeaders!!["hsts"] as Map<*, *>
          assertThat(hsts["max_age"]).isEqualTo(63072000)
          assertThat(c.endpointRateLimits["/api-rl"]).isEqualTo(5 to 30)
          assertThat(c.threatBanConfig["sqli"])
              .isEqualTo(com.guardcore.config.ThreatBanConfig(2, 600))
          assertThat(c.globalBehaviorRules).hasSize(1)
          assertThat(c.globalBehaviorRules[0].ruleType.pythonName).isEqualTo("usage")
          assertThat(c.globalBehaviorRules[0].threshold).isEqualTo(9)
          assertThat(c.globalBehaviorRules[0].window).isEqualTo(1800)
          assertThat(c.globalBehaviorRules[0].action.pythonName).isEqualTo("throttle")
        }
  }

  @Test
  public fun `snake_case property names bind like kebab-case`() {
    runner
        .withPropertyValues(
            "guard.redis_url=redis://snake:6379",
            "guard.rate_limit=33",
            "guard.passive_mode=true",
        )
        .run { context ->
          val c = context.getBean(SecurityConfig::class.java)
          assertThat(c.redisUrl).isEqualTo("redis://snake:6379")
          assertThat(c.rateLimit).isEqualTo(33)
          assertThat(c.passiveMode).isTrue()
        }
  }

  @Test
  public fun `the seven object-typed fields are reachable through customizers`() {
    val customizer = SecurityConfigCustomizer { builder ->
      builder.authVerifier =
          com.guardcore.config.AuthVerifier { _, credential -> "principal:$credential" }
      builder.onError = com.guardcore.config.OnErrorHook { _, _, _ -> }
      builder.onBlock = com.guardcore.config.BlockHook { _, _ -> null }
      builder.customRequestCheck = com.guardcore.config.CustomRequestCheck { null }
      builder.customResponseModifier =
          com.guardcore.config.CustomResponseModifier { response -> response }
    }
    runner
        .withBean(
            SecurityConfigCustomizer::class.java,
            java.util.function.Supplier { customizer },
        )
        .run { context ->
          val c = context.getBean(SecurityConfig::class.java)
          assertThat(c.authVerifier).isNotNull()
          assertThat(c.onError).isNotNull()
          assertThat(c.onBlock).isNotNull()
          assertThat(c.customRequestCheck).isNotNull()
          assertThat(c.customResponseModifier).isNotNull()
        }
  }

  @Test
  public fun `custom SecurityConfig bean replaces the auto-configured one`() {
    val runner2 = WebApplicationContextRunner {
      AnnotationConfigServletWebApplicationContext()
    }
        .withConfiguration(AutoConfigurations.of(GuardSecurityAutoConfiguration::class.java))
        .withBean(
            SecurityConfig::class.java,
            java.util.function.Supplier {
              SecurityConfigBuilder().apply { passiveMode = true }.build()
            },
        )
    runner2.run { context ->
      assertThat(context).hasSingleBean(SecurityConfig::class.java)
      assertThat(context.getBean(SecurityConfig::class.java).passiveMode).isTrue()
    }
  }
}
