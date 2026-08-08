package com.safa.account.data.api

import com.safa.account.data.api.dto.SyncUpPayload
import com.safa.account.data.model.Customer
import com.safa.account.data.model.ExpenseIncome
import com.safa.account.data.model.RemittanceTransaction
import com.safa.account.data.model.Supplier
import com.safa.account.data.model.SupplierDeposit
import com.safa.account.data.model.WalletBatch
import com.safa.account.data.model.WalletLedger
import com.safa.account.data.repository.AppRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

class SyncManager(
    private val repository: AppRepository,
    private val tokenManager: TokenManager
) {
    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    fun getApiService(): ApiService {
        val baseUrl = tokenManager.getBaseUrl().let {
            if (it.endsWith("/")) it else "$it/"
        }
        val key = tokenManager.getApiKey()
        val sec = tokenManager.getApiSecret()
        return RetrofitClient.getApiService(baseUrl, key, sec, tokenManager)
    }

    suspend fun executeGraphQl(
        query: String,
        variables: Map<String, Any?>? = null,
        operationName: String? = null
    ): Result<com.safa.account.data.api.dto.GraphQlResponse> = withContext(Dispatchers.IO) {
        return@withContext try {
            val api = getApiService()
            val req = com.safa.account.data.api.dto.GraphQlRequest(query = query, variables = variables, operationName = operationName)
            val res = api.postGraphQl(req)
            if (res.isSuccessful && res.body() != null) {
                Result.success(res.body()!!)
            } else {
                Result.failure(Exception("GraphQL execution failed with status: ${res.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun checkServerHealth(): Result<String> = withContext(Dispatchers.IO) {
        return@withContext try {
            val baseUrl = tokenManager.getBaseUrl()
            if (baseUrl.isBlank()) return@withContext Result.failure(Exception("Base URL not configured"))

            val api = getApiService()
            val response = api.getRemoteConfig()
            if (response.isSuccessful) {
                Result.success("Server Connected Successfully ($baseUrl)")
            } else {
                Result.failure(Exception("Server returned status: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseDeletedAt(raw: Any?): Long? {
        if (raw == null) return null
        if (raw is Number) {
            val l = raw.toLong()
            return if (l < 2000000000L) l * 1000L else l
        }
        if (raw is String && raw.isNotBlank()) {
            raw.toLongOrNull()?.let { l ->
                return if (l < 2000000000L) l * 1000L else l
            }
            return try {
                SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).parse(raw)?.time
            } catch (e: Exception) {
                null
            }
        }
        return null
    }

    suspend fun syncAll(): Result<String> = withContext(Dispatchers.IO) {
        _syncState.value = SyncState.Syncing
        return@withContext try {
            val api = getApiService()

            // 1. Collect all local Room data (including soft-deleted records)
            val localTxns = repository.allTransactionsRaw.firstOrNull() ?: emptyList()
            val localCustomers = repository.allCustomersRaw.firstOrNull() ?: emptyList()
            val localSuppliers = repository.allSuppliersRaw.firstOrNull() ?: emptyList()
            val localSupplierDeposits = repository.allSupplierDepositsRaw.firstOrNull() ?: emptyList()
            val localExpensesIncomes = repository.allExpensesIncomesRaw.firstOrNull() ?: emptyList()
            val localWalletBatches = repository.allWalletBatchesRaw.firstOrNull() ?: emptyList()
            val localWalletLedgers = repository.allWalletLedgersRaw.firstOrNull() ?: emptyList()

            // 2. Format SyncUp payload for Laravel backend
            val txMaps = localTxns.map { tx ->
                mapOf<String, Any?>(
                    "local_id" to tx.id,
                    "type" to tx.status,
                    "amount" to tx.amountSar,
                    "customer_id" to tx.customerId,
                    "supplier_id" to tx.supplierId,
                    "amount_sar" to tx.amountSar,
                    "customer_rate" to tx.customerRate,
                    "supplier_rate" to tx.supplierRate,
                    "amount_bdt" to tx.amountBdt,
                    "receiver_name" to tx.receiverName,
                    "receiver_phone" to tx.receiverPhone,
                    "receiver_account_type" to tx.receiverAccountType,
                    "receiver_account_no" to tx.receiverAccountNo,
                    "wallet_batch_id" to tx.walletBatchId,
                    "notes" to tx.notes,
                    "timestamp" to tx.timestamp,
                    "deleted_at" to tx.deletedAt
                )
            }
            val custMaps = localCustomers.map { c ->
                mapOf<String, Any?>(
                    "local_id" to c.id,
                    "name" to c.name,
                    "phone" to c.phone,
                    "timestamp" to c.timestamp,
                    "deleted_at" to c.deletedAt
                )
            }
            val suppMaps = localSuppliers.map { s ->
                mapOf<String, Any?>(
                    "local_id" to s.id,
                    "name" to s.name,
                    "phone" to s.phone,
                    "timestamp" to s.timestamp,
                    "deleted_at" to s.deletedAt
                )
            }
            val sdMaps = localSupplierDeposits.map { sd ->
                mapOf<String, Any?>(
                    "local_id" to sd.id,
                    "supplier_id" to sd.supplierId,
                    "amount_sar" to sd.amountSar,
                    "rate" to sd.rate,
                    "amount_bdt" to sd.amountBdt,
                    "paid_bdt" to sd.paidBdt,
                    "transaction_type" to sd.transactionType,
                    "notes" to sd.notes,
                    "timestamp" to sd.timestamp,
                    "deleted_at" to sd.deletedAt
                )
            }
            val eiMaps = localExpensesIncomes.map { ei ->
                mapOf<String, Any?>(
                    "local_id" to ei.id,
                    "title" to ei.title,
                    "amount" to ei.amount,
                    "currency" to ei.currency,
                    "is_expense" to ei.isExpense,
                    "category" to ei.category,
                    "timestamp" to ei.timestamp,
                    "deleted_at" to ei.deletedAt
                )
            }
            val wbMaps = localWalletBatches.map { wb ->
                mapOf<String, Any?>(
                    "local_id" to wb.id,
                    "ledger_id" to wb.ledgerId,
                    "rate" to wb.rate,
                    "initial_bdt" to wb.initialBdt,
                    "remaining_bdt" to wb.remainingBdt,
                    "supplier_id" to wb.supplierId,
                    "supplier_deposit_id" to wb.supplierDepositId,
                    "notes" to wb.notes,
                    "timestamp" to wb.timestamp,
                    "deleted_at" to wb.deletedAt
                )
            }
            val wlMaps = localWalletLedgers.map { wl ->
                mapOf<String, Any?>(
                    "local_id" to wl.id,
                    "name" to wl.name,
                    "timestamp" to wl.timestamp,
                    "deleted_at" to wl.deletedAt
                )
            }

            val payload = SyncUpPayload(
                transactions = txMaps,
                customers = custMaps,
                suppliers = suppMaps,
                supplierDeposits = sdMaps,
                expensesIncomes = eiMaps,
                walletBatches = wbMaps,
                walletLedgers = wlMaps
            )

            // 3. Perform SyncUp POST
            val upRes = api.syncUp(payload)
            if (!upRes.isSuccessful) {
                val err = "SyncUp failed with HTTP ${upRes.code()}"
                _syncState.value = SyncState.Error(err)
                return@withContext Result.failure(Exception(err))
            }

            // 4. Perform SyncDown GET
            val downRes = api.syncDown()
            if (downRes.isSuccessful) {
                val body = downRes.body()
                if (body != null) {
                    // --- 4.1 Sync Down Customers ---
                    body.customers.forEach { map ->
                        val localId = (map["local_id"] as? Number)?.toInt() ?: (map["id"] as? Number)?.toInt() ?: 0
                        val name = map["name"]?.toString() ?: ""
                        val phone = map["phone"]?.toString() ?: ""
                        val ts = (map["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis()
                        val delAt = parseDeletedAt(map["deleted_at"])

                        if (localId > 0 && name.isNotBlank()) {
                            val localMatch = localCustomers.find { it.id == localId }
                            if (localMatch == null) {
                                repository.insertCustomer(
                                    Customer(id = localId, name = name, phone = phone, timestamp = ts, deletedAt = delAt)
                                )
                            } else if (ts >= localMatch.timestamp) {
                                repository.updateCustomer(
                                    localMatch.copy(name = name, phone = phone, timestamp = ts, deletedAt = delAt)
                                )
                            }
                        }
                    }

                    // --- 4.2 Sync Down Suppliers ---
                    body.suppliers.forEach { map ->
                        val localId = (map["local_id"] as? Number)?.toInt() ?: (map["id"] as? Number)?.toInt() ?: 0
                        val name = map["name"]?.toString() ?: ""
                        val phone = map["phone"]?.toString() ?: ""
                        val ts = (map["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis()
                        val delAt = parseDeletedAt(map["deleted_at"])

                        if (localId > 0 && name.isNotBlank()) {
                            val localMatch = localSuppliers.find { it.id == localId }
                            if (localMatch == null) {
                                repository.insertSupplier(
                                    Supplier(id = localId, name = name, phone = phone, timestamp = ts, deletedAt = delAt)
                                )
                            } else if (ts >= localMatch.timestamp) {
                                repository.updateSupplier(
                                    localMatch.copy(name = name, phone = phone, timestamp = ts, deletedAt = delAt)
                                )
                            }
                        }
                    }

                    // --- 4.3 Sync Down Remittance Transactions ---
                    body.transactions.forEach { map ->
                        val localId = (map["local_id"] as? Number)?.toInt() ?: (map["id"] as? Number)?.toInt() ?: 0
                        val customerId = (map["customer_id"] as? Number)?.toInt() ?: 0
                        val supplierId = (map["supplier_id"] as? Number)?.toInt() ?: 0
                        val amountSar = (map["amount_sar"] as? Number)?.toDouble() ?: (map["amount"] as? Number)?.toDouble() ?: 0.0
                        val customerRate = (map["customer_rate"] as? Number)?.toDouble() ?: 0.0
                        val supplierRate = (map["supplier_rate"] as? Number)?.toDouble() ?: 0.0
                        val amountBdt = (map["amount_bdt"] as? Number)?.toDouble() ?: 0.0
                        val receiverName = map["receiver_name"]?.toString() ?: ""
                        val receiverPhone = map["receiver_phone"]?.toString() ?: ""
                        val receiverAccountType = map["receiver_account_type"]?.toString() ?: ""
                        val receiverAccountNo = map["receiver_account_no"]?.toString() ?: ""
                        val status = map["type"]?.toString() ?: map["status"]?.toString() ?: "Pending"
                        val walletBatchId = (map["wallet_batch_id"] as? Number)?.toInt() ?: 0
                        val notes = map["notes"]?.toString() ?: ""
                        val ts = (map["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis()
                        val delAt = parseDeletedAt(map["deleted_at"])

                        if (localId > 0) {
                            val localMatch = localTxns.find { it.id == localId }
                            if (localMatch == null) {
                                repository.insertTransaction(
                                    RemittanceTransaction(
                                        id = localId,
                                        customerId = customerId,
                                        supplierId = supplierId,
                                        amountSar = amountSar,
                                        customerRate = customerRate,
                                        supplierRate = supplierRate,
                                        amountBdt = amountBdt,
                                        receiverName = receiverName,
                                        receiverPhone = receiverPhone,
                                        receiverAccountType = receiverAccountType,
                                        receiverAccountNo = receiverAccountNo,
                                        status = status,
                                        walletBatchId = walletBatchId,
                                        notes = notes,
                                        timestamp = ts,
                                        deletedAt = delAt
                                    )
                                )
                            } else if (ts >= localMatch.timestamp) {
                                repository.updateTransaction(
                                    localMatch.copy(
                                        customerId = customerId,
                                        supplierId = supplierId,
                                        amountSar = amountSar,
                                        customerRate = customerRate,
                                        supplierRate = supplierRate,
                                        amountBdt = amountBdt,
                                        receiverName = receiverName,
                                        receiverPhone = receiverPhone,
                                        receiverAccountType = receiverAccountType,
                                        receiverAccountNo = receiverAccountNo,
                                        status = status,
                                        walletBatchId = walletBatchId,
                                        notes = notes,
                                        timestamp = ts,
                                        deletedAt = delAt
                                    )
                                )
                            }
                        }
                    }

                    // --- 4.4 Sync Down Supplier Deposits ---
                    body.supplierDeposits.forEach { map ->
                        val localId = (map["local_id"] as? Number)?.toInt() ?: (map["id"] as? Number)?.toInt() ?: 0
                        val supplierId = (map["supplier_id"] as? Number)?.toInt() ?: 0
                        val amountSar = (map["amount_sar"] as? Number)?.toDouble() ?: 0.0
                        val rate = (map["rate"] as? Number)?.toDouble() ?: 0.0
                        val amountBdt = (map["amount_bdt"] as? Number)?.toDouble() ?: 0.0
                        val paidBdt = (map["paid_bdt"] as? Number)?.toDouble() ?: 0.0
                        val transactionType = map["transaction_type"]?.toString() ?: "SAR_GIVEN"
                        val notes = map["notes"]?.toString() ?: ""
                        val ts = (map["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis()
                        val delAt = parseDeletedAt(map["deleted_at"])

                        if (localId > 0) {
                            val localMatch = localSupplierDeposits.find { it.id == localId }
                            if (localMatch == null) {
                                repository.insertSupplierDeposit(
                                    SupplierDeposit(
                                        id = localId,
                                        supplierId = supplierId,
                                        amountSar = amountSar,
                                        rate = rate,
                                        amountBdt = amountBdt,
                                        paidBdt = paidBdt,
                                        transactionType = transactionType,
                                        notes = notes,
                                        timestamp = ts,
                                        deletedAt = delAt
                                    )
                                )
                            } else if (ts >= localMatch.timestamp) {
                                repository.updateSupplierDeposit(
                                    localMatch.copy(
                                        supplierId = supplierId,
                                        amountSar = amountSar,
                                        rate = rate,
                                        amountBdt = amountBdt,
                                        paidBdt = paidBdt,
                                        transactionType = transactionType,
                                        notes = notes,
                                        timestamp = ts,
                                        deletedAt = delAt
                                    )
                                )
                            }
                        }
                    }

                    // --- 4.5 Sync Down Expense Incomes ---
                    body.expensesIncomes.forEach { map ->
                        val localId = (map["local_id"] as? Number)?.toInt() ?: (map["id"] as? Number)?.toInt() ?: 0
                        val title = map["title"]?.toString() ?: ""
                        val amount = (map["amount"] as? Number)?.toDouble() ?: 0.0
                        val currency = map["currency"]?.toString() ?: "BDT"
                        val isExpense = (map["is_expense"] as? Boolean)
                            ?: (map["is_expense"]?.toString()?.toBooleanStrictOrNull()) ?: true
                        val category = map["category"]?.toString() ?: "General"
                        val ts = (map["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis()
                        val delAt = parseDeletedAt(map["deleted_at"])

                        if (localId > 0 && title.isNotBlank()) {
                            val localMatch = localExpensesIncomes.find { it.id == localId }
                            if (localMatch == null) {
                                repository.insertExpenseIncome(
                                    ExpenseIncome(
                                        id = localId,
                                        title = title,
                                        amount = amount,
                                        currency = currency,
                                        isExpense = isExpense,
                                        category = category,
                                        timestamp = ts,
                                        deletedAt = delAt
                                    )
                                )
                            } else if (ts >= localMatch.timestamp) {
                                repository.insertExpenseIncome(
                                    localMatch.copy(
                                        title = title,
                                        amount = amount,
                                        currency = currency,
                                        isExpense = isExpense,
                                        category = category,
                                        timestamp = ts,
                                        deletedAt = delAt
                                    )
                                )
                            }
                        }
                    }

                    // --- 4.6 Sync Down Wallet Ledgers ---
                    body.walletLedgers.forEach { map ->
                        val localId = (map["local_id"] as? Number)?.toInt() ?: (map["id"] as? Number)?.toInt() ?: 0
                        val name = map["name"]?.toString() ?: ""
                        val ts = (map["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis()
                        val delAt = parseDeletedAt(map["deleted_at"])

                        if (localId > 0 && name.isNotBlank()) {
                            val localMatch = localWalletLedgers.find { it.id == localId }
                            if (localMatch == null) {
                                repository.insertWalletLedger(
                                    WalletLedger(id = localId, name = name, timestamp = ts, deletedAt = delAt)
                                )
                            } else if (ts >= localMatch.timestamp) {
                                repository.updateWalletLedger(
                                    localMatch.copy(name = name, timestamp = ts, deletedAt = delAt)
                                )
                            }
                        }
                    }

                    // --- 4.7 Sync Down Wallet Batches ---
                    body.walletBatches.forEach { map ->
                        val localId = (map["local_id"] as? Number)?.toInt() ?: (map["id"] as? Number)?.toInt() ?: 0
                        val ledgerId = (map["ledger_id"] as? Number)?.toInt() ?: 0
                        val rate = (map["rate"] as? Number)?.toDouble() ?: 0.0
                        val initialBdt = (map["initial_bdt"] as? Number)?.toDouble() ?: 0.0
                        val remainingBdt = (map["remaining_bdt"] as? Number)?.toDouble() ?: 0.0
                        val supplierId = (map["supplier_id"] as? Number)?.toInt() ?: 0
                        val supplierDepositId = (map["supplier_deposit_id"] as? Number)?.toInt() ?: 0
                        val notes = map["notes"]?.toString() ?: ""
                        val ts = (map["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis()
                        val delAt = parseDeletedAt(map["deleted_at"])

                        if (localId > 0) {
                            val localMatch = localWalletBatches.find { it.id == localId }
                            if (localMatch == null) {
                                repository.insertWalletBatch(
                                    WalletBatch(
                                        id = localId,
                                        ledgerId = ledgerId,
                                        rate = rate,
                                        initialBdt = initialBdt,
                                        remainingBdt = remainingBdt,
                                        supplierId = supplierId,
                                        supplierDepositId = supplierDepositId,
                                        notes = notes,
                                        timestamp = ts,
                                        deletedAt = delAt
                                    )
                                )
                            } else if (ts >= localMatch.timestamp) {
                                repository.updateWalletBatch(
                                    localMatch.copy(
                                        ledgerId = ledgerId,
                                        rate = rate,
                                        initialBdt = initialBdt,
                                        remainingBdt = remainingBdt,
                                        supplierId = supplierId,
                                        supplierDepositId = supplierDepositId,
                                        notes = notes,
                                        timestamp = ts,
                                        deletedAt = delAt
                                    )
                                )
                            }
                        }
                    }
                }
            }

            val summary = "Successfully Synced! Pushed ${localTxns.size} Txns, ${localCustomers.size} Customers, ${localSuppliers.size} Suppliers."
            _syncState.value = SyncState.Success(summary)
            Result.success(summary)
        } catch (e: Exception) {
            val errMsg = e.localizedMessage ?: "Network connection unavailable — Data saved locally."
            _syncState.value = SyncState.Error(errMsg)
            Result.failure(Exception(errMsg, e))
        }
    }
}

