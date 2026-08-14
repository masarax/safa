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
    private val pending = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()
    private val permits = ConcurrentHashMap.newKeySet<String>()
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
     * Confirms a named destructive action and leaves a one-shot permit for the
     * repository mutation that follows a successful direct API delete.
     */
    suspend fun requestAndGrant(targetKey: String, title: String, message: String): Boolean {
        if (!requestConfirmation(title, message)) return false
        permits += targetKey
        return true
    }

    fun grant(targetKey: String) {
        permits += targetKey
    }

    fun consume(targetKey: String): Boolean = permits.remove(targetKey)

    fun resolve(id: String, confirmed: Boolean) {
        pending.remove(id)?.complete(confirmed)
    }

    internal fun pendingCountForTest(): Int = pending.size
    internal fun clearPermitsForTest() = permits.clear()
}
