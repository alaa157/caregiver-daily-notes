package com.caregiver.mobile.ai

import com.caregiver.mobile.data.api.SignalDto
import java.time.LocalDate
import java.util.regex.Pattern

/**
 * Mirror of the backend's SafetySignalEvaluator (same thresholds, same
 * flag ids, same semantics). The flags answer the checkboxes and the words:
 *
 * - "poor appetite streaks" means CONSECUTIVE days (README contract) —
 *   three bad days scattered across a 30-day window doesn't flag.
 * - A fall or chest pain described in a note's text raises the same flag
 *   the checkboxes raise, even when the checkbox sat unticked or pain was
 *   unrecorded. Patterns run on ArabicNormalizer-folded text (so ٧, ی,
 *   tatweel or a bidi mark never break the match) and stay deliberately
 *   coarse: a stray positive costs one look, a missed real fall breaks
 *   trust.
 */
object SafetySignalEvaluator {
    const val HIGH_PAIN_THRESHOLD = 7
    const val MISSED_MED_COUNT_THRESHOLD = 2
    const val POOR_APPETITE_DAYS_THRESHOLD = 3

    // Text rules compiled once at class load (the earlier per-call recompile
    // was wasted work). The Arabic side is written post-normalization.
    private val FALL_TEXT: Pattern = Pattern.compile("fell(?! asleep)|سقط|سقوط")
    private val CHEST_PAIN_TEXT: Pattern = Pattern.compile("chest pain|chest hurts")
    private val ARABIC_PAIN: Pattern = Pattern.compile("الم")
    private val ARABIC_CHEST: Pattern = Pattern.compile("صدر")

    fun evaluate(signals: List<SignalDto>): List<String> {
        val flags = mutableListOf<String>()
        // One normalizer fold per signal; every text rule matches against it.
        val foldedTexts = signals.map { ArabicNormalizer.normalize(it.text) }
        if (signals.any { it.fallReported } || foldedTexts.any { FALL_TEXT.matcher(it).find() }) {
            flags.add("FALL_REPORTED")
        }
        val painScoreFlag = signals.any { (it.pain ?: 0) >= HIGH_PAIN_THRESHOLD }
        val chestPainTextFlag = foldedTexts.any { text ->
            CHEST_PAIN_TEXT.matcher(text).find() ||
                (ARABIC_PAIN.matcher(text).find() && ARABIC_CHEST.matcher(text).find())
        }
        if (painScoreFlag || chestPainTextFlag) flags.add("HIGH_PAIN")
        val missedDates = signals.filter { it.medication == "missed" }
            .map { it.date }.distinct().size
        if (missedDates >= MISSED_MED_COUNT_THRESHOLD) flags.add("REPEATED_MISSED_MED")
        if (signals.any { it.medicationUnverified }) flags.add("MEDICATION_UNVERIFIED")
        val appetiteStreak = longestConsecutiveRun(
            signals.filter { it.poorAppetite }.mapNotNull { runCatching { LocalDate.parse(it.date) }.getOrNull() },
        )
        if (appetiteStreak >= POOR_APPETITE_DAYS_THRESHOLD) flags.add("POOR_APPETITE_DAYS")
        return flags
    }

    /** The longest run of adjacent calendar dates (the README's "streak"). */
    internal fun longestConsecutiveRun(dates: List<LocalDate>): Int {
        var longest = 0
        var run = 0
        var prev: LocalDate? = null
        for (date in dates.distinct().sorted()) {
            run = if (prev != null && prev.plusDays(1) == date) run + 1 else 1
            if (run > longest) longest = run
            prev = date
        }
        return longest
    }
}
