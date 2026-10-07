package com.caregiver.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/** Stateless JWT filter: installs the verified {@link CaregiverPrincipal}. */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private static final String SCHEME = "Bearer ";

  private final JwtService jwtService;
  private final ObjectMapper objectMapper;

  public JwtAuthenticationFilter(JwtService jwtService, ObjectMapper objectMapper) {
    this.jwtService = jwtService;
    this.objectMapper = objectMapper;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String header = request.getHeader("Authorization");
    if (header == null || header.isBlank()) {
      chain.doFilter(request, response);
      return;
    }
    if (header.length() <= SCHEME.length()
        || !header.regionMatches(true, 0, SCHEME, 0, SCHEME.length())) {
      deny(response);
      return;
    }
    String token = header.substring(SCHEME.length()).trim();
    if (token.isEmpty()) {
      deny(response);
      return;
    }
    try {
      JwtPrincipal principal = jwtService.parse(token);
      var authentication = new UsernamePasswordAuthenticationToken(
          new CaregiverPrincipal(principal.caregiverId()), null, List.of());
      SecurityContextHolder.getContext().setAuthentication(authentication);
      chain.doFilter(request, response);
    } catch (InvalidTokenException e) {
      deny(response);
    }
  }

  private void deny(HttpServletResponse response) throws IOException {
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    objectMapper.writeValue(response.getWriter(), ErrorResponse.unauthorized());
  }
}
