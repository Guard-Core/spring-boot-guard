package com.guardcore.spring

import jakarta.servlet.ServletOutputStream
import jakarta.servlet.WriteListener
import jakarta.servlet.http.HttpServletResponse
import jakarta.servlet.http.HttpServletResponseWrapper
import java.io.PrintWriter

/**
 * The unbuffered response flavor (the `PureASGISecurityMiddleware` response path of fastapi-guard,
 * guard/asgi.py): the body streams straight to the container and [beforeCommit] runs exactly once,
 * just before the first byte can leave (first write, flush, close, sendError, sendRedirect), the
 * servlet analog of the `http.response.start` message. Headers set by the hook therefore still
 * reach the client, and nothing is held in memory.
 */
internal class CommitHookResponse(response: HttpServletResponse) :
    HttpServletResponseWrapper(response) {
  var beforeCommit: (() -> Unit)? = null
  private var fired = false
  private var stream: ServletOutputStream? = null
  private var writer: PrintWriter? = null

  /** Runs the hook once; safe to call from any commit point and again after the chain returns. */
  fun fireBeforeCommit() {
    if (fired) {
      return
    }
    fired = true
    beforeCommit?.invoke()
  }

  override fun getOutputStream(): ServletOutputStream =
      stream ?: HookedStream(super.getOutputStream()).also { stream = it }

  override fun getWriter(): PrintWriter =
      writer ?: HookedWriter(super.getWriter()).also { writer = it }

  override fun flushBuffer() {
    fireBeforeCommit()
    super.flushBuffer()
  }

  // The status is stamped first so the hook (metrics, status behavior rules) sees the real one.
  override fun sendError(sc: Int) {
    super.setStatus(sc)
    fireBeforeCommit()
    super.sendError(sc)
  }

  override fun sendError(sc: Int, msg: String?) {
    super.setStatus(sc)
    fireBeforeCommit()
    super.sendError(sc, msg)
  }

  override fun sendRedirect(location: String?) {
    super.setStatus(HttpServletResponse.SC_FOUND)
    fireBeforeCommit()
    super.sendRedirect(location)
  }

  private inner class HookedStream(private val delegate: ServletOutputStream) :
      ServletOutputStream() {
    override fun write(b: Int) {
      fireBeforeCommit()
      delegate.write(b)
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
      fireBeforeCommit()
      delegate.write(b, off, len)
    }

    override fun flush() {
      fireBeforeCommit()
      delegate.flush()
    }

    override fun close() {
      fireBeforeCommit()
      delegate.close()
    }

    override fun isReady(): Boolean = delegate.isReady

    override fun setWriteListener(listener: WriteListener) {
      delegate.setWriteListener(listener)
    }
  }

  private inner class HookedWriter(delegate: PrintWriter) : PrintWriter(delegate) {
    override fun write(c: Int) {
      fireBeforeCommit()
      super.write(c)
    }

    override fun write(buf: CharArray, off: Int, len: Int) {
      fireBeforeCommit()
      super.write(buf, off, len)
    }

    override fun write(s: String, off: Int, len: Int) {
      fireBeforeCommit()
      super.write(s, off, len)
    }

    override fun flush() {
      fireBeforeCommit()
      super.flush()
    }

    override fun close() {
      fireBeforeCommit()
      super.close()
    }
  }
}
