package com.safa.account.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Device-level tests for the conflict/recovery path that runs around an HTTP
 * request. These tests deliberately keep the network out of the test so the
 * durable SQLite state machine is verified independently.
 */
@RunWith(AndroidJUnit4::class)
class LocalFirstStoreConflictRecoveryTest {
    private lateinit var context: Context
    private lateinit var store: LocalFirstStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase("safa_local.db")
        store = LocalFirstStore(context)
    }

    @After
    fun tearDown() {
        store.close()
        context.deleteDatabase("safa_local.db")
    }

    @Test
    fun conflict_rebase_is_promoted_to_a_new_pending_mutation() {
        store.upsertRecord(
            entity = "customers",
            localId = 6101,
            serverId = 7101,
            payload = "{\"local_id\":6101,\"server_id\":7101,\"name\":\"Device A\"}",
            syncStatus = LocalFirstStore.PENDING,
        )

        val inFlight = store.getReadyOutbox().single()
        assertEquals(LocalFirstStore.OUTBOX_PROCESSING, inFlight.status)

        assertTrue(
            store.rebaseProcessingOutbox(
                outboxId = inFlight.id,
                serverId = 7101,
                serverVersion = 7,
                serverSnapshot = "{\"id\":7101,\"name\":\"Device B\"}",
            )
        )

        // The repository's conflict rejection path promotes deferred work back
        // into the normal pending queue atomically.
        store.markFailed(
            entity = "customers",
            localId = 6101,
            message = "CONFLICT_REBASED",
            retryable = false,
        )

        val retry = store.getReadyOutbox().single()
        assertEquals(LocalFirstStore.OUTBOX_PENDING, retry.status)
        assertEquals(7101, retry.serverId)
        assertEquals("RECOVER", retry.operation)
        assertTrue(retry.payload.contains("\"base_version\":7"))
        assertTrue(retry.payload.contains("\"operation\":\"RECOVER\""))
    }

    @Test
    fun processing_outbox_is_recovered_after_process_death() {
        store.upsertRecord(
            entity = "customers",
            localId = 6201,
            serverId = 7201,
            payload = "{\"local_id\":6201,\"server_id\":7201,\"name\":\"Crash Safe\"}",
            syncStatus = LocalFirstStore.PENDING,
        )

        val inFlight = store.getReadyOutbox().single()
        assertEquals(LocalFirstStore.OUTBOX_PROCESSING, inFlight.status)

        // Simulate a process that died after claiming the row. The recovery
        // scanner must make it eligible again instead of losing the mutation.
        store.writableDatabase.execSQL(
            "UPDATE outbox SET updated_at=? WHERE id=?",
            arrayOf(System.currentTimeMillis() - 10 * 60 * 1000L, inFlight.id),
        )

        val recovered = store.getReadyOutbox().single()
        assertEquals(LocalFirstStore.OUTBOX_PROCESSING, recovered.status)
        assertEquals(inFlight.id, recovered.id)
        assertEquals(6201, recovered.localId)
    }
}
