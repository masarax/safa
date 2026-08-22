package com.safa.account.data.api

import com.safa.account.data.api.dto.*
import com.safa.account.data.network.DeleteConfirmationCoordinator
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Response
import retrofit2.http.*

interface ApiService {
    @POST("sync/up") suspend fun syncUp(@Body payload: SyncUpPayload): Response<Map<String, Any>>

    /** One bounded deterministic chunk from the canonical cursor sync feed. */
    @GET("sync/down")
    suspend fun syncDownPage(
        @Query("cursor") cursor: Long,
        @Query("per_page") perPage: Int = 100
    ): Response<SyncDownResponse>

    @GET("customers")
    suspend fun getCustomers(@Query("page") page: Int = 1, @Query("per_page") perPage: Int = 50): Response<Map<String, Any?>>
    @POST("customers") suspend fun createCustomer(@Body customer: Map<String, Any?>): Response<Map<String, Any>>
    @PUT("customers/{id}") suspend fun updateCustomerApi(@Path("id") id: Int, @Body customer: Map<String, Any?>): Response<Map<String, Any>>
    @DELETE("customers/{id}") suspend fun deleteCustomerConfirmed(@Path("id") id: Int, @Query("confirmed") confirmed: Boolean = true, @Header("X-SAFA-DELETE-CONFIRM") confirmation: String = "true"): Response<Map<String, Any>>
    suspend fun deleteCustomerApi(id: Int, confirmed: Boolean = false): Response<Map<String, Any>> = confirmedDelete("customers", id, confirmed) { deleteCustomerConfirmed(id) }

    @GET("suppliers")
    suspend fun getSuppliers(@Query("page") page: Int = 1, @Query("per_page") perPage: Int = 50): Response<Map<String, Any?>>
    @POST("suppliers") suspend fun createSupplier(@Body supplier: Map<String, Any?>): Response<Map<String, Any>>
    @PUT("suppliers/{id}") suspend fun updateSupplierApi(@Path("id") id: Int, @Body supplier: Map<String, Any?>): Response<Map<String, Any>>
    @DELETE("suppliers/{id}") suspend fun deleteSupplierConfirmed(@Path("id") id: Int, @Query("confirmed") confirmed: Boolean = true, @Header("X-SAFA-DELETE-CONFIRM") confirmation: String = "true"): Response<Map<String, Any>>
    suspend fun deleteSupplierApi(id: Int, confirmed: Boolean = false): Response<Map<String, Any>> = confirmedDelete("suppliers", id, confirmed) { deleteSupplierConfirmed(id) }

    @GET("transactions") suspend fun getTransactions(): Response<Map<String, Any?>>
    @POST("transactions") suspend fun createTransactionApi(@Body transaction: Map<String, Any?>): Response<Map<String, Any>>
    @PUT("transactions/{id}") suspend fun updateTransactionApi(@Path("id") id: Int, @Body transaction: Map<String, Any?>): Response<Map<String, Any>>
    @DELETE("transactions/{id}") suspend fun deleteTransactionConfirmed(@Path("id") id: Int, @Query("confirmed") confirmed: Boolean = true, @Header("X-SAFA-DELETE-CONFIRM") confirmation: String = "true"): Response<Map<String, Any>>
    suspend fun deleteTransactionApi(id: Int, confirmed: Boolean = false): Response<Map<String, Any>> = confirmedDelete("transactions", id, confirmed) { deleteTransactionConfirmed(id) }

    @GET("wallet-ledgers") suspend fun getWalletLedgers(): Response<Map<String, Any>>
    @POST("wallet-ledgers") suspend fun createWalletLedger(@Body payload: Map<String, Any?>): Response<Map<String, Any>>
    @PUT("wallet-ledgers/{id}") suspend fun updateWalletLedger(@Path("id") id: Int, @Body payload: Map<String, Any?>): Response<Map<String, Any>>
    @DELETE("wallet-ledgers/{id}") suspend fun deleteWalletLedgerConfirmed(@Path("id") id: Int, @Query("confirmed") confirmed: Boolean = true, @Header("X-SAFA-DELETE-CONFIRM") confirmation: String = "true"): Response<Map<String, Any>>
    suspend fun deleteWalletLedger(id: Int, confirmed: Boolean = false): Response<Map<String, Any>> = confirmedDelete("wallet_ledgers", id, confirmed) { deleteWalletLedgerConfirmed(id) }

    @GET("supplier-deposits") suspend fun getSupplierDeposits(): Response<Map<String, Any>>
    @POST("supplier-deposits") suspend fun createSupplierDeposit(@Body payload: Map<String, Any?>): Response<Map<String, Any>>
    @PUT("supplier-deposits/{id}") suspend fun updateSupplierDeposit(@Path("id") id: Int, @Body payload: Map<String, Any?>): Response<Map<String, Any>>
    @DELETE("supplier-deposits/{id}") suspend fun deleteSupplierDepositConfirmed(@Path("id") id: Int, @Query("confirmed") confirmed: Boolean = true, @Header("X-SAFA-DELETE-CONFIRM") confirmation: String = "true"): Response<Map<String, Any>>
    suspend fun deleteSupplierDeposit(id: Int, confirmed: Boolean = false): Response<Map<String, Any>> = confirmedDelete("supplier_deposits", id, confirmed) { deleteSupplierDepositConfirmed(id) }

