package com.safa.account.data.repository

import android.content.Context

/** Durable non-sensitive sync protocol metadata, isolated per business account. */
internal class SyncCursorStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun read(accountId: Int): Long {
        if (accountId <= 0) return 0L
        return prefs.getLong(key(accountId), 0L).coerceAtLeast(0L)
    }

    fun advance(accountId: Int, cursor: Long) {
        require(accountId > 0) { "A valid account is required for sync cursor persistence" }
        require(cursor >= 0L) { "Sync cursor cannot be negative" }

        synchronized(this) {
            val current = read(accountId)
            require(cursor >= current) { "Sync cursor regression detected" }
            if (cursor == current) return
            check(prefs.edit().putLong(key(accountId), cursor).commit()) {
                "Unable to persist sync cursor"
            }
        }
    }

    private fun key(accountId: Int) = "account_$accountId"

    private companion object {
        const val PREFS_NAME = "safa_sync_cursor_v1"
    }
}
