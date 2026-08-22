package com.safa.account.data.repository

import com.safa.account.data.api.SyncManager
import com.safa.account.data.api.TokenManager
import com.safa.account.data.model.OutboxOperation
import com.safa.account.data.model.OutboxStatus
import com.safa.account.data.model.SyncOutbox
import com.safa.account.domain.feature.customer.CustomerOperationLogger
import com.safa.account.domain.feature.customer.CustomerOutboxGateway
import com.safa.account.domain.feature.customer.CustomerRemoteCreateResult
import com.safa.account.domain.feature.customer.CustomerRemoteDeleteResult
import com.safa.account.domain.feature.customer.CustomerRemoteGateway
import com.safa.account.domain.feature.customer.CustomerSyncGateway
import com.safa.account.utils.ConnectivityMonitor
import com.safa.account.utils.SafaLogger
import org.json.JSONObject

class AppCustomerRemoteGateway(
    private val syncManager: SyncManager,
) : CustomerRemoteGateway {
    override suspend fun create(
        name: String,
        phone: String,
        address: String,
    ): CustomerRemoteCreateResult {
        val response = syncManager.getApiService().createCustomer(
            mapOf("name" to name, "phone" to phone, "address" to address)
        )
        if (!response.isSuccessful || response.body() == null) {
            return CustomerRemoteCreateResult.Rejected(response.code())
        }

        val body = response.body()!!
        val serverId = (body["id"] as? Number)?.toInt()
            ?: ((body["customer"] as? Map<*, *>)?.get("id") as? Number)?.toInt()
            ?: 0
        return CustomerRemoteCreateResult.Created(serverId)
    }

    override suspend fun delete(serverId: Int): CustomerRemoteDeleteResult {
        val response = syncManager.getApiService().deleteCustomerApi(serverId)
        return if (response.isSuccessful) {
            CustomerRemoteDeleteResult.Deleted
        } else {
            CustomerRemoteDeleteResult.Rejected(response.code())
        }
    }
}

class AppCustomerOutboxGateway(
    private val repository: AppRepository,
) : CustomerOutboxGateway {
    override suspend fun enqueueCreate(
        userId: Int,
        localId: Int,
        name: String,
        phone: String,
        address: String,
    ) {
        val payloadJson = JSONObject(
            mapOf(
                "local_id" to localId,
                "name" to name,
                "phone" to phone,
                "address" to address,
            )
        ).toString()
        repository.enqueueOutbox(
            SyncOutbox(
                userId = userId,
                entityType = "CUSTOMER",
                entityLocalId = localId,
                operation = OutboxOperation.CREATE,
                payloadJson = payloadJson,
                status = OutboxStatus.PENDING,
            )
        )
    }

    override suspend fun enqueueDelete(userId: Int, localId: Int, serverId: Int) {
        val payloadJson = JSONObject(
            mapOf("local_id" to localId, "server_id" to serverId)
        ).toString()
        repository.enqueueOutbox(
            SyncOutbox(
                userId = userId,
                entityType = "CUSTOMER",
                entityLocalId = localId,
                entityServerId = serverId,
                operation = OutboxOperation.DELETE,
                payloadJson = payloadJson,
                status = OutboxStatus.PENDING,
            )
        )
    }
}

class AppCustomerSyncGateway(
    private val tokenManager: TokenManager?,
    private val syncManager: SyncManager?,
    private val backgroundSync: () -> Unit,
) : CustomerSyncGateway {
    override fun isOnline(): Boolean = ConnectivityMonitor.isOnline(tokenManager?.getContext())

    override fun requestBackgroundSync() {
        backgroundSync()
    }

    override suspend fun syncNow() {
        syncManager?.syncAll()
    }
}

object SafaCustomerOperationLogger : CustomerOperationLogger {
    override fun online(message: String) {
        SafaLogger.log("ONLINE_REQUEST", message)
    }

    override fun response(message: String) {
        SafaLogger.log("SERVER_RESPONSE", message)
    }

    override fun rejected(message: String) {
        SafaLogger.warn("SERVER_RESPONSE", message)
    }

    override fun fallback(message: String, error: Exception) {
        SafaLogger.error("OFFLINE_QUEUE", message, error)
    }

    override fun queued(message: String) {
        SafaLogger.log("OFFLINE_QUEUE", message)
    }
}