    @GET("wallet-batches") suspend fun getWalletBatches(): Response<Map<String, Any>>
    @POST("wallet-batches") suspend fun createWalletBatch(@Body payload: Map<String, Any?>): Response<Map<String, Any>>
    @PUT("wallet-batches/{id}") suspend fun updateWalletBatch(@Path("id") id: Int, @Body payload: Map<String, Any?>): Response<Map<String, Any>>
    @DELETE("wallet-batches/{id}") suspend fun deleteWalletBatchConfirmed(@Path("id") id: Int, @Query("confirmed") confirmed: Boolean = true, @Header("X-SAFA-DELETE-CONFIRM") confirmation: String = "true"): Response<Map<String, Any>>
    suspend fun deleteWalletBatch(id: Int, confirmed: Boolean = false): Response<Map<String, Any>> = confirmedDelete("wallet_batches", id, confirmed) { deleteWalletBatchConfirmed(id) }

    @GET("expenses-incomes") suspend fun getExpensesIncomes(): Response<Map<String, Any?>>
    @POST("expenses-incomes") suspend fun createExpenseIncome(@Body payload: Map<String, Any?>): Response<Map<String, Any>>
    @PUT("expenses-incomes/{id}") suspend fun updateExpenseIncome(@Path("id") id: Int, @Body payload: Map<String, Any?>): Response<Map<String, Any>>
    @DELETE("expenses-incomes/{id}") suspend fun deleteExpenseIncomeConfirmed(@Path("id") id: Int, @Query("confirmed") confirmed: Boolean = true, @Header("X-SAFA-DELETE-CONFIRM") confirmation: String = "true"): Response<Map<String, Any>>
    suspend fun deleteExpenseIncome(id: Int, confirmed: Boolean = false): Response<Map<String, Any>> = confirmedDelete("expenses_incomes", id, confirmed) { deleteExpenseIncomeConfirmed(id) }

    @GET("auth/health") suspend fun checkServerHealth(): Response<Map<String, Any>>
    @GET("config/remote") suspend fun getRemoteConfig(): Response<Map<String, Any>>
    @POST("config/update") suspend fun updateConfig(@Body config: Map<String, Any?>): Response<Map<String, Any>>
    @Multipart @POST("upload/logo") suspend fun uploadLogo(@Part logo: MultipartBody.Part): Response<Map<String, Any>>
    @GET("version/check") suspend fun checkVersion(@Query("version_code") versionCode: Int): Response<Map<String, Any>>
    @POST("graphql") suspend fun postGraphQl(@Body request: GraphQlRequest): Response<GraphQlResponse>
    @POST("auth/login") suspend fun login(@Body request: MobilePinLoginRequest): Response<Map<String, Any>>
    @POST("auth/logout") suspend fun logout(): Response<Map<String, Any>>
    @POST("auth/logout-all") suspend fun logoutAll(): Response<Map<String, Any>>
    @POST("auth/change-pin") suspend fun changePin(@Body request: ChangePinRequest): Response<Map<String, Any>>
    @GET("auth/session") suspend fun getCurrentSession(): Response<Map<String, Any>>
    @GET("auth/operators") suspend fun getOperators(): Response<Map<String, Any>>
    @POST("auth/operators") suspend fun createOperator(@Body request: OperatorApiRequest): Response<Map<String, Any>>
    @PUT("auth/operators/{id}") suspend fun updateOperator(@Path("id") id: Int, @Body request: OperatorApiRequest): Response<Map<String, Any>>
    @DELETE("auth/operators/{id}") suspend fun deleteOperatorConfirmed(@Path("id") id: Int, @Query("confirmed") confirmed: Boolean = true, @Header("X-SAFA-DELETE-CONFIRM") confirmation: String = "true"): Response<Map<String, Any>>
    suspend fun deleteOperator(id: Int, confirmed: Boolean = false): Response<Map<String, Any>> = confirmedDelete("operators", id, confirmed) { deleteOperatorConfirmed(id) }

    @GET("accounts") suspend fun getAccounts(): Response<Map<String, Any?>>
    @POST("accounts/switch") suspend fun switchAccount(@Body request: Map<String, Any?>): Response<Map<String, Any?>>
    @POST("accounts/share") suspend fun shareAccount(@Body request: Map<String, Any?>): Response<Map<String, Any?>>

    private suspend fun confirmedDelete(
        entity: String,
        id: Int,
        alreadyConfirmed: Boolean,
        call: suspend () -> Response<Map<String, Any>>
    ): Response<Map<String, Any>> {
        val targetKey = "$entity:$id"
        if (!alreadyConfirmed) {
            val ok = DeleteConfirmationCoordinator.requestAndGrant(
                targetKey = targetKey,
                title = "Delete data?",
                message = "This action cannot be undone."
            )
            if (!ok) {
                return Response.error(
                    409,
                    "{\"status\":\"cancelled\",\"message\":\"Delete cancelled.\"}"
                        .toResponseBody("application/json".toMediaType())
                )
            }
        } else {
            DeleteConfirmationCoordinator.grant(targetKey)
        }

        return try {
            val response = call()
            if (!response.isSuccessful) DeleteConfirmationCoordinator.consume(targetKey)
            response
        } catch (t: Throwable) {
            DeleteConfirmationCoordinator.consume(targetKey)
            throw t
        }
    }
}
