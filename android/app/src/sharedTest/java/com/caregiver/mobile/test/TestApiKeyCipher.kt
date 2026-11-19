package com.caregiver.mobile.test

import com.caregiver.mobile.ai.ApiKeyCipher
import com.caregiver.mobile.ai.ApiKeyState
import java.util.Base64

/**
 * Test-only key storage.
 *
 * Robolectric and plain JVM tests cannot serve the "AndroidKeyStore"
 * provider, so the key is stored Base64-encoded instead. This lives in the
 * test source set on purpose: the production code has no plaintext path at
 * all, so no shipped build can end up with an unencrypted key.
 */
class TestApiKeyCipher : ApiKeyCipher {
    override fun encrypt(plain: String, accountId: String): String? =
        Base64.getEncoder().encodeToString(plain.toByteArray(Charsets.UTF_8))

    override fun decrypt(stored: String, accountId: String): ApiKeyState {
        val decoded = runCatching { Base64.getDecoder().decode(stored) }.getOrNull()
            ?: return ApiKeyState.Invalidated
        return ApiKeyState.Readable(String(decoded, Charsets.UTF_8))
    }

    /** No real keystore alias exists in the JVM fake; deletion is a no-op. */
    override fun delete(accountId: String) {}
}
