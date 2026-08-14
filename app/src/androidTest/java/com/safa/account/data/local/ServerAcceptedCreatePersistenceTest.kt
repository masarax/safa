package com.safa.account.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.safa.account.data.model.Customer
import com.safa.account.data.model.RemittanceTransaction
import com.safa.account.data.model.SyncStatus
import com.safa.account.data.repository.AppRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ServerAcceptedCreatePersistenceTest {
    private lateinit var context: Context
    private lateinit var store: LocalFirstStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase("safa_local.db")
        LocalAccountBoundary.bind(context, 77)
        store = LocalFirstStore(context)
    }

    @After
    fun tearDown() {
        store.close()
        context.deleteDatabase("safa_local.db")
    }

    @Test
    fun serverAcceptedCreatesAreCachedAsSyncedWithoutCreateOutbox() = runBlocking {
        val repository = AppRepository(context)

        val customerLocalId = repository.insertCustomer(
            Customer(
                serverId = 9701,
                name = "Already Accepted",
                phone = "0500000000",
                syncStatus = SyncStatus.SYNCED,
            )
        )
        val transactionLocalId = repository.insertTransaction(
            RemittanceTransaction(
                serverId = 9702,
                amountSar = 10.0,
                customerRate = 32.0,
                supplierRate = 31.5,
                amountBdt = 320.0,
                syncStatus = SyncStatus.SYNCED,
            )
        )

        val customer = store.getRecordPayloads("customers").single { it.localId == customerLocalId }
        val transaction = store.getRecordPayloads("transactions").single { it.localId == transactionLocalId }

        assertEquals(9701, customer.serverId)
        assertEquals(LocalFirstStore.SYNCED, customer.syncStatus)
        assertFalse(store.hasPending("customers", customerLocalId))

        assertEquals(9702, transaction.serverId)
        assertEquals(LocalFirstStore.SYNCED, transaction.syncStatus)
        assertFalse(store.hasPending("transactions", transactionLocalId))
        assertEquals(0, store.outboxCount())
    }
}
