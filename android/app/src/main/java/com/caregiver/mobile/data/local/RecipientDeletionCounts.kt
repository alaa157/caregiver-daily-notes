package com.caregiver.mobile.data.local

/**
 * Number of local rows a permanent recipient delete removes. Counts are
 * authoritative: they come from the same Room database the delete runs in.
 */
data class RecipientDeletionCounts(
    val notes: Int,
    val addenda: Int,
    val plans: Int,
    val medications: Int,
    val intakes: Int,
    val outbox: Int,
)
