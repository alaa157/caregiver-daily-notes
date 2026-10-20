package com.caregiver.mobile.ai

import java.util.regex.Pattern

/**
 * Comparison-only Arabic normalization for quote validation, mirroring the
 * backend's ArabicNormalizer. Output is used to compare model quotes against
 * stored note text; stored text is never altered.
 *
 * Folds added so the model cannot lose a match by re-typesetting text:
 * Arabic-Indic digits (٧→7), Eastern/Persian digits (۷→7), zero-width and
 * direction controls (ZWJ/ZWNJ/ZWSP/bidi marks/BOM), Arabic punctuation
 * (، ؛ ؟ « ») onto their Latin twins, and the Persian letter variants
 * (ک ی) onto their Arabic twins. Patterns compile once.
 */
object ArabicNormalizer {
    private val DIACRITICS: Pattern = Pattern.compile("[\\u064B-\\u065F\\u0670]")

    /** Zero-width joiners/separators, bidi marks, word joiners, BOM, soft hyphen. */
    private val INVISIBLE_AND_CONTROLS: Pattern =
        Pattern.compile("[\\u200B-\\u200D\\u2066-\\u2069\\u202A-\\u202E\\uFEFF\\u061C\\u00AD]")
    private val ALEF_FORMS: Pattern = Pattern.compile("[أإآٱ]")
    private val WHITESPACE: Pattern = Pattern.compile("\\s+")

    fun normalize(s: String?): String {
        if (s == null) return ""
        var out = INVISIBLE_AND_CONTROLS.matcher(s).replaceAll("")
        out = out.replace("ـ", "")
        out = DIACRITICS.matcher(out).replaceAll("")
        out = toLatinDigits(out)
        out = unifyPunctuation(out)
        // Persian letter variants onto their Arabic twins.
        out = out.replace("ک", "ك")
        out = out.replace("ی", "ي")
        out = ALEF_FORMS.matcher(out).replaceAll("ا")
        out = out.replace("ؤ", "و")
        out = out.replace("ئ", "ي")
        out = out.replace("ى", "ي")
        out = out.replace("ة", "ه")
        out = out.lowercase()
        out = WHITESPACE.matcher(out).replaceAll(" ")
        return out.trim()
    }

    /** Arabic-Indic ٠-٩ and Eastern/Persian ۰-۹ digits become ASCII. */
    private fun toLatinDigits(s: String): String {
        val b = StringBuilder(s.length)
        for (c in s) {
            when (c) {
                in '٠'..'٩' -> b.append('0' + (c - '٠'))
                in '۰'..'۹' -> b.append('0' + (c - '۰'))
                else -> b.append(c)
            }
        }
        return b.toString()
    }

    /** Arabic-script punctuation becomes its Latin pair so both sides compare equal. */
    private fun unifyPunctuation(s: String): String {
        val b = StringBuilder(s.length)
        for (c in s) {
            when (c) {
                '،' -> b.append(',')
                '؛' -> b.append(';')
                '؟' -> b.append('?')
                '«', '»' -> b.append('"')
                '٫' -> b.append('.')
                '٬' -> b.append(',')
                else -> b.append(c)
            }
        }
        return b.toString()
    }
}
