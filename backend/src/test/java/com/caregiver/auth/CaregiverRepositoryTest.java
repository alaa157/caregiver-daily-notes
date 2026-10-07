package com.caregiver.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;

import com.caregiver.config.CaregiverApplication;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// The entrypoint lives in com.caregiver.config (sibling package), so the
// default upward search cannot find it — declare it explicitly.
@ContextConfiguration(classes = CaregiverApplication.class)
// Keep our H2 PostgreSQL-mode URL: the default embedded replacement would
// bypass Flyway, and this test must execute V1.
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DataJpaTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:authdb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=true",
    "spring.flyway.placeholder-replacement=false",
    "spring.jpa.hibernate.ddl-auto=validate",
    "auth.jwt.secret=test-secret-that-is-definitely-32-bytes-long",
    "auth.jwt.expiry=24h"})
class CaregiverRepositoryTest {

  @Autowired
  private CaregiverRepository repository;

  @Autowired
  private TestEntityManager entityManager;

  @Autowired
  private JdbcTemplate jdbc;

  private static Caregiver caregiver(String email) {
    Caregiver caregiver = new Caregiver();
    caregiver.setEmail(email);
    caregiver.setPasswordHash("{bcrypt}$2a$10$placeholderhashplaceholderhash123456789012");
    return caregiver;
  }

  @Test
  void saveAndFindRoundtrip() {
    Caregiver saved = repository.saveAndFlush(caregiver("  Fatma@Example.COM "));
    entityManager.clear();

    assertThat(saved.getId()).isNotNull();
    assertThat(saved.getCreatedAt()).isNotNull();
    var found = repository.findByEmail("fatma@example.com");
    assertThat(found).isPresent();
    assertThat(found.get().getId()).isEqualTo(saved.getId());
  }

  @Test
  void existsByEmail() {
    assertThat(repository.existsByEmail("nobody@example.com")).isFalse();

    repository.saveAndFlush(caregiver("someone@example.com"));

    assertThat(repository.existsByEmail("someone@example.com")).isTrue();
  }

  @Test
  void normalizationIsLocaleIndependent() {
    // Turkish dotted/dotless I: no-arg toLowerCase() turns INFO into ınfo.
    Locale previous = Locale.getDefault();
    synchronized (CaregiverRepositoryTest.class) {
      Locale.setDefault(Locale.forLanguageTag("tr-TR"));
      try {
        repository.saveAndFlush(caregiver("INFO@example.com"));

        assertThat(repository.findByEmail("info@example.com")).isPresent();
      } finally {
        Locale.setDefault(previous);
      }
    }
  }

  @Test
  void duplicateEmailViolatesNamedConstraint() {
    // V1 names the constraint; identifier case folds per database (upper on
    // H2, lower on Postgres), so compare case-insensitively.
    Integer named = jdbc.queryForObject(
        "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS"
            + " WHERE UPPER(CONSTRAINT_NAME) = 'UK_CAREGIVERS_EMAIL'",
        Integer.class);
    assertThat(named).isEqualTo(1);

    repository.saveAndFlush(caregiver("dup@example.com"));

    // Normalized to the same value, so the second insert collides.
    assertThatThrownBy(() -> repository.saveAndFlush(caregiver("DUP@example.com")))
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}
