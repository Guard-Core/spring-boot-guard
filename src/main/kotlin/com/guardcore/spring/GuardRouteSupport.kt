package com.guardcore.spring

import com.guardcore.config.BehaviorRuleConfig
import com.guardcore.config.RuleAction
import com.guardcore.config.RuleType
import com.guardcore.decorators.RouteAuthVerifier
import com.guardcore.decorators.SecurityDecorator
import com.guardcore.decorators.StampedRoute
import com.guardcore.handlers.BehaviorRule
import com.guardcore.handlers.configToRule
import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.core.annotation.AnnotatedElementUtils
import org.springframework.web.method.HandlerMethod

/**
 * Route identity (the StampedRoute analog, BaseSecurityDecorator.kt:214): JVM method identity is
 * naturally strong, so the route key is the handler method's declaring class + name + erased
 * parameter types. This removes the python `module.qualname` collision class entirely (the W4b
 * route-id study, guard-core-jvm#9) - two methods cannot share an erased descriptor within one
 * class.
 */
public class HandlerRouteKey(public val handlerMethod: HandlerMethod) : StampedRoute {
  override val routeKey: String = buildString {
    append(handlerMethod.beanType.name)
    append('.')
    append(handlerMethod.method.name)
    append('(')
    append(handlerMethod.method.parameterTypes.joinToString(",") { it.name })
    append(')')
  }

  override var stampedRouteId: String? = null
}

/** The verifier lambda shape the facade's `require_auth`/`api_key_auth` factories take. */
public fun interface SpringRouteAuthVerifier : RouteAuthVerifier

/**
 * The suspend validator lambda shape the facade's `custom_validation` factory takes, expressed as a
 * Spring bean type (annotation attributes cannot carry lambdas).
 */
public fun interface CustomRouteValidator {
  public suspend fun validate(request: com.guardcore.protocols.GuardRequest): Any?
}

/**
 * Resolves Guard* annotations on handler methods and applies the SAME facade SecurityDecorator
 * factory sequence the python decorator application runs (ensure -> mutate -> apply,
 * BaseSecurityDecorator.kt:74-96). Applied once per handler method; results are cached by the
 * method's route key, so the decorator route configs exist before the first pipeline run for that
 * route (the python import-time equivalent).
 */
