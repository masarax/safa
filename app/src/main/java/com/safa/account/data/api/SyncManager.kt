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

    // Default test keys used in backend .env
    private val apiKey = "safa_test_api_key_2026"
    private val apiSecret = "safa_test_secret_32byteslong_2026"

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

            // 2. Format SyncUp payload for Laravel backend
            val txMaps = localTxns.map { tx ->
                mapOf(
                    "local_id" to tx.id,
                    "type" to tx.status,
                    "amount" to tx.amountSar,
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

            val payload = SyncUpPayload(
                transactions = txMaps,
                customers = custMaps,
                suppliers = suppMaps
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
                    // Sync downstream transactions from server to Room DB
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
