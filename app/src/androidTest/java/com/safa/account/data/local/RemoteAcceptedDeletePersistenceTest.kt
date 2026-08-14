package com.safa.account.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.safa.account.data.model.Customer
import com.safa.account.data.model.OutboxOperation
import com.safa.account.data.model.SyncOutbox
import com.safa.account.data.model.SyncStatus
import com.safa.account.data.network.DeleteConfirmationCoordinator
import com.safa.account.data.repository.AppRepository
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
        assertTrue(payload.optLong("deleted_at", 0L) > 0L)
        assertFalse(store.hasPending("customers", localId))
        assertEquals(0, store.outboxCount())
        assertNull(repository.getCustomerById(localId))
    }

    @Test
    fun legacyDeleteEnqueueCannotCreateUnconfirmedDestructiveMutation() = runBlocking {
        val repository = AppRepository(context)
        val localId = repository.insertCustomer(
            Customer(
                serverId = 9802,
                name = "Keep Me",
                phone = "0500000002",
                syncStatus = SyncStatus.SYNCED,
            )
        )

        // This mirrors the legacy ViewModel call that follows its repository
        // delete attempt. If the user cancelled confirmation, the canonical
        // repository mutation is absent and this compatibility call must no-op.
        repository.enqueueOutbox(
            SyncOutbox(
                entityType = "CUSTOMER",
                entityLocalId = localId,
                entityServerId = 9802,
                operation = OutboxOperation.DELETE,
                payloadJson = JSONObject(
                    mapOf("local_id" to localId, "server_id" to 9802)
                ).toString(),
            )
        )

        assertEquals(0, store.outboxCount())
        assertFalse(store.hasPending("customers", localId))
        assertNotNull(repository.getCustomerById(localId))
        val raw = store.getRecordPayloads("customers").single { it.localId == localId }
        assertEquals(LocalFirstStore.SYNCED, raw.syncStatus)
        assertFalse(JSONObject(raw.payload).has("deleted_at") && !JSONObject(raw.payload).isNull("deleted_at"))
    }
}
