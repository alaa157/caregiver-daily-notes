package com.caregiver.mobile.presentation.common

import com.caregiver.mobile.data.api.RecipientDto
import com.caregiver.mobile.data.csv.RecipientRef

/**
 * The only recipients allowed in a new-note/new-plan/medication selector:
 * active and not archived. Keep this in one place so every creation flow
 * enforces the same invariant instead of relying on the People screen.
 */
internal fun selectableRecipients(recipients: List<RecipientDto>): List<RecipientDto> =
    recipients.filter { it.active && it.archivedAt == null }

/** CSV import may map unmatched rows only onto a non-archived recipient. */
internal fun selectableCsvRecipients(recipients: List<RecipientRef>): List<RecipientRef> =
    recipients.filter { !it.archived }
