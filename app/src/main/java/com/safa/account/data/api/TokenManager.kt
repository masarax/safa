package com.safa.account.data.api

import android.content.Context
import androidx.core.content.edit

class TokenManager(context: Context) {

    private val prefs = context.getSharedPreferences("safa_secure_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_TOKEN = "auth_token"
        private const val KEY_BASE_URL = "base_url"
        private const val DEFAULT_URL = "https://safa.masarax.com/api/"
    }

    fun saveToken(token: String) = prefs.edit { putString(KEY_TOKEN, token) }

    fun getToken(): String? = prefs.getString(KEY_TOKEN, null)

    fun clearToken() = prefs.edit { remove(KEY_TOKEN) }

    fun saveBaseUrl(url: String) = prefs.edit { putString(KEY_BASE_URL, url) }

    fun getBaseUrl(): String = prefs.getString(KEY_BASE_URL, DEFAULT_URL) ?: DEFAULT_URL
    
    // --- App Settings Persistence ---
    fun saveLanguage(lang: String) = prefs.edit { putString("app_lang", lang) }
    fun getLanguage(): String = prefs.getString("app_lang", "BN") ?: "BN"
    
    fun saveCustomAppName(name: String) = prefs.edit { putString("app_name", name) }
    fun getCustomAppName(): String = prefs.getString("app_name", "SAFA") ?: "SAFA"
    
    fun saveCustomAppLogo(logo: String) = prefs.edit { putString("app_logo", logo) }
    fun getCustomAppLogo(): String = prefs.getString("app_logo", "👑") ?: "👑"
    
    fun saveCustomAppLogoUri(uri: String?) = prefs.edit { putString("app_logo_uri", uri) }
    fun getCustomAppLogoUri(): String? = prefs.getString("app_logo_uri", null)
    
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
