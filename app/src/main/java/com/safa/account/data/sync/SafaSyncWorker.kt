package com.safa.account.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.safa.account.data.api.TokenManager
import com.safa.account.data.repository.AppRepository
import java.io.IOException

/**
 * Persistent background reconciliation for the local-first data layer.
 *
 * The durable outbox owns record-level retry state. WorkManager owns process-level
 * retry/backoff. A transient network/server failure must therefore return
 * Result.retry() instead of escaping from doWork() as a permanent worker failure.
 */
class SafaSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val tokenManager = TokenManager(applicationContext)
        if (tokenManager.getAccessToken().isNullOrBlank()) {
            // Nothing can be synchronized until the user has authenticated.
            return Result.success()
        }

        val repository = AppRepository(applicationContext)

        return try {
            val result = SyncCoordinator.run {
                repository.processOutbox().getOrThrow()
                repository.refreshAll().getOrThrow()
            }

            if (result != null) {
                Result.success()
            } else {
                // Another sync owns the gate. Keep the durable outbox untouched and
                // let WorkManager retry using the request's exponential backoff.
                Result.retry()
            }
        } catch (t: Throwable) {
            when {
                isTransient(t) -> Result.retry()
                // Authentication/authorization failures are not solved by retrying
                // forever. The interceptor owns refresh; if that boundary failed,
                // stop this run and let the next authenticated session start fresh.
                else -> Result.failure()
            }
        }
    }

    private fun isTransient(t: Throwable): Boolean {
        if (t is IOException) return true
        val message = generateSequence(t) { it.cause }
            .mapNotNull { it.message }
            .joinToString(" ")
            .uppercase()

        return message.contains("HTTP 408") ||
            message.contains("HTTP 429") ||
            message.contains("HTTP 500") ||
            message.contains("HTTP 502") ||
            message.contains("HTTP 503") ||
            message.contains("HTTP 504") ||
            message.contains("TIMED OUT") ||
            message.contains("TIMEOUT") ||
            message.contains("CONNECTION") ||
            message.contains("UNABLE TO RESOLVE HOST") ||
            message.contains("NETWORK")
    }
}
