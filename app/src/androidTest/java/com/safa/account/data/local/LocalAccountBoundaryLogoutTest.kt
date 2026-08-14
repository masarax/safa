package com.safa.account.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalAccountBoundaryLogoutTest {
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
    fun destroyAccountStateRemovesCacheOutboxRevisionsAndBinding() {
        LocalAccountBoundary.bind(context, 71)
        store.upsertRecord(
            "customers",
            9101,
            9901,
            "{\"local_id\":9101,\"server_id\":9901,\"sync_version\":7,\"name\":\"Account A\"}",
            LocalFirstStore.SYNCED
        )
        store.recordServerVersion("customers", 9101, 9901, 7)
        store.enqueue(
            "transactions",
            9102,
            0,
            "CREATE",
            "{\"local_id\":9102,\"amount_sar\":\"10.00\"}"
        )

        LocalAccountBoundary.destroyAccountState(context)

        assertNull(LocalAccountBoundary.currentAccountId(context))
        assertTrue(store.getRecordPayloads("customers").isEmpty())
        assertTrue(store.getReadyOutbox().isEmpty())
        assertEquals(0, store.serverVersion("customers", 9101))
    }
}
