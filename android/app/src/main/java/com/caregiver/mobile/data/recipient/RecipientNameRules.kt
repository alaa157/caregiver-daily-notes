package com.caregiver.mobile.data.recipient

import com.caregiver.mobile.data.api.RecipientDto
import java.util.Locale

enum class NameCheck { Ok, Required, TooLong }

object RecipientNameRules {
    const val MAX_LENGTH = 120

    fun normalize(raw: String): String {
        val trimmed = raw.trim()
        val out = StringBuilder(trimmed.length)
        var pendingSpace = false
        for (ch in trimmed) {
            if (ch.isWhitespace()) {
                pendingSpace = out.isNotEmpty()
            } else {
                if (pendingSpace) {
                    out.append(' ')
                    pendingSpace = false
                }
                out.append(ch)
            }
        }
        return out.toString()
    }

    fun check(raw: String): NameCheck {
        val normalized = normalize(raw)
        return when {
            normalized.isEmpty() -> NameCheck.Required
            normalized.length > MAX_LENGTH -> NameCheck.TooLong
            else -> NameCheck.Ok
        }
    }

    fun initial(name: String): String {
        val normalized = normalize(name)
        if (normalized.isEmpty()) return ""
        val cp = normalized.codePointAt(0)
        return normalized.substring(0, Character.charCount(cp))
    }

    fun findDuplicate(
        name: String,
        existing: List<RecipientDto>,
        excludeId: String? = null,
    ): RecipientDto? {
        val target = normalize(name).lowercase(Locale.ROOT)
        return existing.firstOrNull { dto ->
            dto.id != excludeId && normalize(dto.name).lowercase(Locale.ROOT) == target
        }
    }
}
