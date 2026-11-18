package com.caregiver.mobile.data.accounts

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * #585: JVM tests for the PBKDF2 primitive. Costs are kept low where the test
 * is about behaviour rather than KDF strength; the real default is #586's call.
 */
class PasswordHasherTest {

    @Test
    fun knownAnswerVector() {
        // Computed independently with Python:
        //   hashlib.pbkdf2_hmac('sha256', b'password', b'0123456789abcdef', 4096, 32)
        // A 16-byte ASCII salt is used so the vector goes through the public API.
        val salt = "0123456789abcdef".toByteArray(Charsets.US_ASCII)
        val result = PasswordHasher(iterations = 4_096).hash("password", salt)

        assertEquals(
            "615eeee652a9fe1cf9f427d6218526a937add01875d759837ac",
            hex(result.hash),
        )
        assertEquals(Base64.getEncoder().encodeToString(salt), result.salt)
        assertEquals(4_096, result.iterations)
    }

    @Test
    fun oneHundredHashesOfTheSamePasswordGetDifferentSaltsAndHashes() {
        // Cost is irrelevant to salt uniqueness, so keep this test fast.
        val hasher = PasswordHasher(iterations = 1)
        val results = (1..100).map { hasher.hash("same password") }

        assertEquals(100, results.map { it.salt }.toSet().size)
        assertEquals(100, results.map { it.hash }.toSet().size)
    }

    @Test
    fun saltIsSixteenBytesDecoded() {
        val hasher = PasswordHasher(iterations = 1)
        repeat(5) {
            val salt = Base64.getDecoder().decode(hasher.hash("password").salt)
            assertEquals(PasswordHasher.SALT_BYTES, salt.size)
        }
        assertEquals(16, PasswordHasher.SALT_BYTES)
        assertEquals(PasswordHasher.SALT_BYTES, hasher.newSalt().size)
    }

    @Test
    fun hashReportsTheIterationsItActuallyUsed() {
        val hasher = PasswordHasher(iterations = 2_048)
        assertEquals(2_048, hasher.hash("password").iterations)
        assertEquals(PasswordHasher.DEFAULT_ITERATIONS, PasswordHasher().hash("password").iterations)
    }

    @Test
    fun verifyAcceptsTheCorrectPassword() {
        val hasher = PasswordHasher(iterations = 1_000)
        val stored = hasher.hash("correct horse battery staple")

        assertTrue(hasher.verify("correct horse battery staple", stored.hash, stored.salt, stored.iterations))
    }

    @Test
    fun verifyRejectsAWrongPassword() {
        val hasher = PasswordHasher(iterations = 1_000)
        val stored = hasher.hash("right password")

        assertFalse(hasher.verify("wrong password", stored.hash, stored.salt, stored.iterations))
    }

    @Test
    fun verifyUsesTheStoredIterationsNotTheCurrentDefault() {
        // A hash made at a non-default cost must still verify with a hasher
        // configured at today's default, because the row carries its own cost.
        val stored = PasswordHasher(iterations = 1_234).hash("care notes")
        assertTrue(stored.iterations != PasswordHasher.DEFAULT_ITERATIONS)

        val current = PasswordHasher()
        assertTrue(current.verify("care notes", stored.hash, stored.salt, stored.iterations))
        // Re-deriving at any other cost cannot reproduce the stored hash.
        assertFalse(current.verify("care notes", stored.hash, stored.salt, stored.iterations + 1))
    }

    @Test
    fun verifyWithMismatchedIterationsReturnsFalse() {
        val hasher = PasswordHasher(iterations = 1_000)
        val stored = hasher.hash("password")

        assertFalse(hasher.verify("password", stored.hash, stored.salt, 999))
        assertFalse(hasher.verify("password", stored.hash, stored.salt, 1_001))
    }

    @Test
    fun unicodePasswordRoundTrips() {
        // #605: Arabic text and emoji are UTF-16-heavy; the hasher must
        // treat them as opaque characters end to end.
        val hasher = PasswordHasher(iterations = 1_000)
        for (password in listOf("كلمةالسر١", "🔒care1🚑", "pass word😀1")) {
            val stored = hasher.hash(password)
            assertTrue(hasher.verify(password, stored.hash, stored.salt, stored.iterations))
            assertFalse(hasher.verify("different", stored.hash, stored.salt, stored.iterations))
        }
    }

