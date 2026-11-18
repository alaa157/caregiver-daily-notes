package com.caregiver.mobile.presentation.auth

import com.caregiver.mobile.R
import com.caregiver.mobile.data.accounts.AccountError
import com.caregiver.mobile.data.accounts.PasswordPolicy
import com.caregiver.mobile.data.accounts.PasswordProblem

fun passwordProblemRes(problem: PasswordProblem): Int = when (problem) {
    PasswordProblem.TooShort -> R.string.auth_password_problem_too_short
    PasswordProblem.TooLong -> R.string.auth_password_problem_too_long
    PasswordProblem.MissingLetter -> R.string.auth_password_problem_missing_letter
    PasswordProblem.MissingDigit -> R.string.auth_password_problem_missing_digit
    PasswordProblem.OnlyWhitespace -> R.string.auth_password_problem_only_whitespace
}

fun passwordProblemArgs(problem: PasswordProblem): Array<Any> = when (problem) {
    PasswordProblem.TooShort -> arrayOf<Any>(PasswordPolicy.MIN_LENGTH)
    PasswordProblem.TooLong -> arrayOf<Any>(PasswordPolicy.MAX_LENGTH)
    else -> emptyArray()
}

fun accountErrorRes(error: AccountError): Int = when (error) {
    AccountError.EmailTaken -> R.string.auth_error_duplicate_email
    AccountError.InvalidCredentials -> R.string.auth_error_invalid_credentials
    is AccountError.WeakPassword -> passwordProblemRes(error.problem)
    AccountError.AccountNotFound -> R.string.auth_error_account_not_found
    AccountError.CannotRemoveActive -> R.string.account_cannot_remove_active
    AccountError.InvalidEmail -> R.string.auth_error_email_invalid
}
