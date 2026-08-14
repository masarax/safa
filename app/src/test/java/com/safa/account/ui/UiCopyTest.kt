package com.safa.account.ui

import com.safa.account.ui.components.StartupFailurePolicy
import com.safa.account.ui.components.UiCopy
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class UiCopyTest {
    @Test
    fun `common english labels stay compact`() {
        assertEquals("Customers", UiCopy.compact("Customer List"))
        assertEquals("New Customer", UiCopy.compact("Add New Customer"))
        assertEquals("Delete", UiCopy.compact("Delete Customer"))
        assertTrue(UiCopy.compact("Customer List").split(" ").size <= 2)
    }

    @Test
    fun `common bengali labels stay compact`() {
        assertEquals("কাস্টমার", UiCopy.compact("Customer List", "BN"))
        assertEquals("নতুন কাস্টমার", UiCopy.compact("Add New Customer", "BN"))
        assertEquals("মুছুন", UiCopy.compact("Delete Customer", "BN"))
        assertTrue(UiCopy.compact("Customer List", "BN").trim().isNotEmpty())
    }

    @Test
    fun `unknown copy remains unchanged`() {
        assertEquals("Known Customer", UiCopy.compact("Known Customer"))
    }

    @Test
    fun `database startup failure never leaks internal details`() {
        assertStartupFailureIsSafe(IllegalStateException("database init failed; password=secret-db"))
    }

    @Test
    fun `malformed configuration startup failure never leaks internal details`() {
        assertStartupFailureIsSafe(IllegalArgumentException("malformed api url https://internal.example/private"))
    }

    @Test
    fun `network client startup failure never leaks internal details`() {
        assertStartupFailureIsSafe(IOException("TLS handshake failed for private-host.internal"))
    }

    @Test
    fun `unexpected startup failure never leaks internal details`() {
        assertStartupFailureIsSafe(RuntimeException("unexpected stack detail /data/user/0/com.safa.account"))
    }

    @Test
    fun `startup failure copy is stable and localized`() {
        val cause = RuntimeException("must never be rendered")
        val en = StartupFailurePolicy.presentation("en", "ABCD1234", cause)
        val bn = StartupFailurePolicy.presentation("bn", "ABCD1234", cause)

        assertEquals("SAFA could not start", en.title)
        assertEquals("Support ID: ABCD1234", en.supportLabel)
        assertEquals("SAFA চালু করা যায়নি", bn.title)
        assertEquals("সহায়তা আইডি: ABCD1234", bn.supportLabel)
    }

    private fun assertStartupFailureIsSafe(cause: Throwable) {
        val presentation = StartupFailurePolicy.presentation("en", "SAFE1234", cause)
        val rendered = listOf(
            presentation.title,
            presentation.message,
            presentation.supportLabel,
            presentation.retryLabel
        ).joinToString(" ")

        assertFalse(rendered.contains(cause.message.orEmpty()))
        assertEquals("Support ID: SAFE1234", presentation.supportLabel)
        assertNull(StartupFailurePolicy.diagnosticThrowable(debug = false, cause = cause))
        assertSame(cause, StartupFailurePolicy.diagnosticThrowable(debug = true, cause = cause))
    }
}
