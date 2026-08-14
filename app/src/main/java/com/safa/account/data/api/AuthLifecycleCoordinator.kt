package com.safa.account.data.api

import android.content.Context
import com.safa.account.data.local.LocalAccountBoundary
import com.safa.account.data.sync.SyncWorkScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Owns the client-side authentication lifecycle boundary.
 *
 * Logout is best-effort against the server, but local credentials and all
 * account-scoped local state are always destroyed in finally. This prevents
 * cached records or queued mutations from account A surviving into account B.
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
            // Stop both immediate and periodic reconciliation before destroying
            // local account state so no worker can carry account A data into B.
            val context: Context = tokenManager.getContext().applicationContext
            SyncWorkScheduler.cancelAll(context)

            // Do not rely on filesystem deletion alone: an AppRepository may
            // still own an open SQLite connection. The transactional wipe is the
            // zero-leakage guarantee; file deletion below is extra cleanup.
            runCatching { LocalAccountBoundary.destroyAccountState(context) }

            tokenManager.clearAllTokens()
            RetrofitClient.clearCache()
            runCatching { context.deleteDatabase("safa_local.db") }
        }
        serverResult ?: Result.failure(IllegalStateException("Logout did not complete"))
    }
}
