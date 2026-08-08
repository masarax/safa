package com.safa.account.data.api

import com.safa.account.data.api.dto.GraphQlRequest
import com.safa.account.data.api.dto.GraphQlResponse
import com.safa.account.data.api.dto.SyncDownResponse
import com.safa.account.data.api.dto.SyncUpPayload
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface ApiService {

    @POST("sync/up")
    suspend fun syncUp(
        @Body payload: SyncUpPayload
    ): Response<Map<String, Any>>

    @GET("sync/down")
    suspend fun syncDown(): Response<SyncDownResponse>

    @GET("config/remote")
    suspend fun getRemoteConfig(): Response<Map<String, Any>>

    @GET("version/check")
    suspend fun checkVersion(
        @Query("version_code") versionCode: Int
    ): Response<Map<String, Any>>

    @POST("graphql")
    suspend fun postGraphQl(
        @Body request: GraphQlRequest
    ): Response<GraphQlResponse>
}
