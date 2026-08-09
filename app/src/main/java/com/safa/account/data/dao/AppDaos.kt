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

    @Query("SELECT * FROM operators WHERE mobile = :mobile LIMIT 1")
    suspend fun getByMobile(mobile: String): OperatorAccount?

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

    @Query("UPDATE customers SET deletedAt = :deletedAt, syncStatus = 3 WHERE id = :id")
    suspend fun softDeleteById(id: Int, deletedAt: Long = System.currentTimeMillis())

    @Query("SELECT * FROM customers WHERE id = :id LIMIT 1")
    suspend fun getById(id: Int): Customer?

    @Query("SELECT * FROM customers WHERE deletedAt IS NULL ORDER BY name ASC")
    fun getAll(): Flow<List<Customer>>

    @Query("SELECT * FROM customers ORDER BY name ASC")
    fun getAllRaw(): Flow<List<Customer>>

    @Query("SELECT * FROM customers WHERE syncStatus != 1 AND retryCount < 5")
    suspend fun getPending(): List<Customer>

    @Query("UPDATE customers SET serverId = :serverId, syncStatus = 1, syncError = NULL, retryCount = 0, lastSyncAttemptAt = :attemptAt WHERE id = :id")
    suspend fun markSynced(id: Int, serverId: Int, attemptAt: Long = System.currentTimeMillis())

    @Query("UPDATE customers SET syncStatus = 4, syncError = :error, lastSyncAttemptAt = :attemptAt WHERE id = :id")
    suspend fun markFailed(id: Int, error: String, attemptAt: Long = System.currentTimeMillis())

    @Query("UPDATE customers SET retryCount = retryCount + 1, lastSyncAttemptAt = :attemptAt, syncStatus = CASE WHEN retryCount + 1 >= 5 THEN 4 ELSE syncStatus END, syncError = CASE WHEN retryCount + 1 >= 5 THEN 'Max retry limit reached (5 attempts)' ELSE syncError END WHERE id = :id")
    suspend fun incrementRetry(id: Int, attemptAt: Long = System.currentTimeMillis())

    @Query("UPDATE customers SET retryCount = 0, syncStatus = :targetStatus, syncError = NULL, lastSyncAttemptAt = NULL WHERE id = :id")
    suspend fun resetRetryState(id: Int, targetStatus: Int)
}

@Dao
interface SupplierDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(s: Supplier): Long

    @Update
    suspend fun update(s: Supplier)

    @Query("DELETE FROM suppliers WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("UPDATE suppliers SET deletedAt = :deletedAt, syncStatus = 3 WHERE id = :id")
    suspend fun softDeleteById(id: Int, deletedAt: Long = System.currentTimeMillis())

    @Query("SELECT * FROM suppliers WHERE id = :id LIMIT 1")
    suspend fun getById(id: Int): Supplier?

    @Query("SELECT * FROM suppliers WHERE deletedAt IS NULL ORDER BY name ASC")
    fun getAll(): Flow<List<Supplier>>

    @Query("SELECT * FROM suppliers ORDER BY name ASC")
    fun getAllRaw(): Flow<List<Supplier>>

    @Query("SELECT * FROM suppliers WHERE syncStatus != 1 AND retryCount < 5")
    suspend fun getPending(): List<Supplier>

    @Query("UPDATE suppliers SET serverId = :serverId, syncStatus = 1, syncError = NULL, retryCount = 0, lastSyncAttemptAt = :attemptAt WHERE id = :id")
    suspend fun markSynced(id: Int, serverId: Int, attemptAt: Long = System.currentTimeMillis())

    @Query("UPDATE suppliers SET syncStatus = 4, syncError = :error, lastSyncAttemptAt = :attemptAt WHERE id = :id")
    suspend fun markFailed(id: Int, error: String, attemptAt: Long = System.currentTimeMillis())

    @Query("UPDATE suppliers SET retryCount = retryCount + 1, lastSyncAttemptAt = :attemptAt, syncStatus = CASE WHEN retryCount + 1 >= 5 THEN 4 ELSE syncStatus END, syncError = CASE WHEN retryCount + 1 >= 5 THEN 'Max retry limit reached (5 attempts)' ELSE syncError END WHERE id = :id")
    suspend fun incrementRetry(id: Int, attemptAt: Long = System.currentTimeMillis())

    @Query("UPDATE suppliers SET retryCount = 0, syncStatus = :targetStatus, syncError = NULL, lastSyncAttemptAt = NULL WHERE id = :id")
    suspend fun resetRetryState(id: Int, targetStatus: Int)
}

