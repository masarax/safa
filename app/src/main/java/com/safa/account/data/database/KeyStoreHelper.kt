package com.safa.account.data.database

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom
import java.util.Base64

object KeyStoreHelper {
    private const val PREF_FILE = "safa_keystore_prefs"
    private const val KEY_PASSPHRASE = "safa_db_passphrase"

    fun getOrGenerateDbPassphrase(context: Context): ByteArray {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
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
                val randomBytes = ByteArray(32)
                SecureRandom().nextBytes(randomBytes)
                passphraseBase64 = android.util.Base64.encodeToString(randomBytes, android.util.Base64.NO_WRAP)
                prefs.edit().putString(KEY_PASSPHRASE, passphraseBase64).apply()
            }
            android.util.Base64.decode(passphraseBase64, android.util.Base64.NO_WRAP)
        } catch (e: Exception) {
            // Fallback for devices without Hardware KeyStore support or on Exception
            val fallbackPrefs = context.getSharedPreferences("safa_fallback_prefs", Context.MODE_PRIVATE)
            var fallbackBase64 = fallbackPrefs.getString(KEY_PASSPHRASE, null)
            if (fallbackBase64 == null) {
                val randomBytes = ByteArray(32)
                SecureRandom().nextBytes(randomBytes)
                fallbackBase64 = android.util.Base64.encodeToString(randomBytes, android.util.Base64.NO_WRAP)
                fallbackPrefs.edit().putString(KEY_PASSPHRASE, fallbackBase64).apply()
            }
            android.util.Base64.decode(fallbackBase64, android.util.Base64.NO_WRAP)
        }
    }
}
