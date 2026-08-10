package com.safa.account.data.api

import com.safa.account.data.repository.AppRepository
import com.safa.account.utils.SafaLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Compatibility name retained for the UI, but synchronization is no longer a
 * local-database reconciliation job. The server is authoritative and refreshAll
 * simply re-reads the current account data from Laravel.
 */
class SyncManager(private val repository: AppRepository, private val tokenManager: TokenManager) {
    private val _syncState=MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState:StateFlow<SyncState> = _syncState.asStateFlow()

    fun getApiService():ApiService {
        val base=tokenManager.getBaseUrl().let{if(it.endsWith("/"))it else "$it/"}
        return RetrofitClient.getApiService(base,tokenManager.getApiKey(),tokenManager.getApiSecret(),tokenManager)
    }

    suspend fun checkServerHealth():Result<String> = withContext(Dispatchers.IO){runCatching{
        val r=getApiService().getRemoteConfig(); if(!r.isSuccessful) error("Server returned ${r.code()}")
        "Server Connected Successfully (${tokenManager.getBaseUrl()})"
    }}

    suspend fun syncAll():Result<String> = withContext(Dispatchers.IO){
        _syncState.value=SyncState.Syncing
        try { repository.refreshAll().getOrThrow(); _syncState.value=SyncState.Idle; Result.success("Server data refreshed") }
        catch(e:Exception){ _syncState.value=SyncState.Error(e.message ?: "Server sync failed"); SafaLogger.error("SERVER_REFRESH_FAILED",e.message ?: "",e); Result.failure(e) }
    }

    suspend fun processOutbox():Result<Int> = Result.success(0)

    suspend fun executeGraphQl(query:String,variables:Map<String,Any?>?=null,operationName:String?=null)=runCatching{
        val r=getApiService().postGraphQl(com.safa.account.data.api.dto.GraphQlRequest(query,variables,operationName)); if(!r.isSuccessful||r.body()==null) error("GraphQL failed: ${r.code()}"); r.body()!!
    }
}
