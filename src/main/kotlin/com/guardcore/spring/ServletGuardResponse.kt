package com.guardcore.spring

import com.guardcore.protocols.GuardResponse
import com.guardcore.protocols.GuardResponseFactory
import com.guardcore.protocols.MiddlewareResponseFactoryProtocol
import jakarta.servlet.http.HttpServletResponse
import org.springframework.web.util.ContentCachingResponseWrapper

/**
 * The port of fastapi-guard's `guard/adapters.py::StarletteGuardResponse` and
 * `StarletteResponseFactory` (adapters.py:118, adapters.py:164): the servlet response wrapped into
 * the facade's [GuardResponse] protocol, plus the plain factory the facade's response factory and
 * checks drive.
 *
 * The wrapper-backed flavor writes header mutations straight onto the buffered servlet response
 * (ContentCachingResponseWrapper keeps the real response uncommitted until copyBodyToResponse, so
 * the post-handler processResponse pass can still apply security headers and CORS, exactly the
 * envelope Starlette's BaseHTTPMiddleware gives fastapi-guard).
 */
public class ServletGuardResponse
private constructor(
    override val statusCode: Int,
    override var body: ByteArray?,
    override val headers: MutableMap<String, String>,
    /** Non-null when this response wraps the buffered downstream servlet response. */
    internal val wrapped: ContentCachingResponseWrapper?,
) : GuardResponse {
  public constructor(
      statusCode: Int,
      body: ByteArray?,
      headers: Map<String, String> = linkedMapOf(),
  ) : this(statusCode, body, linkedMapOf<String, String>().apply { putAll(headers) }, null)

  public companion object {
    /** Wraps a buffered downstream response (the StarletteGuardResponse analog). */
    public fun wrapping(wrapper: ContentCachingResponseWrapper): ServletGuardResponse =
        ServletGuardResponse(
            wrapper.status,
            wrapper.contentAsByteArray,
            WrapperHeaders(wrapper),
            wrapper,
        )
  }
}

/** Mutable header view over the buffered servlet response (case-insensitive reads). */
private class WrapperHeaders(private val wrapper: ContentCachingResponseWrapper) :
    MutableMap<String, String> {
  override val size: Int
    get() = wrapper.headerNames.size

  override fun isEmpty(): Boolean = wrapper.headerNames.isEmpty()

  override fun containsKey(key: String): Boolean = wrapper.getHeader(key) != null

  override fun containsValue(value: String): Boolean =
      wrapper.headerNames.any { name -> wrapper.getHeaders(name).contains(value) }

  override fun get(key: String): String? = wrapper.getHeader(key)

  override fun put(key: String, value: String): String? {
    val previous = wrapper.getHeader(key)
    wrapper.setHeader(key, value)
    return previous
  }

  override fun remove(key: String): String {
    throw UnsupportedOperationException(
        "Servlet responses cannot remove a header; set it to a replacement value instead",
    )
  }

  override fun putAll(from: Map<out String, String>) {
    for ((key, value) in from) {
      put(key, value)
    }
  }

  override fun clear() {
    throw UnsupportedOperationException("Servlet responses cannot clear headers")
  }

  override val keys: MutableSet<String>
    get() = linkedSetOf<String>().apply { addAll(wrapper.headerNames) }

  override val values: MutableCollection<String>
    get() =
        mutableListOf<String>().apply {
          for (name in wrapper.headerNames) {
            addAll(wrapper.getHeaders(name))
          }
        }

  override val entries: MutableSet<MutableMap.MutableEntry<String, String>>
    get() {
      val entries = linkedSetOf<MutableMap.MutableEntry<String, String>>()
      for (name in wrapper.headerNames) {
        val first = wrapper.getHeader(name)
        if (first != null) {
          entries.add(
              object : MutableMap.MutableEntry<String, String> {
                override val key: String = name

                override val value: String = first

                override fun setValue(newValue: String): String {
                  wrapper.setHeader(name, newValue)
                  return first
                }
              },
          )
        }
      }
      return entries
    }
}

/**
 * The port of StarletteResponseFactory (adapters.py:164). Plain responses carry Starlette's exact
 * observable header shape - content-type "text/plain; charset=utf-8" with content-length for
 * create_response (adapters.py:166-169), location + content-length 0 for the redirect (starlette
 * RedirectResponse body semantics, adapters.py:171-175).
 */
