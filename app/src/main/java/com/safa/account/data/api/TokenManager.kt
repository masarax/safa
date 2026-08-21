package com.safa.account.data.api

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Base64
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.safa.account.data.network.DeviceSecurityHelper
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.json.JSONObject

class TokenManager(private val context: Context) {
    private val legacyPrefs = context.getSharedPreferences("safa_secure_prefs", Context.MODE_PRIVATE)
    private val prefs: SharedPreferences = if (Build.FINGERPRINT == "robolectric") {
        context.getSharedPreferences("safa_secure_prefs_v2", Context.MODE_PRIVATE)
    } else {
        EncryptedSharedPreferences.create(
            context,
            "safa_secure_prefs_v2",
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private val _sessionInvalidated = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val sessionInvalidated = _sessionInvalidated.asSharedFlow()

    @Volatile
    private var biometricUnlockApproved = false

    @Volatile
    private var logoutInProgress = false

    init { migrateLegacyPreferences() }

    companion object {
        private const val KEY_ACCESS_TOKEN = "auth_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_DEVICE_TOKEN = "device_token"
        private const val KEY_SESSION_TOKEN = "session_token"
        private const val KEY_FINGERPRINT_TOKEN = "fingerprint_token"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_LAST_MOBILE = "last_mobile"
        private const val KEY_ACTIVE_ACCOUNT_ID = "active_account_id"
        private const val KEY_BIOMETRIC_ENABLED = "biometric_quick_unlock_enabled"
        private const val KEY_BIOMETRIC_USER_ID = "biometric_quick_unlock_user_id"
        private const val KEY_BIOMETRIC_MOBILE = "biometric_quick_unlock_mobile"
        private const val KEY_MIGRATION_COMPLETE = "secure_prefs_migration_complete"
        private const val DEFAULT_URL = "https://safa.masarax.com/api/"
    }

    private fun migrateLegacyPreferences() {
        if (prefs.getBoolean(KEY_MIGRATION_COMPLETE, false)) return
        if (legacyPrefs.all.isNotEmpty()) {
            prefs.edit {
                legacyPrefs.all.forEach { (key, value) ->
                    when (value) {
                        is String -> putString(key, value)
                        is Boolean -> putBoolean(key, value)
                        is Int -> putInt(key, value)
                        is Long -> putLong(key, value)
                        is Float -> putFloat(key, value)
                    }
                }
                putBoolean(KEY_MIGRATION_COMPLETE, true)
            }
            legacyPrefs.edit { clear() }
        } else {
            prefs.edit { putBoolean(KEY_MIGRATION_COMPLETE, true) }
        }
    }

    fun getContext(): Context = context
    fun saveLastMobile(mobile: String) = prefs.edit { putString(KEY_LAST_MOBILE, mobile) }
    fun getLastMobile(): String = prefs.getString(KEY_LAST_MOBILE, "") ?: ""

    fun saveApiKey(key: String) = prefs.edit { putString(KEY_API_KEY, key) }
    fun getApiKey(): String = prefs.getString(KEY_API_KEY, null)?.takeIf { it.isNotBlank() } ?: ""

    fun saveApiSecret(@Suppress("UNUSED_PARAMETER") secret: String) = Unit
    fun getApiSecret(): String = ""

    fun saveAccessToken(token: String?) = prefs.edit { putString(KEY_ACCESS_TOKEN, token) }
    fun getAccessToken(): String? = prefs.getString(KEY_ACCESS_TOKEN, null)
    fun saveToken(token: String) = saveAccessToken(token)
    fun getToken(): String? = getAccessToken()
    fun clearToken() = clearAllTokens()
    fun saveRefreshToken(token: String?) = prefs.edit { putString(KEY_REFRESH_TOKEN, token) }
    fun getRefreshToken(): String? = prefs.getString(KEY_REFRESH_TOKEN, null)

    fun saveDeviceToken(token: String?) = prefs.edit { putString(KEY_DEVICE_TOKEN, token) }
    fun getDeviceToken(): String {
        var token = prefs.getString(KEY_DEVICE_TOKEN, null)
        if (token.isNullOrBlank()) {
            token = DeviceSecurityHelper.getOrCreateDeviceUuid(context)
            saveDeviceToken(token)
        }
        return token
    }

    fun saveSessionToken(token: String?) = prefs.edit { putString(KEY_SESSION_TOKEN, token) }
    fun getSessionToken(): String? = prefs.getString(KEY_SESSION_TOKEN, null)
    fun saveFingerprintToken(token: String?) = prefs.edit { putString(KEY_FINGERPRINT_TOKEN, token) }
    fun getFingerprintToken(): String {
        var token = prefs.getString(KEY_FINGERPRINT_TOKEN, null)
        if (token.isNullOrBlank()) {
            token = DeviceSecurityHelper.getHardwareFingerprintHash(context)
            saveFingerprintToken(token)
        }
        return token
    }

    private fun accountIdFromAccessToken(token: String?): Int? = runCatching {
        val payload = token?.split('.')?.getOrNull(1)?.takeIf { it.isNotBlank() }
            ?: return@runCatching null
        val decoded = Base64.decode(payload, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        JSONObject(String(decoded, Charsets.UTF_8)).optInt("account_id", 0).takeIf { it > 0 }
    }.getOrNull()

    fun saveAllTokens(accessToken: String?, refreshToken: String?, deviceToken: String?, sessionToken: String?, fingerprintToken: String?) {
        val automaticAccountId = accountIdFromAccessToken(accessToken)
        prefs.edit {
            putString(KEY_ACCESS_TOKEN, accessToken)
            putString(KEY_REFRESH_TOKEN, refreshToken)
            if (!deviceToken.isNullOrBlank()) putString(KEY_DEVICE_TOKEN, deviceToken)
            putString(KEY_SESSION_TOKEN, sessionToken)
            if (!fingerprintToken.isNullOrBlank()) putString(KEY_FINGERPRINT_TOKEN, fingerprintToken)
            if (automaticAccountId != null) putInt(KEY_ACTIVE_ACCOUNT_ID, automaticAccountId)
        }
    }

    fun notifySessionInvalidated() {
        clearAllTokens()
        _sessionInvalidated.tryEmit(Unit)
    }

    fun beginLogout() {
        logoutInProgress = true
        revokeBiometricUnlockApproval()
    }

    fun finishLogout() { logoutInProgress = false }
    fun isLogoutInProgress(): Boolean = logoutInProgress

    fun clearAllTokens() = prefs.edit {
        remove(KEY_ACCESS_TOKEN)
        remove(KEY_REFRESH_TOKEN)
        remove(KEY_SESSION_TOKEN)
        remove(KEY_ACTIVE_ACCOUNT_ID)
        remove(KEY_BIOMETRIC_ENABLED)
        remove(KEY_BIOMETRIC_USER_ID)
        remove(KEY_BIOMETRIC_MOBILE)
        biometricUnlockApproved = false
    }

    fun enableBiometricQuickUnlock(userId: Int, mobile: String) = prefs.edit {
        putBoolean(KEY_BIOMETRIC_ENABLED, true)
        putInt(KEY_BIOMETRIC_USER_ID, userId)
        putString(KEY_BIOMETRIC_MOBILE, mobile.trim())
        biometricUnlockApproved = false
    }

    fun disableBiometricQuickUnlock() = prefs.edit {
        remove(KEY_BIOMETRIC_ENABLED)
        remove(KEY_BIOMETRIC_USER_ID)
        remove(KEY_BIOMETRIC_MOBILE)
        biometricUnlockApproved = false
    }

    fun approveBiometricUnlock() { biometricUnlockApproved = true }
    fun revokeBiometricUnlockApproval() { biometricUnlockApproved = false }
    fun isBiometricUnlockApproved(): Boolean = biometricUnlockApproved

    fun isBiometricQuickUnlockEnabled(): Boolean = prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false)
    fun getBiometricQuickUnlockUserId(): Int? = prefs.getInt(KEY_BIOMETRIC_USER_ID, 0).takeIf { it > 0 }
    fun getBiometricQuickUnlockMobile(): String = prefs.getString(KEY_BIOMETRIC_MOBILE, "") ?: ""

    fun isBiometricQuickUnlockBoundTo(userId: Int, mobile: String): Boolean =
        isBiometricQuickUnlockEnabled() &&
            getBiometricQuickUnlockUserId() == userId &&
            getBiometricQuickUnlockMobile().trim() == mobile.trim()

    fun hasValidLocalSessionForQuickUnlock(): Boolean =
        isBiometricQuickUnlockEnabled() &&
            !getAccessToken().isNullOrBlank() &&
            !getRefreshToken().isNullOrBlank() &&
            !getSessionToken().isNullOrBlank() &&
            getBiometricQuickUnlockUserId() != null &&
            getBiometricQuickUnlockMobile().isNotBlank()

    fun saveActiveAccountId(accountId: Int?) = prefs.edit {
        if (accountId == null || accountId <= 0) remove(KEY_ACTIVE_ACCOUNT_ID) else putInt(KEY_ACTIVE_ACCOUNT_ID, accountId)
    }
    fun getActiveAccountId(): Int? = prefs.getInt(KEY_ACTIVE_ACCOUNT_ID, 0).takeIf { it > 0 }
    fun saveBaseUrl(url: String) = prefs.edit { putString(KEY_BASE_URL, url.trim().removeSuffix("/") + "/") }
    fun getBaseUrl(): String {
        val stored = prefs.getString(KEY_BASE_URL, null)?.trim().orEmpty()
        if (stored.isBlank()) return DEFAULT_URL
        val normalized = if (stored.endsWith("/")) stored else "$stored/"
        return if (normalized.startsWith("https://safa.masarax.com/api/", ignoreCase = true)) normalized else DEFAULT_URL
    }
    fun saveLanguage(lang: String) = prefs.edit { putString("app_lang", lang) }
    fun getLanguage(): String = prefs.getString("app_lang", "BN") ?: "BN"
    fun saveDarkMode(isDark: Boolean) = prefs.edit { putBoolean("app_dark_mode", isDark) }
    fun getDarkMode(): Boolean = prefs.getBoolean("app_dark_mode", false)
    fun saveThemeMode(mode: String) = prefs.edit { putString("app_theme_mode", mode) }
    fun getThemeMode(): String = prefs.getString("app_theme_mode", "LIGHT") ?: "LIGHT"
    fun saveCustomAppName(name: String) = prefs.edit { putString("app_name", name) }
    fun getCustomAppName(): String = prefs.getString("app_name", "SAFA") ?: "SAFA"
    fun saveCustomAppLogo(logo: String) = prefs.edit { putString("app_logo", logo) }
    fun getCustomAppLogo(): String = prefs.getString("app_logo", "SAFA") ?: "SAFA"
    fun getCustomAppLogoUri(): String? = prefs.getString("app_logo_uri", null)
    fun saveCustomAppLogoUri(uri: String?) = prefs.edit { putString("app_logo_uri", uri) }
    fun saveServerLogoUrl(url: String?) = saveCustomAppLogoUri(url)
    fun getServerLogoUrl(): String? = getCustomAppLogoUri()
    fun saveAppVersion(version: String) = prefs.edit { putString("app_version", version) }
    fun getAppVersion(): String = prefs.getString("app_version", "1.0") ?: "1.0"
    fun saveLocalCurrency(curr: String) = prefs.edit { putString("local_curr", curr) }
    fun getLocalCurrency(): String = prefs.getString("local_curr", "BDT") ?: "BDT"
    fun saveForeignCurrency(curr: String) = prefs.edit { putString("foreign_curr", curr) }
    fun getForeignCurrency(): String = prefs.getString("foreign_curr", "SAR") ?: "SAR"
    fun saveRateFeatureEnabled(enabled: Boolean) = prefs.edit { putBoolean("rate_feature", enabled) }
    fun getRateFeatureEnabled(): Boolean = prefs.getBoolean("rate_feature", true)
    fun saveSupplierRateEnabled(enabled: Boolean) = prefs.edit { putBoolean("supplier_rate_enabled", enabled) }
    fun getSupplierRateEnabled(): Boolean = prefs.getBoolean("supplier_rate_enabled", true)
    fun saveWalletRateEnabled(enabled: Boolean) = prefs.edit { putBoolean("wallet_rate_enabled", enabled) }
    fun getWalletRateEnabled(): Boolean = prefs.getBoolean("wallet_rate_enabled", true)
}