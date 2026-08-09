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
    fun insertCustomerDelegatesToCustomerDao() {
        runBlocking {
            val customerDao: CustomerDao = mock()
            val repo = buildRepo(customerDao = customerDao)
            val customer = Customer(name = "Test User", phone = "01711223344")

            repo.insertCustomer(customer)

            verify(customerDao, times(1)).insert(customer)
        }
    }

    @Test
    fun manualRetryResetsStateForPendingCreate() {
        runBlocking {
            val customerDao: CustomerDao = mock()
            val repo = buildRepo(customerDao = customerDao)
            val failedCustomer = Customer(id = 10, serverId = 0, name = "Failed", phone = "01700", syncStatus = com.safa.account.data.model.SyncStatus.SYNC_FAILED, retryCount = 5, deletedAt = null)

            org.mockito.kotlin.whenever(customerDao.getById(10)).thenReturn(failedCustomer)

            repo.retryFailedCustomer(10)

            // Should reset to PENDING_CREATE (0) because serverId == 0 and deletedAt == null
            verify(customerDao, times(1)).resetRetryState(10, com.safa.account.data.model.SyncStatus.PENDING_CREATE)
        }
    }

    @Test
    fun manualRetryPreservesPendingUpdateState() {
        runBlocking {
            val customerDao: CustomerDao = mock()
            val repo = buildRepo(customerDao = customerDao)
            val failedUpdate = Customer(id = 20, serverId = 55, name = "Updated", phone = "01800", syncStatus = com.safa.account.data.model.SyncStatus.SYNC_FAILED, retryCount = 5, deletedAt = null)

            org.mockito.kotlin.whenever(customerDao.getById(20)).thenReturn(failedUpdate)

            repo.retryFailedCustomer(20)

            // MUST preserve PENDING_UPDATE (2) because serverId > 0 and deletedAt == null
            verify(customerDao, times(1)).resetRetryState(20, com.safa.account.data.model.SyncStatus.PENDING_UPDATE)
        }
    }

    @Test
    fun manualRetryPreservesPendingDeleteState() {
        runBlocking {
            val customerDao: CustomerDao = mock()
            val repo = buildRepo(customerDao = customerDao)
            val failedDelete = Customer(id = 30, serverId = 66, name = "Deleted", phone = "01900", syncStatus = com.safa.account.data.model.SyncStatus.SYNC_FAILED, retryCount = 5, deletedAt = 1700000000L)

            org.mockito.kotlin.whenever(customerDao.getById(30)).thenReturn(failedDelete)

            repo.retryFailedCustomer(30)

            // MUST preserve PENDING_DELETE (3) because deletedAt != null
            verify(customerDao, times(1)).resetRetryState(30, com.safa.account.data.model.SyncStatus.PENDING_DELETE)
        }
    }
}


