package com.caregiver.preview;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;

import com.caregiver.config.CaregiverApplication;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ContextConfiguration(classes = CaregiverApplication.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DataJpaTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:previewdb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=true",
    "spring.flyway.placeholder-replacement=false",
    "spring.jpa.hibernate.ddl-auto=validate"})
class PreviewPersistenceTest {

  @Autowired
  private RecipientRepository recipients;

  @Autowired
  private NoteRepository notes;

  @Autowired
  private AddendumRepository addenda;

  @Autowired
  private PlanRepository plans;

  @Autowired
  private PlanVersionRepository versions;

  @Autowired
  private TestEntityManager entityManager;

  @Autowired
  private JdbcTemplate jdbc;

  private final UUID caregiver = UUID.randomUUID();

  @Test
  void v2CreatesNamedConstraints() {
    for (String name : new String[] {"UK_NOTES_RECIPIENT_DAY", "UK_PLAN_VERSIONS_PLAN_VERSION"}) {
      Integer count = jdbc.queryForObject(
          "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS"
              + " WHERE UPPER(CONSTRAINT_NAME) = '" + name + "'",
          Integer.class);
      assertThat(count).isEqualTo(1);
    }
  }

  @Test
  void recipientRoundtripScopedByCaregiver() {
    Recipient saved = recipients.saveAndFlush(new Recipient(caregiver, "Fatma", true));
    entityManager.clear();

    assertThat(recipients.findByIdAndCaregiverId(saved.getId(), caregiver)).isPresent();
    assertThat(recipients.findByIdAndCaregiverId(saved.getId(), UUID.randomUUID())).isEmpty();
    assertThat(recipients.findByCaregiverId(caregiver)).hasSize(1);
  }

  @Test
  void oneNotePerRecipientPerDay() {
    Recipient recipient = recipients.saveAndFlush(new Recipient(caregiver, "Fatma", true));
    notes.saveAndFlush(Note.of(caregiver, recipient.getId(), LocalDate.parse("2026-10-08")));

    assertThatThrownBy(
            () -> notes.saveAndFlush(Note.of(caregiver, recipient.getId(), LocalDate.parse("2026-10-08"))))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void addendaOrderChronologically() {
    Recipient recipient = recipients.saveAndFlush(new Recipient(caregiver, "Fatma", true));
    Note note = notes.saveAndFlush(Note.of(caregiver, recipient.getId(), LocalDate.parse("2026-10-08")));
    Addendum second = new Addendum(caregiver, note.getId(), "second");
    second.setCreatedAt(java.time.Instant.parse("2026-10-08T10:01:00Z"));
    Addendum first = new Addendum(caregiver, note.getId(), "first");
    first.setCreatedAt(java.time.Instant.parse("2026-10-08T10:00:00Z"));
    addenda.saveAndFlush(second);
    addenda.saveAndFlush(first);

    List<Addendum> rows = addenda.findByNoteIdAndCaregiverIdOrderByCreatedAtAsc(note.getId(), caregiver);
    assertThat(rows).extracting(Addendum::getText).containsExactly("first", "second");
  }

  @Test
  void planVersionsOrderNewestFirst() {
    Recipient recipient = recipients.saveAndFlush(new Recipient(caregiver, "Fatma", true));
    Plan plan = plans.saveAndFlush(new Plan(caregiver, recipient.getId()));
    versions.saveAndFlush(new PlanVersion(plan.getId(), 0, "Dismissed", List.of("old")));
    versions.saveAndFlush(new PlanVersion(plan.getId(), 1, "Suggested", List.of("new")));

    List<PlanVersion> rows = versions.findByPlanIdOrderByVersionDesc(plan.getId());
    assertThat(rows).extracting(PlanVersion::getVersion).containsExactly(1, 0);
  }
}
