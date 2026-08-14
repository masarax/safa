package com.safa.account.data.network

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withTimeoutOrNull

/**
 * UI-owned, coroutine-safe confirmation channel for destructive foreground actions.
 *
 * This coordinator is deliberately never called from an OkHttp interceptor or a
 * WorkManager worker. Callers request confirmation before scheduling/sending a
 * destructive operation; waiting suspends the caller coroutine and consumes no
 * network dispatcher thread.
 */
data class DeleteConfirmationRequest(
    val id: String,
    val title: String,
    val message: String
)

object DeleteConfirmationCoordinator {
    private const val CONFIRMATION_TIMEOUT_MS = 60_000L
    private const val PERMIT_TTL_MS = 30_000L
    private val pending = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()
    private val permits = ConcurrentHashMap<String, Long>()
    private val _requests = MutableSharedFlow<DeleteConfirmationRequest>(extraBufferCapacity = 16)
    val requests = _requests.asSharedFlow()

    suspend fun requestConfirmation(title: String, message: String): Boolean {
        val id = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<Boolean>()
        pending[id] = deferred
        _requests.emit(DeleteConfirmationRequest(id, title, message))
        return try {
            withTimeoutOrNull(CONFIRMATION_TIMEOUT_MS) { deferred.await() } ?: false
        } finally {
            pending.remove(id, deferred)
        }
    }

    /**
     * Confirms a named destructive action and leaves a short-lived one-shot
     * acknowledgement for the repository mutation that follows a successful
     * direct API delete. Exact target scoping + expiry prevents stale permits
     * from authorizing an unrelated future delete.
     */
    suspend fun requestAndGrant(targetKey: String, title: String, message: String): Boolean {
        if (!requestConfirmation(title, message)) return false
        grant(targetKey)
        return true
    }

    fun grant(targetKey: String) {
        permits[targetKey] = System.currentTimeMillis() + PERMIT_TTL_MS
    }

    fun consume(targetKey: String): Boolean {
        val expiresAt = permits.remove(targetKey) ?: return false
        return expiresAt >= System.currentTimeMillis()
    }

    fun resolve(id: String, confirmed: Boolean) {
        pending.remove(id)?.complete(confirmed)
    }

    internal fun pendingCountForTest(): Int = pending.size
    internal fun clearPermitsForTest() = permits.clear()
}
