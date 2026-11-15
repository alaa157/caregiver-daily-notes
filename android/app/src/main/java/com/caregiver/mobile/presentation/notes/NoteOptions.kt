package com.caregiver.mobile.presentation.notes

import com.caregiver.mobile.R

/** Chip groups in the note editor (board 5); single-select each. */
enum class OptionGroup { Mood, Appetite, Sleep, Mobility, Medication }

/** Canonical wire values. The backend stores these verbatim (or ""). */
object NoteOptions {
    val Mood = listOf("good", "fair", "bad")
    val Appetite = listOf("good", "reduced", "poor")
    val Sleep = listOf("good", "broken", "poor")
    val Mobility = listOf("walks", "assisted", "bed")
    val Medication = listOf("taken", "missed", "unsure")

    fun values(group: OptionGroup): List<String> = when (group) {
        OptionGroup.Mood -> Mood
        OptionGroup.Appetite -> Appetite
        OptionGroup.Sleep -> Sleep
        OptionGroup.Mobility -> Mobility
        OptionGroup.Medication -> Medication
    }
}

/**
 * Localized labels for wire values. Unknown/legacy values fall back to the
 * raw value so old notes never render blank.
 */
object OptionLabels {
    /**
     * String resource for a known (group, value) pair, or null for anything
     * else so callers can fall back to the raw value.
     */
    fun labelRes(group: OptionGroup, value: String): Int? {
        val groupName = when (group) {
            OptionGroup.Mood -> "mood"
            OptionGroup.Appetite -> "appetite"
            OptionGroup.Sleep -> "sleep"
            OptionGroup.Mobility -> "mobility"
            OptionGroup.Medication -> "medication"
        }
        return when ("$groupName.$value") {
            "mood.calm" -> R.string.note_option_mood_calm
            "mood.happy" -> R.string.note_option_mood_happy
            "mood.anxious" -> R.string.note_option_mood_anxious
            "mood.agitated" -> R.string.note_option_mood_agitated
            "mood.sad" -> R.string.note_option_mood_sad
            "mood.confused" -> R.string.note_option_mood_confused
            "mood.good" -> R.string.note_option_mood_good
            "mood.fair" -> R.string.note_option_mood_fair
            "mood.bad" -> R.string.note_option_mood_bad
            "appetite.good" -> R.string.note_option_appetite_good
            "appetite.fair" -> R.string.note_option_appetite_fair
            "appetite.poor" -> R.string.note_option_appetite_poor
            "appetite.refused" -> R.string.note_option_appetite_refused
            "appetite.reduced" -> R.string.note_option_appetite_reduced
            "sleep.restful" -> R.string.note_option_sleep_restful
            "sleep.restless" -> R.string.note_option_sleep_restless
            "sleep.poor" -> R.string.note_option_sleep_poor
            "sleep.sleepless" -> R.string.note_option_sleep_sleepless
            "sleep.good" -> R.string.note_option_sleep_good
            "sleep.broken" -> R.string.note_option_sleep_broken
            "mobility.independent" -> R.string.note_option_mobility_independent
            "mobility.assisted" -> R.string.note_option_mobility_assisted
            "mobility.bedridden" -> R.string.note_option_mobility_bedridden
            "mobility.walks" -> R.string.note_option_mobility_walks
            "mobility.bed" -> R.string.note_option_mobility_bed
            "medication.taken" -> R.string.note_option_medication_taken
            "medication.missed" -> R.string.note_option_medication_missed
            "medication.unsure" -> R.string.note_option_medication_unsure
            else -> null
        }
    }

    fun label(group: OptionGroup, value: String, arabic: Boolean): String {
        if (arabic) {
            return when (group) {
                OptionGroup.Mood -> when (value) {
                    "good" -> "جيدة"
                    "fair" -> "متوسطة"
                    "bad" -> "سيئة"
                    else -> value
                }
                OptionGroup.Appetite -> when (value) {
                    "good" -> "جيدة"
                    "reduced" -> "منخفضة"
                    "poor" -> "ضعيفة"
                    else -> value
                }
                OptionGroup.Sleep -> when (value) {
                    "good" -> "جيد"
                    "broken" -> "متقطع"
                    "poor" -> "ضعيف"
                    else -> value
                }
                OptionGroup.Mobility -> when (value) {
                    "walks" -> "يمشي"
                    "assisted" -> "بمساعدة"
                    "bed" -> "طريح الفراش"
                    else -> value
                }
                OptionGroup.Medication -> when (value) {
                    "taken" -> "أخذ الدواء"
                    "missed" -> "فاته الدواء"
                    "unsure" -> "غير متأكد"
                    else -> value
                }
            }
        }
        return when (value) {
            "good" -> "Good"
            "fair" -> "Fair"
            "bad" -> "Bad"
            "reduced" -> "Reduced"
            "poor" -> "Poor"
            "broken" -> "Broken"
            "walks" -> "Walks"
            "assisted" -> "Assisted"
            "bed" -> "In bed"
            "taken" -> "Taken"
            "missed" -> "Missed"
            "unsure" -> "Unsure"
            else -> value
        }
    }
}
