package com.caregiver.medications;

import com.caregiver.recipient.CareRecipient;
import com.caregiver.recipient.InactiveRecipientException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Shared lifecycle rules for writes to medications and their intake history. */
final class MedicationLifecycle {

  private MedicationLifecycle() {
  }

  static void requireActiveRecipient(CareRecipient recipient) {
    if (!recipient.isActive()) {
      throw new InactiveRecipientException();
    }
  }

  static void requireActiveMedication(MedicationRecord medication, String action) {
    if (!medication.isActive()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT,
          "Archived medication records cannot be " + action + ".");
    }
  }
}
