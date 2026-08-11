package com.safa.account.data.sync

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Process-wide gate for all foreground/background reconciliation.
 *
 * WorkManager already prevents duplicate work by name, but foreground sync,
 * connectivity callbacks and periodic work use different entry points. This
 * gate makes those paths share one critical section so a refresh/upload pair
 * cannot interleave inside the same app process.
 */
object SyncCoordinator {
    // Large local-first batches can legitimately take longer than 30 seconds
    // on slower devices. Waiting up to two minutes avoids false retry storms
    // while still preventing an indefinitely blocked reconciliation caller.
    private const val LOCK_TIMEOUT_MS = 2 * 60 * 1000L
    private val mutex = Mutex()

    suspend fun <T> run(block: suspend () -> T): T? =
        withTimeoutOrNull(LOCK_TIMEOUT_MS) {
            mutex.withLock { block() }
        }
}
