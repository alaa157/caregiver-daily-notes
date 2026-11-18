package com.caregiver.mobile.data.accounts

/**
 * One reason a password was rejected, typed so the UI can localize each
 * variant separately (#588, epic #581).
 */
sealed interface PasswordProblem {
    /** Fewer than [PasswordPolicy.MIN_LENGTH] code points. */
    data object TooShort : PasswordProblem

    /** More than [PasswordPolicy.MAX_LENGTH] code points — rejected, never truncated. */
    data object TooLong : PasswordProblem

    /** Contains no letter (`Character.isLetter`, so Arabic script counts). */
    data object MissingLetter : PasswordProblem

    /** Contains no digit (`Character.isDigit`, so Arabic-Indic digits count). */
    data object MissingDigit : PasswordProblem

    /** Entirely whitespace. */
    data object OnlyWhitespace : PasswordProblem
}

/**
 * The single password policy shared by `AccountManager.signUp` (#590) and
 * the register form (#599), so a client-side pass can never be rejected
 * server-side for a different reason.
 *
 * Length is counted in Unicode code points, not UTF-16 units, so Arabic
 * text and emoji are not double-counted. Pure Kotlin — no Android
 * dependencies, JVM-testable.
 */
object PasswordPolicy {
    const val MIN_LENGTH = 8
    const val MAX_LENGTH = 128

    fun validate(password: String): PasswordProblem? {
        val length = password.codePointCount(0, password.length)
        if (length < MIN_LENGTH) return PasswordProblem.TooShort
        if (length > MAX_LENGTH) return PasswordProblem.TooLong
        if (password.isBlank()) return PasswordProblem.OnlyWhitespace

        var hasLetter = false
        var hasDigit = false
        var i = 0
        while (i < password.length && !(hasLetter && hasDigit)) {
            val codePoint = password.codePointAt(i)
            hasLetter = hasLetter || Character.isLetter(codePoint)
            hasDigit = hasDigit || Character.isDigit(codePoint)
            i += Character.charCount(codePoint)
        }
        if (!hasLetter) return PasswordProblem.MissingLetter
        if (!hasDigit) return PasswordProblem.MissingDigit
        return null
    }
}
