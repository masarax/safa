package com.safa.account.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.safa.account.data.api.TokenManager
import com.safa.account.data.repository.AppRepository

/**
 * Persistent background reconciliation for local-first data.
 * WorkManager provides execution outside the foreground activity and only runs
 * when a network is available (see SyncWorkScheduler).
 */
class SafaSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val tokenManager = TokenManager(applicationContext)
        if (tokenManager.getAccessToken().isNullOrBlank()) return Result.success()

        val repository = AppRepository(applicationContext)
        return try {
            repository.processOutbox().getOrThrow()
            repository.refreshAll().getOrThrow()
            Result.success()
        } catch (_: Exception) {
            // Network/server failures are retried by WorkManager. Individual
            // outbox records still keep their own retry/backoff state in SQLite.
            Result.retry()
        }
    }
}
