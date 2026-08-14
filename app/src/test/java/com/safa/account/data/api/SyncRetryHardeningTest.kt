package com.safa.account.data.api

import com.safa.account.data.network.ApiSecurityInterceptor
import com.safa.account.data.network.DeleteConfirmationCoordinator
import com.safa.account.data.repository.AppRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class SyncRetryHardeningTest {

    @Test
    fun testManualSyncProcessesOutboxThenRefreshesLocalSnapshot() {
        runBlocking {
            val repository: AppRepository = mock()
            val tokenManager: TokenManager = mock()
            whenever(repository.processOutbox()).thenReturn(Result.success(3))
            whenever(repository.refreshAll()).thenReturn(Result.success(Unit))
            whenever(tokenManager.getBaseUrl()).thenReturn("https://safa.masarax.com/api/")
            whenever(tokenManager.getApiKey()).thenReturn("key")
            whenever(tokenManager.getApiSecret()).thenReturn("sec")
            whenever(tokenManager.getContext()).thenReturn(null)
            whenever(repository.allCustomersRaw).thenReturn(flowOf(emptyList()))

            val manager = SyncManager(repository, tokenManager)
            val result = manager.syncAll()

            assertTrue(result.isSuccess)
            assertEquals("Local data synchronized", result.getOrNull())
            assertEquals(SyncState.Idle, manager.syncState.value)
            verify(repository, times(1)).processOutbox()
            verify(repository, times(1)).refreshAll()
        }
    }

    @Test
    fun testManualSyncStopsWhenOutboxUploadFails() {
        runBlocking {
            val repository: AppRepository = mock()
            val tokenManager: TokenManager = mock()
            val failure = IllegalStateException("HTTP 500")
            whenever(repository.processOutbox()).thenReturn(Result.failure(failure))
            whenever(tokenManager.getBaseUrl()).thenReturn("https://safa.masarax.com/api/")
            whenever(tokenManager.getApiKey()).thenReturn("key")
            whenever(tokenManager.getApiSecret()).thenReturn("sec")
            whenever(tokenManager.getContext()).thenReturn(null)

            val manager = SyncManager(repository, tokenManager)
            val result = manager.syncAll()

            assertTrue(result.isFailure)
            val returned = result.exceptionOrNull()
            assertTrue(returned is IllegalStateException)
            assertEquals("HTTP 500", returned?.message)
            assertEquals(SyncState.Error("HTTP 500"), manager.syncState.value)
            verify(repository, times(1)).processOutbox()
            verify(repository, never()).refreshAll()
        }
    }

    @Test
    fun testManualSyncDoesNotRefreshWhenDownloadFails() {
        runBlocking {
            val repository: AppRepository = mock()
            val tokenManager: TokenManager = mock()
            val failure = IllegalStateException("HTTP 422")
            whenever(repository.processOutbox()).thenReturn(Result.success(0))
            whenever(repository.refreshAll()).thenReturn(Result.failure(failure))
            whenever(tokenManager.getBaseUrl()).thenReturn("https://safa.masarax.com/api/")
            whenever(tokenManager.getApiKey()).thenReturn("key")
            whenever(tokenManager.getApiSecret()).thenReturn("sec")
            whenever(tokenManager.getContext()).thenReturn(null)

            val manager = SyncManager(repository, tokenManager)
            val result = manager.syncAll()

            assertTrue(result.isFailure)
            val returned = result.exceptionOrNull()
            assertTrue(returned is IllegalStateException)
            assertEquals("HTTP 422", returned?.message)
            assertEquals(SyncState.Error("HTTP 422"), manager.syncState.value)
            verify(repository, times(1)).processOutbox()
            verify(repository, times(1)).refreshAll()
        }
    }

    @Test
    fun unconfirmedDeleteIsRejectedWithoutCallingNetworkChain() {
        val request = Request.Builder()
            .url("https://safa.masarax.com/api/customers/10")
            .delete()
            .build()
        val chain: Interceptor.Chain = mock()
        whenever(chain.request()).thenReturn(request)

        val response = ApiSecurityInterceptor(apiKey = "key").intercept(chain)

        assertEquals(428, response.code)
        assertEquals("Delete confirmation required", response.message)
        verify(chain, never()).proceed(any())
    }

    @Test
    fun confirmedDeleteProceedsImmediatelyWithoutUiWait() {
        val request = Request.Builder()
            .url("https://safa.masarax.com/api/customers/10?confirmed=true")
            .delete()
            .header("X-SAFA-DELETE-CONFIRM", "true")
            .build()
        val chain: Interceptor.Chain = mock()
        whenever(chain.request()).thenReturn(request)
        whenever(chain.proceed(any())).thenAnswer { invocation ->
            val secured = invocation.arguments[0] as Request
            Response.Builder()
                .request(secured)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("{}".toResponseBody())
                .build()
        }

        val response = ApiSecurityInterceptor(apiKey = "key").intercept(chain)

        assertEquals(200, response.code)
        verify(chain, times(1)).proceed(any())
    }

    @Test
    fun foregroundDeleteConfirmationSuspendsAndResolvesWithoutBlockingNetworkThread() = runBlocking {
        val requestAwaiter = async { DeleteConfirmationCoordinator.requests.first() }
        val confirmation = async {
            DeleteConfirmationCoordinator.requestConfirmation("Delete data?", "This action cannot be undone.")
        }

        val request = requestAwaiter.await()
        assertEquals("Delete data?", request.title)
        DeleteConfirmationCoordinator.resolve(request.id, true)

        assertTrue(confirmation.await())
        assertEquals(0, DeleteConfirmationCoordinator.pendingCountForTest())
    }
}
