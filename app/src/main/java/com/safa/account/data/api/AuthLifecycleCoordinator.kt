package com.safa.account.data.api

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Owns the client-side authentication lifecycle boundary.
 *
 * Logout is best-effort against the server, but local credentials and the
 * account-scoped local-first database are always destroyed in finally so a
 * network failure or account switch can never leave another user's offline
 * financial data available on the device.
 */
class AuthLifecycleCoordinator(context: Context, private val tokenManager: TokenManager) {
    private val appContext = context.applicationContext

    suspend fun logout(): Result<Unit> = withContext(Dispatchers.IO) {
        var serverResult: Result<Unit>? = null
        try {
            val api = RetrofitClient.getApiService(
                tokenManager.getBaseUrl(),
                tokenManager.getApiKey(),
                tokenManager.getApiSecret(),
                tokenManager
            )
            val response = api.logout()
            serverResult = if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(IllegalStateException("Server logout returned HTTP ${response.code()}"))
            }
        } catch (t: Throwable) {
            serverResult = Result.failure(t)
        } finally {
            // Security/data-isolation boundary: never retain credentials or
            // offline records after explicit logout. The local store is
            // currently a single-device database, so deleting it prevents
            // cross-account leakage until account-keyed storage is available.
            tokenManager.clearAllTokens()
            appContext.deleteDatabase("safa_local.db")
            RetrofitClient.clearCache()
        }
        serverResult ?: Result.failure(IllegalStateException("Logout did not complete"))
    }
}
