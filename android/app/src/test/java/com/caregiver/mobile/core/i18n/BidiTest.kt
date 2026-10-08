package com.caregiver.mobile.core.i18n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Task 8: bidi-safe mixed text. Arabic UI with English names/numbers and
 * Egyptian-dialect quotes inside evidence must stay readable — every
 * interpolated string gets FSI/PDI isolation.
 */
class BidiTest {

    @Test
    fun isolateWrapsWithFsiPdi() {
        val out = Bidi.isolate("Ahmed")
        assertTrue(out.startsWith("\u2066"))
        assertTrue(out.endsWith("\u2069"))
        assertTrue(out.contains("Ahmed"))
    }

    @Test
    fun isolateEmptyStaysEmpty() {
        assertEquals("", Bidi.isolate(""))
    }

    @Test
    fun isolatePreservesNumbersAndPlaceholders() {
        val out = Bidi.isolate("note #104")
        assertTrue(out.contains("note #104"))
        assertTrue(out.startsWith("\u2066"))
        assertTrue(out.endsWith("\u2069"))
    }

    @Test
    fun isolateArabicWithEnglishName() {
        // English recipient name inside an Arabic row.
        val out = Bidi.isolate("John")
        assertEquals("\u2066John\u2069", out)
    }

    @Test
    fun isolateDoesNotDoubleWrap() {
        val once = Bidi.isolate("Layla")
        // Isolating twice must not nest markers — idempotent by contract.
        assertEquals(once, Bidi.isolate(once))
    }
}
