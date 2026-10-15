package com.guardcore.spring

import com.guardcore.config.BehaviorRuleConfig
import com.guardcore.config.BufferOverflowPolicy
import com.guardcore.config.LogFormat
import com.guardcore.config.LogLevel
import com.guardcore.config.SecurityConfigBuilder
import com.guardcore.config.ThreatBanConfig

/**
 * Applies [GuardProperties] onto a [SecurityConfigBuilder]. Every conversion mirrors the python
 * field types: LogLevel / LogFormat / BufferOverflowPolicy by python name, paths via Paths.get,
 * endpoint rate limit tuples as (requests, window) pairs, threat-ban entries as ThreatBanConfig.
 */
public fun GuardProperties.applyTo(builder: SecurityConfigBuilder) {

  trustedProxies?.let { v -> builder.trustedProxies = v }
  trustedProxyDepth?.let { v -> builder.trustedProxyDepth = v }
  trustXForwardedProto?.let { v -> builder.trustXForwardedProto = v }
  passiveMode?.let { v -> builder.passiveMode = v }
  enableRedis?.let { v -> builder.enableRedis = v }
  redisUrl?.let { v -> builder.redisUrl = v }
  redisPrefix?.let { v -> builder.redisPrefix = v }
  redisSocketConnectTimeout?.let { v -> builder.redisSocketConnectTimeout = v }
  redisSocketTimeout?.let { v -> builder.redisSocketTimeout = v }
  redisHealthCheckInterval?.let { v -> builder.redisHealthCheckInterval = v }
  redisMaxConnections?.let { v -> builder.redisMaxConnections = v }
  redisRetries?.let { v -> builder.redisRetries = v }
  whitelist?.let { v -> builder.whitelist = v }
  blacklist?.let { v -> builder.blacklist = v }
  exemptIps?.let { v -> builder.exemptIps = v }
  whitelistCountries?.let { v -> builder.whitelistCountries = v }
  blockedCountries?.let { v -> builder.blockedCountries = v }
  blockedUserAgents?.let { v -> builder.blockedUserAgents = v }
  autoBanThreshold?.let { v -> builder.autoBanThreshold = v }
  autoBanDuration?.let { v -> builder.autoBanDuration = v }
  threatBanConfig?.let { v ->
    builder.threatBanConfig =
        v.entries.associate { (k, e) -> k to ThreatBanConfig(e.threshold, e.duration) }
  }
  enableRateLimitAutoBan?.let { v -> builder.enableRateLimitAutoBan = v }
  globalBehaviorRules?.let { v ->
    builder.globalBehaviorRules = v.map { it.toBehaviorRuleConfig() }
  }
  behaviorScanResponseBody?.let { v -> builder.behaviorScanResponseBody = v }
  behaviorMaxResponseBodyInspectBytes?.let { v -> builder.behaviorMaxResponseBodyInspectBytes = v }
  bodyReadTimeout?.let { v -> builder.bodyReadTimeout = v }
  syncBodyReadMaxConcurrent?.let { v -> builder.syncBodyReadMaxConcurrent = v }
  customLogFile?.let { v -> builder.customLogFile = v }
  logSuspiciousLevel?.let { v ->
    builder.logSuspiciousLevel = LogLevel.entries.first { it.pythonName == v.uppercase() }
  }
  logRequestLevel?.let { v ->
    builder.logRequestLevel = LogLevel.entries.first { it.pythonName == v.uppercase() }
  }
  logCountryCheckLevel?.let { v ->
    builder.logCountryCheckLevel = LogLevel.entries.first { it.pythonName == v.uppercase() }
  }
  logFormat?.let { v ->
    builder.logFormat = LogFormat.entries.first { it.pythonName == v.lowercase() }
  }
  customErrorResponses?.let { v ->
    builder.customErrorResponses = v.entries.associate { (k, value) -> k.toInt() to value }
  }
  rateLimit?.let { v -> builder.rateLimit = v }
  rateLimitWindow?.let { v -> builder.rateLimitWindow = v }
  enforceHttps?.let { v -> builder.enforceHttps = v }
  securityHeaders?.let { v -> builder.securityHeaders = normalizeSecurityHeaders(v) }
  enableCors?.let { v -> builder.enableCors = v }
  corsAllowOrigins?.let { v -> builder.corsAllowOrigins = v }
  corsAllowMethods?.let { v -> builder.corsAllowMethods = v }
  corsAllowHeaders?.let { v -> builder.corsAllowHeaders = v }
  corsAllowCredentials?.let { v -> builder.corsAllowCredentials = v }
  corsExposeHeaders?.let { v -> builder.corsExposeHeaders = v }
  corsMaxAge?.let { v -> builder.corsMaxAge = v }
  blockCloudProviders?.let { v -> builder.blockCloudProviders = v }
  cloudIpRefreshInterval?.let { v -> builder.cloudIpRefreshInterval = v }
  lazyInit?.let { v -> builder.lazyInit = v }
  geoIpDbMaxAge?.let { v -> builder.geoIpDbMaxAge = v }
  excludePaths?.let { v -> builder.excludePaths = v }
  enableIpBanning?.let { v -> builder.enableIpBanning = v }
  enableRateLimiting?.let { v -> builder.enableRateLimiting = v }
  enablePenetrationDetection?.let { v -> builder.enablePenetrationDetection = v }
  failSecure?.let { v -> builder.failSecure = v }
  redisFailOpen?.let { v -> builder.redisFailOpen = v }
  routeResolutionStrict?.let { v -> builder.routeResolutionStrict = v }
  ipinfoToken?.let { v -> builder.ipinfoToken = v }
  ipinfoDbPath?.let { v -> builder.ipinfoDbPath = java.nio.file.Paths.get(v) }
  enableAgent?.let { v -> builder.enableAgent = v }
  agentApiKey?.let { v -> builder.agentApiKey = v }
  agentStrict?.let { v -> builder.agentStrict = v }
  agentEndpoint?.let { v -> builder.agentEndpoint = v }
  agentProjectId?.let { v -> builder.agentProjectId = v }
  agentBufferSize?.let { v -> builder.agentBufferSize = v }
  agentFlushInterval?.let { v -> builder.agentFlushInterval = v }
  agentEnableEvents?.let { v -> builder.agentEnableEvents = v }
  agentEnableMetrics?.let { v -> builder.agentEnableMetrics = v }
  agentTimeout?.let { v -> builder.agentTimeout = v }
  agentRetryAttempts?.let { v -> builder.agentRetryAttempts = v }
  agentProjectEncryptionKey?.let { v -> builder.agentProjectEncryptionKey = v }
  agentGuardVersion?.let { v -> builder.agentGuardVersion = v }
  agentHighWatermarkRatio?.let { v -> builder.agentHighWatermarkRatio = v }
  agentMaxConcurrentFlushes?.let { v -> builder.agentMaxConcurrentFlushes = v }
  agentBufferOverflowPolicy?.let { v ->
    builder.agentBufferOverflowPolicy =
        BufferOverflowPolicy.entries.first { it.pythonName == v.lowercase() }
  }
  agentBackoffFactor?.let { v -> builder.agentBackoffFactor = v }
  agentSensitiveHeaders?.let { v -> builder.agentSensitiveHeaders = v }
  agentMaxPayloadSize?.let { v -> builder.agentMaxPayloadSize = v }
  agentCompressionEnabled?.let { v -> builder.agentCompressionEnabled = v }
  agentCompressionThreshold?.let { v -> builder.agentCompressionThreshold = v }
  agentInstallId?.let { v -> builder.agentInstallId = v }
  agentPayloadSigningSecret?.let { v -> builder.agentPayloadSigningSecret = v }
  enableDynamicRules?.let { v -> builder.enableDynamicRules = v }
  dynamicRuleInterval?.let { v -> builder.dynamicRuleInterval = v }
  dynamicRulesCachePath?.let { v -> builder.dynamicRulesCachePath = java.nio.file.Paths.get(v) }
  agentStatusInterval?.let { v -> builder.agentStatusInterval = v }
  emergencyMode?.let { v -> builder.emergencyMode = v }
  emergencyWhitelist?.let { v -> builder.emergencyWhitelist = v }
  endpointRateLimits?.let { v ->
    builder.endpointRateLimits = v.entries.associate { (k, e) -> k to (e.requests to e.window) }
  }
  detectionCompilerTimeout?.let { v -> builder.detectionCompilerTimeout = v }
  detectionPatternValidationCachePath?.let { v -> builder.detectionPatternValidationCachePath = v }
  detectionMaxContentLength?.let { v -> builder.detectionMaxContentLength = v }
  detectionMaxBodyInspectBytes?.let { v -> builder.detectionMaxBodyInspectBytes = v }
  detectionMaxScanValues?.let { v -> builder.detectionMaxScanValues = v }
  detectionMaxScanChars?.let { v -> builder.detectionMaxScanChars = v }
  detectionMaxScanSeconds?.let { v -> builder.detectionMaxScanSeconds = v }
  detectionMaxJsonDepth?.let { v -> builder.detectionMaxJsonDepth = v }
  detectionBinaryMinRunLength?.let { v -> builder.detectionBinaryMinRunLength = v }
  detectionPreserveAttackPatterns?.let { v -> builder.detectionPreserveAttackPatterns = v }
  detectionSemanticThreshold?.let { v -> builder.detectionSemanticThreshold = v }
  detectionAnomalyThreshold?.let { v -> builder.detectionAnomalyThreshold = v }
  detectionSlowPatternThreshold?.let { v -> builder.detectionSlowPatternThreshold = v }
  detectionMonitorHistorySize?.let { v -> builder.detectionMonitorHistorySize = v }
  detectionMaxTrackedPatterns?.let { v -> builder.detectionMaxTrackedPatterns = v }
  detectionAnomalyEmissionCooldown?.let { v -> builder.detectionAnomalyEmissionCooldown = v }
  detectionMinSamplesForAnomaly?.let { v -> builder.detectionMinSamplesForAnomaly = v }
  detectionThreatScoreThreshold?.let { v -> builder.detectionThreatScoreThreshold = v }
  mutedEventTypes?.let { v -> builder.mutedEventTypes = v }
  mutedMetricTypes?.let { v -> builder.mutedMetricTypes = v }
  mutedCheckLogs?.let { v -> builder.mutedCheckLogs = v }
  logSensitiveHeaders?.let { v -> builder.logSensitiveHeaders = v }
  logSensitiveParams?.let { v -> builder.logSensitiveParams = v }
  logSensitiveBodyFields?.let { v -> builder.logSensitiveBodyFields = v }
  enableOtel?.let { v -> builder.enableOtel = v }
  otelServiceName?.let { v -> builder.otelServiceName = v }
  otelExporterEndpoint?.let { v -> builder.otelExporterEndpoint = v }
  otelResourceAttributes?.let { v -> builder.otelResourceAttributes = v }
  enableLogfire?.let { v -> builder.enableLogfire = v }
  logfireServiceName?.let { v -> builder.logfireServiceName = v }
  enableEnrichment?.let { v -> builder.enableEnrichment = v }
  excludedDetectionHeaders?.let { v -> builder.excludedDetectionHeaders = v }
  excludedSemanticDetectionHeaders?.let { v -> builder.excludedSemanticDetectionHeaders = v }
  excludedDetectionParams?.let { v -> builder.excludedDetectionParams = v }
  excludedDetectionBodyFields?.let { v -> builder.excludedDetectionBodyFields = v }
  detectionScanBody?.let { v -> builder.detectionScanBody = v }
  enabledDetectionCategories?.let { v -> builder.enabledDetectionCategories = v }
}

