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
 *
 * Sync failures are converted to WorkManager retries instead of escaping from
 * CoroutineWorker as an immediate permanent failure. A bounded attempt count
 * prevents a permanently broken installation from retrying forever.
 */
class SafaSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val MAX_WORK_ATTEMPTS = 5
    }

    override suspend fun doWork(): Result {
        val tokenManager = TokenManager(applicationContext)
        if (tokenManager.getAccessToken().isNullOrBlank()) return Result.success()

        val repository = AppRepository(applicationContext)

        return try {
            val completed = SyncCoordinator.run {
                repository.processOutbox().getOrThrow()
                repository.refreshAll().getOrThrow()
                true
            }

            when {
                completed == true -> Result.success()
                runAttemptCount < MAX_WORK_ATTEMPTS -> Result.retry()
                else -> Result.failure()
            }
        } catch (_: Throwable) {
            // Network/auth/server failures must use WorkManager backoff rather than
            // escaping CoroutineWorker as a terminal FAILED state.
            if (runAttemptCount < MAX_WORK_ATTEMPTS) Result.retry() else Result.failure()
        }
    }
}
