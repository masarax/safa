package com.safa.account.feature.customer

import com.safa.account.data.model.Customer
import com.safa.account.data.model.RemittanceTransaction
import com.safa.account.data.money.MoneyMath
import com.safa.account.domain.feature.CustomerRepository
import com.safa.account.domain.feature.TransactionRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CustomerFeatureStoreTest {
    @Test
    fun `search balance filter and sort preserve customer screen semantics`() = runTest {
        val customers = FakeCustomerRepository(
            listOf(
                Customer(id = 1, name = "Amina", phone = "050111", address = "Riyadh", timestamp = 100),
                Customer(id = 2, name = "Zahid", phone = "050222", address = "Jeddah", timestamp = 200),
                Customer(id = 3, name = "Bashir", phone = "050333", address = "Dammam", timestamp = 300),
            )
        )
        val transactions = FakeTransactionRepository(
            listOf(
                RemittanceTransaction(
                    id = 11,
                    customerId = 1,
                    amountSar = MoneyMath.amount("100"),
                    sarCollected = MoneyMath.amount("40"),
                ),
                RemittanceTransaction(
                    id = 12,
                    customerId = 2,
                    amountSar = MoneyMath.amount("100"),
                    sarCollected = MoneyMath.amount("130"),
                ),
                RemittanceTransaction(
                    id = 13,
                    customerId = 3,
                    amountSar = MoneyMath.amount("100"),
                    sarCollected = MoneyMath.amount("100"),
                ),
            )
        )
        val store = CustomerFeatureStore(customers, transactions, backgroundScope)
        runCurrent()

        assertEquals(listOf(3, 2, 1), store.state.value.visibleCustomers.map { it.id })

        store.onEvent(CustomerEvent.SearchChanged("riy"))
        runCurrent()
        assertEquals(listOf(1), store.state.value.visibleCustomers.map { it.id })

        store.onEvent(CustomerEvent.SearchChanged(""))
        store.onEvent(CustomerEvent.BalanceFilterChanged(CustomerBalanceFilter.DUE))
        runCurrent()
        assertEquals(listOf(1), store.state.value.visibleCustomers.map { it.id })

        store.onEvent(CustomerEvent.BalanceFilterChanged(CustomerBalanceFilter.ADVANCE))
        runCurrent()
        assertEquals(listOf(2), store.state.value.visibleCustomers.map { it.id })

        store.onEvent(CustomerEvent.BalanceFilterChanged(CustomerBalanceFilter.ALL))
        store.onEvent(CustomerEvent.SortChanged(CustomerSort.NAME))
        runCurrent()
        assertEquals(listOf(1, 3, 2), store.state.value.visibleCustomers.map { it.id })
    }

    @Test
    fun `selection exposes only the selected customer ledger and clears deterministically`() = runTest {
        val customers = FakeCustomerRepository(
            listOf(
                Customer(id = 7, name = "Selected"),
                Customer(id = 8, name = "Other"),
            )
        )
        val transactions = FakeTransactionRepository(
            listOf(
                RemittanceTransaction(id = 70, customerId = 7),
                RemittanceTransaction(id = 71, customerId = 7),
                RemittanceTransaction(id = 80, customerId = 8),
            )
        )
        val store = CustomerFeatureStore(customers, transactions, backgroundScope)

        store.onEvent(CustomerEvent.CustomerSelected(7))
        runCurrent()

        assertEquals(7, store.state.value.selectedCustomer?.id)
        assertEquals(listOf(70, 71), store.state.value.selectedTransactions.map { it.id })

        store.onEvent(CustomerEvent.CustomerSelectionCleared)
        runCurrent()

        assertNull(store.state.value.selectedCustomer)
        assertEquals(emptyList<RemittanceTransaction>(), store.state.value.selectedTransactions)
    }

    @Test
    fun `balance filter keeps existing five halala tolerance`() = runTest {
        val customers = FakeCustomerRepository(listOf(Customer(id = 1), Customer(id = 2)))
        val transactions = FakeTransactionRepository(
            listOf(
                RemittanceTransaction(
                    id = 1,
                    customerId = 1,
                    amountSar = MoneyMath.amount("10.04"),
                    sarCollected = MoneyMath.amount("10.00"),
                ),
                RemittanceTransaction(
                    id = 2,
                    customerId = 2,
                    amountSar = MoneyMath.amount("10.06"),
                    sarCollected = MoneyMath.amount("10.00"),
                ),
            )
        )
        val store = CustomerFeatureStore(customers, transactions, backgroundScope)

        store.onEvent(CustomerEvent.BalanceFilterChanged(CustomerBalanceFilter.DUE))
        runCurrent()

        assertEquals(listOf(2), store.state.value.visibleCustomers.map { it.id })
    }

    private class FakeCustomerRepository(initial: List<Customer>) : CustomerRepository {
        private val values = MutableStateFlow(initial)
        override val items: Flow<List<Customer>> = values

        override suspend fun insert(item: Customer): Int {
            val nextId = item.id.takeIf { it > 0 } ?: ((values.value.maxOfOrNull { it.id } ?: 0) + 1)
            values.value = values.value + item.copy(id = nextId)
            return nextId
        }

        override suspend fun update(item: Customer) {
            values.value = values.value.map { current -> if (current.id == item.id) item else current }
        }

        override suspend fun find(id: Int): Customer? = values.value.firstOrNull { it.id == id }

        override suspend fun delete(id: Int) {
            values.value = values.value.filterNot { it.id == id }
        }
    }

    private class FakeTransactionRepository(initial: List<RemittanceTransaction>) : TransactionRepository {
        private val values = MutableStateFlow(initial)
        override val items: Flow<List<RemittanceTransaction>> = values

        override suspend fun insert(item: RemittanceTransaction): Int {
            val nextId = item.id.takeIf { it > 0 } ?: ((values.value.maxOfOrNull { it.id } ?: 0) + 1)
            values.value = values.value + item.copy(id = nextId)
            return nextId
        }

        override suspend fun update(item: RemittanceTransaction) {
            values.value = values.value.map { current -> if (current.id == item.id) item else current }
        }

        override suspend fun find(id: Int): RemittanceTransaction? = values.value.firstOrNull { it.id == id }

        override suspend fun delete(id: Int) {
            values.value = values.value.filterNot { it.id == id }
        }
    }
}
