package com.safa.account.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OutboxProcessingRecoveryTest {
    private lateinit var context: Context
    private lateinit var store: LocalFirstStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase("safa_local.db")
        store = LocalFirstStore(context)
    }

    @After
    fun tearDown() {
        store.close()
        context.deleteDatabase("safa_local.db")
    }

    @Test
    fun resetProcessingMakesTransportInterruptedMutationImmediatelyReadyAgain() {
        val payload = "{\"local_id\":9001,\"name\":\"Offline Customer\"}"
        store.upsertRecord("customers", 9001, 0, payload, LocalFirstStore.PENDING)
        store.enqueue("customers", 9001, 0, "CREATE", payload)

        val leased = store.getReadyOutbox(10)
        assertEquals(1, leased.size)
        assertTrue(store.getReadyOutbox(10).isEmpty())

        store.resetProcessing()

        val retried = store.getReadyOutbox(10)
        assertEquals(1, retried.size)
        assertEquals("customers", retried.single().entity)
        assertEquals(9001, retried.single().localId)
    }

    @Test
    fun resetProcessingPromotesDeferredEditWithoutLosingIt() {
        val firstPayload = "{\"local_id\":9002,\"name\":\"Before\"}"
        val deferredPayload = "{\"local_id\":9002,\"name\":\"After\"}"
        store.upsertRecord("customers", 9002, 0, firstPayload, LocalFirstStore.PENDING)
        store.enqueue("customers", 9002, 0, "CREATE", firstPayload)
        assertEquals(1, store.getReadyOutbox(10).size)

        store.enqueue("customers", 9002, 0, "UPDATE", deferredPayload)
        store.resetProcessing()

        val retried = store.getReadyOutbox(10).single()
        assertEquals("UPDATE", retried.operation)
        assertTrue(retried.payload.contains("After"))
    }
}
