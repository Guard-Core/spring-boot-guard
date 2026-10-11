package com.guardcore.spring

import com.guardcore.protocols.BoundedBodyReader
import com.guardcore.protocols.GuardRequest
import com.guardcore.protocols.RequestState
import jakarta.servlet.ReadListener
import jakarta.servlet.ServletInputStream
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletRequestWrapper
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URLDecoder
import java.nio.charset.Charset

/**
 * The port of fastapi-guard's `guard/adapters.py::StarletteGuardRequest` (adapters.py:35): the
 * jakarta.servlet HttpServletRequest wrapped into the facade's [GuardRequest] protocol.
 *
 * Observable-behavior anchors against the Starlette reference:
 * - url_path strips the context path (the root_path analog of get_route_path, adapters.py:40).
 * - headers are case-insensitive with repeated headers joined with ", " (the
 *   _join_repeated_header_lines analog, adapters.py:23-32).
 * - body() is safe to call repeatedly (cached by the wrapping request, adapters.py:72-73) and
 *   readBodyPrefix() is the bounded reader the facade's BodyReader drives (adapters.py:75-107): a
 *   bounded read caches only the prefix it pulled and the downstream servlet stream replays it
 *   before continuing with the live remainder.
 */
public class ServletGuardRequest(private val request: GuardHttpServletRequest) :
    GuardRequest, BoundedBodyReader {
  /** Python property `url_path` (request_protocol.py:20 via adapters.py:40). */
  override val urlPath: String
    get() {
      val contextPath = request.contextPath ?: ""
      val uri = request.requestURI ?: "/"
      return if (contextPath.isNotEmpty() && uri.startsWith(contextPath)) {
        uri.removePrefix(contextPath).ifEmpty { "/" }
      } else {
        uri
      }
    }

  /** Python property `url_scheme` (request_protocol.py:23 via adapters.py:44). */
  override val urlScheme: String
    get() = request.scheme ?: "http"

  /** Python property `url_full` (request_protocol.py:26 via adapters.py:48). */
  override val urlFull: String
    get() = request.requestURL.toString() + (request.queryString?.let { "?$it" } ?: "")

  /**
   * Python method `url_replace_scheme` (request_protocol.py:29 via adapters.py:51): Starlette's
   * `url.replace(scheme=...)` swaps only the scheme component.
   */
  override fun urlReplaceScheme(scheme: String): String {
    val full = urlFull
    val current = urlScheme
    return if (full.startsWith("$current://")) {
      "$scheme://" + full.substring(current.length + 3)
    } else {
      full
    }
  }

  /** Python property `method` (request_protocol.py:32 via adapters.py:55). */
  override val method: String
    get() = request.method

  /** Python property `client_host` (request_protocol.py:35 via adapters.py:59). */
  override val clientHost: String?
    get() = request.remoteAddr

  /** Python property `headers` (request_protocol.py:38 via adapters.py:65). */
  override val headers: Map<String, String> by lazy {
    val joined = linkedMapOf<String, MutableList<String>>()
    val names: java.util.Enumeration<String> =
        request.headerNames ?: java.util.Collections.emptyEnumeration()
    while (names.hasMoreElements()) {
      val name = names.nextElement()
      val values: java.util.Enumeration<String> =
          request.getHeaders(name) ?: java.util.Collections.emptyEnumeration()
      while (values.hasMoreElements()) {
        joined.getOrPut(name.lowercase()) { mutableListOf() }.add(values.nextElement())
      }
    }
    CaseInsensitiveHeaders(joined.mapValues { (_, values) -> values.joinToString(", ") })
  }

  /** Python property `query_params` (request_protocol.py:41 via adapters.py:69). */
  override val queryParams: Map<String, String> by lazy {
    val queryString = request.queryString ?: return@lazy emptyMap<String, String>()
    val params = linkedMapOf<String, String>()
    for (pair in queryString.split("&")) {
      if (pair.isEmpty()) {
        continue
      }
      val separator = pair.indexOf('=')
      val name: String
      val value: String
      if (separator < 0) {
        name = URLDecoder.decode(pair, Charsets.UTF_8)
        value = ""
      } else {
        name = URLDecoder.decode(pair.substring(0, separator), Charsets.UTF_8)
        value = URLDecoder.decode(pair.substring(separator + 1), Charsets.UTF_8)
      }
      params[name] = value
    }
    params
  }

  /** Python async `body` (request_protocol.py:44 via adapters.py:72). */
  override suspend fun body(): ByteArray = request.cachedBody()

  /** Python async `read_body_prefix` (request_protocol.py:125 via adapters.py:75). */
  override suspend fun readBodyPrefix(maxBytes: Int): ByteArray = request.readPrefix(maxBytes)

  /** Python property `state` (request_protocol.py:47): per-request scratch bag. */
  override val state: RequestState
    get() = request.stateBag

  /** Python property `scope` (request_protocol.py:50): the raw platform request. */
  override val scope: Map<String, Any?>
    get() = mapOf("servlet_request" to request)
}

