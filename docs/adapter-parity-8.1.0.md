# Adapter parity: fastapi-guard 8.1.0 (PureASGISecurityMiddleware)

Assessment of the W10 parity wave (2026-10-13): what fastapi-guard 8.1.0
changed, how this adapter's native idiom maps onto it, and which of the
pure-streaming properties hold here. Conclusion up front: the servlet filter
idiom already satisfies the cancellation and body-replay properties; the
unbuffered-streaming property does NOT hold (the response wrapper buffers),
which is a deliberate, documented trade of this adapter and not something this
wave changes.

## What upstream shipped

fastapi-guard 8.1.0 (2026-10-10, #156) added `PureASGISecurityMiddleware`: the
full screening pipeline running on raw ASGI without `BaseHTTPMiddleware`. It
exists to win three properties the classic middleware loses to Starlette's
anyio task group:

1. No downstream wrapping that could cancel in-flight work (a client
   disconnect can never cancel an in-flight database call).
2. Unbuffered response streaming (security and CORS headers injected directly
   on `http.response.start`, body bytes never held).
3. A caching receive that replays the request body to the application after
   the guard reads it.

Scope note from the upstream release: decorator-driven response rules (return
patterns) and `custom_response_modifier` need a materialized `Response`
object; they stay on the classic middleware and both classes ship side by
side.

## The idiom mapping

This adapter's native idiom is the servlet envelope: `GuardFilter`, a Spring
`OncePerRequestFilter` placed pre-routing so unmatched paths are also
scanned. The servlet container hands the filter the raw request/response and
the downstream chain; there is no `BaseHTTPMiddleware` analog to avoid -
nothing ever wrapped the downstream app in a task group here. The PureASGI
release validates an idiom this family already holds natively, the same way
fastapi-guard's Starlette-only constraint never applied to the servlet lane.

## Property-by-property

### 1. No downstream cancelling wrap: HOLDS

`GuardFilter.doFilterInternal` runs `filterChain.doFilter` synchronously on
the same servlet thread. No coroutine scope wraps the downstream call: the
guard's own suspend stages bridge through `runBlocking` confined to the guard
entry point (the facade's xBlocking bridge convention), and even the
security-bypass path's downstream leg runs under plain `runBlocking`, which
has no cancellation source. A client disconnect cannot cancel in-flight
handler work in this model; it surfaces later, as a write failure on the
committed response. This is the property PureASGI exists to win on Starlette;
the servlet lane never had the problem.

### 2. A caching receive that replays the body: HOLDS

`GuardHttpServletRequest` (ServletGuardRequest.kt) reads the servlet input
stream exactly once into a single-source byte cache and hands the downstream
chain a replaying `ServletInputStream`: the bytes the penetration scan saw
are the bytes the handler sees, with bounded prefix reads stopping the wire
pull at the cap. This is the exact contract of the reference's caching
receive (and Starlette's own `_body` cache it mirrors).

### 3. Unbuffered response streaming: DOES NOT HOLD (documented finding)

`GuardFilter` wraps the response in Spring's `ContentCachingResponseWrapper`
and calls `copyBodyToResponse()` only after the facade's `process_response`
exit path and CORS injection run. Every response body is fully materialized
in memory before the first byte reaches the client: SSE streams, file
downloads, and large bodies included. The wrapper is what lets the adapter
post-process a handler's response before it commits (behavioral return rules,
the custom response modifier, response-time metrics, CORS into the buffered
response), which is the classic-middleware feature set.

This is the trade fastapi-guard 8.1.0's scope note describes, seen from the
other side: response rules and the response modifier need a materialized
response, so they live on the buffering idiom. The servlet adapter IS the
classic middleware of the JVM family; the buffering is its cost, not a parity
bug. Reported, not fixed, per the W10 scope: the fix shape, if ever needed
for SSE-at-scale deployments, is a second header-pre-commit filter variant
shipping beside `GuardFilter` (the PureASGI-beside-SecurityMiddleware
pattern), not a change to `GuardFilter`'s contract.

## Verdict

Zero code changes in the 8.1.0 parity wave. Two of the three pure-streaming
properties hold natively; the third (unbuffered streaming) is a documented
deliberate trade of the buffering idiom this adapter is built on, priced
exactly as upstream prices it: the features that need a materialized response
are the features this adapter ships.
