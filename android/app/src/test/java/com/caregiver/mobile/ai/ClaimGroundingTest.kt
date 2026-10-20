package com.caregiver.mobile.ai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Lexical grounding gate: a claim has to be supported by the quote it cites.
 * The gate never proves a claim, it only rejects claims the quote does not
 * support.
 */
class ClaimGroundingTest {
    @Test
    fun claimDroppingTheQuotesNegationIsRejected() {
        // The reported case: the quote denies the pain the claim reports.
        assertFalse(ClaimGrounding.isSupported("had pain", "had no pain"))
        assertFalse(ClaimGrounding.isSupported("pain", "no pain"))
        assertFalse(ClaimGrounding.isSupported("walked", "did not walk"))
        assertFalse(ClaimGrounding.isSupported("pain at all", "no pain at all"))
        assertFalse(ClaimGrounding.isSupported("slept well", "did not sleep well"))
    }

    @Test
    fun claimKeepingTheQuotesNegationIsAccepted() {
        assertTrue(ClaimGrounding.isSupported("had no pain", "had no pain"))
        assertTrue(ClaimGrounding.isSupported("no pain", "there was no pain"))
        assertTrue(ClaimGrounding.isSupported("did not walk", "did not walk"))
    }

    @Test
    fun claimAddingANegationTheQuoteLacksIsRejected() {
        assertFalse(ClaimGrounding.isSupported("did not walk", "walked"))
        assertFalse(ClaimGrounding.isSupported("no pain", "pain"))
    }

    @Test
    fun negationElsewhereInTheQuoteIsNotAFlip() {
        // The quote denies a fever, not the walk the claim reports.
        assertTrue(ClaimGrounding.isSupported("walked normally", "no fever, walked normally"))
    }

    @Test
    fun egyptianNegationsAreRecognised() {
        // "مش" and "مفيش" negate, and so does the fused "ما...ش".
        assertFalse(ClaimGrounding.isSupported("ألم", "مفيش ألم"))
        assertFalse(ClaimGrounding.isSupported("ألم شديد", "مفيش ألم شديد"))
        assertFalse(ClaimGrounding.isSupported("ماشي", "مش ماشي"))
        assertFalse(ClaimGrounding.isSupported("ماشي", "ماكانش ماشي"))
        assertTrue(ClaimGrounding.isSupported("مفيش ألم", "مفيش ألم"))
        assertTrue(ClaimGrounding.isSupported("مش ماشي", "مش ماشي"))
        // The same negation in another wording is still the same negation.
        assertTrue(ClaimGrounding.isSupported("مش ماشي", "ماكانش ماشي"))
    }

    @Test
    fun wordsThatMerelyStartLikeANegationAreNotNegations() {
        // "مشغول" (busy) is not a negation.
        assertTrue(ClaimGrounding.isSupported("مشغول", "كان مشغول"))
    }

    @Test
    fun trivialQuotesSupportNothing() {
        assertFalse(ClaimGrounding.isSupported("pain", "a"))
        assertFalse(ClaimGrounding.isSupported("had severe pain", "a"))
        assertFalse(ClaimGrounding.isSupported("was walking", "the"))
        assertFalse(ClaimGrounding.isSupported("no pain", "no"))
    }

    @Test
    fun shortQuoteWordsDoNotCoverLongClaimWords() {
        // "a" must not count as support for "pain".
        assertFalse(ClaimGrounding.isSupported("pain after medication", "a pain"))
        assertFalse(ClaimGrounding.isSupported("pain", "painless"))
    }

    @Test
    fun supportedClaimIsAccepted() {
        assertTrue(ClaimGrounding.isSupported("walked", "walked"))
        assertTrue(ClaimGrounding.isSupported("walked slowly", "walked slowly today"))
        assertTrue(ClaimGrounding.isSupported("patient is fine", "patient looked fine today"))
    }

    @Test
    fun sameWordInAnotherFormIsAccepted() {
        assertTrue(ClaimGrounding.isSupported("medication taken", "medications taken"))
        assertTrue(ClaimGrounding.isSupported("walking slowly", "walked slowly"))
        assertTrue(ClaimGrounding.isSupported("had pain", "had pains"))
    }

    @Test
    fun unsupportedClaimIsRejected() {
        assertFalse(ClaimGrounding.isSupported("took medication", "walked"))
        assertFalse(ClaimGrounding.isSupported("severe pain", "walked"))
        assertFalse(ClaimGrounding.isSupported("fell in the hallway", "walked in the hallway"))
    }

    @Test
    fun emptyInputIsRejected() {
        assertFalse(ClaimGrounding.isSupported("", "walked"))
        assertFalse(ClaimGrounding.isSupported("walked", ""))
    }
}
