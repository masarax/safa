package com.safa.account.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase

/**
 * Enforces a single authenticated-account namespace for the durable local cache.
 *
 * SAFA intentionally keeps one account's business snapshot in safa_local.db at
 * a time. A clean account switch atomically discards cache/revision state before
 * binding the new account. If any mutation is unresolved, switching is rejected
 * so an account-A outbox row can never be uploaded with account-B credentials.
 */
object LocalAccountBoundary {
    private const val META_ACTIVE_ACCOUNT_ID = "active_account_id"
    private const val META_SYNC_CURSOR = "sync_cursor"

    enum class Result { BOUND, UNCHANGED, SWITCHED, BLOCKED_BY_PENDING_MUTATIONS }

    fun currentAccountId(context: Context): Int? = withStore(context) { db ->
        db.rawQuery("SELECT value FROM meta WHERE key=? LIMIT 1", arrayOf(META_ACTIVE_ACCOUNT_ID)).use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0).toIntOrNull()?.takeIf { it > 0 } else null
        }
    }

    fun hasUnresolvedMutations(context: Context): Boolean = withStore(context) { db ->
        unresolvedMutationCount(db) > 0
    }

    fun bind(context: Context, accountId: Int): Result {
        require(accountId > 0) { "accountId must be positive" }
        return withStore(context) { db ->
            db.beginTransaction()
            try {
                val current = db.rawQuery(
                    "SELECT value FROM meta WHERE key=? LIMIT 1",
                    arrayOf(META_ACTIVE_ACCOUNT_ID)
                ).use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0).toIntOrNull()?.takeIf { it > 0 } else null
                }

                val result = when {
                    current == accountId -> Result.UNCHANGED
                    current == null -> {
                        putAccount(db, accountId)
                        Result.BOUND
                    }
                    unresolvedMutationCount(db) > 0 -> Result.BLOCKED_BY_PENDING_MUTATIONS
                    else -> {
                        // Cache + revision/cursor metadata are account-owned. Keep
                        // only device/local-id seed metadata across account switches.
                        db.delete("records", null, null)
                        db.delete("outbox", null, null)
                        db.delete("server_versions", null, null)
                        db.delete("meta", "key=?", arrayOf(META_SYNC_CURSOR))
                        putAccount(db, accountId)
                        Result.SWITCHED
                    }
                }
                db.setTransactionSuccessful()
                result
            } finally {
                db.endTransaction()
            }
        }
    }

    /**
     * Authentication boundary: destroy every account-owned durable structure in
     * one SQLite transaction before credentials can be reused by another user.
     * Device/local-id seed metadata is intentionally retained; business/account
     * data, outbox mutations, revisions, cursor and active-account binding are not.
     */
    fun destroyAccountState(context: Context) {
        withStore(context) { db ->
            db.beginTransaction()
            try {
                db.delete("records", null, null)
                db.delete("outbox", null, null)
                db.delete("server_versions", null, null)
                db.delete("meta", "key IN (?,?)", arrayOf(META_ACTIVE_ACCOUNT_ID, META_SYNC_CURSOR))
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
    }

    fun clearBinding(context: Context) {
        withStore(context) { db ->
            db.delete("meta", "key IN (?,?)", arrayOf(META_ACTIVE_ACCOUNT_ID, META_SYNC_CURSOR))
        }
    }

    private fun unresolvedMutationCount(db: SQLiteDatabase): Int = db.rawQuery(
        "SELECT COUNT(*) FROM outbox WHERE status IN (?,?,?)",
        arrayOf(
            LocalFirstStore.OUTBOX_PENDING,
            LocalFirstStore.OUTBOX_PROCESSING,
            LocalFirstStore.OUTBOX_FAILED
        )
    ).use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else 0 }

    private fun putAccount(db: SQLiteDatabase, accountId: Int) {
        db.execSQL(
            "INSERT OR REPLACE INTO meta(key,value) VALUES(?,?)",
            arrayOf<Any>(META_ACTIVE_ACCOUNT_ID, accountId.toString())
        )
    }

    private inline fun <T> withStore(context: Context, block: (SQLiteDatabase) -> T): T {
        val store = LocalFirstStore(context.applicationContext)
        return try {
            block(store.writableDatabase)
        } finally {
            store.close()
        }
    }
}
