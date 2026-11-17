package com.caregiver.ratelimit;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Buffers a small request body so the rate-limit filter can inspect it and the
 * controller still receives a fresh, unread stream. Unlike
 * {@code ContentCachingRequestWrapper}, each {@link #getInputStream()} call
 * returns a new stream over the cached bytes.
 */
final class CachedBodyRequestWrapper extends HttpServletRequestWrapper {

  private static final int MAX_BODY_BYTES = 64 * 1024;

  private final byte[] body;

  CachedBodyRequestWrapper(HttpServletRequest request, byte[] body) {
    super(request);
    this.body = body;
  }

  /** Reads at most {@link #MAX_BODY_BYTES}; auth bodies are small JSON. */
  static byte[] readBody(HttpServletRequest request) {
    try {
      return request.getInputStream().readNBytes(MAX_BODY_BYTES);
    } catch (Exception unreadable) {
      return new byte[0];
    }
  }

  byte[] body() {
    return body;
  }

  @Override
  public ServletInputStream getInputStream() {
    return new ByteArrayServletInputStream(new ByteArrayInputStream(body));
  }

  @Override
  public BufferedReader getReader() {
    String encoding = getCharacterEncoding();
    var charset = encoding == null ? StandardCharsets.UTF_8 : java.nio.charset.Charset.forName(encoding);
    return new BufferedReader(new InputStreamReader(new ByteArrayInputStream(body), charset));
  }

  @Override
  public int getContentLength() {
    return body.length;
  }

  @Override
  public long getContentLengthLong() {
    return body.length;
  }

  private static final class ByteArrayServletInputStream extends ServletInputStream {
    private final ByteArrayInputStream delegate;

    private ByteArrayServletInputStream(ByteArrayInputStream delegate) {
      this.delegate = delegate;
    }

    @Override
    public boolean isFinished() {
      return delegate.available() == 0;
    }

    @Override
    public boolean isReady() {
      return true;
    }

    @Override
    public void setReadListener(ReadListener readListener) {
      throw new UnsupportedOperationException("Async reads are not supported for cached bodies");
    }

    @Override
    public int read() {
      return delegate.read();
    }
  }
}
