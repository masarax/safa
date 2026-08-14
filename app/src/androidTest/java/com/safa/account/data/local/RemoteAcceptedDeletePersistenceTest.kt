package com.safa.account.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.safa.account.data.model.Customer
import com.safa.account.data.model.SyncStatus
import com.safa.account.data.network.DeleteConfirmationCoordinator
import com.safa.account.data.repository.AppRepository
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RemoteAcceptedDeletePersistenceTest {
    private lateinit var context: Context
    private lateinit var store: LocalFirstStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase("safa_local.db")
        DeleteConfirmationCoordinator.clearPermitsForTest()
        LocalAccountBoundary.bind(context, 88)
        store = LocalFirstStore(context)
    }

    @After
    fun tearDown() {
        DeleteConfirmationCoordinator.clearPermitsForTest()
        store.close()
        context.deleteDatabase("safa_local.db")
    }

    @Test
    fun directServerAcceptedDeleteBecomesSyncedTombstoneWithoutRetryOutbox() = runBlocking {
        val repository = AppRepository(context)
        val localId = repository.insertCustomer(
            Customer(
                serverId = 9801,
                name = "Delete Once",
                phone = "0500000001",
                syncStatus = SyncStatus.SYNCED,
            )
        )
        assertEquals(0, store.outboxCount())

        // ApiService leaves this exact one-shot acknowledgement only after the
        // direct confirmed DELETE has succeeded on the server.
        DeleteConfirmationCoordinator.grant("customers:9801")
        repository.deleteCustomerById(localId)

        val raw = store.getRecordPayloads("customers").single { it.localId == localId }
        val payload = JSONObject(raw.payload)
        assertEquals(9801, raw.serverId)
        assertEquals(LocalFirstStore.SYNCED, raw.syncStatus)
        assertNotNull(payload.opt("deleted_at"))
        assertFalse(store.hasPending("customers", localId))
        assertEquals(0, store.outboxCount())
        assertEquals(null, repository.getCustomerById(localId))
    }
}
