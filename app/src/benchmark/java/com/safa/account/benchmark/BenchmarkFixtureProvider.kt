package com.safa.account.benchmark

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import com.safa.account.data.local.LocalFirstStore
import org.json.JSONObject

/**
 * Exists only in the non-production `benchmark` build type.
 *
 * Macrobenchmark executes in a separate APK/process, so it must not reflect into
 * production classes or attempt to open the target app's private files. This
 * provider performs deterministic seeding inside the target app process and is
 * physically absent from debug/release manifests.
 */
class BenchmarkFixtureProvider : ContentProvider() {
    companion object {
        const val AUTHORITY = "com.safa.account.benchmark-fixture"
        const val METHOD_SEED = "seed"
        private const val CUSTOMER_COUNT = 400
        private const val SUPPLIER_COUNT = 120
        private const val TRANSACTION_COUNT = 1_200
        private const val LEDGER_COUNT = 8
        private const val BATCHES_PER_LEDGER = 8
        private const val BASE_TIMESTAMP = 1_787_300_000L
    }

    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        if (method != METHOD_SEED) return Bundle.EMPTY
        val appContext = requireNotNull(context).applicationContext
        LocalFirstStore(appContext).use { store -> seed(store) }
        return Bundle().apply {
            putInt("customers", CUSTOMER_COUNT)
            putInt("suppliers", SUPPLIER_COUNT)
            putInt("transactions", TRANSACTION_COUNT)
            putInt("wallet_batches", LEDGER_COUNT * BATCHES_PER_LEDGER)
        }
    }

    private fun seed(store: LocalFirstStore) {
        fun put(entity: String, id: Int, payload: JSONObject) {
            store.upsertRecord(
                entity = entity,
                localId = id,
                serverId = id,
                payload = payload.toString(),
                syncStatus = LocalFirstStore.SYNCED,
            )
        }

        repeat(CUSTOMER_COUNT) { index ->
            val id = 10_000 + index
            put("customers", id, JSONObject()
                .put("local_id", id).put("server_id", id)
                .put("name", "Benchmark Customer ${index + 1}")
                .put("phone", "+966500${index.toString().padStart(6, '0')}")
                .put("address", "Benchmark District ${(index % 20) + 1}")
                .put("avatar_color", "4280391411").put("avatar_emoji", "👤")
                .put("timestamp", BASE_TIMESTAMP + index))
        }

        repeat(SUPPLIER_COUNT) { index ->
            val id = 20_000 + index
            put("suppliers", id, JSONObject()
                .put("local_id", id).put("server_id", id)
                .put("name", "Benchmark Supplier ${index + 1}")
                .put("phone", "+966511${index.toString().padStart(6, '0')}")
                .put("address", "Benchmark Market ${(index % 12) + 1}")
                .put("avatar_color", "4280391411").put("avatar_emoji", "🏢")
                .put("timestamp", BASE_TIMESTAMP + index))
        }

        repeat(TRANSACTION_COUNT) { index ->
            val id = 30_000 + index
            val customerId = 10_000 + (index % CUSTOMER_COUNT)
            val supplierId = 20_000 + (index % SUPPLIER_COUNT)
            val amountSar = 100 + (index % 900)
            val customerRate = 32.5 + ((index % 25) / 100.0)
            val supplierRate = customerRate - 0.15
            val amountBdt = amountSar * customerRate
            put("transactions", id, JSONObject()
                .put("local_id", id).put("server_id", id)
                .put("customer_id", customerId).put("supplier_id", supplierId)
                .put("amount_sar", amountSar.toString())
                .put("customer_rate", customerRate.toString())
                .put("supplier_rate", supplierRate.toString())
                .put("amount_bdt", amountBdt.toString())
                .put("sar_collected", amountSar.toString())
                .put("bdt_disbursed", amountBdt.toString())
                .put("receiver_name", "Benchmark Receiver ${index + 1}")
                .put("receiver_phone", "+880170${index.toString().padStart(7, '0')}")
                .put("receiver_account_type", "Bank")
                .put("receiver_account_no", "BENCH-${index.toString().padStart(8, '0')}")
                .put("status", if (index % 5 == 0) "Pending" else "Delivered")
                .put("operator_id", 1).put("wallet_batch_id", 0)
                .put("notes", "Synthetic benchmark data")
                .put("timestamp", BASE_TIMESTAMP + index))
        }

        repeat(LEDGER_COUNT) { ledgerIndex ->
            val ledgerId = 40_000 + ledgerIndex
            put("wallet_ledgers", ledgerId, JSONObject()
                .put("local_id", ledgerId).put("server_id", ledgerId)
                .put("name", "Benchmark Ledger ${ledgerIndex + 1}")
                .put("timestamp", BASE_TIMESTAMP + ledgerIndex))

            repeat(BATCHES_PER_LEDGER) { batchIndex ->
                val batchId = 50_000 + ledgerIndex * BATCHES_PER_LEDGER + batchIndex
                put("wallet_batches", batchId, JSONObject()
                    .put("local_id", batchId).put("server_id", batchId)
                    .put("ledger_id", ledgerId)
                    .put("rate", (32.1 + batchIndex / 100.0).toString())
                    .put("initial_bdt", "500000.00")
                    .put("remaining_bdt", (500000 - batchIndex * 25000).toString())
                    .put("supplier_id", 20_000 + (batchId % SUPPLIER_COUNT))
                    .put("supplier_deposit_id", 0)
                    .put("notes", "Synthetic benchmark stock")
                    .put("timestamp", BASE_TIMESTAMP + batchId))
            }
        }
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
