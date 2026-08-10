package com.safa.account.data.network

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Coordinates immediate, network-constrained reconciliation without creating sync loops. */
object SyncTrigger {
    private val syncRunning = AtomicBoolean(false)
    private const val UNIQUE_WORK = "SafaImmediateSync"

    fun schedule(context: Context) {
        if (syncRunning.get()) return
        val request = OneTimeWorkRequestBuilder<AutoSyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setInitialDelay(250, TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            UNIQUE_WORK,
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    fun begin(): Boolean = syncRunning.compareAndSet(false, true)
    fun end() { syncRunning.set(false) }
    fun isRunning(): Boolean = syncRunning.get()
}