/** Recursive scalar normalization for the `guard.security-headers` tree. */
private fun normalizeSecurityHeaders(source: Map<String, Any?>): Map<String, Any?> {
  val result = linkedMapOf<String, Any?>()
  for ((key, value) in source) {
    result[key] =
        when (value) {
          is Map<*, *> -> {
            @Suppress("UNCHECKED_CAST") normalizeSecurityHeaders(value as Map<String, Any?>)
          }
          is String -> normalizeScalar(value)
          else -> value
        }
  }
  return result
}

private fun normalizeScalar(value: String): Any? {
  if (value.equals("true", ignoreCase = true)) return true
  if (value.equals("false", ignoreCase = true)) return false
  if (value.matches(Regex("-?[0-9]+"))) {
    return value.toLongOrNull()?.let { if (it in Int.MIN_VALUE..Int.MAX_VALUE) it.toInt() else it }
  }
  return value
}

private fun GuardProperties.BehaviorRuleEntry.toBehaviorRuleConfig(): BehaviorRuleConfig =
    BehaviorRuleConfig(
        ruleType = com.guardcore.config.RuleType.entries.first { it.pythonName == ruleType },
        threshold = threshold,
        window = window,
        pattern = pattern,
        action = com.guardcore.config.RuleAction.entries.first { it.pythonName == action },
        banDuration = banDuration,
        correlateWithDetection = correlateWithDetection,
    )