@Dao
interface TransactionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(tx: RemittanceTransaction): Long

    @Update
    suspend fun update(tx: RemittanceTransaction)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("UPDATE transactions SET deletedAt = :deletedAt, syncStatus = 3 WHERE id = :id")
    suspend fun softDeleteById(id: Int, deletedAt: Long = System.currentTimeMillis())

    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    suspend fun getById(id: Int): RemittanceTransaction?

    @Query("SELECT * FROM transactions WHERE deletedAt IS NULL ORDER BY timestamp DESC")
    fun getAll(): Flow<List<RemittanceTransaction>>

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun getAllRaw(): Flow<List<RemittanceTransaction>>

    @Query("SELECT * FROM transactions WHERE syncStatus != 1 AND retryCount < 5")
    suspend fun getPending(): List<RemittanceTransaction>

    @Query("UPDATE transactions SET serverId = :serverId, syncStatus = 1, syncError = NULL, retryCount = 0, lastSyncAttemptAt = :attemptAt WHERE id = :id")
    suspend fun markSynced(id: Int, serverId: Int, attemptAt: Long = System.currentTimeMillis())

    @Query("UPDATE transactions SET syncStatus = 4, syncError = :error, lastSyncAttemptAt = :attemptAt WHERE id = :id")
    suspend fun markFailed(id: Int, error: String, attemptAt: Long = System.currentTimeMillis())

    @Query("UPDATE transactions SET retryCount = retryCount + 1, lastSyncAttemptAt = :attemptAt, syncStatus = CASE WHEN retryCount + 1 >= 5 THEN 4 ELSE syncStatus END, syncError = CASE WHEN retryCount + 1 >= 5 THEN 'Max retry limit reached (5 attempts)' ELSE syncError END WHERE id = :id")
    suspend fun incrementRetry(id: Int, attemptAt: Long = System.currentTimeMillis())

    @Query("UPDATE transactions SET retryCount = 0, syncStatus = :targetStatus, syncError = NULL, lastSyncAttemptAt = NULL WHERE id = :id")
    suspend fun resetRetryState(id: Int, targetStatus: Int)
}

@Dao
interface SupplierDepositDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(dep: SupplierDeposit): Long

    @Update
    suspend fun update(dep: SupplierDeposit)

    @Query("DELETE FROM supplier_deposits WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("UPDATE supplier_deposits SET deletedAt = :deletedAt, syncStatus = 3 WHERE id = :id")
    suspend fun softDeleteById(id: Int, deletedAt: Long = System.currentTimeMillis())

    @Query("SELECT * FROM supplier_deposits WHERE id = :id LIMIT 1")
    suspend fun getById(id: Int): SupplierDeposit?

    @Query("SELECT * FROM supplier_deposits WHERE deletedAt IS NULL ORDER BY timestamp DESC")
    fun getAll(): Flow<List<SupplierDeposit>>

    @Query("SELECT * FROM supplier_deposits ORDER BY timestamp DESC")
    fun getAllRaw(): Flow<List<SupplierDeposit>>

    @Query("SELECT * FROM supplier_deposits WHERE syncStatus != 1 AND retryCount < 5")
    suspend fun getPending(): List<SupplierDeposit>

    @Query("UPDATE supplier_deposits SET serverId = :serverId, syncStatus = 1, syncError = NULL, retryCount = 0, lastSyncAttemptAt = :attemptAt WHERE id = :id")
    suspend fun markSynced(id: Int, serverId: Int, attemptAt: Long = System.currentTimeMillis())

    @Query("UPDATE supplier_deposits SET syncStatus = 4, syncError = :error, lastSyncAttemptAt = :attemptAt WHERE id = :id")
    suspend fun markFailed(id: Int, error: String, attemptAt: Long = System.currentTimeMillis())

    @Query("UPDATE supplier_deposits SET retryCount = retryCount + 1, lastSyncAttemptAt = :attemptAt, syncStatus = CASE WHEN retryCount + 1 >= 5 THEN 4 ELSE syncStatus END, syncError = CASE WHEN retryCount + 1 >= 5 THEN 'Max retry limit reached (5 attempts)' ELSE syncError END WHERE id = :id")
    suspend fun incrementRetry(id: Int, attemptAt: Long = System.currentTimeMillis())

    @Query("UPDATE supplier_deposits SET retryCount = 0, syncStatus = :targetStatus, syncError = NULL, lastSyncAttemptAt = NULL WHERE id = :id")
    suspend fun resetRetryState(id: Int, targetStatus: Int)
}

