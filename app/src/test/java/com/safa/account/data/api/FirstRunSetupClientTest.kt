package com.safa.account.data.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FirstRunSetupClientTest {
    @Test
    fun setupPhaseIsReadOnlyFromExplicitSetupRequired503() {
        assertEquals(
            "database",
            FirstRunSetupClient.phaseFromHealthResponse(
                503,
                "{\"status\":\"setup_required\",\"phase\":\"database\",\"setup_path\":\"/setup\"}"
            )
        )
        assertEquals(
            "admin",
            FirstRunSetupClient.phaseFromHealthResponse(
                503,
                "{\"status\":\"setup_required\",\"phase\":\"admin\"}"
            )
        )
        assertNull(FirstRunSetupClient.phaseFromHealthResponse(503, "{\"status\":\"update_required\"}"))
        assertNull(FirstRunSetupClient.phaseFromHealthResponse(200, "{\"status\":\"setup_required\",\"phase\":\"database\"}"))
        assertNull(FirstRunSetupClient.phaseFromHealthResponse(503, "not-json"))
    }

    @Test
    fun apiBaseUrlResolvesToSameOriginSetupGateway() {
        assertEquals("https://safa.masarax.com/setup", FirstRunSetupClient.webSetupUrl("https://safa.masarax.com/api/"))
        assertEquals("https://safa.masarax.com/setup", FirstRunSetupClient.webSetupUrl("https://safa.masarax.com/api/v1/"))
        assertEquals("https://example.test/safa/setup", FirstRunSetupClient.webSetupUrl("https://example.test/safa/api/"))
        assertEquals("https://example.test/safa/setup", FirstRunSetupClient.webSetupUrl("https://example.test/safa/api/v1"))
    }
}
