package com.safa.account.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SyncSnapshotGuardTest {
    @Test fun olderSnapshotIsIgnoredWhenLocalRevisionIsNewer() = assertEquals(SyncSnapshotGuard.Decision.IGNORE, SyncSnapshotGuard.decide(4, 5, false))
    @Test fun equalRevisionReplayIsIdempotentlyApplicable() = assertEquals(SyncSnapshotGuard.Decision.APPLY, SyncSnapshotGuard.decide(5, 5, false))
    @Test fun pendingMutationAlwaysWinsOverIncomingSnapshot() = assertEquals(SyncSnapshotGuard.Decision.IGNORE, SyncSnapshotGuard.decide(99, 5, true))
    @Test fun isoTimestampPreservesInstant() {
        val expected = 1_700_000_000_000L
        assertEquals(expected, SyncSnapshotGuard.parseTimestamp("2023-11-14T22:13:20Z"))
        assertEquals(expected, SyncSnapshotGuard.parseTimestamp("2023-11-15T01:13:20+03:00"))
    }
    @Test fun epochSecondsAreNormalizedToMilliseconds() = assertEquals(1_700_000_000_000L, SyncSnapshotGuard.parseTimestamp(1_700_000_000L, Long.MAX_VALUE))
    @Test fun invalidTimestampDoesNotInventLocalState() = assertNull(SyncSnapshotGuard.parseTimestamp("not-a-timestamp", Long.MAX_VALUE))
}