@Dao
interface ExpenseIncomeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(e: ExpenseIncome): Long

    @Update
    suspend fun update(e: ExpenseIncome)

    @Query("DELETE FROM expenses_incomes WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("UPDATE expenses_incomes SET deletedAt = :deletedAt, syncStatus = 3 WHERE id = :id")
    suspend fun softDeleteById(id: Int, deletedAt: Long = System.currentTimeMillis())

    @Query("SELECT * FROM expenses_incomes WHERE id = :id LIMIT 1")
    suspend fun getById(id: Int): ExpenseIncome?

    @Query("SELECT * FROM expenses_incomes WHERE deletedAt IS NULL ORDER BY timestamp DESC")
    fun getAll(): Flow<List<ExpenseIncome>>

    @Query("SELECT * FROM expenses_incomes ORDER BY timestamp DESC")
    fun getAllRaw(): Flow<List<ExpenseIncome>>

    @Query("SELECT * FROM expenses_incomes WHERE syncStatus != 1 AND retryCount < 5")
    suspend fun getPending(): List<ExpenseIncome>

    @Query("UPDATE expenses_incomes SET serverId = :serverId, syncStatus = 1, syncError = NULL, retryCount = 0, lastSyncAttemptAt = :attemptAt WHERE id = :id")
    suspend fun markSynced(id: Int, serverId: Int, attemptAt: Long = System.currentTimeMillis())

    @Query("UPDATE expenses_incomes SET syncStatus = 4, syncError = :error, lastSyncAttemptAt = :attemptAt WHERE id = :id")
    suspend fun markFailed(id: Int, error: String, attemptAt: Long = System.currentTimeMillis())

    @Query("UPDATE expenses_incomes SET retryCount = retryCount + 1, lastSyncAttemptAt = :attemptAt, syncStatus = CASE WHEN retryCount + 1 >= 5 THEN 4 ELSE syncStatus END, syncError = CASE WHEN retryCount + 1 >= 5 THEN 'Max retry limit reached (5 attempts)' ELSE syncError END WHERE id = :id")
    suspend fun incrementRetry(id: Int, attemptAt: Long = System.currentTimeMillis())

    @Query("UPDATE expenses_incomes SET retryCount = 0, syncStatus = :targetStatus, syncError = NULL, lastSyncAttemptAt = NULL WHERE id = :id")
    suspend fun resetRetryState(id: Int, targetStatus: Int)
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

    @Query("UPDATE wallet_ledgers SET deletedAt = :deletedAt, syncStatus = 3 WHERE id = :id")
    suspend fun softDeleteById(id: Int, deletedAt: Long = System.currentTimeMillis())

    @Query("SELECT * FROM wallet_ledgers WHERE id = :id LIMIT 1")
    suspend fun getById(id: Int): WalletLedger?

    @Query("SELECT * FROM wallet_ledgers WHERE deletedAt IS NULL ORDER BY timestamp ASC")
    fun getAll(): Flow<List<WalletLedger>>

    @Query("SELECT * FROM wallet_ledgers ORDER BY timestamp ASC")
    fun getAllRaw(): Flow<List<WalletLedger>>

    @Query("SELECT * FROM wallet_ledgers WHERE syncStatus != 1 AND retryCount < 5")
    suspend fun getPending(): List<WalletLedger>

    @Query("UPDATE wallet_ledgers SET serverId = :serverId, syncStatus = 1, syncError = NULL, retryCount = 0, lastSyncAttemptAt = :attemptAt WHERE id = :id")
    suspend fun markSynced(id: Int, serverId: Int, attemptAt: Long = System.currentTimeMillis())

    @Query("UPDATE wallet_ledgers SET syncStatus = 4, syncError = :error, lastSyncAttemptAt = :attemptAt WHERE id = :id")
    suspend fun markFailed(id: Int, error: String, attemptAt: Long = System.currentTimeMillis())

    @Query("UPDATE wallet_ledgers SET retryCount = retryCount + 1, lastSyncAttemptAt = :attemptAt, syncStatus = CASE WHEN retryCount + 1 >= 5 THEN 4 ELSE syncStatus END, syncError = CASE WHEN retryCount + 1 >= 5 THEN 'Max retry limit reached (5 attempts)' ELSE syncError END WHERE id = :id")
    suspend fun incrementRetry(id: Int, attemptAt: Long = System.currentTimeMillis())

    @Query("UPDATE wallet_ledgers SET retryCount = 0, syncStatus = :targetStatus, syncError = NULL, lastSyncAttemptAt = NULL WHERE id = :id")
    suspend fun resetRetryState(id: Int, targetStatus: Int)
}

