package com.safa.account.data.api

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Read-only compatibility adapter for the pre-v3 credential store.
 *
 * New credentials are never written here. This adapter exists only so an
 * installed user can move one complete legacy generation into TokenVault before
 * the old preference files are deleted.
 */
@Suppress("DEPRECATION")
internal object LegacySecurePreferences {
    const val ENCRYPTED_PREFS_NAME = "safa_secure_prefs_v2"
    const val PLAIN_PREFS_NAME = "safa_secure_prefs"

    fun encrypted(context: Context): SharedPreferences = if (Build.FINGERPRINT == "robolectric") {
        context.getSharedPreferences(ENCRYPTED_PREFS_NAME, Context.MODE_PRIVATE)
    } else {
        EncryptedSharedPreferences.create(
            context,
            ENCRYPTED_PREFS_NAME,
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun plain(context: Context): SharedPreferences =
        context.getSharedPreferences(PLAIN_PREFS_NAME, Context.MODE_PRIVATE)

    fun delete(context: Context) {
        context.deleteSharedPreferences(ENCRYPTED_PREFS_NAME)
        context.deleteSharedPreferences(PLAIN_PREFS_NAME)
    }
}
