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
    // A foreground sync can legitimately be busy with a large outbox batch.
    // Waiting only 10 seconds caused avoidable background retries on slower phones.
    private const val LOCK_TIMEOUT_MS = 30_000L
    private val mutex = Mutex()

    suspend fun <T> run(block: suspend () -> T): T? =
        withTimeoutOrNull(LOCK_TIMEOUT_MS) {
            mutex.withLock { block() }
        }
}
