package com.safa.account.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LoginErrorResponseInterceptorTest {
    @Test fun `401 is generic credential error`() {
        val error = ApiLoginErrorParser.fromHttp(401, "{\"error\":{\"code\":\"INVALID_CREDENTIALS\"}}")
        assertTrue(error is ApiLoginError.Credentials)
        assertEquals("INVALID_CREDENTIALS", error.code)
    }

    @Test fun `403 revoked device is distinguishable`() {
        val error = ApiLoginErrorParser.fromHttp(403, "{\"error\":{\"code\":\"DEVICE_REVOKED\"}}")
        assertEquals("DEVICE_REVOKED", error.code)
    }

    @Test fun `403 inactive account is distinguishable`() {
        val error = ApiLoginErrorParser.fromHttp(403, "{\"error\":{\"code\":\"ACCOUNT_INACTIVE\"}}")
        assertEquals("ACCOUNT_INACTIVE", error.code)
    }

    @Test fun `422 preserves the code but never renders an untrusted server message`() {
        val error = ApiLoginErrorParser.fromHttp(422, "{\"error\":{\"code\":\"MOBILE_INVALID\",\"message\":\"password=server-secret\"}}")
        assertEquals("MOBILE_INVALID", error.code)
        assertEquals("Please check the mobile number and PIN format.", error.message)
    }

    @Test fun `429 is rate limited`() {
        val error = ApiLoginErrorParser.fromHttp(429, "{\"message\":\"Too many attempts\"}", "30")
        assertTrue(error is ApiLoginError.Throttled)
        assertEquals(30L, (error as ApiLoginError.Throttled).retryAfterSeconds)
    }

    @Test fun `5xx is server error`() {
        val error = ApiLoginErrorParser.fromHttp(503, "not-json")
        assertTrue(error is ApiLoginError.Server)
    }

    @Test fun `timeout is network error`() {
        val error = ApiLoginErrorParser.fromThrowable(java.net.SocketTimeoutException())
        assertEquals("TIMEOUT", error.code)
    }

    @Test fun `malformed non-server response is controlled`() {
        val error = ApiLoginErrorParser.fromHttp(418, "not-json")
        assertEquals("UNEXPECTED_RESPONSE", error.code)
    }
}
