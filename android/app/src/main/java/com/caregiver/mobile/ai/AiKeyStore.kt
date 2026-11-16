package com.caregiver.mobile.ai

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

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
 *
 * Methods take [accountId] because the keystore alias is namespaced per
 * account (#613).
 */
interface ApiKeyCipher {
    /** The stored form of [plain], or null when the keystore refused the write. */
    fun encrypt(plain: String, accountId: String): String?

    /** Reads back a stored key. Never returns [ApiKeyState.Absent]. */
    fun decrypt(stored: String, accountId: String): ApiKeyState

    /**
     * Deletes the keystore alias for [accountId]. A missing alias is a
     * no-op, never an error (#614).
     */
    fun delete(accountId: String)
}

/**
 * Adapter that bridges the old single-cipher construction to the new
 * per-account `ApiKeyCipher` interface. Production uses
 * `AndroidKeyStoreCipher` (which now takes accountId per call); tests can
 * inject a fake that ignores the accountId argument.
 */
private class PerAccountAndroidKeyStoreCipher(
    private val activeAccountId: Flow<String?>,
) : ApiKeyCipher {
    private val cipher = AndroidKeyStoreCipher()

    override fun encrypt(plain: String, accountId: String): String? =
        cipher.encrypt(plain, accountId)

    override fun decrypt(stored: String, accountId: String): ApiKeyState =
        cipher.decrypt(stored, accountId)

    override fun delete(accountId: String) = cipher.delete(accountId)
}

class AiKeyStore internal constructor(
    private val context: Context,
    private val cipher: ApiKeyCipher,
    private val activeAccountId: Flow<String?>,
) {
    /**
     * Production: the key is encrypted with a hardware-backed keystore key.
     */
    constructor(context: Context, activeAccountId: Flow<String?>) : this(
        context,
        PerAccountAndroidKeyStoreCipher(activeAccountId),
        activeAccountId,
    )

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        migrateLegacySettings()
        migrateLegacyConsent()
    }

    private fun accountId(): String? = kotlinx.coroutines.runBlocking { activeAccountId.first() }

    private fun perAccountKey(key: String, accountId: String): String = "${accountId}_$key"

    fun provider(): AiProvider? {
        val id = accountId() ?: return null
        return prefs.getString(perAccountKey(KEY_PROVIDER, id), null)
            ?.let { runCatching { AiProvider.valueOf(it) }.getOrNull() }
    }

    fun selectedProvider(): AiProvider = provider() ?: AiProvider.OPENAI

    fun setProvider(p: AiProvider) {
        val id = accountId() ?: return
        prefs.edit().putString(perAccountKey(KEY_PROVIDER, id), p.name).apply()
    }

    fun apiKeyState(provider: AiProvider = selectedProvider()): ApiKeyState {
        val id = accountId() ?: return ApiKeyState.Absent
        val stored = prefs.getString(perAccountKey(keySlot(provider), id), null)
            ?: return ApiKeyState.Absent
        return cipher.decrypt(stored, id)
    }

    fun apiKey(provider: AiProvider = selectedProvider()): String? =
        (apiKeyState(provider) as? ApiKeyState.Readable)?.key

    fun setApiKey(key: String, provider: AiProvider = selectedProvider()): Boolean {
        val id = accountId() ?: return false
        val stored = cipher.encrypt(key, id) ?: return false
        prefs.edit().putString(perAccountKey(keySlot(provider), id), stored).apply()
        return true
    }

    fun clearApiKey(provider: AiProvider = selectedProvider()) {
        val id = accountId() ?: return
        prefs.edit().remove(perAccountKey(keySlot(provider), id)).apply()
    }

    fun hasKey(provider: AiProvider = selectedProvider()): Boolean =
        (apiKeyState(provider) as? ApiKeyState.Readable)?.key?.isNotBlank() == true

    fun otherUsableProvider(exclude: AiProvider): AiProvider? {
        val id = accountId() ?: return null
        return AiProvider.values().firstOrNull { it != exclude && hasKey(it) }
    }

    fun model(provider: AiProvider = selectedProvider()): String? {
        val id = accountId() ?: return null
        return prefs.getString(perAccountKey(modelSlot(provider), id), null)?.takeIf { it.isNotBlank() }
    }

    fun setModel(m: String, provider: AiProvider = selectedProvider()) {
        val id = accountId() ?: return
        prefs.edit().putString(perAccountKey(modelSlot(provider), id), m).apply()
    }

    fun baseUrl(provider: AiProvider = selectedProvider()): String? {
        val id = accountId() ?: return null
        return prefs.getString(perAccountKey(baseUrlSlot(provider), id), null)
    }

    fun setBaseUrl(url: String, provider: AiProvider = selectedProvider()) {
        val id = accountId() ?: return
        prefs.edit().putString(perAccountKey(baseUrlSlot(provider), id), url).apply()
    }

    fun baseUrlOrDefault(provider: AiProvider): String =
        baseUrl(provider)?.takeIf { it.isNotBlank() } ?: provider.defaultBaseUrl

    fun consentGiven(provider: AiProvider = selectedProvider()): Boolean {
        val id = accountId() ?: return false
        return prefs.getBoolean(perAccountKey(consentSlot(provider), id), false)
    }

    fun setConsentGiven(provider: AiProvider = selectedProvider()) {
        val id = accountId() ?: return
        prefs.edit().putBoolean(perAccountKey(consentSlot(provider), id), true).apply()
    }

    fun revokeConsent(provider: AiProvider = selectedProvider()) {
        val id = accountId() ?: return
        prefs.edit().remove(perAccountKey(consentSlot(provider), id)).apply()
    }

    fun systemPrompt(): String {
        val id = accountId() ?: return ""
        return prefs.getString(perAccountKey(KEY_SYSTEM_PROMPT, id), "").orEmpty()
    }

    fun setSystemPrompt(value: String) {
        val id = accountId() ?: return
        prefs.edit().putString(perAccountKey(KEY_SYSTEM_PROMPT, id), value).apply()
    }

    fun summarySystemPrompt(): String {
        val id = accountId() ?: return ""
        return prefs.getString(perAccountKey(KEY_SUMMARY_PROMPT, id), "").orEmpty()
    }

    fun setSummarySystemPrompt(value: String) {
        val id = accountId() ?: return
        prefs.edit().putString(perAccountKey(KEY_SUMMARY_PROMPT, id), value).apply()
    }

    fun planSystemPrompt(): String {
        val id = accountId() ?: return ""
        return prefs.getString(perAccountKey(KEY_PLAN_PROMPT, id), "").orEmpty()
    }

    fun setPlanSystemPrompt(value: String) {
        val id = accountId() ?: return
        prefs.edit().putString(perAccountKey(KEY_PLAN_PROMPT, id), value).apply()
    }

    /**
     * Older builds kept one key, model, and base URL for whichever provider
     * was selected. Move those entries into the **owner account's** slots so
     * an upgrade does not make the saved key disappear. The legacy entries
     * are only removed after their values have been copied; if the process
     * dies first, the migration runs again on the next start.
     *
     * With no active account, nothing to migrate.
     */
    private fun migrateLegacySettings() {
        val id = accountId() ?: return
        val editor = prefs.edit()
        var changed = false

        val legacyApiKey = prefs.getString(KEY_API_LEGACY, null)
        if (legacyApiKey != null) {
            // The legacy `api_key` entry was written already encrypted by
            // older builds, so copy it verbatim into the owner account's slot.
            if (!prefs.contains(perAccountKey(keySlot(AiProvider.OPENAI), id))) {
                editor.putString(perAccountKey(keySlot(AiProvider.OPENAI), id), legacyApiKey)
                changed = true
            }
            editor.remove(KEY_API_LEGACY)
            changed = true
        }

        fun migrate(legacyKey: String, targetKey: String) {
            val value = prefs.getString(legacyKey, null) ?: return
            if (!prefs.contains(perAccountKey(targetKey, id))) {
                editor.putString(perAccountKey(targetKey, id), value)
            }
            editor.remove(legacyKey)
            changed = true
        }

        migrate(KEY_MODEL_LEGACY, modelSlot(AiProvider.OPENAI))
        migrate(KEY_BASE_URL_LEGACY, baseUrlSlot(AiProvider.OPENAI))
        if (changed) editor.apply()
    }

    /**
     * Older builds stored one consent flag for every provider. Move it to
     * the **owner account's** provider and remove the legacy flag: the other
     * providers must ask again, because the caregiver never saw their names
     * or data policies. With no active account, nothing to migrate.
     */
    private fun migrateLegacyConsent() {
        val id = accountId() ?: return
        if (!prefs.contains(KEY_CONSENT)) return
        val editor = prefs.edit()
        if (prefs.getBoolean(KEY_CONSENT, false)) {
            editor.putBoolean(perAccountKey(consentSlot(AiProvider.OPENAI), id), true)
        }
        editor.remove(KEY_CONSENT)
        editor.apply()
    }

    /**
     * Deletes every AI key and setting for [accountId] (#614): all
     * namespaced SharedPreferences entries and the account's keystore alias.
     * Another account's data is untouched — only keys with the
     * `{accountId}_` prefix are removed. Idempotent: deleting an account
     * with no keys succeeds silently. Called by `AccountCleanupImpl`'s
     * `deleteAiKeys` hook.
     */
    fun deleteAllForAccount(accountId: String) {
        val prefix = "${accountId}_"
        val toRemove = prefs.all.keys.filter { it.startsWith(prefix) }
        if (toRemove.isNotEmpty()) {
            prefs.edit().apply {
                toRemove.forEach { remove(it) }
            }.apply()
        }
        cipher.delete(accountId)
    }

    private fun keySlot(provider: AiProvider): String = KEY_API_PREFIX + provider.name

    private fun consentSlot(provider: AiProvider): String = KEY_CONSENT_PREFIX + provider.name

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
        /** Legacy global consent flag, migrated to [KEY_CONSENT_PREFIX]. */
        private const val KEY_CONSENT = "consent"
        private const val KEY_CONSENT_PREFIX = "consent_"
        private const val KEY_SYSTEM_PROMPT = "system_prompt"
        private const val KEY_SUMMARY_PROMPT = "summary_prompt"
        private const val KEY_PLAN_PROMPT = "plan_prompt"
    }
}
