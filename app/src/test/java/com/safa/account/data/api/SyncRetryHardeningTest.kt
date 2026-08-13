package com.safa.account.data.api

import com.safa.account.data.repository.AppRepository
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
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
}
