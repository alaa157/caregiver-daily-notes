package com.caregiver.mobile.core.i18n

/**
 * Task 8: bidi-safe mixed text.
 *
 * Arabic UI routinely embeds English names, numbers, and Egyptian-dialect
 * quotes. Every interpolated string is wrapped in FSI/PDI isolates so the
 * Unicode bidi algorithm cannot reorder the surrounding Arabic copy.
 * Idempotent: isolating an already-isolated string returns it unchanged.
 */
object Bidi {
    private const val FSI = "\u2066"
    private const val PDI = "\u2069"

    fun isolate(text: String): String {
        if (text.isEmpty()) {
            return text
        }
        if (text.startsWith(FSI) && text.endsWith(PDI)) {
            return text
        }
        return "$FSI$text$PDI"
    }
}
