package com.safa.account.data.api

import com.safa.account.data.repository.AppRepository
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.mockito.kotlin.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SyncRetryHardeningTest {

    @Test
    fun testManualSyncProcessesOutboxThenRefreshesLocalSnapshot() = runBlocking {
        val repository: AppRepository = mock()
        val tokenManager: TokenManager = mock()
        whenever(repository.processOutbox()).thenReturn(Result.success(3))
        whenever(repository.refreshAll()).thenReturn(Result.success(Unit))
        whenever(tokenManager.getBaseUrl()).thenReturn("https://safa.masarax.com/api/")
        whenever(tokenManager.getApiKey()).thenReturn("key")
        whenever(tokenManager.getApiSecret()).thenReturn("sec")
        whenever(repository.allCustomersRaw).thenReturn(flowOf(emptyList()))

        val manager = spy(SyncManager(repository, tokenManager))
        val result = manager.syncAll()

        assertTrue(result.isSuccess)
        assertEquals("Local data synchronized", result.getOrNull())
        verify(repository, times(1)).processOutbox()
        verify(repository, times(1)).refreshAll()
    }

    @Test
    fun testManualSyncStopsWhenOutboxUploadFails() = runBlocking {
        val repository: AppRepository = mock()
        val tokenManager: TokenManager = mock()
        val failure = IllegalStateException("HTTP 500")
        whenever(repository.processOutbox()).thenReturn(Result.failure(failure))
        whenever(tokenManager.getBaseUrl()).thenReturn("https://safa.masarax.com/api/")
        whenever(tokenManager.getApiKey()).thenReturn("key")
        whenever(tokenManager.getApiSecret()).thenReturn("sec")

        val manager = spy(SyncManager(repository, tokenManager))
        val result = manager.syncAll()

        assertTrue(result.isFailure)
        assertEquals(failure, result.exceptionOrNull())
        verify(repository, times(1)).processOutbox()
        verify(repository, never()).refreshAll()
    }

    @Test
    fun testManualSyncDoesNotRefreshWhenDownloadFails() = runBlocking {
        val repository: AppRepository = mock()
        val tokenManager: TokenManager = mock()
        val failure = IllegalStateException("HTTP 422")
        whenever(repository.processOutbox()).thenReturn(Result.success(0))
        whenever(repository.refreshAll()).thenReturn(Result.failure(failure))
        whenever(tokenManager.getBaseUrl()).thenReturn("https://safa.masarax.com/api/")
        whenever(tokenManager.getApiKey()).thenReturn("key")
        whenever(tokenManager.getApiSecret()).thenReturn("sec")

        val manager = spy(SyncManager(repository, tokenManager))
        val result = manager.syncAll()

        assertTrue(result.isFailure)
        assertEquals(failure, result.exceptionOrNull())
        verify(repository, times(1)).processOutbox()
        verify(repository, times(1)).refreshAll()
    }
}
