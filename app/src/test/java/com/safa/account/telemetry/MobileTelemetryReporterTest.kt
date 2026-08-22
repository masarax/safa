package com.safa.account.telemetry

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MobileTelemetryReporterTest {
    @Test
    fun payloadContainsOnlyBoundedTelemetryFields() {
        val raw = JSONObject().apply {
            put("id", "event-id")
            put("event_type", "sync_failure")
            put("release", "1.0.42")
            put("reason", "http_503")
            put("stack_fingerprint", "abcdef0123456789abcdef0123456789")
            put("duration_ms", 1234)
            put("bytes", 4567)
            put("pending_count", 8)
            put("oldest_pending_seconds", 90)
            put("access_token", "secret")
            put("receiver_account", "999999")
        }

        val payload = MobileTelemetryReporter.payload(raw)

        assertEquals("sync_failure", payload["event_type"])
        assertEquals("1.0.42", payload["release"])
        assertTrue(payload.containsKey("stack_fingerprint"))
        assertFalse(payload.containsKey("id"))
        assertFalse(payload.containsKey("access_token"))
        assertFalse(payload.containsKey("receiver_account"))
        assertFalse(payload.toString().contains("secret"))
        assertFalse(payload.toString().contains("999999"))
    }
}
