package com.safa.account.feature.customer

import com.safa.account.data.model.Customer
import com.safa.account.data.model.RemittanceTransaction
import com.safa.account.data.money.MoneyMath
import com.safa.account.domain.feature.CustomerRepository
import com.safa.account.domain.feature.TransactionRepository
import java.math.BigDecimal
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

enum class CustomerSort {
    NEWEST,
    OLDEST,
    NAME,
    DUE,
    ADVANCE,
}

enum class CustomerBalanceFilter {
    ALL,
    DUE,
    ADVANCE,
}

sealed interface CustomerEvent {
    data class SearchChanged(val query: String) : CustomerEvent
    data class SortChanged(val sort: CustomerSort) : CustomerEvent
    data class BalanceFilterChanged(val filter: CustomerBalanceFilter) : CustomerEvent
    data class CustomerSelected(val customerId: Int) : CustomerEvent
    data object CustomerSelectionCleared : CustomerEvent
}

data class CustomerFeatureState(
    val customers: List<Customer> = emptyList(),
    val visibleCustomers: List<Customer> = emptyList(),
    val selectedCustomer: Customer? = null,
    val selectedTransactions: List<RemittanceTransaction> = emptyList(),
    val searchQuery: String = "",
    val sort: CustomerSort = CustomerSort.NEWEST,
    val balanceFilter: CustomerBalanceFilter = CustomerBalanceFilter.ALL,
)

/**
 * Android-independent presentation store for the customer feature.
 *
 * The store depends only on feature repository ports and immutable state/events,
 * so customer list/profile behavior can evolve without adding more state to the
 * application-wide SafaViewModel. Mutation/sync authority remains in the shared
 * repository/runtime until its own incremental extraction slice.
 */
class CustomerFeatureStore(
    customerRepository: CustomerRepository,
    transactionRepository: TransactionRepository,
    scope: CoroutineScope,
) {
    private data class Controls(
        val query: String,
        val sort: CustomerSort,
        val filter: CustomerBalanceFilter,
        val selectedCustomerId: Int?,
    )

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _sort = MutableStateFlow(CustomerSort.NEWEST)
    val sort: StateFlow<CustomerSort> = _sort.asStateFlow()

    private val _balanceFilter = MutableStateFlow(CustomerBalanceFilter.ALL)
    val balanceFilter: StateFlow<CustomerBalanceFilter> = _balanceFilter.asStateFlow()

    private val _selectedCustomerId = MutableStateFlow<Int?>(null)
    val selectedCustomerId: StateFlow<Int?> = _selectedCustomerId.asStateFlow()

    private val controls = combine(
        _searchQuery,
        _sort,
        _balanceFilter,
        _selectedCustomerId,
    ) { query, sort, filter, selectedCustomerId ->
        Controls(query, sort, filter, selectedCustomerId)
    }

    val state: StateFlow<CustomerFeatureState> = combine(
        customerRepository.items,
        transactionRepository.items,
        controls,
    ) { customers, transactions, controls ->
        val selected = controls.selectedCustomerId?.let { selectedId ->
            customers.firstOrNull { it.id == selectedId }
        }
        CustomerFeatureState(
            customers = customers,
            visibleCustomers = CustomerFeatureProjector.visibleCustomers(
                customers = customers,
                transactions = transactions,
                query = controls.query,
                sort = controls.sort,
                balanceFilter = controls.filter,
            ),
            selectedCustomer = selected,
            selectedTransactions = selected?.let { customer ->
                transactions.filter { it.customerId == customer.id }
            }.orEmpty(),
            searchQuery = controls.query,
            sort = controls.sort,
            balanceFilter = controls.filter,
        )
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = CustomerFeatureState(),
    )

    fun onEvent(event: CustomerEvent) {
        when (event) {
            is CustomerEvent.SearchChanged -> _searchQuery.value = event.query
            is CustomerEvent.SortChanged -> _sort.value = event.sort
            is CustomerEvent.BalanceFilterChanged -> _balanceFilter.value = event.filter
            is CustomerEvent.CustomerSelected -> _selectedCustomerId.value = event.customerId
            CustomerEvent.CustomerSelectionCleared -> _selectedCustomerId.value = null
        }
    }
}

object CustomerFeatureProjector {
    private val balanceThreshold: BigDecimal = MoneyMath.amount("0.05")

    fun balanceSar(
        customerId: Int,
        transactions: List<RemittanceTransaction>,
    ): BigDecimal {
        val customerTransactions = transactions.filter { it.customerId == customerId }
        val spent = customerTransactions.fold(MoneyMath.ZERO_AMOUNT) { total, transaction ->
            MoneyMath.add(total, transaction.amountSar)
        }
        val collected = customerTransactions.fold(MoneyMath.ZERO_AMOUNT) { total, transaction ->
            MoneyMath.add(total, transaction.sarCollected)
        }
        return MoneyMath.subtract(spent, collected)
    }

    fun visibleCustomers(
        customers: List<Customer>,
        transactions: List<RemittanceTransaction>,
        query: String,
        sort: CustomerSort,
        balanceFilter: CustomerBalanceFilter,
    ): List<Customer> {
        var result = if (query.isBlank()) {
            customers
        } else {
            val normalizedQuery = query.trim()
            customers.filter { customer ->
                customer.name.contains(normalizedQuery, ignoreCase = true) ||
                    customer.phone.contains(normalizedQuery, ignoreCase = true) ||
                    customer.address.contains(normalizedQuery, ignoreCase = true)
            }
        }

        result = when (balanceFilter) {
            CustomerBalanceFilter.ALL -> result
            CustomerBalanceFilter.DUE -> result.filter { customer ->
                balanceSar(customer.id, transactions) > balanceThreshold
            }
            CustomerBalanceFilter.ADVANCE -> result.filter { customer ->
                balanceSar(customer.id, transactions) < balanceThreshold.negate()
            }
        }

        return when (sort) {
            CustomerSort.NEWEST -> result.sortedByDescending { it.timestamp }
            CustomerSort.OLDEST -> result.sortedBy { it.timestamp }
            CustomerSort.NAME -> result.sortedBy { it.name.lowercase(Locale.ROOT) }
            CustomerSort.DUE -> result.sortedByDescending { customer ->
                balanceSar(customer.id, transactions)
            }
            CustomerSort.ADVANCE -> result.sortedBy { customer ->
                balanceSar(customer.id, transactions)
            }
        }
    }
}
