package com.caregiver.mobile.presentation.auth

import com.caregiver.mobile.R
import com.caregiver.mobile.data.accounts.AccountError
import com.caregiver.mobile.data.accounts.PasswordProblem
import org.junit.Assert.assertEquals
import org.junit.Test

class AuthErrorResourcesTest {

    private val problems = listOf(
        PasswordProblem.TooShort,
        PasswordProblem.TooLong,
        PasswordProblem.MissingLetter,
        PasswordProblem.MissingDigit,
        PasswordProblem.OnlyWhitespace,
    )

    @Test
    fun everyPasswordProblemHasMappedString() {
        assertEquals(R.string.auth_password_problem_too_short, passwordProblemRes(PasswordProblem.TooShort))
        assertEquals(R.string.auth_password_problem_too_long, passwordProblemRes(PasswordProblem.TooLong))
        assertEquals(R.string.auth_password_problem_missing_letter, passwordProblemRes(PasswordProblem.MissingLetter))
        assertEquals(R.string.auth_password_problem_missing_digit, passwordProblemRes(PasswordProblem.MissingDigit))
        assertEquals(R.string.auth_password_problem_only_whitespace, passwordProblemRes(PasswordProblem.OnlyWhitespace))
    }

    @Test
    fun everyAccountErrorHasMappedString() {
        assertEquals(R.string.auth_error_duplicate_email, accountErrorRes(AccountError.EmailTaken))
        assertEquals(R.string.auth_error_invalid_credentials, accountErrorRes(AccountError.InvalidCredentials))
        assertEquals(R.string.auth_error_account_not_found, accountErrorRes(AccountError.AccountNotFound))
        assertEquals(R.string.account_cannot_remove_active, accountErrorRes(AccountError.CannotRemoveActive))
        assertEquals(R.string.auth_error_email_invalid, accountErrorRes(AccountError.InvalidEmail))
        problems.forEach { problem ->
            assertEquals(passwordProblemRes(problem), accountErrorRes(AccountError.WeakPassword(problem)))
        }
    }
}
