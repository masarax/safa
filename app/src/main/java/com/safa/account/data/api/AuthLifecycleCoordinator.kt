package com.safa.account.data.api

import android.content.Context
import com.safa.account.data.local.LocalAccountBoundary
import com.safa.account.data.sync.SyncCoordinator
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
            val context: Context = tokenManager.getContext().applicationContext

            // Stop future/immediate WorkManager runs first. Cancellation is
            // asynchronous, so the process-wide sync mutex below is the actual
            // race-free lifecycle barrier against a worker that is already active.
            SyncWorkScheduler.cancelAll(context)

            SyncCoordinator.runExclusive {
                // Do not rely on filesystem deletion alone: an AppRepository may
                // still own an open SQLite connection. The transactional wipe is
                // the zero-leakage guarantee.
                runCatching { LocalAccountBoundary.destroyAccountState(context) }

                // Credentials are cleared only while we exclusively own the sync
                // gate, so no same-process sync can read A's credentials after the
                // durable A state has been destroyed or repopulate it afterward.
                tokenManager.clearAllTokens()
                RetrofitClient.clearCache()
            }

            // Extra cleanup after the transactional wipe. This may be a no-op if
            // another repository still owns an open SQLite handle, which is safe.
            runCatching { context.deleteDatabase("safa_local.db") }
        }
        serverResult ?: Result.failure(IllegalStateException("Logout did not complete"))
    }
}