/** Case-insensitive read-only header map (the facade reads `headers["content-type"]`). */
public class CaseInsensitiveHeaders(private val backing: Map<String, String>) :
    Map<String, String> {
  override val size: Int
    get() = backing.size

  override val keys: Set<String>
    get() = backing.keys

  override val values: Collection<String>
    get() = backing.values

  override val entries: Set<Map.Entry<String, String>>
    get() = backing.entries

  override fun isEmpty(): Boolean = backing.isEmpty()

  override fun containsKey(key: String): Boolean = backing.containsKey(key.lowercase())

  override fun containsValue(value: String): Boolean = backing.containsValue(value)

  override fun get(key: String): String? = backing[key.lowercase()]
}

/** The python request.state bag (setattr/getattr on request.state). */
public class MapRequestState : RequestState {
  private val attrs: MutableMap<String, Any?> = linkedMapOf()

  override fun getAttr(name: String): Any? = attrs[name]

  override fun setAttr(name: String, value: Any?) {
    attrs[name] = value
  }
}

/**
 * The body-caching request wrapper. Reads the servlet input stream exactly once into a growable
 * cache; bounded prefix reads stop pulling from the wire after the prefix while the downstream
 * servlet stream replays the cached bytes before continuing with the live remainder.
 */
public class GuardHttpServletRequest(request: HttpServletRequest) :
    HttpServletRequestWrapper(request) {
  public val stateBag: MapRequestState = MapRequestState()

  private val cache = ByteCache()
  private var streamOpened: Boolean = false

  @Synchronized
  public fun cachedBody(): ByteArray {
    openStreamIfNecessary()
    cache.readUpstream()
    return cache.snapshot()
  }

  @Synchronized
  public fun readPrefix(maxBytes: Int): ByteArray {
    if (maxBytes <= 0) {
      return ByteArray(0)
    }
    openStreamIfNecessary()
    cache.readUpstream(maxBytes)
    return cache.snapshot().copyOfRange(0, minOf(maxBytes, cache.size()))
  }

  private fun openStreamIfNecessary() {
    if (!streamOpened) {
      streamOpened = true
      cache.open(super.getInputStream())
    }
  }

  override fun getInputStream(): ServletInputStream {
    openStreamIfNecessary()
    return cache.replayingServletInputStream()
  }

  override fun getReader(): BufferedReader =
      BufferedReader(
          InputStreamReader(
              getInputStream(),
              request.characterEncoding?.let { Charset.forName(it) } ?: Charsets.UTF_8,
          ),
      )
}

/** Single-source byte cache serving both the facade reads and the downstream servlet stream. */
private class ByteCache {
  private var bytes: ByteArray = ByteArray(0)
  private var length: Int = 0
  private var upstream: ServletInputStream? = null
  private var upstreamFinished: Boolean = false

  fun open(stream: ServletInputStream) {
    upstream = stream
  }

  fun size(): Int = length

  fun snapshot(): ByteArray = bytes.copyOf(length)

  /** Pulls up to [limit] further bytes (unbounded when null) from the underlying stream. */
  fun readUpstream(limit: Int? = null) {
    val stream = upstream ?: return
    var pulled = 0
    while (limit == null || pulled < limit) {
      if (length == bytes.size) {
        bytes = bytes.copyOf(maxOf(1024, bytes.size * 2))
      }
      val n = stream.read(bytes, length, bytes.size - length)
      if (n < 0) {
        upstreamFinished = true
        break
      }
      if (n == 0) {
        break
      }
      length += n
      pulled += n
    }
  }

  fun replayingServletInputStream(): ServletInputStream =
      object : ServletInputStream() {
        private var replayPos: Int = 0

        override fun read(): Int {
          if (replayPos >= length) {
            readUpstream(1)
            if (replayPos >= length) {
              return -1
            }
          }
          val b = bytes[replayPos].toInt() and 0xff
          replayPos += 1
          return b
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
          if (replayPos >= length) {
            readUpstream(len)
            if (replayPos >= length) {
              return -1
            }
          }
          val n = minOf(len, length - replayPos)
          System.arraycopy(bytes, replayPos, b, off, n)
          replayPos += n
          return n
        }

        override fun available(): Int = length - replayPos

        override fun isFinished(): Boolean = replayPos >= length && upstreamFinished

        override fun isReady(): Boolean = true

        override fun setReadListener(listener: ReadListener?) {
          throw UnsupportedOperationException("Guard body replay does not support async reads")
        }
      }
}
