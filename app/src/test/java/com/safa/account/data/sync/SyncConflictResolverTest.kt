package com.safa.account.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncConflictResolverTest {
    private fun local(ts: Long, sequence: Long = 0L) =
        SyncConflictResolver.Version(ts, SyncConflictResolver.Source.LOCAL, sequence)

    private fun server(ts: Long, sequence: Long = 0L) =
        SyncConflictResolver.Version(ts, SyncConflictResolver.Source.SERVER, sequence)

    @Test
    fun newerLocalMutationWins() {
        assertEquals(
            SyncConflictResolver.Decision.KEEP_LOCAL,
            SyncConflictResolver.decide(local(200), server(100))
        )
    }

    @Test
    fun newerServerSnapshotWins() {
        assertEquals(
            SyncConflictResolver.Decision.KEEP_SERVER,
            SyncConflictResolver.decide(local(100), server(200))
        )
    }

    @Test
    fun sequenceBreaksEqualTimestampTie() {
        assertEquals(
            SyncConflictResolver.Decision.KEEP_LOCAL,
            SyncConflictResolver.decide(local(100, 8), server(100, 7))
        )
        assertEquals(
            SyncConflictResolver.Decision.KEEP_SERVER,
            SyncConflictResolver.decide(local(100, 7), server(100, 8))
        )
    }

    @Test
    fun pendingLocalMutationBlocksServerSnapshot() {
        assertFalse(
            SyncConflictResolver.canApplyServerSnapshot(
                hasPendingLocalMutation = true,
                local = local(100),
                server = server(200)
            )
        )
    }

    @Test
    fun cleanLocalRecordCanAcceptNewerServerSnapshot() {
        assertTrue(
            SyncConflictResolver.canApplyServerSnapshot(
                hasPendingLocalMutation = false,
                local = local(100),
                server = server(200)
            )
        )
    }

    @Test
    fun acceptedCreateAttachesServerIdentityWithoutReplacingLocalIdentity() {
        val ack = SyncConflictResolver.Acknowledgement(
            identity = SyncConflictResolver.Identity(localId = 41, serverId = 9001),
            operation = SyncConflictResolver.Operation.CREATE,
            serverVersion = server(200)
        )

        assertEquals(41, ack.identity.localId)
        assertEquals(9001, ack.identity.serverId)
        assertTrue(ack.identity.hasServerIdentity)
    }

    @Test
    fun newerLocalEditAfterUploadIsRebasedAndMustBeUploadedAgain() {
        val ack = SyncConflictResolver.Acknowledgement(
            identity = SyncConflictResolver.Identity(localId = 41, serverId = 9001),
            operation = SyncConflictResolver.Operation.UPDATE,
            serverVersion = server(200)
        )

        assertEquals(
            SyncConflictResolver.Decision.REBASE_LOCAL_TO_SERVER,
            SyncConflictResolver.reconcileAcknowledgement(
                acknowledgement = ack,
                currentLocal = local(250),
                hasNewerPendingLocalMutation = true
            )
        )
    }

    @Test
    fun deleteAndRestoreRemainExplicitOperations() {
        val delete = SyncConflictResolver.Acknowledgement(
            identity = SyncConflictResolver.Identity(10, 20),
            operation = SyncConflictResolver.Operation.DELETE,
            serverVersion = server(100),
            serverDeleted = true
        )
        val restore = SyncConflictResolver.Acknowledgement(
            identity = SyncConflictResolver.Identity(10, 20),
            operation = SyncConflictResolver.Operation.RESTORE,
            serverVersion = server(200),
            serverDeleted = false
        )

        assertEquals(SyncConflictResolver.Operation.DELETE, delete.operation)
        assertEquals(SyncConflictResolver.Operation.RESTORE, restore.operation)
        assertTrue(delete.serverDeleted)
        assertFalse(restore.serverDeleted)
    }

    @Test
    fun unresolvedRequiredForeignKeyFailsClosed() {
        val unresolved = SyncConflictResolver.Identity(localId = 10, serverId = 0)

        try {
            SyncConflictResolver.requireResolvedForeignKey(unresolved, required = true)
            throw AssertionError("Expected unresolved foreign key to fail")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message!!.contains("server identity"))
        }
    }

    @Test
    fun optionalForeignKeyMayRemainNullUntilReconciled() {
        assertEquals(
            null,
            SyncConflictResolver.requireResolvedForeignKey(null, required = false)
        )
    }
}
