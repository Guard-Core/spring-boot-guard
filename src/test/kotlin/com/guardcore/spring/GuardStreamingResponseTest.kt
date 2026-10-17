package com.guardcore.spring

import jakarta.servlet.Filter
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.HttpServletRequest
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.core.Ordered
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.test.context.TestPropertySource
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.context.request.async.DeferredResult
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody
import org.springframework.web.util.ContentCachingResponseWrapper

private const val CHUNK_BYTES = 1024 * 1024
private const val CHUNKS = 64

/** Records which response flavor the downstream chain received, per request path. */
public object ResponseKindProbe {
  public val kinds: ConcurrentHashMap<String, String> = ConcurrentHashMap()
  public val release: CountDownLatch = CountDownLatch(1)
  public val sseRelease: CountDownLatch = CountDownLatch(1)
}

@RestController
public class StreamingTestController {
  @GetMapping("/s/big")
  public fun big(): StreamingResponseBody = StreamingResponseBody { out ->
    val chunk = ByteArray(CHUNK_BYTES) { 'x'.code.toByte() }
    out.write(chunk)
    out.flush()
    ResponseKindProbe.release.await(60, TimeUnit.SECONDS)
    repeat(CHUNKS - 1) { out.write(chunk) }
  }

  @GetMapping("/s/sse")
  public fun sse(): SseEmitter {
    val emitter = SseEmitter(0L)
    Thread {
      emitter.send(SseEmitter.event().name("tick").data("one"))
      ResponseKindProbe.sseRelease.await(60, TimeUnit.SECONDS)
      emitter.send(SseEmitter.event().name("tick").data("two"))
      emitter.complete()
    }
        .start()
    return emitter
  }

  @GetMapping("/s/deferred")
  public fun deferred(): DeferredResult<Map<String, String>> {
    val result = DeferredResult<Map<String, String>>()
    Thread { result.setResult(mapOf("deferred" to "true")) }.start()
    return result
  }

  @GetMapping("/s/json") public fun json(): Map<String, String> = mapOf("ok" to "true")

  @GetMapping("/s/teapot")
  @GuardReturnMonitor(pattern = "status:418", maxOccurrences = 1, window = 3600, action = "ban")
  public fun teapot(): ResponseEntity<String> =
      ResponseEntity.status(HttpStatus.I_AM_A_TEAPOT).body("short and stout")

  @GetMapping("/s/leaky")
  @GuardReturnMonitor(pattern = "leaked-token", maxOccurrences = 1, window = 3600, action = "ban")
  public fun leaky(): String = "here is a " + listOf("leaked", "token").joinToString("-")

  @GetMapping("/s/redirect")
  public fun redirect(response: jakarta.servlet.http.HttpServletResponse) {
    response.sendRedirect("/s/json")
  }
}

@SpringBootConfiguration
@EnableAutoConfiguration
@org.springframework.context.annotation.Import(StreamingTestController::class)
public open class StreamingGuardTestApp {
  @Bean
  public open fun responseKindProbe(): FilterRegistrationBean<Filter> =
      FilterRegistrationBean<Filter>(
              object : Filter {
                override fun doFilter(
                    req: ServletRequest,
                    res: ServletResponse,
                    chain: FilterChain,
                ) {
                  val path = (req as HttpServletRequest).requestURI
                  ResponseKindProbe.kinds[path] =
                      if (res is ContentCachingResponseWrapper) "buffered"
                      else res.javaClass.simpleName
                  chain.doFilter(req, res)
                }
              },
          )
          .apply { order = Ordered.LOWEST_PRECEDENCE }
}

