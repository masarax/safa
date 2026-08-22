package com.safa.account.data.repository

import com.safa.account.data.api.SyncManager
import com.safa.account.data.api.TokenManager
import com.safa.account.data.model.OutboxOperation
import com.safa.account.data.model.OutboxStatus
import com.safa.account.data.model.SyncOutbox
import com.safa.account.domain.feature.supplier.SupplierOperationLogger
import com.safa.account.domain.feature.supplier.SupplierOutboxGateway
import com.safa.account.domain.feature.supplier.SupplierRemoteCreateResult
import com.safa.account.domain.feature.supplier.SupplierRemoteDeleteResult
import com.safa.account.domain.feature.supplier.SupplierRemoteGateway
import com.safa.account.domain.feature.supplier.SupplierSyncGateway
import com.safa.account.utils.ConnectivityMonitor
import com.safa.account.utils.SafaLogger
import org.json.JSONObject

class AppSupplierRemoteGateway(
    private val syncManager: SyncManager,
) : SupplierRemoteGateway {
    override suspend fun create(
        name: String,
        phone: String,
        address: String,
    ): SupplierRemoteCreateResult {
        val response = syncManager.getApiService().createSupplier(
            mapOf("name" to name, "phone" to phone, "address" to address)
        )
        if (!response.isSuccessful || response.body() == null) {
            return SupplierRemoteCreateResult.Rejected(response.code())
        }

        val body = response.body()!!
        val serverId = (body["id"] as? Number)?.toInt()
            ?: ((body["supplier"] as? Map<*, *>)?.get("id") as? Number)?.toInt()
            ?: 0
        return SupplierRemoteCreateResult.Created(serverId)
    }

    override suspend fun delete(serverId: Int): SupplierRemoteDeleteResult {
        val response = syncManager.getApiService().deleteSupplierApi(serverId)
        return if (response.isSuccessful) {
            SupplierRemoteDeleteResult.Deleted
        } else {
            SupplierRemoteDeleteResult.Rejected(response.code())
        }
    }
}

class AppSupplierOutboxGateway(
    private val repository: AppRepository,
) : SupplierOutboxGateway {
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
                entityType = "SUPPLIER",
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
                entityType = "SUPPLIER",
                entityLocalId = localId,
                entityServerId = serverId,
                operation = OutboxOperation.DELETE,
                payloadJson = payloadJson,
                status = OutboxStatus.PENDING,
            )
        )
    }
}

class AppSupplierSyncGateway(
    private val tokenManager: TokenManager?,
    private val syncManager: SyncManager?,
    private val backgroundSync: () -> Unit,
) : SupplierSyncGateway {
    override fun isOnline(): Boolean = ConnectivityMonitor.isOnline(tokenManager?.getContext())

    override fun requestBackgroundSync() {
        backgroundSync()
    }

    override suspend fun syncNow() {
        syncManager?.syncAll()
    }
}

object SafaSupplierOperationLogger : SupplierOperationLogger {
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
