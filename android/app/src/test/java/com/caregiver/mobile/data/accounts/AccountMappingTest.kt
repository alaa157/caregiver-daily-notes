package com.caregiver.mobile.data.accounts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * #589: entity → account mapping drops every credential field, and the
 * entity's `toString` cannot leak a known hash or salt.
 */
class AccountMappingTest {

    private val entity = AccountEntity(
        id = "acc-1",
        email = "caregiver@example.com",
        displayName = "Amina",
        passwordHash = "pbkdf2\$supersecrethashvalue",
        salt = "supersaltvalue",
        kdfIterations = 100_000,
        createdAt = 1_700_000_000_000L,
        lastUsedAt = 1_700_000_100_000L,
    )

    @Test
    fun toAccountDropsCredentials() {
        val account = entity.toAccount()

        assertEquals(Account(
            id = "acc-1",
            email = "caregiver@example.com",
            displayName = "Amina",
            createdAt = 1_700_000_000_000L,
            lastUsedAt = 1_700_000_100_000L,
        ), account)
    }

    @Test
    fun accountHasNoCredentialFields() {
        // Compile-time guarantee by construction; this asserts the runtime
        // shape stays free of credential-looking values.
        val account = entity.toAccount().toString()
        assertFalse(account.contains("supersecrethashvalue"))
        assertFalse(account.contains("supersaltvalue"))
        // The iteration count must not leak; check the field name, since a
        // bare "100000" can coincidentally occur inside a timestamp.
        assertFalse(account.contains("kdfIterations"))
    }

    @Test
    fun entityToStringOmitsHashAndSalt() {
        val printed = entity.toString()
        assertFalse(printed.contains("supersecrethashvalue"))
        assertFalse(printed.contains("supersaltvalue"))
        assertFalse(printed.contains("kdfIterations"))
    }

    @Test
    fun entityToStringKeepsSafeFields() {
        val printed = entity.toString()
        assertTrue(printed.contains("acc-1"))
        assertTrue(printed.contains("caregiver@example.com"))
        assertTrue(printed.contains("Amina"))
    }

    @Test
    fun accountErrorIsExhaustiveWithoutElse() {
        // A `when` over every variant with no `else` must compile — this is
        // the compiler-checked exhaustiveness the UI relies on.
        fun label(error: AccountError): String = when (error) {
            AccountError.EmailTaken -> "taken"
            AccountError.InvalidCredentials -> "credentials"
            is AccountError.WeakPassword -> "weak:${error.problem}"
            AccountError.AccountNotFound -> "missing"
            AccountError.CannotRemoveActive -> "active"
            AccountError.InvalidEmail -> "email"
        }

        assertEquals("weak:TooShort", label(AccountError.WeakPassword(PasswordProblem.TooShort)))
        assertEquals("taken", label(AccountError.EmailTaken))
    }
}
