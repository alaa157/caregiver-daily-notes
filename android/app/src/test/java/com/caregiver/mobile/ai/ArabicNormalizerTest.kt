package com.caregiver.mobile.ai

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the normalizer folds the quote-matching depends on, mirroring the
 * backend's ArabicNormalizerTest: a model re-typesetting text (digits,
 * punctuation, invisible marks, Persian letter variants) must not break
 * the comparison against stored notes.
 */
class ArabicNormalizerTest {

    @Test
    fun nullYieldsEmpty() {
        assertEquals("", ArabicNormalizer.normalize(null))
    }

    @Test
    fun stripsTashkeelAndTatweel() {
        assertEquals("ياكل جيدا", ArabicNormalizer.normalize("يَأْكُلُ جَيِّدًا"))
        assertEquals("كتاب", ArabicNormalizer.normalize("كــتـاب"))
    }

    @Test
    fun unifiesAlefAndHamzaForms() {
        assertEquals("احمد وابراهيم وامال", ArabicNormalizer.normalize("أحمد وإبراهيم وآمال"))
        assertEquals("مومن وشيي", ArabicNormalizer.normalize("مؤمن وشيئ"))
    }

    @Test
    fun mapsTaaMarbutaAndAlefMaqsura() {
        assertEquals("ممتازه", ArabicNormalizer.normalize("ممتازة"))
        assertEquals("مستشفي", ArabicNormalizer.normalize("مستشفى"))
    }

    @Test
    fun foldsArabicIndicAndPersianDigits() {
        // "ألم ٧" vs "ألم 7" used to be different strings, so a model quote
        // that re-wrote the digit failed quote matching and the observation
        // was dropped.
        assertEquals("الم 7", ArabicNormalizer.normalize("ألم ٧"))
        assertEquals("7", ArabicNormalizer.normalize("٧"))
        assertEquals("7", ArabicNormalizer.normalize("۷"))
        assertEquals("12", ArabicNormalizer.normalize("١٢"))
        assertEquals("12", ArabicNormalizer.normalize("۱۲"))
        // Numbers in quotes keep matching across the digit systems.
        assertEquals(
            ArabicNormalizer.normalize("الألم 7 من 10"),
            ArabicNormalizer.normalize("الألم ٧ من ١٠"),
        )
    }

    @Test
    fun stripsZeroWidthAndDirectionMarks() {
        val plain = ArabicNormalizer.normalize("سقطت اليوم")
        assertEquals(plain, ArabicNormalizer.normalize("سقطت\u200B اليوم"))
        assertEquals(plain, ArabicNormalizer.normalize("سقط\u200Dت اليوم"))
        assertEquals(plain, ArabicNormalizer.normalize("\u200Fسقطت اليوم"))
        assertEquals(plain, ArabicNormalizer.normalize("سقطت \u200Eاليوم"))
        assertEquals(plain, ArabicNormalizer.normalize("\uFEFFسقطت اليوم"))
    }

    @Test
    fun unifiesArabicAndLatinPunctuation() {
        // The model may retypeset Arabic commas/question marks as Latin:
        // the comparison must survive. Codepoint escapes keep both members
        // of each pair visibly different characters (\\u060C vs ',',
        // \\u061F vs '?', \\u00AB/\\u00BB vs quotes, \\u061B vs ';').
        assertEquals(
            ArabicNormalizer.normalize("ألم\u060C سقطت\u061F"),
            ArabicNormalizer.normalize("ألم, سقطت?"),
        )
        assertEquals(
            ArabicNormalizer.normalize("الرأي \u00ABجيد\u00BB\u061B"),
            ArabicNormalizer.normalize("الرأي \"جيد\";"),
        )
    }

    @Test
    fun foldsPersianLetterVariants() {
        // Persian kaf \u06A9 folds to Arabic kaf \u0643; Persian yeh \u06CC
        // folds to Arabic yeh \u064A — a Persian-styled input becomes the
        // same normalized string as its Arabic twin.
        assertEquals(
            ArabicNormalizer.normalize("\u0643\u062A\u0627\u0628"),
            ArabicNormalizer.normalize("\u06A9\u062A\u0627\u0628"),
        )
        assertEquals(
            ArabicNormalizer.normalize("\u0631\u0623\u064A\u064A"),
            ArabicNormalizer.normalize("\u0631\u0623\u06CC\u064A"),
        )
    }
}
