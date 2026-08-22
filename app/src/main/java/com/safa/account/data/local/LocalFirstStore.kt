package com.safa.account.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Base64
import com.safa.account.data.network.DeviceSecurityHelper
import com.safa.account.utils.SafaLogger
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.MessageDigest
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONObject

/** Encrypted local database metadata + durable crash-safe outbox. */
class LocalFirstStore(context: Context) : SQLiteOpenHelper(context.applicationContext, "safa_local.db", null, VERSION) {
    companion object {
        private const val VERSION = 6
        private const val MAX_RETRIES = 5
        private const val PROCESSING_TIMEOUT_MS = 2 * 60 * 1000L
        const val PENDING = 0
        const val SYNCED = 1
        const val FAILED = 4
        const val OUTBOX_PENDING = "PENDING"
        const val OUTBOX_PROCESSING = "PROCESSING"
        const val OUTBOX_FAILED = "FAILED"

        private val REQUIRED_COLUMNS = mapOf(
            "records" to setOf(
                "entity", "local_id", "server_id", "payload", "sync_status", "sync_version",
                "last_mutation_id", "retry_count", "last_error", "updated_at"
            ),
            "outbox" to setOf(
                "id", "entity", "local_id", "server_id", "operation", "payload", "status",
                "retry_count", "next_attempt_at", "last_error", "created_at", "updated_at",
                "deferred_operation", "deferred_payload"
            ),
            "server_versions" to setOf("entity", "local_id", "server_id", "sync_version", "updated_at"),
            "meta" to setOf("key", "value")
        )

        private val REQUIRED_INDEXES = mapOf(
            "records" to setOf("idx_records_sync", "idx_records_server"),
            "outbox" to setOf("idx_outbox_ready", "idx_outbox_processing"),
            "server_versions" to setOf("idx_server_versions_server")
        )
    }

