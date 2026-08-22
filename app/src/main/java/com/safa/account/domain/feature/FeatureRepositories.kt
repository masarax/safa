package com.safa.account.domain.feature

import com.safa.account.data.model.Customer
import com.safa.account.data.model.DailyRate
import com.safa.account.data.model.ExpenseIncome
import com.safa.account.data.model.OperatorAccount
import com.safa.account.data.model.RemittanceTransaction
import com.safa.account.data.model.Supplier
import com.safa.account.data.model.SupplierDeposit
import com.safa.account.data.model.WalletBatch
import com.safa.account.data.model.WalletLedger
import kotlinx.coroutines.flow.Flow

/**
 * Feature-facing data ports. Implementations may share one persistence/sync
 * owner, but presentation code depends only on the operations for its feature.
 */
interface CustomerRepository {
    val items: Flow<List<Customer>>
    suspend fun insert(item: Customer): Int
    suspend fun update(item: Customer)
    suspend fun find(id: Int): Customer?
    suspend fun delete(id: Int)
    suspend fun removeAccepted(id: Int)
}

interface SupplierRepository {
    val items: Flow<List<Supplier>>
    suspend fun insert(item: Supplier): Int
    suspend fun update(item: Supplier)
    suspend fun find(id: Int): Supplier?
    suspend fun delete(id: Int)
}

interface TransactionRepository {
    val items: Flow<List<RemittanceTransaction>>
    suspend fun insert(item: RemittanceTransaction): Int
    suspend fun update(item: RemittanceTransaction)
    suspend fun find(id: Int): RemittanceTransaction?
    suspend fun delete(id: Int)
}

interface SupplierFundingRepository {
    val items: Flow<List<SupplierDeposit>>
    suspend fun insert(item: SupplierDeposit): Int
    suspend fun update(item: SupplierDeposit)
    suspend fun delete(id: Int)
}

interface WalletRepository {
    val ledgers: Flow<List<WalletLedger>>
    val batches: Flow<List<WalletBatch>>
    suspend fun insertLedger(item: WalletLedger): Int
    suspend fun updateLedger(item: WalletLedger)
    suspend fun deleteLedger(id: Int)
    suspend fun insertBatch(item: WalletBatch): Int
    suspend fun updateBatch(item: WalletBatch)
    suspend fun findBatch(id: Int): WalletBatch?
    suspend fun deleteBatch(id: Int)
    suspend fun deleteBatchesForSupplierDeposit(supplierDepositId: Int)
}

interface ExpenseRepository {
    val items: Flow<List<ExpenseIncome>>
    suspend fun insert(item: ExpenseIncome): Int
    suspend fun update(item: ExpenseIncome)
    suspend fun delete(id: Int)
}

interface RateRepository {
    val items: Flow<List<DailyRate>>
    suspend fun find(date: String): DailyRate?
    suspend fun insert(item: DailyRate)
}

interface AdminRepository {
    val operators: Flow<List<OperatorAccount>>
    suspend fun insert(item: OperatorAccount): Int
    suspend fun update(item: OperatorAccount)
    suspend fun findByMobile(mobile: String): OperatorAccount?
    suspend fun removeLocal(item: OperatorAccount)
}

interface FeatureRepositorySet {
    val customers: CustomerRepository
    val suppliers: SupplierRepository
    val transactions: TransactionRepository
    val supplierFunding: SupplierFundingRepository
    val wallet: WalletRepository
    val expenses: ExpenseRepository
    val rates: RateRepository
    val admin: AdminRepository
}
