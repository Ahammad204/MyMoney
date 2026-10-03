package com.example.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Securely stores the Gemini API key encrypted using the Android Keystore.
 * The key is encrypted using AES-GCM-NoPadding with a hardware-backed or Keystore-managed master key.
 * Ciphertext and IV are persisted to a private SharedPreferences file ('secure_ai_prefs.xml')
 * which is explicitly excluded from Android Auto-Backup and device transfers.
 */
object SecureApiKeyStorage {
    private const val TAG = "SecureApiKeyStorage"
    private const val ANDROID_KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val KEY_ALIAS = "MyMoney_Gemini_Key_Alias"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH_BITS = 128

    private const val PREFS_FILE = "secure_ai_prefs"
    private const val PREF_ENCRYPTED_API_KEY = "encrypted_gemini_api_key"
    private const val PREF_IV = "gemini_api_key_iv"

    @Synchronized
    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE_PROVIDER).apply { load(null) }
        if (keyStore.containsAlias(KEY_ALIAS)) {
            val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
            if (entry != null) {
                return entry.secretKey
            }
        }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE_PROVIDER
        )
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()

        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    /**
     * Encrypts and saves the Gemini API key.
     */
    @Synchronized
    fun saveApiKey(context: Context, apiKey: String): Boolean {
        val trimmed = apiKey.trim()
        if (trimmed.isEmpty()) {
            clearApiKey(context)
            return true
        }

        return try {
            val secretKey = getOrCreateSecretKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)

            val iv = cipher.iv
            val encryptedBytes = cipher.doFinal(trimmed.toByteArray(Charsets.UTF_8))

            val ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP)
            val encryptedBase64 = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)

            val prefs = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
            prefs.edit()
                .putString(PREF_IV, ivBase64)
                .putString(PREF_ENCRYPTED_API_KEY, encryptedBase64)
                .commit()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to encrypt and save API key", e)
            false
        }
    }

    /**
     * Reads and decrypts the Gemini API key. Returns null if not configured or decryption fails.
     */
    @Synchronized
    fun getApiKey(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
        val ivBase64 = prefs.getString(PREF_IV, null) ?: return null
        val encryptedBase64 = prefs.getString(PREF_ENCRYPTED_API_KEY, null) ?: return null

        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE_PROVIDER).apply { load(null) }
            val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
                ?: return null

            val iv = Base64.decode(ivBase64, Base64.NO_WRAP)
            val encryptedBytes = Base64.decode(encryptedBase64, Base64.NO_WRAP)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
            cipher.init(Cipher.DECRYPT_MODE, entry.secretKey, spec)

            val decryptedBytes = cipher.doFinal(encryptedBytes)
            val key = String(decryptedBytes, Charsets.UTF_8).trim()
            if (key.isBlank()) null else key
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decrypt API key", e)
            null
        }
    }

    /**
     * Checks if a valid API key is currently saved.
     */
    @Synchronized
    fun hasApiKey(context: Context): Boolean {
        return !getApiKey(context).isNullOrBlank()
    }

    /**
     * Removes the API key and clears its encrypted state.
     */
    @Synchronized
    fun clearApiKey(context: Context): Boolean {
        return try {
            val prefs = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
            prefs.edit()
                .remove(PREF_IV)
                .remove(PREF_ENCRYPTED_API_KEY)
                .commit()

            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE_PROVIDER).apply { load(null) }
            if (keyStore.containsAlias(KEY_ALIAS)) {
                keyStore.deleteEntry(KEY_ALIAS)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear API key", e)
            false
        }
    }
}
