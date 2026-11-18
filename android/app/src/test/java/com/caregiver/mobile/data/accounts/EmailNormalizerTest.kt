package com.caregiver.mobile.data.accounts

import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** #584: pure JVM rules for the local login key — no Robolectric needed. */
class EmailNormalizerTest {
    private val originalLocale = Locale.getDefault()

    @After
    fun restoreLocale() {
        Locale.setDefault(originalLocale)
    }

    @Test
    fun trimsAndFoldsCase() {
        assertEquals("a@b.com", EmailNormalizer.normalize("  A@B.COM "))
        assertEquals("a@b.com", EmailNormalizer.normalize("A@B.COM"))
    }

    @Test
    fun lowercasingIsLocaleIndependent() {
        // The Turkish default locale maps 'I' to 'ı'; ROOT must keep 'i'.
        Locale.setDefault(Locale.forLanguageTag("tr-TR"))
        assertEquals("i@x.com", EmailNormalizer.normalize("I@X.COM"))
    }

    @Test
    fun nfkcFoldingMakesComposedAndDecomposedEqual() {
        val decomposed = EmailNormalizer.normalize("e\u0301@x.com")
        val composed = EmailNormalizer.normalize("\u00e9@x.com")
        assertNotNull(decomposed)
        assertEquals(composed, decomposed)
        assertEquals("\u00e9@x.com", decomposed)
    }

    @Test
    fun acceptsArabicScriptLocalPartWithoutTransliteration() {
        assertEquals("ليلى@example.com", EmailNormalizer.normalize("ليلى@EXAMPLE.COM"))
    }

    @Test
    fun rejectsEmptyAfterTrim() {
        assertNull(EmailNormalizer.normalize(""))
        assertNull(EmailNormalizer.normalize("   \t "))
    }

    @Test
    fun rejectsMissingOrMultipleAtSigns() {
        assertNull(EmailNormalizer.normalize("abc.example.com"))
        assertNull(EmailNormalizer.normalize("a@b@example.com"))
        assertNull(EmailNormalizer.normalize("a@@b.com"))
    }

    @Test
    fun rejectsEmptyLocalOrDomainPart() {
        assertNull(EmailNormalizer.normalize("@b.com"))
        assertNull(EmailNormalizer.normalize("a@"))
        assertNull(EmailNormalizer.normalize("@"))
    }

    @Test
    fun rejectsWhitespaceAndControlCharacters() {
        assertNull(EmailNormalizer.normalize("a b@example.com"))
        assertNull(EmailNormalizer.normalize("a@b .com"))
        assertNull(EmailNormalizer.normalize("a\u0000b@example.com"))
        assertNull(EmailNormalizer.normalize("a\u007fb@example.com"))
    }

    @Test
    fun acceptsExactly254CharactersAndRejectsLonger() {
        // 1 local char + '@' + domain: build a 254-char address exactly.
        val domain = "@" + "b".repeat(9) + ".com" // 14 chars
        val at254 = "a".repeat(254 - domain.length) + domain
        assertEquals(254, at254.length)
        assertNotNull(EmailNormalizer.normalize(at254))

        val at255 = "a".repeat(255 - domain.length) + domain
        assertEquals(255, at255.length)
        assertNull(EmailNormalizer.normalize(at255))
    }

    @Test
    fun nfkcExpansionPastLimitIsRejected() {
        // NFKC can grow a string (ligature ﬁ -> "fi"). 128 ligatures are 128
        // chars before folding but 256 after, so the post-fold guard catches
        // the expansion even though the raw input was under the limit.
        val raw = "\uFB01".repeat(128) + "@x.com"
        assertEquals(134, raw.length)
        assertNull(EmailNormalizer.normalize(raw))
    }
}
