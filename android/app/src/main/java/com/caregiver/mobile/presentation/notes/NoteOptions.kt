package com.caregiver.mobile.presentation.notes

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
