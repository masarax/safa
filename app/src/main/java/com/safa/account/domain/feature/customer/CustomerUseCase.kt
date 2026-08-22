package com.safa.account.domain.feature.customer

import com.safa.account.data.model.Customer
import com.safa.account.data.model.SyncStatus
import com.safa.account.domain.feature.CustomerRepository

sealed interface CustomerRemoteCreateResult {
    data class Created(val serverId: Int) : CustomerRemoteCreateResult
    data class Rejected(val status: Int) : CustomerRemoteCreateResult
}

sealed interface CustomerRemoteDeleteResult {
    data object Deleted : CustomerRemoteDeleteResult
    data class Rejected(val status: Int) : CustomerRemoteDeleteResult
}

interface CustomerRemoteGateway {
    suspend fun create(name: String, phone: String, address: String): CustomerRemoteCreateResult
    suspend fun delete(serverId: Int): CustomerRemoteDeleteResult
}

interface CustomerOutboxGateway {
    suspend fun enqueueCreate(
        userId: Int,
        localId: Int,
        name: String,
        phone: String,
        address: String,
    )

    suspend fun enqueueDelete(userId: Int, localId: Int, serverId: Int)
}

interface CustomerSyncGateway {
    fun isOnline(): Boolean
    fun requestBackgroundSync()
    suspend fun syncNow()
}

interface CustomerOperationLogger {
    fun online(message: String)
    fun response(message: String)
    fun rejected(message: String)
    fun fallback(message: String, error: Exception)
    fun queued(message: String)
}

object NoOpCustomerOperationLogger : CustomerOperationLogger {
    override fun online(message: String) = Unit
    override fun response(message: String) = Unit
    override fun rejected(message: String) = Unit
    override fun fallback(message: String, error: Exception) = Unit
    override fun queued(message: String) = Unit
}

sealed interface CustomerCommandResult {
    data object Completed : CustomerCommandResult
    data object InvalidInput : CustomerCommandResult
    data object NotFound : CustomerCommandResult
    data class Rejected(val action: String, val status: Int) : CustomerCommandResult
}

/**
 * Customer-scoped mutation orchestration.
 *
 * This preserves SAFA's server-authoritative / local-first behavior while moving
 * customer create/update/delete decisions out of the global application ViewModel.
 * Infrastructure is supplied through narrow ports so this logic is unit-testable
 * without Android, Retrofit, SQLite, or production credentials.
 */
class CustomerUseCase(
    private val repository: CustomerRepository,
    private val remote: CustomerRemoteGateway?,
    private val outbox: CustomerOutboxGateway,
    private val sync: CustomerSyncGateway,
    private val logger: CustomerOperationLogger = NoOpCustomerOperationLogger,
) {
    suspend fun create(
        name: String,
        phone: String,
        address: String,
        userId: Int,
    ): CustomerCommandResult {
        if (name.isBlank() || phone.isBlank()) return CustomerCommandResult.InvalidInput

        if (sync.isOnline() && remote != null) {
            logger.online("Online create customer")
            try {
                when (val result = remote.create(name, phone, address)) {
                    is CustomerRemoteCreateResult.Created -> {
                        logger.response("Server created customer id=${result.serverId}")
                        repository.insert(
                            Customer(
                                serverId = result.serverId,
                                name = name,
                                phone = phone,
                                address = address,
                                syncStatus = SyncStatus.SYNCED,
                            )
                        )
                        return CustomerCommandResult.Completed
                    }

                    is CustomerRemoteCreateResult.Rejected -> {
                        logger.rejected("Create customer rejected with HTTP ${result.status}")
                        return CustomerCommandResult.Rejected("Create customer", result.status)
                    }
                }
            } catch (error: Exception) {
                logger.fallback("Create customer network call failed; using outbox", error)
            }
        }

        logger.queued("Offline create customer")
        val localId = repository.insert(
            Customer(
                name = name,
                phone = phone,
                address = address,
                syncStatus = SyncStatus.PENDING_CREATE,
            )
        )
        outbox.enqueueCreate(userId, localId, name, phone, address)
        logger.queued("Enqueued outbox CREATE for customer localId=$localId")
        sync.requestBackgroundSync()
        return CustomerCommandResult.Completed
    }

    suspend fun update(customer: Customer): CustomerCommandResult {
        val updatedStatus = if (customer.syncStatus == SyncStatus.SYNCED) {
            SyncStatus.PENDING_UPDATE
        } else {
            customer.syncStatus
        }
        repository.update(customer.copy(syncStatus = updatedStatus))
        sync.syncNow()
        return CustomerCommandResult.Completed
    }

    suspend fun delete(id: Int, userId: Int): CustomerCommandResult {
        val target = repository.find(id) ?: return CustomerCommandResult.NotFound

        if (sync.isOnline() && remote != null && target.serverId > 0) {
            logger.online("Online delete customer serverId=${target.serverId}")
            try {
                when (val result = remote.delete(target.serverId)) {
                    CustomerRemoteDeleteResult.Deleted -> {
                        logger.response("Server deleted customer serverId=${target.serverId}")
                        repository.removeAccepted(id)
                        return CustomerCommandResult.Completed
                    }

                    is CustomerRemoteDeleteResult.Rejected -> {
                        logger.rejected("Delete customer rejected with HTTP ${result.status}")
                        return CustomerCommandResult.Rejected("Delete customer", result.status)
                    }
                }
            } catch (error: Exception) {
                logger.fallback("Delete customer network call failed; using outbox", error)
            }
        }

        logger.queued("Offline delete customer localId=$id")
        repository.delete(id)
        outbox.enqueueDelete(userId, id, target.serverId)
        sync.requestBackgroundSync()
        return CustomerCommandResult.Completed
    }
}
