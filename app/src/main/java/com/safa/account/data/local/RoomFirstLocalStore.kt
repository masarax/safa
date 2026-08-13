package com.safa.account.data.local

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/**
 * Repository-facing local storage contract. Room owns domain records; the
 * legacy LocalFirstStore remains the encrypted durable outbox/reconciliation
 * engine until that subsystem can be migrated independently without changing
 * sync semantics.
 */
interface LocalStorePort {
    fun nextLocalId(): Int
    fun upsertRecord(entity: String, localId: Int, serverId: Int, payload: String, syncStatus: Int = LocalFirstStore.PENDING, retryCount: Int = 0, error: String? = null)
    fun enqueue(entity: String, localId: Int, serverId: Int, operation: String, payload: String): Long
    fun getRecordPayloads(entity: String): List<LocalFirstStore.StoredRecord>
    fun hasPending(entity: String, localId: Int): Boolean
    fun markSynced(entity: String, localId: Int, serverId: Int, serverVersion: Int = -1)
    fun markFailed(entity: String, localId: Int, message: String, retryable: Boolean)
    fun retry(entity: String, localId: Int)
    fun clearEntity(entity: String)
    fun getReadyOutbox(limit: Int = 50): List<LocalFirstStore.OutboxRecord>
    fun markOutboxProcessing(ids: List<Long>)
}

/**
 * Safe bridge for the current sync engine: domain reads/writes go through
 * Room, while the existing encrypted LocalFirstStore continues to own the
 * durable outbox and reconciliation state.
 */
class RoomFirstLocalStore(context: Context) : LocalStorePort {
    private val appContext = context.applicationContext
    private val legacy = LocalFirstStore(appContext)
    private val room = RoomLocalDatabase.get(appContext)
    private val dao = room.domainRecords()

    private val entities = listOf(
        "operators",
        "customers",
        "suppliers",
        "transactions",
        "supplier_deposits",
        "expenses_incomes",
        "wallet_ledgers",
        "wallet_batches"
    )

    init {
        migrateLegacyDomainDataIfNeeded()
    }

    override fun nextLocalId(): Int = legacy.nextLocalId()

    override fun upsertRecord(entity: String, localId: Int, serverId: Int, payload: String, syncStatus: Int, retryCount: Int, error: String?) {
        // Preserve the existing encrypted outbox/reconciliation semantics.
        legacy.upsertRecord(entity, localId, serverId, payload, syncStatus, retryCount, error)
        roomCall {
            dao.upsert(
                RoomDomainRecord(
                    entity = entity,
                    localId = localId,
                    serverId = serverId,
                    payload = payload,
                    syncVersion = payloadSyncVersion(payload),
                    syncStatus = syncStatus,
                    mutationId = payloadMutationId(payload),
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    override fun enqueue(entity: String, localId: Int, serverId: Int, operation: String, payload: String): Long =
        legacy.enqueue(entity, localId, serverId, operation, payload)

    override fun getRecordPayloads(entity: String): List<LocalFirstStore.StoredRecord> = roomCall {
        dao.getAll(entity).map {
            LocalFirstStore.StoredRecord(
                entity = it.entity,
                localId = it.localId,
                serverId = it.serverId,
                payload = it.payload,
                syncStatus = it.syncStatus,
                retryCount = legacy.retryCount(it.entity, it.localId),
                error = legacy.getRecordPayloads(it.entity).firstOrNull { row -> row.localId == it.localId }?.error
            )
        }
    }

    override fun hasPending(entity: String, localId: Int): Boolean = legacy.hasPending(entity, localId)

    override fun markSynced(entity: String, localId: Int, serverId: Int, serverVersion: Int) {
        legacy.markSynced(entity, localId, serverId, serverVersion)
        roomCall {
            dao.get(entity, localId)?.let { current ->
                dao.upsert(
                    current.copy(
                        serverId = serverId,
                        syncVersion = if (serverVersion >= 0) serverVersion else current.syncVersion,
                        syncStatus = LocalFirstStore.SYNCED,
                        mutationId = null,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    override fun markFailed(entity: String, localId: Int, message: String, retryable: Boolean) {
        legacy.markFailed(entity, localId, message, retryable)
        syncRoomFromLegacy(entity, localId)
    }

    override fun retry(entity: String, localId: Int) {
        legacy.retry(entity, localId)
        syncRoomFromLegacy(entity, localId)
    }

    override fun clearEntity(entity: String) {
        legacy.clearEntity(entity)
        roomCall { dao.deleteEntity(entity) }
    }

    override fun getReadyOutbox(limit: Int): List<LocalFirstStore.OutboxRecord> = legacy.getReadyOutbox(limit)

    override fun markOutboxProcessing(ids: List<Long>) = legacy.markOutboxProcessing(ids)

    private fun migrateLegacyDomainDataIfNeeded() {
        entities.forEach { entity ->
            val existing = roomCall { dao.count(entity) }
            if (existing > 0) return@forEach
            val legacyRows = legacy.getRecordPayloads(entity)
            if (legacyRows.isEmpty()) return@forEach
            roomCall {
                dao.upsertAll(
                    legacyRows.map { row ->
                        RoomDomainRecord(
                            entity = row.entity,
                            localId = row.localId,
                            serverId = row.serverId,
                            payload = row.payload,
                            syncVersion = payloadSyncVersion(row.payload),
                            syncStatus = row.syncStatus,
                            mutationId = payloadMutationId(row.payload),
                            updatedAt = System.currentTimeMillis()
                        )
                    }
                )
            }
        }
    }

    private fun syncRoomFromLegacy(entity: String, localId: Int) {
        val row = legacy.getRecordPayloads(entity).firstOrNull { it.localId == localId } ?: return
        roomCall {
            dao.upsert(
                RoomDomainRecord(
                    entity = row.entity,
                    localId = row.localId,
                    serverId = row.serverId,
                    payload = row.payload,
                    syncVersion = payloadSyncVersion(row.payload),
                    syncStatus = row.syncStatus,
                    mutationId = payloadMutationId(row.payload),
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    private fun payloadSyncVersion(payload: String): Int =
        runCatching { org.json.JSONObject(payload).optInt("sync_version", 0) }.getOrDefault(0)

    private fun payloadMutationId(payload: String): String? =
        runCatching { org.json.JSONObject(payload).optJSONObject("_sync")?.optString("mutation_id")?.takeIf { it.isNotBlank() } }.getOrNull()

    private fun <T> roomCall(block: suspend () -> T): T = runBlocking(Dispatchers.IO) { block() }
}
