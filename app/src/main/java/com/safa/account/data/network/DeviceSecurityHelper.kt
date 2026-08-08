package com.safa.account.data.network

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest
import java.util.UUID

object DeviceSecurityHelper {
    private const val PREF_FILE = "safa_device_sec_prefs"
    private const val KEY_DEVICE_UUID = "safa_device_uuid"

    /**
     * Generates or retrieves a persistent, hardware-backed Device UUID using Android KeyStore.
     */
    fun getOrCreateDeviceUuid(context: Context): String {
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

            var uuid = prefs.getString(KEY_DEVICE_UUID, null)
            if (uuid.isNullOrBlank()) {
                uuid = UUID.randomUUID().toString()
                prefs.edit().putString(KEY_DEVICE_UUID, uuid).apply()
            }
            uuid
        } catch (e: Exception) {
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
     * Computes a SHA-256 Hardware Fingerprint Hash combining:
     * - KeyStore Device UUID
     * - Android Build Serial / Hardware Fingerprint
     * - App Signing Signature
     */
    fun getHardwareFingerprintHash(context: Context): String {
        val deviceUuid = getOrCreateDeviceUuid(context)
        val buildInfo = getBuildInfo()
        val appSignature = getAppSignature(context)

        val rawFingerprint = "$deviceUuid|$buildInfo|$appSignature"
        return sha256(rawFingerprint)
    }

    private fun getBuildInfo(): String {
        val serial = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    Build.getSerial()
                } catch (e: SecurityException) {
                    Build.UNKNOWN
                }
            } else {
                @Suppress("DEPRECATION")
                Build.SERIAL
            }
        } catch (e: Exception) {
            Build.UNKNOWN
        }

        val serialInfo = if (serial != Build.UNKNOWN) serial else ""

        return listOf(
            serialInfo,
            Build.FINGERPRINT,
            Build.MODEL,
            Build.MANUFACTURER,
            Build.HARDWARE,
            Build.BOARD,
            Build.DEVICE,
            Build.PRODUCT
        ).joinToString(":")
    }

    @Suppress("DEPRECATION")
    private fun getAppSignature(context: Context): String {
        return try {
            val packageName = context.packageName
            val pm = context.packageManager
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val packageInfo = pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                val signingInfo = packageInfo.signingInfo
                if (signingInfo != null) {
                    if (signingInfo.hasMultipleSigners()) {
                        signingInfo.apkContentsSigners
                    } else {
                        signingInfo.signingCertificateHistory
                    }
                } else null
            } else {
                val packageInfo = pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
                packageInfo.signatures
            }

            signatures?.joinToString(",") { it.toCharsString() } ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }
}
