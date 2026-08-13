package com.safa.account.data.network

import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class LoginErrorResponseInterceptorTest {
    private val request = Request.Builder()
        .url("https://safa.masarax.com/api/auth/login")
        .post("{}".toResponseBody())
        .build()

    private fun transformed(code: Int, json: String): String {
        val chain = mock<okhttp3.Interceptor.Chain>()
        whenever(chain.request()).thenReturn(request)
        whenever(chain.proceed(request)).thenReturn(
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("error")
                .body(json.toResponseBody())
                .build()
        )

        return LoginErrorResponseInterceptor()
            .intercept(chain)
            .body!!
            .string()
    }

    @Test
    fun `401 remains generic credential error`() {
        assertEquals("Mobile number or PIN is incorrect.", transformed(401, "{\"message\":\"Invalid 6-Digit PIN.\"}"))
    }

    @Test
    fun `422 preserves validation error`() {
        assertEquals("Validation error: Invalid mobile number.", transformed(422, "{\"message\":\"Invalid mobile number.\"}"))
    }

    @Test
    fun `429 is exposed as rate limit`() {
        assertEquals("Rate limit: Too many login attempts.", transformed(429, "{\"message\":\"Too many login attempts.\"}"))
    }

    @Test
    fun `5xx is exposed as server error`() {
        assertEquals("Server error: Authentication backend failed.", transformed(503, "{\"message\":\"Authentication backend failed.\"}"))
    }
}
