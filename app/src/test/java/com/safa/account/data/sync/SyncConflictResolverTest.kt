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
}
