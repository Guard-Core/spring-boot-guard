package com.guardcore.spring

/**
 * The `guard.*` configuration-properties surface binding application.yml/properties into the
 * facade's SecurityConfig (fastapi-guard's SecurityConfig(**kwargs) construction idiom).
 *
 * Coverage: every SecurityConfig field EXCEPT the seven object-typed ones that cannot come from
 * properties (geo_ip_handler, cloud_ip_store, custom_request_check, auth_verifier,
 * custom_response_modifier, on_error, on_block) - those are wired programmatically through
 * [SecurityConfigCustomizer] beans, so all 129 fields are reachable. Property names use snake_case
 * or kebab-case alike (Spring relaxed binding: guard.redis_url == guard.redis-url) and match the
 * python field names one to one (ConfigFieldRegistry.kt).
 *
 * Unset properties stay null and the python defaults apply byte-exactly (the builder only applies
 * provided values, SecurityConfigBuilder.kt).
 */
@org.springframework.boot.context.properties.ConfigurationProperties(prefix = "guard")
public class GuardProperties {

  /** python_name: trusted_proxies. */
  public var trustedProxies: List<String>? = null

  /** python_name: trusted_proxy_depth. */
  public var trustedProxyDepth: Int? = null

  /** python_name: trust_x_forwarded_proto. */
  public var trustXForwardedProto: Boolean? = null

  /** python_name: passive_mode. */
  public var passiveMode: Boolean? = null

  /** python_name: enable_redis. */
  public var enableRedis: Boolean? = null

  /** python_name: redis_url. */
  public var redisUrl: String? = null

  /** python_name: redis_prefix. */
  public var redisPrefix: String? = null

  /** python_name: redis_socket_connect_timeout. */
  public var redisSocketConnectTimeout: Double? = null

  /** python_name: redis_socket_timeout. */
  public var redisSocketTimeout: Double? = null

  /** python_name: redis_health_check_interval. */
  public var redisHealthCheckInterval: Int? = null

  /** python_name: redis_max_connections. */
  public var redisMaxConnections: Int? = null

  /** python_name: redis_retries. */
  public var redisRetries: Int? = null

  /** python_name: whitelist. */
  public var whitelist: List<String>? = null

  /** python_name: blacklist. */
  public var blacklist: List<String>? = null

  /** python_name: exempt_ips. */
  public var exemptIps: List<String>? = null

  /** python_name: whitelist_countries. */
  public var whitelistCountries: Set<String>? = null

  /** python_name: blocked_countries. */
  public var blockedCountries: Set<String>? = null

  /** python_name: blocked_user_agents. */
  public var blockedUserAgents: List<String>? = null

  /** python_name: auto_ban_threshold. */
  public var autoBanThreshold: Int? = null

  /** python_name: auto_ban_duration. */
  public var autoBanDuration: Int? = null

  /** python_name: threat_ban_config. */
  public var threatBanConfig: Map<String, ThreatBanEntry>? = null

  /** python_name: enable_rate_limit_auto_ban. */
  public var enableRateLimitAutoBan: Boolean? = null

  /** python_name: global_behavior_rules. */
  public var globalBehaviorRules: List<BehaviorRuleEntry>? = null

  /** python_name: behavior_scan_response_body. */
  public var behaviorScanResponseBody: Boolean? = null

  /** python_name: behavior_max_response_body_inspect_bytes. */
  public var behaviorMaxResponseBodyInspectBytes: Int? = null

  /** python_name: body_read_timeout. */
  public var bodyReadTimeout: Double? = null

  /** python_name: sync_body_read_max_concurrent. */
  public var syncBodyReadMaxConcurrent: Int? = null

  /** python_name: custom_log_file. */
  public var customLogFile: String? = null

  /** python_name: log_suspicious_level. */
  public var logSuspiciousLevel: String? = null

  /** python_name: log_request_level. */
  public var logRequestLevel: String? = null

  /** python_name: log_country_check_level. */
  public var logCountryCheckLevel: String? = null

  /** python_name: log_format. */
  public var logFormat: String? = null

  /** python_name: custom_error_responses. */
  public var customErrorResponses: Map<String, String>? = null

  /** python_name: rate_limit. */
  public var rateLimit: Int? = null

  /** python_name: rate_limit_window. */
  public var rateLimitWindow: Int? = null

  /** python_name: enforce_https. */
  public var enforceHttps: Boolean? = null

  /** python_name: security_headers. */
  public var securityHeaders: Map<String, Any?>? = null

  /** python_name: enable_cors. */
  public var enableCors: Boolean? = null

