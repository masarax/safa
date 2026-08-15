package com.safa.account.data.network

import org.junit.Assert.assertEquals
import org.junit.Test

class CanonicalAuthEndpointInterceptorTest {
    @Test
    fun `versioned login is routed to canonical auth endpoint`() {
        assertEquals(
            "/api/auth/login",
            CanonicalAuthEndpointInterceptor.canonicalAuthPath("/api/v1/auth/login")
        )
    }

    @Test
    fun `all versioned auth lifecycle routes stay on canonical auth service`() {
        listOf("session", "logout", "logout-all", "change-pin", "operators").forEach { endpoint ->
            assertEquals(
                "/api/auth/$endpoint",
                CanonicalAuthEndpointInterceptor.canonicalAuthPath("/api/v1/auth/$endpoint")
            )
        }
    }

    @Test
    fun `business v1 routes are not rewritten`() {
        assertEquals(
            "/api/v1/sync/down",
            CanonicalAuthEndpointInterceptor.canonicalAuthPath("/api/v1/sync/down")
        )
    }

    @Test
    fun `canonical auth routes remain unchanged`() {
        assertEquals(
            "/api/auth/login",
            CanonicalAuthEndpointInterceptor.canonicalAuthPath("/api/auth/login")
        )
    }
}
