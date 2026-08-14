package com.safa.account.utils

import java.io.IOException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafaLoggerTest {
    private fun assertSanitized(throwable: Throwable, sensitiveText: String) {
        val summary = SafaLogger.safeThrowableSummary(throwable)
        assertTrue(summary.contains(throwable.javaClass.name))
        assertFalse(summary.contains(sensitiveText))
        assertFalse(summary.contains(throwable.message.orEmpty()))
    }

    @Test
    fun databaseInitializationFailureDoesNotExposeRawMessage() {
        assertSanitized(
            IllegalStateException("database=/data/user/0/com.safa.account/private.db"),
            "/data/user/0/com.safa.account/private.db"
        )
    }

    @Test
    fun malformedConfigurationFailureDoesNotExposeRawMessage() {
        assertSanitized(
            IllegalArgumentException("API_SECRET=internal-secret-value"),
            "internal-secret-value"
        )
    }

    @Test
    fun networkInitializationFailureDoesNotExposeRawMessage() {
        assertSanitized(
            IOException("Authorization: Bearer private-token"),
            "private-token"
        )
    }

    @Test
    fun unexpectedFailureDoesNotExposeRawMessageAndKeepsCauseType() {
        val cause = IllegalStateException("account=42")
        val failure = RuntimeException("unexpected internal state", cause)
        val summary = SafaLogger.safeThrowableSummary(failure)

        assertFalse(summary.contains("unexpected internal state"))
        assertFalse(summary.contains("account=42"))
        assertTrue(summary.contains(RuntimeException::class.java.name))
        assertTrue(summary.contains(IllegalStateException::class.java.name))
    }
}
