package com.caregiver.recipient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.caregiver.auth.Caregiver;
import com.caregiver.auth.CaregiverRepository;
import com.caregiver.config.CaregiverApplication;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(
    classes = CaregiverApplication.class,
    properties = {
      "spring.datasource.url=jdbc:h2:mem:recipient_care_details_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.flyway.enabled=true",
      "spring.flyway.placeholder-replacement=false",
      "spring.jpa.hibernate.ddl-auto=validate",
      "auth.jwt.secret=test-secret-that-is-definitely-32-bytes-long",
      "auth.jwt.expiry=24h"
    })
class CareRecipientCareDetailsTest {

  @Autowired private CareRecipientRepository recipientRepository;
  @Autowired private CaregiverRepository caregiverRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void cleanDatabase() {
    recipientRepository.deleteAll();
    caregiverRepository.deleteAll();
  }

  @Test
  void persistsAndReloadsCareDetails() {
    Caregiver caregiver = createCaregiver();
    CareRecipient recipient = recipientRepository.saveAndFlush(
        new CareRecipient(caregiver, "Care details recipient", null));
    UUID recipientId = recipient.getId();

    recipient.updateCareDetails(
        LocalDate.of(1940, 3, 12), "Penicillin", "Asthma", "Samir Hassan", "+1-555-0100");
    recipientRepository.saveAndFlush(recipient);

    CareRecipient reloaded = recipientRepository.findById(recipientId).orElseThrow();
    assertThat(reloaded.getDateOfBirth()).isEqualTo(LocalDate.of(1940, 3, 12));
    assertThat(reloaded.getAllergies()).isEqualTo("Penicillin");
    assertThat(reloaded.getConditions()).isEqualTo("Asthma");
    assertThat(reloaded.getEmergencyContactName()).isEqualTo("Samir Hassan");
    assertThat(reloaded.getEmergencyContactPhone()).isEqualTo("+1-555-0100");
  }

  @Test
  void archivedRecipientCannotUpdateCareDetails() {
    Caregiver caregiver = createCaregiver();
    CareRecipient recipient = recipientRepository.saveAndFlush(
        new CareRecipient(caregiver, "Archived recipient", null));
    recipient.archive(caregiver.getId());
    recipientRepository.saveAndFlush(recipient);

    assertThatThrownBy(() -> recipient.updateCareDetails(
        LocalDate.of(1940, 3, 12), "Penicillin", "Asthma", "Samir Hassan", "+1-555-0100"))
        .isInstanceOf(InactiveRecipientException.class);
  }

  private Caregiver createCaregiver() {
    UUID caregiverId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO caregivers (id, email, password_hash) VALUES (?, ?, ?)",
        caregiverId, "care-details-" + caregiverId + "@example.com", "not-a-real-password-hash");
    return caregiverRepository.findById(caregiverId).orElseThrow();
  }
}
