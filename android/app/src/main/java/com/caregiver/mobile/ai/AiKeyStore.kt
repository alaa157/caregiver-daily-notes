package com.caregiver.mobile.ai

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class AiKeyStore(private val context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun provider(): AiProvider? =
        prefs.getString(KEY_PROVIDER, null)?.let { runCatching { AiProvider.valueOf(it) }.getOrNull() }

    fun setProvider(p: AiProvider) {
        prefs.edit().putString(KEY_PROVIDER, p.name).apply()
    }

    fun apiKey(): String? = prefs.getString(KEY_API, null)?.let { decryptOrNull(it) }

    fun setApiKey(key: String) {
        prefs.edit().putString(KEY_API, encrypt(key)).apply()
    }

    fun clearApiKey() {
        prefs.edit().remove(KEY_API).apply()
    }

    fun hasKey(): Boolean = !apiKey().isNullOrBlank()

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

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val combined = iv + ct
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    private fun decryptOrNull(encoded: String): String? = runCatching {
        val combined = Base64.decode(encoded, Base64.NO_WRAP)
        val iv = combined.copyOfRange(0, IV_SIZE)
        val ct = combined.copyOfRange(IV_SIZE, combined.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_SIZE_BITS, iv))
        String(cipher.doFinal(ct), Charsets.UTF_8)
    }.getOrNull()

    private fun getOrCreateKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        ks.getKey(ALIAS, null)?.let { return it as SecretKey }
        val spec = KeyGenParameterSpec.Builder(
            ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(spec)
        return gen.generateKey()
    }

    companion object {
        private const val PREFS_NAME = "ai_settings"
        private const val ALIAS = "caregiver_ai_key_v1"
        private const val KEY_PROVIDER = "provider"
        private const val KEY_API = "api_key"
        private const val KEY_MODEL = "model"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_CONSENT = "consent"
        private const val KEY_SYSTEM_PROMPT = "system_prompt"
        private const val KEY_SUMMARY_PROMPT = "summary_prompt"
        private const val KEY_PLAN_PROMPT = "plan_prompt"
        private const val IV_SIZE = 12
        private const val TAG_SIZE_BITS = 128
    }
}
