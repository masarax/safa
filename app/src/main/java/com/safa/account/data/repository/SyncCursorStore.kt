package com.safa.account.data.repository

import android.content.Context

/** Durable non-sensitive sync protocol metadata, isolated per business account. */
internal class SyncCursorStore(context: Context) {
    data class State(val cursor: Long, val permissionScope: String?)

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun read(accountId: Int): State {
        if (accountId <= 0) return State(0L, null)
        val cursor = prefs.getLong(cursorKey(accountId), 0L).coerceAtLeast(0L)
        return State(
            cursor = cursor,
            // Cursor zero is a bootstrap position, not a durable permission
            // checkpoint. Always learn the current server scope from the next
            // cursor=0 response before advancing the checkpoint.
            permissionScope = if (cursor == 0L) null else prefs.getString(scopeKey(accountId), null)?.takeIf { it.isNotBlank() }
        )
    }

    fun commit(accountId: Int, cursor: Long, permissionScope: String) {
        require(accountId > 0) { "A valid account is required for sync cursor persistence" }
        require(cursor >= 0L) { "Sync cursor cannot be negative" }
        require(permissionScope.isNotBlank()) { "Sync permission scope is required" }

        synchronized(this) {
            val current = read(accountId)
            require(cursor >= current.cursor) { "Sync cursor regression detected" }
            if (cursor == current.cursor && permissionScope == current.permissionScope) return
            check(
                prefs.edit()
                    .putLong(cursorKey(accountId), cursor)
                    .putString(scopeKey(accountId), permissionScope)
                    .commit()
            ) { "Unable to persist sync cursor" }
        }
    }

    fun resetForPermissionScope(accountId: Int, permissionScope: String) {
        require(accountId > 0) { "A valid account is required for sync cursor persistence" }
        require(permissionScope.isNotBlank()) { "Sync permission scope is required" }
        synchronized(this) {
            check(
                prefs.edit()
                    .putLong(cursorKey(accountId), 0L)
                    .putString(scopeKey(accountId), permissionScope)
                    .commit()
            ) { "Unable to reset sync cursor" }
        }
    }

    private fun cursorKey(accountId: Int) = "account_${accountId}_cursor"
    private fun scopeKey(accountId: Int) = "account_${accountId}_permission_scope"

    private companion object {
        const val PREFS_NAME = "safa_sync_cursor_v1"
    }
}
