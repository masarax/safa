package com.safa.account.data.network

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * UI-owned confirmation channel for destructive network operations.
 * The HTTP interceptor can safely pause a background request while Compose
 * renders the actual confirmation modal from an Activity context.
 */
data class DeleteConfirmationRequest(
    val id: String,
    val title: String,
    val message: String
)

object DeleteConfirmationCoordinator {
    private val pending = java.util.concurrent.ConcurrentHashMap<String, CompletableFuture<Boolean>>()
    private val _requests = MutableSharedFlow<DeleteConfirmationRequest>(extraBufferCapacity = 16)
    val requests = _requests.asSharedFlow()

    fun awaitConfirmation(title: String, message: String): Boolean {
        val id = UUID.randomUUID().toString()
        val future = CompletableFuture<Boolean>()
        pending[id] = future
        _requests.tryEmit(DeleteConfirmationRequest(id, title, message))
        return try {
            future.get(60, TimeUnit.SECONDS)
        } catch (_: Exception) {
            false
        } finally {
            pending.remove(id)
        }
    }

    fun resolve(id: String, confirmed: Boolean) {
        pending.remove(id)?.complete(confirmed)
    }
}
