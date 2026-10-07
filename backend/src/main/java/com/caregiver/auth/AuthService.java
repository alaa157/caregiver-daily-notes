package com.caregiver.auth;

import com.caregiver.config.SecurityBeans;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Caregiver registration and login. Error mapping lands in Task 8. */
@Service
public class AuthService {

  /** BCrypt truncates past 72 bytes — reject instead of silently truncating. */
  static final int MAX_PASSWORD_BYTES = 72;

  private final CaregiverRepository repository;
  private final PasswordEncoder encoder;
  private final JwtService jwtService;

  public AuthService(
      CaregiverRepository repository, PasswordEncoder encoder, JwtService jwtService) {
    this.repository = repository;
    this.encoder = encoder;
    this.jwtService = jwtService;
  }

  @Transactional
  public AuthResponse register(String email, String password) {
    String normalized = normalize(email);
    if (normalized.isEmpty()) {
      throw new ValidationException("Email is required.");
    }
    if (!normalized.contains("@")) {
      throw new ValidationException("Enter a valid email address.");
    }
    if (password == null || password.isEmpty()) {
      throw new ValidationException("Password is required.");
    }
    if (password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
      throw new ValidationException("Password must be at most 72 bytes.");
    }
    if (repository.existsByEmail(normalized)) {
      throw new DuplicateEmailException("Email is already registered.");
    }
    Caregiver caregiver = new Caregiver();
    caregiver.setEmail(normalized);
    caregiver.setPasswordHash(encoder.encode(password));
    try {
      caregiver = repository.saveAndFlush(caregiver);
    } catch (DataIntegrityViolationException e) {
      // Lost a concurrent registration race — but only an email conflict
      // becomes a duplicate; anything else is an unexpected failure.
      if (isEmailConflict(e)) {
        throw new DuplicateEmailException("Email is already registered.");
      }
      throw e;
    }
    return new AuthResponse(jwtService.issue(caregiver.getId()));
  }

  @Transactional
  public AuthResponse login(String email, String password) {
    String normalized = normalize(email);
    if (password == null
        || password.isEmpty()
        || password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
      throw new ValidationException("Invalid email or password.");
    }
    var caregiver = repository.findByEmail(normalized);
    // Always verify: the dummy hash keeps unknown-email logins as expensive
    // as wrong-password ones. Require both presence and match — a matching
    // dummy hash must never authenticate a missing caregiver.
    boolean matched = encoder.matches(
        password, caregiver.map(Caregiver::getPasswordHash).orElse(SecurityBeans.DUMMY_HASH));
    if (caregiver.isEmpty() || !matched) {
      throw new BadCredentialsException();
    }
    return new AuthResponse(jwtService.issue(caregiver.get().getId()));
  }

  static String normalize(String email) {
    return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
  }

  /**
   * True only for violations of {@code uk_caregivers_email}, identified via
   * Hibernate's structured constraint name — never broad message matching.
   * H2 reports the backing index, which it derives from the constraint name
   * with an {@code _INDEX_n} suffix; Postgres reports the exact name.
   */
  static boolean isEmailConflict(DataIntegrityViolationException e) {
    Throwable cause = e;
    while (cause != null) {
      if (cause instanceof org.hibernate.exception.ConstraintViolationException cve
          && matchesEmailConstraint(cve.getConstraintName())) {
        return true;
      }
      cause = cause.getCause();
    }
    return false;
  }

  private static boolean matchesEmailConstraint(String name) {
    if (name == null) {
      return false;
    }
    String simple = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1) : name;
    String upper = simple.toUpperCase(Locale.ROOT);
    return upper.equals("UK_CAREGIVERS_EMAIL") || upper.startsWith("UK_CAREGIVERS_EMAIL_INDEX");
  }
}
