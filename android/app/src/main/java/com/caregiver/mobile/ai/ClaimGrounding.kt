package com.caregiver.mobile.ai

/**
 * Conservative lexical grounding check for one AI observation: does the quote
 * the model cited actually support the claim it made?
 *
 * The check never proves that a claim is true. It only rejects claims the
 * quote does not support: words the quote never mentions, and negations that
 * flip the meaning ("had pain" cited for "had no pain"). Claims that survive
 * are still labelled unverified by the caller.
 *
 * Everything here compares [ArabicNormalizer]-normalized text, so the Arabic
 * forms the notes are written in are judged the same way as the English ones.
 */
object ClaimGrounding {
    /** Share of the claim's own words the quote has to cover. */
    const val MIN_SUPPORT_PERCENT = 80

    /** Shortest word that carries information: "a" or "no" is not evidence. */
    const val MIN_CONTENT_WORD_CHARS = 3

    /**
     * What the check could establish about one claim and the quote it cites.
     */
    enum class Verdict {
        /** The quote supports the claim. */
        SUPPORTED,

        /**
         * The quote does not support the claim: it contradicts it, or the
         * claim's own words are not in the quote.
         */
        UNSUPPORTED,

        /**
         * The claim and the quote are written in different languages, so no
         * word of one can be found in the other and nothing can be said
         * about the claim.
         *
         * The summary is written in the app's language while the quote stays
         * in the note's language, so this is the normal case for a caregiver
         * who writes notes in one language and reads the app in another. A
         * translated claim shares no words with the quote it cites, and
         * rejecting it would say nothing about the model — it would drop
         * every observation of a perfectly good summary.
         *
         * The check must therefore not veto an observation here. The caller
         * still verifies the quote against the note itself, and the
         * observation stays labelled unverified.
         */
        NOT_COMPARABLE,
    }

    private val wordSplit = Regex("\\s+")

    /** Words that make no claim of their own. */
    private val stopwords = setOf(
        "a", "an", "the", "and", "or", "but", "if", "to", "of", "in", "on",
        "at", "for", "with", "by", "as", "is", "are", "was", "were", "be",
        "been", "has", "have", "had", "it", "its", "this", "that", "these",
        "those", "patient", "today",
        "في", "من", "على", "الى", "إلى", "عن", "مع", "و", "ف", "ب", "ل",
        "هذا", "هذه", "هو", "هي", "كان", "قد", "أن", "إن",
        // Demonstratives, relatives and question words: they point at
        // something instead of asserting it, so a negation in front of one
        // ("ما هذا؟" = "what is this?") is not a negation of it.
        "ذلك", "تلك", "الذي", "التي", "الذين", "اللاتي",
        "ماذا", "أين", "متى", "كيف", "لماذا", "هل",
    )

    /**
     * Negation words, including the Egyptian forms the notes are written in:
     * "مش", "مفيش", and the fused "ما...ش" ("ماشي", "ماكانش").
     */
    private val negationWords = setOf(
        "not", "no", "never", "none", "nothing", "nobody", "nowhere",
        "without", "neither", "nor", "cannot", "cant",
        "لم", "لا", "لن", "ما", "ليس", "غير", "بدون", "بلا",
        "مش", "مفيش", "مفي", "ماينفعش",
    )

    /** True when [claim] is supported by [quote]. */
    fun isSupported(claim: String, quote: String): Boolean =
        verdict(claim, quote) == Verdict.SUPPORTED

