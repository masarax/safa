package com.safa.account.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.safa.account.data.api.TokenManager
import com.safa.account.data.local.LocalFirstStore
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
            // Cancellation can race an in-flight upload after rows were leased.
            // Release the lease before propagating cancellation. If the server
            // committed before cancellation, the stable mutation_id makes replay
            // idempotent on the next reconciliation attempt.
            runCatching { LocalFirstStore(applicationContext).resetProcessing() }
            throw t
        } catch (t: Throwable) {
            if (isTransient(t)) {
                // processOutbox leases rows as PROCESSING before the network call.
                // A thrown transport exception produces no HTTP response, so the
                // repository cannot mark those rows retryable itself. Release the
                // lease immediately instead of waiting for stale-processing recovery.
                runCatching { LocalFirstStore(applicationContext).resetProcessing() }
                retryOrFail()
            } else {
                Result.failure()
            }
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
