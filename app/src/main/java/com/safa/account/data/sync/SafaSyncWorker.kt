package com.safa.account.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.safa.account.data.api.TokenManager
import com.safa.account.data.repository.AppRepository

/**
 * Persistent background reconciliation for the local-first data layer.
 *
 * WorkManager owns connectivity/backoff while LocalFirstStore owns per-record
 * retry state. A temporary server/network outage must never turn the worker
 * permanently FAILED after an arbitrary number of WorkManager attempts: the
 * durable outbox is specifically designed to recover when connectivity returns.
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
            repository.processOutbox().getOrThrow()
            repository.refreshAll().getOrThrow()
            Result.success()
        } catch (_: Throwable) {
            // Keep the durable outbox alive. WorkManager applies exponential
            // backoff and will retry when the network/server becomes available.
            Result.retry()
        }
    }
}
