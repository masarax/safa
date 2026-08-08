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
) {
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
    suspend fun getWalletBatchById(id: Int) = walletBatchDao.getById(id)
}

