package com.safa.account.data.sync

/**
 * Deterministic conflict policy for local-first reconciliation.
 *
 * The server remains authoritative after a successful upload, while a newer local
 * mutation is never discarded merely because an older mutation completed in flight.
 * Timestamps are compared only as a tie-breaker; equal timestamps are resolved in
 * favour of the explicit source priority so reconciliation is deterministic.
 */
object SyncConflictResolver {
    enum class Source { LOCAL, SERVER }
    enum class Decision { KEEP_LOCAL, KEEP_SERVER }

    data class Version(
        val timestamp: Long,
        val source: Source,
        val sequence: Long = 0L
    )

    fun decide(local: Version?, server: Version?): Decision {
        if (local == null && server == null) return Decision.KEEP_SERVER
        if (local == null) return Decision.KEEP_SERVER
        if (server == null) return Decision.KEEP_LOCAL

        return when {
            local.timestamp > server.timestamp -> Decision.KEEP_LOCAL
            server.timestamp > local.timestamp -> Decision.KEEP_SERVER
            local.sequence > server.sequence -> Decision.KEEP_LOCAL
            server.sequence > local.sequence -> Decision.KEEP_SERVER
            local.source == Source.LOCAL -> Decision.KEEP_LOCAL
            else -> Decision.KEEP_SERVER
        }
    }

    /**
     * Returns true when a server snapshot may safely replace a local record.
     * Pending local work must always survive a download merge.
     */
    fun canApplyServerSnapshot(hasPendingLocalMutation: Boolean, local: Version?, server: Version?): Boolean {
        if (hasPendingLocalMutation) return false
        return decide(local, server) == Decision.KEEP_SERVER
    }
}
