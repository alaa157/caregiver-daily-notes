package com.caregiver.preview;

import com.caregiver.auth.AuthContext;
import com.caregiver.auth.ValidationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Preview plans scoped to the caregiver. Replaced by #9. */
@Service
public class PlanService {

  private final PlanRepository plans;
  private final PlanVersionRepository versions;
  private final RecipientService recipients;

  public PlanService(
      PlanRepository plans, PlanVersionRepository versions, RecipientService recipients) {
    this.plans = plans;
    this.versions = versions;
    this.recipients = recipients;
  }

  public List<PlanDto> list() {
    return plans.findByCaregiverId(AuthContext.currentCaregiverId()).stream()
        .map(this::dto)
        .toList();
  }

  @Transactional
  public PlanDto suggest(UUID recipientId, List<String> items) {
    recipients.requireOwned(recipientId);
    Plan plan = plans.save(new Plan(AuthContext.currentCaregiverId(), recipientId));
    versions.save(new PlanVersion(plan.getId(), 1, "Suggested", orEmpty(items)));
    return dto(plan);
  }

  public List<PlanVersionDto> versions(UUID planId) {
    return versionRows(owned(planId)).stream()
        .map(v -> PlanVersionDto.of(v, reasonOf(v.getStatus())))
        .toList();
  }

  @Transactional
  public PlanDto append(UUID planId, String status, List<String> items) {
    Plan plan = owned(planId);
    List<PlanVersion> rows = versionRows(plan);
    int next = rows.stream().mapToInt(PlanVersion::getVersion).max().orElse(0) + 1;
    List<String> content =
        items != null && !items.isEmpty()
            ? items
            : rows.isEmpty() ? List.of() : rows.get(0).getItems();
    versions.save(new PlanVersion(plan.getId(), next, status, content));
    return dto(plan);
  }

  @Transactional
  public PlanDto transition(UUID planId, String action) {
    Plan plan = owned(planId);
    List<PlanVersion> rows = versionRows(plan);
    if (rows.isEmpty()) {
      throw new ValidationException("Plan has no versions.");
    }
    PlanVersion latest = rows.get(0);
    switch (action) {
      case "accept", "dismiss" -> {
        if (!latest.getStatus().equals("Suggested")
            && !latest.getStatus().equals("Edited-and-Accepted")) {
          throw new ValidationException("Illegal transition from " + latest.getStatus() + ".");
        }
        latest.setStatus(action.equals("accept") ? "Accepted" : "Dismissed");
        versions.save(latest);
      }
      case "edit-accept" -> {
        if (latest.getStatus().equals("Archived")) {
          throw new ValidationException("Illegal transition from Archived.");
        }
        int next = rows.stream().mapToInt(PlanVersion::getVersion).max().orElse(0) + 1;
        versions.save(
            new PlanVersion(plan.getId(), next, "Edited-and-Accepted", latest.getItems()));
      }
      case "archive" -> {
        if (latest.getStatus().equals("Archived")) {
          throw new ValidationException("Plan is already archived.");
        }
        latest.setStatus("Archived");
        versions.save(latest);
      }
      default -> throw new ValidationException("Unknown plan action.");
    }
    return dto(plan);
  }

  private Plan owned(UUID planId) {
    UUID caregiverId = AuthContext.currentCaregiverId();
    return plans.findByIdAndCaregiverId(planId, caregiverId)
        .orElseThrow(
            () -> {
              if (plans.existsById(planId)) {
                return new PreviewForbiddenException("Plan belongs to another caregiver.");
              }
              return new PreviewNotFoundException("Plan not found.");
            });
  }

  private List<PlanVersion> versionRows(Plan plan) {
    return versions.findByPlanIdOrderByVersionDesc(plan.getId());
  }

  private PlanDto dto(Plan plan) {
    return new PlanDto(
        plan.getId().toString(),
        plan.getRecipientId().toString(),
        versionRows(plan).stream()
            .map(v -> PlanVersionDto.of(v, reasonOf(v.getStatus())))
            .toList());
  }

  private static String reasonOf(String status) {
    return status + " via preview.";
  }

  private static List<String> orEmpty(List<String> items) {
    return items == null ? List.of() : List.copyOf(items);
  }
}