    private val appContext = context.applicationContext

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE records (entity TEXT NOT NULL, local_id INTEGER NOT NULL, server_id INTEGER NOT NULL DEFAULT 0, payload BLOB NOT NULL, sync_status INTEGER NOT NULL DEFAULT 0, sync_version INTEGER NOT NULL DEFAULT 0, last_mutation_id TEXT, retry_count INTEGER NOT NULL DEFAULT 0, last_error TEXT, updated_at INTEGER NOT NULL, PRIMARY KEY(entity, local_id))")
        db.execSQL("CREATE TABLE outbox (id INTEGER PRIMARY KEY AUTOINCREMENT, entity TEXT NOT NULL, local_id INTEGER NOT NULL, server_id INTEGER NOT NULL DEFAULT 0, operation TEXT NOT NULL, payload BLOB NOT NULL, status TEXT NOT NULL DEFAULT 'PENDING', retry_count INTEGER NOT NULL DEFAULT 0, next_attempt_at INTEGER NOT NULL DEFAULT 0, last_error TEXT, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, deferred_operation TEXT, deferred_payload BLOB, UNIQUE(entity, local_id))")
        db.execSQL("CREATE TABLE server_versions (entity TEXT NOT NULL, local_id INTEGER NOT NULL, server_id INTEGER NOT NULL DEFAULT 0, sync_version INTEGER NOT NULL DEFAULT 0, updated_at INTEGER NOT NULL, PRIMARY KEY(entity, local_id))")
        db.execSQL("CREATE INDEX idx_records_sync ON records(sync_status, retry_count, updated_at)")
        db.execSQL("CREATE INDEX idx_records_server ON records(entity, server_id)")
        db.execSQL("CREATE INDEX idx_outbox_ready ON outbox(status, next_attempt_at, id)")
        db.execSQL("CREATE INDEX idx_outbox_processing ON outbox(status, updated_at)")
        db.execSQL("CREATE INDEX idx_server_versions_server ON server_versions(entity, server_id)")
        db.execSQL("CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)")
        val seed = deviceSeed()
        putMeta(db, "device_seed", seed.toString())
        putMeta(db, "next_local_id", seed.toString())
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        try {
            if (oldVersion < 2) {
                addColumnIfMissing(db, "outbox", "next_attempt_at", "next_attempt_at INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_outbox_ready ON outbox(status, next_attempt_at, id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_records_server ON records(entity, server_id)")
            }
            if (oldVersion < 3) db.execSQL("CREATE INDEX IF NOT EXISTS idx_outbox_processing ON outbox(status, updated_at)")
            if (oldVersion < 4) db.execSQL("CREATE INDEX IF NOT EXISTS idx_records_entity_local ON records(entity, local_id)")
            if (oldVersion < 5) {
                addColumnIfMissing(db, "outbox", "deferred_operation", "deferred_operation TEXT")
                addColumnIfMissing(db, "outbox", "deferred_payload", "deferred_payload BLOB")
            }
            if (oldVersion < 6) {
                addColumnIfMissing(db, "records", "sync_version", "sync_version INTEGER NOT NULL DEFAULT 0")
                addColumnIfMissing(db, "records", "last_mutation_id", "last_mutation_id TEXT")
                db.execSQL("CREATE TABLE IF NOT EXISTS server_versions (entity TEXT NOT NULL, local_id INTEGER NOT NULL, server_id INTEGER NOT NULL DEFAULT 0, sync_version INTEGER NOT NULL DEFAULT 0, updated_at INTEGER NOT NULL, PRIMARY KEY(entity, local_id))")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_server_versions_server ON server_versions(entity, server_id)")
            }
            verifySchema(db)
        } catch (t: Throwable) {
            SafaLogger.error("LOCAL_DB_MIGRATION_FAILED", "Local database schema upgrade failed", t)
            throw t
        }
    }

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        try {
            verifySchema(db)
        } catch (t: Throwable) {
            SafaLogger.error("LOCAL_DB_SCHEMA_INVALID", "Local database schema verification failed", t)
            throw t
        }
    }

    private fun addColumnIfMissing(db: SQLiteDatabase, table: String, column: String, definition: String) {
        if (!hasColumn(db, table, column)) db.execSQL("ALTER TABLE $table ADD COLUMN $definition")
    }

    private fun hasColumn(db: SQLiteDatabase, table: String, column: String): Boolean =
        db.rawQuery("PRAGMA table_info($table)", null).use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            var found = false
            while (cursor.moveToNext()) {
                if (cursor.getString(nameIndex) == column) {
                    found = true
                    break
                }
            }
            found
        }

    private fun hasTable(db: SQLiteDatabase, table: String): Boolean =
        db.rawQuery("SELECT 1 FROM sqlite_master WHERE type='table' AND name=? LIMIT 1", arrayOf(table)).use { it.moveToFirst() }

    private fun hasIndex(db: SQLiteDatabase, table: String, index: String): Boolean =
        db.rawQuery("PRAGMA index_list($table)", null).use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            var found = false
            while (cursor.moveToNext()) {
                if (cursor.getString(nameIndex) == index) {
                    found = true
                    break
                }
            }
            found
        }

    private fun verifySchema(db: SQLiteDatabase) {
        REQUIRED_COLUMNS.forEach { (table, columns) ->
            check(hasTable(db, table)) { "Required local table is missing: $table" }
            columns.forEach { column ->
                check(hasColumn(db, table, column)) { "Required local column is missing: $table.$column" }
            }
        }
        REQUIRED_INDEXES.forEach { (table, indexes) ->
            indexes.forEach { index ->
                check(hasIndex(db, table, index)) { "Required local index is missing: $index" }
            }
        }
    }

    private fun deviceSeed(): Int {
        val uuid = DeviceSecurityHelper.getOrCreateDeviceUuid(appContext)
        val digest = MessageDigest.getInstance("SHA-256").digest(uuid.toByteArray(StandardCharsets.UTF_8))
        val value = ((digest[0].toInt() and 0x7F) shl 24) or ((digest[1].toInt() and 0xFF) shl 16) or ((digest[2].toInt() and 0xFF) shl 8) or (digest[3].toInt() and 0xFF)
        return value.coerceAtLeast(1)
    }

    fun nextLocalId(): Int = writableDatabase.transactionResult {
        val seed = getMeta(this, "device_seed")?.toLongOrNull() ?: deviceSeed().toLong()
        val current = getMeta(this, "next_local_id")?.toLongOrNull()?.takeIf { it >= seed } ?: seed
        val next = if (current >= Int.MAX_VALUE - 1L) seed else current + 1L
        putMeta(this, "next_local_id", next.toString())
        current.toInt()
    }

    /** Durable insert/update of a mutation. New edits never destroy an in-flight upload. */
    fun enqueue(entity: String, localId: Int, serverId: Int, operation: String, payload: String): Long = writableDatabase.transactionResult {
        val now = System.currentTimeMillis()
        val baseVersion = serverVersionInternal(this, entity, localId)
        val envelope = ensureMutationEnvelope(entity, localId, serverId, operation, payload, baseVersion)
        val existing = rawQuery("SELECT id,status,created_at FROM outbox WHERE entity=? AND local_id=? LIMIT 1", arrayOf(entity, localId.toString())).use { c ->
            if (c.moveToFirst()) Triple(c.getLong(0), c.getString(1), c.getLong(2)) else null
        }
        if (existing != null) {
            val (id, status, createdAt) = existing
            if (status == OUTBOX_PROCESSING) {
                execSQL("UPDATE outbox SET server_id=?, deferred_operation=?, deferred_payload=?, last_error=NULL, updated_at=? WHERE id=?", arrayOf<Any?>(serverId, operation.uppercase(), PayloadCipher.encrypt(envelope), now, id))
            } else {
                val values = ContentValues().apply {
                    put("server_id", serverId); put("operation", operation.uppercase()); put("payload", PayloadCipher.encrypt(envelope)); put("status", OUTBOX_PENDING)
                    put("retry_count", 0); put("next_attempt_at", 0L); putNull("last_error"); put("created_at", createdAt); put("updated_at", now); putNull("deferred_operation"); putNull("deferred_payload")
                }
                update("outbox", values, "id=?", arrayOf(id.toString()))
            }
            id
        } else {
            val values = ContentValues().apply {
                put("entity", entity); put("local_id", localId); put("server_id", serverId); put("operation", operation.uppercase()); put("payload", PayloadCipher.encrypt(envelope))
                put("status", OUTBOX_PENDING); put("retry_count", 0); put("next_attempt_at", 0L); putNull("last_error"); put("created_at", now); put("updated_at", now); putNull("deferred_operation"); putNull("deferred_payload")
            }
            insertOrThrow("outbox", null, values)
        }
    }

    fun upsertRecord(entity: String, localId: Int, serverId: Int, payload: String, syncStatus: Int = PENDING, retryCount: Int = 0, error: String? = null) {
        writableDatabase.transaction {
            var effectiveLocalId = localId
            if (serverId > 0) rawQuery("SELECT local_id FROM records WHERE entity=? AND server_id=? LIMIT 1", arrayOf(entity, serverId.toString())).use { c -> if (c.moveToFirst()) effectiveLocalId = c.getInt(0) }
            val now = System.currentTimeMillis()
            val payloadObject = runCatching { JSONObject(payload) }.getOrNull()
            val payloadVersion = payloadObject?.optLong("sync_version", -1L)?.takeIf { it >= 0L }
            val knownVersion = (payloadVersion ?: serverVersionInternal(this, entity, effectiveLocalId).toLong()).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            val values = ContentValues().apply {
                put("entity", entity); put("local_id", effectiveLocalId); put("server_id", serverId); put("payload", PayloadCipher.encrypt(payload)); put("sync_status", syncStatus)
                put("sync_version", knownVersion); put("last_mutation_id", payloadObject?.optJSONObject("_sync")?.optString("mutation_id")?.takeIf { it.isNotBlank() }); put("retry_count", retryCount); put("last_error", error); put("updated_at", now)
            }
            insertWithOnConflict("records", null, values, SQLiteDatabase.CONFLICT_REPLACE)
            if (serverId > 0) recordServerVersionInternal(this, entity, effectiveLocalId, serverId, knownVersion.toLong(), now)
            if (syncStatus != SYNCED) {
                val recoveryPayload = ensureMutationEnvelope(entity, effectiveLocalId, serverId, "RECOVER", payload, knownVersion)
                val outbox = ContentValues().apply {
                    put("entity", entity); put("local_id", effectiveLocalId); put("server_id", serverId); put("operation", "RECOVER"); put("payload", PayloadCipher.encrypt(recoveryPayload)); put("status", OUTBOX_PENDING)
                    put("retry_count", 0); put("next_attempt_at", 0L); putNull("last_error"); put("created_at", now); put("updated_at", now)
                }
                insertWithOnConflict("outbox", null, outbox, SQLiteDatabase.CONFLICT_IGNORE)
            }
        }
    }

    fun prepareOutboxPayload(outboxId: Long): String? = writableDatabase.transactionResult {
        rawQuery("SELECT entity,local_id,server_id,operation,payload FROM outbox WHERE id=? LIMIT 1", arrayOf(outboxId.toString())).use { c ->
            if (!c.moveToFirst()) return@transactionResult null
            val entity = c.getString(0); val localId = c.getInt(1); val serverId = c.getInt(2); val operation = c.getString(3); val current = PayloadCipher.decrypt(c.getBlob(4))
            val envelope = ensureMutationEnvelope(entity, localId, serverId, operation, current, serverVersionInternal(this, entity, localId))
            if (envelope != current) execSQL("UPDATE outbox SET payload=?, updated_at=? WHERE id=?", arrayOf<Any?>(PayloadCipher.encrypt(envelope), System.currentTimeMillis(), outboxId))
            envelope
        }
    }

    fun recordServerVersion(entity: String, localId: Int, serverId: Int, syncVersion: Int) {
        writableDatabase.transaction {
            recordServerVersionInternal(this, entity, localId, serverId, syncVersion.toLong(), System.currentTimeMillis())
            execSQL("UPDATE records SET server_id=?, sync_version=? WHERE entity=? AND local_id=? AND sync_status=?", arrayOf<Any?>(serverId, syncVersion, entity, localId, SYNCED))
        }
    }

    fun retry(entity: String, localId: Int) {
        val now = System.currentTimeMillis(); writableDatabase.transaction {
            execSQL("UPDATE records SET sync_status=?, retry_count=0, last_error=NULL, updated_at=? WHERE entity=? AND local_id=?", arrayOf<Any?>(PENDING, now, entity, localId))
            execSQL("UPDATE outbox SET status=?, retry_count=0, next_attempt_at=0, last_error=NULL, updated_at=? WHERE entity=? AND local_id=?", arrayOf<Any?>(OUTBOX_PENDING, now, entity, localId))
        }
    }

    fun markSynced(entity: String, localId: Int, serverId: Int, serverVersion: Int = -1) {
        writableDatabase.transaction {
            val now = System.currentTimeMillis(); val effectiveVersion = if (serverVersion >= 0) serverVersion else serverVersionInternal(this, entity, localId)
            recordServerVersionInternal(this, entity, localId, serverId, effectiveVersion.toLong(), now)
            var hasDeferred = false
            rawQuery("SELECT id,deferred_operation,deferred_payload FROM outbox WHERE entity=? AND local_id=? LIMIT 1", arrayOf(entity, localId.toString())).use { c ->
                if (c.moveToFirst()) {
                    val id = c.getLong(0); val deferredOperation = c.getString(1); val deferredPayload = c.getBlob(2)
                    if (!deferredOperation.isNullOrBlank() && deferredPayload != null) {
                        hasDeferred = true
                        execSQL("UPDATE outbox SET server_id=?, operation=?, payload=?, status=?, retry_count=0, next_attempt_at=0, last_error=NULL, deferred_operation=NULL, deferred_payload=NULL, updated_at=? WHERE id=?", arrayOf<Any?>(serverId, deferredOperation, deferredPayload, OUTBOX_PENDING, now, id))
                        execSQL("UPDATE records SET server_id=?, sync_version=?, sync_status=?, updated_at=? WHERE entity=? AND local_id=?", arrayOf<Any?>(serverId, effectiveVersion, PENDING, now, entity, localId))
                    } else delete("outbox", "id=?", arrayOf(id.toString()))
                }
            }
            if (!hasDeferred) rawQuery("SELECT payload FROM records WHERE entity=? AND local_id=? LIMIT 1", arrayOf(entity, localId.toString())).use { c ->
                if (c.moveToFirst()) {
                    val updatedPayload = applyServerMetadata(PayloadCipher.decrypt(c.getBlob(0)), serverId, effectiveVersion)
                    execSQL("UPDATE records SET server_id=?, sync_version=?, sync_status=?, retry_count=0, last_error=NULL, last_mutation_id=NULL, payload=?, updated_at=? WHERE entity=? AND local_id=?", arrayOf<Any?>(serverId, effectiveVersion, SYNCED, PayloadCipher.encrypt(updatedPayload), now, entity, localId))
                }
            }
        }
    }

    fun rebaseProcessingOutbox(outboxId: Long, serverId: Int, serverVersion: Int, serverSnapshot: String? = null): Boolean = writableDatabase.transactionResult { rebaseProcessingOutboxInternal(this, outboxId, serverId, serverVersion) }

    fun rebaseLatestProcessingOutbox(entity: String, localId: Int, serverId: Int, serverVersion: Int, serverSnapshot: String? = null): Boolean = writableDatabase.transactionResult {
        val id = rawQuery("SELECT id FROM outbox WHERE entity=? AND local_id=? AND status=? ORDER BY id DESC LIMIT 1", arrayOf(entity, localId.toString(), OUTBOX_PROCESSING)).use { c -> if (c.moveToFirst()) c.getLong(0) else null } ?: return@transactionResult false
        rebaseProcessingOutboxInternal(this, id, serverId, serverVersion)
    }

    private fun rebaseProcessingOutboxInternal(db: SQLiteDatabase, outboxId: Long, serverId: Int, serverVersion: Int): Boolean {
        return db.rawQuery("SELECT entity,local_id,operation,payload FROM outbox WHERE id=? AND status=? LIMIT 1", arrayOf(outboxId.toString(), OUTBOX_PROCESSING)).use { c ->
            if (!c.moveToFirst()) return@use false
            val entity = c.getString(0); val localId = c.getInt(1); val operation = c.getString(2); val localPayload = PayloadCipher.decrypt(c.getBlob(3))
            val rebased = runCatching { JSONObject(localPayload) }.getOrElse { JSONObject() }.apply {
                put("server_id", serverId); put("sync_version", serverVersion); put("_sync", JSONObject().apply { put("mutation_id", UUID.randomUUID().toString()); put("base_version", serverVersion); put("operation", operation.uppercase()) })
            }.toString()
            val now = System.currentTimeMillis()
            recordServerVersionInternal(db, entity, localId, serverId, serverVersion.toLong(), now)
            db.execSQL("UPDATE records SET server_id=?, sync_version=?, sync_status=?, last_error=?, updated_at=? WHERE entity=? AND local_id=?", arrayOf<Any?>(serverId, serverVersion, PENDING, "Rebased after server conflict", now, entity, localId))
            db.execSQL("UPDATE outbox SET server_id=?, deferred_operation=?, deferred_payload=?, last_error=?, updated_at=? WHERE id=?", arrayOf<Any?>(serverId, operation, PayloadCipher.encrypt(rebased), "Rebased after server conflict", now, outboxId))
            true
        }
    }

    fun markFailed(entity: String, localId: Int, message: String, retryable: Boolean) {
        val now = System.currentTimeMillis(); writableDatabase.transaction {
            if (!retryable) {
                val promoted = promoteDeferred(this, entity, localId, now, message)
                if (!promoted) {
                    execSQL("UPDATE records SET sync_status=?, last_error=?, updated_at=? WHERE entity=? AND local_id=?", arrayOf<Any?>(FAILED, message.take(500), now, entity, localId))
                    execSQL("UPDATE outbox SET status=?, last_error=?, updated_at=? WHERE entity=? AND local_id=?", arrayOf<Any?>(OUTBOX_FAILED, message.take(500), now, entity, localId))
                }
            } else {
                execSQL("UPDATE records SET retry_count=retry_count+1, last_error=?, updated_at=? WHERE entity=? AND local_id=?", arrayOf<Any?>(message.take(500), now, entity, localId))
                val retry = retryCount(entity, localId); val exhausted = retry >= MAX_RETRIES; val promoted = exhausted && promoteDeferred(this, entity, localId, now, message)
                if (!promoted) {
                    val delay = when (retry) { 1 -> 1_000L; 2 -> 2_000L; 3 -> 5_000L; 4 -> 15_000L; else -> 60_000L }
                    execSQL("UPDATE outbox SET status=?, retry_count=?, next_attempt_at=?, last_error=?, updated_at=? WHERE entity=? AND local_id=?", arrayOf<Any?>(if (exhausted) OUTBOX_FAILED else OUTBOX_PENDING, retry, if (exhausted) Long.MAX_VALUE else now + delay, message.take(500), now, entity, localId))
                    if (exhausted) execSQL("UPDATE records SET sync_status=?, last_error=?, updated_at=? WHERE entity=? AND local_id=?", arrayOf<Any?>(FAILED, "Maximum sync retries reached: ${message.take(420)}", now, entity, localId))
                }
            }
        }
    }

    private fun promoteDeferred(db: SQLiteDatabase, entity: String, localId: Int, now: Long, reason: String? = null): Boolean {
        db.rawQuery("SELECT id,server_id,deferred_operation,deferred_payload FROM outbox WHERE entity=? AND local_id=? LIMIT 1", arrayOf(entity, localId.toString())).use { c ->
            if (!c.moveToFirst()) return false
            val id = c.getLong(0); val serverId = c.getInt(1); val operation = c.getString(2); val payload = c.getBlob(3)
            if (operation.isNullOrBlank() || payload == null) return false
            db.execSQL("UPDATE outbox SET server_id=?, operation=?, payload=?, status=?, retry_count=0, next_attempt_at=0, last_error=?, deferred_operation=NULL, deferred_payload=NULL, updated_at=? WHERE id=?", arrayOf<Any?>(serverId, operation, payload, OUTBOX_PENDING, reason?.take(500), now, id))
            db.execSQL("UPDATE records SET sync_status=?, retry_count=0, last_error=?, updated_at=? WHERE entity=? AND local_id=?", arrayOf<Any?>(PENDING, reason?.take(500), now, entity, localId)); return true
        }
    }

    fun retryCount(entity: String, localId: Int): Int = readableDatabase.rawQuery("SELECT retry_count FROM records WHERE entity=? AND local_id=?", arrayOf(entity, localId.toString())).use { if (it.moveToFirst()) it.getInt(0) else 0 }
    fun serverVersion(entity: String, localId: Int): Int = readableDatabase.rawQuery("SELECT sync_version FROM server_versions WHERE entity=? AND local_id=? LIMIT 1", arrayOf(entity, localId.toString())).use { if (it.moveToFirst()) it.getInt(0) else 0 }
    fun hasPending(entity: String, localId: Int): Boolean = readableDatabase.rawQuery("SELECT 1 FROM outbox WHERE entity=? AND local_id=? AND status IN (?,?) LIMIT 1", arrayOf(entity, localId.toString(), OUTBOX_PENDING, OUTBOX_PROCESSING)).use { it.moveToFirst() }
    fun getRecordPayloads(entity: String): List<StoredRecord> = readableDatabase.rawQuery("SELECT entity,local_id,server_id,payload,sync_status,retry_count,last_error FROM records WHERE entity=? ORDER BY local_id", arrayOf(entity)).use { c -> buildList { while (c.moveToNext()) add(StoredRecord(c.getString(0), c.getInt(1), c.getInt(2), PayloadCipher.decrypt(c.getBlob(3)), c.getInt(4), c.getInt(5), c.getString(6))) } }

    fun getReadyOutbox(limit: Int = 50): List<OutboxRecord> {
        resetStaleProcessing()
        return writableDatabase.transactionResult {
            val now = System.currentTimeMillis(); val candidateLimit = (limit.coerceIn(1, 100) * 4).coerceAtMost(400); val candidates = mutableListOf<OutboxRecord>()
            rawQuery("SELECT id,entity,local_id,server_id,operation,payload,status,retry_count,last_error FROM outbox WHERE status=? AND next_attempt_at<=? AND retry_count<? ORDER BY CASE entity WHEN 'customers' THEN 10 WHEN 'suppliers' THEN 20 WHEN 'wallet_ledgers' THEN 30 WHEN 'supplier_deposits' THEN 40 WHEN 'wallet_batches' THEN 50 WHEN 'transactions' THEN 60 WHEN 'expenses_incomes' THEN 70 ELSE 100 END, id LIMIT ?", arrayOf(OUTBOX_PENDING, now.toString(), MAX_RETRIES.toString(), candidateLimit.toString())).use { c -> while (c.moveToNext()) candidates += OutboxRecord(c.getLong(0), c.getString(1), c.getInt(2), c.getInt(3), c.getString(4), PayloadCipher.decrypt(c.getBlob(5)), c.getString(6), c.getInt(7), c.getString(8)) }
            val candidateKeys = candidates.associateBy { it.entity to it.localId }; val selected = mutableListOf<OutboxRecord>(); var changed = true
            while (changed && selected.size < limit.coerceIn(1, 100)) {
                changed = false
                for (row in candidates) {
                    if (row in selected || !dependencyReady(row, candidateKeys, selected)) continue
                    selected += row; changed = true
                    if (selected.size >= limit.coerceIn(1, 100)) break
                }
            }
            selected.forEach { row -> execSQL("UPDATE outbox SET status=?, updated_at=? WHERE id=? AND status=?", arrayOf<Any?>(OUTBOX_PROCESSING, now, row.id, OUTBOX_PENDING)) }
            selected
        }
    }

    private fun dependencyReady(row: OutboxRecord, candidates: Map<Pair<String, Int>, OutboxRecord>, selected: List<OutboxRecord>): Boolean {
        val payload = runCatching { JSONObject(row.payload) }.getOrNull() ?: return true
        val dependencies = when (row.entity) {
            "supplier_deposits" -> listOf("suppliers" to payload.optInt("supplier_id", 0))
            "wallet_batches" -> listOf("wallet_ledgers" to payload.optInt("ledger_id", 0), "suppliers" to payload.optInt("supplier_id", 0), "supplier_deposits" to payload.optInt("supplier_deposit_id", 0))
            "transactions" -> listOf("customers" to payload.optInt("customer_id", 0), "suppliers" to payload.optInt("supplier_id", 0), "wallet_batches" to payload.optInt("wallet_batch_id", 0))
            else -> emptyList()
        }.filter { it.second > 0 }
        for ((parentEntity, parentLocalId) in dependencies) {
            val synced = readableDatabase.rawQuery("SELECT sync_status FROM records WHERE entity=? AND local_id=? LIMIT 1", arrayOf(parentEntity, parentLocalId.toString())).use { c -> c.moveToFirst() && c.getInt(0) == SYNCED }
            if (synced) continue
            val parent = candidates[parentEntity to parentLocalId] ?: return false
            if (!selected.any { it.entity == parentEntity && it.localId == parentLocalId }) return false
            if (parent.operation.equals("DELETE", ignoreCase = true) && !row.operation.equals("DELETE", ignoreCase = true)) return false
        }
        return true
    }

    fun markOutboxProcessing(ids: List<Long>) { if (ids.isEmpty()) return; val now = System.currentTimeMillis(); writableDatabase.transaction { ids.forEach { id -> execSQL("UPDATE outbox SET status=?, updated_at=? WHERE id=? AND status=?", arrayOf<Any?>(OUTBOX_PROCESSING, now, id, OUTBOX_PENDING)) } } }

    fun resetStaleProcessing(timeoutMs: Long = PROCESSING_TIMEOUT_MS) {
        val cutoff = System.currentTimeMillis() - timeoutMs.coerceAtLeast(1_000L); val now = System.currentTimeMillis(); writableDatabase.transaction {
            rawQuery("SELECT id FROM outbox WHERE status=? AND updated_at<?", arrayOf(OUTBOX_PROCESSING, cutoff.toString())).use { c ->
                val ids = buildList { while (c.moveToNext()) add(c.getLong(0)) }
                ids.forEach { id -> rawQuery("SELECT deferred_operation,deferred_payload FROM outbox WHERE id=?", arrayOf(id.toString())).use { d ->
                    if (d.moveToFirst() && !d.getString(0).isNullOrBlank() && d.getBlob(1) != null) execSQL("UPDATE outbox SET operation=?, payload=?, status=?, retry_count=0, next_attempt_at=0, last_error=COALESCE(last_error, 'Recovered abandoned sync item'), deferred_operation=NULL, deferred_payload=NULL, updated_at=? WHERE id=?", arrayOf<Any?>(d.getString(0), d.getBlob(1), OUTBOX_PENDING, now, id))
                    else execSQL("UPDATE outbox SET status=?, next_attempt_at=0, last_error=COALESCE(last_error, 'Recovered abandoned sync item'), updated_at=? WHERE id=?", arrayOf<Any?>(OUTBOX_PENDING, now, id))
                } }
            }
        }
    }

    fun resetProcessing() {
        val now = System.currentTimeMillis(); writableDatabase.transaction {
            rawQuery("SELECT id FROM outbox WHERE status=?", arrayOf(OUTBOX_PROCESSING)).use { c ->
                val ids = buildList { while (c.moveToNext()) add(c.getLong(0)) }
                ids.forEach { id -> rawQuery("SELECT deferred_operation,deferred_payload FROM outbox WHERE id=?", arrayOf(id.toString())).use { d ->
                    if (d.moveToFirst() && !d.getString(0).isNullOrBlank() && d.getBlob(1) != null) execSQL("UPDATE outbox SET operation=?, payload=?, status=?, retry_count=0, next_attempt_at=0, last_error=NULL, deferred_operation=NULL, deferred_payload=NULL, updated_at=? WHERE id=?", arrayOf<Any?>(d.getString(0), d.getBlob(1), OUTBOX_PENDING, now, id))
                    else execSQL("UPDATE outbox SET status=?, next_attempt_at=0, updated_at=? WHERE id=?", arrayOf<Any?>(OUTBOX_PENDING, now, id))
                } }
            }
        }
    }

    fun deleteOutbox(id: Long): Int = writableDatabase.transactionResult {
        var promoted = false
        rawQuery("SELECT deferred_operation,deferred_payload FROM outbox WHERE id=?", arrayOf(id.toString())).use { c -> if (c.moveToFirst() && !c.getString(0).isNullOrBlank() && c.getBlob(1) != null) { execSQL("UPDATE outbox SET operation=?, payload=?, status=?, retry_count=0, next_attempt_at=0, last_error=NULL, deferred_operation=NULL, deferred_payload=NULL, updated_at=? WHERE id=?", arrayOf<Any?>(c.getString(0), c.getBlob(1), OUTBOX_PENDING, System.currentTimeMillis(), id)); promoted = true } }
        if (promoted) 1 else delete("outbox", "id=?", arrayOf(id.toString()))
    }

    fun outboxCount(): Int = readableDatabase.rawQuery("SELECT COUNT(*) FROM outbox WHERE status IN (?,?)", arrayOf(OUTBOX_PENDING, OUTBOX_FAILED)).use { if (it.moveToFirst()) it.getInt(0) else 0 }
    fun clearEntity(entity: String) { writableDatabase.transaction { delete("records", "entity=?", arrayOf(entity)); delete("outbox", "entity=?", arrayOf(entity)); delete("server_versions", "entity=?", arrayOf(entity)) } }
    private fun putMeta(db: SQLiteDatabase, key: String, value: String) { db.insertWithOnConflict("meta", null, ContentValues().apply { put("key", key); put("value", value) }, SQLiteDatabase.CONFLICT_REPLACE) }
    private fun getMeta(db: SQLiteDatabase, key: String): String? = db.rawQuery("SELECT value FROM meta WHERE key=?", arrayOf(key)).use { if (it.moveToFirst()) it.getString(0) else null }

    private fun ensureMutationEnvelope(entity: String, localId: Int, serverId: Int, operation: String, payload: String, baseVersion: Int): String {
        val objectPayload = runCatching { JSONObject(payload) }.getOrElse { JSONObject() }; val existing = objectPayload.optJSONObject("_sync")
        if (existing != null && existing.optString("mutation_id").isNotBlank()) return objectPayload.toString()
        objectPayload.put("server_id", serverId); objectPayload.put("sync_version", baseVersion)
        objectPayload.put("_sync", JSONObject().apply { put("mutation_id", UUID.randomUUID().toString()); put("base_version", if (serverId > 0) baseVersion else 0); put("operation", operation.uppercase()) })
        return objectPayload.toString()
    }

    private fun applyServerMetadata(payload: String, serverId: Int, syncVersion: Int): String {
        val objectPayload = runCatching { JSONObject(payload) }.getOrElse { JSONObject() }; objectPayload.put("server_id", serverId); objectPayload.put("sync_version", syncVersion); objectPayload.remove("_sync"); return objectPayload.toString()
    }

    private fun recordServerVersionInternal(db: SQLiteDatabase, entity: String, localId: Int, serverId: Int, syncVersion: Long, now: Long) {
        db.insertWithOnConflict("server_versions", null, ContentValues().apply { put("entity", entity); put("local_id", localId); put("server_id", serverId); put("sync_version", syncVersion); put("updated_at", now) }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    private fun serverVersionInternal(db: SQLiteDatabase, entity: String, localId: Int): Int = db.rawQuery("SELECT sync_version FROM server_versions WHERE entity=? AND local_id=? LIMIT 1", arrayOf(entity, localId.toString())).use { if (it.moveToFirst()) it.getInt(0) else 0 }

    private inline fun <T> SQLiteDatabase.transaction(block: SQLiteDatabase.() -> T): T { beginTransaction(); return try { val result = block(); setTransactionSuccessful(); result } finally { endTransaction() } }
    private inline fun <T> SQLiteDatabase.transactionResult(block: SQLiteDatabase.() -> T): T = transaction(block)

    data class StoredRecord(val entity: String, val localId: Int, val serverId: Int, val payload: String, val syncStatus: Int, val retryCount: Int, val error: String?)
    data class OutboxRecord(val id: Long, val entity: String, val localId: Int, val serverId: Int, val operation: String, val payload: String, val status: String, val retryCount: Int, val error: String?)
}

private object PayloadCipher {
    private const val ALIAS = "safa_local_payload_key_v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val KEYSTORE = "AndroidKeyStore"
    private const val IV_SIZE = 12
    private const val TAG_SIZE = 128
    private fun key(): SecretKey {
        var store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        if (!store.containsAlias(ALIAS)) {
            val generator = KeyGenerator.getInstance("AES", KEYSTORE)
            generator.init(android.security.keystore.KeyGenParameterSpec.Builder(ALIAS, android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or android.security.keystore.KeyProperties.PURPOSE_DECRYPT).setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE).setUserAuthenticationRequired(false).build())
            generator.generateKey(); store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        }
        return store.getKey(ALIAS, null) as SecretKey
    }
    fun encrypt(value: String): ByteArray { val cipher = Cipher.getInstance(TRANSFORMATION); cipher.init(Cipher.ENCRYPT_MODE, key()); return Base64.encode(cipher.iv + cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8)), Base64.NO_WRAP) }
    fun decrypt(value: ByteArray): String {
        try {
            val raw = Base64.decode(value, Base64.NO_WRAP); require(raw.size > IV_SIZE) { "Invalid encrypted payload" }
            val cipher = Cipher.getInstance(TRANSFORMATION); cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_SIZE, raw.copyOfRange(0, IV_SIZE)))
            return String(cipher.doFinal(raw.copyOfRange(IV_SIZE, raw.size)), StandardCharsets.UTF_8)
        } catch (_: Exception) { throw SecurityException("Unable to decrypt SAFA local data") }
    }
}
