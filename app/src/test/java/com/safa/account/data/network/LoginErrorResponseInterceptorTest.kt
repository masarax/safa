package com.safa.account.data.network

import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class LoginErrorResponseInterceptorTest {
    private val request = Request.Builder()
        .url("https://safa.masarax.com/api/auth/login")
        .post("{}".toRequestBody())
        .build()

    private fun responseJson(code: Int, json: String): JSONObject {
        val chain = mock<okhttp3.Interceptor.Chain>()
        whenever(chain.request()).thenReturn(request)
        whenever(chain.proceed(request)).thenReturn(
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code).message("error").body(json.toResponseBody()).build()
        )
        return JSONObject(LoginErrorResponseInterceptor().intercept(chain).body!!.string())
    }

    @Test fun `401 remains generic credential error`() {
        val json = responseJson(401, "{\"message\":\"Invalid PIN\"}")
        assertEquals("INVALID_CREDENTIALS", json.getJSONObject("error").getString("code"))
        assertEquals("Mobile number or PIN is incorrect.", json.getString("message"))
    }

    @Test fun `403 revoked device stays distinguishable`() {
        val json = responseJson(403, "{\"error\":{\"code\":\"DEVICE_REVOKED\",\"message\":\"Device revoked\"}}")
        assertEquals("DEVICE_REVOKED", json.getJSONObject("error").getString("code"))
    }

    @Test fun `422 preserves validation code`() {
        val json = responseJson(422, "{\"error\":{\"code\":\"MOBILE_INVALID\",\"message\":\"Invalid mobile\"}}")
        assertEquals("MOBILE_INVALID", json.getJSONObject("error").getString("code"))
        assertEquals("Invalid mobile", json.getString("message"))
    }

    @Test fun `429 remains rate limited`() {
        val json = responseJson(429, "{\"message\":\"Too many attempts\"}")
        assertEquals("RATE_LIMITED", json.getJSONObject("error").getString("code"))
    }

    @Test fun `5xx remains distinguishable`() {
        val json = responseJson(503, "{\"message\":\"database failed\"}")
        assertEquals("SERVER_ERROR", json.getJSONObject("error").getString("code"))
    }

    @Test fun `malformed body remains controlled for caller classification`() {
        val response = run {
            val chain = mock<okhttp3.Interceptor.Chain>()
            whenever(chain.request()).thenReturn(request)
            whenever(chain.proceed(request)).thenReturn(
                Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(500).message("error").body("not-json".toResponseBody()).build()
            )
            LoginErrorResponseInterceptor().intercept(chain)
        }
        assertEquals("not-json", response.body!!.string())
    }
}
