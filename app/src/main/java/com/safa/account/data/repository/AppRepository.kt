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
    suspend fun insertCustomer(c: Customer) = customerDao.insert(c)
    suspend fun updateCustomer(c: Customer) = customerDao.update(c)
    suspend fun deleteCustomerById(id: Int) = customerDao.deleteById(id)

    // ─── Suppliers ────────────────────────────────────────────────────────────
    val allSuppliers: Flow<List<Supplier>> = supplierDao.getAll()
    suspend fun insertSupplier(s: Supplier) = supplierDao.insert(s)
    suspend fun updateSupplier(s: Supplier) = supplierDao.update(s)
    suspend fun deleteSupplierById(id: Int) = supplierDao.deleteById(id)

    // ─── Transactions ─────────────────────────────────────────────────────────
    val allTransactions: Flow<List<RemittanceTransaction>> = transactionDao.getAll()
    suspend fun insertTransaction(tx: RemittanceTransaction) = transactionDao.insert(tx)
    suspend fun updateTransaction(tx: RemittanceTransaction) = transactionDao.update(tx)
    suspend fun deleteTransactionById(id: Int) = transactionDao.deleteById(id)

    // ─── Supplier Deposits ────────────────────────────────────────────────────
    val allSupplierDeposits: Flow<List<SupplierDeposit>> = supplierDepositDao.getAll()
    suspend fun insertSupplierDeposit(dep: SupplierDeposit) = supplierDepositDao.insert(dep)
    suspend fun updateSupplierDeposit(dep: SupplierDeposit) = supplierDepositDao.update(dep)
    suspend fun deleteSupplierDepositById(id: Int) = supplierDepositDao.deleteById(id)

    // ─── Expenses & Incomes ───────────────────────────────────────────────────
    val allExpensesIncomes: Flow<List<ExpenseIncome>> = expenseIncomeDao.getAll()
    suspend fun insertExpenseIncome(e: ExpenseIncome) = expenseIncomeDao.insert(e)
    suspend fun deleteExpenseIncomeById(id: Int) = expenseIncomeDao.deleteById(id)

    // ─── Daily Rates ──────────────────────────────────────────────────────────
    val allDailyRates: Flow<List<DailyRate>> = dailyRateDao.getAll()
    suspend fun insertDailyRate(r: DailyRate) = dailyRateDao.insert(r)
    suspend fun getDailyRateByDate(date: String) = dailyRateDao.getByDate(date)

    // ─── Wallet Ledgers ───────────────────────────────────────────────────────
    val allWalletLedgers: Flow<List<WalletLedger>> = walletLedgerDao.getAll()
    suspend fun insertWalletLedger(l: WalletLedger) = walletLedgerDao.insert(l)
    suspend fun updateWalletLedger(l: WalletLedger) = walletLedgerDao.update(l)
    suspend fun deleteWalletLedgerById(id: Int) = walletLedgerDao.deleteById(id)

    // ─── Wallet Batches ───────────────────────────────────────────────────────
    val allWalletBatches: Flow<List<WalletBatch>> = walletBatchDao.getAll()
    suspend fun insertWalletBatch(b: WalletBatch) = walletBatchDao.insert(b)
    suspend fun updateWalletBatch(b: WalletBatch) = walletBatchDao.update(b)
    suspend fun deleteWalletBatchById(id: Int) = walletBatchDao.deleteById(id)
    suspend fun deleteWalletBatchBySupplierDepositId(depositId: Int) = walletBatchDao.deleteBySupplierDepositId(depositId)
    suspend fun getWalletBatchById(id: Int) = walletBatchDao.getById(id)
}
