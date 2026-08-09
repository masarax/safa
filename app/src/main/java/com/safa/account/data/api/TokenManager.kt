package com.safa.account.data.api

import android.content.Context
import androidx.core.content.edit
import com.safa.account.data.network.DeviceSecurityHelper

class TokenManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("safa_secure_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_ACCESS_TOKEN = "auth_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_DEVICE_TOKEN = "device_token"
        private const val KEY_SESSION_TOKEN = "session_token"
        private const val KEY_FINGERPRINT_TOKEN = "fingerprint_token"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_API_SECRET = "api_secret"
        private const val KEY_LAST_MOBILE = "last_mobile"
        private const val DEFAULT_URL = "https://safa.masarax.com/api/"

        private val DEFAULT_API_KEY: String = ""
        private val DEFAULT_API_SECRET: String = ""
    }

    fun saveLastMobile(mobile: String) = prefs.edit { putString(KEY_LAST_MOBILE, mobile) }
    fun getLastMobile(): String = prefs.getString(KEY_LAST_MOBILE, "") ?: ""

    fun saveApiKey(key: String) = prefs.edit { putString(KEY_API_KEY, key) }
    fun getApiKey(): String = prefs.getString(KEY_API_KEY, DEFAULT_API_KEY) ?: DEFAULT_API_KEY

    fun saveApiSecret(secret: String) = prefs.edit { putString(KEY_API_SECRET, secret) }
    fun getApiSecret(): String = prefs.getString(KEY_API_SECRET, DEFAULT_API_SECRET) ?: DEFAULT_API_SECRET

    // --- 5-Token Security Layer ---
    fun saveAccessToken(token: String?) = prefs.edit { putString(KEY_ACCESS_TOKEN, token) }
    fun getAccessToken(): String? = prefs.getString(KEY_ACCESS_TOKEN, null)

    // Legacy / Convenience Compatibility
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

    fun saveAllTokens(
        accessToken: String?,
        refreshToken: String?,
        deviceToken: String?,
        sessionToken: String?,
        fingerprintToken: String?
    ) {
        prefs.edit {
            putString(KEY_ACCESS_TOKEN, accessToken)
            putString(KEY_REFRESH_TOKEN, refreshToken)
            putString(KEY_DEVICE_TOKEN, deviceToken)
            putString(KEY_SESSION_TOKEN, sessionToken)
            putString(KEY_FINGERPRINT_TOKEN, fingerprintToken)
        }
    }

    fun clearAllTokens() = prefs.edit {
        remove(KEY_ACCESS_TOKEN)
        remove(KEY_REFRESH_TOKEN)
        remove(KEY_DEVICE_TOKEN)
        remove(KEY_SESSION_TOKEN)
        remove(KEY_FINGERPRINT_TOKEN)
    }

    fun saveBaseUrl(url: String) = prefs.edit { putString(KEY_BASE_URL, url) }

    fun getBaseUrl(): String = prefs.getString(KEY_BASE_URL, DEFAULT_URL) ?: DEFAULT_URL
    
    // --- App Settings & Theme Persistence ---
    fun saveLanguage(lang: String) = prefs.edit { putString("app_lang", lang) }
    fun getLanguage(): String = prefs.getString("app_lang", "BN") ?: "BN"

    fun saveDarkMode(isDark: Boolean) = prefs.edit { putBoolean("app_dark_mode", isDark) }
    fun getDarkMode(): Boolean = prefs.getBoolean("app_dark_mode", false)

    fun saveThemeMode(mode: String) = prefs.edit { putString("app_theme_mode", mode) } // "LIGHT" | "DARK" | "SYSTEM"
    fun getThemeMode(): String = prefs.getString("app_theme_mode", "LIGHT") ?: "LIGHT"
    
    fun saveCustomAppName(name: String) = prefs.edit { putString("app_name", name) }
    fun getCustomAppName(): String = prefs.getString("app_name", "SAFA") ?: "SAFA"
    
    fun saveCustomAppLogo(logo: String) = prefs.edit { putString("app_logo", logo) }
    fun getCustomAppLogo(): String = prefs.getString("app_logo", "SAFA") ?: "SAFA"
    
    fun saveCustomAppLogoUri(uri: String?) = prefs.edit { putString("app_logo_uri", uri) }
    fun getCustomAppLogoUri(): String? = prefs.getString("app_logo_uri", "https://safa.masarax.com/safa-logo.png")

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
