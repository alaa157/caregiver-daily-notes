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

    private val wordSplit = Regex("\\s+")

    /** Words that make no claim of their own. */
    private val stopwords = setOf(
        "a", "an", "the", "and", "or", "but", "if", "to", "of", "in", "on",
        "at", "for", "with", "by", "as", "is", "are", "was", "were", "be",
        "been", "has", "have", "had", "it", "its", "this", "that", "these",
        "those", "patient", "today",
        "في", "من", "على", "الى", "إلى", "عن", "مع", "و", "ف", "ب", "ل",
        "هذا", "هذه", "هو", "هي", "كان", "قد", "أن", "إن",
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
    fun isSupported(claim: String, quote: String): Boolean {
        val nClaim = ArabicNormalizer.normalize(claim)
        val nQuote = ArabicNormalizer.normalize(quote)
        if (nClaim.isEmpty() || nQuote.isEmpty()) return false

        val claimWords = words(nClaim)
        val quoteWords = words(nQuote)

        // A quote has to carry information. "a" or "no" can sit inside
        // almost any note, so it is not evidence for anything.
        if (quoteWords.none { isQuoteContentWord(it) }) return false

        // A negation the claim adds and the quote does not share flips the
        // meaning just as much as one the claim drops: "did not walk" for
        // "walked", and "had pain" for "had no pain".
        if (claimWords.any { isNegation(it) } && quoteWords.none { isNegation(it) }) return false
        if (quoteNegatesClaim(quoteWords, claimWords)) return false

        // Only the claim's own words are weighed. Negations are judged above,
        // so counting them here would only punish a different wording of the
        // same negation ("didn't" against "did not").
        val claimContent = claimWords.filter { isClaimContentWord(it) }
        if (claimContent.isEmpty()) return true
        val supported = claimContent.count { word -> quoteWords.any { wordsMatch(word, it) } }
        return supported * 100 >= claimContent.size * MIN_SUPPORT_PERCENT
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
