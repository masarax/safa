package com.safa.account.data.api

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.core.content.edit
import com.safa.account.data.network.DeviceSecurityHelper
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.json.JSONObject

class TokenManager internal constructor(
    private val context: Context,
    private val vault: TokenVault,
    private val migrationFault: (() -> Unit)?,
) {
    constructor(context: Context) : this(
        context.applicationContext,
        TokenVault(context.applicationContext),
        null,
    )

    private val prefs: SharedPreferences =
        context.getSharedPreferences(METADATA_PREFS_NAME, Context.MODE_PRIVATE)

    private val _sessionInvalidated = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val sessionInvalidated = _sessionInvalidated.asSharedFlow()

    @Volatile
    private var biometricUnlockApproved = false

    @Volatile
    private var logoutInProgress = false

    init {
        migrateLegacyPreferences()
        reconcileAccountContextFromVault()
    }

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
        private const val KEY_LEGACY_MIGRATION_COMPLETE = "secure_prefs_migration_complete"
        private const val KEY_VAULT_MIGRATION_COMPLETE = "secure_vault_v3_migration_complete"
        internal const val METADATA_PREFS_NAME = "safa_secure_metadata_v3"
        private const val DEFAULT_URL = "https://safa.masarax.com/api/"

        private val SECRET_KEYS = setOf(
            KEY_ACCESS_TOKEN,
            KEY_REFRESH_TOKEN,
            KEY_DEVICE_TOKEN,
            KEY_SESSION_TOKEN,
            KEY_FINGERPRINT_TOKEN,
        )
    }

    /**
     * Move one complete legacy credential generation into the v3 vault.
     *
     * The new generation is encrypted and read back before the metadata marker is
     * committed. Legacy stores are deleted only after that durable checkpoint.
     * If the process stops between those steps, the next construction reuses the
     * already verified v3 generation and safely completes the migration.
     */
    private fun migrateLegacyPreferences() {
        if (prefs.getBoolean(KEY_VAULT_MIGRATION_COMPLETE, false)) {
            LegacySecurePreferences.delete(context)
            return
        }

        val plainSnapshot = LegacySecurePreferences.plain(context).all.toMap()
        val currentGeneration = try {
            vault.read()
        } catch (_: TokenVaultException) {
            runCatching { vault.reset() }
            TokenGeneration()
        }

        val encryptedSnapshot = try {
            LegacySecurePreferences.encrypted(context).all.toMap()
        } catch (_: Exception) {
            if (currentGeneration.isEmpty()) {
                completeUnrecoverableLegacyReset(plainSnapshot)
            } else {
                // A prior migration run may have durably verified v3 and then
                // stopped before the metadata marker/legacy cleanup. Never
                // discard that recoverable authoritative generation just
                // because the older compatibility vault later became unreadable.
                commitMigratedMetadata(plainSnapshot, clearSessionBinding = false)
                LegacySecurePreferences.delete(context)
            }
            return
        }

        val legacy = LinkedHashMap<String, Any?>().apply {
            putAll(plainSnapshot)
            putAll(encryptedSnapshot)
        }
        val legacyGeneration = generationFromLegacy(legacy)

        if (!legacyGeneration.isEmpty() && currentGeneration.isEmpty()) {
            vault.write(legacyGeneration)
            if (vault.read() != legacyGeneration) {
                throw TokenVaultException("Legacy token generation verification failed")
            }
            migrationFault?.invoke()
        }

        commitMigratedMetadata(legacy, clearSessionBinding = false)
        LegacySecurePreferences.delete(context)
    }

    private fun commitMigratedMetadata(values: Map<String, Any?>, clearSessionBinding: Boolean) {
        val editor = prefs.edit()
        values.forEach { (key, value) ->
            if (key !in SECRET_KEYS && key != KEY_LEGACY_MIGRATION_COMPLETE) {
                putMetadataValue(editor, key, value)
            }
        }
        if (clearSessionBinding) {
            editor.remove(KEY_ACTIVE_ACCOUNT_ID)
            editor.remove(KEY_BIOMETRIC_ENABLED)
            editor.remove(KEY_BIOMETRIC_USER_ID)
            editor.remove(KEY_BIOMETRIC_MOBILE)
        }
        editor.putBoolean(KEY_VAULT_MIGRATION_COMPLETE, true)
        if (!editor.commit()) {
            throw TokenVaultException("Unable to commit token vault migration metadata")
        }
        if (clearSessionBinding) biometricUnlockApproved = false
    }

    /**
     * A legacy encrypted store whose key can no longer authenticate is not
     * recoverable. Fail closed: preserve only non-secret plain metadata, reset
     * the v3 key/ciphertext, clear session binding, and retire the unreadable
     * legacy store so the user must authenticate again.
     */
    private fun completeUnrecoverableLegacyReset(plainSnapshot: Map<String, Any?>) {
        runCatching { vault.reset() }
        commitMigratedMetadata(plainSnapshot, clearSessionBinding = true)
        LegacySecurePreferences.delete(context)
    }

    private fun generationFromLegacy(values: Map<String, Any?>) = TokenGeneration(
        accessToken = values[KEY_ACCESS_TOKEN] as? String,
        refreshToken = values[KEY_REFRESH_TOKEN] as? String,
        deviceToken = values[KEY_DEVICE_TOKEN] as? String,
        sessionToken = values[KEY_SESSION_TOKEN] as? String,
        fingerprintToken = values[KEY_FINGERPRINT_TOKEN] as? String,
    )

    private fun putMetadataValue(editor: SharedPreferences.Editor, key: String, value: Any?) {
        when (value) {
            is String -> editor.putString(key, value)
            is Boolean -> editor.putBoolean(key, value)
            is Int -> editor.putInt(key, value)
            is Long -> editor.putLong(key, value)
            is Float -> editor.putFloat(key, value)
            is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
        }
    }

    private fun readGeneration(): TokenGeneration = try {
        vault.read()
    } catch (_: TokenVaultException) {
        failClosedVaultReset()
        TokenGeneration()
    }

    private fun writeGeneration(generation: TokenGeneration) {
        try {
            vault.write(generation)
        } catch (error: TokenVaultException) {
            failClosedVaultReset()
            throw error
        }
    }

    private fun failClosedVaultReset() {
        runCatching { vault.reset() }
        prefs.edit {
            remove(KEY_ACTIVE_ACCOUNT_ID)
            remove(KEY_BIOMETRIC_ENABLED)
            remove(KEY_BIOMETRIC_USER_ID)
            remove(KEY_BIOMETRIC_MOBILE)
        }
        biometricUnlockApproved = false
        _sessionInvalidated.tryEmit(Unit)
    }

    private fun reconcileAccountContextFromVault() {
        val accountId = accountIdFromAccessToken(readGeneration().accessToken) ?: return
        if (getActiveAccountId() != accountId) {
            prefs.edit { putInt(KEY_ACTIVE_ACCOUNT_ID, accountId) }
        }
    }

    fun getContext(): Context = context
    fun saveLastMobile(mobile: String) = prefs.edit { putString(KEY_LAST_MOBILE, mobile) }
    fun getLastMobile(): String = prefs.getString(KEY_LAST_MOBILE, "") ?: ""

    fun saveApiKey(key: String) = prefs.edit { putString(KEY_API_KEY, key) }
    fun getApiKey(): String = prefs.getString(KEY_API_KEY, null)?.takeIf { it.isNotBlank() } ?: ""

    fun saveApiSecret(@Suppress("UNUSED_PARAMETER") secret: String) = Unit
    fun getApiSecret(): String = ""

    fun saveAccessToken(token: String?) = writeGeneration(readGeneration().copy(accessToken = token))
    fun getAccessToken(): String? = readGeneration().accessToken
    fun saveToken(token: String) = saveAccessToken(token)
    fun getToken(): String? = getAccessToken()
    fun clearToken() = clearAllTokens()
    fun saveRefreshToken(token: String?) = writeGeneration(readGeneration().copy(refreshToken = token))
    fun getRefreshToken(): String? = readGeneration().refreshToken

    fun saveDeviceToken(token: String?) = writeGeneration(readGeneration().copy(deviceToken = token))
    fun getDeviceToken(): String {
        var token = readGeneration().deviceToken
        if (token.isNullOrBlank()) {
            token = DeviceSecurityHelper.getOrCreateDeviceUuid(context)
            saveDeviceToken(token)
        }
        return token
    }

    fun saveSessionToken(token: String?) = writeGeneration(readGeneration().copy(sessionToken = token))
    fun getSessionToken(): String? = readGeneration().sessionToken
    fun saveFingerprintToken(token: String?) = writeGeneration(readGeneration().copy(fingerprintToken = token))
    fun getFingerprintToken(): String {
        var token = readGeneration().fingerprintToken
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
        val current = readGeneration()
        val next = TokenGeneration(
            accessToken = accessToken,
            refreshToken = refreshToken,
            deviceToken = deviceToken?.takeIf { it.isNotBlank() } ?: current.deviceToken,
            sessionToken = sessionToken,
            fingerprintToken = fingerprintToken?.takeIf { it.isNotBlank() } ?: current.fingerprintToken,
        )
        writeGeneration(next)

        accountIdFromAccessToken(accessToken)?.let { accountId ->
            prefs.edit { putInt(KEY_ACTIVE_ACCOUNT_ID, accountId) }
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

    fun clearAllTokens() {
        val current = readGeneration()
        writeGeneration(
            current.copy(
                accessToken = null,
                refreshToken = null,
                sessionToken = null,
            )
        )
        prefs.edit {
            remove(KEY_ACTIVE_ACCOUNT_ID)
            remove(KEY_BIOMETRIC_ENABLED)
            remove(KEY_BIOMETRIC_USER_ID)
            remove(KEY_BIOMETRIC_MOBILE)
        }
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
