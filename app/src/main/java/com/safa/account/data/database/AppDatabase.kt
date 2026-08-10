package com.safa.account.data.database

import android.content.Context

/**
 * Compatibility shim kept temporarily for existing Activity wiring.
 * It does NOT create, open, encrypt, migrate, cache, or persist a database.
 * All returned values are simply the application context; AppRepository uses
 * that context to build the authenticated remote API client.
 */
@Deprecated("Local database removed. Use AppRepository(ApiService) directly.")
class AppDatabase private constructor(private val context: Context) {
    fun operatorDao(): Context = context
    fun customerDao(): Context = context
    fun supplierDao(): Context = context
    fun transactionDao(): Context = context
    fun supplierDepositDao(): Context = context
    fun expenseIncomeDao(): Context = context
    fun dailyRateDao(): Context = context
    fun walletLedgerDao(): Context = context
    fun walletBatchDao(): Context = context
    fun syncOutboxDao(): Context = context

    companion object {
        fun getDatabase(context: Context): AppDatabase = AppDatabase(context.applicationContext)
    }
}
