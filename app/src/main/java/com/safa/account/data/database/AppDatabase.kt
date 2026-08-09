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

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE customers ADD COLUMN serverId INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE customers ADD COLUMN syncStatus INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE customers ADD COLUMN syncError TEXT DEFAULT NULL")

        db.execSQL("ALTER TABLE suppliers ADD COLUMN serverId INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE suppliers ADD COLUMN syncStatus INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE suppliers ADD COLUMN syncError TEXT DEFAULT NULL")

        db.execSQL("ALTER TABLE transactions ADD COLUMN serverId INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE transactions ADD COLUMN syncStatus INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE transactions ADD COLUMN syncError TEXT DEFAULT NULL")

        db.execSQL("ALTER TABLE supplier_deposits ADD COLUMN serverId INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE supplier_deposits ADD COLUMN syncStatus INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE supplier_deposits ADD COLUMN syncError TEXT DEFAULT NULL")

        db.execSQL("ALTER TABLE expenses_incomes ADD COLUMN serverId INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE expenses_incomes ADD COLUMN syncStatus INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE expenses_incomes ADD COLUMN syncError TEXT DEFAULT NULL")

        db.execSQL("ALTER TABLE wallet_ledgers ADD COLUMN serverId INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE wallet_ledgers ADD COLUMN syncStatus INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE wallet_ledgers ADD COLUMN syncError TEXT DEFAULT NULL")

        db.execSQL("ALTER TABLE wallet_batches ADD COLUMN serverId INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE wallet_batches ADD COLUMN syncStatus INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE wallet_batches ADD COLUMN syncError TEXT DEFAULT NULL")
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE customers ADD COLUMN retryCount INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE customers ADD COLUMN lastSyncAttemptAt INTEGER DEFAULT NULL")

        db.execSQL("ALTER TABLE suppliers ADD COLUMN retryCount INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE suppliers ADD COLUMN lastSyncAttemptAt INTEGER DEFAULT NULL")

        db.execSQL("ALTER TABLE transactions ADD COLUMN retryCount INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE transactions ADD COLUMN lastSyncAttemptAt INTEGER DEFAULT NULL")

        db.execSQL("ALTER TABLE supplier_deposits ADD COLUMN retryCount INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE supplier_deposits ADD COLUMN lastSyncAttemptAt INTEGER DEFAULT NULL")

        db.execSQL("ALTER TABLE expenses_incomes ADD COLUMN retryCount INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE expenses_incomes ADD COLUMN lastSyncAttemptAt INTEGER DEFAULT NULL")

        db.execSQL("ALTER TABLE wallet_ledgers ADD COLUMN retryCount INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE wallet_ledgers ADD COLUMN lastSyncAttemptAt INTEGER DEFAULT NULL")

        db.execSQL("ALTER TABLE wallet_batches ADD COLUMN retryCount INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE wallet_batches ADD COLUMN lastSyncAttemptAt INTEGER DEFAULT NULL")
    }
}

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
    version = 5,
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
                    .addMigrations(MIGRATION_3_4, MIGRATION_4_5)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
