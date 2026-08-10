package com.safa.account.data.api

import com.safa.account.data.api.dto.SyncUpPayload
import com.safa.account.data.model.Customer
import com.safa.account.data.repository.AppRepository
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Test
import org.mockito.kotlin.*
import retrofit2.Response

class SyncRetryHardeningTest {

    @Test
    fun testSuccessfulSyncResetsRetryCount() {
        runBlocking {
            val repository: AppRepository = mock()
            val tokenManager: TokenManager = mock()
            val apiService: ApiService = mock()

            whenever(tokenManager.getBaseUrl()).thenReturn("https://safa.masarax.com/api/")
            whenever(tokenManager.getApiKey()).thenReturn("key")
            whenever(tokenManager.getApiSecret()).thenReturn("sec")

            val syncManager = spy(SyncManager(repository, tokenManager))
            doReturn(apiService).whenever(syncManager).getApiService()

            val pendingCustomer = Customer(id = 1, name = "Test", phone = "017", retryCount = 3)
            whenever(repository.getPendingCustomers()).thenReturn(listOf(pendingCustomer))
            whenever(repository.getPendingTransactions()).thenReturn(emptyList())
            whenever(repository.getPendingSuppliers()).thenReturn(emptyList())
            whenever(repository.getPendingSupplierDeposits()).thenReturn(emptyList())
            whenever(repository.getPendingExpensesIncomes()).thenReturn(emptyList())
            whenever(repository.getPendingWalletBatches()).thenReturn(emptyList())
            whenever(repository.getPendingWalletLedgers()).thenReturn(emptyList())
            whenever(repository.getPendingOutbox()).thenReturn(emptyList())

            whenever(repository.allCustomersRaw).thenReturn(kotlinx.coroutines.flow.flowOf(emptyList()))
            whenever(repository.allSuppliersRaw).thenReturn(kotlinx.coroutines.flow.flowOf(emptyList()))
            whenever(repository.allTransactionsRaw).thenReturn(kotlinx.coroutines.flow.flowOf(emptyList()))
            whenever(repository.allSupplierDepositsRaw).thenReturn(kotlinx.coroutines.flow.flowOf(emptyList()))
            whenever(repository.allExpensesIncomesRaw).thenReturn(kotlinx.coroutines.flow.flowOf(emptyList()))
            whenever(repository.allWalletBatchesRaw).thenReturn(kotlinx.coroutines.flow.flowOf(emptyList()))
            whenever(repository.allWalletLedgersRaw).thenReturn(kotlinx.coroutines.flow.flowOf(emptyList()))

            val okBody = mapOf(
                "status" to "success",
                "accepted" to mapOf("customers" to listOf(mapOf("local_id" to 1, "server_id" to 99)))
            )
            whenever(apiService.syncUp(any())).thenReturn(Response.success(okBody))
            whenever(apiService.syncDown()).thenReturn(Response.success(com.safa.account.data.api.dto.SyncDownResponse()))

            syncManager.syncAll()

            verify(repository, times(1)).markCustomerSynced(eq(1), eq(99))
        }
    }

    @Test
    fun testRetryableHttp500IncrementsRetryCount() {
        runBlocking {
            val repository: AppRepository = mock()
            val tokenManager: TokenManager = mock()
            val apiService: ApiService = mock()

            whenever(tokenManager.getBaseUrl()).thenReturn("https://safa.masarax.com/api/")
            whenever(tokenManager.getApiKey()).thenReturn("key")
            whenever(tokenManager.getApiSecret()).thenReturn("sec")

            val syncManager = spy(SyncManager(repository, tokenManager))
            doReturn(apiService).whenever(syncManager).getApiService()

            val pendingCustomer = Customer(id = 2, name = "Retry User", phone = "018", retryCount = 1)
            whenever(repository.getPendingCustomers()).thenReturn(listOf(pendingCustomer))
            whenever(repository.getPendingTransactions()).thenReturn(emptyList())
            whenever(repository.getPendingSuppliers()).thenReturn(emptyList())
            whenever(repository.getPendingSupplierDeposits()).thenReturn(emptyList())
            whenever(repository.getPendingExpensesIncomes()).thenReturn(emptyList())
            whenever(repository.getPendingWalletBatches()).thenReturn(emptyList())
            whenever(repository.getPendingWalletLedgers()).thenReturn(emptyList())
            whenever(repository.getPendingOutbox()).thenReturn(emptyList())

            // Return HTTP 500 Internal Server Error
            whenever(apiService.syncUp(any())).thenReturn(Response.error(500, "Internal Error".toResponseBody()))

            syncManager.syncAll()

            // HTTP 500 is retryable -> incrementCustomerRetry must be called
            verify(repository, times(1)).incrementCustomerRetry(eq(2))
            verify(repository, never()).markCustomerFailed(any(), any())
        }
    }

    @Test
    fun testNonRetryableHttp422DoesNotIncrementRetryForever() {
        runBlocking {
            val repository: AppRepository = mock()
            val tokenManager: TokenManager = mock()
            val apiService: ApiService = mock()

            whenever(tokenManager.getBaseUrl()).thenReturn("https://safa.masarax.com/api/")
            whenever(tokenManager.getApiKey()).thenReturn("key")
            whenever(tokenManager.getApiSecret()).thenReturn("sec")

            val syncManager = spy(SyncManager(repository, tokenManager))
            doReturn(apiService).whenever(syncManager).getApiService()

            val pendingCustomer = Customer(id = 3, name = "Invalid User", phone = "019", retryCount = 0)
            whenever(repository.getPendingCustomers()).thenReturn(listOf(pendingCustomer))
            whenever(repository.getPendingTransactions()).thenReturn(emptyList())
            whenever(repository.getPendingSuppliers()).thenReturn(emptyList())
            whenever(repository.getPendingSupplierDeposits()).thenReturn(emptyList())
            whenever(repository.getPendingExpensesIncomes()).thenReturn(emptyList())
            whenever(repository.getPendingWalletBatches()).thenReturn(emptyList())
            whenever(repository.getPendingWalletLedgers()).thenReturn(emptyList())
            whenever(repository.getPendingOutbox()).thenReturn(emptyList())

            // Return HTTP 422 Unprocessable Entity
            whenever(apiService.syncUp(any())).thenReturn(Response.error(422, "Validation Failed".toResponseBody()))

            syncManager.syncAll()

            // HTTP 422 is non-retryable -> markCustomerFailed must be called immediately
            verify(repository, times(1)).markCustomerFailed(eq(3), any())
            verify(repository, never()).incrementCustomerRetry(any())
        }
    }
}
