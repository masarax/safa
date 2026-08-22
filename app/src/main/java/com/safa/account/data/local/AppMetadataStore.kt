package com.safa.account.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.safaMetadataDataStore by preferencesDataStore(name = "safa_app_metadata")

/** Non-secret application/session metadata. Tokens and keys remain Keystore-backed. */
class AppMetadataStore(private val context: Context) {
    private object Keys {
        val language = stringPreferencesKey("language")
        val theme = stringPreferencesKey("theme")
        val appVersion = stringPreferencesKey("app_version")
        val onboardingComplete = booleanPreferencesKey("onboarding_complete")
        fun syncCursor(accountId: Int) = longPreferencesKey("sync_cursor_account_$accountId")
    }

    val language: Flow<String> = context.safaMetadataDataStore.data.map { it[Keys.language] ?: "BN" }
    val theme: Flow<String> = context.safaMetadataDataStore.data.map { it[Keys.theme] ?: "LIGHT" }
    val appVersion: Flow<String> = context.safaMetadataDataStore.data.map { it[Keys.appVersion] ?: "1.0" }
    val onboardingComplete: Flow<Boolean> = context.safaMetadataDataStore.data.map { it[Keys.onboardingComplete] ?: false }

    suspend fun setLanguage(value: String) = context.safaMetadataDataStore.edit { it[Keys.language] = value }
    suspend fun setTheme(value: String) = context.safaMetadataDataStore.edit { it[Keys.theme] = value }
    suspend fun setAppVersion(value: String) = context.safaMetadataDataStore.edit { it[Keys.appVersion] = value }
    suspend fun setOnboardingComplete(value: Boolean) = context.safaMetadataDataStore.edit { it[Keys.onboardingComplete] = value }

    suspend fun getSyncCursor(accountId: Int): Long? {
        if (accountId <= 0) return null
        return context.safaMetadataDataStore.data.first()[Keys.syncCursor(accountId)]
    }

    suspend fun setSyncCursor(accountId: Int, cursor: Long) {
        if (accountId <= 0) return
        context.safaMetadataDataStore.edit { preferences ->
            preferences[Keys.syncCursor(accountId)] = cursor.coerceAtLeast(0L)
        }
    }

    suspend fun clearSyncCursor(accountId: Int) {
        if (accountId <= 0) return
        context.safaMetadataDataStore.edit { it.remove(Keys.syncCursor(accountId)) }
    }
}
