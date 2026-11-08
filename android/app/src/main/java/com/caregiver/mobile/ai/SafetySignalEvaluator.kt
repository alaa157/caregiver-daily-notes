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
    // "fall" is matched as a whole word so a fall report is caught, while
    // "fell/fall asleep" remains the one common non-fall use of the word.
    private val FALL_TEXT: Pattern = Pattern.compile(
        "\\b(?:fell|fall|fallen|slipped|tripped)\\b(?! asleep)|سقط|سقوط|" +
            "(?<!\\p{L})وقع(?:ت|ه|ها|وا)?(?!\\p{L})",
    )
    private val CHEST_PAIN_TEXT: Pattern = Pattern.compile(
        "\\bchest pain\\b|\\bchest hurts\\b|\\bpain in (?:the )?chest\\b|" +
            "\\bchest tightness\\b|\\bchest pressure\\b|\\bchest discomfort\\b",
    )
    // Word-bounded Arabic terms: "الم" is a pain word, but the same letters
    // sit inside "المريض" and "المنزل", so a substring match used to fire on
    // unrelated notes. "آلام" normalizes to "الام" and needs its own form.
    private val ARABIC_PAIN: Pattern = Pattern.compile(
        "(?<!\\p{L})(?:الم|الام|وجع(?:ت|ه|ها|هم|هما|ك|ي)?)(?!\\p{L})",
    )
    private val ARABIC_CHEST: Pattern = Pattern.compile(
        "(?<!\\p{L})(?:ال)?صدر(?:ه|ها|هم|هما|ك|ي)?(?!\\p{L})",
    )
    private val BREATHING_TEXT: Pattern = Pattern.compile(
        "\\b(?:trouble|difficulty|problems?) (?:in )?breathing\\b|" +
            "\\bbreathing (?:trouble|difficulty|problems?)\\b|" +
            "\\bshort(?:ness)? of breath\\b|\\bcan(?:no|')?t breathe\\b|" +
            "\\bcannot breathe\\b|\\bgasping\\b|\\bwheez(?:ing|es)\\b|" +
            "\\bbreathless\\b|صعوبه (?:في )?التنفس|صعوبة (?:في )?التنفس|" +
            "ضيق (?:في )?التنفس|مش قادر(?:ه|ة)? يتنفس|" +
            "مش قادره تتنفس|تعبان(?:ه|ة)? في التنفس|اللهث|لهث|النهجان|نهجان",
    )
    private val CONSCIOUSNESS_TEXT: Pattern = Pattern.compile(
        "\\b(?:lost|loss of) consciousness\\b|\\bpassed out\\b|" +
            "\\bpassing out\\b|\\bfaint(?:ed|ing)?\\b|\\bunconscious\\b|" +
            "\\bblacked out\\b|\\bblackout\\b|فقد(?:ان)? الوعي|" +
            "فقدت? الوعي|اغماء|اغمي|مغمي عليه|مغمى عليه|غاب عن الوعي",
    )

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
        // These are the other two "acutely dangerous" findings the model
        // prompt names. They must be caught deterministically too: the
        // summary still has to warn when no key is configured or the model
        // call fails.
        if (foldedTexts.any { BREATHING_TEXT.matcher(it).find() }) {
            flags.add("BREATHING_TROUBLE")
        }
        if (foldedTexts.any { CONSCIOUSNESS_TEXT.matcher(it).find() }) {
            flags.add("LOSS_OF_CONSCIOUSNESS")
        }
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
