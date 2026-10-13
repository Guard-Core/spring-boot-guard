package com.guardcore.spring

import com.guardcore.config.SecurityConfig
import com.guardcore.core.behavioral.BehavioralContext
import com.guardcore.core.behavioral.BehavioralProcessor
import com.guardcore.core.bypass.BypassContext
import com.guardcore.core.bypass.BypassHandler
import com.guardcore.core.checks.ChecksFactory
import com.guardcore.core.checks.SecurityCheckPipeline
import com.guardcore.core.events.CompositeAgentHandler
import com.guardcore.core.events.MetricsCollector
import com.guardcore.core.events.SecurityEventBus
import com.guardcore.core.initialization.HandlerInitializer
import com.guardcore.core.responses.ErrorResponseFactory
import com.guardcore.core.responses.ResponseContext
import com.guardcore.core.routing.RouteConfigResolver
import com.guardcore.core.routing.RoutingContext
import com.guardcore.core.validation.RequestValidator
import com.guardcore.core.validation.ValidationContext
import com.guardcore.decorators.BaseSecurityDecorator
import com.guardcore.decorators.RouteConfig
import com.guardcore.errors.GuardRedisError
import com.guardcore.handlers.BehaviorRule
import com.guardcore.handlers.CloudManager
import com.guardcore.handlers.CorsHandler
import com.guardcore.handlers.HandlerAgentSink
import com.guardcore.handlers.RateLimitManager
import com.guardcore.handlers.RedisManager
import com.guardcore.handlers.SecurityHeadersManager
import com.guardcore.protocols.AgentHandlerProtocol
import com.guardcore.protocols.GeoIPHandler
import com.guardcore.protocols.GuardMiddlewareProtocol
import com.guardcore.protocols.GuardRequest
import com.guardcore.protocols.GuardResponse
import com.guardcore.protocols.GuardResponseFactory
import com.guardcore.protocols.MiddlewareEventBusProtocol
import com.guardcore.protocols.MiddlewareResponseFactoryProtocol
import com.guardcore.protocols.RedisHandlerProtocol
import com.guardcore.util.IpExtraction
import com.guardcore.util.LoggingUtils
import java.util.logging.Logger
import kotlinx.coroutines.sync.withLock

/**
 * The port of fastapi-guard's `guard/middleware.py::SecurityMiddleware` (middleware.py:46) for the
 * Spring WebMVC envelope: the engine-facing middleware surface (GuardMiddlewareProtocol) the
 * [GuardFilter] drives per request. The adapter contains NO security logic - every decision comes
 * from the facade (pipeline, bypass, validator, behavioral processor, response factory).
 *
 * fastapi-guard wires the same graph: rate limit manager, redis/agent/geo handlers, event bus +
 * metrics, HandlerInitializer, RouteConfigResolver, ErrorResponseFactory, RequestValidator,
 * BypassHandler, BehavioralProcessor, CorsHandler (middleware.py:57-126) and the 17-check pipeline
 * (middleware.py:262). Initialization is lazy on the first request behind a lock (middleware.py:145
 * `_ensure_initialized`); the GuardRedisError path produces the 503 + Retry-After response
 * (middleware.py:571).
 */
