package com.caregiver.mobile.ai

import android.content.Context
import android.content.SharedPreferences

/**
 * The state of the stored API key.
 *
 * [Invalidated] is the state that used to be invisible: the keystore key was
 * invalidated (a lock-screen change) or lost, the stored bytes can no longer
 * be decrypted, and the user has to enter the key again. Reporting it as
 * "no key" would send the caregiver looking for a key they never removed.
 */
sealed interface ApiKeyState {
    /** No key saved yet. */
    data object Absent : ApiKeyState

    /** A saved key that can be used. */
    data class Readable(val key: String) : ApiKeyState

    /** A saved key that can no longer be decrypted: enter it again. */
    data object Invalidated : ApiKeyState

    /** The keystore itself failed; the stored key may still be intact. */
    data object Unavailable : ApiKeyState
}

/**
 * Encrypts the API key at rest.
 *
 * Production uses the hardware-backed AndroidKeyStore ([AndroidKeyStoreCipher]).
 * The interface exists so tests can run on a JVM that has no keystore
 * provider at all: the only plaintext implementation lives in the test source
 * set, so production code cannot store an unencrypted key by accident.
 */
interface ApiKeyCipher {
    /** The stored form of [plain], or null when the keystore refused the write. */
    fun encrypt(plain: String): String?

    /** Reads back a stored key. Never returns [ApiKeyState.Absent]. */
    fun decrypt(stored: String): ApiKeyState
}

class AiKeyStore internal constructor(
    private val context: Context,
    private val cipher: ApiKeyCipher,
) {
    /**
     * Production: the key is encrypted with a hardware-backed keystore key.
     */
    constructor(context: Context) : this(context, AndroidKeyStoreCipher())

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        migrateLegacySettings()
    }

    fun provider(): AiProvider? =
        prefs.getString(KEY_PROVIDER, null)?.let { runCatching { AiProvider.valueOf(it) }.getOrNull() }

    /** The provider whose key and connection settings are currently selected. */
    fun selectedProvider(): AiProvider = provider() ?: AiProvider.OPENAI

    fun setProvider(p: AiProvider) {
        prefs.edit().putString(KEY_PROVIDER, p.name).apply()
    }

    /**
     * The stored key for [provider] and what can be done with it.
     *
     * Keys live in one slot per provider. Switching provider must never make
     * the app reuse the previous provider's credential.
     */
    fun apiKeyState(provider: AiProvider = selectedProvider()): ApiKeyState {
        val stored = prefs.getString(keySlot(provider), null) ?: return ApiKeyState.Absent
        return cipher.decrypt(stored)
    }

    /** The stored key for [provider], or null when there is none or it cannot be read. */
    fun apiKey(provider: AiProvider = selectedProvider()): String? =
        (apiKeyState(provider) as? ApiKeyState.Readable)?.key

    /**
     * Stores the key for [provider].
     *
     * Returns false when the keystore refused the write. The caller tells the
     * user; the screen never crashes on a keystore failure.
     */
    fun setApiKey(key: String, provider: AiProvider = selectedProvider()): Boolean {
        val stored = cipher.encrypt(key) ?: return false
        prefs.edit().putString(keySlot(provider), stored).apply()
        return true
    }

    fun clearApiKey(provider: AiProvider = selectedProvider()) {
        prefs.edit().remove(keySlot(provider)).apply()
    }

    /** True when a usable, non-blank key is stored for [provider]. */
    fun hasKey(provider: AiProvider = selectedProvider()): Boolean =
        (apiKeyState(provider) as? ApiKeyState.Readable)?.key?.isNotBlank() == true

    fun model(provider: AiProvider = selectedProvider()): String? =
        prefs.getString(modelSlot(provider), null)?.takeIf { it.isNotBlank() }

    fun setModel(m: String, provider: AiProvider = selectedProvider()) {
        prefs.edit().putString(modelSlot(provider), m).apply()
    }

    fun baseUrl(provider: AiProvider = selectedProvider()): String? =
        prefs.getString(baseUrlSlot(provider), null)

    fun setBaseUrl(url: String, provider: AiProvider = selectedProvider()) {
        prefs.edit().putString(baseUrlSlot(provider), url).apply()
    }

    /** The saved base URL for [provider], or its default when none was saved. */
    fun baseUrlOrDefault(provider: AiProvider): String =
        baseUrl(provider)?.takeIf { it.isNotBlank() } ?: provider.defaultBaseUrl

    fun consentGiven(): Boolean = prefs.getBoolean(KEY_CONSENT, false)

    fun setConsentGiven() {
        prefs.edit().putBoolean(KEY_CONSENT, true).apply()
    }

    fun revokeConsent() {
        prefs.edit().putBoolean(KEY_CONSENT, false).apply()
    }

    fun systemPrompt(): String = prefs.getString(KEY_SYSTEM_PROMPT, "").orEmpty()

    fun setSystemPrompt(value: String) {
        prefs.edit().putString(KEY_SYSTEM_PROMPT, value).apply()
    }

    fun summarySystemPrompt(): String = prefs.getString(KEY_SUMMARY_PROMPT, "").orEmpty()

    fun setSummarySystemPrompt(value: String) {
        prefs.edit().putString(KEY_SUMMARY_PROMPT, value).apply()
    }


    fun planSystemPrompt(): String = prefs.getString(KEY_PLAN_PROMPT, "").orEmpty()

    fun setPlanSystemPrompt(value: String) {
        prefs.edit().putString(KEY_PLAN_PROMPT, value).apply()
    }

    /**
     * Older builds kept one key, model, and base URL for whichever provider
     * was selected. Move those entries into that provider's slots so an
     * upgrade does not make the saved key disappear. The legacy entries are
     * only removed after their values have been copied; if the process dies
     * first, the migration runs again on the next start.
     */
    private fun migrateLegacySettings() {
        val selected = selectedProvider()
        val editor = prefs.edit()
        var changed = false

        fun migrate(legacyKey: String, targetKey: String) {
            val value = prefs.getString(legacyKey, null) ?: return
            if (!prefs.contains(targetKey)) {
                editor.putString(targetKey, value)
            }
            editor.remove(legacyKey)
            changed = true
        }

        migrate(KEY_API_LEGACY, keySlot(selected))
        migrate(KEY_MODEL_LEGACY, modelSlot(selected))
        migrate(KEY_BASE_URL_LEGACY, baseUrlSlot(selected))
        if (changed) editor.apply()
    }

    private fun keySlot(provider: AiProvider): String = KEY_API_PREFIX + provider.name

    private fun modelSlot(provider: AiProvider): String = KEY_MODEL_PREFIX + provider.name

    private fun baseUrlSlot(provider: AiProvider): String = KEY_BASE_URL_PREFIX + provider.name

    companion object {
        private const val PREFS_NAME = "ai_settings"
        private const val KEY_PROVIDER = "provider"
        private const val KEY_API_LEGACY = "api_key"
        private const val KEY_MODEL_LEGACY = "model"
        private const val KEY_BASE_URL_LEGACY = "base_url"
        private const val KEY_API_PREFIX = "api_key_"
        private const val KEY_MODEL_PREFIX = "model_"
        private const val KEY_BASE_URL_PREFIX = "base_url_"
        private const val KEY_CONSENT = "consent"
        private const val KEY_SYSTEM_PROMPT = "system_prompt"
        private const val KEY_SUMMARY_PROMPT = "summary_prompt"
        private const val KEY_PLAN_PROMPT = "plan_prompt"
    }
}
