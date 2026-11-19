package com.caregiver.mobile.data.accounts

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * #587 source-level guard + #604 logging audit.
 *
 * ## #587
 * Hash and salt values must never be compared with a short-circuiting operator:
 * [MessageDigest.isEqual][java.security.MessageDigest.isEqual] is the single
 * sanctioned primitive, used in [PasswordHasher.verify]. This test reads the
 * checked-in sources so a future `==` / `equals` / `contentEquals` regression
 * fails in CI instead of relying on a reviewer to run a manual grep.
 *
 * ## #604
 * Credential material (password, hash, salt) must never reach a log or crash
 * report. This test scans `data/accounts/` for `Log.` or `println` statements
 * that interpolate credential fields, and asserts that `AccountEntity` and
 * `HashedPassword` redact their sensitive fields in `toString()`. Exception
 * messages are also scanned.
 *
 * Only `src/main/java/.../data/accounts` is scanned — the one package that
 * handles secrets. Test sources and generated code are out of scope.
 */
class NoInsecureHashComparisonTest {

    /** PasswordHasher is the single place any hash/salt comparison may live. */
    @Test
    fun passwordHasherUsesConstantTimeComparison() {
        val code = stripComments(passwordHasherSource().readText())

        assertTrue("PasswordHasher.verify must call MessageDigest.isEqual", code.contains("MessageDigest.isEqual"))
        for (token in FORBIDDEN_COMPARISONS) {
            assertFalse("PasswordHasher.kt must not use '$token'", code.contains(token))
        }
    }

    /** No equals/contentEquals against hash or salt anywhere in the package. */
    @Test
    fun noEqualsOrContentEqualsInAccountsPackage() {
        for (file in accountsMainSources()) {
            val code = stripComments(file.readText())
            for (token in listOf("contentEquals", ".equals(")) {
                assertFalse("${file.name} must not use '$token' for a hash/salt comparison", code.contains(token))
            }
        }
    }

    /** No `==` / `!=` on a line that references a hash or salt field. */
    @Test
    fun noDoubleEqualsAgainstHashOrSaltFields() {
        val secretWord = Regex("(hash|salt)", RegexOption.IGNORE_CASE)
        val comparison = Regex("={2,3}|!={1,2}")
        for (file in accountsMainSources()) {
            stripComments(file.readText()).lineSequence().forEach { line ->
                if (secretWord.containsMatchIn(line) && comparison.containsMatchIn(line)) {
                    throw AssertionError("${file.name} compares a hash/salt with == or != : ${line.trim()}")
                }
            }
        }
    }

    // ── #604: no credential material in logs or crash reports ───────────────

    @Test
    fun accountEntityToStringRedactsCredentials() {
        val entityFile = File(locateAccountsMainDir(), "AccountEntity.kt")
        assertTrue("AccountEntity.kt must exist", entityFile.exists())
        val source = stripComments(entityFile.readText())

        assertTrue("toString must be overridden", "override fun toString()" in source)
        val toStringBlock = source.substringAfter("override fun toString()")
            .substringBefore("}")
        assertFalse("toString must not interpolate passwordHash",
            "passwordHash" in toStringBlock)
        assertFalse("toString must not interpolate salt",
            "salt" in toStringBlock)
        assertFalse("toString must not interpolate kdfIterations",
            "kdfIterations" in toStringBlock)
    }

    @Test
    fun hashedPasswordToStringRedactsTheHash() {
        val hasherFile = passwordHasherSource()
        val source = stripComments(hasherFile.readText())

        assertTrue("HashedPassword.toString must be overridden",
            "override fun toString()" in source)
        val toStringBlock = source.substringAfter("override fun toString()")
            .substringBefore("}")
        assertTrue("toString must redact the hash",
            "redacted" in toStringBlock.toLowerCase())
        // Redacted placeholders (hash=<redacted>) are safe; interpolating the
        // actual field values is not.
        assertFalse("toString must not interpolate the raw hash field",
            "\$hash" in toStringBlock || "\${hash" in toStringBlock)
        assertFalse("toString must not interpolate the raw salt field",
            "\$salt" in toStringBlock || "\${salt" in toStringBlock)
    }

    @Test
    fun noLogStatementsInterpolateCredentialFields() {
        for (file in accountsMainSources()) {
            val source = stripComments(file.readText())
            val lines = source.lines()
            lines.forEachIndexed { index, line ->
                if ("Log." in line || "println(" in line) {
                    assertFalse(
                        "${file.name}:${index + 1} must not log password/hash/salt",
                        listOf("password", "hash", "salt").any { field ->
                            "$$field" in line || "\$$field" in line
                        }
                    )
                }
            }
        }
    }

    @Test
    fun exceptionMessagesDoNotContainCredentialMaterial() {
        val hasherFile = passwordHasherSource()
        val source = stripComments(hasherFile.readText())
        val requireLines = source.lines()
            .filter { "require(" in it || "IllegalArgumentException(" in it }
        // Naming a parameter or a constant (e.g. "salt must be at least
        // $SALT_BYTES bytes") leaks nothing; interpolating a credential value
        // does. Match $field / ${field} at word boundaries so constant names
        // like $SALT_BYTES are not false positives.
        val interpolation = Regex("""\$(password|hash|salt)\b|\$\{(password|hash|salt)\b""")
        requireLines.forEach { line ->
            assertFalse(
                "require message must not interpolate credential material: $line",
                interpolation.containsMatchIn(line.lowercase())
            )
        }
    }

    private companion object {
        val FORBIDDEN_COMPARISONS = listOf("contentEquals", ".equals(", "==", "!=", "===", "!==")
        private const val RELATIVE = "src/main/java/com/caregiver/mobile/data/accounts"

        fun accountsMainSources(): List<File> {
            val dir = locateAccountsMainDir()
            return dir.listFiles { file -> file.isFile && file.extension == "kt" }?.toList()
                ?: throw AssertionError("no Kotlin sources in ${dir.path}")
        }

        fun passwordHasherSource(): File = File(locateAccountsMainDir(), "PasswordHasher.kt")

        /** Find the accounts source dir whether Gradle runs us from android/app or the repo root. */
        fun locateAccountsMainDir(): File {
            var cursor: File? = File(".").absoluteFile
            while (cursor != null) {
                for (candidate in listOf(File(cursor, RELATIVE), File(cursor, "android/app/$RELATIVE"))) {
                    if (candidate.isDirectory) return candidate
                }
                cursor = cursor.parentFile
            }
            throw AssertionError("could not locate $RELATIVE from ${System.getProperty("user.dir")}")
        }

        /** Drop line and block comments so documented tokens don't trip the guard. */
        fun stripComments(source: String): String =
            source.replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
                .replace(Regex("//[^\\n]*"), "")
    }
}
