package com.safa.account.data.api

import com.safa.account.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RetrofitClientIdentityTest {
    @Test
    fun blankStoredClientIdFallsBackToBuildClientId() {
        assertTrue(BuildConfig.SAFA_API_KEY.isNotBlank())
        assertEquals(BuildConfig.SAFA_API_KEY.trim(), RetrofitClient.effectiveApiKey(""))
        assertEquals(BuildConfig.SAFA_API_KEY.trim(), RetrofitClient.effectiveApiKey("   "))
    }

    @Test
    fun explicitClientIdStillWins() {
        assertEquals("custom-client", RetrofitClient.effectiveApiKey(" custom-client "))
    }
}
