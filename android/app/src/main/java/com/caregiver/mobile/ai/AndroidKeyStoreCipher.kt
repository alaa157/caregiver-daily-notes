package com.caregiver.mobile.ai

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Hardware-backed storage for the API key: AES-GCM with a key held in the
 * AndroidKeyStore, so the key bytes never leave the secure hardware.
 *
 * Every failure is reported, never thrown: a keystore that refuses to work
 * must not take the screen down with it.
 */
internal class AndroidKeyStoreCipher : ApiKeyCipher {

    override fun encrypt(plain: String): String? = runCatching {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val combined = cipher.iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        Base64.encodeToString(combined, Base64.NO_WRAP)
    }.getOrNull()

    override fun decrypt(stored: String): ApiKeyState {
        val decoded = runCatching { Base64.decode(stored, Base64.NO_WRAP) }.getOrNull()
            ?: return ApiKeyState.Unavailable
        if (decoded.size <= IV_SIZE) return ApiKeyState.Invalidated

        val keyStore = try {
            KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        } catch (e: Exception) {
            return ApiKeyState.Unavailable
        }
        val key = try {
            keyStore.getKey(ALIAS, null) as? SecretKey
        } catch (e: Exception) {
            return ApiKeyState.Unavailable
        } ?: return ApiKeyState.Invalidated

        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                key,
                GCMParameterSpec(TAG_SIZE_BITS, decoded.copyOfRange(0, IV_SIZE)),
            )
            ApiKeyState.Readable(
                String(cipher.doFinal(decoded.copyOfRange(IV_SIZE, decoded.size)), Charsets.UTF_8),
            )
        } catch (e: KeyPermanentlyInvalidatedException) {
            // The lock screen changed and invalidated the key for good.
            ApiKeyState.Invalidated
        } catch (e: GeneralSecurityException) {
            // The stored bytes no longer match the key: it was replaced or
            // wiped. Either way the user has to enter the key again.
            ApiKeyState.Invalidated
        } catch (e: Exception) {
            ApiKeyState.Unavailable
        }
    }

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
        private const val ALIAS = "caregiver_ai_key_v1"
        private const val IV_SIZE = 12
        private const val TAG_SIZE_BITS = 128
    }
}
