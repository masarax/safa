package com.safa.account.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.InvalidationTracker
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.safa.account.data.dao.*
import com.safa.account.data.model.*
import com.safa.account.data.network.SyncTrigger
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory

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

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `sync_outbox` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `userId` INTEGER NOT NULL DEFAULT 0,
                `accountId` INTEGER NOT NULL DEFAULT 0,
                `entityType` TEXT NOT NULL,
                `entityLocalId` INTEGER NOT NULL,
                `entityServerId` INTEGER NOT NULL DEFAULT 0,
                `operation` TEXT NOT NULL,
                `payloadJson` TEXT NOT NULL,
                `status` TEXT NOT NULL DEFAULT 'PENDING',
                `retryCount` INTEGER NOT NULL DEFAULT 0,
                `lastError` TEXT DEFAULT NULL,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL
            )
        """.trimIndent())
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
        SyncOutbox::class,
    ],
    version = 6,
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
    abstract fun syncOutboxDao(): SyncOutboxDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null
        private var observerRegistered = false

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: run {
                    android.util.Log.i("SafaApp", "STARTUP_050_BEFORE_KEYSTORE")
                    val passphrase = KeyStoreHelper.getOrGenerateDbPassphrase(context.applicationContext)
                    android.util.Log.i("SafaApp", "STARTUP_060_AFTER_KEYSTORE")
                    SQLiteDatabase.loadLibs(context.applicationContext)
                    android.util.Log.i("SafaApp", "STARTUP_080_AFTER_SQLCIPHER")
                    val factory = SupportFactory(passphrase)
                    val database = Room.databaseBuilder(
                        context.applicationContext,
                        AppDatabase::class.java,
                        "safa_encrypted_db"
                    )
                        .openHelperFactory(factory)
                        .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                        .build()

                    INSTANCE = database
                    if (!observerRegistered) {
                        observerRegistered = true
                        database.invalidationTracker.addObserver(object : InvalidationTracker.Observer(
                            "customers",
                            "suppliers",
                            "transactions",
                            "supplier_deposits",
                            "expenses_incomes",
                            "wallet_ledgers",
                            "wallet_batches",
                            "sync_outbox"
                        ) {
                            override fun onInvalidated(tables: Set<String>) {
                                SyncTrigger.schedule(context.applicationContext)
                            }
                        })
                    }
                    database
                }
            }
        }
    }
}
