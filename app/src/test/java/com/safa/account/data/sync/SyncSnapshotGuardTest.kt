package com.safa.account.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SyncSnapshotGuardTest {
    @Test fun newerLocalRevisionSurvivesDelayedOlderServerSnapshot() =
        assertEquals(SyncSnapshotGuard.Decision.IGNORE, SyncSnapshotGuard.decide(4, 5, false))

    @Test fun equalRevisionReplayIsIdempotentlyApplicable() =
        assertEquals(SyncSnapshotGuard.Decision.APPLY, SyncSnapshotGuard.decide(5, 5, false))

    @Test fun newerServerRevisionIsAllowedWhenNoLocalMutationExists() =
        assertEquals(SyncSnapshotGuard.Decision.APPLY, SyncSnapshotGuard.decide(6, 5, false))

    @Test fun localPendingMutationAlwaysWinsOverEvenNewerServerSnapshot() =
        assertEquals(SyncSnapshotGuard.Decision.IGNORE, SyncSnapshotGuard.decide(99, 5, true))

    @Test fun localTombstoneBlocksStaleServerResurrection() =
        assertEquals(SyncSnapshotGuard.Decision.IGNORE, SyncSnapshotGuard.decide(4, 5, true))

    @Test fun isoTimestampPreservesInstant() {
        val expected = 1_700_000_000_000L
        assertEquals(expected, SyncSnapshotGuard.parseTimestamp("2023-11-14T22:13:20Z", Long.MAX_VALUE))
        assertEquals(expected, SyncSnapshotGuard.parseTimestamp("2023-11-15T01:13:20+03:00", Long.MAX_VALUE))
    }

    @Test fun laravelSqlTimestampUsesExplicitUtcPolicy() {
        assertEquals(
            1_700_000_000_000L,
            SyncSnapshotGuard.parseTimestamp("2023-11-14 22:13:20", Long.MAX_VALUE)
        )
    }

    @Test fun zoneLessIsoTimestampUsesExplicitUtcPolicy() {
        assertEquals(
            1_700_000_000_000L,
            SyncSnapshotGuard.parseTimestamp("2023-11-14T22:13:20", Long.MAX_VALUE)
        )
    }

    @Test fun epochSecondsAreNormalizedToMilliseconds() =
        assertEquals(1_700_000_000_000L, SyncSnapshotGuard.parseTimestamp(1_700_000_000L, Long.MAX_VALUE))

    @Test fun invalidTimestampDoesNotInventLocalState() =
        assertNull(SyncSnapshotGuard.parseTimestamp("not-a-timestamp", Long.MAX_VALUE))
}
