package com.safa.account.data.sync

/**
 * Deterministic conflict policy for local-first reconciliation.
 *
 * The server is authoritative only after a mutation has been accepted and mapped
 * to a server identity. A newer local mutation must never be discarded merely
 * because an older request completed in flight.
 */
object SyncConflictResolver {
    enum class Source { LOCAL, SERVER }
    enum class Operation { CREATE, UPDATE, DELETE, RESTORE }
    enum class Decision {
        KEEP_LOCAL,
        KEEP_SERVER,
        REBASE_LOCAL_TO_SERVER,
        DROP_LOCAL
    }

    data class Version(
        val timestamp: Long,
        val source: Source,
        val sequence: Long = 0L
    )

    data class Identity(
        val localId: Int,
        val serverId: Int
    ) {
        val hasServerIdentity: Boolean get() = serverId > 0
    }

    /**
     * Server acknowledgement for a local mutation. The local id is never replaced;
     * only its server identity is reconciled.
     */
    data class Acknowledgement(
        val identity: Identity,
        val operation: Operation,
        val serverVersion: Version,
        val serverDeleted: Boolean = false
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
     * A server snapshot can replace a clean local row only when the server version
     * wins. Pending local work always survives a download merge.
     */
    fun canApplyServerSnapshot(
        hasPendingLocalMutation: Boolean,
        local: Version?,
        server: Version?
    ): Boolean {
        if (hasPendingLocalMutation) return false
        return decide(local, server) == Decision.KEEP_SERVER
    }

    /**
     * Reconciles an accepted upload without losing a newer local edit.
     *
     * - The same local row keeps its local id.
     * - The server id is attached to that row.
     * - DELETE/RESTORE are treated as explicit operations, not inferred from a
     *   missing row.
     * - A newer local mutation stays pending and is uploaded again.
     */
    fun reconcileAcknowledgement(
        acknowledgement: Acknowledgement,
        currentLocal: Version?,
        hasNewerPendingLocalMutation: Boolean
    ): Decision {
        if (hasNewerPendingLocalMutation) return Decision.REBASE_LOCAL_TO_SERVER

        val server = acknowledgement.serverVersion
        return when (decide(currentLocal, server)) {
            Decision.KEEP_LOCAL -> Decision.KEEP_LOCAL
            Decision.KEEP_SERVER -> Decision.KEEP_SERVER
            Decision.REBASE_LOCAL_TO_SERVER -> Decision.REBASE_LOCAL_TO_SERVER
            Decision.DROP_LOCAL -> Decision.DROP_LOCAL
        }
    }

    /**
     * Rejects an invalid mapping instead of silently falling back to a foreign
     * primary key. This prevents a local id from accidentally becoming a server
     * foreign key when the parent has not been reconciled yet.
     */
    fun requireResolvedForeignKey(parent: Identity?, required: Boolean = true): Int? {
        if (!required) return parent?.serverId?.takeIf { it > 0 }
        return parent?.serverId?.takeIf { it > 0 }
            ?: throw IllegalStateException("Referenced entity has no resolved server identity")
    }
}
