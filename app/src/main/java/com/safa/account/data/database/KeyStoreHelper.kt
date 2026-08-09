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
    private const val KEY_PASSPHRASE = "safa_db_passphrase"

    fun getOrGenerateDbPassphrase(context: Context): ByteArray {
        return try {
            getOrGenerateDbPassphraseInternal(context)
        } catch (t: Throwable) {
            android.util.Log.w("SafaKeyStore", "MasterKey initialization error, resetting KeyStore alias for Android 16 compatibility: ${t.message}")
            try {
                val keyStore = java.security.KeyStore.getInstance("AndroidKeyStore")
                keyStore.load(null)
                keyStore.deleteEntry(MasterKey.DEFAULT_MASTER_KEY_ALIAS)
            } catch (ignored: Throwable) {}
            getOrGenerateDbPassphraseInternal(context)
        }
    }

    private fun getOrGenerateDbPassphraseInternal(context: Context): ByteArray {
        val masterKey = MasterKey.Builder(context, MasterKey.DEFAULT_MASTER_KEY_ALIAS)
            .setKeyGenParameterSpec(
                KeyGenParameterSpec.Builder(
                    MasterKey.DEFAULT_MASTER_KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
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
        return android.util.Base64.decode(passphraseBase64, android.util.Base64.NO_WRAP)
    }
}
