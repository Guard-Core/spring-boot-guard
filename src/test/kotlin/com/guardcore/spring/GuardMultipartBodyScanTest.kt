package com.guardcore.spring

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@RestController
public class MultipartTestController {
  @PostMapping("/m/upload")
  public fun upload(
      @RequestPart("file") file: MultipartFile,
      @RequestParam("note", required = false) note: String?,
  ): Map<String, Any?> =
      mapOf(
          "name" to file.originalFilename,
          "size" to file.size,
          "content" to String(file.bytes, Charsets.ISO_8859_1),
          "note" to note,
      )

  @PostMapping("/m/form")
  public fun form(@RequestParam("a") a: String): Map<String, String> = mapOf("a" to a)
}

@SpringBootConfiguration
@EnableAutoConfiguration
@org.springframework.context.annotation.Import(MultipartTestController::class)
public open class MultipartGuardTestApp

@SpringBootTest(
    classes = [MultipartGuardTestApp::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
)
@TestPropertySource(
    properties =
        [
            "guard.enable-redis=false",
            "guard.trusted-proxies[0]=127.0.0.1",
            "guard.detection-scan-body=true",
            "guard.detection-max-body-inspect-bytes=2048",
            "spring.servlet.multipart.max-file-size=8MB",
            "spring.servlet.multipart.max-request-size=9MB",
        ],
)
public class GuardMultipartBodyScanTest {
  @Value("\${local.server.port}") public var port: Int = 0

  private val client = HttpClient.newHttpClient()
  private val nextClient = java.util.concurrent.atomic.AtomicInteger(0)
  private val boundary = "----guardBoundary7MA4YWxkTrZu0gW"

  private fun multipart(file: ByteArray, note: String? = null): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    fun text(s: String) = out.write(s.toByteArray(Charsets.ISO_8859_1))
    if (note != null) {
      text("--$boundary\r\nContent-Disposition: form-data; name=\"note\"\r\n\r\n$note\r\n")
    }
    text(
        "--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"a.bin\"\r\n" +
            "Content-Type: application/octet-stream\r\n\r\n",
    )
    out.write(file)
    text("\r\n--$boundary--\r\n")
    return out.toByteArray()
  }

  private fun post(path: String, contentType: String, body: ByteArray): HttpResponse<String> =
      client.send(
          HttpRequest.newBuilder(URI("http://127.0.0.1:$port$path"))
              .header("Content-Type", contentType)
              .header("User-Agent", "Mozilla/5.0")
              .header("X-Forwarded-For", "198.51.100.${nextClient.incrementAndGet()}")
              .POST(HttpRequest.BodyPublishers.ofByteArray(body))
              .build(),
          HttpResponse.BodyHandlers.ofString(),
      )

  private fun upload(file: ByteArray, note: String? = null): HttpResponse<String> =
      post("/m/upload", "multipart/form-data; boundary=$boundary", multipart(file, note))

  @Test
  public fun `a small multipart upload reaches the controller intact with body scanning on`() {
    val response = upload("hello multipart".toByteArray(), note = "hi")
    assertThat(response.statusCode()).isEqualTo(200)
    assertThat(response.body())
        .contains("\"name\":\"a.bin\"", "\"size\":15", "hello multipart", "\"note\":\"hi\"")
  }

  @Test
  public fun `a multipart upload far larger than the inspect cap reaches the controller intact`() {
    val big = ByteArray(5 * 1024 * 1024) { ('a'.code + it % 26).toByte() }
    val response = upload(big)
    assertThat(response.statusCode()).isEqualTo(200)
    assertThat(response.body()).contains("\"size\":${big.size}")
    assertThat(response.body()).contains(String(big, 0, 64, Charsets.ISO_8859_1))
    assertThat(response.body()).contains(String(big, big.size - 64, 64, Charsets.ISO_8859_1))
  }

  @Test
  public fun `an attack payload in a multipart text field is still blocked`() {
    val response =
        upload("harmless".toByteArray(), note = "1 UNION SELECT username, password FROM users")
    assertThat(response.statusCode()).isEqualTo(400)
    assertThat(response.body()).isEqualTo("Suspicious activity detected")
  }

  @Test
  public fun `an attack payload inside a text file part is blocked like the reference`() {
    val response = upload("<script>alert(document.cookie)</script>".toByteArray())
    assertThat(response.statusCode()).isEqualTo(400)
  }

  @Test
  public fun `a urlencoded form still binds request params with body scanning on`() {
    val response = post("/m/form", "application/x-www-form-urlencoded", "a=1".toByteArray())
    assertThat(response.statusCode()).isEqualTo(200)
    assertThat(response.body()).contains("\"a\":\"1\"")
  }
}
