package com.caregiver.mobile.ai

/**
 * Comparison-only Arabic normalization for quote validation, mirroring the
 * backend's ArabicNormalizer. Output is used to compare model quotes against
 * stored note text; stored text is never altered.
 */
object ArabicNormalizer {
    fun normalize(s: String?): String {
        if (s == null) return ""
        var out = s.replace("ـ", "")
        out = out.replace(Regex("[\\u064B-\\u065F\\u0670]"), "")
        out = out.replace(Regex("[أإآٱ]"), "ا")
        out = out.replace("ؤ", "و")
        out = out.replace("ئ", "ي")
        out = out.replace("ى", "ي")
        out = out.replace("ة", "ه")
        out = out.lowercase()
        out = out.replace(Regex("\\s+"), " ")
        return out.trim()
    }
}