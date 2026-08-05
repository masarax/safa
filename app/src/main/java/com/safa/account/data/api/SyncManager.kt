package com.safa.account.data.api

import com.safa.account.data.repository.AppRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull

class SyncManager(
    private val repository: AppRepository,
    private val tokenManager: TokenManager
) {
    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    suspend fun checkServerHealth(): Result<String> {
        return try {
            val baseUrl = tokenManager.getBaseUrl()
            if (baseUrl.isBlank()) return Result.failure(Exception("Base URL not configured"))
            // Simple connectivity check — ping the base URL
            val client = okhttp3.OkHttpClient()
            val request = okhttp3.Request.Builder().url(baseUrl).get().build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                Result.success("Connected to $baseUrl")
            } else {
                Result.failure(Exception("Server responded with ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun syncAll(): Result<String> {
        _syncState.value = SyncState.Syncing
        return try {
            // Offline-first: collect local data and push to server
            val transactions = repository.allTransactions.firstOrNull() ?: emptyList()
            val customers = repository.allCustomers.firstOrNull() ?: emptyList()
            val suppliers = repository.allSuppliers.firstOrNull() ?: emptyList()

            // In a full implementation this would POST to /sync/up via Retrofit.
            // Skipping actual network call here; wired once real API credentials are set.
            val summary = "Sync prepared: ${transactions.size} txns, ${customers.size} customers, ${suppliers.size} suppliers"
            _syncState.value = SyncState.Success(summary)
            Result.success(summary)
        } catch (e: Exception) {
            _syncState.value = SyncState.Error(e.localizedMessage ?: "Unknown sync error")
            Result.failure(e)
        }
    }
}
