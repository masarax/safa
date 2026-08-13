package com.safa.account

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.safa.account.data.network.ApiLoginError
import com.safa.account.data.network.ApiLoginErrorParser
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun useAppContext() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.safa.account", appContext.packageName)
    }

    @Test
    fun login401_isGenericCredentialError() {
        val error = ApiLoginErrorParser.fromHttp(401, "{\"message\":\"wrong PIN\"}")
        assertTrue(error is ApiLoginError.Credentials)
        assertEquals("INVALID_CREDENTIALS", error.code)
    }

    @Test
    fun login403_inactiveAccountIsAuthorizationError() {
        val error = ApiLoginErrorParser.fromHttp(403, "{\"code\":\"ACCOUNT_INACTIVE\"}")
        assertTrue(error is ApiLoginError.Authorization)
        assertEquals("ACCOUNT_INACTIVE", error.code)
    }

    @Test
    fun login403_revokedDeviceIsAuthorizationError() {
        val error = ApiLoginErrorParser.fromHttp(403, "{\"error\":{\"code\":\"DEVICE_REVOKED\"}}")
        assertTrue(error is ApiLoginError.Authorization)
        assertEquals("DEVICE_REVOKED", error.code)
    }

    @Test
    fun login422_readsLaravelValidationError() {
        val error = ApiLoginErrorParser.fromHttp(422, "{\"errors\":{\"pin\":[\"The pin must be 6 digits.\"]}}")
        assertTrue(error is ApiLoginError.Validation)
        assertEquals("The pin must be 6 digits.", error.message)
    }

    @Test
    fun login429_preservesRetryMetadata() {
        val error = ApiLoginErrorParser.fromHttp(429, "{\"message\":\"Too many attempts.\"}", "30")
        assertTrue(error is ApiLoginError.Throttled)
        assertEquals(30L, (error as ApiLoginError.Throttled).retryAfterSeconds)
    }

    @Test
    fun login5xx_isServerError() {
        val error = ApiLoginErrorParser.fromHttp(503, "not-json")
        assertTrue(error is ApiLoginError.Server)
        assertEquals("SERVER_ERROR", error.code)
    }

    @Test
    fun malformedResponse_isControlled() {
        val error = ApiLoginErrorParser.fromHttp(418, "not-json")
        assertTrue(error is ApiLoginError.Unexpected)
        assertEquals("UNEXPECTED_RESPONSE", error.code)
    }

    @Test
    fun timeout_isNetworkError() {
        val error = ApiLoginErrorParser.fromThrowable(java.net.SocketTimeoutException())
        assertEquals("TIMEOUT", error.code)
    }
}
