package com.safa.account.data.repository

import com.safa.account.data.api.ApiService
import com.safa.account.data.api.dto.SyncDownResponse
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import retrofit2.Response

class SyncCursorDownloadTest {

    @Test
    fun refreshAllRequestsNextChunkOnlyAfterCurrentCursorAdvances() = runBlocking<Unit> {
        val api: ApiService = mock()
        whenever(api.syncDownPage(0L, 100)).thenReturn(
            Response.success(
                SyncDownResponse(
                    protocol = "cursor-v1",
                    cursor = 0L,
                    nextCursor = 2L,
                    highWater = 3L,
                    permissionScope = "scope-a",
                    hasMore = true,
                    customers = listOf(mapOf("id" to 10, "local_id" to 101, "sync_version" to 1))
                )
            )
        )
        whenever(api.syncDownPage(2L, 100)).thenReturn(
            Response.success(
                SyncDownResponse(
                    protocol = "cursor-v1",
                    cursor = 2L,
                    nextCursor = 3L,
                    highWater = 3L,
                    permissionScope = "scope-a",
                    hasMore = false,
                    customers = listOf(mapOf("id" to 11, "local_id" to 102, "sync_version" to 1))
                )
            )
        )

        val result = AppRepository(api).refreshAll()

        assertTrue(result.isSuccess)
        verify(api, times(1)).syncDownPage(0L, 100)
        verify(api, times(1)).syncDownPage(2L, 100)
    }

    @Test
    fun largeFeedIsConsumedAsBoundedChunksInsteadOfOneAccountSnapshot() = runBlocking<Unit> {
        val api: ApiService = mock()
        val totalRows = 5_000L
        val chunkSize = 100
        val expectedPages = (totalRows / chunkSize).toInt()

        whenever(api.syncDownPage(any(), eq(chunkSize))).thenAnswer { invocation ->
            val cursor = invocation.getArgument<Long>(0)
            val nextCursor = (cursor + chunkSize).coerceAtMost(totalRows)
            val rows = (cursor until nextCursor).map { offset ->
                mapOf<String, Any?>(
                    "id" to (100_000L + offset),
                    "local_id" to (200_000L + offset),
                    "name" to "Load Customer $offset",
                    "sync_version" to 0
                )
            }
            Response.success(
                SyncDownResponse(
                    protocol = "cursor-v1",
                    cursor = cursor,
                    nextCursor = nextCursor,
                    highWater = totalRows,
                    permissionScope = "scope-load",
                    hasMore = nextCursor < totalRows,
                    customers = rows
                )
            )
        }

        val result = AppRepository(api).refreshAll()

        assertTrue(result.isSuccess)
        verify(api, times(expectedPages)).syncDownPage(any(), eq(chunkSize))
    }

    @Test
    fun refreshAllRejectsNonAdvancingCursorWhenMoreDataIsAdvertised() = runBlocking<Unit> {
        val api: ApiService = mock()
        whenever(api.syncDownPage(0L, 100)).thenReturn(
            Response.success(
                SyncDownResponse(
                    protocol = "cursor-v1",
                    cursor = 0L,
                    nextCursor = 0L,
                    highWater = 1L,
                    permissionScope = "scope-a",
                    hasMore = true
                )
            )
        )

        val result = AppRepository(api).refreshAll()

        assertTrue(result.isFailure)
        verify(api, times(1)).syncDownPage(0L, 100)
    }

    @Test
    fun refreshAllRejectsCursorResponsesWithoutPermissionScope() = runBlocking<Unit> {
        val api: ApiService = mock()
        whenever(api.syncDownPage(0L, 100)).thenReturn(
            Response.success(
                SyncDownResponse(
                    protocol = "cursor-v1",
                    cursor = 0L,
                    nextCursor = 0L,
                    hasMore = false
                )
            )
        )

        assertTrue(AppRepository(api).refreshAll().isFailure)
    }
}
