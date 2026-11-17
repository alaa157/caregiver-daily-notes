package com.caregiver.ratelimit;

import com.caregiver.auth.AuthContext;
import com.caregiver.auth.ErrorResponse;
import com.caregiver.config.RateLimitProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Applies per-IP and per-account ceilings to the open auth endpoints and a
 * per-caregiver ceiling to the BYOK AI routes. Runs inside the security chain
 * after the JWT filter so a verified caregiver id is available.
 *
 * <p>Only the configured paths are touched; normal usage is unaffected. Each
 * denial renders the shared {@code {"code","message"}} error envelope with a
 * stable {@code RATE_LIMITED} code and a {@code Retry-After} header.</p>
 */
public final class RateLimitFilter extends OncePerRequestFilter {

  public static final String ERROR_CODE = "RATE_LIMITED";
  private static final Set<String> AUTH_PATHS = Set.of("/api/auth/login", "/api/auth/register");
  private static final Set<String> AI_PATHS = Set.of(
      "/api/summaries/byok", "/api/plans/generate/byok", "/api/ai/byok/connection-test");

  private final RateLimitProperties properties;
  private final ObjectMapper objectMapper;
  private final TokenBucketLimiter authIpLimiter;
  private final TokenBucketLimiter authAccountLimiter;
  private final TokenBucketLimiter aiLimiter;

  public RateLimitFilter(RateLimitProperties properties, ObjectMapper objectMapper, LongSupplier clockMillis) {
    this.properties = properties;
    this.objectMapper = objectMapper;
    this.authIpLimiter = new TokenBucketLimiter(
        properties.authIpMaxRequests(), properties.authIpWindow(), clockMillis);
    this.authAccountLimiter = new TokenBucketLimiter(
        properties.authAccountMaxRequests(), properties.authAccountWindow(), clockMillis);
    this.aiLimiter = new TokenBucketLimiter(properties.aiMaxRequests(), properties.aiWindow(), clockMillis);
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !properties.enabled() || !isTarget(request);
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String path = path(request);
    HttpServletRequest downstream = request;

    if (AUTH_PATHS.contains(path)) {
      if (!allow("ip:" + request.getRemoteAddr(), authIpLimiter, response)) {
        return;
      }
      // Login/register bodies are small JSON. Cache what we read so the
      // controller still sees an unread body after the per-account check.
      byte[] body = CachedBodyRequestWrapper.readBody(request);
      downstream = new CachedBodyRequestWrapper(request, body);
      String email = extractEmail(body);
      if (email != null && !allow("account:" + email, authAccountLimiter, response)) {
        return;
      }
    } else if (AI_PATHS.contains(path)) {
      if (!allow(aiKey(request), aiLimiter, response)) {
        return;
      }
    }

    chain.doFilter(downstream, response);
  }

  private boolean allow(String key, TokenBucketLimiter limiter, HttpServletResponse response) throws IOException {
    TokenBucketLimiter.Decision decision = limiter.tryConsume(key);
    if (decision.allowed()) {
      return true;
    }
    deny(response, decision.retryAfterSeconds(), limiter.capacity());
    return false;
  }

  private void deny(HttpServletResponse response, long retryAfterSeconds, int limit) throws IOException {
    response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
    response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds));
    response.setHeader("X-RateLimit-Limit", Integer.toString(limit));
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    objectMapper.writeValue(response.getWriter(),
        new ErrorResponse(ERROR_CODE, "Too many requests. Please try again later."));
  }

  /** Verified caregiver id when present, otherwise the source IP as a fallback. */
  private String aiKey(HttpServletRequest request) {
    try {
      UUID caregiverId = AuthContext.currentCaregiverId();
      return "caregiver:" + caregiverId;
    } catch (RuntimeException noVerifiedPrincipal) {
      return "ip:" + request.getRemoteAddr();
    }
  }

  private String extractEmail(byte[] body) {
    try {
      if (body.length == 0) {
        return null;
      }
      JsonNode json = objectMapper.readTree(body);
      if (json == null || !json.isObject() || !json.hasNonNull("email")) {
        return null;
      }
      String email = json.get("email").asText(null);
      if (email == null) {
        return null;
      }
      email = email.trim().toLowerCase(Locale.ROOT);
      return email.isEmpty() ? null : email;
    } catch (Exception malformedOrUnreadable) {
      // Malformed bodies are reported by MVC validation; the IP limit still
      // applies, so never fail the request here.
      return null;
    }
  }

  private static boolean isTarget(HttpServletRequest request) {
    if (!"POST".equalsIgnoreCase(request.getMethod())) {
      return false;
    }
    String path = path(request);
    return AUTH_PATHS.contains(path) || AI_PATHS.contains(path);
  }

  private static String path(HttpServletRequest request) {
    String uri = request.getRequestURI();
    String context = request.getContextPath();
    return context != null && !context.isEmpty() && uri.startsWith(context)
        ? uri.substring(context.length()) : uri;
  }
}
