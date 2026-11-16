package com.caregiver.mobile.data.accounts

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

private const val KDF_ALGORITHM = "PBKDF2WithHmacSHA256"

/**
 * The three values an account row needs in order to re-derive its hash later.
 *
 * [hash] and [salt] are Base64 without wrapping; [iterations] is the cost that
 * was actually used and must be stored per row, so raising the default later
 * cannot lock an existing account out. The generated `toString` is redacted:
 * neither value may reach a log through an ordinary log statement or string
 * interpolation.
 */
data class HashedPassword(
    val hash: String,
    val salt: String,
    val iterations: Int,
) {
    override fun toString(): String =
        "HashedPassword(hash=<redacted>, salt=<redacted>, iterations=$iterations)"
}

/**
 * PBKDF2-HMAC-SHA256 password hashing for local accounts (epic #581).
 *
 * Nothing in this path depends on the Android framework, so it is JVM-testable
 * without a device or Robolectric. Every [hash] call gets a fresh
 * [SALT_BYTES]-byte salt from [SecureRandom], and the cost actually used is
 * returned with the result for the caller to persist.
 *
 * [verify] deliberately uses the iteration count handed to it, not this
 * object's configured cost: re-deriving at a newer cost would reject every
 * older account. Constant-time comparison is out of scope (#587) — this class
 * only performs the plain equality check needed to answer the question.
 */
class PasswordHasher(
    private val iterations: Int = DEFAULT_ITERATIONS,
) {
    private val secureRandom = SecureRandom()

    init {
        require(iterations > 0) { "iterations must be positive" }
    }

    /**
     * Hashes [password], generating a fresh salt unless one is supplied (the
     * parameter exists for known-answer tests and must never be reused in
     * production). The returned [HashedPassword] carries the cost used.
     */
    fun hash(password: String, salt: ByteArray = newSalt()): HashedPassword {
        require(salt.size >= SALT_BYTES) { "salt must be at least $SALT_BYTES bytes" }
        return HashedPassword(
            hash = Base64.getEncoder().encodeToString(derive(password, salt, iterations)),
            salt = Base64.getEncoder().encodeToString(salt),
            iterations = iterations,
        )
    }

    /**
     * True when [password] re-derives [hash] from [salt] at the *stored*
     * [iterations]. Malformed stored values and crypto failures are a failed
     * verification, never a crash and never a logged secret.
     */
    fun verify(password: String, hash: String, salt: String, iterations: Int): Boolean {
        if (iterations <= 0) return false
        return try {
            val saltBytes = Base64.getDecoder().decode(salt)
            if (saltBytes.size < SALT_BYTES) return false
            val expected = Base64.getDecoder().decode(hash)
            val actual = derive(password, saltBytes, iterations)
            // Plain equality only; #587 replaces this with a constant-time compare.
            expected.contentEquals(actual)
        } catch (_: IllegalArgumentException) {
            false
        } catch (_: java.security.GeneralSecurityException) {
            false
        }
    }

    /** A fresh salt for one account or one rehash. Never reused. */
    fun newSalt(): ByteArray = ByteArray(SALT_BYTES).also { secureRandom.nextBytes(it) }

    private fun derive(password: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, KEY_LENGTH_BITS)
        return try {
            SecretKeyFactory.getInstance(KDF_ALGORITHM).generateSecret(spec).encoded
        } finally {
            // Drop our copy of the password as soon as the key is derived.
            spec.clearPassword()
        }
    }

    companion object {
        /**
         * Placeholder from #585. #586 measures device cost and records the
         * final value (600k is the OWASP-aligned candidate for SHA-256). The
         * cost is stored per row, so changing this cannot lock out accounts.
         */
        const val DEFAULT_ITERATIONS: Int = 600_000

        /** Generated salt length, and the minimum accepted on [hash]. */
        const val SALT_BYTES: Int = 16

        /** Derived key length in bits. */
        const val KEY_LENGTH_BITS: Int = 256
    }
}
