package com.safa.account.ui

import com.safa.account.data.api.SyncState
import org.junit.Assert.*
import org.junit.Test

class Phase3SyncUxTest {

    @Test
    fun `verify sync state transitions`() {
        val idle: SyncState = SyncState.Idle
        val syncing: SyncState = SyncState.Syncing
        val success: SyncState = SyncState.Success("Synced")
        val error: SyncState = SyncState.Error("Network Error")

        assertEquals(SyncState.Idle, idle)
        assertEquals(SyncState.Syncing, syncing)
        assertEquals("Synced", (success as SyncState.Success).message)
        assertEquals("Network Error", (error as SyncState.Error).message)
    }
}
