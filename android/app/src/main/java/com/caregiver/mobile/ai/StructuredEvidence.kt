package com.caregiver.mobile.ai

import com.caregiver.mobile.data.api.NoteDto

/**
 * Typed evidence for one AI summary observation.
 *
 * A note reaches the model as free text plus a rendered "Structured
 * observations: mood=...; pain=.../10" line. That line is a display
 * representation, not something the caregiver wrote: requiring the model
 * to quote it verbatim rejects correct paraphrases ("the mood was fair"
 * for mood=FAIR), while the obvious fix — fuzzy-matching the claim
 * against all note text — would let a claim borrow support from words
 * that happen to sit near each other. Typed evidence avoids both:
 *
 * - [NOTE_TEXT] quotes the original note text verbatim.
 * - [ADDENDUM] quotes an addendum verbatim (addenda are corrections,
 *   never part of the original text).
 * - [STRUCTURED_FIELD] cites a recorded field by name and value; the
 *   value is validated against the [NoteDto] itself, so the model never
 *   has to reproduce the artificial "mood=FAIR" text.
 */
enum class EvidenceType { NOTE_TEXT, STRUCTURED_FIELD, ADDENDUM }

object StructuredEvidence {

    /** Canonical structured field names, in [NoteDto.aiSourceText] order. */
    val CANONICAL_FIELDS = listOf(
        "mood", "appetite", "sleep", "mobility", "medicationTaken", "pain", "fall",
    )

    /**
     * Known wire vocabularies per field (kept in sync with the note
     * editor's option lists). Only used to catch a claim that asserts a
     * different known value for the field it cites; unknown/legacy field
     * values skip this guard.
     */
    private val knownValues: Map<String, List<String>> = mapOf(
        "mood" to listOf("good", "fair", "bad"),
        "appetite" to listOf("good", "reduced", "poor"),
        "sleep" to listOf("good", "broken", "poor"),
        "mobility" to listOf("walks", "assisted", "bed"),
        "medicationTaken" to listOf("taken", "missed", "unsure"),
    )

    private val fieldAliases: Map<String, String> = mapOf(
        "mood" to "mood",
        "appetite" to "appetite",
        "sleep" to "sleep",
        "mobility" to "mobility",
        "medicationtaken" to "medicationTaken",
        "medication_taken" to "medicationTaken",
        "medication taken" to "medicationTaken",
        "medication" to "medicationTaken",
        "pain" to "pain",
        "fall" to "fall",
    )

    /** Words that can sit in front of a target and flip it; kept small on purpose. */
    private val negationWords = setOf(
        "not", "no", "never", "without", "none", "nothing", "n't",
        "لم", "لا", "لن", "ما", "ليس", "غير", "بدون", "بلا", "مش", "مفيش", "مفي",
    )

    /**
     * The evidence type an observation declares. A blank type is a legacy
     * payload (older prompts/models): a structured citation is still
     * recognised from its field, otherwise the quote path is used. An
     * unrecognised non-blank type returns null and must be rejected.
     */
    fun typeOf(rawType: String, structuredField: String): EvidenceType? {
        val normalized = rawType.trim().uppercase().replace('-', '_').replace(' ', '_')
        return when (normalized) {
            "" -> if (structuredField.isNotBlank()) EvidenceType.STRUCTURED_FIELD else EvidenceType.NOTE_TEXT
            "NOTE_TEXT", "TEXT", "QUOTE" -> EvidenceType.NOTE_TEXT
            "STRUCTURED_FIELD", "STRUCTURED", "FIELD" -> EvidenceType.STRUCTURED_FIELD
            "ADDENDUM" -> EvidenceType.ADDENDUM
            else -> null
        }
    }

    /** Canonical field name for [raw], or null when it names no structured field. */
    fun canonicalField(raw: String): String? =
        fieldAliases[raw.trim().lowercase()]

    /** The value actually recorded on [note] for [field], as model-facing text. */
    fun actualValue(note: NoteDto, field: String): String = when (field) {
        "mood" -> note.mood
        "appetite" -> note.appetite
        "sleep" -> note.sleep
        "mobility" -> note.mobility
        "medicationTaken" -> note.medicationTaken
        "pain" -> note.pain.toString()
        "fall" -> note.fall.toString()
        else -> ""
    }

    /**
     * True when [valueRaw] is exactly the value recorded on [note] for the
     * field it names. Strings compare case-insensitively; pain accepts
     * "7" and "7/10"; fall accepts true/false and yes/no.
     */
    fun matches(note: NoteDto, fieldRaw: String, valueRaw: String): Boolean {
        val field = canonicalField(fieldRaw) ?: return false
        val value = valueRaw.trim()
        if (value.isEmpty()) return false
        val actual = actualValue(note, field).trim()
        if (actual.isEmpty()) return false
        return when (field) {
            "pain" -> parsePain(value) == note.pain
            "fall" -> parseBoolean(value) == note.fall
            else -> ArabicNormalizer.normalizeForEvidence(value) ==
                ArabicNormalizer.normalizeForEvidence(actual)
        }
    }

