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
 *
 * The process-wide SyncCoordinator also serializes this worker with foreground
 * and connectivity-triggered sync so upload/download pairs cannot interleave.
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

        val result = SyncCoordinator.run {
            repository.processOutbox().getOrThrow()
            repository.refreshAll().getOrThrow()
        }

        return if (result != null) {
            Result.success()
        } else {
            // Another sync owns the gate or the worker exceeded the coordinator
            // wait window. Keep the durable work queued and let WorkManager retry.
            Result.retry()
        }
    }
}
