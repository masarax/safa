package com.safa.account.data.database

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom

object KeyStoreHelper {
    private const val PREF_FILE = "safa_keystore_prefs"
    private const val FALLBACK_PREF_FILE = "safa_secure_passphrase_store"
    private const val KEY_PASSPHRASE = "safa_db_passphrase"

    fun getOrGenerateDbPassphrase(context: Context): ByteArray {
        // Try hardware-backed KeyStore MasterKey & EncryptedSharedPreferences first
        try {
            val spec = KeyGenParameterSpec.Builder(
                MasterKey.DEFAULT_MASTER_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()

            val masterKey = MasterKey.Builder(context, MasterKey.DEFAULT_MASTER_KEY_ALIAS)
                .setKeyGenParameterSpec(spec)
                .build()

            val prefs: SharedPreferences = EncryptedSharedPreferences.create(
                context,
                PREF_FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )

            var passphraseBase64 = prefs.getString(KEY_PASSPHRASE, null)
            if (passphraseBase64 == null) {
                // Check if passphrase was previously stored in secure fallback
                val fallbackPrefs = context.getSharedPreferences(FALLBACK_PREF_FILE, Context.MODE_PRIVATE)
                passphraseBase64 = fallbackPrefs.getString(KEY_PASSPHRASE, null)
                
                if (passphraseBase64 == null) {
                    val randomBytes = ByteArray(32)
                    SecureRandom().nextBytes(randomBytes)
                    passphraseBase64 = android.util.Base64.encodeToString(randomBytes, android.util.Base64.NO_WRAP)
                    fallbackPrefs.edit().putString(KEY_PASSPHRASE, passphraseBase64).apply()
                }
                prefs.edit().putString(KEY_PASSPHRASE, passphraseBase64).apply()
            }
            return android.util.Base64.decode(passphraseBase64, android.util.Base64.NO_WRAP)
        } catch (e: Exception) {
            android.util.Log.w("SafaKeyStore", "Hardware KeyStore access failed, reading from secure passphrase store: ${e.message}")
            // Fallback storage preserving existing passphrase without destroying database keys
            val fallbackPrefs = context.getSharedPreferences(FALLBACK_PREF_FILE, Context.MODE_PRIVATE)
            var fallbackBase64 = fallbackPrefs.getString(KEY_PASSPHRASE, null)
            if (fallbackBase64 == null) {
                val randomBytes = ByteArray(32)
                SecureRandom().nextBytes(randomBytes)
                fallbackBase64 = android.util.Base64.encodeToString(randomBytes, android.util.Base64.NO_WRAP)
                fallbackPrefs.edit().putString(KEY_PASSPHRASE, fallbackBase64).apply()
            }
            return android.util.Base64.decode(fallbackBase64, android.util.Base64.NO_WRAP)
        }
    }
}