    @Test
    fun emptyPasswordIsNotRejectedByTheHasher() {
        // Emptiness is a password-policy decision (#588), not this primitive's:
        // the hasher must still be able to derive and verify one.
        val hasher = PasswordHasher(iterations = 1)
        val stored = hasher.hash("")

        assertTrue(hasher.verify("", stored.hash, stored.salt, stored.iterations))
    }

    @Test
    fun hashedPasswordToStringRedactsTheSecrets() {
        val stored = PasswordHasher(iterations = 1).hash("a password")
        val rendered = stored.toString()

        assertFalse(rendered.contains(stored.hash))
        assertFalse(rendered.contains(stored.salt))
        assertTrue(rendered.contains("redacted"))
        assertTrue(rendered.contains("iterations=1"))
    }

    @Test
    fun malformedStoredValuesFailVerificationWithoutThrowing() {
        // #587: a non-Base64 hash and an empty hash are failed verifications,
        // never exceptions and never a fall back to string comparison.
        val hasher = PasswordHasher(iterations = 1)
        val shortSalt = Base64.getEncoder().encodeToString(ByteArray(4))

        assertFalse(hasher.verify("password", "not base64", "also not base64", 1))
        assertFalse(hasher.verify("password", "", shortSalt, 1))
        assertFalse(hasher.verify("password", "AAAA", shortSalt, 0))
    }

    @Test
    fun verifyReturnsFalseForAHashOfTheWrongLength() {
        // #587: MessageDigest.isEqual over decoded bytes must reject a hash
        // that decodes to the wrong number of bytes (too short and too long)
        // rather than throwing or comparing text. The valid salt proves the
        // failure is about the hash, not an earlier malformed-input guard.
        val hasher = PasswordHasher(iterations = 1)
        val validSalt = Base64.getEncoder().encodeToString(ByteArray(PasswordHasher.SALT_BYTES))
        val expectedLength = PasswordHasher.KEY_LENGTH_BITS / 8 // 32 bytes

        val tooShort = Base64.getEncoder().encodeToString(ByteArray(expectedLength - 1))
        val tooLong = Base64.getEncoder().encodeToString(ByteArray(expectedLength + 1))
        val empty = Base64.getEncoder().encodeToString(ByteArray(0))

        assertFalse(hasher.verify("password", tooShort, validSalt, 1))
        assertFalse(hasher.verify("password", tooLong, validSalt, 1))
        assertFalse(hasher.verify("password", empty, validSalt, 1))
    }

    // ── #586: KdfPolicy.needsRehash ──────────────────────────────────────

    @Test
    fun needsRehashIsTrueForIterationsBelowTheDefault() {
        assertTrue(KdfPolicy.needsRehash(PasswordHasher.DEFAULT_ITERATIONS - 1))
        assertTrue(KdfPolicy.needsRehash(1))
    }

    @Test
    fun needsRehashIsFalseAtOrAboveTheDefault() {
        assertFalse(KdfPolicy.needsRehash(PasswordHasher.DEFAULT_ITERATIONS))
        assertFalse(KdfPolicy.needsRehash(PasswordHasher.DEFAULT_ITERATIONS + 1))
    }

    @Test
    fun rehashProducesANewSaltThatStillVerifies() {
        // The hasher-level half of #586: a row stored at a low cost is
        // re-derived at the default with a fresh salt, and the same password
        // still verifies against the new row. AccountManager.signIn owns the
        // "only after a successful verify, never fail the login" wiring.
        val oldHasher = PasswordHasher(iterations = 1)
        val old = oldHasher.hash("correct horse")
        assertTrue(KdfPolicy.needsRehash(old.iterations))

        val currentHasher = PasswordHasher()
        val upgraded = currentHasher.hash("correct horse")

        assertEquals(PasswordHasher.DEFAULT_ITERATIONS, upgraded.iterations)
        assertTrue(upgraded.salt != old.salt)
        assertTrue(currentHasher.verify("correct horse", upgraded.hash, upgraded.salt, upgraded.iterations))
        assertFalse(currentHasher.verify("wrong", upgraded.hash, upgraded.salt, upgraded.iterations))
        // The old row still verifies at its own stored cost — rehash is additive.
        assertTrue(oldHasher.verify("correct horse", old.hash, old.salt, old.iterations))
    }

    private fun hex(bytes: ByteArray): String =
        bytes.joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