@Dao
interface WalletBatchDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(b: WalletBatch): Long

    @Update
    suspend fun update(b: WalletBatch)

    @Query("DELETE FROM wallet_batches WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("UPDATE wallet_batches SET deletedAt = :deletedAt, syncStatus = 3 WHERE id = :id")
    suspend fun softDeleteById(id: Int, deletedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM wallet_batches WHERE supplierDepositId = :depositId")
    suspend fun deleteBySupplierDepositId(depositId: Int)

    @Query("UPDATE wallet_batches SET deletedAt = :deletedAt, syncStatus = 3 WHERE supplierDepositId = :depositId")
    suspend fun softDeleteBySupplierDepositId(depositId: Int, deletedAt: Long = System.currentTimeMillis())

    @Query("SELECT * FROM wallet_batches WHERE id = :id LIMIT 1")
    suspend fun getById(id: Int): WalletBatch?

    @Query("SELECT * FROM wallet_batches WHERE deletedAt IS NULL ORDER BY timestamp ASC")
    fun getAll(): Flow<List<WalletBatch>>

    @Query("SELECT * FROM wallet_batches ORDER BY timestamp ASC")
    fun getAllRaw(): Flow<List<WalletBatch>>

    @Query("SELECT * FROM wallet_batches WHERE syncStatus != 1 AND retryCount < 5")
    suspend fun getPending(): List<WalletBatch>

    @Query("UPDATE wallet_batches SET serverId = :serverId, syncStatus = 1, syncError = NULL, retryCount = 0, lastSyncAttemptAt = :attemptAt WHERE id = :id")
    suspend fun markSynced(id: Int, serverId: Int, attemptAt: Long = System.currentTimeMillis())

    @Query("UPDATE wallet_batches SET syncStatus = 4, syncError = :error, lastSyncAttemptAt = :attemptAt WHERE id = :id")
    suspend fun markFailed(id: Int, error: String, attemptAt: Long = System.currentTimeMillis())

    @Query("UPDATE wallet_batches SET retryCount = retryCount + 1, lastSyncAttemptAt = :attemptAt, syncStatus = CASE WHEN retryCount + 1 >= 5 THEN 4 ELSE syncStatus END, syncError = CASE WHEN retryCount + 1 >= 5 THEN 'Max retry limit reached (5 attempts)' ELSE syncError END WHERE id = :id")
    suspend fun incrementRetry(id: Int, attemptAt: Long = System.currentTimeMillis())

    @Query("UPDATE wallet_batches SET retryCount = 0, syncStatus = :targetStatus, syncError = NULL, lastSyncAttemptAt = NULL WHERE id = :id")
    suspend fun resetRetryState(id: Int, targetStatus: Int)
}

