package com.safa.account.domain.feature.customer

import com.safa.account.data.model.Customer
import com.safa.account.data.model.SyncStatus
import com.safa.account.domain.feature.CustomerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomerUseCaseTest {
    @Test
    fun `online create persists accepted server identity without outbox`() = runBlocking {
        val repository = FakeCustomerRepository()
        val remote = FakeRemoteGateway(createResult = CustomerRemoteCreateResult.Created(42))
        val outbox = FakeOutboxGateway()
        val sync = FakeSyncGateway(online = true)
        val useCase = CustomerUseCase(repository, remote, outbox, sync)

        val result = useCase.create("Rahim", "0500000000", "Riyadh", userId = 7)

        assertEquals(CustomerCommandResult.Completed, result)
        val stored = repository.current.single()
        assertEquals(42, stored.serverId)
        assertEquals(SyncStatus.SYNCED, stored.syncStatus)
        assertTrue(outbox.creates.isEmpty())
        assertEquals(0, sync.backgroundRequests)
    }

    @Test
    fun `create network failure falls back to durable local outbox`() = runBlocking {
        val repository = FakeCustomerRepository()
        val remote = FakeRemoteGateway(createFailure = IllegalStateException("network"))
        val outbox = FakeOutboxGateway()
        val sync = FakeSyncGateway(online = true)
        val useCase = CustomerUseCase(repository, remote, outbox, sync)

        val result = useCase.create("Karim", "0500000001", "Jeddah", userId = 9)

        assertEquals(CustomerCommandResult.Completed, result)
        val stored = repository.current.single()
        assertEquals(SyncStatus.PENDING_CREATE, stored.syncStatus)
        assertEquals(9, outbox.creates.single().userId)
        assertEquals(stored.id, outbox.creates.single().localId)
        assertEquals(1, sync.backgroundRequests)
    }

    @Test
    fun `server rejection does not convert authoritative rejection into offline create`() = runBlocking {
        val repository = FakeCustomerRepository()
        val remote = FakeRemoteGateway(createResult = CustomerRemoteCreateResult.Rejected(422))
        val outbox = FakeOutboxGateway()
        val sync = FakeSyncGateway(online = true)
        val useCase = CustomerUseCase(repository, remote, outbox, sync)

        val result = useCase.create("Invalid", "0500000002", "", userId = 3)

        assertEquals(CustomerCommandResult.Rejected("Create customer", 422), result)
        assertTrue(repository.current.isEmpty())
        assertTrue(outbox.creates.isEmpty())
        assertEquals(0, sync.backgroundRequests)
    }

    @Test
    fun `offline delete tombstones locally and queues server identity`() = runBlocking {
        val existing = Customer(id = 11, serverId = 81, name = "Delete", phone = "1")
        val repository = FakeCustomerRepository(existing)
        val outbox = FakeOutboxGateway()
        val sync = FakeSyncGateway(online = false)
        val useCase = CustomerUseCase(repository, FakeRemoteGateway(), outbox, sync)

        val result = useCase.delete(id = 11, userId = 5)

        assertEquals(CustomerCommandResult.Completed, result)
        assertEquals(listOf(11), repository.softDeletedIds)
        assertTrue(repository.removedAcceptedIds.isEmpty())
        assertEquals(DeleteRecord(5, 11, 81), outbox.deletes.single())
        assertEquals(1, sync.backgroundRequests)
    }

    @Test
    fun `accepted remote delete removes local record without enqueueing tombstone`() = runBlocking {
        val existing = Customer(id = 12, serverId = 82, name = "Delete", phone = "2")
        val repository = FakeCustomerRepository(existing)
        val outbox = FakeOutboxGateway()
        val sync = FakeSyncGateway(online = true)
        val remote = FakeRemoteGateway(deleteResult = CustomerRemoteDeleteResult.Deleted)
        val useCase = CustomerUseCase(repository, remote, outbox, sync)

        val result = useCase.delete(id = 12, userId = 6)

        assertEquals(CustomerCommandResult.Completed, result)
        assertEquals(listOf(12), repository.removedAcceptedIds)
        assertTrue(repository.softDeletedIds.isEmpty())
        assertTrue(outbox.deletes.isEmpty())
        assertEquals(0, sync.backgroundRequests)
    }

    @Test
    fun `update marks synced customer pending before synchronous sync`() = runBlocking {
        val existing = Customer(id = 13, serverId = 83, name = "Before", phone = "3")
        val repository = FakeCustomerRepository(existing)
        val sync = FakeSyncGateway(online = true)
        val useCase = CustomerUseCase(repository, FakeRemoteGateway(), FakeOutboxGateway(), sync)

        val result = useCase.update(existing.copy(name = "After"))

        assertEquals(CustomerCommandResult.Completed, result)
        assertEquals("After", repository.current.single().name)
        assertEquals(SyncStatus.PENDING_UPDATE, repository.current.single().syncStatus)
        assertEquals(1, sync.synchronousRequests)
    }

    private class FakeCustomerRepository(vararg initial: Customer) : CustomerRepository {
        private val state = MutableStateFlow(initial.toList())
        override val items: Flow<List<Customer>> = state
        val current: List<Customer> get() = state.value
        val softDeletedIds = mutableListOf<Int>()
        val removedAcceptedIds = mutableListOf<Int>()
        private var nextId = (initial.maxOfOrNull { it.id } ?: 0) + 1

        override suspend fun insert(item: Customer): Int {
            val stored = if (item.id > 0) item else item.copy(id = nextId++)
            state.value = state.value.filterNot { it.id == stored.id } + stored
            return stored.id
        }

        override suspend fun update(item: Customer) {
            state.value = state.value.map { if (it.id == item.id) item else it }
        }

        override suspend fun find(id: Int): Customer? = state.value.find { it.id == id }

        override suspend fun delete(id: Int) {
            softDeletedIds += id
        }

        override suspend fun removeAccepted(id: Int) {
            removedAcceptedIds += id
            state.value = state.value.filterNot { it.id == id }
        }
    }

    private class FakeRemoteGateway(
        private val createResult: CustomerRemoteCreateResult = CustomerRemoteCreateResult.Created(1),
        private val deleteResult: CustomerRemoteDeleteResult = CustomerRemoteDeleteResult.Deleted,
        private val createFailure: Exception? = null,
        private val deleteFailure: Exception? = null,
    ) : CustomerRemoteGateway {
        override suspend fun create(name: String, phone: String, address: String): CustomerRemoteCreateResult {
            createFailure?.let { throw it }
            return createResult
        }

        override suspend fun delete(serverId: Int): CustomerRemoteDeleteResult {
            deleteFailure?.let { throw it }
            return deleteResult
        }
    }

    private data class CreateRecord(
        val userId: Int,
        val localId: Int,
        val name: String,
        val phone: String,
        val address: String,
    )

    private data class DeleteRecord(val userId: Int, val localId: Int, val serverId: Int)

    private class FakeOutboxGateway : CustomerOutboxGateway {
        val creates = mutableListOf<CreateRecord>()
        val deletes = mutableListOf<DeleteRecord>()

        override suspend fun enqueueCreate(
            userId: Int,
            localId: Int,
            name: String,
            phone: String,
            address: String,
        ) {
            creates += CreateRecord(userId, localId, name, phone, address)
        }

        override suspend fun enqueueDelete(userId: Int, localId: Int, serverId: Int) {
            deletes += DeleteRecord(userId, localId, serverId)
        }
    }

    private class FakeSyncGateway(private val online: Boolean) : CustomerSyncGateway {
        var backgroundRequests = 0
        var synchronousRequests = 0

        override fun isOnline(): Boolean = online

        override fun requestBackgroundSync() {
            backgroundRequests++
        }

        override suspend fun syncNow() {
            synchronousRequests++
        }
    }
}
