package com.safa.account.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Device-level contract tests for the durable local-first state machine.
 *
 * getReadyOutbox() returns the selected pre-claim snapshots while atomically
 * changing those rows to PROCESSING in SQLite. Parent rows selected earlier in
 * one batch may therefore unlock dependent rows in that same ordered batch.
 */
@RunWith(AndroidJUnit4::class)
class LocalFirstStoreRecoveryTest {

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
    fun pending_record_always_has_recovery_outbox() {
        store.upsertRecord(
            entity = "customers",
            localId = 1001,
            serverId = 0,
            payload = "{\"local_id\":1001,\"name\":\"Offline Customer\"}",
            syncStatus = LocalFirstStore.PENDING,
        )

        val ready = store.getReadyOutbox()

        assertEquals(1, ready.size)
        assertEquals("customers", ready.single().entity)
        assertEquals(1001, ready.single().localId)
        assertEquals("RECOVER", ready.single().operation)
        assertEquals(LocalFirstStore.OUTBOX_PENDING, ready.single().status)
        assertTrue(store.hasPending("customers", 1001))
    }

    @Test
    fun newer_edit_is_promoted_after_inflight_mutation_succeeds() {
        store.upsertRecord(
            entity = "customers",
            localId = 2001,
            serverId = 0,
            payload = "{\"local_id\":2001,\"name\":\"Version 1\"}",
            syncStatus = LocalFirstStore.PENDING,
        )

        val first = store.getReadyOutbox().single()
        assertEquals("RECOVER", first.operation)

        store.enqueue(
            entity = "customers",
            localId = 2001,
            serverId = 7001,
            operation = "UPDATE",
            payload = "{\"local_id\":2001,\"name\":\"Version 2\"}",
        )

        store.markSynced("customers", 2001, 7001)

        val promoted = store.getReadyOutbox().singleOrNull()
        assertNotNull(promoted)
        val promotedRecord = requireNotNull(promoted)
        assertEquals("UPDATE", promotedRecord.operation)
        assertEquals(2001, promotedRecord.localId)
        assertEquals(7001, promotedRecord.serverId)

        val payload = JSONObject(promotedRecord.payload)
        assertEquals(2001, payload.getInt("local_id"))
        assertEquals("Version 2", payload.getString("name"))
        assertEquals(7001, payload.getInt("server_id"))
        assertTrue(payload.has("sync_version"))
        val sync = payload.getJSONObject("_sync")
        assertEquals("UPDATE", sync.getString("operation"))
        assertTrue(sync.getString("mutation_id").isNotBlank())
        assertTrue(store.hasPending("customers", 2001))
    }

    @Test
    fun unsynced_customer_is_selected_before_dependent_transaction_in_same_batch() {
        store.upsertRecord(
            entity = "customers",
            localId = 3001,
            serverId = 0,
            payload = "{\"local_id\":3001,\"name\":\"Parent\"}",
            syncStatus = LocalFirstStore.PENDING,
        )
        store.enqueue(
            entity = "transactions",
            localId = 3002,
            serverId = 0,
            operation = "CREATE",
            payload = "{\"local_id\":3002,\"customer_id\":3001,\"supplier_id\":0,\"wallet_batch_id\":0}",
        )

        val ready = store.getReadyOutbox(10)

        assertEquals(2, ready.size)
        assertEquals("customers", ready[0].entity)
        assertEquals(3001, ready[0].localId)
        assertEquals("transactions", ready[1].entity)
        assertEquals(3002, ready[1].localId)
    }

    @Test
    fun customer_is_claimed_before_dependent_transaction_in_same_batch() {
        store.upsertRecord(
            entity = "customers",
            localId = 4001,
            serverId = 0,
            payload = "{\"local_id\":4001,\"name\":\"Parent\"}",
            syncStatus = LocalFirstStore.PENDING,
        )
        store.enqueue(
            entity = "transactions",
            localId = 4002,
            serverId = 0,
            operation = "CREATE",
            payload = "{\"local_id\":4002,\"customer_id\":4001,\"supplier_id\":0,\"wallet_batch_id\":0}",
        )

        val ready = store.getReadyOutbox(10)

        assertEquals(2, ready.size)
        assertEquals("customers", ready[0].entity)
        assertEquals(4001, ready[0].localId)
        assertEquals("transactions", ready[1].entity)
        assertEquals(4002, ready[1].localId)
    }

    @Test
    fun transaction_waits_for_supplier_chain_when_supplier_is_missing() {
        store.enqueue(
            entity = "supplier_deposits",
            localId = 5002,
            serverId = 0,
            operation = "CREATE",
            payload = "{\"local_id\":5002,\"supplier_id\":5001}",
        )
        store.enqueue(
            entity = "wallet_batches",
            localId = 5003,
            serverId = 0,
            operation = "CREATE",
            payload = "{\"local_id\":5003,\"ledger_id\":5004,\"supplier_id\":5001,\"supplier_deposit_id\":5002}",
        )
        store.enqueue(
            entity = "transactions",
            localId = 5005,
            serverId = 0,
            operation = "CREATE",
            payload = "{\"local_id\":5005,\"customer_id\":0,\"supplier_id\":5001,\"wallet_batch_id\":5003}",
        )

        val ready = store.getReadyOutbox(10)

        assertEquals(0, ready.size)
    }
}
