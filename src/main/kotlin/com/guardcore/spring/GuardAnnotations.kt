package com.guardcore.spring

/**
 * The JVM adapter idiom for the python decorator factories (specs/impl/jvm.md module mapping: "JVM
 * idiom = annotations + interceptor/plugin per adapter"): one annotation per facade
 * SecurityDecorator factory method (SecurityDecorator.kt), carrying the SAME parameter surface 1:1
 * with the same defaults.
 *
 * The python idiom is `@security_decorator.rate_limit(requests=5)` applied to an endpoint; the
 * Spring idiom is the annotation applied to a handler method. Annotation attributes must be
 * compile-time constants, so the two factory parameters that take callables
 * (`require_auth`/`api_key_auth` verifier, `custom_validation` validator) reference Spring beans by
 * name instead (bean type: com.guardcore.decorators.RouteAuthVerifier / the CustomRouteValidator
 * functional interface below).
 *
 * Behavioral factories that append rules (usage_monitor, return_monitor, behavior_analysis,
 * suspicious_frequency, custom_validation, honeypot_detection) are repeatable, matching the python
 * stacking semantics.
 */

// -------------------------------------------------- access_control (SecurityDecorator.kt:34)

/** Python `require_ip` (access_control.py:16). */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
public annotation class GuardRequireIp(
    val whitelist: Array<String> = [],
    val blacklist: Array<String> = [],
)

/** Python `block_countries` (access_control.py:30). */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
public annotation class GuardBlockCountries(val countries: Array<String>)

/** Python `allow_countries` (access_control.py:42). */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
public annotation class GuardAllowCountries(val countries: Array<String>)

/** Python `block_clouds` (access_control.py:54); empty = every known provider. */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
public annotation class GuardBlockClouds(val providers: Array<String> = [])

/** Python `bypass` (access_control.py:71). */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
public annotation class GuardBypass(val checks: Array<String>)

// -------------------------------------------------- rate_limiting (SecurityDecorator.kt:106)

/** Python `rate_limit` (rate_limiting.py:11). */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
public annotation class GuardRateLimit(val requests: Int, val window: Int = 60)

/** Python `geo_rate_limit` (rate_limiting.py:20). */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
public annotation class GuardGeoRateLimit(val limits: Array<Entry>) {
  /** One country entry: `limits = {"US": (5, 60)}` in python, `Entry` here. */
  @Target()
  @Retention(AnnotationRetention.RUNTIME)
  public annotation class Entry(
      val country: String,
      val requests: Int,
      val window: Int,
  )
}

// -------------------------------------------------- behavioral (SecurityDecorator.kt:127)

/** Python `usage_monitor` (behavioral.py:18). */
@Repeatable
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
public annotation class GuardUsageMonitor(
    val maxCalls: Int,
    val window: Int = 3600,
    val action: String = "ban",
)

/** Python `return_monitor` (behavioral.py:34). */
@Repeatable
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
public annotation class GuardReturnMonitor(
    val pattern: String,
    val maxOccurrences: Int,
    val window: Int = 86400,
    val action: String = "ban",
)

/** Python `behavior_analysis` (behavioral.py:52). */
@Repeatable
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
public annotation class GuardBehaviorAnalysis(val rules: Array<GuardBehaviorRule>)

/** Python `suspicious_frequency` (behavioral.py:70): frequency converts to threshold x window. */
@Repeatable
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
public annotation class GuardSuspiciousFrequency(
    val maxFrequency: Double,
    val window: Int = 300,
    val action: String = "ban",
)

/** One behavior rule (the BehaviorRuleConfig surface, RuleConfigs.kt). */
@Target()
@Retention(AnnotationRetention.RUNTIME)
public annotation class GuardBehaviorRule(
    val ruleType: String,
    val threshold: Int,
    val window: Int = 3600,
    val pattern: String = "",
    val action: String = "log",
    val banDuration: Int = -1,
    val correlateWithDetection: Boolean = false,
)

// -------------------------------------------------- authentication (SecurityDecorator.kt:202)

/** Python `require_https` (authentication.py:15). */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
public annotation class GuardRequireHttps

/** Python `require_auth` (authentication.py:22); verifierBean: a RouteAuthVerifier bean name. */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
public annotation class GuardRequireAuth(
    val type: String = "bearer",
    val verifierBean: String = "",
)

/** Python `api_key_auth` (authentication.py:44). */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
public annotation class GuardApiKeyAuth(
    val headerName: String = "X-API-Key",
    val verifierBean: String = "",
)

/** Python `require_authorization_header` (authentication.py:68). */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
public annotation class GuardRequireAuthorizationHeader(val scheme: String = "bearer")

/** Python `require_headers` (authentication.py:82). */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
public annotation class GuardRequireHeaders(val headers: Array<GuardRequiredHeader>)

/** One required header pair (annotation attributes cannot carry maps). */
@Target()
@Retention(AnnotationRetention.RUNTIME)
public annotation class GuardRequiredHeader(val name: String, val value: String)

// -------------------------------------------------- content_filtering (SecurityDecorator.kt:275)

/** Python `block_user_agents` (content_filtering.py:17). */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
public annotation class GuardBlockUserAgents(val patterns: Array<String>)

/** Python `content_type_filter` (content_filtering.py:40). */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
public annotation class GuardContentTypeFilter(val allowedTypes: Array<String>)

/** Python `max_request_size` (content_filtering.py:50). */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
public annotation class GuardMaxRequestSize(val sizeBytes: Int)

/** Python `require_referrer` (content_filtering.py:60). */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
public annotation class GuardRequireReferrer(val allowedDomains: Array<String>)

/**
 * Python `custom_validation` (content_filtering.py:70); validatorBean: a CustomRouteValidator bean
 * name (the annotation surface cannot carry the suspend lambda).
 */
@Repeatable
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
public annotation class GuardCustomValidation(val validatorBean: String)

/** Python `detection_exclusion` (content_filtering.py:79). */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
public annotation class GuardDetectionExclusion(
    val headers: Array<String> = [],
    val params: Array<String> = [],
    val bodyFields: Array<String> = [],
    val categories: Array<String> = [],
    val scanBody: Boolean = true,
)

// -------------------------------------------------- advanced (SecurityDecorator.kt:357)

/** Python `time_window` (advanced.py:52). */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
public annotation class GuardTimeWindow(
    val startTime: String,
    val endTime: String,
    val timezone: String = "UTC",
)

/** Python `suspicious_detection` (advanced.py:64). */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
public annotation class GuardSuspiciousDetection(val enabled: Boolean = true)

/** Python `honeypot_detection` (advanced.py:72). */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
public annotation class GuardHoneypotDetection(val trapFields: Array<String>)
