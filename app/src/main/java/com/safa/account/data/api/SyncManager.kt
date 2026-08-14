package com.safa.account.data.api

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import com.safa.account.data.local.LocalAccountBoundary
import com.safa.account.data.repository.AppRepository
import com.safa.account.data.sync.SyncCoordinator
import com.safa.account.data.sync.SyncWorkScheduler
import com.safa.account.utils.SafaLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/** One server-authorized business account available to the authenticated user. */
data class AccountChoice(
    val accountId: Int,
    val ownerName: String,
    val role: String,
    val isOwner: Boolean
)

/**
 * Foreground/manual sync facade.
 * Background synchronization is owned by WorkManager and all reconciliation
 * entry points share SyncCoordinator so upload/download pairs never interleave.
 */
class SyncManager(private val repository: AppRepository, private val tokenManager: TokenManager) {
    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()
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
            SafaLogger.error("SYNC_NETWORK_CALLBACK_FAILED", "Network callback registration failed", it)
        }
        runCatching { SyncWorkScheduler.schedule(context) }
    }

    fun stop() {
        val cm = runCatching {
            tokenManager.getContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        }.getOrNull()
        networkCallback?.let { callback -> cm?.let { runCatching { it.unregisterDefaultNetworkCallbackSafe(callback) } } }
        networkCallback = null
        started = false
    }

    private fun ConnectivityManager.unregisterDefaultNetworkCallbackSafe(callback: ConnectivityManager.NetworkCallback) {
        unregisterNetworkCallback(callback)
    }

    suspend fun listAccounts(): Result<List<AccountChoice>> = withContext(Dispatchers.IO) {
        runCatching {
            val response = getApiService().getAccounts()
            if (!response.isSuccessful || response.body() == null) error("Unable to load authorized accounts")
            val body = response.body()!!
            @Suppress("UNCHECKED_CAST")
            val accounts = (body["accounts"] as? List<Map<String, Any?>>).orEmpty().mapNotNull { raw ->
                val id = (raw["account_id"] as? Number)?.toInt() ?: raw["account_id"]?.toString()?.toIntOrNull() ?: 0
                if (id <= 0) return@mapNotNull null
                AccountChoice(
                    accountId = id,
                    ownerName = raw["owner_name"]?.toString().orEmpty(),
                    role = raw["role"]?.toString().orEmpty(),
                    isOwner = raw["is_owner"] == true || raw["is_owner"]?.toString() == "1"
                )
            }.distinctBy { it.accountId }

            val authorizedIds = accounts.mapTo(HashSet()) { it.accountId }
            val savedActive = tokenManager.getActiveAccountId()
            if (savedActive != null && savedActive !in authorizedIds) {
                // Access may have been revoked while the app was offline. Never
                // continue showing or replaying the revoked account's cache.
                // Security wins over preserving now-unauthorized pending data.
                LocalAccountBoundary.destroyAccountState(tokenManager.getContext().applicationContext)
                repository.clearLocalPresentation()
                tokenManager.saveActiveAccountId(null)
            }

            val serverActive = ((body["active_account_id"] as? Number)?.toInt()
                ?: body["active_account_id"]?.toString()?.toIntOrNull())
                ?.takeIf { it > 0 && it in authorizedIds }
            bootstrapAccount(serverActive ?: accounts.singleOrNull()?.accountId)
            accounts
        }
    }

    suspend fun switchAccount(accountId: Int): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            require(accountId > 0) { "Invalid account" }
            val current = tokenManager.getActiveAccountId()
            if (current != null && current != accountId && repository.hasPendingLocalChanges()) {
                error("Sync pending changes before switching accounts")
            }

            val response = getApiService().switchAccount(mapOf("account_id" to accountId))
            if (!response.isSuccessful || response.body() == null) error("Account switch was not authorized")
            val returned = (response.body()!!["active_account_id"] as? Number)?.toInt()
                ?: response.body()!!["active_account_id"]?.toString()?.toIntOrNull()
                ?: 0
            if (returned != accountId) error("Account switch response did not match the requested account")

            when (repository.bindAccount(accountId)) {
                LocalAccountBoundary.Result.BLOCKED_BY_PENDING_MUTATIONS -> error("Sync pending changes before switching accounts")
                else -> Unit
            }
            tokenManager.saveActiveAccountId(accountId)

            // The old account presentation was cleared by bindAccount() before
            // this request. If download fails, the UI remains empty rather than
            // leaking the prior account's cached business data.
            repository.refreshAll().getOrThrow()
            accountId
        }
    }

    private fun bootstrapAccount(accountId: Int?) {
        val id = accountId?.takeIf { it > 0 } ?: return
        val existing = tokenManager.getActiveAccountId()
        if (existing != null && existing != id) return
        when (repository.bindAccount(id)) {
            LocalAccountBoundary.Result.BLOCKED_BY_PENDING_MUTATIONS -> return
            else -> tokenManager.saveActiveAccountId(id)
        }
    }

    private suspend fun ensureActiveAccount(): Int {
        tokenManager.getActiveAccountId()?.let { active ->
            when (repository.bindAccount(active)) {
                LocalAccountBoundary.Result.BLOCKED_BY_PENDING_MUTATIONS -> error("Local mutation account boundary mismatch")
                else -> return active
            }
        }

        val accounts = listAccounts().getOrThrow()
        tokenManager.getActiveAccountId()?.let { return it }
        if (accounts.size > 1) error("Select an account before synchronization")
        error("No authorized account is available")
    }

    suspend fun checkServerHealth(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val r = RetrofitClient.getHealthApiService(tokenManager.getBaseUrl()).checkServerHealth()
            if (!r.isSuccessful) error("Health endpoint returned HTTP ${r.code()}")
            if (r.body()?.get("status")?.toString()?.trim() != "ok") error("Health endpoint returned an invalid status")
            "Server Connected Successfully (${tokenManager.getBaseUrl()})"
        }
    }

    /**
     * Manual/foreground reconciliation using the same gate as WorkManager.
     * Sync failures are returned as Result.failure instead of escaping from the
     * facade, so the UI can never be left permanently in the Syncing state.
     */
    suspend fun syncAll(): Result<String> {
        _syncState.value = SyncState.Syncing
        return try {
            ensureActiveAccount()
            val result = SyncCoordinator.run {
                repository.processOutbox().getOrThrow()
                repository.refreshAll().getOrThrow()
            }

            if (result == null) {
                val error = IllegalStateException("Another synchronization is active; retry shortly")
                _syncState.value = SyncState.Error("Synchronization is already in progress")
                SafaLogger.error("SYNC_GATE_BUSY", "Synchronization gate is already active", error)
                Result.failure(error)
            } else {
                _syncState.value = SyncState.Idle
                Result.success("Local data synchronized")
            }
        } catch (t: Throwable) {
            _syncState.value = SyncState.Error("Synchronization failed")
            SafaLogger.error("SYNC_FOREGROUND_FAILED", "Foreground synchronization failed", t)
            Result.failure(IllegalStateException("Synchronization failed"))
        }
    }

    suspend fun processOutbox(): Result<Int> = try {
        ensureActiveAccount()
        SyncCoordinator.run { repository.processOutbox() }
            ?: Result.failure(IllegalStateException("Another synchronization is active; retry shortly"))
    } catch (t: Throwable) {
        SafaLogger.error("SYNC_OUTBOX_FAILED", "Outbox processing failed", t)
        Result.failure(IllegalStateException("Outbox processing failed"))
    }

    suspend fun executeGraphQl(
        query: String,
        variables: Map<String, Any?>? = null,
        operationName: String? = null
    ) = runCatching {
        val r = getApiService().postGraphQl(com.safa.account.data.api.dto.GraphQlRequest(query, variables, operationName))
        if (!r.isSuccessful || r.body() == null) error("GraphQL failed: ${r.code()}")
        r.body()!!
    }
}
