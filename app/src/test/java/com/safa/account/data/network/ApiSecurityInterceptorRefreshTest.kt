package com.safa.account.data.network

import com.safa.account.data.api.TokenManager
import java.util.concurrent.atomic.AtomicInteger
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ApiSecurityInterceptorRefreshTest {

    @Test
    fun expiredAttachedTokenTriggersExactlyOneRefreshAndRetriesWithRotatedToken() {
        val tokenManager: TokenManager = mock()
        whenever(tokenManager.getDeviceToken()).thenReturn("device")
        whenever(tokenManager.getFingerprintToken()).thenReturn("fingerprint")
        whenever(tokenManager.getSessionToken()).thenReturn("session")
        whenever(tokenManager.getActiveAccountId()).thenReturn(7)
        whenever(tokenManager.getAccessToken()).thenReturn("expired", "expired", "fresh")
        whenever(tokenManager.getRefreshToken()).thenReturn("refresh", "refresh", "rotated-refresh")

        val refreshCalls = AtomicInteger(0)
        val interceptor = ApiSecurityInterceptor(
            apiKey = "key",
            tokenManager = tokenManager,
            refreshOverride = {
                refreshCalls.incrementAndGet()
                "fresh"
            }
        )
        val original = Request.Builder()
            .url("https://safa.masarax.com/api/customers")
            .get()
            .build()
        val chain: Interceptor.Chain = mock()
        whenever(chain.request()).thenReturn(original)
        var proceedCount = 0
        whenever(chain.proceed(any())).thenAnswer { invocation ->
            proceedCount++
            val request = invocation.arguments[0] as Request
            if (proceedCount == 1) {
                assertEquals("Bearer expired", request.header("Authorization"))
                response(request, 401, "Unauthorized")
            } else {
                assertEquals("Bearer fresh", request.header("Authorization"))
                assertEquals("true", request.header("X-SAFA-RETRY"))
                response(request, 200, "OK")
            }
        }

        val response = interceptor.intercept(chain)

        assertEquals(200, response.code)
        assertEquals(1, refreshCalls.get())
        verify(chain, times(2)).proceed(any())
        verify(tokenManager, never()).notifySessionInvalidated()
    }

    @Test
    fun concurrentTokenRotationReusesNewerStoredTokenWithoutSecondRefresh() {
        val tokenManager: TokenManager = mock()
        whenever(tokenManager.getDeviceToken()).thenReturn("device")
        whenever(tokenManager.getFingerprintToken()).thenReturn("fingerprint")
        whenever(tokenManager.getSessionToken()).thenReturn("session")
        whenever(tokenManager.getActiveAccountId()).thenReturn(7)
        whenever(tokenManager.getAccessToken()).thenReturn("old", "new", "new")
        whenever(tokenManager.getRefreshToken()).thenReturn("refresh")

        val refreshCalls = AtomicInteger(0)
        val interceptor = ApiSecurityInterceptor(
            apiKey = "key",
            tokenManager = tokenManager,
            refreshOverride = {
                refreshCalls.incrementAndGet()
                "unexpected"
            }
        )
        val original = Request.Builder()
            .url("https://safa.masarax.com/api/suppliers")
            .get()
            .build()
        val chain: Interceptor.Chain = mock()
        whenever(chain.request()).thenReturn(original)
        var proceedCount = 0
        whenever(chain.proceed(any())).thenAnswer { invocation ->
            proceedCount++
            val request = invocation.arguments[0] as Request
            if (proceedCount == 1) response(request, 401, "Unauthorized")
            else {
                assertEquals("Bearer new", request.header("Authorization"))
                response(request, 200, "OK")
            }
        }

        val response = interceptor.intercept(chain)

        assertEquals(200, response.code)
        assertEquals(0, refreshCalls.get())
        verify(chain, times(2)).proceed(any())
    }

    @Test
    fun failedRefreshInvalidatesSessionWithoutRetryLoop() {
        val tokenManager: TokenManager = mock()
        whenever(tokenManager.getDeviceToken()).thenReturn("device")
        whenever(tokenManager.getFingerprintToken()).thenReturn("fingerprint")
        whenever(tokenManager.getSessionToken()).thenReturn("session")
        whenever(tokenManager.getActiveAccountId()).thenReturn(7)
        whenever(tokenManager.getAccessToken()).thenReturn("expired")
        whenever(tokenManager.getRefreshToken()).thenReturn("refresh")

        val interceptor = ApiSecurityInterceptor(
            apiKey = "key",
            tokenManager = tokenManager,
            refreshOverride = { null }
        )
        val original = Request.Builder()
            .url("https://safa.masarax.com/api/transactions")
            .get()
            .build()
        val chain: Interceptor.Chain = mock()
        whenever(chain.request()).thenReturn(original)
        whenever(chain.proceed(any())).thenAnswer { invocation ->
            response(invocation.arguments[0] as Request, 401, "Unauthorized")
        }

        val response = interceptor.intercept(chain)

        assertEquals(401, response.code)
        verify(chain, times(1)).proceed(any())
        verify(tokenManager, times(1)).notifySessionInvalidated()
    }

    private fun response(request: Request, code: Int, message: String): Response =
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(message)
            .body("{}".toResponseBody())
            .build()
}
