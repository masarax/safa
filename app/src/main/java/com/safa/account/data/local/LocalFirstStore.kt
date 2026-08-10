package com.safa.account.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.math.max

/** Encrypted local database metadata + crash-safe outbox. */
class LocalFirstStore(context: Context) : SQLiteOpenHelper(context.applicationContext, "safa_local.db", null, VERSION) {
    companion object {
        private const val VERSION = 2
        const val PENDING = 0
        const val SYNCED = 1
        const val FAILED = 4
        const val OUTBOX_PENDING = "PENDING"
        const val OUTBOX_PROCESSING = "PROCESSING"
        const val OUTBOX_FAILED = "FAILED"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE records (entity TEXT NOT NULL, local_id INTEGER NOT NULL, server_id INTEGER NOT NULL DEFAULT 0, payload BLOB NOT NULL, sync_status INTEGER NOT NULL DEFAULT 0, retry_count INTEGER NOT NULL DEFAULT 0, last_error TEXT, updated_at INTEGER NOT NULL, PRIMARY KEY(entity, local_id))")
        db.execSQL("CREATE TABLE outbox (id INTEGER PRIMARY KEY AUTOINCREMENT, entity TEXT NOT NULL, local_id INTEGER NOT NULL, server_id INTEGER NOT NULL DEFAULT 0, operation TEXT NOT NULL, payload BLOB NOT NULL, status TEXT NOT NULL DEFAULT 'PENDING', retry_count INTEGER NOT NULL DEFAULT 0, next_attempt_at INTEGER NOT NULL DEFAULT 0, last_error TEXT, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, UNIQUE(entity, local_id))")
        db.execSQL("CREATE INDEX idx_records_sync ON records(sync_status, retry_count, updated_at)")
        db.execSQL("CREATE INDEX idx_outbox_ready ON outbox(status, next_attempt_at, id)")
        db.execSQL("CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)")
        putMeta(db, "next_local_id", "1")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            // next_attempt_at was part of the v1 schema in the first production build;
            // keep this branch defensive for early development databases.
            try { db.execSQL("ALTER TABLE outbox ADD COLUMN next_attempt_at INTEGER NOT NULL DEFAULT 0") } catch (_: Exception) { }
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_outbox_ready ON outbox(status, next_attempt_at, id)")
        }
    }

    fun nextLocalId(): Int = writableDatabase.transactionResult {
        val current = getMeta(this, "next_local_id")?.toLongOrNull() ?: 1L
        putMeta(this, "next_local_id", (current + 1L).toString())
        current.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    fun upsertRecord(entity: String, localId: Int, serverId: Int, payload: String, syncStatus: Int = PENDING, retryCount: Int = 0, error: String? = null) {
        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put("entity", entity); put("local_id", localId); put("server_id", serverId)
            put("payload", PayloadCipher.encrypt(payload)); put("sync_status", syncStatus)
            put("retry_count", retryCount); put("last_error", error); put("updated_at", now)
        }
        writableDatabase.insertWithOnConflict("records", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun enqueue(entity: String, localId: Int, serverId: Int, operation: String, payload: String) {
        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put("entity", entity); put("local_id", localId); put("server_id", serverId); put("operation", operation)
            put("payload", PayloadCipher.encrypt(payload)); put("status", OUTBOX_PENDING); put("retry_count", 0)
            put("next_attempt_at", 0L); put("last_error", null as String?); put("created_at", now); put("updated_at", now)
        }
        writableDatabase.insertWithOnConflict("outbox", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun markSynced(entity: String, localId: Int, serverId: Int) {
        writableDatabase.execSQL("UPDATE records SET server_id=?, sync_status=?, retry_count=0, last_error=NULL, updated_at=? WHERE entity=? AND local_id=?", arrayOf(serverId, SYNCED, System.currentTimeMillis(), entity, localId))
        writableDatabase.execSQL("DELETE FROM outbox WHERE entity=? AND local_id=?", arrayOf(entity, localId))
    }

    fun markFailed(entity: String, localId: Int, message: String, retryable: Boolean) {
        val now = System.currentTimeMillis()
        val db = writableDatabase
        if (retryable) {
            db.execSQL("UPDATE records SET retry_count=retry_count+1, last_error=?, updated_at=? WHERE entity=? AND local_id=?", arrayOf(message.take(500), now, entity, localId))
            val retry = retryCount(entity, localId)
            val delay = when (retry) { 1 -> 1_000L; 2 -> 2_000L; 3 -> 5_000L; 4 -> 15_000L; else -> 60_000L }
            db.execSQL("UPDATE outbox SET status=?, retry_count=?, next_attempt_at=?, last_error=?, updated_at=? WHERE entity=? AND local_id=?", arrayOf(OUTBOX_PENDING, retry, now + delay, message.take(500), now, entity, localId))
        } else {
            db.execSQL("UPDATE records SET sync_status=?, last_error=?, updated_at=? WHERE entity=? AND local_id=?", arrayOf(FAILED, message.take(500), now, entity, localId))
            db.execSQL("UPDATE outbox SET status=?, last_error=?, updated_at=? WHERE entity=? AND local_id=?", arrayOf(OUTBOX_FAILED, message.take(500), now, entity, localId))
        }
    }

    fun retryCount(entity: String, localId: Int): Int = readableDatabase.rawQuery("SELECT retry_count FROM records WHERE entity=? AND local_id=?", arrayOf(entity, localId)).use { if (it.moveToFirst()) it.getInt(0) else 0 }

    fun getRecordPayloads(entity: String): List<StoredRecord> = readableDatabase.rawQuery("SELECT entity,local_id,server_id,payload,sync_status,retry_count,last_error FROM records WHERE entity=? ORDER BY local_id", arrayOf(entity)).use { c ->
        buildList { while (c.moveToNext()) add(StoredRecord(c.getString(0), c.getInt(1), c.getInt(2), PayloadCipher.decrypt(c.getBlob(3)), c.getInt(4), c.getInt(5), c.getString(6))) }
    }

    fun getReadyOutbox(limit: Int = 50): List<OutboxRecord> = readableDatabase.rawQuery("SELECT id,entity,local_id,server_id,operation,payload,status,retry_count,last_error FROM outbox WHERE status=? AND next_attempt_at<=? AND retry_count<5 ORDER BY id LIMIT ?", arrayOf(OUTBOX_PENDING, System.currentTimeMillis().toString(), limit.toString())).use { c ->
        buildList { while (c.moveToNext()) add(OutboxRecord(c.getLong(0), c.getString(1), c.getInt(2), c.getInt(3), c.getString(4), PayloadCipher.decrypt(c.getBlob(5)), c.getString(6), c.getInt(7), c.getString(8))) }
    }

    fun markOutboxProcessing(ids: List<Long>) {
        if (ids.isEmpty()) return
        writableDatabase.transaction { ids.forEach { execSQL("UPDATE outbox SET status=?, updated_at=? WHERE id=?", arrayOf(OUTBOX_PROCESSING, System.currentTimeMillis(), it)) } }
    }

    fun resetProcessing() { writableDatabase.execSQL("UPDATE outbox SET status=?, updated_at=? WHERE status=?", arrayOf(OUTBOX_PENDING, System.currentTimeMillis(), OUTBOX_PROCESSING)) }
    fun outboxCount(): Int = readableDatabase.rawQuery("SELECT COUNT(*) FROM outbox WHERE status IN (?,?)", arrayOf(OUTBOX_PENDING, OUTBOX_FAILED)).use { if (it.moveToFirst()) it.getInt(0) else 0 }
    fun clearEntity(entity: String) { writableDatabase.delete("records", "entity=?", arrayOf(entity)) }

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
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        if (!store.containsAlias(ALIAS)) {
            val generator = KeyGenerator.getInstance("AES", KEYSTORE)
            generator.init(android.security.keystore.KeyGenParameterSpec.Builder(ALIAS, android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or android.security.keystore.KeyProperties.PURPOSE_DECRYPT).setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE).setUserAuthenticationRequired(false).build())
            generator.generateKey()
        }
        return store.getKey(ALIAS, null) as SecretKey
    }

    fun encrypt(value: String): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        return Base64.encode(cipher.iv + cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8)), Base64.NO_WRAP)
    }

    fun decrypt(value: ByteArray): String {
        try {
            val raw = Base64.decode(value, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_SIZE, raw.copyOfRange(0, IV_SIZE)))
            return String(cipher.doFinal(raw.copyOfRange(IV_SIZE, raw.size)), StandardCharsets.UTF_8)
        } catch (_: Exception) {
            throw SecurityException("Unable to decrypt SAFA local data")
        }
    }
}
