package com.safa.account.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Single-owner scheduler for local-first reconciliation.
 *
 * Periodic sync is durable and network-constrained. User/network-triggered sync
 * uses a unique expedited request so a reconnect does not wait for the next
 * periodic window. If expedited quota is unavailable, WorkManager transparently
 * falls back to a normal request.
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
     * Queue one reconciliation immediately. Existing work is kept deliberately:
     * repeated UI/network callbacks must not create competing sync workers.
     * Expedited execution is requested because this is a user/reconnect-triggered
     * operation; WorkManager falls back safely when quota is unavailable.
     */
    fun runNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<SafaSyncWorker>()
            .setConstraints(networkConstraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()

        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            IMMEDIATE_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    /**
     * Cancel only an explicitly queued immediate run. Persistent periodic sync
     * remains installed and continues to reconcile when connectivity returns.
     */
    fun cancelImmediate(context: Context) {
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(IMMEDIATE_WORK_NAME)
    }
}
