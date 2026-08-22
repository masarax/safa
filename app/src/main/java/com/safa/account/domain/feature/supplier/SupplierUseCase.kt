package com.safa.account.domain.feature.supplier

import com.safa.account.data.model.Supplier
import com.safa.account.data.model.SyncStatus
import com.safa.account.domain.feature.SupplierRepository

sealed interface SupplierRemoteCreateResult {
    data class Created(val serverId: Int) : SupplierRemoteCreateResult
    data class Rejected(val status: Int) : SupplierRemoteCreateResult
}

sealed interface SupplierRemoteDeleteResult {
    data object Deleted : SupplierRemoteDeleteResult
    data class Rejected(val status: Int) : SupplierRemoteDeleteResult
}

interface SupplierRemoteGateway {
    suspend fun create(name: String, phone: String, address: String): SupplierRemoteCreateResult
    suspend fun delete(serverId: Int): SupplierRemoteDeleteResult
}

interface SupplierOutboxGateway {
    suspend fun enqueueCreate(
        userId: Int,
        localId: Int,
        name: String,
        phone: String,
        address: String,
    )

    suspend fun enqueueDelete(userId: Int, localId: Int, serverId: Int)
}

interface SupplierSyncGateway {
    fun isOnline(): Boolean
    fun requestBackgroundSync()
    suspend fun syncNow()
}

interface SupplierOperationLogger {
    fun online(message: String)
    fun response(message: String)
    fun rejected(message: String)
    fun fallback(message: String, error: Exception)
    fun queued(message: String)
}

object NoOpSupplierOperationLogger : SupplierOperationLogger {
    override fun online(message: String) = Unit
    override fun response(message: String) = Unit
    override fun rejected(message: String) = Unit
    override fun fallback(message: String, error: Exception) = Unit
    override fun queued(message: String) = Unit
}

sealed interface SupplierCommandResult {
    data object Completed : SupplierCommandResult
    data object InvalidInput : SupplierCommandResult
    data object NotFound : SupplierCommandResult
    data class Rejected(val action: String, val status: Int) : SupplierCommandResult
}

/**
 * Supplier-scoped create/update/delete orchestration.
 *
 * Supplier funding and wallet mutation remain outside this boundary on purpose;
 * this incremental slice only removes supplier CRUD decisions from the global
 * application ViewModel while preserving the canonical local-first repository.
 */
class SupplierUseCase(
    private val repository: SupplierRepository,
    private val remote: SupplierRemoteGateway?,
    private val outbox: SupplierOutboxGateway,
    private val sync: SupplierSyncGateway,
    private val logger: SupplierOperationLogger = NoOpSupplierOperationLogger,
) {
    suspend fun create(
        name: String,
        phone: String,
        address: String,
        userId: Int,
    ): SupplierCommandResult {
        if (name.isBlank()) return SupplierCommandResult.InvalidInput

        if (sync.isOnline() && remote != null) {
            logger.online("Online create supplier")
            try {
                when (val result = remote.create(name, phone, address)) {
                    is SupplierRemoteCreateResult.Created -> {
                        logger.response("Server created supplier id=${result.serverId}")
                        repository.insert(
                            Supplier(
                                serverId = result.serverId,
                                name = name,
                                phone = phone,
                                address = address,
                                syncStatus = SyncStatus.SYNCED,
                            )
                        )
                        return SupplierCommandResult.Completed
                    }

                    is SupplierRemoteCreateResult.Rejected -> {
                        logger.rejected("Create supplier rejected with HTTP ${result.status}")
                        return SupplierCommandResult.Rejected("Create supplier", result.status)
                    }
                }
            } catch (error: Exception) {
                logger.fallback("Create supplier network call failed; using outbox", error)
            }
        }

        logger.queued("Offline create supplier")
        val localId = repository.insert(
            Supplier(
                name = name,
                phone = phone,
                address = address,
                syncStatus = SyncStatus.PENDING_CREATE,
            )
        )
        outbox.enqueueCreate(userId, localId, name, phone, address)
        sync.requestBackgroundSync()
        return SupplierCommandResult.Completed
    }

    suspend fun update(supplier: Supplier): SupplierCommandResult {
        val updatedStatus = if (supplier.syncStatus == SyncStatus.SYNCED) {
            SyncStatus.PENDING_UPDATE
        } else {
            supplier.syncStatus
        }
        repository.update(supplier.copy(syncStatus = updatedStatus))
        sync.syncNow()
        return SupplierCommandResult.Completed
    }

    suspend fun delete(id: Int, userId: Int): SupplierCommandResult {
        val target = repository.find(id) ?: return SupplierCommandResult.NotFound

        if (sync.isOnline() && remote != null && target.serverId > 0) {
            logger.online("Online delete supplier serverId=${target.serverId}")
            try {
                when (val result = remote.delete(target.serverId)) {
                    SupplierRemoteDeleteResult.Deleted -> {
                        logger.response("Server deleted supplier serverId=${target.serverId}")
                        repository.removeAccepted(id)
                        return SupplierCommandResult.Completed
                    }

                    is SupplierRemoteDeleteResult.Rejected -> {
                        logger.rejected("Delete supplier rejected with HTTP ${result.status}")
                        return SupplierCommandResult.Rejected("Delete supplier", result.status)
                    }
                }
            } catch (error: Exception) {
                logger.fallback("Delete supplier network call failed; using outbox", error)
            }
        }

        logger.queued("Offline delete supplier localId=$id")
        repository.delete(id)
        outbox.enqueueDelete(userId, id, target.serverId)
        sync.requestBackgroundSync()
        return SupplierCommandResult.Completed
    }
}
