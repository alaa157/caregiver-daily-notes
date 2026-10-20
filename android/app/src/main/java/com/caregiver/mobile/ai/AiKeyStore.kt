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

    fun provider(): AiProvider? =
        prefs.getString(KEY_PROVIDER, null)?.let { runCatching { AiProvider.valueOf(it) }.getOrNull() }

    fun setProvider(p: AiProvider) {
        prefs.edit().putString(KEY_PROVIDER, p.name).apply()
    }

    /** The stored key and what can be done with it. */
    fun apiKeyState(): ApiKeyState {
        val stored = prefs.getString(KEY_API, null) ?: return ApiKeyState.Absent
        return cipher.decrypt(stored)
    }

    /** The stored key, or null when there is none or it cannot be read. */
    fun apiKey(): String? = (apiKeyState() as? ApiKeyState.Readable)?.key

    /**
     * Stores the key.
     *
     * Returns false when the keystore refused the write. The caller tells the
     * user; the screen never crashes on a keystore failure.
     */
    fun setApiKey(key: String): Boolean {
        val stored = cipher.encrypt(key) ?: return false
        prefs.edit().putString(KEY_API, stored).apply()
        return true
    }

    fun clearApiKey() {
        prefs.edit().remove(KEY_API).apply()
    }

    /** True when a usable, non-blank key is stored. */
    fun hasKey(): Boolean =
        (apiKeyState() as? ApiKeyState.Readable)?.key?.isNotBlank() == true

    fun model(): String? =
        prefs.getString(KEY_MODEL, null)?.takeIf { it.isNotBlank() }

    fun setModel(m: String) {
        prefs.edit().putString(KEY_MODEL, m).apply()
    }

    fun baseUrl(): String? = prefs.getString(KEY_BASE_URL, null)

    fun setBaseUrl(url: String) {
        prefs.edit().putString(KEY_BASE_URL, url).apply()
    }

    /** The saved base URL, or the provider default when none was saved. */
    fun baseUrlOrDefault(provider: AiProvider): String =
        baseUrl()?.takeIf { it.isNotBlank() } ?: provider.defaultBaseUrl

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

    companion object {
        private const val PREFS_NAME = "ai_settings"
        private const val KEY_PROVIDER = "provider"
        private const val KEY_API = "api_key"
        private const val KEY_MODEL = "model"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_CONSENT = "consent"
        private const val KEY_SYSTEM_PROMPT = "system_prompt"
        private const val KEY_SUMMARY_PROMPT = "summary_prompt"
        private const val KEY_PLAN_PROMPT = "plan_prompt"
    }
}
