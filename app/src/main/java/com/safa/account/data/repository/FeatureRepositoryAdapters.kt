package com.safa.account.data.repository

import com.safa.account.data.model.Customer
import com.safa.account.data.model.DailyRate
import com.safa.account.data.model.ExpenseIncome
import com.safa.account.data.model.OperatorAccount
import com.safa.account.data.model.RemittanceTransaction
import com.safa.account.data.model.Supplier
import com.safa.account.data.model.SupplierDeposit
import com.safa.account.data.model.WalletBatch
import com.safa.account.data.model.WalletLedger
import com.safa.account.domain.feature.AdminRepository
import com.safa.account.domain.feature.CustomerRepository
import com.safa.account.domain.feature.ExpenseRepository
import com.safa.account.domain.feature.FeatureRepositorySet
import com.safa.account.domain.feature.RateRepository
import com.safa.account.domain.feature.SupplierFundingRepository
import com.safa.account.domain.feature.SupplierRepository
import com.safa.account.domain.feature.TransactionRepository
import com.safa.account.domain.feature.WalletRepository

/**
 * Compatibility adapter around the one canonical local-first repository.
 * Splitting feature ports must not create a second cache, database, outbox or
 * synchronization owner.
 */
class AppFeatureRepositorySet(private val repository: AppRepository) : FeatureRepositorySet {
    override val customers: CustomerRepository = object : CustomerRepository {
        override val items = repository.allCustomers
        override suspend fun insert(item: Customer) = repository.insertCustomer(item)
        override suspend fun update(item: Customer) = repository.updateCustomer(item)
        override suspend fun find(id: Int) = repository.getCustomerById(id)
        override suspend fun delete(id: Int) = repository.softDeleteCustomerById(id)
    }

    override val suppliers: SupplierRepository = object : SupplierRepository {
        override val items = repository.allSuppliers
        override suspend fun insert(item: Supplier) = repository.insertSupplier(item)
        override suspend fun update(item: Supplier) = repository.updateSupplier(item)
        override suspend fun find(id: Int) = repository.getSupplierById(id)
        override suspend fun delete(id: Int) = repository.softDeleteSupplierById(id)
    }

    override val transactions: TransactionRepository = object : TransactionRepository {
        override val items = repository.allTransactions
        override suspend fun insert(item: RemittanceTransaction) = repository.insertTransaction(item)
        override suspend fun update(item: RemittanceTransaction) = repository.updateTransaction(item)
        override suspend fun find(id: Int) = repository.getTransactionById(id)
        override suspend fun delete(id: Int) = repository.softDeleteTransactionById(id)
    }

    override val supplierFunding: SupplierFundingRepository = object : SupplierFundingRepository {
        override val items = repository.allSupplierDeposits
        override suspend fun insert(item: SupplierDeposit) = repository.insertSupplierDeposit(item)
        override suspend fun update(item: SupplierDeposit) = repository.updateSupplierDeposit(item)
        override suspend fun delete(id: Int) = repository.softDeleteSupplierDepositById(id)
    }

    override val wallet: WalletRepository = object : WalletRepository {
        override val ledgers = repository.allWalletLedgers
        override val batches = repository.allWalletBatches
        override suspend fun insertLedger(item: WalletLedger) = repository.insertWalletLedger(item)
        override suspend fun updateLedger(item: WalletLedger) = repository.updateWalletLedger(item)
        override suspend fun deleteLedger(id: Int) = repository.softDeleteWalletLedgerById(id)
        override suspend fun insertBatch(item: WalletBatch) = repository.insertWalletBatch(item)
        override suspend fun updateBatch(item: WalletBatch) = repository.updateWalletBatch(item)
        override suspend fun findBatch(id: Int) = repository.getWalletBatchById(id)
        override suspend fun deleteBatch(id: Int) = repository.softDeleteWalletBatchById(id)
        override suspend fun deleteBatchesForSupplierDeposit(supplierDepositId: Int) =
            repository.softDeleteWalletBatchBySupplierDepositId(supplierDepositId)
    }

    override val expenses: ExpenseRepository = object : ExpenseRepository {
        override val items = repository.allExpensesIncomes
        override suspend fun insert(item: ExpenseIncome) = repository.insertExpenseIncome(item)
        override suspend fun update(item: ExpenseIncome) = repository.updateExpenseIncome(item)
        override suspend fun delete(id: Int) = repository.softDeleteExpenseIncomeById(id)
    }

    override val rates: RateRepository = object : RateRepository {
        override val items = repository.allDailyRates
        override suspend fun find(date: String): DailyRate? = repository.getDailyRateByDate(date)
        override suspend fun insert(item: DailyRate) = repository.insertDailyRate(item)
    }

    override val admin: AdminRepository = object : AdminRepository {
        override val operators = repository.allOperators
        override suspend fun insert(item: OperatorAccount) = repository.insertOperator(item)
        override suspend fun update(item: OperatorAccount) = repository.updateOperator(item)
        override suspend fun findByMobile(mobile: String) = repository.getOperatorByMobile(mobile)
        override suspend fun removeLocal(item: OperatorAccount) = repository.removeOperatorLocally(item)
    }
}
