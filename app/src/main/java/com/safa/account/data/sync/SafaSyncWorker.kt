package com.safa.account.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.safa.account.data.api.TokenManager
import com.safa.account.data.repository.AppRepository
import java.io.IOException
import kotlinx.coroutines.CancellationException

/**
 * Persistent background reconciliation for the local-first data layer.
 *
 * The durable outbox owns record-level retry state. WorkManager owns process-level
 * retry/backoff, but the worker also caps transient attempts so a permanently
 * unreachable service cannot create an unbounded retry loop.
 */
class SafaSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val MAX_TRANSIENT_ATTEMPTS = 5
    }

    override suspend fun doWork(): Result {
        val tokenManager = TokenManager(applicationContext)
        if (tokenManager.getAccessToken().isNullOrBlank()) {
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
                retryOrFail()
            }
        } catch (t: CancellationException) {
            throw t
        } catch (t: Throwable) {
            if (isTransient(t)) retryOrFail() else Result.failure()
        }
    }

    private fun retryOrFail(): Result =
        if (runAttemptCount >= MAX_TRANSIENT_ATTEMPTS) {
            Result.failure()
        } else {
            Result.retry()
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
