package com.safa.account.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.safa.account.data.dao.*
import com.safa.account.data.model.*
import kotlinx.coroutines.CoroutineScope
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory

@Database(
    entities = [
        OperatorAccount::class,
        Customer::class,
        Supplier::class,
        RemittanceTransaction::class,
        SupplierDeposit::class,
        ExpenseIncome::class,
        DailyRate::class,
        WalletLedger::class,
        WalletBatch::class,
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun operatorDao(): OperatorDao
    abstract fun customerDao(): CustomerDao
    abstract fun supplierDao(): SupplierDao
    abstract fun transactionDao(): TransactionDao
    abstract fun supplierDepositDao(): SupplierDepositDao
    abstract fun expenseIncomeDao(): ExpenseIncomeDao
    abstract fun dailyRateDao(): DailyRateDao
    abstract fun walletLedgerDao(): WalletLedgerDao
    abstract fun walletBatchDao(): WalletBatchDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context, @Suppress("UNUSED_PARAMETER") scope: CoroutineScope): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val passphrase = KeyStoreHelper.getOrGenerateDbPassphrase(context.applicationContext)
                val factory = SupportFactory(passphrase)
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "safa_encrypted_db"
                )
                    .openHelperFactory(factory)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
