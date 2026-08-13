package com.safa.account.data.api

import android.content.Context
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
            // the local database so queued work cannot carry account A state
            // into a subsequent account B session.
            val context: Context = tokenManager.getContext().applicationContext
            SyncWorkScheduler.cancelAll(context)
            tokenManager.clearAllTokens()
            RetrofitClient.clearCache()

            // The local-first database contains cached records, revisions and
            // durable outbox mutations. It is intentionally device-local, not
            // a cross-account store. Deleting it at the auth boundary gives a
            // deterministic zero-leakage guarantee when switching accounts.
            context.deleteDatabase("safa_local.db")
        }
        serverResult ?: Result.failure(IllegalStateException("Logout did not complete"))
    }
}
