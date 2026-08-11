package com.safa.account.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Owns the client-side authentication lifecycle boundary.
 *
 * Logout is best-effort against the server, but local credentials are always
 * destroyed in finally so a network failure can never leave a usable session
 * behind on the device.
 */
class AuthLifecycleCoordinator(private val tokenManager: TokenManager) {

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
            // Security boundary: never keep an access/refresh/session token or
            // biometric quick-unlock authorization after explicit logout.
            tokenManager.clearAllTokens()
            RetrofitClient.clearCache()
        }
        serverResult ?: Result.failure(IllegalStateException("Logout did not complete"))
    }
}