  /** python_name: cors_allow_origins. */
  public var corsAllowOrigins: List<String>? = null

  /** python_name: cors_allow_methods. */
  public var corsAllowMethods: List<String>? = null

  /** python_name: cors_allow_headers. */
  public var corsAllowHeaders: List<String>? = null

  /** python_name: cors_allow_credentials. */
  public var corsAllowCredentials: Boolean? = null

  /** python_name: cors_expose_headers. */
  public var corsExposeHeaders: List<String>? = null

  /** python_name: cors_max_age. */
  public var corsMaxAge: Int? = null

  /** python_name: block_cloud_providers. */
  public var blockCloudProviders: Set<String>? = null

  /** python_name: cloud_ip_refresh_interval. */
  public var cloudIpRefreshInterval: Int? = null

  /** python_name: lazy_init. */
  public var lazyInit: Boolean? = null

  /** python_name: geo_ip_db_max_age. */
  public var geoIpDbMaxAge: Int? = null

  /** python_name: exclude_paths. */
  public var excludePaths: List<String>? = null

  /** python_name: enable_ip_banning. */
  public var enableIpBanning: Boolean? = null

  /** python_name: enable_rate_limiting. */
  public var enableRateLimiting: Boolean? = null

  /** python_name: enable_penetration_detection. */
  public var enablePenetrationDetection: Boolean? = null

  /** python_name: fail_secure. */
  public var failSecure: Boolean? = null

  /** python_name: redis_fail_open. */
  public var redisFailOpen: Boolean? = null

  /** python_name: route_resolution_strict. */
  public var routeResolutionStrict: Boolean? = null

  /** python_name: ipinfo_token. */
  public var ipinfoToken: String? = null

  /** python_name: ipinfo_db_path. */
  public var ipinfoDbPath: String? = null

  /** python_name: enable_agent. */
  public var enableAgent: Boolean? = null

  /** python_name: agent_api_key. */
  public var agentApiKey: String? = null

  /** python_name: agent_strict. */
  public var agentStrict: Boolean? = null

  /** python_name: agent_endpoint. */
  public var agentEndpoint: String? = null

  /** python_name: agent_project_id. */
  public var agentProjectId: String? = null

  /** python_name: agent_buffer_size. */
  public var agentBufferSize: Int? = null

  /** python_name: agent_flush_interval. */
  public var agentFlushInterval: Int? = null

  /** python_name: agent_enable_events. */
  public var agentEnableEvents: Boolean? = null

  /** python_name: agent_enable_metrics. */
  public var agentEnableMetrics: Boolean? = null

  /** python_name: agent_timeout. */
  public var agentTimeout: Int? = null

  /** python_name: agent_retry_attempts. */
  public var agentRetryAttempts: Int? = null

  /** python_name: agent_project_encryption_key. */
  public var agentProjectEncryptionKey: String? = null

  /** python_name: agent_guard_version. */
  public var agentGuardVersion: String? = null

  /** python_name: agent_high_watermark_ratio. */
  public var agentHighWatermarkRatio: Double? = null

  /** python_name: agent_max_concurrent_flushes. */
  public var agentMaxConcurrentFlushes: Int? = null

  /** python_name: agent_buffer_overflow_policy. */
  public var agentBufferOverflowPolicy: String? = null

  /** python_name: agent_backoff_factor. */
  public var agentBackoffFactor: Double? = null

  /** python_name: agent_sensitive_headers. */
  public var agentSensitiveHeaders: List<String>? = null

  /** python_name: agent_max_payload_size. */
  public var agentMaxPayloadSize: Int? = null

  /** python_name: agent_compression_enabled. */
  public var agentCompressionEnabled: Boolean? = null

  /** python_name: agent_compression_threshold. */
  public var agentCompressionThreshold: Int? = null

  /** python_name: agent_install_id. */
  public var agentInstallId: String? = null

  /** python_name: agent_payload_signing_secret. */
  public var agentPayloadSigningSecret: String? = null

  /** python_name: enable_dynamic_rules. */
  public var enableDynamicRules: Boolean? = null

  /** python_name: dynamic_rule_interval. */
  public var dynamicRuleInterval: Int? = null

  /** python_name: dynamic_rules_cache_path. */
  public var dynamicRulesCachePath: String? = null

  /** python_name: agent_status_interval. */
  public var agentStatusInterval: Int? = null

  /** python_name: emergency_mode. */
  public var emergencyMode: Boolean? = null

  /** python_name: emergency_whitelist. */
  public var emergencyWhitelist: List<String>? = null

