package com.safa.account.data.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RetrofitClientUrlTest {
    @Test
    fun invalidUrlDoesNotFallBackToProduction() {
        assertThrows(IllegalArgumentException::class.java) {
            RetrofitClient.healthBaseUrl("not a url")
        }
    }

    @Test
    fun missingSchemeIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            RetrofitClient.healthBaseUrl("staging.example.test")
        }
    }

    @Test
    fun stagingHttpsRemainsInStaging() {
        assertEquals(
            "https://staging.example.test/api/",
            RetrofitClient.healthBaseUrl("https://staging.example.test")
        )
    }

    @Test
    fun productionHttpsRemainsProduction() {
        assertEquals(
            "https://safa.masarax.com/api/",
            RetrofitClient.healthBaseUrl("https://safa.masarax.com/api/v1/")
        )
    }

    @Test
    fun debugHttpIsExplicitAndDoesNotRedirectHosts() {
        assertEquals(
            "http://10.0.2.2:8000/api/",
            RetrofitClient.healthBaseUrl("http://10.0.2.2:8000")
        )
    }

    @Test
    fun embeddedCredentialsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            RetrofitClient.healthBaseUrl("https://user:pass@example.test")
        }
    }
}