public class GuardMiddleware(
    override val config: SecurityConfig,
    servletResponseFactory: ServletGuardResponseFactory = ServletGuardResponseFactory(),
    decorator: BaseSecurityDecorator? = null,
    /** An externally supplied agent handler (guard-agent-jvm); null when none is registered. */
    public val injectedAgentHandler: AgentHandlerProtocol? = null,
) : GuardMiddlewareProtocol {
  override val logger: Logger =
      LoggingUtils.setupCustomLogging(config.customLogFile, config.logFormat.pythonName)

  override var lastCloudIpRefresh: Int = 0

  override val suspiciousRequestCounts: MutableMap<String, MutableMap<String, Int>> = linkedMapOf()

  /** Python `rate_limit_handler` (middleware.py:57): the facade singleton manager. */
  override val rateLimitHandler: Any? = RateLimitManager.getInstance(config)

  /** Python `guard_decorator` (middleware.py:58): route decorator owning per-route configs. */
  private var guardDecoratorField: BaseSecurityDecorator? = decorator

  override val guardDecorator: Any?
    get() = guardDecoratorField

  /** Python `geo_ip_handler` (middleware.py:62-65): only when country filtering is configured. */
  override val geoIpHandler: GeoIPHandler?
    get() =
        if (config.whitelistCountries.isNotEmpty() || config.blockedCountries.isNotEmpty()) {
          config.geoIpHandler
        } else {
          null
        }

  /** Python `redis_handler` (middleware.py:66-70): Lettuce via the facade manager. */
  public val redisHandler: RedisManager? = if (config.enableRedis) RedisManager(config) else null

  private var agentHandlerField: AgentHandlerProtocol? = injectedAgentHandler

  /** Python `agent_handler` (middleware.py:72-93): the injected handler, then the composite. */
  override val agentHandler: AgentHandlerProtocol?
    get() = agentHandlerField

  /** Python `agent_degraded` (middleware.py:73). */
  public var agentDegraded: Boolean = false
    private set

  /** Python `security_pipeline` (middleware.py:95). */
  public var securityPipeline: SecurityCheckPipeline? = null
    private set

  /** Python `_guard_response_factory` (middleware.py:97): the plain servlet factory. */
  public val plainResponseFactory: ServletGuardResponseFactory = servletResponseFactory

  override val guardResponseFactory: GuardResponseFactory?
    get() = plainResponseFactory

  /** Python `event_bus` (middleware.py:99). */
  override var eventBus: MiddlewareEventBusProtocol =
      SecurityEventBus(injectedAgentHandler as? HandlerAgentSink, config, geoIpHandler)
    private set

  /** Python `metrics_collector` (middleware.py:102). */
  public var metricsCollector: MetricsCollector =
      MetricsCollector(injectedAgentHandler as? HandlerAgentSink, config)
    private set

  /** Python `handler_initializer` (middleware.py:104-111). */
  public val handlerInitializer: HandlerInitializer =
      HandlerInitializer(
          config = config,
          redisHandler = redisHandler,
          agentHandler = injectedAgentHandler,
          geoIpHandler = geoIpHandler,
          rateLimitHandler = rateLimitHandler,
          guardDecorator = guardDecoratorField,
      )

  /** Python `route_resolver` (middleware.py:113-118). */
  override val routeResolver: RouteConfigResolver =
      RouteConfigResolver(RoutingContext(config, logger, guardDecoratorField))

  /** Python `response_factory` (middleware.py:213): the facade ErrorResponseFactory. */
  public var errorResponseFactory: ErrorResponseFactory
    private set

  /** The duck-typed response factory surface the facade checks call through. */
  override val responseFactory: MiddlewareResponseFactoryProtocol
    get() = middlewareResponseFactory

  private val middlewareResponseFactory: GuardMiddlewareResponseFactory

  /** Python `validator` (middleware.py:220). */
  public var validator: RequestValidator
    private set

  /** Python `bypass_handler` (middleware.py:230). */
  public var bypassHandler: BypassHandler
    private set

  /** Python `behavioral_processor` (middleware.py:239). */
  public var behavioralProcessor: BehavioralProcessor
    private set

  /** Python `_cors_handler` (middleware.py:124-126). */
  public val corsHandler: CorsHandler? = if (config.enableCors) CorsHandler(config) else null

  /** Python `_initialized` + `_init_lock` (middleware.py:122-123). */
  private val initMutex = kotlinx.coroutines.sync.Mutex()

  private var initialized: Boolean = false

  init {
    // Host-side seam registration (the facade's documented host contract, CoreTestSupport.kt:132 /
    // GuardCore.kt:15): wires the detection-engine seams (user-agent matcher G-5, straddle overlap)
    // and the handler seams. Without it, seam-backed matching degrades to the python failure-path
    // behavior (no blocklist matches).
    com.guardcore.handlers.HandlerSeams.register()
    com.guardcore.util.DetectionEngineWiring.register()

    configureSecurityHeaders(config)
    middlewareResponseFactory = GuardMiddlewareResponseFactory(plainResponseFactory, errorFactory())
    errorResponseFactory = errorFactory()
    validator = RequestValidator(ValidationContext(config, logger, eventBus))
    bypassHandler =
        BypassHandler(
            BypassContext(config, logger, eventBus, routeResolver, errorResponseFactory, validator),
        )
    behavioralProcessor = buildBehavioralProcessor()
    if (config.enableAgent && injectedAgentHandler == null) {
      // The adapter never builds the agent itself: an AgentHandlerProtocol bean is the only agent
      // source. Mirror the reference AgentPackageNotInstalledError degrade path
      // (middleware.py:83-93).
      onAgentInitFailed(
          "Agent enabled but no agent handler was provided. Register a guard-agent-jvm " +
              "AgentHandlerProtocol bean (GuardAgentTelemetry), or disable enable_agent.",
          null,
      )
    }
  }

  private fun errorFactory(): ErrorResponseFactory =
      ErrorResponseFactory(
          ResponseContext(
              config = config,
              logger = logger,
              metricsCollector = metricsCollector,
              agentHandler = agentHandlerField,
              guardDecorator = guardDecoratorField,
              responseFactory = PlainResponseFactoryAdapter(plainResponseFactory),
          ),
      )

  private fun buildBehavioralProcessor(): BehavioralProcessor =
      BehavioralProcessor(
          BehavioralContext(
              config = config,
              logger = logger,
              eventBus = eventBus,
              guardDecorator = guardDecoratorField,
              behaviorTracker = handlerInitializer.behaviorTracker,
          ),
      )

  /** Python `mark_initialized` (middleware.py:131). */
  public fun isInitialized(): Boolean = initialized

  /**
   * Python `_warn_if_eager_init_not_honored` (middleware.py:134): logged when lazy_init=False but
   * the first request is doing the initialization (no GuardEagerInitializer was wired).
   */
  private fun warnIfEagerInitNotHonored() {
    if (config.lazyInit) {
      return
    }
    logger.warning(
        "lazy_init=False was set, but GuardMiddleware is initializing on " +
            "the first request instead of at context startup. Register " +
            "GuardEagerInitializer (guard.eager-init=true) to initialize at boot.",
    )
  }

  /**
   * Python `_ensure_initialized` (middleware.py:145): lock-guarded lazy init. Returns the 503
   * response when initialization fails on Redis with redis_fail_open=False (the dispatch
   * GuardRedisError path, middleware.py:583-587).
   */
  public suspend fun ensureInitialized(): GuardResponse? {
    if (initialized) {
      return null
    }
    initMutex.withLock {
      if (initialized) {
        return null
      }
      warnIfEagerInitNotHonored()
      try {
        initialize()
      } catch (e: GuardRedisError) {
        logger.severe("Redis unavailable during initialization: $e")
        if (!config.redisFailOpen) {
          val response =
              errorResponseFactory.createErrorResponse(503, "Service temporarily unavailable")
          response.headers["Retry-After"] = "5"
          initialized = true
          return response
        }
        initialized = true
        return null
      }
      initialized = true
      return null
    }
  }

  /** Python `initialize` (middleware.py:688): pipeline + redis + agent integrations. */
  public suspend fun initialize() {
    buildSecurityPipeline()
    handlerInitializer.initializeRedisHandlers()
    handlerInitializer.initializeAgentIntegrations()

    val composite = handlerInitializer.compositeHandler
    if (composite != null) {
      // Python initialize() lines 719-726: adopt the composite as the agent handler and rebuild
      // the event bus + metrics collector around it, then the event-dependent contexts.
      agentHandlerField = CompositeAgentProtocol(composite)
      eventBus = handlerInitializer.buildEventBus(geoIpHandler = geoIpHandler)
      metricsCollector = handlerInitializer.buildMetricsCollector()
      rebuildEventDependentContexts()
    }
  }

  /** Python `_build_security_pipeline` (middleware.py:262): the fixed 17-check pipeline. */
  private fun buildSecurityPipeline() {
    val pipeline = ChecksFactory.buildDefaultPipeline(this)
    securityPipeline = pipeline
    logger.info(
        "Security pipeline initialized with ${pipeline.size} checks: ${pipeline.getCheckNames()} " +
            "(${ChecksFactory.DEFAULT_CHECK_CLASSES.size - pipeline.size} skipped)",
    )
  }

  /** Python `_configure_security_headers` (middleware.py:273-304). */
  private fun configureSecurityHeaders(config: SecurityConfig) {
    val manager = kotlinx.coroutines.runBlocking { SecurityHeadersManager.getInstance() }
    val headersConfig = config.securityHeaders
    if (headersConfig == null || headersConfig["enabled"] == false) {
      manager.enabled = false
      return
    }
    manager.enabled = true
    @Suppress("UNCHECKED_CAST") val hsts = headersConfig["hsts"] as? Map<String, Any?> ?: emptyMap()
    @Suppress("UNCHECKED_CAST")
    manager.configureCsp(headersConfig["csp"] as? Map<String, List<String>>)
    manager.configureHsts(
        (hsts["max_age"] as? Number)?.toInt(),
        hsts["include_subdomains"] as? Boolean ?: true,
        hsts["preload"] as? Boolean ?: false,
    )
    manager.configureCors(
        if (config.enableCors) config.corsAllowOrigins else null,
        config.corsAllowCredentials,
        config.corsAllowMethods,
        config.corsAllowHeaders,
    )
    manager.updateDefaultHeaders(
        headersConfig["frame_options"] as? String,
        headersConfig["content_type_options"] as? String,
        headersConfig["xss_protection"] as? String,
        headersConfig["referrer_policy"] as? String,
        headersConfig["permissions_policy"] as? String ?: "UNSET",
    )
    @Suppress("UNCHECKED_CAST")
    manager.addCustomHeaders(headersConfig["custom"] as? Map<String, String>)
  }

  /** Python `_build_event_dependent_contexts` (middleware.py:204): rebuilt after agent wiring. */
  private fun rebuildEventDependentContexts() {
    errorResponseFactory = errorFactory()
    middlewareResponseFactory.rebind(errorResponseFactory)
    validator = RequestValidator(ValidationContext(config, logger, eventBus))
    bypassHandler =
        BypassHandler(
            BypassContext(config, logger, eventBus, routeResolver, errorResponseFactory, validator),
        )
    behavioralProcessor = buildBehavioralProcessor()
  }

  /** Python `set_decorator_handler` (middleware.py:306). */
  public fun setDecoratorHandler(decoratorHandler: BaseSecurityDecorator?) {
    guardDecoratorField = decoratorHandler
  }

  /** Python async `create_error_response` (middleware.py:678). */
  override suspend fun createErrorResponse(statusCode: Int, defaultMessage: String): GuardResponse =
      errorResponseFactory.createErrorResponse(statusCode, defaultMessage)

  /** Python async `refresh_cloud_ip_ranges` (middleware.py:661). */
  override suspend fun refreshCloudIpRanges() {
    val providers = config.blockCloudProviders
    if (providers.isNullOrEmpty()) {
      return
    }
    if (config.enableRedis && redisHandler != null) {
      CloudManager.getInstance()
          .refreshAsync(
              providers.toList(),
              ttl = config.cloudIpRefreshInterval,
          )
    } else {
      CloudManager.getInstance().refresh(providers.toList())
    }
    lastCloudIpRefresh = (System.currentTimeMillis() / 1000).toInt()
  }

  /** Python async `reset` (middleware.py:685): clears rate limit state. */
  public suspend fun reset() {
    (rateLimitHandler as RateLimitManager).reset()
  }

  /** Python `get_initialization_status` (middleware.py:705). */
  public fun getInitializationStatus(): Map<String, Any?> =
      handlerInitializer.getInitializationStatus()

  /**
   * Python `_process_response` (middleware.py:333): the response-exit path through the facade
   * factory (behavioral return rules + metrics + security headers + CORS + modifier).
   */
  public suspend fun processResponse(
      request: GuardRequest,
      response: GuardResponse,
      responseTime: Double,
      routeConfig: RouteConfig?,
  ): GuardResponse =
      errorResponseFactory.processResponse(
          request,
          response,
          responseTime,
          routeConfig,
          processBehavioralRules = {
              guardRequest: GuardRequest,
              guardResponse: GuardResponse,
              clientIp: String,
              route: RouteConfig ->
            behavioralProcessor.processReturnRules(guardRequest, guardResponse, clientIp, route)
          },
          processGlobalBehavioralRules = {
              guardRequest: GuardRequest,
              guardResponse: GuardResponse,
              clientIp: String,
              rules: List<BehaviorRule> ->
            behavioralProcessor.processGlobalReturnRules(
                guardRequest,
                guardResponse,
                clientIp,
                rules,
            )
          },
      )

  /**
   * The client IP for the dispatch flow (middleware.py:603): delegated to the facade's IpExtraction
   * (adapters never reimplement IP security logic).
   */
  public suspend fun extractClientIp(request: GuardRequest): String =
      IpExtraction.extractClientIp(request, config, agentHandler)

  /** The decorator handler for wiring by the auto-configuration. */
  public fun decoratorHandler(): BaseSecurityDecorator? = guardDecoratorField

  /** Python `_on_agent_init_failed` (middleware.py:245). */
  internal fun onAgentInitFailed(message: String, exc: Exception?) {
    agentDegraded = true
    val hook = config.onError
    if (hook != null) {
      hook.invoke("agent_init", exc ?: IllegalStateException(message), linkedMapOf())
    }
    if (config.agentStrict) {
      throw (exc ?: IllegalStateException(message))
    }
    logger.severe(message)
    logger.warning("Continuing without agent functionality")
  }

  /**
   * Python `shutdown_agent_integrations` on application shutdown: stops the composite, which stops
   * the injected agent and flushes its buffer. Spring calls it as the bean's inferred destroy
   * method.
   */
  public fun shutdown() {
    kotlinx.coroutines.runBlocking { handlerInitializer.shutdownAgentIntegrations() }
  }

  /**
   * The composite handler as the agent protocol (python passes the composite as agent_handler; the
   * HandlerInitializer builds the same adapter shape over the composite).
   */
  private class CompositeAgentProtocol(
      private val composite: CompositeAgentHandler,
  ) : AgentHandlerProtocol {
    override suspend fun initializeRedis(redisHandler: RedisHandlerProtocol) {
      composite.initializeRedis(redisHandler)
    }

    override suspend fun sendEvent(event: Any?) {
      @Suppress("UNCHECKED_CAST")
      composite.sendEvent((event as? Map<String, Any?>) ?: linkedMapOf())
    }

    override suspend fun sendMetric(metric: Any?) {
      @Suppress("UNCHECKED_CAST")
      composite.sendMetric((metric as? Map<String, Any?>) ?: linkedMapOf())
    }

    override suspend fun start() {
      composite.start()
    }

    override suspend fun stop() {
      composite.stop()
    }

    override suspend fun flushBuffer() {
      composite.flushBuffer()
    }

    override suspend fun getDynamicRules(): Any? = composite.getDynamicRules()

    override suspend fun healthCheck(): Boolean = composite.healthCheck()
  }
}
