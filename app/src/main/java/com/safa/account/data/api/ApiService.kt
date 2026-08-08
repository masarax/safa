package com.safa.account.data.api

import com.safa.account.data.api.dto.*
import retrofit2.Response
import retrofit2.http.*

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

    // --- Server-Driven Auth & SuperAdmin Activation ---
    @POST("auth/login")
    suspend fun login(
        @Body request: MobilePinLoginRequest
    ): Response<Map<String, Any>>

    @POST("auth/activate-superadmin")
    suspend fun activateSuperAdmin(
        @Body request: ActivateSuperAdminRequest
    ): Response<Map<String, Any>>

    // --- Server-Driven RBAC Operator Management ---
    @GET("auth/operators")
    suspend fun getOperators(): Response<Map<String, Any>>

    @POST("auth/operators")
    suspend fun createOperator(
        @Body request: OperatorApiRequest
    ): Response<Map<String, Any>>

    @PUT("auth/operators/{id}")
    suspend fun updateOperator(
        @Path("id") id: Int,
        @Body request: OperatorApiRequest
    ): Response<Map<String, Any>>

    @DELETE("auth/operators/{id}")
    suspend fun deleteOperator(
        @Path("id") id: Int
    ): Response<Map<String, Any>>
}

