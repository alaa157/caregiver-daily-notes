package com.caregiver.mobile.data.accounts

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * #587 source-level guard. Hash and salt values must never be compared with a
 * short-circuiting operator: [MessageDigest.isEqual][java.security.MessageDigest.isEqual]
 * is the single sanctioned primitive, used in [PasswordHasher.verify]. This
 * test reads the checked-in sources so a future `==` / `equals` / `contentEquals`
 * regression fails in CI instead of relying on a reviewer to run a manual grep.
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
