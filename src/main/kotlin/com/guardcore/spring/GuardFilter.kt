package com.guardcore.spring

import com.guardcore.decorators.BaseSecurityDecorator
import com.guardcore.handlers.CorsPreflightResponse
import com.guardcore.handlers.isPreflight
import com.guardcore.java.JavaBridges
import com.guardcore.protocols.GuardRequest
import com.guardcore.protocols.GuardResponse
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.HandlerExecutionChain
import org.springframework.web.servlet.HandlerMapping
import org.springframework.web.util.ContentCachingResponseWrapper

/**
 * The port of fastapi-guard's `SecurityMiddleware.dispatch` (middleware.py:578) onto the servlet
 * envelope, run as a [OncePerRequestFilter].
 *
 * WHY A FILTER AND NOT A HANDLERINTERCEPTOR: fastapi-guard is an ASGI middleware - it envelops the
 * whole application (runs before routing on every request, blocks before handlers run, and
 * processes the handler's response afterwards). A Boot HandlerInterceptor runs after handler
 * mapping and cannot guard unmatched paths (404 probes still must be scanned, exactly the
 * fastapi-guard observable) and cannot reliably post-process a handler's response before it is
 * committed. The filter gives the exact envelope: pipeline before the handler, buffered
 * post-processing after, and route resolution through the public HandlerMapping.getHandler surface
 * (the same early-resolution problem the TS express adapter solves by scanning the router stack,
 * packages/express/src/route-id.ts).
 *
 * Coroutine bridge: the servlet thread is blocking, so every guard stage runs through runBlocking
 * confined to this entry point (the facade's xBlocking/unasync bridge convention, JavaBridges.kt);
 * the pipeline call itself goes through JavaBridges.executePipelineBlocking.
 */
