package com.safa.account.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.safa.account.data.model.RemittanceTransaction
import com.safa.account.data.model.DailyRate
import com.safa.account.data.money.MoneyMath
import com.safa.account.data.repository.AppRepository
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Device-level contract tests for the durable local-first state machine. */
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
        store.upsertRecord("customers", 1001, 0, "{\"local_id\":1001,\"name\":\"Offline Customer\"}", LocalFirstStore.PENDING)
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
        store.upsertRecord("customers", 2001, 0, "{\"local_id\":2001,\"name\":\"Version 1\"}", LocalFirstStore.PENDING)
        val first = store.getReadyOutbox().single()
        assertEquals("RECOVER", first.operation)

        store.enqueue("customers", 2001, 7001, "UPDATE", "{\"local_id\":2001,\"name\":\"Version 2\"}")
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
        store.upsertRecord("customers", 3001, 0, "{\"local_id\":3001,\"name\":\"Parent\"}", LocalFirstStore.PENDING)
        store.enqueue("transactions", 3002, 0, "CREATE", "{\"local_id\":3002,\"customer_id\":3001,\"supplier_id\":0,\"wallet_batch_id\":0}")

        val ready = store.getReadyOutbox(10)
        assertEquals(2, ready.size)
        assertEquals("customers", ready[0].entity)
        assertEquals(3001, ready[0].localId)
        assertEquals("transactions", ready[1].entity)
        assertEquals(3002, ready[1].localId)
    }

    @Test
    fun customer_is_claimed_before_dependent_transaction_in_same_batch() {
        store.upsertRecord("customers", 4001, 0, "{\"local_id\":4001,\"name\":\"Parent\"}", LocalFirstStore.PENDING)
        store.enqueue("transactions", 4002, 0, "CREATE", "{\"local_id\":4002,\"customer_id\":4001,\"supplier_id\":0,\"wallet_batch_id\":0}")

        val ready = store.getReadyOutbox(10)
        assertEquals(2, ready.size)
        assertEquals("customers", ready[0].entity)
        assertEquals(4001, ready[0].localId)
        assertEquals("transactions", ready[1].entity)
        assertEquals(4002, ready[1].localId)
    }

    @Test
    fun transaction_waits_for_supplier_chain_when_supplier_is_missing() {
        store.enqueue("supplier_deposits", 5002, 0, "CREATE", "{\"local_id\":5002,\"supplier_id\":5001}")
        store.enqueue("wallet_batches", 5003, 0, "CREATE", "{\"local_id\":5003,\"ledger_id\":5004,\"supplier_id\":5001,\"supplier_deposit_id\":5002}")
        store.enqueue("transactions", 5005, 0, "CREATE", "{\"local_id\":5005,\"customer_id\":0,\"supplier_id\":5001,\"wallet_batch_id\":5003}")
        assertEquals(0, store.getReadyOutbox(10).size)
    }

    @Test
    fun financial_payload_is_canonical_before_encrypted_local_persistence() = runBlocking {
        LocalAccountBoundary.bind(context, 1)
        val repository = AppRepository(context)
        val localId = repository.insertTransaction(
            RemittanceTransaction(
                amountSar = MoneyMath.amount("0.30"),
                customerRate = MoneyMath.rate("32.12345"),
                supplierRate = MoneyMath.rate("32"),
                amountBdt = MoneyMath.amount("9.637"),
                sarCollected = MoneyMath.amount("-0.105"),
                bdtDisbursed = MoneyMath.amount("9.637")
            )
        )

        val row = store.getRecordPayloads("transactions").single { it.localId == localId }
        val payload = JSONObject(row.payload)
        assertEquals("0.30", payload.getString("amount_sar"))
        assertEquals("32.1235", payload.getString("customer_rate"))
        assertEquals("32.0000", payload.getString("supplier_rate"))
        assertEquals("9.64", payload.getString("amount_bdt"))
        assertEquals("-0.11", payload.getString("sar_collected"))
        assertEquals("9.64", payload.getString("bdt_disbursed"))
    }

    @Test
    fun dailyRatesSurviveRepositoryRecreationAsExactDecimals() = runBlocking {
        LocalAccountBoundary.bind(context, 1)
        val repository = AppRepository(context)
        repository.insertDailyRate(
            DailyRate(
                date = "2026-08-14",
                customerRate = MoneyMath.rate("32.12345"),
                supplierRate = MoneyMath.rate("32")
            )
        )

        val restored = AppRepository(context).getDailyRateByDate("2026-08-14")
        assertNotNull(restored)
        assertEquals("32.1235", restored!!.customerRate.toPlainString())
        assertEquals("32.0000", restored.supplierRate.toPlainString())
        assertTrue(store.getRecordPayloads("daily_rates").single().payload.contains("32.1235"))
    }

    @Test
    fun confirmed_delete_outbox_survives_close_and_reopen_without_ui() {
        store.upsertRecord(
            "customers",
            7001,
            9001,
            "{\"local_id\":7001,\"server_id\":9001,\"sync_version\":3,\"name\":\"Delete Me\",\"deleted_at\":1700000000000}",
            LocalFirstStore.PENDING
        )
        store.enqueue(
            "customers",
            7001,
            9001,
            "DELETE",
            "{\"local_id\":7001,\"server_id\":9001,\"sync_version\":3,\"deleted_at\":1700000000000}"
        )

        store.close()
        store = LocalFirstStore(context)
        val replay = store.getReadyOutbox().single()

        assertEquals("customers", replay.entity)
        assertEquals(7001, replay.localId)
        assertEquals(9001, replay.serverId)
        assertEquals("DELETE", replay.operation)
        assertTrue(JSONObject(replay.payload).getJSONObject("_sync").getString("mutation_id").isNotBlank())
    }

    @Test
    fun account_switch_is_blocked_while_account_a_has_unresolved_mutation() {
        assertEquals(LocalAccountBoundary.Result.BOUND, LocalAccountBoundary.bind(context, 11))
        store.upsertRecord("customers", 8101, 0, "{\"local_id\":8101,\"name\":\"Account A Offline\"}", LocalFirstStore.PENDING)

        val result = LocalAccountBoundary.bind(context, 22)

        assertEquals(LocalAccountBoundary.Result.BLOCKED_BY_PENDING_MUTATIONS, result)
        assertEquals(11, LocalAccountBoundary.currentAccountId(context))
        assertEquals(1, store.getRecordPayloads("customers").size)
        assertTrue(store.hasPending("customers", 8101))
    }

    @Test
    fun clean_account_switch_atomically_clears_old_account_cache_and_revisions() {
        assertEquals(LocalAccountBoundary.Result.BOUND, LocalAccountBoundary.bind(context, 31))
        store.upsertRecord(
            "customers",
            8201,
            9201,
            "{\"local_id\":8201,\"server_id\":9201,\"sync_version\":5,\"name\":\"Account A Cached\"}",
            LocalFirstStore.SYNCED
        )
        store.recordServerVersion("customers", 8201, 9201, 5)

        val result = LocalAccountBoundary.bind(context, 32)

        assertEquals(LocalAccountBoundary.Result.SWITCHED, result)
        assertEquals(32, LocalAccountBoundary.currentAccountId(context))
        assertTrue(store.getRecordPayloads("customers").isEmpty())
        assertEquals(0, store.serverVersion("customers", 8201))
    }

    @Test
    fun rebinding_same_account_never_clears_its_cache() {
        LocalAccountBoundary.bind(context, 41)
        store.upsertRecord("customers", 8301, 9301, "{\"local_id\":8301,\"server_id\":9301,\"name\":\"Keep\"}", LocalFirstStore.SYNCED)

        assertEquals(LocalAccountBoundary.Result.UNCHANGED, LocalAccountBoundary.bind(context, 41))
        assertEquals(1, store.getRecordPayloads("customers").size)
    }
}
