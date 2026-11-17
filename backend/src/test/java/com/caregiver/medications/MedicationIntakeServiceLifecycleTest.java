package com.caregiver.medications;

import com.caregiver.auth.Caregiver;
import com.caregiver.auth.CaregiverPrincipal;
import com.caregiver.auth.CaregiverRepository;
import com.caregiver.common.VerificationState;
import com.caregiver.config.CaregiverApplication;
import com.caregiver.recipient.CareRecipient;
import com.caregiver.recipient.CareRecipientRepository;
import com.caregiver.recipient.InactiveRecipientException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/** Service-level lifecycle contract for medication intake writes. */
@SpringBootTest(
    classes = CaregiverApplication.class,
    properties = {
      "spring.datasource.url=jdbc:h2:mem:medicationintakelifecycle;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.flyway.enabled=true",
      "spring.flyway.placeholder-replacement=false",
      "spring.jpa.hibernate.ddl-auto=validate",
      "auth.jwt.secret=test-secret-that-is-definitely-32-bytes-long",
      "auth.jwt.expiry=24h"})
class MedicationIntakeServiceLifecycleTest {
  @Autowired private MedicationIntakeService service;
  @Autowired private CaregiverRepository caregivers;
  @Autowired private CareRecipientRepository recipients;
  @Autowired private MedicationRepository medications;
  @Autowired private MedicationIntakeEventRepository intakeEvents;
  @Autowired private JdbcTemplate jdbc;

  @AfterEach
  void clearAuthentication() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void archivedMedicationRejectsIntakeWithoutCreatingARow() {
    Fixture fixture = fixture();
    authenticate(fixture.caregiverId());
    fixture.medication().archive();
    medications.saveAndFlush(fixture.medication());

    ResponseStatusException failure = catchThrowableOfType(
        () -> recordIntake(fixture), ResponseStatusException.class);

    assertThat(failure).isNotNull();
    assertThat(failure.getStatusCode().value()).isEqualTo(HttpStatus.CONFLICT.value());
    assertThat(events(fixture)).isEmpty();
  }

  @Test
  void archivedRecipientRejectsIntakeWithoutCreatingARow() {
    Fixture fixture = fixture();
    authenticate(fixture.caregiverId());
    fixture.recipient().archive(fixture.caregiverId());
    recipients.saveAndFlush(fixture.recipient());

    assertThatThrownBy(() -> recordIntake(fixture))
        .isInstanceOf(InactiveRecipientException.class);
    assertThat(events(fixture)).isEmpty();
  }

  @Test
  void activeMedicationAndRecipientStillAcceptIntake() {
    Fixture fixture = fixture();
    authenticate(fixture.caregiverId());

    MedicationIntakeResponse response = recordIntake(fixture);

    assertThat(response).isNotNull();
    assertThat(events(fixture)).hasSize(1);
  }

  private MedicationIntakeResponse recordIntake(Fixture fixture) {
    return service.create(fixture.recipient().getId(), fixture.medication().getId(),
        new MedicationIntakeRequest(MedicationIntakeEvent.IntakeState.TAKEN,
            Instant.parse("2026-11-01T08:30:00Z"), VerificationState.UNVERIFIED), null);
  }

  private List<MedicationIntakeEvent> events(Fixture fixture) {
    return intakeEvents.findAllByCaregiver_IdAndRecipient_IdAndMedication_IdOrderByOccurredAtAscIdAsc(
        fixture.caregiverId(), fixture.recipient().getId(), fixture.medication().getId());
  }

  private Fixture fixture() {
    UUID caregiverId = UUID.randomUUID();
    jdbc.update("INSERT INTO caregivers (id, email, password_hash) VALUES (?, ?, ?)",
        caregiverId, "intake-lifecycle-" + caregiverId + "@example.com",
        "{bcrypt}$2a$10$placeholderhashplaceholderhash123456789012");
    Caregiver caregiver = caregivers.getReferenceById(caregiverId);
    CareRecipient recipient = recipients.saveAndFlush(
        new CareRecipient(caregiver, "Lifecycle recipient", "family"));
    MedicationRecord medication = medications.saveAndFlush(new MedicationRecord(
        caregiver, recipient, "Lifecycle med", "10 mg", "daily at 08:30"));
    return new Fixture(caregiverId, recipient, medication);
  }

  private void authenticate(UUID caregiverId) {
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken(new CaregiverPrincipal(caregiverId), null, List.of()));
  }

  private record Fixture(UUID caregiverId, CareRecipient recipient, MedicationRecord medication) {
  }
}