    /**
     * What the check could establish about [claim] and the [quote] it cites.
     *
     * A claim and a quote in different languages come back as
     * [Verdict.NOT_COMPARABLE]: no word of one can be found in the other,
     * so the claim cannot be judged here and must not be rejected for it.
     */
    fun verdict(claim: String, quote: String): Verdict {
        val nClaim = ArabicNormalizer.normalize(claim)
        val nQuote = ArabicNormalizer.normalize(quote)
        if (nClaim.isEmpty() || nQuote.isEmpty()) return Verdict.UNSUPPORTED

        val claimWords = words(nClaim)
        val quoteWords = words(nQuote)

        // A quote has to carry information. "a" or "no" can sit inside
        // almost any note, so it is not evidence for anything.
        if (quoteWords.none { isQuoteContentWord(it) }) return Verdict.UNSUPPORTED

        // The claim and the quote may be written in different languages: the
        // summary is written in the app's language while the quote stays in
        // the note's language. The checks below would then reject every
        // observation for a reason that says nothing about the model, so
        // the check stops here and leaves the observation alone. The caller
        // still verifies the quote against the note itself.
        if (isDifferentLanguage(nClaim, nQuote)) return Verdict.NOT_COMPARABLE

        // A negation the claim adds and the quote does not share flips the
        // meaning just as much as one the claim drops: "did not walk" for
        // "walked", and "had pain" for "had no pain".
        if (claimNegatesQuote(claimWords, quoteWords)) return Verdict.UNSUPPORTED
        if (quoteNegatesClaim(quoteWords, claimWords)) return Verdict.UNSUPPORTED

        // Only the claim's own words are weighed. Negations are judged above,
        // so counting them here would only punish a different wording of the
        // same negation ("didn't" against "did not").
        val claimContent = claimWords.filter { isClaimContentWord(it) }
        if (claimContent.isEmpty()) return Verdict.SUPPORTED
        val supported = claimContent.count { word -> quoteWords.any { wordsMatch(word, it) } }
        return if (supported * 100 >= claimContent.size * MIN_SUPPORT_PERCENT) {
            Verdict.SUPPORTED
        } else {
            Verdict.UNSUPPORTED
        }
    }

    /**
     * True when the claim negates a word the quote states plainly. The word
     * after a negation is the one it applies to, so "did not walk" negates
     * "walk": a claim that says "did not walk" against a quote that says
     * "walked" drops what the quote states and is rejected.
     *
     * Only the word the negation actually applies to is looked at. A
     * negation in front of a pronoun ("ما هذا") points at something instead
     * of denying it, and a claim carrying one is not a claim about it.
     */
    private fun claimNegatesQuote(claimWords: List<String>, quoteWords: List<String>): Boolean {
        for (index in claimWords.indices) {
            if (!isNegation(claimWords[index])) continue
            val negated = claimWords.getOrNull(index + 1) ?: continue
            if (!isScopeWord(negated)) continue
            if (quoteStates(quoteWords, negated)) return true
        }
        return false
    }

    /**
     * True when the quote states [word] without negating it. A quote that
     * negates the word ("no pain") does not state it plainly.
     */
    private fun quoteStates(quoteWords: List<String>, word: String): Boolean {
        for (index in quoteWords.indices) {
            if (!wordsMatch(quoteWords[index], word)) continue
            val previous = quoteWords.getOrNull(index - 1)
            if (previous == null || !isNegation(previous)) return true
        }
        return false
    }

    /**
     * True when the quote negates a word the claim states plainly. The word
     * after a negation is the one it applies to, so "had no pain" negates
     * "pain": a claim about plain "pain" dropped the negation.
     */
    private fun quoteNegatesClaim(quoteWords: List<String>, claimWords: List<String>): Boolean {
        for (index in quoteWords.indices) {
            if (!isNegation(quoteWords[index])) continue
            val negated = quoteWords.getOrNull(index + 1) ?: continue
            if (!isScopeWord(negated)) continue
            if (claimStates(claimWords, negated)) return true
        }
        return false
    }

    /**
     * True when the claim states [word] without negating it. A claim that
     * repeats the negation ("no pain" against "had no pain") does not.
     */
    private fun claimStates(claimWords: List<String>, word: String): Boolean {
        for (index in claimWords.indices) {
            if (!wordsMatch(claimWords[index], word)) continue
            val previous = claimWords.getOrNull(index - 1)
            if (previous == null || !isNegation(previous)) return true
        }
        return false
    }

    /**
     * True when the quote word supports the claim word. Words are compared
     * after a light stem, so "walked" covers "walk" and "pills" covers
     * "pill". A word is never matched by a substring of another one: "a" must
     * not count as support for "pain".
     */
    private fun wordsMatch(claimWord: String, quoteWord: String): Boolean {
        if (claimWord == quoteWord) return true
        return stem(claimWord) == stem(quoteWord)
    }

