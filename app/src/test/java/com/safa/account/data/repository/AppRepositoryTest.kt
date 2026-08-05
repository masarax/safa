package com.safa.account.data.repository

import com.safa.account.data.dao.*
import com.safa.account.data.model.Customer
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.times

class AppRepositoryTest {

    private fun buildRepo(
        operatorDao: OperatorDao = mock(),
        customerDao: CustomerDao = mock(),
        supplierDao: SupplierDao = mock(),
        transactionDao: TransactionDao = mock(),
        supplierDepositDao: SupplierDepositDao = mock(),
        expenseIncomeDao: ExpenseIncomeDao = mock(),
        dailyRateDao: DailyRateDao = mock(),
        walletLedgerDao: WalletLedgerDao = mock(),
        walletBatchDao: WalletBatchDao = mock(),
    ) = AppRepository(
        operatorDao, customerDao, supplierDao, transactionDao,
        supplierDepositDao, expenseIncomeDao, dailyRateDao,
        walletLedgerDao, walletBatchDao
    )

    @Test
    fun `insertCustomer delegates to CustomerDao`() = runBlocking {
        val customerDao: CustomerDao = mock()
        val repo = buildRepo(customerDao = customerDao)
        val customer = Customer(name = "Test User", phone = "01711223344")

        repo.insertCustomer(customer)

        verify(customerDao, times(1)).insert(customer)
    }
}