public class GuardFilter(
    private val middleware: GuardMiddleware,
    private val handlerMappings: List<HandlerMapping>,
    private val annotationConfigurator: AnnotationRouteConfigurator,
    private val decoratorHandler: BaseSecurityDecorator?,
) : OncePerRequestFilter() {
  override fun doFilterInternal(
      request: HttpServletRequest,
      response: HttpServletResponse,
      filterChain: FilterChain,
  ) {
    val guardRequestWrapper = GuardHttpServletRequest(request)
    val responseWrapper = ContentCachingResponseWrapper(response)
    val guardRequest = ServletGuardRequest(guardRequestWrapper)

    // Python `_ensure_initialized` + the GuardRedisError 503 path (middleware.py:582-587).
    val initFailure = kotlinx.coroutines.runBlocking { middleware.ensureInitialized() }
    if (initFailure != null) {
      injectCorsHeaders(guardRequest.headers, initFailure)
      finish(initFailure, responseWrapper, response)
      return
    }

    populateGuardState(guardRequestWrapper)

    // Python `_handle_preflight` (middleware.py:514).
    val preflightResponse = runGuardStage {
      handlePreflight(guardRequest)
    }
    if (preflightResponse != null) {
      finish(preflightResponse, responseWrapper, response)
      return
    }

    // Python `_handle_passthrough` (middleware.py:529): exclude-path scoping + the
    // unresolvable-client-address fail-secure 403. call_next is never invoked on this path.
    val passthroughResponse = runGuardStage {
      middleware.bypassHandler.handlePassthrough(guardRequest) { null }
    }
    if (passthroughResponse != null) {
      injectCorsHeaders(guardRequest.headers, passthroughResponse)
      finish(passthroughResponse, responseWrapper, response)
      return
    }

    // Python dispatch: client IP -> route config -> security bypass (middleware.py:603-612).
    val bypassStage: Pair<String, RouteConfig?>? =
        kotlinx.coroutines.runBlocking {
          val ip = middleware.extractClientIp(guardRequest)
          val routeConfig = middleware.routeResolver.getRouteConfig(guardRequest)
          val bypass =
              middleware.bypassHandler.handleSecurityBypass(
                  guardRequest,
                  { callNext(guardRequestWrapper, responseWrapper, filterChain) },
                  routeConfig,
              )
          if (bypass != null) {
            injectCorsHeaders(guardRequest.headers, bypass)
            finish(bypass, responseWrapper, response)
            null
          } else {
            ip to routeConfig
          }
        }
    if (bypassStage == null) {
      return
    }
    val (clientIp, routeConfig) = bypassStage

    // Python `_handle_pipeline_block` (middleware.py:560): the 17 fixed-order checks, through the
    // facade's blocking bridge (JavaBridges.executePipelineBlocking).
    val blocked =
        middleware.securityPipeline?.let {
          JavaBridges.executePipelineBlocking(it, guardRequest)
        }
    if (blocked != null) {
      injectCorsHeaders(guardRequest.headers, blocked)
      finish(blocked, responseWrapper, response)
      return
    }

    // Python: usage rules (middleware.py:618-621).
    if (routeConfig != null && routeConfig.behaviorRules.isNotEmpty() && clientIp.isNotEmpty()) {
      runGuardStage {
        middleware.behavioralProcessor.processUsageRules(guardRequest, clientIp, routeConfig)
        null
      }
    }

    // Python: `call_next` (middleware.py:624).
    val startTime = System.nanoTime()
    try {
      filterChain.doFilter(guardRequestWrapper, responseWrapper)
    } catch (e: Exception) {
      runCatching { responseWrapper.copyBodyToResponse() }
      throw e
    }
    val responseTime = (System.nanoTime() - startTime) / 1_000_000_000.0

    // Python `_process_response` + CORS injection (middleware.py:627-631).
    val guardResponse = ServletGuardResponse.wrapping(responseWrapper)
    runGuardStage {
      middleware.processResponse(guardRequest, guardResponse, responseTime, routeConfig)
      null
    }
    injectCorsHeadersIntoWrapper(guardRequest.headers, responseWrapper)
    responseWrapper.copyBodyToResponse()
  }

  /** Python `_handle_preflight` (middleware.py:514-527). */
  private suspend fun handlePreflight(guardRequest: GuardRequest): GuardResponse? {
    val corsHandler = middleware.corsHandler ?: return null
    if (!isPreflight(guardRequest.method, guardRequest.headers)) {
      return null
    }
    val blocking = middleware.securityPipeline?.execute(guardRequest)
    if (blocking != null) {
      return blocking
    }
    val preflight: CorsPreflightResponse = corsHandler.buildPreflightResponse(guardRequest.headers)
    return ServletGuardResponse(
        preflight.statusCode,
        preflight.body.encodeToByteArray(),
        preflight.headers,
    )
  }

  /** The wrapped call_next (middleware.py:590 `wrap_call_next`): runs the downstream chain. */
  private fun callNext(
      request: GuardHttpServletRequest,
      responseWrapper: ContentCachingResponseWrapper,
      filterChain: FilterChain,
  ): GuardResponse {
    filterChain.doFilter(request, responseWrapper)
    return ServletGuardResponse.wrapping(responseWrapper)
  }

  /** Runs a suspend guard stage on the servlet thread (the xBlocking bridge convention). */
  private fun runGuardStage(stage: suspend () -> GuardResponse?): GuardResponse? =
      kotlinx.coroutines.runBlocking { stage() }

  /**
   * Python `_populate_guard_state` (middleware.py:473): stamps the guard route id from the matched
   * HandlerMethod (applying Guard* annotations on first sight), the endpoint id, and the decorator.
   */
  private fun populateGuardState(wrapper: GuardHttpServletRequest) {
    val decorator = decoratorHandler
    if (decorator != null) {
      wrapper.stateBag.setAttr("guard_decorator", decorator)
    }

    val handlerMethod = resolveHandlerMethod(wrapper)
    if (handlerMethod == null) {
      wrapper.stateBag.setAttr("guard_route_unresolved", true)
      return
    }

    val routeId = annotationConfigurator.configure(handlerMethod)
    if (routeId != null) {
      wrapper.stateBag.setAttr("guard_route_id", routeId)
    }
    wrapper.stateBag.setAttr(
        "guard_endpoint_id",
        "${handlerMethod.beanType.name}.${handlerMethod.method.name}",
    )
  }

  /**
   * Early handler resolution (the fastapi-guard `_resolve_route` analog, middleware.py:356): ask
   * the application's HandlerMappings what would serve this request, without dispatching.
   */
  private fun resolveHandlerMethod(request: HttpServletRequest): HandlerMethod? {
    for (mapping in handlerMappings) {
      val chain: HandlerExecutionChain =
          try {
            mapping.getHandler(request) ?: continue
          } catch (e: Exception) {
            continue
          }
      return chain.handler as? HandlerMethod
    }
    return null
  }

  /** Python `_inject_cors_headers` (middleware.py:507) over a facade GuardResponse. */
  private fun injectCorsHeaders(requestHeaders: Map<String, String>, response: GuardResponse) {
    val corsHandler = middleware.corsHandler ?: return
    val corsHeaders = corsHandler.buildResponseHeaders(requestHeaders)
    for ((key, value) in corsHeaders) {
      response.headers[key] = value
    }
  }

  /** Python `_inject_cors_headers` over the buffered downstream response. */
  private fun injectCorsHeadersIntoWrapper(
      requestHeaders: Map<String, String>,
      wrapper: ContentCachingResponseWrapper,
  ) {
    val corsHandler = middleware.corsHandler ?: return
    val corsHeaders = corsHandler.buildResponseHeaders(requestHeaders)
    for ((key, value) in corsHeaders) {
      wrapper.setHeader(key, value)
    }
  }

  /**
   * The `unwrap_response` analog (adapters.py:189): a facade-created response is written to the raw
   * servlet response; a wrapper-backed response (security-bypass path) is already buffered in the
   * wrapper and only needs its body flushed.
   */
  private fun finish(
      response: GuardResponse,
      responseWrapper: ContentCachingResponseWrapper,
      servletResponse: HttpServletResponse,
  ) {
    if (response is ServletGuardResponse && response.wrapped != null) {
      responseWrapper.copyBodyToResponse()
      return
    }
    writeGuardResponse(response, servletResponse)
  }
}
