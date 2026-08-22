package com.safa.account.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalFirstStoreMigrationTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(DB_NAME)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(DB_NAME)
    }

    @Test
    fun every_supported_legacy_version_upgrades_without_losing_records_or_outbox() {
        for (version in 1..5) {
            context.deleteDatabase(DB_NAME)
            createLegacyDatabase(version)

            LocalFirstStore(context).use { store ->
                val db = store.writableDatabase
                assertEquals(6, db.version)
                assertEquals(1, count(db, "records"))
                assertEquals(1, count(db, "outbox"))
                assertTrue(columns(db, "records").containsAll(REQUIRED_RECORD_COLUMNS))
                assertTrue(columns(db, "outbox").containsAll(REQUIRED_OUTBOX_COLUMNS))
                assertTrue(columns(db, "server_versions").containsAll(REQUIRED_SERVER_VERSION_COLUMNS))
                assertTrue(indexes(db, "records").containsAll(setOf("idx_records_sync", "idx_records_server")))
                assertTrue(indexes(db, "outbox").containsAll(setOf("idx_outbox_ready", "idx_outbox_processing")))
                assertTrue(indexes(db, "server_versions").contains("idx_server_versions_server"))
            }
        }
    }

    @Test
    fun already_present_expected_column_is_treated_as_idempotent_not_as_an_error() {
        createLegacyDatabase(version = 1, precreateNextAttemptAt = true)

        LocalFirstStore(context).use { store ->
            val db = store.writableDatabase
            assertEquals(6, db.version)
            assertTrue(columns(db, "outbox").contains("next_attempt_at"))
            assertEquals(1, count(db, "records"))
            assertEquals(1, count(db, "outbox"))
        }
    }

    @Test
    fun unexpected_legacy_schema_damage_fails_closed_and_does_not_advance_version() {
        createLegacyDatabase(version = 5, omitRecordLastError = true)
        val helper = LocalFirstStore(context)

        val failure = try {
            runCatching { helper.writableDatabase }.exceptionOrNull()
        } finally {
            helper.close()
        }

        assertNotNull(failure)
        SQLiteDatabase.openDatabase(context.getDatabasePath(DB_NAME).path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            assertEquals(5, db.version)
            assertTrue(!columns(db, "records").contains("last_error"))
            assertEquals(1, count(db, "records"))
            assertEquals(1, count(db, "outbox"))
        }
    }

    private fun createLegacyDatabase(
        version: Int,
        precreateNextAttemptAt: Boolean = false,
        omitRecordLastError: Boolean = false
    ) {
        require(version in 1..5)
        val path = context.getDatabasePath(DB_NAME)
        path.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(path, null).use { db ->
            val recordLastError = if (omitRecordLastError) "" else ", last_error TEXT"
            db.execSQL(
                "CREATE TABLE records (" +
                    "entity TEXT NOT NULL, local_id INTEGER NOT NULL, server_id INTEGER NOT NULL DEFAULT 0, " +
                    "payload BLOB NOT NULL, sync_status INTEGER NOT NULL DEFAULT 0, retry_count INTEGER NOT NULL DEFAULT 0" +
                    recordLastError + ", updated_at INTEGER NOT NULL, PRIMARY KEY(entity, local_id))"
            )
            db.execSQL(
                "CREATE TABLE outbox (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, entity TEXT NOT NULL, local_id INTEGER NOT NULL, " +
                    "server_id INTEGER NOT NULL DEFAULT 0, operation TEXT NOT NULL, payload BLOB NOT NULL, " +
                    "status TEXT NOT NULL DEFAULT 'PENDING', retry_count INTEGER NOT NULL DEFAULT 0, " +
                    "last_error TEXT, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, UNIQUE(entity, local_id))"
            )
            db.execSQL("CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)")
            db.execSQL("CREATE INDEX idx_records_sync ON records(sync_status, retry_count, updated_at)")

            if (version >= 2 || precreateNextAttemptAt) {
                db.execSQL("ALTER TABLE outbox ADD COLUMN next_attempt_at INTEGER NOT NULL DEFAULT 0")
            }
            if (version >= 2) {
                db.execSQL("CREATE INDEX idx_outbox_ready ON outbox(status, next_attempt_at, id)")
                db.execSQL("CREATE INDEX idx_records_server ON records(entity, server_id)")
            }
            if (version >= 3) db.execSQL("CREATE INDEX idx_outbox_processing ON outbox(status, updated_at)")
            if (version >= 4) db.execSQL("CREATE INDEX idx_records_entity_local ON records(entity, local_id)")
            if (version >= 5) {
                db.execSQL("ALTER TABLE outbox ADD COLUMN deferred_operation TEXT")
                db.execSQL("ALTER TABLE outbox ADD COLUMN deferred_payload BLOB")
            }

            val recordColumns = if (omitRecordLastError) {
                "entity,local_id,server_id,payload,sync_status,retry_count,updated_at"
            } else {
                "entity,local_id,server_id,payload,sync_status,retry_count,last_error,updated_at"
            }
            val recordValues = if (omitRecordLastError) {
                arrayOf<Any?>("customers", 101, 0, byteArrayOf(1, 2, 3), 0, 0, 1_700_000_000_000L)
            } else {
                arrayOf<Any?>("customers", 101, 0, byteArrayOf(1, 2, 3), 0, 0, null, 1_700_000_000_000L)
            }
            db.execSQL(
                "INSERT INTO records ($recordColumns) VALUES (${recordValues.joinToString(",") { "?" }})",
                recordValues
            )
            db.execSQL(
                "INSERT INTO outbox (entity,local_id,server_id,operation,payload,status,retry_count,last_error,created_at,updated_at) " +
                    "VALUES (?,?,?,?,?,?,?,?,?,?)",
                arrayOf<Any?>(
                    "customers", 101, 0, "CREATE", byteArrayOf(4, 5, 6), "PENDING", 0, null,
                    1_700_000_000_000L, 1_700_000_000_000L
                )
            )
            db.execSQL("INSERT INTO meta (key,value) VALUES ('device_seed','101')")
            db.execSQL("INSERT INTO meta (key,value) VALUES ('next_local_id','102')")
            db.version = version
        }
    }

    private fun count(db: SQLiteDatabase, table: String): Int =
        db.rawQuery("SELECT COUNT(*) FROM $table", null).use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }

    private fun columns(db: SQLiteDatabase, table: String): Set<String> =
        db.rawQuery("PRAGMA table_info($table)", null).use { cursor ->
            val index = cursor.getColumnIndexOrThrow("name")
            buildSet {
                while (cursor.moveToNext()) add(cursor.getString(index))
            }
        }

    private fun indexes(db: SQLiteDatabase, table: String): Set<String> =
        db.rawQuery("PRAGMA index_list($table)", null).use { cursor ->
            val index = cursor.getColumnIndexOrThrow("name")
            buildSet {
                while (cursor.moveToNext()) add(cursor.getString(index))
            }
        }

    companion object {
        private const val DB_NAME = "safa_local.db"
        private val REQUIRED_RECORD_COLUMNS = setOf(
            "entity", "local_id", "server_id", "payload", "sync_status", "sync_version",
            "last_mutation_id", "retry_count", "last_error", "updated_at"
        )
        private val REQUIRED_OUTBOX_COLUMNS = setOf(
            "id", "entity", "local_id", "server_id", "operation", "payload", "status", "retry_count",
            "next_attempt_at", "last_error", "created_at", "updated_at", "deferred_operation", "deferred_payload"
        )
        private val REQUIRED_SERVER_VERSION_COLUMNS = setOf(
            "entity", "local_id", "server_id", "sync_version", "updated_at"
        )
    }
}
