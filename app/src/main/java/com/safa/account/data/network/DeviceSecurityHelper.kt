package com.safa.account.data.network

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest
import java.util.UUID

/**
 * Device identity helpers used by the mobile authentication layer.
 *
 * Important: a device fingerprint must be stable for the lifetime of an app
 * installation. Android OS updates, vendor changes, and Build.FINGERPRINT are
 * not authentication events and therefore must not silently invalidate a
 * previously bound device.
 */
object DeviceSecurityHelper {
    private const val PREF_FILE = "safa_device_sec_prefs"
    private const val KEY_DEVICE_UUID = "safa_device_uuid"
    private const val KEY_FINGERPRINT_SEED = "safa_fingerprint_seed"

    /** Generates or retrieves a persistent device UUID. */
    fun getOrCreateDeviceUuid(context: Context): String {
        return try {
            val prefs = encryptedPrefs(context)
            var uuid = prefs.getString(KEY_DEVICE_UUID, null)
            if (uuid.isNullOrBlank()) {
                uuid = UUID.randomUUID().toString()
                prefs.edit().putString(KEY_DEVICE_UUID, uuid).apply()
            }
            uuid
        } catch (_: Exception) {
            val fallbackPrefs = context.getSharedPreferences("safa_device_fallback_prefs", Context.MODE_PRIVATE)
            var uuid = fallbackPrefs.getString(KEY_DEVICE_UUID, null)
            if (uuid.isNullOrBlank()) {
                uuid = UUID.randomUUID().toString()
                fallbackPrefs.edit().putString(KEY_DEVICE_UUID, uuid).apply()
            }
            uuid
        }
    }

    /**
     * Returns a stable installation-bound fingerprint.
     *
     * Do not include Build.FINGERPRINT, MODEL, OS version, or other mutable
     * hardware metadata here: those values can change after an Android update
     * and would incorrectly look like a device-security change to the server.
     */
    fun getHardwareFingerprintHash(context: Context): String {
        val seed = getOrCreateFingerprintSeed(context)
        return sha256("SAFA|fingerprint|${context.packageName}|$seed")
    }

    private fun encryptedPrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context, MasterKey.DEFAULT_MASTER_KEY_ALIAS)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            PREF_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private fun getOrCreateFingerprintSeed(context: Context): String {
        return try {
            val prefs = encryptedPrefs(context)
            var seed = prefs.getString(KEY_FINGERPRINT_SEED, null)
            if (seed.isNullOrBlank()) {
                seed = UUID.randomUUID().toString()
                prefs.edit().putString(KEY_FINGERPRINT_SEED, seed).apply()
            }
            seed
        } catch (_: Exception) {
            val prefs = context.getSharedPreferences("safa_device_fallback_prefs", Context.MODE_PRIVATE)
            var seed = prefs.getString(KEY_FINGERPRINT_SEED, null)
            if (seed.isNullOrBlank()) {
                seed = UUID.randomUUID().toString()
                prefs.edit().putString(KEY_FINGERPRINT_SEED, seed).apply()
            }
            seed
        }
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