@SpringBootTest(
    classes = [StreamingGuardTestApp::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
)
@TestPropertySource(
    properties =
        [
            "guard.enable-redis=false",
            "guard.behavior-scan-response-body=true",
            "guard.enable-cors=true",
            "guard.trusted-proxies[0]=127.0.0.1",
            "guard.cors-allow-origins[0]=https://app.example",
        ],
)
public class GuardStreamingResponseTest {
  @Value("\${local.server.port}") public var port: Int = 0

  private val client = HttpClient.newHttpClient()

  private fun request(path: String, ip: String = "198.51.100.7"): HttpRequest =
      HttpRequest.newBuilder(URI("http://127.0.0.1:$port$path"))
          .header("Origin", "https://app.example")
          .header("X-Forwarded-For", ip)
          .GET()
          .build()

  private fun HttpResponse<*>.assertGuardHeaders() {
    assertThat(headers().firstValue("X-Content-Type-Options")).hasValue("nosniff")
    assertThat(headers().firstValue("Access-Control-Allow-Origin")).hasValue("https://app.example")
  }

  @Test
  public fun `a 64 MB streamed body is never buffered and the first chunk arrives before the handler finishes`() {
    val response = client.send(request("/s/big"), HttpResponse.BodyHandlers.ofInputStream())
    assertThat(ResponseKindProbe.kinds["/s/big"]).isEqualTo("CommitHookResponse")
    response.assertGuardHeaders()
    response.body().use { body ->
      val first = body.readNBytes(CHUNK_BYTES)
      assertThat(first).hasSize(CHUNK_BYTES)
      // The handler is still parked on the latch: the first chunk reached the client before it
      // finished, which a buffering wrapper cannot do.
      assertThat(ResponseKindProbe.release.count).isEqualTo(1)
      ResponseKindProbe.release.countDown()
      var total = first.size.toLong()
      val sink = ByteArray(CHUNK_BYTES)
      while (true) {
        val n = body.read(sink)
        if (n < 0) break
        total += n
      }
      assertThat(total).isEqualTo(CHUNK_BYTES.toLong() * CHUNKS)
    }
  }

  @Test
  public fun `server-sent events arrive incrementally with the guard headers`() {
    val response = client.send(request("/s/sse"), HttpResponse.BodyHandlers.ofInputStream())
    assertThat(ResponseKindProbe.kinds["/s/sse"]).isEqualTo("CommitHookResponse")
    response.assertGuardHeaders()
    BufferedReader(InputStreamReader(response.body())).use { reader ->
      assertThat(reader.readLine()).isEqualTo("event:tick")
      assertThat(reader.readLine()).isEqualTo("data:one")
      ResponseKindProbe.sseRelease.countDown()
      reader.readLine()
      assertThat(reader.readLine()).isEqualTo("event:tick")
      assertThat(reader.readLine()).isEqualTo("data:two")
    }
  }

  @Test
  public fun `a deferred result completes through the same unbuffered wrapper with guard headers`() {
    val response = client.send(request("/s/deferred"), HttpResponse.BodyHandlers.ofString())
    assertThat(response.statusCode()).isEqualTo(200)
    assertThat(response.body()).contains("deferred")
    response.assertGuardHeaders()
  }

  @Test
  public fun `a plain json response streams unbuffered with security headers`() {
    val response = client.send(request("/s/json"), HttpResponse.BodyHandlers.ofString())
    assertThat(ResponseKindProbe.kinds["/s/json"]).isEqualTo("CommitHookResponse")
    assertThat(response.body()).contains("ok")
    response.assertGuardHeaders()
  }

  @Test
  public fun `a redirect carries the guard headers`() {
    val noFollow = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build()
    val response = noFollow.send(request("/s/redirect"), HttpResponse.BodyHandlers.ofString())
    assertThat(response.statusCode()).isEqualTo(302)
    response.assertGuardHeaders()
  }

  @Test
  public fun `a status return pattern bans without buffering the response`() {
    val ip = "198.51.100.21"
    val first = client.send(request("/s/teapot", ip), HttpResponse.BodyHandlers.ofString())
    assertThat(first.statusCode()).isEqualTo(418)
    assertThat(ResponseKindProbe.kinds["/s/teapot"]).isEqualTo("CommitHookResponse")
    // The tracker trips when the count exceeds the threshold, so the second hit bans.
    client.send(request("/s/teapot", ip), HttpResponse.BodyHandlers.ofString())
    val banned = client.send(request("/s/teapot", ip), HttpResponse.BodyHandlers.ofString())
    assertThat(banned.statusCode()).isEqualTo(403)
  }

  @Test
  public fun `a body return pattern buffers only that route and still bans`() {
    val ip = "198.51.100.22"
    val first = client.send(request("/s/leaky", ip), HttpResponse.BodyHandlers.ofString())
    assertThat(first.statusCode()).isEqualTo(200)
    assertThat(first.body()).isEqualTo("here is a leaked-token")
    first.assertGuardHeaders()
    assertThat(ResponseKindProbe.kinds["/s/leaky"]).isEqualTo("buffered")
    client.send(request("/s/leaky", ip), HttpResponse.BodyHandlers.ofString())
    val banned = client.send(request("/s/leaky", ip), HttpResponse.BodyHandlers.ofString())
    assertThat(banned.statusCode()).isEqualTo(403)
  }
}
