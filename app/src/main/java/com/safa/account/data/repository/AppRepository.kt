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

    // ─── Customers ────────────────────────────────────────────────────────────
    val allCustomers: Flow<List<Customer>> = customerDao.getAll()
    val allCustomersRaw: Flow<List<Customer>> = customerDao.getAllRaw()
    suspend fun insertCustomer(c: Customer) = customerDao.insert(c)
    suspend fun updateCustomer(c: Customer) = customerDao.update(c)
    suspend fun deleteCustomerById(id: Int) = customerDao.deleteById(id)
    suspend fun softDeleteCustomerById(id: Int, deletedAt: Long = System.currentTimeMillis()) = customerDao.softDeleteById(id, deletedAt)

    // ─── Suppliers ────────────────────────────────────────────────────────────
    val allSuppliers: Flow<List<Supplier>> = supplierDao.getAll()
    val allSuppliersRaw: Flow<List<Supplier>> = supplierDao.getAllRaw()
    suspend fun insertSupplier(s: Supplier) = supplierDao.insert(s)
    suspend fun updateSupplier(s: Supplier) = supplierDao.update(s)
    suspend fun deleteSupplierById(id: Int) = supplierDao.deleteById(id)
    suspend fun softDeleteSupplierById(id: Int, deletedAt: Long = System.currentTimeMillis()) = supplierDao.softDeleteById(id, deletedAt)

    // ─── Transactions ─────────────────────────────────────────────────────────
    val allTransactions: Flow<List<RemittanceTransaction>> = transactionDao.getAll()
    val allTransactionsRaw: Flow<List<RemittanceTransaction>> = transactionDao.getAllRaw()
    suspend fun insertTransaction(tx: RemittanceTransaction) = transactionDao.insert(tx)
    suspend fun updateTransaction(tx: RemittanceTransaction) = transactionDao.update(tx)
    suspend fun deleteTransactionById(id: Int) = transactionDao.deleteById(id)
    suspend fun softDeleteTransactionById(id: Int, deletedAt: Long = System.currentTimeMillis()) = transactionDao.softDeleteById(id, deletedAt)

    // ─── Supplier Deposits ────────────────────────────────────────────────────
    val allSupplierDeposits: Flow<List<SupplierDeposit>> = supplierDepositDao.getAll()
    val allSupplierDepositsRaw: Flow<List<SupplierDeposit>> = supplierDepositDao.getAllRaw()
    suspend fun insertSupplierDeposit(dep: SupplierDeposit) = supplierDepositDao.insert(dep)
    suspend fun updateSupplierDeposit(dep: SupplierDeposit) = supplierDepositDao.update(dep)
    suspend fun deleteSupplierDepositById(id: Int) = supplierDepositDao.deleteById(id)
    suspend fun softDeleteSupplierDepositById(id: Int, deletedAt: Long = System.currentTimeMillis()) = supplierDepositDao.softDeleteById(id, deletedAt)

    // ─── Expenses & Incomes ───────────────────────────────────────────────────
    val allExpensesIncomes: Flow<List<ExpenseIncome>> = expenseIncomeDao.getAll()
    val allExpensesIncomesRaw: Flow<List<ExpenseIncome>> = expenseIncomeDao.getAllRaw()
    suspend fun insertExpenseIncome(e: ExpenseIncome) = expenseIncomeDao.insert(e)
    suspend fun deleteExpenseIncomeById(id: Int) = expenseIncomeDao.deleteById(id)
    suspend fun softDeleteExpenseIncomeById(id: Int, deletedAt: Long = System.currentTimeMillis()) = expenseIncomeDao.softDeleteById(id, deletedAt)

    // ─── Daily Rates ──────────────────────────────────────────────────────────
    val allDailyRates: Flow<List<DailyRate>> = dailyRateDao.getAll()
    suspend fun insertDailyRate(r: DailyRate) = dailyRateDao.insert(r)
    suspend fun getDailyRateByDate(date: String) = dailyRateDao.getByDate(date)

    // ─── Wallet Ledgers ───────────────────────────────────────────────────────
    val allWalletLedgers: Flow<List<WalletLedger>> = walletLedgerDao.getAll()
    val allWalletLedgersRaw: Flow<List<WalletLedger>> = walletLedgerDao.getAllRaw()
    suspend fun insertWalletLedger(l: WalletLedger) = walletLedgerDao.insert(l)
    suspend fun updateWalletLedger(l: WalletLedger) = walletLedgerDao.update(l)
    suspend fun deleteWalletLedgerById(id: Int) = walletLedgerDao.deleteById(id)
    suspend fun softDeleteWalletLedgerById(id: Int, deletedAt: Long = System.currentTimeMillis()) = walletLedgerDao.softDeleteById(id, deletedAt)

    // ─── Wallet Batches ───────────────────────────────────────────────────────
    val allWalletBatches: Flow<List<WalletBatch>> = walletBatchDao.getAll()
    val allWalletBatchesRaw: Flow<List<WalletBatch>> = walletBatchDao.getAllRaw()
    suspend fun insertWalletBatch(b: WalletBatch) = walletBatchDao.insert(b)
    suspend fun updateWalletBatch(b: WalletBatch) = walletBatchDao.update(b)
    suspend fun deleteWalletBatchById(id: Int) = walletBatchDao.deleteById(id)
    suspend fun softDeleteWalletBatchById(id: Int, deletedAt: Long = System.currentTimeMillis()) = walletBatchDao.softDeleteById(id, deletedAt)
    suspend fun deleteWalletBatchBySupplierDepositId(depositId: Int) = walletBatchDao.deleteBySupplierDepositId(depositId)
    suspend fun softDeleteWalletBatchBySupplierDepositId(depositId: Int, deletedAt: Long = System.currentTimeMillis()) = walletBatchDao.softDeleteBySupplierDepositId(depositId, deletedAt)
    suspend fun getWalletBatchById(id: Int) = walletBatchDao.getById(id)
}

