package com.safa.account.ui

import com.safa.account.ui.components.UiCopy
import org.junit.Assert.assertEquals
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
}
