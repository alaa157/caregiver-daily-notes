package com.caregiver.mobile.data.accounts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * #588: every [PasswordProblem] variant reachable, length boundaries in
 * code points (7 vs 8, 128 vs 129), and non-Latin scripts counted once.
 */
class PasswordPolicyTest {

    @Test
    fun compliantPasswordReturnsNull() {
        assertNull(PasswordPolicy.validate("caregiver1"))
    }

    @Test
    fun sevenCharactersIsTooShort() {
        assertEquals(
            PasswordProblem.TooShort,
            PasswordPolicy.validate("caregi1"),
        )
    }

    @Test
    fun eightCharactersPassesLength() {
        assertNull(PasswordPolicy.validate("abcdefg1"))
    }

    @Test
    fun oneHundredTwentyEightCharactersPasses() {
        val password = "a".repeat(127) + "1"
        assertEquals(128, password.codePointCount(0, password.length))
        assertNull(PasswordPolicy.validate(password))
    }

    @Test
    fun oneHundredTwentyNineCharactersIsTooLong() {
        val password = "a".repeat(128) + "1"
        assertEquals(PasswordProblem.TooLong, PasswordPolicy.validate(password))
    }

    @Test
    fun whitespaceOnlyIsRejected() {
        assertEquals(
            PasswordProblem.OnlyWhitespace,
            PasswordPolicy.validate("        "),
        )
    }

    @Test
    fun lettersOnlyIsMissingDigit() {
        assertEquals(
            PasswordProblem.MissingDigit,
            PasswordPolicy.validate("abcdefgh"),
        )
    }

    @Test
    fun digitsOnlyIsMissingLetter() {
        assertEquals(
            PasswordProblem.MissingLetter,
            PasswordPolicy.validate("12345678"),
        )
    }

    @Test
    fun eightArabicCharactersCountAsEight() {
        // "كلمةالسر" is 8 Arabic letters — passes length, needs a digit.
        assertEquals(
            PasswordProblem.MissingDigit,
            PasswordPolicy.validate("كلمةالسر"),
        )
        assertNull(PasswordPolicy.validate("كلمةالسر١"))
    }

    @Test
    fun emojiCountAsOneCodePointEach() {
        // 4 emoji = 4 code points (8 UTF-16 units) — too short, not 8.
        assertEquals(
            PasswordProblem.TooShort,
            PasswordPolicy.validate("😀😁😂😃"),
        )
        // 8 emoji = 8 code points — passes length, fails letter+digit.
        assertEquals(
            PasswordProblem.MissingLetter,
            PasswordPolicy.validate("😀😁😂😃😄😅😆😇"),
        )
    }

    @Test
    fun arabicIndicDigitsCountAsDigits() {
        assertNull(PasswordPolicy.validate("abcdefgh١"))
    }

    @Test
    fun tooShortWhitespaceIsTooShort() {
        assertEquals(PasswordProblem.TooShort, PasswordPolicy.validate("   "))
    }
}
