package com.safa.account.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Base64
import com.safa.account.data.network.DeviceSecurityHelper
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypted local database metadata + durable crash-safe outbox. */
class LocalFirstStore(context: Context) : SQLiteOpenHelper(context.applicationContext, "safa_local.db", null, VERSION) {
    companion object {
        private const val VERSION = 5
        private const val MAX_RETRIES = 5
        private const val PROCESSING_TIMEOUT_MS = 2 * 60 * 1000L
        const val PENDING = 0
        const val SYNCED = 1
        const val FAILED = 4
        const val OUTBOX_PENDING = "PENDING"
        const val OUTBOX_PROCESSING = "PROCESSING"
        const val OUTBOX_FAILED = "FAILED"
    }

    private val appContext = context.applicationContext

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE records (entity TEXT NOT NULL, local_id INTEGER NOT NULL, server_id INTEGER NOT NULL DEFAULT 0, payload BLOB NOT NULL, sync_status INTEGER NOT NULL DEFAULT 0, retry_count INTEGER NOT NULL DEFAULT 0, last_error TEXT, updated_at INTEGER NOT NULL, PRIMARY KEY(entity, local_id))")
        db.execSQL("CREATE TABLE outbox (id INTEGER PRIMARY KEY AUTOINCREMENT, entity TEXT NOT NULL, local_id INTEGER NOT NULL, server_id INTEGER NOT NULL DEFAULT 0, operation TEXT NOT NULL, payload BLOB NOT NULL, status TEXT NOT NULL DEFAULT 'PENDING', retry_count INTEGER NOT NULL DEFAULT 0, next_attempt_at INTEGER NOT NULL DEFAULT 0, last_error TEXT, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, deferred_operation TEXT, deferred_payload BLOB, UNIQUE(entity, local_id))")
        db.execSQL("CREATE INDEX idx_records_sync ON records(sync_status, retry_count, updated_at)")
        db.execSQL("CREATE INDEX idx_records_server ON records(entity, server_id)")
        db.execSQL("CREATE INDEX idx_outbox_ready ON outbox(status, next_attempt_at, id)")
        db.execSQL("CREATE INDEX idx_outbox_processing ON outbox(status, updated_at)")
        db.execSQL("CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)")
        val seed = deviceSeed()
        putMeta(db, "device_seed", seed.toString())
        putMeta(db, "next_local_id", seed.toString())
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            try { db.execSQL("ALTER TABLE outbox ADD COLUMN next_attempt_at INTEGER NOT NULL DEFAULT 0") } catch (_: Exception) { }
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_outbox_ready ON outbox(status, next_attempt_at, id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_records_server ON records(entity, server_id)")
        }
        if (oldVersion < 3) db.execSQL("CREATE INDEX IF NOT EXISTS idx_outbox_processing ON outbox(status, updated_at)")
        if (oldVersion < 4) db.execSQL("CREATE INDEX IF NOT EXISTS idx_records_entity_local ON records(entity, local_id)")
        if (oldVersion < 5) {
            db.execSQL("ALTER TABLE outbox ADD COLUMN deferred_operation TEXT")
            db.execSQL("ALTER TABLE outbox ADD COLUMN deferred_payload BLOB")
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

    fun upsertRecord(entity: String, localId: Int, serverId: Int, payload: String, syncStatus: Int = PENDING, retryCount: Int = 0, error: String? = null) {
        writableDatabase.transaction {
            var effectiveLocalId = localId
            if (serverId > 0) {
                rawQuery("SELECT local_id FROM records WHERE entity=? AND server_id=? LIMIT 1", arrayOf(entity, serverId.toString())).use { c ->
                    if (c.moveToFirst()) effectiveLocalId = c.getInt(0)
                }
            }
            val now = System.currentTimeMillis()
            val values = ContentValues().apply {
                put("entity", entity); put("local_id", effectiveLocalId); put("server_id", serverId)
                put("payload", PayloadCipher.encrypt(payload)); put("sync_status", syncStatus)
                put("retry_count", retryCount); put("last_error", error); put("updated_at", now)
            }
            insertWithOnConflict("records", null, values, SQLiteDatabase.CONFLICT_REPLACE)
            if (syncStatus != SYNCED) {
                val outbox = ContentValues().apply {
                    put("entity", entity); put("local_id", effectiveLocalId); put("server_id", serverId)
                    put("operation", "RECOVER"); put("payload", PayloadCipher.encrypt(payload)); put("status", OUTBOX_PENDING)
                    put("retry_count", 0); put("next_attempt_at", 0L); putNull("last_error")
                    put("created_at", now); put("updated_at", now)
                }
                insertWithOnConflict("outbox", null, outbox, SQLiteDatabase.CONFLICT_IGNORE)
            }
        }
    }

    fun enqueue(entity: String, localId: Int, serverId: Int, operation: String, payload: String) {
        val now = System.currentTimeMillis()
        val encryptedPayload = PayloadCipher.encrypt(payload)
        writableDatabase.transaction {
            var processing = false
            rawQuery("SELECT id,status FROM outbox WHERE entity=? AND local_id=? LIMIT 1", arrayOf(entity, localId.toString())).use { c ->
                if (c.moveToFirst() && c.getString(1) == OUTBOX_PROCESSING) {
                    processing = true
                    execSQL("UPDATE outbox SET server_id=?, deferred_operation=?, deferred_payload=?, updated_at=? WHERE id=?", arrayOf<Any?>(serverId, operation, encryptedPayload, now, c.getLong(0)))
                }
            }
            if (!processing) {
                val values = ContentValues().apply {
                    put("entity", entity); put("local_id", localId); put("server_id", serverId); put("operation", operation)
                    put("payload", encryptedPayload); put("status", OUTBOX_PENDING); put("retry_count", 0); put("next_attempt_at", 0L)
                    putNull("last_error"); put("created_at", now); put("updated_at", now); putNull("deferred_operation"); putNull("deferred_payload")
                }
                insertWithOnConflict("outbox", null, values, SQLiteDatabase.CONFLICT_REPLACE)
            }
        }
    }

    fun hasPending(entity: String, localId: Int): Boolean = readableDatabase.rawQuery("SELECT 1 FROM records WHERE entity=? AND local_id=? AND sync_status!=? LIMIT 1", arrayOf(entity, localId.toString(), SYNCED.toString())).use { it.moveToFirst() }
    fun findLocalIdByServerId(entity: String, serverId: Int): Int? = readableDatabase.rawQuery("SELECT local_id FROM records WHERE entity=? AND server_id=? LIMIT 1", arrayOf(entity, serverId.toString())).use { if (it.moveToFirst()) it.getInt(0) else null }

    fun retry(entity: String, localId: Int) {
        val now = System.currentTimeMillis()
        writableDatabase.transaction {
            execSQL("UPDATE records SET sync_status=?, retry_count=0, last_error=NULL, updated_at=? WHERE entity=? AND local_id=?", arrayOf<Any?>(PENDING, now, entity, localId))
            execSQL("UPDATE outbox SET status=?, retry_count=0, next_attempt_at=0, last_error=NULL, updated_at=? WHERE entity=? AND local_id=?", arrayOf<Any?>(OUTBOX_PENDING, now, entity, localId))
        }
    }

    fun markSynced(entity: String, localId: Int, serverId: Int) {
        writableDatabase.transaction {
            val now = System.currentTimeMillis()
            execSQL("UPDATE records SET server_id=?, sync_status=?, retry_count=0, last_error=NULL, updated_at=? WHERE entity=? AND local_id=?", arrayOf<Any?>(serverId, SYNCED, now, entity, localId))
            rawQuery("SELECT id,deferred_operation,deferred_payload FROM outbox WHERE entity=? AND local_id=? LIMIT 1", arrayOf(entity, localId.toString())).use { c ->
                if (c.moveToFirst()) {
                    val id = c.getLong(0); val deferredOperation = c.getString(1); val deferredPayload = c.getBlob(2)
                    if (!deferredOperation.isNullOrBlank() && deferredPayload != null) {
                        execSQL("UPDATE outbox SET server_id=?, operation=?, payload=?, status=?, retry_count=0, next_attempt_at=0, last_error=NULL, deferred_operation=NULL, deferred_payload=NULL, updated_at=? WHERE id=?", arrayOf<Any?>(serverId, deferredOperation, deferredPayload, OUTBOX_PENDING, now, id))
                        execSQL("UPDATE records SET server_id=?, sync_status=?, updated_at=? WHERE entity=? AND local_id=?", arrayOf<Any?>(serverId, PENDING, now, entity, localId))
                    } else delete("outbox", "id=?", arrayOf(id.toString()))
                }
            }
        }
    }

    fun markFailed(entity: String, localId: Int, message: String, retryable: Boolean) {
        val now = System.currentTimeMillis()
        writableDatabase.transaction {
            if (!retryable) {
                val promoted = promoteDeferred(this, entity, localId, now, message)
                if (!promoted) {
                    execSQL("UPDATE records SET sync_status=?, last_error=?, updated_at=? WHERE entity=? AND local_id=?", arrayOf<Any?>(FAILED, message.take(500), now, entity, localId))
                    execSQL("UPDATE outbox SET status=?, last_error=?, updated_at=? WHERE entity=? AND local_id=?", arrayOf<Any?>(OUTBOX_FAILED, message.take(500), now, entity, localId))
                }
            } else {
                execSQL("UPDATE records SET retry_count=retry_count+1, last_error=?, updated_at=? WHERE entity=? AND local_id=?", arrayOf<Any?>(message.take(500), now, entity, localId))
                val retry = retryCount(entity, localId)
                val exhausted = retry >= MAX_RETRIES
                val promoted = exhausted && promoteDeferred(this, entity, localId, now, message)
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
            db.execSQL("UPDATE records SET sync_status=?, retry_count=0, last_error=?, updated_at=? WHERE entity=? AND local_id=?", arrayOf<Any?>(PENDING, reason?.take(500), now, entity, localId))
            return true
        }
    }

    fun retryCount(entity: String, localId: Int): Int = readableDatabase.rawQuery("SELECT retry_count FROM records WHERE entity=? AND local_id=?", arrayOf(entity, localId.toString())).use { if (it.moveToFirst()) it.getInt(0) else 0 }
    fun getRecordPayloads(entity: String): List<StoredRecord> = readableDatabase.rawQuery("SELECT entity,local_id,server_id,payload,sync_status,retry_count,last_error FROM records WHERE entity=? ORDER BY local_id", arrayOf(entity)).use { c -> buildList { while (c.moveToNext()) add(StoredRecord(c.getString(0), c.getInt(1), c.getInt(2), PayloadCipher.decrypt(c.getBlob(3)), c.getInt(4), c.getInt(5), c.getString(6))) } }

    fun getReadyOutbox(limit: Int = 50): List<OutboxRecord> {
        resetStaleProcessing()
        return writableDatabase.transactionResult {
            val now = System.currentTimeMillis(); val rows = mutableListOf<OutboxRecord>()
            rawQuery("SELECT id,entity,local_id,server_id,operation,payload,status,retry_count,last_error FROM outbox WHERE status=? AND next_attempt_at<=? AND retry_count<? ORDER BY id LIMIT ?", arrayOf(OUTBOX_PENDING, now.toString(), MAX_RETRIES.toString(), limit.coerceIn(1, 100).toString())).use { c ->
                while (c.moveToNext()) rows += OutboxRecord(c.getLong(0), c.getString(1), c.getInt(2), c.getInt(3), c.getString(4), PayloadCipher.decrypt(c.getBlob(5)), c.getString(6), c.getInt(7), c.getString(8))
            }
            rows.forEach { row -> execSQL("UPDATE outbox SET status=?, updated_at=? WHERE id=? AND status=?", arrayOf<Any?>(OUTBOX_PROCESSING, now, row.id, OUTBOX_PENDING)) }
            rows
        }
    }

    fun markOutboxProcessing(ids: List<Long>) {
        if (ids.isEmpty()) return
        val now = System.currentTimeMillis()
        writableDatabase.transaction { ids.forEach { id -> execSQL("UPDATE outbox SET status=?, updated_at=? WHERE id=? AND status=?", arrayOf<Any?>(OUTBOX_PROCESSING, now, id, OUTBOX_PENDING)) } }
    }

    fun resetStaleProcessing(timeoutMs: Long = PROCESSING_TIMEOUT_MS) {
        val cutoff = System.currentTimeMillis() - timeoutMs.coerceAtLeast(1_000L); val now = System.currentTimeMillis()
        writableDatabase.transaction {
            rawQuery("SELECT id FROM outbox WHERE status=? AND updated_at<?", arrayOf(OUTBOX_PROCESSING, cutoff.toString())).use { c ->
                val ids = buildList { while (c.moveToNext()) add(c.getLong(0)) }
                ids.forEach { id ->
                    rawQuery("SELECT deferred_operation,deferred_payload FROM outbox WHERE id=?", arrayOf(id.toString())).use { d ->
                        if (d.moveToFirst() && !d.getString(0).isNullOrBlank() && d.getBlob(1) != null) execSQL("UPDATE outbox SET operation=?, payload=?, status=?, retry_count=0, next_attempt_at=0, last_error=COALESCE(last_error, 'Recovered abandoned sync item'), deferred_operation=NULL, deferred_payload=NULL, updated_at=? WHERE id=?", arrayOf<Any?>(d.getString(0), d.getBlob(1), OUTBOX_PENDING, now, id))
                        else execSQL("UPDATE outbox SET status=?, next_attempt_at=0, last_error=COALESCE(last_error, 'Recovered abandoned sync item'), updated_at=? WHERE id=?", arrayOf<Any?>(OUTBOX_PENDING, now, id))
                    }
                }
            }
        }
    }

    fun resetProcessing() {
        val now = System.currentTimeMillis()
        writableDatabase.transaction {
            rawQuery("SELECT id FROM outbox WHERE status=?", arrayOf(OUTBOX_PROCESSING)).use { c ->
                val ids = buildList { while (c.moveToNext()) add(c.getLong(0)) }
                ids.forEach { id ->
                    rawQuery("SELECT deferred_operation,deferred_payload FROM outbox WHERE id=?", arrayOf(id.toString())).use { d ->
                        if (d.moveToFirst() && !d.getString(0).isNullOrBlank() && d.getBlob(1) != null) execSQL("UPDATE outbox SET operation=?, payload=?, status=?, retry_count=0, next_attempt_at=0, last_error=NULL, deferred_operation=NULL, deferred_payload=NULL, updated_at=? WHERE id=?", arrayOf<Any?>(d.getString(0), d.getBlob(1), OUTBOX_PENDING, now, id))
                        else execSQL("UPDATE outbox SET status=?, next_attempt_at=0, updated_at=? WHERE id=?", arrayOf<Any?>(OUTBOX_PENDING, now, id))
                    }
                }
            }
        }
    }

    fun deleteOutbox(id: Long): Int = writableDatabase.transactionResult {
        var promoted = false
        rawQuery("SELECT deferred_operation,deferred_payload FROM outbox WHERE id=?", arrayOf(id.toString())).use { c ->
            if (c.moveToFirst() && !c.getString(0).isNullOrBlank() && c.getBlob(1) != null) {
                execSQL("UPDATE outbox SET operation=?, payload=?, status=?, retry_count=0, next_attempt_at=0, last_error=NULL, deferred_operation=NULL, deferred_payload=NULL, updated_at=? WHERE id=?", arrayOf<Any?>(c.getString(0), c.getBlob(1), OUTBOX_PENDING, System.currentTimeMillis(), id))
                promoted = true
            }
        }
        if (promoted) 1 else delete("outbox", "id=?", arrayOf(id.toString()))
    }

    fun outboxCount(): Int = readableDatabase.rawQuery("SELECT COUNT(*) FROM outbox WHERE status IN (?,?)", arrayOf(OUTBOX_PENDING, OUTBOX_FAILED)).use { if (it.moveToFirst()) it.getInt(0) else 0 }
    fun clearEntity(entity: String) { writableDatabase.transaction { delete("records", "entity=?", arrayOf(entity)); delete("outbox", "entity=?", arrayOf(entity)) } }
    private fun putMeta(db: SQLiteDatabase, key: String, value: String) { db.insertWithOnConflict("meta", null, ContentValues().apply { put("key", key); put("value", value) }, SQLiteDatabase.CONFLICT_REPLACE) }
    private fun getMeta(db: SQLiteDatabase, key: String): String? = db.rawQuery("SELECT value FROM meta WHERE key=?", arrayOf(key)).use { if (it.moveToFirst()) it.getString(0) else null }

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
