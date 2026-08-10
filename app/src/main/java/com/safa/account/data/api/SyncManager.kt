package com.safa.account.data.api

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
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

/** Local-first synchronization coordinator. */
class SyncManager(private val repository: AppRepository, private val tokenManager: TokenManager) {
    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var periodicJob: Job? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    fun getApiService(): ApiService {
        val base = tokenManager.getBaseUrl().let { if (it.endsWith("/")) it else "$it/" }
        return RetrofitClient.getApiService(base, tokenManager.getApiKey(), tokenManager.getApiSecret(), tokenManager)
    }

    /** Starts periodic and reconnect-triggered sync. Safe to call more than once. */
    fun start() {
        if (periodicJob != null) return
        val cm = tokenManager.getContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { scope.launch { syncAll() } }
        }
        runCatching { cm.registerDefaultNetworkCallback(callback); networkCallback = callback }
        periodicJob = scope.launch {
            while (true) {
                delay(30_000L)
                syncAll()
            }
        }
    }

    fun stop() {
        periodicJob?.cancel()
        periodicJob = null
        val cm = tokenManager.getContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        networkCallback?.let { runCatching { cm.unregisterNetworkCallback(it) } }
        networkCallback = null
    }

    suspend fun checkServerHealth(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val r = RetrofitClient.getHealthApiService(tokenManager.getBaseUrl()).checkServerHealth()
            if (!r.isSuccessful) error("Health endpoint returned HTTP ${r.code()}")
            val status = r.body()?.get("status")?.toString()?.trim().orEmpty()
            if (status != "ok") error("Health endpoint returned an invalid status")
            "Server Connected Successfully (${tokenManager.getBaseUrl()})"
        }
    }

    suspend fun syncAll(): Result<String> = mutex.withLock {
        _syncState.value = SyncState.Syncing
        try {
            repository.processOutbox().getOrThrow()
            repository.refreshAll().getOrThrow()
            _syncState.value = SyncState.Idle
            Result.success("Local data synchronized")
        } catch (e: Exception) {
            _syncState.value = SyncState.Error(e.message ?: "Synchronization paused")
            SafaLogger.error("SYNC_FAILED", e.message ?: "", e)
            Result.failure(e)
        }
    }

    suspend fun processOutbox(): Result<Int> = repository.processOutbox()

    suspend fun executeGraphQl(query: String, variables: Map<String, Any?>? = null, operationName: String? = null) = runCatching {
        val r = getApiService().postGraphQl(com.safa.account.data.api.dto.GraphQlRequest(query, variables, operationName))
        if (!r.isSuccessful || r.body() == null) error("GraphQL failed: ${r.code()}")
        r.body()!!
    }
}