    /**
     * Drops the endings that make the same word look different, and the
     * Arabic definite article. Short words are left alone: stripping "s" off
     * "as" would only hide the difference between words.
     */
    private fun stem(word: String): String {
        var stemmed = word
        if (stemmed.length > 4 && stemmed.endsWith("'s")) {
            stemmed = stemmed.dropLast(2)
        } else if (stemmed.length > 4 && stemmed.endsWith("es")) {
            stemmed = stemmed.dropLast(2)
        } else if (stemmed.length > 4 && stemmed.endsWith("ed")) {
            stemmed = stemmed.dropLast(2)
        } else if (stemmed.length > 4 && stemmed.endsWith("ing")) {
            stemmed = stemmed.dropLast(3)
        } else if (stemmed.length > 3 && stemmed.endsWith("s")) {
            stemmed = stemmed.dropLast(1)
        }
        if (stemmed.length > 4 && stemmed.startsWith("ال")) {
            stemmed = stemmed.drop(2)
        }
        return stemmed
    }

    /**
     * True when a quote word carries information of its own. A negation
     * counts here: "no pain" is a quote about pain, and the words it negates
     * are what the claim has to be checked against.
     */
    private fun isQuoteContentWord(word: String): Boolean =
        word.length >= MIN_CONTENT_WORD_CHARS && word !in stopwords

    /**
     * True when a claim word carries information of its own. Negations are
     * left out: they are judged by the negation rules, not by word overlap.
     */
    private fun isClaimContentWord(word: String): Boolean =
        isQuoteContentWord(word) && !isNegation(word)

    /**
     * True when a word can be the one a negation applies to.
     *
     * A function word or a pronoun points at something instead of asserting
     * it, so a negation in front of one ("ما هذا" = "what is this?") is not
     * a negation of it and must not be read as one.
     */
    private fun isScopeWord(word: String): Boolean = isQuoteContentWord(word)

    /** The script a text is written in. */
    private enum class Script { ARABIC, LATIN, NEUTRAL }

    /**
     * The script [text] is written in, decided by the letters it uses. A
     * text without letters — a number, a symbol — is neutral.
     */
    private fun scriptOf(text: String): Script {
        var arabic = 0
        var latin = 0
        for (character in text) {
            when (character) {
                // Arabic, Arabic Supplement, Arabic Extended-A, and the two
                // Arabic presentation-form blocks.
                in '\u0600'..'\u06FF', in '\u0750'..'\u077F', in '\u08A0'..'\u08FF',
                in '\uFB50'..'\uFDFF', in '\uFE70'..'\uFEFF' -> arabic++
                in 'A'..'Z', in 'a'..'z' -> latin++
            }
        }
        return when {
            arabic == 0 && latin == 0 -> Script.NEUTRAL
            arabic >= latin -> Script.ARABIC
            else -> Script.LATIN
        }
    }

    /**
     * True when [claim] and [quote] are written in different languages.
     *
     * The app writes the summary in its own language while the quote stays
     * in the language of the note, so a caregiver who writes notes in one
     * language and reads the app in another gets a claim and a quote that
     * share no word at all. Comparing them word by word would then reject
     * every observation of a good summary.
     */
    private fun isDifferentLanguage(claim: String, quote: String): Boolean {
        val claimScript = scriptOf(claim)
        val quoteScript = scriptOf(quote)
        return (claimScript == Script.ARABIC && quoteScript == Script.LATIN) ||
            (claimScript == Script.LATIN && quoteScript == Script.ARABIC)
    }

    /**
     * True when [word] negates. Besides the listed words this covers the
     * English contractions ("didn't"), the Egyptian fused "ما...ش" ("ماشي",
     * "ماكانش", "مافيش") and "مش" glued to a word ("بمش").
     */
    private fun isNegation(word: String): Boolean {
        if (word in negationWords) return true
        // English contractions: didn't, doesn't, isn't, wasn't, won't, can't.
        if (word.endsWith("n't")) return true
        // Egyptian: "ما" plus a verb marked with "ش", either at the end
        // ("ماكانش", "مافيش") or right behind the prefix ("ماشي").
        if (word.length >= 3 && word.startsWith("ما")) {
            if (word.endsWith("ش")) return true
            if (word.indexOf("ش") in 2..3) return true
        }
        // "مش" glued to a word ("بمش").
        if (word.length > 3 && word.endsWith("مش")) return true
        return false
    }

    private fun words(text: String): List<String> =
        text.split(wordSplit).filter { it.isNotBlank() }
}
