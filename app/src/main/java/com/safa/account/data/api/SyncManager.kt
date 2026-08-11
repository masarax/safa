package com.safa.account.data.api

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import com.safa.account.data.api.dto.SyncUpPayload
import com.safa.account.data.repository.AppRepository
import com.safa.account.data.sync.SyncWorkScheduler
import com.safa.account.utils.SafaLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Foreground/manual sync facade.
 * Background synchronization is owned by WorkManager so the app has one
 * persistent coordinator instead of competing 30-second loops.
 */
class SyncManager(private val repository: AppRepository, private val tokenManager: TokenManager) {
    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()
    private val mutex = Mutex()
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var started = false

    init { runCatching { start() } }

    fun getApiService(): ApiService {
        val base = tokenManager.getBaseUrl().let { if (it.endsWith("/")) it else "$it/" }
        return RetrofitClient.getApiService(base, tokenManager.getApiKey(), tokenManager.getApiSecret(), tokenManager)
    }

    /** Registers a connectivity trigger and schedules persistent WorkManager sync. */
    fun start() {
        if (started) return
        started = true
        val context = tokenManager.getContext()
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                runCatching { SyncWorkScheduler.runNow(context) }
            }
        }
        runCatching {
            cm.registerDefaultNetworkCallback(callback)
            networkCallback = callback
        }.onFailure {
            SafaLogger.error("SYNC_NETWORK_CALLBACK_FAILED", it.message ?: "", it)
        }
        runCatching { SyncWorkScheduler.schedule(context) }
    }

    fun stop() {
        val cm = runCatching { tokenManager.getContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager }.getOrNull()
        networkCallback?.let { callback -> cm?.let { runCatching { it.unregisterNetworkCallback(callback) } } }
        networkCallback = null
        started = false
    }

    suspend fun checkServerHealth(): Result<String> = withContext(kotlinx.coroutines.Dispatchers.IO) { runCatching {
        val r = RetrofitClient.getHealthApiService(tokenManager.getBaseUrl()).checkServerHealth()
        if (!r.isSuccessful) error("Health endpoint returned HTTP ${r.code()}")
        if (r.body()?.get("status")?.toString()?.trim() != "ok") error("Health endpoint returned an invalid status")
        "Server Connected Successfully (${tokenManager.getBaseUrl()})"
    } }

    /** Manual/foreground reconciliation. WorkManager remains the background coordinator. */
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
