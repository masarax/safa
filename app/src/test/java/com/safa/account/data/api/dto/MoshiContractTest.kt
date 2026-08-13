package com.safa.account.data.api.dto

import com.squareup.moshi.Moshi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MoshiContractTest {
    private val moshi = Moshi.Builder().build()

    @Test
    fun syncDownToleratesUnknownFieldsAndKeepsDefaults() {
        val adapter = moshi.adapter(SyncDownResponse::class.java)
        val decoded = requireNotNull(
            adapter.fromJson(
                """{
                    "status":"success",
                    "account_id":7,
                    "customers":[],
                    "future_server_field":{"ignored":true}
                }"""
            )
        )

        assertEquals("success", decoded.status)
        assertEquals(7, decoded.accountId)
        assertEquals(1, decoded.page)
        assertEquals(100, decoded.perPage)
        assertFalse(decoded.hasMore)
        assertTrue(decoded.transactions.isEmpty())
    }

    @Test
    fun nullableAndLocalizedGraphQlFieldsRoundTrip() {
        val adapter = moshi.adapter(GraphQlError::class.java)
        val original = GraphQlError(
            message = "ভুল তথ্য — خطأ",
            locations = null,
            path = null,
            extensions = null
        )

        val decoded = requireNotNull(adapter.fromJson(adapter.toJson(original)))
        assertEquals(original.message, decoded.message)
        assertNull(decoded.locations)
        assertNull(decoded.path)
        assertNull(decoded.extensions)
    }
}
