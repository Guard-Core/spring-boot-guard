package com.guardcore.spring

import com.guardcore.protocols.BoundedBodyReader
import com.guardcore.protocols.GuardRequest
import com.guardcore.protocols.RequestState
import jakarta.servlet.ReadListener
import jakarta.servlet.ServletException
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
 *
 * The container's own form parsing (`getParts` for multipart, `getParameter*` for urlencoded
 * bodies) reads the container's input directly and never sees this replay, so the two form flavors
 * are handled apart from the replay cache: multipart is scanned from the container-parsed parts,
 * and urlencoded bodies are merged into the parameter accessors from the cache.
 */
public class GuardHttpServletRequest(request: HttpServletRequest) :
    HttpServletRequestWrapper(request) {
  public val stateBag: MapRequestState = MapRequestState()

  private val cache = ByteCache()
  private var streamOpened: Boolean = false
  private var multipartPrefix: ByteArray? = null
  private var multipartPrefixLimit: Int = 0

  private val isMultipart: Boolean
    get() = contentType?.lowercase()?.startsWith(MULTIPART_FORM_DATA) == true

  private val isUrlEncodedForm: Boolean
    get() = contentType?.lowercase()?.startsWith(URLENCODED_FORM) == true

  @Synchronized
  public fun cachedBody(): ByteArray {
    multipartScanBytes(Int.MAX_VALUE)?.let {
      return it
    }
    openStreamIfNecessary()
    cache.readUpstream()
    return cache.snapshot()
  }

  @Synchronized
  public fun readPrefix(maxBytes: Int): ByteArray {
    if (maxBytes <= 0) {
      return ByteArray(0)
    }
    multipartScanBytes(maxBytes)?.let {
      return it.copyOfRange(0, minOf(maxBytes, it.size))
    }
    openStreamIfNecessary()
    cache.readUpstream(maxBytes)
    return cache.snapshot().copyOfRange(0, minOf(maxBytes, cache.size()))
  }

  /**
   * A multipart/form-data body rebuilt from the container-parsed parts (each part's headers, then
   * its payload), reading payloads only until the rebuilt body reaches [maxBytes] in total. This is
   * what the engine's multipart scan consumes (it scans file names, part headers and part payloads,
   * the way the Python reference parses the capped raw body). The container spools large file parts
   * to disk, so at most [maxBytes] of an upload is held in memory. Null when the request is not
   * multipart or the container cannot parse parts (no multipart config); the raw replay path then
   * applies.
   */
  private fun multipartScanBytes(maxBytes: Int): ByteArray? {
    if (!isMultipart || streamOpened) {
      return null
    }
    multipartPrefix?.let { built ->
      if (multipartPrefixLimit >= maxBytes) {
        return built
      }
    }
    val boundary = multipartBoundary() ?: return null
    val parts =
        try {
          super.getParts()
        } catch (_: ServletException) {
          return null
        } catch (_: IllegalStateException) {
          return null
        } catch (_: java.io.IOException) {
          return null
        }
    val out = java.io.ByteArrayOutputStream()
    for (part in parts) {
      if (out.size() >= maxBytes) {
        break
      }
      out.write("--$boundary\r\n".toByteArray())
      for (name in part.headerNames) {
        for (value in part.getHeaders(name)) {
          out.write("$name: $value\r\n".toByteArray())
        }
      }
      out.write("\r\n".toByteArray())
      part.inputStream.use { out.write(it.readNBytes(maxOf(0, maxBytes - out.size()))) }
      out.write("\r\n".toByteArray())
    }
    out.write("--$boundary--\r\n".toByteArray())
    return out.toByteArray().also {
      multipartPrefix = it
      multipartPrefixLimit = maxBytes
    }
  }

  private fun multipartBoundary(): String? =
      BOUNDARY_PARAM.find(contentType ?: "")?.groupValues?.get(1)

  /** Body parameters of a urlencoded form, appended after the query parameters (Tomcat order). */
  private fun mergedParameters(): Map<String, Array<String>>? {
    if (!streamOpened || !isUrlEncodedForm) {
      return null
    }
    // Drain the live remainder first so the container's own parameter parse sees a finished stream
    // instead of parsing the unread tail as if it were the whole body.
    cachedBody()
    val merged = linkedMapOf<String, MutableList<String>>()
    for ((name, values) in super.getParameterMap()) {
      merged.getOrPut(name) { mutableListOf() }.addAll(values)
    }
    val charset = characterEncoding?.let { Charset.forName(it) } ?: Charsets.UTF_8
    for (pair in String(cache.snapshot(), Charsets.ISO_8859_1).split("&")) {
      if (pair.isEmpty()) {
        continue
      }
      val separator = pair.indexOf('=')
      val rawName = if (separator < 0) pair else pair.substring(0, separator)
      val rawValue = if (separator < 0) "" else pair.substring(separator + 1)
      merged
          .getOrPut(decodeForm(rawName, charset)) { mutableListOf() }
          .add(decodeForm(rawValue, charset))
    }
    return merged.mapValues { it.value.toTypedArray() }
  }

  private fun decodeForm(raw: String, charset: Charset): String =
      URLDecoder.decode(String(raw.toByteArray(Charsets.ISO_8859_1), charset), charset)

  private var formParameterCache: Map<String, Array<String>>? = null

  @Synchronized
  private fun formParameters(): Map<String, Array<String>>? =
      formParameterCache ?: mergedParameters()?.also { formParameterCache = it }

  override fun getParameterMap(): Map<String, Array<String>> =
      formParameters() ?: super.getParameterMap()

  override fun getParameter(name: String): String? =
      formParameters()?.let { it[name]?.firstOrNull() } ?: super.getParameter(name)

  override fun getParameterValues(name: String): Array<String>? =
      formParameters()?.let { it[name] } ?: super.getParameterValues(name)

  override fun getParameterNames(): java.util.Enumeration<String> =
      formParameters()?.let { java.util.Collections.enumeration(it.keys) }
          ?: super.getParameterNames()

  private fun openStreamIfNecessary() {
    if (!streamOpened) {
      streamOpened = true
      cache.open(super.getInputStream())
    }
  }

  override fun getInputStream(): ServletInputStream {
    if (isMultipart && multipartPrefix != null) {
      return super.getInputStream()
    }
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

  private companion object {
    const val MULTIPART_FORM_DATA = "multipart/form-data"
    const val URLENCODED_FORM = "application/x-www-form-urlencoded"
    val BOUNDARY_PARAM = Regex("""boundary="?([^";\s]+)"?""", RegexOption.IGNORE_CASE)
  }
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
