package com.caregiver.ratelimit;

import com.caregiver.auth.CaregiverPrincipal;
import com.caregiver.config.RateLimitProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitFilterTest {

  private static final String LOGIN = "/api/auth/login";
  private static final String SUMMARIES = "/api/summaries/byok";

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  private static RateLimitProperties props(boolean enabled, int ip, int account, int ai) {
    return new RateLimitProperties(enabled, ip, Duration.ofMinutes(1), account,
        Duration.ofMinutes(5), ai, Duration.ofMinutes(1));
  }

  private static RateLimitFilter filter(RateLimitProperties properties, AtomicLong clock) {
    return new RateLimitFilter(properties, new ObjectMapper(), clock::get);
  }

  private static MockHttpServletRequest post(String uri, String remoteAddr, String body) {
    var request = new MockHttpServletRequest("POST", uri);
    request.setRemoteAddr(remoteAddr);
    if (body != null) {
      request.setContentType("application/json");
      request.setContent(body.getBytes(StandardCharsets.UTF_8));
    }
    return request;
  }

  private static String loginBody(String email) {
    return "{\"email\":\"" + email + "\",\"password\":\"whatever\"}";
  }

  private static void run(RateLimitFilter filter, MockHttpServletRequest request,
      MockHttpServletResponse response, MockFilterChain chain) throws Exception {
    filter.doFilter(request, response, chain);
  }

  private static void assertDenied(MockHttpServletResponse response, MockFilterChain chain)
      throws Exception {
    assertThat(response.getStatus()).isEqualTo(429);
    assertThat(response.getHeader("Retry-After")).isNotNull();
    assertThat(Long.parseLong(response.getHeader("Retry-After"))).isGreaterThanOrEqualTo(1);
    assertThat(response.getContentAsString()).contains("\"code\":\"RATE_LIMITED\"");
    assertThat(chain.getRequest()).isNull();
  }

  private static void assertPassed(MockFilterChain chain) {
    assertThat(chain.getRequest()).isNotNull();
  }

  private static void asCaregiver(UUID id) {
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken(new CaregiverPrincipal(id), null, List.of()));
  }

  @Test
  void loginIsLimitedPerIp() throws Exception {
    var filter = filter(props(true, 2, 100, 100), new AtomicLong(0));
    for (int i = 0; i < 2; i++) {
      var chain = new MockFilterChain();
      run(filter, post(LOGIN, "203.0.113.10", loginBody("a@example.com")),
          new MockHttpServletResponse(), chain);
      assertPassed(chain);
    }
    var deniedResponse = new MockHttpServletResponse();
    var deniedChain = new MockFilterChain();
    run(filter, post(LOGIN, "203.0.113.10", loginBody("a@example.com")), deniedResponse, deniedChain);
    assertDenied(deniedResponse, deniedChain);
  }

  @Test
  void loginIsLimitedPerAccountRegardlessOfIp() throws Exception {
    var filter = filter(props(true, 100, 2, 100), new AtomicLong(0));
    for (int i = 0; i < 2; i++) {
      var chain = new MockFilterChain();
      run(filter, post(LOGIN, "203.0.113." + (10 + i), loginBody("target@example.com")),
          new MockHttpServletResponse(), chain);
      assertPassed(chain);
    }
    var deniedResponse = new MockHttpServletResponse();
    var deniedChain = new MockFilterChain();
    run(filter, post(LOGIN, "198.51.100.77", loginBody("target@example.com")), deniedResponse, deniedChain);
    assertDenied(deniedResponse, deniedChain);
  }

  @Test
  void differentAccountsAreIndependent() throws Exception {
    var filter = filter(props(true, 100, 1, 100), new AtomicLong(0));

    var first = new MockFilterChain();
    run(filter, post(LOGIN, "203.0.113.10", loginBody("alice@example.com")),
        new MockHttpServletResponse(), first);
    var second = new MockFilterChain();
    run(filter, post(LOGIN, "203.0.113.10", loginBody("bob@example.com")),
        new MockHttpServletResponse(), second);
    assertPassed(first);
    assertPassed(second);

    var deniedResponse = new MockHttpServletResponse();
    var deniedChain = new MockFilterChain();
    run(filter, post(LOGIN, "203.0.113.10", loginBody("alice@example.com")), deniedResponse, deniedChain);
    assertDenied(deniedResponse, deniedChain);
  }

  @Test
  void accountEmailIsNormalizedBeforeKeying() throws Exception {
    var filter = filter(props(true, 100, 1, 100), new AtomicLong(0));

    run(filter, post(LOGIN, "203.0.113.10", loginBody("Alice@Example.com")),
        new MockHttpServletResponse(), new MockFilterChain());
    var deniedResponse = new MockHttpServletResponse();
    var deniedChain = new MockFilterChain();
    run(filter, post(LOGIN, "198.51.100.20", loginBody("  alice@example.com  ")), deniedResponse, deniedChain);
    assertDenied(deniedResponse, deniedChain);
  }

  @Test
  void aiRequestsAreLimitedPerCaregiver() throws Exception {
    var filter = filter(props(true, 100, 100, 1), new AtomicLong(0));
    UUID first = UUID.randomUUID();
    UUID second = UUID.randomUUID();

    asCaregiver(first);
    var firstChain = new MockFilterChain();
    run(filter, post(SUMMARIES, "203.0.113.10", "{}"), new MockHttpServletResponse(), firstChain);
    assertPassed(firstChain);

    asCaregiver(second);
    var otherChain = new MockFilterChain();
    run(filter, post(SUMMARIES, "203.0.113.10", "{}"), new MockHttpServletResponse(), otherChain);
    assertPassed(otherChain);

    asCaregiver(first);
    var deniedResponse = new MockHttpServletResponse();
    var deniedChain = new MockFilterChain();
    run(filter, post(SUMMARIES, "203.0.113.10", "{}"), deniedResponse, deniedChain);
    assertDenied(deniedResponse, deniedChain);
  }

  @Test
  void aiFallsBackToIpWhenThereIsNoVerifiedCaregiver() throws Exception {
    var filter = filter(props(true, 100, 100, 1), new AtomicLong(0));

    run(filter, post(SUMMARIES, "203.0.113.10", "{}"), new MockHttpServletResponse(), new MockFilterChain());
    var deniedResponse = new MockHttpServletResponse();
    var deniedChain = new MockFilterChain();
    run(filter, post(SUMMARIES, "203.0.113.10", "{}"), deniedResponse, deniedChain);
    assertDenied(deniedResponse, deniedChain);
  }

  @Test
  void otherEndpointsAreNotFiltered() throws Exception {
    var filter = filter(props(true, 1, 100, 100), new AtomicLong(0));
    run(filter, post(LOGIN, "203.0.113.10", loginBody("a@example.com")),
        new MockHttpServletResponse(), new MockFilterChain());
    run(filter, post(LOGIN, "203.0.113.10", loginBody("a@example.com")),
        new MockHttpServletResponse(), new MockFilterChain());

    var chain = new MockFilterChain();
    run(filter, post("/api/summaries", "203.0.113.10", "{}"), new MockHttpServletResponse(), chain);
    assertPassed(chain);
  }

  @Test
  void disabledConfigurationBypassesAllLimits() throws Exception {
    var filter = filter(props(false, 1, 1, 1), new AtomicLong(0));
    for (int i = 0; i < 5; i++) {
      var chain = new MockFilterChain();
      run(filter, post(LOGIN, "203.0.113.10", loginBody("a@example.com")),
          new MockHttpServletResponse(), chain);
      assertPassed(chain);
    }
  }
}