    /** The canonical, displayable citation built from the note's own values. */
    fun canonicalQuote(note: NoteDto, field: String): String = when (field) {
        "pain" -> "pain=${note.pain}/10"
        else -> "$field=${actualValue(note, field)}"
    }

    /**
     * True when [claim] actually talks about the cited field/value, so a
     * valid structured citation cannot be attached to an unrelated claim.
     * The claim must name the field or its exact value (so a natural
     * paraphrase like "the mood was fair" qualifies), must not negate it
     * ("mood was not fair"), must not assert a different known value for
     * the field ("mood was good" against mood=fair), and — for pain —
     * must not carry a different number.
     */
    fun claimAligns(claim: String, field: String, actual: String): Boolean {
        val tokens = tokensOf(claim)
        if (tokens.isEmpty() || actual.isBlank()) return false

        val fieldTargets = when (field) {
            "medicationTaken" -> setOf("medicationtaken", "medication")
            "fall" -> setOf("fall", "fell", "falls")
            else -> setOf(field.lowercase())
        }
        // "true"/"false" carry no claim meaning on their own; the fall
        // tokens above are what a fall claim has to mention.
        val valueTargets = if (field == "fall") emptySet() else setOf(actual.lowercase())
        val targets = fieldTargets + valueTargets

        if (tokens.none { token -> targets.any { wordsMatch(token, it) } }) return false

        // A negation in front of the field/value flips the claim, except
        // where the recorded value is itself the negated state: pain=0
        // ("no pain") and fall=false ("did not fall").
        for (index in tokens.indices) {
            val token = tokens[index]
            if (targets.none { wordsMatch(token, it) }) continue
            val previous = tokens.getOrNull(index - 1) ?: continue
            if (!isNegation(previous)) continue
            val consistentNegation = (field == "pain" && actual == "0") ||
                (field == "fall" && actual == "false")
            if (!consistentNegation) return false
        }

        // "The patient fell" against fall=false asserts the opposite.
        if (field == "fall" && actual == "false") {
            for (index in tokens.indices) {
                if (!wordsMatch(tokens[index], "fall") && !wordsMatch(tokens[index], "fell")) continue
                val previous = tokens.getOrNull(index - 1)
                if (previous == null || !isNegation(previous)) return false
            }
        }

        // A known value for this field that is not the recorded one is a
        // different claim, unless it is negated ("mood fair, not good").
        for (known in knownValues[field].orEmpty()) {
            if (known == actual) continue
            for (index in tokens.indices) {
                if (!wordsMatch(tokens[index], known)) continue
                val previous = tokens.getOrNull(index - 1)
                if (previous == null || !isNegation(previous)) return false
            }
        }

        // Pain numbers survive paraphrase: any number in the claim must
        // be the recorded pain score. The scale itself ("7 out of 10",
        // "7/10") is not a second score.
        if (field == "pain") {
            val claimText = ArabicNormalizer.normalizeForEvidence(claim)
                .replace(Regex("/\\s*10\\b"), "")
                .replace(Regex("\\bof\\s*10\\b"), "")
            val claimed = numberPattern.findAll(claimText).map { it.value }.toList()
            if (claimed.any { it != actual }) return false
        }
        return true
    }

    private fun parsePain(value: String): Int? =
        numberPattern.find(value)?.value?.toIntOrNull()

    private fun parseBoolean(value: String): Boolean? = when (value.trim().lowercase()) {
        "true", "yes", "1", "y", "t" -> true
        "false", "no", "0", "n", "f" -> false
        else -> null
    }

    private fun tokensOf(text: String): List<String> =
        ArabicNormalizer.normalizeForEvidence(text)
            .split(Regex("\\s+"))
            .map { it.trim { character -> !character.isLetterOrDigit() } }
            .filter { it.isNotEmpty() }

    private fun isNegation(word: String): Boolean =
        word in negationWords || word.endsWith("n't")

    /** Light stemming so "walked" covers "walks" and "fell" covers "fall". */
    private fun wordsMatch(a: String, b: String): Boolean = a == b || stem(a) == stem(b)

    private fun stem(word: String): String {
        var stemmed = word
        if (stemmed.length > 4 && stemmed.endsWith("ing")) stemmed = stemmed.dropLast(3)
        else if (stemmed.length > 4 && stemmed.endsWith("ed")) stemmed = stemmed.dropLast(2)
        else if (stemmed.endsWith("oes") || stemmed.endsWith("ches") || stemmed.endsWith("shes")) {
            stemmed = stemmed.dropLast(2)
        } else if (stemmed.length > 3 && stemmed.endsWith("s") && !stemmed.endsWith("ss")) {
            stemmed = stemmed.dropLast(1)
        }
        return stemmed
    }

    private val numberPattern = Regex("\\d+(?:[.,]\\d+)?")
}
