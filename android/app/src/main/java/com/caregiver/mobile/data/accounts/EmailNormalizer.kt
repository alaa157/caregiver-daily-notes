package com.caregiver.mobile.data.accounts

import java.text.Normalizer
import java.util.Locale

/**
 * Canonical form of the local login key (epic #581).
 *
 * Email is the key a caregiver signs in with, so two spellings that differ
 * only in case, composition or surrounding whitespace must resolve to one
 * account. The [unique index][AccountEntity] on the stored value is the final
 * guard; this normalizer is applied by every caller before a lookup or an
 * insert so that guard can do its job.
 *
 * Pure JVM code — no Android dependency — so the rules are unit-testable
 * without Robolectric. It deliberately does no RFC 5322 validation: this is
 * a local login key, not a mail delivery address.
 */
object EmailNormalizer {
    private const val MAX_LENGTH = 254

    /**
     * Returns the canonical form of [raw], or null when it cannot be used as
     * a login key. Callers must treat null as a validation error.
     */
    fun normalize(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed.length > MAX_LENGTH) return null
        // Reject embedded whitespace and control characters up front; trim()
        // already removed the leading/trailing ones. Doing this before NFKC
        // keeps a control character from being folded into something else.
        if (trimmed.any { it.isWhitespace() || it.isISOControl() }) return null

        // Fold composed and decomposed forms so "é" and "e + combining
        // acute" compare equal. Locale.ROOT, never the default locale: under
        // Turkish, lowercasing "I" with the default locale yields "ı" and
        // would break login for the same address typed elsewhere.
        val folded = Normalizer.normalize(trimmed, Normalizer.Form.NFKC)
            .lowercase(Locale.ROOT)
        if (folded.length > MAX_LENGTH) return null

        val at = folded.indexOf('@')
        if (at <= 0) return null // no '@' or empty local part
        if (folded.indexOf('@', at + 1) >= 0) return null // more than one '@'
        if (at == folded.length - 1) return null // empty domain part
        return folded
    }
}
