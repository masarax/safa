package com.safa.account.data.network

import com.safa.account.data.api.TokenManager
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ApiSecurityInterceptorHeaderPolicyTest {
    @Test
    fun `business request carries session proof but never refresh credential`() {
        val tokenManager: TokenManager = mock()
        whenever(tokenManager.getAccessToken()).thenReturn("access")
        whenever(tokenManager.getSessionToken()).thenReturn("session")
        whenever(tokenManager.getDeviceToken()).thenReturn("device")
        whenever(tokenManager.getFingerprintToken()).thenReturn("fingerprint")
        whenever(tokenManager.getActiveAccountId()).thenReturn(17)

        val original = Request.Builder()
            .url("https://safa.masarax.com/api/customers")
            .get()
            .build()
        val chain: Interceptor.Chain = mock()
        whenever(chain.request()).thenReturn(original)
        whenever(chain.proceed(any())).thenAnswer { invocation ->
            val request = invocation.arguments[0] as Request
            assertEquals("Bearer access", request.header("Authorization"))
            assertEquals("session", request.header("X-SAFA-SESSION-TOKEN"))
            assertEquals("device", request.header("X-SAFA-DEVICE-TOKEN"))
            assertEquals("fingerprint", request.header("X-SAFA-FINGERPRINT-TOKEN"))
            assertEquals("17", request.header("X-SAFA-ACCOUNT-ID"))
            assertNull(request.header("X-SAFA-REFRESH-TOKEN"))
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("{}".toResponseBody())
                .build()
        }

        val response = ApiSecurityInterceptor("key", tokenManager = tokenManager).intercept(chain)

        assertEquals(200, response.code)
        verify(tokenManager, never()).getRefreshToken()
    }
}
