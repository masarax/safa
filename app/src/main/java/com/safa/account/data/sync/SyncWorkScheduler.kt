package com.safa.account.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Single-owner scheduler for local-first reconciliation.
 *
 * Rules:
 * - Periodic reconciliation is unique, so application restarts cannot create duplicates.
 * - Immediate/manual reconciliation is also unique, so repeated UI/network callbacks do
 *   not run multiple workers against the same SQLite outbox at the same time.
 * - WorkManager owns network waiting/retry; LocalFirstStore owns per-record retry state.
 */
object SyncWorkScheduler {
    private const val PERIODIC_WORK_NAME = "safa_persistent_sync"
    private const val IMMEDIATE_WORK_NAME = "safa_immediate_sync"

    private fun networkConstraints(): Constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<SafaSyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(networkConstraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    /**
     * Queue one reconciliation immediately. Existing work is kept deliberately: if a sync
     * is already running, another caller must not start a competing sync worker.
     */
    fun runNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<SafaSyncWorker>()
            .setConstraints(networkConstraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            IMMEDIATE_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    /**
     * Cancel only an explicitly queued immediate run. The persistent periodic sync remains
     * installed and will continue to reconcile when connectivity is available.
     */
    fun cancelImmediate(context: Context) {
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(IMMEDIATE_WORK_NAME)
    }
}
