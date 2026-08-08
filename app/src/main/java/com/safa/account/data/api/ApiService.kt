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

    // --- Direct Endpoint Mappings (/api/customers, /api/suppliers, /api/deposits, /api/transactions) ---
    @GET("customers")
    suspend fun getCustomers(): Response<List<Map<String, Any>>>

    @POST("customers")
    suspend fun createCustomer(@Body customer: Map<String, Any?>): Response<Map<String, Any>>

    @PUT("customers/{id}")
    suspend fun updateCustomerApi(@Path("id") id: Int, @Body customer: Map<String, Any?>): Response<Map<String, Any>>

    @DELETE("customers/{id}")
    suspend fun deleteCustomerApi(@Path("id") id: Int): Response<Map<String, Any>>

    @GET("suppliers")
    suspend fun getSuppliers(): Response<List<Map<String, Any>>>

    @POST("suppliers")
    suspend fun createSupplier(@Body supplier: Map<String, Any?>): Response<Map<String, Any>>

    @PUT("suppliers/{id}")
    suspend fun updateSupplierApi(@Path("id") id: Int, @Body supplier: Map<String, Any?>): Response<Map<String, Any>>

    @DELETE("suppliers/{id}")
    suspend fun deleteSupplierApi(@Path("id") id: Int): Response<Map<String, Any>>

    @GET("deposits")
    suspend fun getDeposits(): Response<List<Map<String, Any>>>

    @POST("deposits")
    suspend fun createDeposit(@Body deposit: Map<String, Any?>): Response<Map<String, Any>>

    @GET("transactions")
    suspend fun getTransactions(): Response<List<Map<String, Any>>>

    @POST("transactions")
    suspend fun createTransactionApi(@Body transaction: Map<String, Any?>): Response<Map<String, Any>>

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