public class ServletGuardResponseFactory : GuardResponseFactory {
  override fun createResponse(content: String, statusCode: Int): ServletGuardResponse {
    val body = content.encodeToByteArray()
    val headers =
        linkedMapOf(
            "Content-Type" to "text/plain; charset=utf-8",
            "Content-Length" to body.size.toString(),
        )
    return ServletGuardResponse(statusCode, body, headers)
  }

  override fun createRedirectResponse(url: String, statusCode: Int): ServletGuardResponse {
    val headers =
        linkedMapOf(
            "Location" to url,
            "Content-Length" to "0",
        )
    return ServletGuardResponse(statusCode, ByteArray(0), headers)
  }
}

/**
 * The plain factory as the MiddlewareResponseFactoryProtocol the facade's ResponseContext takes
 * (python passes the plain StarletteResponseFactory into the context, middleware.py:211; only
 * create_response/create_redirect_response are consumed there, factory.py:36/57).
 */
public class PlainResponseFactoryAdapter(
    private val plain: ServletGuardResponseFactory,
) : MiddlewareResponseFactoryProtocol {
  override fun createResponse(
      content: Any?,
      statusCode: Int,
  ): com.guardcore.protocols.GuardResponse =
      when (content) {
        null -> plain.createResponse("", statusCode)
        is String -> plain.createResponse(content, statusCode)
        else -> plain.createResponse(content.toString(), statusCode)
      }

  override fun createRedirectResponse(
      url: Any?,
      statusCode: Int,
  ): com.guardcore.protocols.GuardResponse =
      plain.createRedirectResponse(url?.toString() ?: "", statusCode)

  override suspend fun createHttpsRedirect(
      request: com.guardcore.protocols.GuardRequest,
  ): com.guardcore.protocols.GuardResponse =
      plain.createRedirectResponse(request.urlReplaceScheme("https"), 301)

  override suspend fun applyModifier(
      response: com.guardcore.protocols.GuardResponse,
  ): com.guardcore.protocols.GuardResponse = response
}

/**
 * The `middleware.response_factory` duck-typed surface the facade checks call through
 * (MiddlewareSurfaceProtocols.kt:49): composes the plain factory with the facade's
 * ErrorResponseFactory exactly like the python middleware exposes its ErrorResponseFactory as
 * `middleware.response_factory` (fastapi-guard middleware.py:213).
 */
public class GuardMiddlewareResponseFactory(
    private val plain: ServletGuardResponseFactory,
    private var errorFactory: com.guardcore.core.responses.ErrorResponseFactory,
) : MiddlewareResponseFactoryProtocol {
  /** Rebinds after agent wiring rebuilds the facade factory (middleware.py:703). */
  public fun rebind(errorFactory: com.guardcore.core.responses.ErrorResponseFactory) {
    this.errorFactory = errorFactory
  }

  override fun createResponse(
      content: Any?,
      statusCode: Int,
  ): com.guardcore.protocols.GuardResponse =
      when (content) {
        null -> plain.createResponse("", statusCode)
        is String -> plain.createResponse(content, statusCode)
        else -> plain.createResponse(content.toString(), statusCode)
      }

  override fun createRedirectResponse(
      url: Any?,
      statusCode: Int,
  ): com.guardcore.protocols.GuardResponse =
      plain.createRedirectResponse(url?.toString() ?: "", statusCode)

  override suspend fun createHttpsRedirect(
      request: com.guardcore.protocols.GuardRequest,
  ): com.guardcore.protocols.GuardResponse = errorFactory.createHttpsRedirect(request)

  override suspend fun applyModifier(
      response: com.guardcore.protocols.GuardResponse,
  ): com.guardcore.protocols.GuardResponse = errorFactory.applyModifier(response)
}

/** Writes a facade GuardResponse onto the servlet response (the unwrap_response analog). */
public fun writeGuardResponse(
    response: GuardResponse,
    servletResponse: HttpServletResponse,
) {
  servletResponse.status = response.statusCode
  for ((name, value) in response.headers) {
    servletResponse.setHeader(name, value)
  }
  val body = response.body
  if (body != null && body.isNotEmpty()) {
    servletResponse.outputStream.use { it.write(body) }
    servletResponse.outputStream.flush()
  } else {
    servletResponse.flushBuffer()
  }
}
