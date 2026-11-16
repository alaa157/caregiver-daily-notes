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
 *
 * The keystore alias is namespaced per account id, so each account has its
 * own encrypted slot (#613).
 */
internal class AndroidKeyStoreCipher : ApiKeyCipher {

    private fun alias(accountId: String) = "${ALIAS_PREFIX}_$accountId"

    override fun encrypt(plain: String, accountId: String): String? = runCatching {
        encryptWithKey(plain, accountId)
    }.recoverCatching {
        // An invalidated alias cannot encrypt new credentials. Existing data
        // encrypted by that key is already unreadable; replace the alias so
        // the user can save a newly entered key.
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        keyStore.deleteEntry(alias(accountId))
        encryptWithKey(plain, accountId)
    }.getOrNull()

    private fun encryptWithKey(plain: String, accountId: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey(accountId))
        val combined = cipher.iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    override fun decrypt(stored: String, accountId: String): ApiKeyState {
        val decoded = runCatching { Base64.decode(stored, Base64.NO_WRAP) }.getOrNull()
            ?: return ApiKeyState.Unavailable
        if (decoded.size <= IV_SIZE) return ApiKeyState.Invalidated

        val keyStore = try {
            KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        } catch (e: Exception) {
            return ApiKeyState.Unavailable
        }
        val key = try {
            keyStore.getKey(alias(accountId), null) as? SecretKey
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

    /** Deletes the alias for [accountId]; a missing alias is a no-op (#614). */
    override fun delete(accountId: String) {
        runCatching {
            KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
                .deleteEntry(alias(accountId))
        }
    }

    private fun getOrCreateKey(accountId: String): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        ks.getKey(alias(accountId), null)?.let { return it as SecretKey }
        val spec = KeyGenParameterSpec.Builder(
            alias(accountId),
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
        private const val ALIAS_PREFIX = "caregiver_ai_key_v1"
        private const val IV_SIZE = 12
        private const val TAG_SIZE_BITS = 128
    }
}
