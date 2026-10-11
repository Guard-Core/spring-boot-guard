package com.guardcore.spring

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest

/**
 * The servlet protocol adapter unit surface (the StarletteGuardRequest/Response anchors,
 * fastapi-guard guard/adapters.py).
 */
public class ServletGuardProtocolTest {
  @Test
  public fun `url path strips the context path and keeps the route path`() {
    val http = MockHttpServletRequest()
    http.contextPath = "/app"
    http.requestURI = "/app/api/items"
    val guard = ServletGuardRequest(GuardHttpServletRequest(http))
    assertEquals("/api/items", guard.urlPath)

    val root = MockHttpServletRequest()
    root.contextPath = ""
    root.requestURI = "/api/items"
    assertEquals("/api/items", ServletGuardRequest(GuardHttpServletRequest(root)).urlPath)
  }

  @Test
  public fun `headers are case-insensitive and repeated headers join with comma space`() {
    val http = MockHttpServletRequest()
    http.addHeader("Content-Type", "application/json")
    http.addHeader("X-Dup", "a")
    http.addHeader("x-dup", "b")
    val guard = ServletGuardRequest(GuardHttpServletRequest(http))
    assertEquals("application/json", guard.headers["CONTENT-TYPE"])
    assertEquals("application/json", guard.headers["content-type"])
    assertEquals("a, b", guard.headers["X-Dup"])
  }

  @Test
  public fun `body reads are cached and repeatable`() {
    val http = MockHttpServletRequest()
    http.setContent("hello world".toByteArray())
    http.method = "POST"
    val guard = ServletGuardRequest(GuardHttpServletRequest(http))
    val first = kotlinx.coroutines.runBlocking { guard.body() }
    val second = kotlinx.coroutines.runBlocking { guard.body() }
    assertEquals("hello world", first.decodeToString())
    assertEquals(first.decodeToString(), second.decodeToString())
    val prefix = kotlinx.coroutines.runBlocking { guard.readBodyPrefix(5) }
    assertEquals("hello", prefix.decodeToString())
  }

  @Test
  public fun `bounded prefix read leaves the remainder available downstream`() {
    val http = MockHttpServletRequest()
    http.setContent("abcdefghij".toByteArray())
    http.method = "POST"
    val wrapper = GuardHttpServletRequest(http)
    val guard = ServletGuardRequest(wrapper)
    val prefix = kotlinx.coroutines.runBlocking { guard.readBodyPrefix(4) }
    assertEquals("abcd", prefix.decodeToString())
    val downstream = wrapper.getInputStream().readBytes()
    assertEquals("abcdefghij", downstream.decodeToString())
  }

  @Test
  public fun `url scheme method client host and query params observe the starlette contract`() {
    val http = MockHttpServletRequest()
    http.method = "POST"
    http.setScheme("http")
    http.remoteAddr = "203.0.113.7"
    http.requestURI = "/api/search"
    http.setQueryString("q=%2Fsafe%3D1&flag=&q=second")
    val guard = ServletGuardRequest(GuardHttpServletRequest(http))
    assertEquals("POST", guard.method)
    assertEquals("http", guard.urlScheme)
    assertEquals("203.0.113.7", guard.clientHost)
    assertEquals("http://localhost/api/search?q=%2Fsafe%3D1&flag=&q=second", guard.urlFull)
    assertEquals("second", guard.queryParams["q"])
    assertEquals("", guard.queryParams["flag"])
    assertEquals(
        "https://localhost/api/search?q=%2Fsafe%3D1&flag=&q=second",
        guard.urlReplaceScheme("https"),
    )
  }

  @Test
  public fun `state bag stores attributes across calls`() {
    val guard = ServletGuardRequest(GuardHttpServletRequest(MockHttpServletRequest()))
    guard.state.setAttr("client_ip", "203.0.113.7")
    assertEquals("203.0.113.7", guard.state.getAttr("client_ip"))
  }

  @Test
  public fun `plain response factory matches the starlette header shapes`() {
    val factory = ServletGuardResponseFactory()
    val response = factory.createResponse("Blocked", 403)
    assertEquals(403, response.statusCode)
    assertTrue(java.util.Arrays.equals("Blocked".toByteArray(), response.body))
    assertEquals("text/plain; charset=utf-8", response.headers["Content-Type"])
    assertEquals("7", response.headers["Content-Length"])

    val redirect = factory.createRedirectResponse("https://localhost/x", 301)
    assertEquals(301, redirect.statusCode)
    assertEquals("https://localhost/x", redirect.headers["Location"])
    assertEquals("0", redirect.headers["Content-Length"])
    assertNull(redirect.wrapped)
  }

  @Test
  public fun `route keys are method-identity strong - same name different params differ`() {
    val bean = TestMethodRoute()
    val routeKey =
        HandlerRouteKey(
            org.springframework.web.method.HandlerMethod(
                bean,
                TestMethodRoute::class.java.getDeclaredMethod("handler", String::class.java),
            ),
        )
    val routeKey2 =
        HandlerRouteKey(
            org.springframework.web.method.HandlerMethod(
                bean,
                TestMethodRoute::class
                    .java
                    .getDeclaredMethod(
                        "handler",
                        Int::class.javaPrimitiveType,
                    ),
            ),
        )
    assertEquals("${TestMethodRoute::class.java.name}.handler(java.lang.String)", routeKey.routeKey)
    assertTrue(routeKey.routeKey != routeKey2.routeKey)
    assertTrue(routeKey.stampedRouteId == null)
  }

  public class TestMethodRoute {
    public fun handler(id: String): String = id

    public fun handler(id: Int): String = id.toString()
  }
}
