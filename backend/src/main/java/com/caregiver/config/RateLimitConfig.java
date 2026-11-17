package com.caregiver.config;

import com.caregiver.ratelimit.RateLimitFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the rate-limit filter into the security chain only (not the servlet chain). */
@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitConfig {

  @Bean
  RateLimitFilter rateLimitFilter(RateLimitProperties properties, ObjectMapper objectMapper) {
    return new RateLimitFilter(properties, objectMapper, System::currentTimeMillis);
  }

  /** Prevent Boot from also registering the filter directly with the container. */
  @Bean
  FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(RateLimitFilter filter) {
    var registration = new FilterRegistrationBean<>(filter);
    registration.setEnabled(false);
    return registration;
  }
}
