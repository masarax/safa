package com.safa.account.data.api

import com.safa.account.data.api.dto.*
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.*

interface ApiService {
    @POST("sync/up") suspend fun syncUp(@Body payload: SyncUpPayload): Response<Map<String, Any>>
    @GET("sync/down") suspend fun syncDown(): Response<SyncDownResponse>

    @GET("customers") suspend fun getCustomers(): Response<Map<String, Any?>>
    @POST("customers") suspend fun createCustomer(@Body customer: Map<String, Any?>): Response<Map<String, Any>>
    @PUT("customers/{id}") suspend fun updateCustomerApi(@Path("id") id: Int, @Body customer: Map<String, Any?>): Response<Map<String, Any>>
    @DELETE("customers/{id}") suspend fun deleteCustomerApi(@Path("id") id: Int, @Query("confirmed") confirmed: Boolean = false): Response<Map<String, Any>>

    @GET("suppliers") suspend fun getSuppliers(): Response<Map<String, Any?>>
    @POST("suppliers") suspend fun createSupplier(@Body supplier: Map<String, Any?>): Response<Map<String, Any>>
    @PUT("suppliers/{id}") suspend fun updateSupplierApi(@Path("id") id: Int, @Body supplier: Map<String, Any?>): Response<Map<String, Any>>
    @DELETE("suppliers/{id}") suspend fun deleteSupplierApi(@Path("id") id: Int, @Query("confirmed") confirmed: Boolean = false): Response<Map<String, Any>>

    @GET("transactions") suspend fun getTransactions(): Response<Map<String, Any?>>
    @POST("transactions") suspend fun createTransactionApi(@Body transaction: Map<String, Any?>): Response<Map<String, Any>>
    @PUT("transactions/{id}") suspend fun updateTransactionApi(@Path("id") id: Int, @Body transaction: Map<String, Any?>): Response<Map<String, Any>>
    @DELETE("transactions/{id}") suspend fun deleteTransactionApi(@Path("id") id: Int, @Query("confirmed") confirmed: Boolean = false): Response<Map<String, Any>>

    @GET("wallet-ledgers") suspend fun getWalletLedgers(): Response<Map<String, Any>>
    @POST("wallet-ledgers") suspend fun createWalletLedger(@Body payload: Map<String, Any?>): Response<Map<String, Any>>
    @PUT("wallet-ledgers/{id}") suspend fun updateWalletLedger(@Path("id") id: Int, @Body payload: Map<String, Any?>): Response<Map<String, Any>>
    @DELETE("wallet-ledgers/{id}") suspend fun deleteWalletLedger(@Path("id") id: Int, @Query("confirmed") confirmed: Boolean = false): Response<Map<String, Any>>

    @GET("supplier-deposits") suspend fun getSupplierDeposits(): Response<Map<String, Any>>
    @POST("supplier-deposits") suspend fun createSupplierDeposit(@Body payload: Map<String, Any?>): Response<Map<String, Any>>
    @PUT("supplier-deposits/{id}") suspend fun updateSupplierDeposit(@Path("id") id: Int, @Body payload: Map<String, Any?>): Response<Map<String, Any>>
    @DELETE("supplier-deposits/{id}") suspend fun deleteSupplierDeposit(@Path("id") id: Int, @Query("confirmed") confirmed: Boolean = false): Response<Map<String, Any>>

    @GET("wallet-batches") suspend fun getWalletBatches(): Response<Map<String, Any>>
    @POST("wallet-batches") suspend fun createWalletBatch(@Body payload: Map<String, Any?>): Response<Map<String, Any>>
    @PUT("wallet-batches/{id}") suspend fun updateWalletBatch(@Path("id") id: Int, @Body payload: Map<String, Any?>): Response<Map<String, Any>>
    @DELETE("wallet-batches/{id}") suspend fun deleteWalletBatch(@Path("id") id: Int, @Query("confirmed") confirmed: Boolean = false): Response<Map<String, Any>>

    @GET("expenses-incomes") suspend fun getExpensesIncomes(): Response<Map<String, Any?>>
    @POST("expenses-incomes") suspend fun createExpenseIncome(@Body payload: Map<String, Any?>): Response<Map<String, Any>>
    @PUT("expenses-incomes/{id}") suspend fun updateExpenseIncome(@Path("id") id: Int, @Body payload: Map<String, Any?>): Response<Map<String, Any>>
    @DELETE("expenses-incomes/{id}") suspend fun deleteExpenseIncome(@Path("id") id: Int, @Query("confirmed") confirmed: Boolean = false): Response<Map<String, Any>>

    // Used only with RetrofitClient.getHealthApiService(); no HMAC/JWT/session headers.
    @GET("health.php") suspend fun checkServerHealth(): Response<Map<String, Any>>

    @GET("config/remote") suspend fun getRemoteConfig(): Response<Map<String, Any>>
    @POST("config/update") suspend fun updateConfig(@Body config: Map<String, Any?>): Response<Map<String, Any>>
    @Multipart @POST("upload/logo") suspend fun uploadLogo(@Part logo: MultipartBody.Part): Response<Map<String, Any>>
    @GET("version/check") suspend fun checkVersion(@Query("version_code") versionCode: Int): Response<Map<String, Any>>

    @POST("graphql") suspend fun postGraphQl(@Body request: GraphQlRequest): Response<GraphQlResponse>

    @POST("auth/login") suspend fun login(@Body request: MobilePinLoginRequest): Response<Map<String, Any>>
    @POST("auth/refresh") suspend fun refresh(): Response<Map<String, Any>>
    @POST("auth/logout") suspend fun logout(): Response<Map<String, Any>>
    @POST("auth/logout-all") suspend fun logoutAll(): Response<Map<String, Any>>
    @POST("auth/activate-superadmin") suspend fun activateSuperAdmin(@Body request: ActivateSuperAdminRequest): Response<Map<String, Any>>
    @GET("auth/operators") suspend fun getOperators(): Response<Map<String, Any>>
    @POST("auth/operators") suspend fun createOperator(@Body request: OperatorApiRequest): Response<Map<String, Any>>
    @PUT("auth/operators/{id}") suspend fun updateOperator(@Path("id") id: Int, @Body request: OperatorApiRequest): Response<Map<String, Any>>
    @DELETE("auth/operators/{id}") suspend fun deleteOperator(@Path("id") id: Int): Response<Map<String, Any>>

    @GET("accounts") suspend fun getAccounts(): Response<Map<String, Any?>>
    @POST("accounts/switch") suspend fun switchAccount(@Body request: Map<String, Any?>): Response<Map<String, Any?>>
    @POST("accounts/share") suspend fun shareAccount(@Body request: Map<String, Any?>): Response<Map<String, Any?>>
}
