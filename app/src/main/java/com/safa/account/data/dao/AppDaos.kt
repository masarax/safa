package com.safa.account.data.dao

import androidx.room.*
import com.safa.account.data.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface OperatorDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(op: OperatorAccount): Long

    @Update
    suspend fun update(op: OperatorAccount)

    @Query("SELECT * FROM operators ORDER BY id ASC")
    fun getAll(): Flow<List<OperatorAccount>>

    @Query("SELECT * FROM operators WHERE username = :username LIMIT 1")
    suspend fun getByUsername(username: String): OperatorAccount?

    @Query("DELETE FROM operators WHERE id = :id")
    suspend fun deleteById(id: Int)
}

@Dao
interface CustomerDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(c: Customer): Long

    @Update
    suspend fun update(c: Customer)

    @Query("DELETE FROM customers WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("SELECT * FROM customers ORDER BY name ASC")
    fun getAll(): Flow<List<Customer>>
}

@Dao
interface SupplierDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(s: Supplier): Long

    @Update
    suspend fun update(s: Supplier)

    @Query("DELETE FROM suppliers WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("SELECT * FROM suppliers ORDER BY name ASC")
    fun getAll(): Flow<List<Supplier>>
}

@Dao
interface TransactionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(tx: RemittanceTransaction): Long

    @Update
    suspend fun update(tx: RemittanceTransaction)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun getAll(): Flow<List<RemittanceTransaction>>
}

@Dao
interface SupplierDepositDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(dep: SupplierDeposit): Long

    @Update
    suspend fun update(dep: SupplierDeposit)

    @Query("DELETE FROM supplier_deposits WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("SELECT * FROM supplier_deposits ORDER BY timestamp DESC")
    fun getAll(): Flow<List<SupplierDeposit>>
}

@Dao
interface ExpenseIncomeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(e: ExpenseIncome): Long

    @Query("DELETE FROM expenses_incomes WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("SELECT * FROM expenses_incomes ORDER BY timestamp DESC")
    fun getAll(): Flow<List<ExpenseIncome>>
}

@Dao
interface DailyRateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(r: DailyRate)

    @Query("SELECT * FROM daily_rates WHERE date = :date LIMIT 1")
    suspend fun getByDate(date: String): DailyRate?

    @Query("SELECT * FROM daily_rates ORDER BY date DESC")
    fun getAll(): Flow<List<DailyRate>>
}

@Dao
interface WalletLedgerDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(l: WalletLedger): Long

    @Update
    suspend fun update(l: WalletLedger)

    @Query("DELETE FROM wallet_ledgers WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("SELECT * FROM wallet_ledgers ORDER BY timestamp ASC")
    fun getAll(): Flow<List<WalletLedger>>
}

@Dao
interface WalletBatchDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(b: WalletBatch): Long

    @Update
    suspend fun update(b: WalletBatch)

    @Query("DELETE FROM wallet_batches WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("DELETE FROM wallet_batches WHERE supplierDepositId = :depositId")
    suspend fun deleteBySupplierDepositId(depositId: Int)

    @Query("SELECT * FROM wallet_batches WHERE id = :id LIMIT 1")
    suspend fun getById(id: Int): WalletBatch?

    @Query("SELECT * FROM wallet_batches ORDER BY timestamp ASC")
    fun getAll(): Flow<List<WalletBatch>>
}
