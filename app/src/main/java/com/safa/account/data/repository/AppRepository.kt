package com.safa.account.data.repository

import com.safa.account.data.dao.*
import com.safa.account.data.model.*
import kotlinx.coroutines.flow.Flow

class AppRepository(
    private val operatorDao: OperatorDao,
    private val customerDao: CustomerDao,
    private val supplierDao: SupplierDao,
    private val transactionDao: TransactionDao,
    private val supplierDepositDao: SupplierDepositDao,
    private val expenseIncomeDao: ExpenseIncomeDao,
    private val dailyRateDao: DailyRateDao,
    private val walletLedgerDao: WalletLedgerDao,
    private val walletBatchDao: WalletBatchDao,
    private val syncOutboxDao: SyncOutboxDao? = null
) {
    // ─── Sync Outbox ──────────────────────────────────────────────────────────
    suspend fun getPendingOutbox(): List<SyncOutbox> = syncOutboxDao?.getPendingOutbox() ?: emptyList()
    suspend fun enqueueOutbox(outbox: SyncOutbox): Long = syncOutboxDao?.insert(outbox) ?: -1L
    suspend fun updateOutboxStatus(id: Int, status: String) = syncOutboxDao?.updateStatus(id, status)
    suspend fun markOutboxFailed(id: Int, status: String, error: String?) = syncOutboxDao?.markFailed(id, status, error)
    suspend fun deleteOutboxById(id: Int) = syncOutboxDao?.deleteById(id)
    suspend fun purgeSyncedOutbox() = syncOutboxDao?.purgeSynced()
    // ─── Operators ────────────────────────────────────────────────────────────
    val allOperators: Flow<List<OperatorAccount>> = operatorDao.getAll()
    suspend fun insertOperator(op: OperatorAccount) = operatorDao.insert(op)
    suspend fun updateOperator(op: OperatorAccount) = operatorDao.update(op)
    suspend fun deleteOperator(op: OperatorAccount) = operatorDao.deleteById(op.id)
    suspend fun getOperatorByUsername(username: String) = operatorDao.getByUsername(username)
    suspend fun getOperatorByMobile(mobile: String) = operatorDao.getByMobile(mobile)

    // ─── Customers ────────────────────────────────────────────────────────────
    val allCustomers: Flow<List<Customer>> = customerDao.getAll()
    val allCustomersRaw: Flow<List<Customer>> = customerDao.getAllRaw()
    suspend fun getPendingCustomers(): List<Customer> = customerDao.getPending()
    suspend fun insertCustomer(c: Customer) = customerDao.insert(c)
    suspend fun updateCustomer(c: Customer) = customerDao.update(c)
    suspend fun deleteCustomerById(id: Int) = customerDao.deleteById(id)
    suspend fun softDeleteCustomerById(id: Int, deletedAt: Long = System.currentTimeMillis()) = customerDao.softDeleteById(id, deletedAt)
    suspend fun markCustomerSynced(id: Int, serverId: Int) = customerDao.markSynced(id, serverId)
    suspend fun markCustomerFailed(id: Int, error: String) = customerDao.markFailed(id, error)
    suspend fun incrementCustomerRetry(id: Int) = customerDao.incrementRetry(id)
    suspend fun resetCustomerRetry(id: Int, targetStatus: Int) = customerDao.resetRetryState(id, targetStatus)
    suspend fun getCustomerById(id: Int): Customer? = customerDao.getById(id)
    suspend fun retryFailedCustomer(id: Int) {
        val record = customerDao.getById(id) ?: return
        val targetStatus = when {
            record.deletedAt != null -> SyncStatus.PENDING_DELETE
            record.serverId > 0 -> SyncStatus.PENDING_UPDATE
            else -> SyncStatus.PENDING_CREATE
        }
        customerDao.resetRetryState(id, targetStatus)
    }

    // ─── Suppliers ────────────────────────────────────────────────────────────
    val allSuppliers: Flow<List<Supplier>> = supplierDao.getAll()
    val allSuppliersRaw: Flow<List<Supplier>> = supplierDao.getAllRaw()
    suspend fun getPendingSuppliers(): List<Supplier> = supplierDao.getPending()
    suspend fun insertSupplier(s: Supplier) = supplierDao.insert(s)
    suspend fun updateSupplier(s: Supplier) = supplierDao.update(s)
    suspend fun deleteSupplierById(id: Int) = supplierDao.deleteById(id)
    suspend fun softDeleteSupplierById(id: Int, deletedAt: Long = System.currentTimeMillis()) = supplierDao.softDeleteById(id, deletedAt)
    suspend fun markSupplierSynced(id: Int, serverId: Int) = supplierDao.markSynced(id, serverId)
    suspend fun markSupplierFailed(id: Int, error: String) = supplierDao.markFailed(id, error)
    suspend fun incrementSupplierRetry(id: Int) = supplierDao.incrementRetry(id)
    suspend fun resetSupplierRetry(id: Int, targetStatus: Int) = supplierDao.resetRetryState(id, targetStatus)
    suspend fun getSupplierById(id: Int): Supplier? = supplierDao.getById(id)
    suspend fun retryFailedSupplier(id: Int) {
        val record = supplierDao.getById(id) ?: return
        val targetStatus = when {
            record.deletedAt != null -> SyncStatus.PENDING_DELETE
            record.serverId > 0 -> SyncStatus.PENDING_UPDATE
            else -> SyncStatus.PENDING_CREATE
        }
        supplierDao.resetRetryState(id, targetStatus)
    }

    // ─── Transactions ─────────────────────────────────────────────────────────
    val allTransactions: Flow<List<RemittanceTransaction>> = transactionDao.getAll()
    val allTransactionsRaw: Flow<List<RemittanceTransaction>> = transactionDao.getAllRaw()
    suspend fun getPendingTransactions(): List<RemittanceTransaction> = transactionDao.getPending()
    suspend fun insertTransaction(tx: RemittanceTransaction) = transactionDao.insert(tx)
    suspend fun updateTransaction(tx: RemittanceTransaction) = transactionDao.update(tx)
    suspend fun deleteTransactionById(id: Int) = transactionDao.deleteById(id)
    suspend fun softDeleteTransactionById(id: Int, deletedAt: Long = System.currentTimeMillis()) = transactionDao.softDeleteById(id, deletedAt)
    suspend fun markTransactionSynced(id: Int, serverId: Int) = transactionDao.markSynced(id, serverId)
    suspend fun markTransactionFailed(id: Int, error: String) = transactionDao.markFailed(id, error)
    suspend fun incrementTransactionRetry(id: Int) = transactionDao.incrementRetry(id)
    suspend fun resetTransactionRetry(id: Int, targetStatus: Int) = transactionDao.resetRetryState(id, targetStatus)
    suspend fun getTransactionById(id: Int): RemittanceTransaction? = transactionDao.getById(id)
    suspend fun retryFailedTransaction(id: Int) {
        val record = transactionDao.getById(id) ?: return
        val targetStatus = when {
            record.deletedAt != null -> SyncStatus.PENDING_DELETE
            record.serverId > 0 -> SyncStatus.PENDING_UPDATE
            else -> SyncStatus.PENDING_CREATE
        }
        transactionDao.resetRetryState(id, targetStatus)
    }

    // ─── Supplier Deposits ────────────────────────────────────────────────────
    val allSupplierDeposits: Flow<List<SupplierDeposit>> = supplierDepositDao.getAll()
    val allSupplierDepositsRaw: Flow<List<SupplierDeposit>> = supplierDepositDao.getAllRaw()
    suspend fun getPendingSupplierDeposits(): List<SupplierDeposit> = supplierDepositDao.getPending()
    suspend fun insertSupplierDeposit(dep: SupplierDeposit) = supplierDepositDao.insert(dep)
    suspend fun updateSupplierDeposit(dep: SupplierDeposit) = supplierDepositDao.update(dep)
    suspend fun deleteSupplierDepositById(id: Int) = supplierDepositDao.deleteById(id)
    suspend fun softDeleteSupplierDepositById(id: Int, deletedAt: Long = System.currentTimeMillis()) = supplierDepositDao.softDeleteById(id, deletedAt)
    suspend fun markSupplierDepositSynced(id: Int, serverId: Int) = supplierDepositDao.markSynced(id, serverId)
    suspend fun markSupplierDepositFailed(id: Int, error: String) = supplierDepositDao.markFailed(id, error)
    suspend fun incrementSupplierDepositRetry(id: Int) = supplierDepositDao.incrementRetry(id)
    suspend fun resetSupplierDepositRetry(id: Int, targetStatus: Int) = supplierDepositDao.resetRetryState(id, targetStatus)
    suspend fun getSupplierDepositById(id: Int): SupplierDeposit? = supplierDepositDao.getById(id)
    suspend fun retryFailedSupplierDeposit(id: Int) {
        val record = supplierDepositDao.getById(id) ?: return
        val targetStatus = when {
            record.deletedAt != null -> SyncStatus.PENDING_DELETE
            record.serverId > 0 -> SyncStatus.PENDING_UPDATE
            else -> SyncStatus.PENDING_CREATE
        }
        supplierDepositDao.resetRetryState(id, targetStatus)
    }

    // ─── Expenses & Incomes ───────────────────────────────────────────────────
    val allExpensesIncomes: Flow<List<ExpenseIncome>> = expenseIncomeDao.getAll()
    val allExpensesIncomesRaw: Flow<List<ExpenseIncome>> = expenseIncomeDao.getAllRaw()
    suspend fun getPendingExpensesIncomes(): List<ExpenseIncome> = expenseIncomeDao.getPending()
    suspend fun insertExpenseIncome(e: ExpenseIncome) = expenseIncomeDao.insert(e)
    suspend fun updateExpenseIncome(e: ExpenseIncome) = expenseIncomeDao.update(e)
    suspend fun deleteExpenseIncomeById(id: Int) = expenseIncomeDao.deleteById(id)
    suspend fun softDeleteExpenseIncomeById(id: Int, deletedAt: Long = System.currentTimeMillis()) = expenseIncomeDao.softDeleteById(id, deletedAt)
    suspend fun markExpenseIncomeSynced(id: Int, serverId: Int) = expenseIncomeDao.markSynced(id, serverId)
    suspend fun markExpenseIncomeFailed(id: Int, error: String) = expenseIncomeDao.markFailed(id, error)
    suspend fun incrementExpenseIncomeRetry(id: Int) = expenseIncomeDao.incrementRetry(id)
    suspend fun resetExpenseIncomeRetry(id: Int, targetStatus: Int) = expenseIncomeDao.resetRetryState(id, targetStatus)
    suspend fun getExpenseIncomeById(id: Int): ExpenseIncome? = expenseIncomeDao.getById(id)
    suspend fun retryFailedExpenseIncome(id: Int) {
        val record = expenseIncomeDao.getById(id) ?: return
        val targetStatus = when {
            record.deletedAt != null -> SyncStatus.PENDING_DELETE
            record.serverId > 0 -> SyncStatus.PENDING_UPDATE
            else -> SyncStatus.PENDING_CREATE
        }
        expenseIncomeDao.resetRetryState(id, targetStatus)
    }

    // ─── Daily Rates ──────────────────────────────────────────────────────────
    val allDailyRates: Flow<List<DailyRate>> = dailyRateDao.getAll()
    suspend fun insertDailyRate(r: DailyRate) = dailyRateDao.insert(r)
    suspend fun getDailyRateByDate(date: String) = dailyRateDao.getByDate(date)

    // ─── Wallet Ledgers ───────────────────────────────────────────────────────
    val allWalletLedgers: Flow<List<WalletLedger>> = walletLedgerDao.getAll()
    val allWalletLedgersRaw: Flow<List<WalletLedger>> = walletLedgerDao.getAllRaw()
    suspend fun getPendingWalletLedgers(): List<WalletLedger> = walletLedgerDao.getPending()
    suspend fun insertWalletLedger(l: WalletLedger) = walletLedgerDao.insert(l)
    suspend fun updateWalletLedger(l: WalletLedger) = walletLedgerDao.update(l)
    suspend fun deleteWalletLedgerById(id: Int) = walletLedgerDao.deleteById(id)
    suspend fun softDeleteWalletLedgerById(id: Int, deletedAt: Long = System.currentTimeMillis()) = walletLedgerDao.softDeleteById(id, deletedAt)
    suspend fun markWalletLedgerSynced(id: Int, serverId: Int) = walletLedgerDao.markSynced(id, serverId)
    suspend fun markWalletLedgerFailed(id: Int, error: String) = walletLedgerDao.markFailed(id, error)
    suspend fun incrementWalletLedgerRetry(id: Int) = walletLedgerDao.incrementRetry(id)
    suspend fun resetWalletLedgerRetry(id: Int, targetStatus: Int) = walletLedgerDao.resetRetryState(id, targetStatus)
    suspend fun getWalletLedgerById(id: Int): WalletLedger? = walletLedgerDao.getById(id)
    suspend fun retryFailedWalletLedger(id: Int) {
        val record = walletLedgerDao.getById(id) ?: return
        val targetStatus = when {
            record.deletedAt != null -> SyncStatus.PENDING_DELETE
            record.serverId > 0 -> SyncStatus.PENDING_UPDATE
            else -> SyncStatus.PENDING_CREATE
        }
        walletLedgerDao.resetRetryState(id, targetStatus)
    }

    // ─── Wallet Batches ───────────────────────────────────────────────────────
    val allWalletBatches: Flow<List<WalletBatch>> = walletBatchDao.getAll()
    val allWalletBatchesRaw: Flow<List<WalletBatch>> = walletBatchDao.getAllRaw()
    suspend fun getPendingWalletBatches(): List<WalletBatch> = walletBatchDao.getPending()
    suspend fun insertWalletBatch(b: WalletBatch) = walletBatchDao.insert(b)
    suspend fun updateWalletBatch(b: WalletBatch) = walletBatchDao.update(b)
    suspend fun deleteWalletBatchById(id: Int) = walletBatchDao.deleteById(id)
    suspend fun softDeleteWalletBatchById(id: Int, deletedAt: Long = System.currentTimeMillis()) = walletBatchDao.softDeleteById(id, deletedAt)
    suspend fun deleteWalletBatchBySupplierDepositId(depositId: Int) = walletBatchDao.deleteBySupplierDepositId(depositId)
    suspend fun softDeleteWalletBatchBySupplierDepositId(depositId: Int, deletedAt: Long = System.currentTimeMillis()) = walletBatchDao.softDeleteBySupplierDepositId(depositId, deletedAt)
    suspend fun markWalletBatchSynced(id: Int, serverId: Int) = walletBatchDao.markSynced(id, serverId)
    suspend fun markWalletBatchFailed(id: Int, error: String) = walletBatchDao.markFailed(id, error)
    suspend fun incrementWalletBatchRetry(id: Int) = walletBatchDao.incrementRetry(id)
    suspend fun resetWalletBatchRetry(id: Int, targetStatus: Int) = walletBatchDao.resetRetryState(id, targetStatus)
    suspend fun getWalletBatchById(id: Int) = walletBatchDao.getById(id)
    suspend fun retryFailedWalletBatch(id: Int) {
        val record = walletBatchDao.getById(id) ?: return
        val targetStatus = when {
            record.deletedAt != null -> SyncStatus.PENDING_DELETE
            record.serverId > 0 -> SyncStatus.PENDING_UPDATE
            else -> SyncStatus.PENDING_CREATE
        }
        walletBatchDao.resetRetryState(id, targetStatus)
    }
}