public class AnnotationRouteConfigurator(
    private val decorator: SecurityDecorator,
    private val beanFactory: ConfigurableBeanFactory?,
) {
  /** route key -> stamped route id (the decorator may have suffixed it). */
  private val applied: MutableMap<String, String> = linkedMapOf()

  /**
   * Applies all Guard* annotations of [handlerMethod] (once) and returns the stamped route id, or
   * null when the method carries none (fastapi-guard only stamps `_guard_route_id` on decorated
   * endpoints, middleware.py:491).
   */
  @Synchronized
  public fun configure(handlerMethod: HandlerMethod): String? {
    val routeKey = HandlerRouteKey(handlerMethod)
    applied[routeKey.routeKey]?.let {
      return it
    }
    val annotations = collectAnnotations(handlerMethod)
    if (annotations.isEmpty()) {
      return null
    }
    apply(routeKey, annotations)
    val routeId =
        checkNotNull(routeKey.stampedRouteId) {
          "guard route id was not stamped for ${routeKey.routeKey}"
        }
    applied[routeKey.routeKey] = routeId
    return routeId
  }

  private fun apply(routeKey: HandlerRouteKey, annotations: List<Annotation>) {
    for (annotation in annotations) {
      when (annotation) {
        is GuardRequireIp ->
            decorator.requireIp(
                routeKey,
                whitelist = annotation.whitelist.takeIf { it.isNotEmpty() }?.toList(),
                blacklist = annotation.blacklist.takeIf { it.isNotEmpty() }?.toList(),
            )
        is GuardBlockCountries -> decorator.blockCountries(routeKey, annotation.countries.toList())
        is GuardAllowCountries -> decorator.allowCountries(routeKey, annotation.countries.toList())
        is GuardBlockClouds ->
            decorator.blockClouds(
                routeKey,
                annotation.providers.takeIf { it.isNotEmpty() }?.toList(),
            )
        is GuardBypass -> decorator.bypass(routeKey, annotation.checks.toList())
        is GuardRateLimit -> decorator.rateLimit(routeKey, annotation.requests, annotation.window)
        is GuardGeoRateLimit -> decorator.geoRateLimit(routeKey, annotation.limits.toMap())
        is GuardUsageMonitor ->
            decorator.usageMonitor(
                routeKey,
                annotation.maxCalls,
                annotation.window,
                annotation.action,
            )
        is GuardReturnMonitor ->
            decorator.returnMonitor(
                routeKey,
                annotation.pattern,
                annotation.maxOccurrences,
                annotation.window,
                annotation.action,
            )
        is GuardBehaviorAnalysis ->
            decorator.behaviorAnalysis(routeKey, annotation.rules.map { it.toBehaviorRule() })
        is GuardSuspiciousFrequency ->
            decorator.suspiciousFrequency(
                routeKey,
                annotation.maxFrequency,
                annotation.window,
                annotation.action,
            )
        is GuardRequireHttps -> decorator.requireHttps(routeKey)
        is GuardRequireAuth ->
            decorator.requireAuth(
                routeKey,
                annotation.type,
                verifierBean(annotation.verifierBean),
            )
        is GuardApiKeyAuth ->
            decorator.apiKeyAuth(
                routeKey,
                annotation.headerName,
                verifierBean(annotation.verifierBean),
            )
        is GuardRequireAuthorizationHeader ->
            decorator.requireAuthorizationHeader(routeKey, annotation.scheme)
        is GuardRequireHeaders ->
            decorator.requireHeaders(
                routeKey,
                annotation.headers.associate { it.name to it.value },
            )
        is GuardBlockUserAgents -> decorator.blockUserAgents(routeKey, annotation.patterns.toList())
        is GuardContentTypeFilter ->
            decorator.contentTypeFilter(routeKey, annotation.allowedTypes.toList())
        is GuardMaxRequestSize -> decorator.maxRequestSize(routeKey, annotation.sizeBytes)
        is GuardRequireReferrer ->
            decorator.requireReferrer(routeKey, annotation.allowedDomains.toList())
        is GuardCustomValidation ->
            decorator.customValidation(
                routeKey,
            ) { request ->
              validatorBean(annotation.validatorBean).validate(request)
            }
        is GuardDetectionExclusion -> {
          val anySet =
              annotation.headers.isNotEmpty() ||
                  annotation.params.isNotEmpty() ||
                  annotation.bodyFields.isNotEmpty() ||
                  annotation.categories.isNotEmpty()
          decorator.detectionExclusion(
              routeKey,
              headers = annotation.headers.toSet().takeIf { annotation.headers.isNotEmpty() },
              params = annotation.params.toSet().takeIf { annotation.params.isNotEmpty() },
              bodyFields =
                  annotation.bodyFields.toSet().takeIf { annotation.bodyFields.isNotEmpty() },
              categories =
                  annotation.categories.toSet().takeIf { annotation.categories.isNotEmpty() },
              scanBody = annotation.scanBody.takeIf { !it || anySet },
          )
        }
        is GuardTimeWindow ->
            decorator.timeWindow(
                routeKey,
                annotation.startTime,
                annotation.endTime,
                annotation.timezone,
            )
        is GuardSuspiciousDetection -> decorator.suspiciousDetection(routeKey, annotation.enabled)
        is GuardHoneypotDetection ->
            decorator.honeypotDetection(routeKey, annotation.trapFields.toList())
      }
    }
  }

  private fun verifierBean(name: String): RouteAuthVerifier? {
    if (name.isEmpty()) {
      return null
    }
    val factory =
        checkNotNull(beanFactory) {
          "verifierBean '$name' requires a bean factory (annotation used outside a Spring context)"
        }
    return factory.getBean(name, RouteAuthVerifier::class.java)
  }

  private fun validatorBean(name: String): CustomRouteValidator {
    if (name.isEmpty()) {
      throw IllegalArgumentException("GuardCustomValidation requires a validatorBean name")
    }
    val factory =
        checkNotNull(beanFactory) {
          "validatorBean '$name' requires a bean factory (annotation used outside a Spring context)"
        }
    return factory.getBean(name, CustomRouteValidator::class.java)
  }

  private fun collectAnnotations(handlerMethod: HandlerMethod): List<Annotation> {
    // Search the user-facing method AND the class so class-level annotations apply to every
    // handler method of the bean (the python router-level decorator adoption analog).
    val found = mutableListOf<Annotation>()
    for (annotationType in GUARD_ANNOTATION_TYPES) {
      val onMethod =
          AnnotatedElementUtils.findMergedAnnotation(handlerMethod.method, annotationType)
      if (onMethod != null) {
        found.add(onMethod)
      }
      val onClass =
          AnnotatedElementUtils.findMergedAnnotation(handlerMethod.beanType, annotationType)
      if (onClass != null) {
        found.add(onClass)
      }
    }
    return found
  }

  private fun Array<GuardGeoRateLimit.Entry>.toMap(): Map<String, Pair<Int, Int>> = associate {
    it.country to (it.requests to it.window)
  }

  /** The facade's own converter (configToRule, BehaviorTracker.kt:36). */
  private fun GuardBehaviorRule.toBehaviorRule(): BehaviorRule =
      configToRule(toBehaviorRuleConfig())

  private fun GuardBehaviorRule.toBehaviorRuleConfig(): BehaviorRuleConfig =
      BehaviorRuleConfig(
          ruleType = RuleType.entries.first { it.pythonName == ruleType },
          threshold = threshold,
          window = window,
          pattern = pattern.takeIf { it.isNotEmpty() },
          action = RuleAction.entries.first { it.pythonName == action },
          banDuration = banDuration.takeIf { it > 0 },
          correlateWithDetection = correlateWithDetection,
      )

  public companion object {
    /** All Guard* annotation types, in factory-declaration order (SecurityDecorator.kt). */
    public val GUARD_ANNOTATION_TYPES: List<Class<out Annotation>> =
        listOf(
            GuardRequireIp::class.java,
            GuardBlockCountries::class.java,
            GuardAllowCountries::class.java,
            GuardBlockClouds::class.java,
            GuardBypass::class.java,
            GuardRateLimit::class.java,
            GuardGeoRateLimit::class.java,
            GuardUsageMonitor::class.java,
            GuardReturnMonitor::class.java,
            GuardBehaviorAnalysis::class.java,
            GuardSuspiciousFrequency::class.java,
            GuardRequireHttps::class.java,
            GuardRequireAuth::class.java,
            GuardApiKeyAuth::class.java,
            GuardRequireAuthorizationHeader::class.java,
            GuardRequireHeaders::class.java,
            GuardBlockUserAgents::class.java,
            GuardContentTypeFilter::class.java,
            GuardMaxRequestSize::class.java,
            GuardRequireReferrer::class.java,
            GuardCustomValidation::class.java,
            GuardDetectionExclusion::class.java,
            GuardTimeWindow::class.java,
            GuardSuspiciousDetection::class.java,
            GuardHoneypotDetection::class.java,
        )
  }
}
