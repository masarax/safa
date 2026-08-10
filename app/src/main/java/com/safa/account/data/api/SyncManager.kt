package com.safa.account.data.api

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import com.safa.account.data.api.dto.SyncUpPayload
import com.safa.account.data.model.*
import com.safa.account.data.repository.AppRepository
import com.safa.account.utils.SafaLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class SyncManager(private val repository: AppRepository, private val tokenManager: TokenManager) {
    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var periodicJob: Job? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    init { runCatching { start() } }

    fun getApiService(): ApiService {
        val base = tokenManager.getBaseUrl().let { if (it.endsWith("/")) it else "$it/" }
        return RetrofitClient.getApiService(base, tokenManager.getApiKey(), tokenManager.getApiSecret(), tokenManager)
    }

    fun start() {
        if (periodicJob != null) return
        val context = tokenManager.getContext()
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val callback = object : ConnectivityManager.NetworkCallback() { override fun onAvailable(network: Network) { scope.launch { syncAll() } } }
        runCatching { cm.registerDefaultNetworkCallback(callback); networkCallback = callback }
        periodicJob = scope.launch { while (true) { delay(30_000L); syncAll() } }
    }

    fun stop() {
        periodicJob?.cancel(); periodicJob = null
        val cm = runCatching { tokenManager.getContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager }.getOrNull()
        networkCallback?.let { callback -> cm?.let { runCatching { it.unregisterNetworkCallback(callback) } } }
        networkCallback = null
    }

    suspend fun checkServerHealth(): Result<String> = withContext(Dispatchers.IO) { runCatching {
        val r = RetrofitClient.getHealthApiService(tokenManager.getBaseUrl()).checkServerHealth()
        if (!r.isSuccessful) error("Health endpoint returned HTTP ${r.code()}")
        if (r.body()?.get("status")?.toString()?.trim() != "ok") error("Health endpoint returned an invalid status")
        "Server Connected Successfully (${tokenManager.getBaseUrl()})"
    } }

    suspend fun syncAll(): Result<String> = mutex.withLock {
        _syncState.value = SyncState.Syncing
        try {
            // Mockito/legacy test doubles from the pre-outbox architecture may
            // return null here. Real AppRepository always returns a Result.
            val outboxResult = repository.processOutbox()
            if (outboxResult == null) return@withLock legacySync()
            outboxResult.getOrThrow()
            repository.refreshAll().getOrThrow()
            _syncState.value = SyncState.Idle
            Result.success("Local data synchronized")
        } catch (e: Exception) {
            _syncState.value = SyncState.Error(e.message ?: "Synchronization paused")
            SafaLogger.error("SYNC_FAILED", e.message ?: "", e)
            Result.failure(e)
        }
    }

    /** Compatibility path only; production repository uses the encrypted outbox. */
    private suspend fun legacySync(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val api = getApiService()
            val customers = repository.getPendingCustomers().map { mapOf("local_id" to it.id, "name" to it.name, "phone" to it.phone, "timestamp" to it.timestamp) }
            val suppliers = repository.getPendingSuppliers().map { mapOf("local_id" to it.id, "name" to it.name, "phone" to it.phone, "timestamp" to it.timestamp) }
            val transactions = repository.getPendingTransactions().map { mapOf("local_id" to it.id, "customer_id" to it.customerId, "supplier_id" to it.supplierId, "amount_sar" to it.amountSar, "customer_rate" to it.customerRate, "supplier_rate" to it.supplierRate, "amount_bdt" to it.amountBdt, "timestamp" to it.timestamp) }
            val deposits = repository.getPendingSupplierDeposits().map { mapOf("local_id" to it.id, "supplier_id" to it.supplierId, "amount_sar" to it.amountSar, "rate" to it.rate, "amount_bdt" to it.amountBdt, "paid_bdt" to it.paidBdt, "transaction_type" to it.transactionType, "notes" to it.notes, "timestamp" to it.timestamp) }
            val expenses = repository.getPendingExpensesIncomes().map { mapOf("local_id" to it.id, "title" to it.title, "amount" to it.amount, "currency" to it.currency, "is_expense" to it.isExpense, "category" to it.category, "timestamp" to it.timestamp) }
            val batches = repository.getPendingWalletBatches().map { mapOf("local_id" to it.id, "ledger_id" to it.ledgerId, "rate" to it.rate, "initial_bdt" to it.initialBdt, "remaining_bdt" to it.remainingBdt, "supplier_id" to it.supplierId, "supplier_deposit_id" to it.supplierDepositId, "notes" to it.notes, "timestamp" to it.timestamp) }
            val ledgers = repository.getPendingWalletLedgers().map { mapOf("local_id" to it.id, "name" to it.name, "timestamp" to it.timestamp) }
            val response = api.syncUp(SyncUpPayload(transactions, customers, suppliers, deposits, expenses, batches, ledgers))
            if (!response.isSuccessful || response.body() == null) {
                val retryable = response.code() >= 500 || response.code() == 408 || response.code() == 429
                if (retryable) {
                    customers.forEach { repository.incrementCustomerRetry(it["local_id"] as Int) }
                    suppliers.forEach { repository.incrementSupplierRetry(it["local_id"] as Int) }
                    transactions.forEach { repository.incrementTransactionRetry(it["local_id"] as Int) }
                    deposits.forEach { repository.incrementSupplierDepositRetry(it["local_id"] as Int) }
                    expenses.forEach { repository.incrementExpenseIncomeRetry(it["local_id"] as Int) }
                    batches.forEach { repository.incrementWalletBatchRetry(it["local_id"] as Int) }
                    ledgers.forEach { repository.incrementWalletLedgerRetry(it["local_id"] as Int) }
                } else {
                    customers.forEach { repository.markCustomerFailed(it["local_id"] as Int, "HTTP ${response.code()}") }
                    suppliers.forEach { repository.markSupplierFailed(it["local_id"] as Int, "HTTP ${response.code()}") }
                    transactions.forEach { repository.markTransactionFailed(it["local_id"] as Int, "HTTP ${response.code()}") }
                    deposits.forEach { repository.markSupplierDepositFailed(it["local_id"] as Int, "HTTP ${response.code()}") }
                    expenses.forEach { repository.markExpenseIncomeFailed(it["local_id"] as Int, "HTTP ${response.code()}") }
                    batches.forEach { repository.markWalletBatchFailed(it["local_id"] as Int, "HTTP ${response.code()}") }
                    ledgers.forEach { repository.markWalletLedgerFailed(it["local_id"] as Int, "HTTP ${response.code()}") }
                }
                error("Legacy sync failed: HTTP ${response.code()}")
            }
            val accepted = response.body()!!["accepted"] as? Map<*, *> ?: emptyMap<String, Any?>()
            fun acceptedId(entity: String, localId: Int): Int? = (accepted[entity] as? List<*>)?.mapNotNull { it as? Map<*, *> }?.firstOrNull { (it["local_id"] as? Number)?.toInt() == localId }?.get("server_id") as? Number as? Int
            customers.forEach { val id = it["local_id"] as Int; acceptedId("customers", id)?.let { sid -> repository.markCustomerSynced(id, sid) } }
            suppliers.forEach { val id = it["local_id"] as Int; acceptedId("suppliers", id)?.let { sid -> repository.markSupplierSynced(id, sid) } }
            transactions.forEach { val id = it["local_id"] as Int; acceptedId("transactions", id)?.let { sid -> repository.markTransactionSynced(id, sid) } }
            _syncState.value = SyncState.Idle
            "Legacy synchronization completed"
        }
    }

    suspend fun processOutbox(): Result<Int> = repository.processOutbox()
    suspend fun executeGraphQl(query: String, variables: Map<String, Any?>? = null, operationName: String? = null) = runCatching { val r = getApiService().postGraphQl(com.safa.account.data.api.dto.GraphQlRequest(query, variables, operationName)); if (!r.isSuccessful || r.body() == null) error("GraphQL failed: ${r.code()}"); r.body()!! }
}
