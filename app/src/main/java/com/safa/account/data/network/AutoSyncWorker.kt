package com.safa.account.data.network

import android.content.Context

/**
 * Compatibility shim. The old WorkManager/local-database worker has been
 * removed. Remote data is refreshed by the ViewModel/repository directly.
 */
@Deprecated("Local database sync worker removed")
object AutoSyncWorker {
    fun schedulePeriodicSync(context: Context) = Unit
}
