package com.safa.account.data.api

import android.content.Context
import androidx.core.content.edit

class TokenManager(context: Context) {

    private val prefs = context.getSharedPreferences("safa_secure_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_TOKEN = "auth_token"
        private const val KEY_BASE_URL = "base_url"
        private const val DEFAULT_URL = "http://192.168.100.229:8000/api/"
    }

    fun saveToken(token: String) = prefs.edit { putString(KEY_TOKEN, token) }

    fun getToken(): String? = prefs.getString(KEY_TOKEN, null)

    fun clearToken() = prefs.edit { remove(KEY_TOKEN) }

    fun saveBaseUrl(url: String) = prefs.edit { putString(KEY_BASE_URL, url) }

    fun getBaseUrl(): String = prefs.getString(KEY_BASE_URL, DEFAULT_URL) ?: DEFAULT_URL
}
