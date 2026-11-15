package com.caregiver.mobile.data.recipient

import java.util.Locale

/**
 * The fixed relationship vocabulary offered by the add/edit-person forms.
 * Codes travel on the wire and into backups; free text is only allowed
 * through the "OTHER" chip.
 */
object RelationshipOptions {
    val codes = listOf(
        "MOTHER", "FATHER", "SPOUSE", "GRANDMOTHER", "GRANDFATHER",
        "CHILD", "SIBLING", "RELATIVE", "FRIEND", "CLIENT", "OTHER",
    )

    const val MAX_LENGTH = 80

    fun normalize(raw: String?): String? =
        raw?.trim()?.takeIf { it.isNotEmpty() }

    fun isTooLong(raw: String?): Boolean =
        (normalize(raw)?.length ?: 0) > MAX_LENGTH

    fun codeOf(value: String?): String? {
        val code = value?.trim()?.uppercase(Locale.ROOT) ?: return null
        return code.takeIf { it in codes }
    }

    fun storedValue(chip: String?, other: String): String? =
        if (chip == "OTHER") normalize(other) else chip

    fun choiceOf(value: String?): Pair<String?, String> {
        val code = codeOf(value)
        if (code != null) return code to ""
        val normalized = normalize(value)
        return if (normalized != null) "OTHER" to normalized else null to ""
    }
}
