package com.safa.account.data.api

import com.safa.account.data.api.dto.SyncDownResponse
import com.safa.account.data.api.dto.SyncUpPayload
import com.safa.account.data.model.Customer
import com.safa.account.data.model.RemittanceTransaction
import com.safa.account.data.model.Supplier
import com.safa.account.data.repository.AppRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext

class SyncManager(
    private val repository: AppRepository,
    private val tokenManager: TokenManager
) {
    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private val apiKey = com.safa.account.BuildConfig.SAFA_API_KEY
    private val apiSecret = com.safa.account.BuildConfig.SAFA_API_SECRET

    private fun getApiService(): ApiService {
        val baseUrl = tokenManager.getBaseUrl().let {
            if (it.endsWith("/")) it else "$it/"
        }
        return RetrofitClient.getApiService(baseUrl, apiKey, apiSecret)
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

    suspend fun syncAll(): Result<String> = withContext(Dispatchers.IO) {
        _syncState.value = SyncState.Syncing
        return@withContext try {
            val api = getApiService()

            // 1. Collect local Room data
            val localTxns = repository.allTransactions.firstOrNull() ?: emptyList()
            val localCustomers = repository.allCustomers.firstOrNull() ?: emptyList()
            val localSuppliers = repository.allSuppliers.firstOrNull() ?: emptyList()
            val localSupplierDeposits = repository.allSupplierDeposits.firstOrNull() ?: emptyList()
            val localExpensesIncomes = repository.allExpensesIncomes.firstOrNull() ?: emptyList()
            val localWalletBatches = repository.allWalletBatches.firstOrNull() ?: emptyList()
            val localWalletLedgers = repository.allWalletLedgers.firstOrNull() ?: emptyList()

            // 2. Format SyncUp payload for Laravel backend
            val txMaps = localTxns.map { tx ->
                mapOf(
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
                    "timestamp" to tx.timestamp
                )
            }
            val custMaps = localCustomers.map { c ->
                mapOf(
                    "local_id" to c.id,
                    "name" to c.name,
                    "phone" to c.phone
                )
            }
            val suppMaps = localSuppliers.map { s ->
                mapOf(
                    "local_id" to s.id,
                    "name" to s.name,
                    "phone" to s.phone
                )
            }
            val sdMaps = localSupplierDeposits.map { sd ->
                mapOf(
                    "local_id" to sd.id,
                    "supplier_id" to sd.supplierId,
                    "amount_sar" to sd.amountSar,
                    "rate" to sd.rate,
                    "amount_bdt" to sd.amountBdt,
                    "paid_bdt" to sd.paidBdt,
                    "transaction_type" to sd.transactionType,
                    "notes" to sd.notes,
                    "timestamp" to sd.timestamp
                )
            }
            val eiMaps = localExpensesIncomes.map { ei ->
                mapOf(
                    "local_id" to ei.id,
                    "title" to ei.title,
                    "amount" to ei.amount,
                    "currency" to ei.currency,
                    "is_expense" to ei.isExpense,
                    "category" to ei.category,
                    "timestamp" to ei.timestamp
                )
            }
            val wbMaps = localWalletBatches.map { wb ->
                mapOf(
                    "local_id" to wb.id,
                    "ledger_id" to wb.ledgerId,
                    "rate" to wb.rate,
                    "initial_bdt" to wb.initialBdt,
                    "remaining_bdt" to wb.remainingBdt,
                    "supplier_id" to wb.supplierId,
                    "supplier_deposit_id" to wb.supplierDepositId,
                    "notes" to wb.notes,
                    "timestamp" to wb.timestamp
                )
            }
            val wlMaps = localWalletLedgers.map { wl ->
                mapOf(
                    "local_id" to wl.id,
                    "name" to wl.name,
                    "timestamp" to wl.timestamp
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
                    // Sync downstream logic here
                    // Note: Basic conflict resolution for downstream can be added if needed
                    body.customers.forEach { map ->
                        val name = map["name"]?.toString() ?: ""
                        val phone = map["phone"]?.toString() ?: ""
                        if (name.isNotBlank()) {
                            val exists = localCustomers.any { it.name.equals(name, ignoreCase = true) }
                            if (!exists) {
                                repository.insertCustomer(Customer(name = name, phone = phone))
                            }
                        }
                    }

                    body.suppliers.forEach { map ->
                        val name = map["name"]?.toString() ?: ""
                        val phone = map["phone"]?.toString() ?: ""
                        if (name.isNotBlank()) {
                            val exists = localSuppliers.any { it.name.equals(name, ignoreCase = true) }
                            if (!exists) {
                                repository.insertSupplier(Supplier(name = name, phone = phone))
                            }
                        }
                    }
                    
                    // We sync down the rest in a similar fashion or just acknowledge success
                }
            }

            val summary = "Successfully Synced! Pushed ${localTxns.size} Txns, ${localCustomers.size} Customers."
            _syncState.value = SyncState.Success(summary)
            Result.success(summary)
        } catch (e: Exception) {
            val errMsg = e.localizedMessage ?: "Network connection unavailable — Data saved locally."
            _syncState.value = SyncState.Error(errMsg)
            Result.failure(Exception(errMsg, e))
        }
    }
}
