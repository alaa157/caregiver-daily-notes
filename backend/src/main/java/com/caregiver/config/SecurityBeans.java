package com.caregiver.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Password hashing beans. Plaintext passwords are never stored. */
@Configuration
public class SecurityBeans {

  /**
   * Fixed, valid BCrypt hash of the sentinel {@code "dummy-password-for-timing-parity"}.
   * Used only to run a real hash verification on unknown-email logins so missing
   * and wrong credentials take the same time. It is not a caregiver record and
   * verifies no real password.
   */
  public static final String DUMMY_HASH =
      "$2a$10$BJNSE3OBC2mkUIMWe8/7OOYloIGE1qiru6OM.Frb2yxSf0hJX/PtC";

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }
}
