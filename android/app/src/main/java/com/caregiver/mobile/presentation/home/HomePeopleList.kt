package com.caregiver.mobile.presentation.home

import com.caregiver.mobile.core.time.DateFormats
import java.time.LocalDate

/**
 * Pure helpers for the redesigned Home people list (#897). Kept in their own
 * file so the list logic is unit-testable and the ViewModel stays untouched.
 */

/** How recent a recipient's last note is, for the muted "Last note:" line. */
enum class LastNoteWhen { Today, Yesterday, Older }

/**
 * Classifies a note date against [today]. Returns null only when the stored
 * date is blank or malformed, so the UI can omit the line rather than invent
 * a relative label. Day precision only: note storage carries no timestamp,
 * so a wall-clock time (the prototype's "9:40") is never fabricated.
 */
fun lastNoteWhen(noteDate: String?, today: LocalDate): LastNoteWhen? {
    val date = DateFormats.parseIsoDate(noteDate) ?: return null
    return when (date) {
        today -> LastNoteWhen.Today
        today.minusDays(1) -> LastNoteWhen.Yesterday
        else -> LastNoteWhen.Older
    }
}

/**
 * People who still need today's note come first. The sort is stable, so the
 * dashboard's original order is preserved inside each of the two groups.
 */
fun peopleWithoutTodayNoteFirst(cards: List<HomeCard>): List<HomeCard> =
    cards.sortedBy { RecipientChip.DoneToday in it.chips }