  /** python_name: endpoint_rate_limits. */
  public var endpointRateLimits: Map<String, EndpointRateLimitEntry>? = null

  /** python_name: detection_compiler_timeout. */
  public var detectionCompilerTimeout: Double? = null

  /** python_name: detection_pattern_validation_cache_path. */
  public var detectionPatternValidationCachePath: String? = null

  /** python_name: detection_max_content_length. */
  public var detectionMaxContentLength: Int? = null

  /** python_name: detection_max_body_inspect_bytes. */
  public var detectionMaxBodyInspectBytes: Int? = null

  /** python_name: detection_max_scan_values. */
  public var detectionMaxScanValues: Int? = null

  /** python_name: detection_max_scan_chars. */
  public var detectionMaxScanChars: Int? = null

  /** python_name: detection_max_json_depth. */
  public var detectionMaxJsonDepth: Int? = null

  /** python_name: detection_binary_min_run_length. */
  public var detectionBinaryMinRunLength: Int? = null

  /** python_name: detection_preserve_attack_patterns. */
  public var detectionPreserveAttackPatterns: Boolean? = null

  /** python_name: detection_semantic_threshold. */
  public var detectionSemanticThreshold: Double? = null

  /** python_name: detection_anomaly_threshold. */
  public var detectionAnomalyThreshold: Double? = null

  /** python_name: detection_slow_pattern_threshold. */
  public var detectionSlowPatternThreshold: Double? = null

  /** python_name: detection_monitor_history_size. */
  public var detectionMonitorHistorySize: Int? = null

  /** python_name: detection_max_tracked_patterns. */
  public var detectionMaxTrackedPatterns: Int? = null

  /** python_name: detection_anomaly_emission_cooldown. */
  public var detectionAnomalyEmissionCooldown: Double? = null

  /** python_name: detection_min_samples_for_anomaly. */
  public var detectionMinSamplesForAnomaly: Int? = null

  /** python_name: detection_threat_score_threshold. */
  public var detectionThreatScoreThreshold: Double? = null

  /** python_name: muted_event_types. */
  public var mutedEventTypes: Set<String>? = null

  /** python_name: muted_metric_types. */
  public var mutedMetricTypes: Set<String>? = null

  /** python_name: muted_check_logs. */
  public var mutedCheckLogs: Set<String>? = null

  /** python_name: log_sensitive_headers. */
  public var logSensitiveHeaders: Set<String>? = null

  /** python_name: log_sensitive_params. */
  public var logSensitiveParams: Set<String>? = null

  /** python_name: log_sensitive_body_fields. */
  public var logSensitiveBodyFields: Set<String>? = null

  /** python_name: enable_otel. */
  public var enableOtel: Boolean? = null

  /** python_name: otel_service_name. */
  public var otelServiceName: String? = null

  /** python_name: otel_exporter_endpoint. */
  public var otelExporterEndpoint: String? = null

  /** python_name: otel_resource_attributes. */
  public var otelResourceAttributes: Map<String, String>? = null

  /** python_name: enable_logfire. */
  public var enableLogfire: Boolean? = null

  /** python_name: logfire_service_name. */
  public var logfireServiceName: String? = null

  /** python_name: enable_enrichment. */
  public var enableEnrichment: Boolean? = null

  /** python_name: excluded_detection_headers. */
  public var excludedDetectionHeaders: Set<String>? = null

  /** python_name: excluded_detection_params. */
  public var excludedDetectionParams: Set<String>? = null

  /** python_name: excluded_detection_body_fields. */
  public var excludedDetectionBodyFields: Set<String>? = null

  /** python_name: detection_scan_body. */
  public var detectionScanBody: Boolean? = null

  /** python_name: enabled_detection_categories. */
  public var enabledDetectionCategories: Set<String>? = null

  /** One `guard.threat-ban-config.<category>` entry (python ThreatBanConfig dict value). */
  public class ThreatBanEntry {
    public var threshold: Int = 0

    public var duration: Int = 0
  }

  /** One `guard.endpoint-rate-limits.<path>` entry (python (requests, window) tuple value). */
  public class EndpointRateLimitEntry {
    public var requests: Int = 0

    public var window: Int = 60
  }

  /** One `guard.global-behavior-rules[n]` entry (python BehaviorRuleConfig dict). */
  public class BehaviorRuleEntry {
    public var ruleType: String = "usage"

    public var threshold: Int = 1

    public var window: Int = 3600

    public var pattern: String? = null

    public var action: String = "log"

    public var banDuration: Int? = null

    public var correlateWithDetection: Boolean = false
  }
}
