package com.safa.account.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

// ─── Room Entities (persisted to SQLCipher-encrypted DB) ─────────────────────

@Entity(tableName = "operators")
data class OperatorAccount(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val username: String,
    val role: String,       // "SuperAdmin" | "Owner" | "Staff"
    val pin: String,        // stored as hashed PIN locally
    val mobile: String = "",
    val email: String = "",
    val isActivated: Boolean = true,
    val permissions: String = "edit,create,delete,update",
    val isBiometricEnabled: Boolean = false,
    val isActive: Boolean = true,
    val canViewCustomers: Boolean = true,
    val canAddCustomers: Boolean = true,
    val canEditCustomers: Boolean = true,
    val canDeleteCustomers: Boolean = true,
    val canViewSuppliers: Boolean = true,
    val canAddSuppliers: Boolean = true,
    val canEditSuppliers: Boolean = true,
    val canDeleteSuppliers: Boolean = true,
    val canViewTransactions: Boolean = true,
    val canAddTransactions: Boolean = true,
    val canEditTransactions: Boolean = true,
    val canDeleteTransactions: Boolean = true,
    val canManageWallet: Boolean = true,
    val canManageExpenses: Boolean = true,
    val canViewReports: Boolean = true
)

object SyncStatus {
    const val PENDING_CREATE = 0
    const val SYNCED = 1
    const val PENDING_UPDATE = 2
    const val PENDING_DELETE = 3
    const val SYNC_FAILED = 4
}

@Entity(tableName = "customers")
data class Customer(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val serverId: Int = 0,
    val name: String,
    val phone: String,
    val address: String = "",
    val securityNotes: String = "",
    val avatarColor: String = "4280391411",
    val avatarEmoji: String = "👤",
    val timestamp: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null,
    val syncStatus: Int = SyncStatus.PENDING_CREATE,
    val syncError: String? = null,
    val retryCount: Int = 0,
    val lastSyncAttemptAt: Long? = null
)

@Entity(tableName = "suppliers")
data class Supplier(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val serverId: Int = 0,
    val name: String,
    val phone: String = "",
    val address: String = "",
    val tradeLicense: String = "",
    val securityNotes: String = "",
    val avatarColor: String = "4280391411",
    val avatarEmoji: String = "🏢",
    val timestamp: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null,
    val syncStatus: Int = SyncStatus.PENDING_CREATE,
    val syncError: String? = null,
    val retryCount: Int = 0,
    val lastSyncAttemptAt: Long? = null
)

@Entity(
    tableName = "transactions",
    indices = [
        androidx.room.Index(value = ["customerId"]),
        androidx.room.Index(value = ["supplierId"]),
        androidx.room.Index(value = ["operatorId"]),
        androidx.room.Index(value = ["walletBatchId"])
    ]
)
data class RemittanceTransaction(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val serverId: Int = 0,
    val customerId: Int,
    val supplierId: Int = 0,
    val amountSar: Double,
    val customerRate: Double,
    val supplierRate: Double,
    val amountBdt: Double,
    val sarCollected: Double = amountSar,
    val bdtDisbursed: Double = amountBdt,
    val receiverName: String,
    val receiverPhone: String,
    val receiverAccountType: String,
    val receiverAccountNo: String,
    val status: String = "Pending",   // "Pending" | "Delivered" | "Cancelled"
    val operatorId: Int = 0,
    val walletBatchId: Int = 0,
    val notes: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null,
    val syncStatus: Int = SyncStatus.PENDING_CREATE,
    val syncError: String? = null,
    val retryCount: Int = 0,
    val lastSyncAttemptAt: Long? = null
) {
    fun getProfitBdt(): Double {
        val cr = java.math.BigDecimal(customerRate.toString())
        val sr = java.math.BigDecimal(supplierRate.toString())
        val amt = java.math.BigDecimal(amountSar.toString())
        return cr.subtract(sr).multiply(amt).toDouble()
    }
    
    fun getProfitSar(): Double {
        if (customerRate <= 0.0) return 0.0
        val amt = java.math.BigDecimal(amountSar.toString())
        val bdt = java.math.BigDecimal(amountBdt.toString())
        val cr = java.math.BigDecimal(customerRate.toString())
        return amt.subtract(bdt.divide(cr, 4, java.math.RoundingMode.HALF_UP)).toDouble()
    }
}

@Entity(
    tableName = "supplier_deposits",
    indices = [androidx.room.Index(value = ["supplierId"])]
)
data class SupplierDeposit(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val serverId: Int = 0,
    val supplierId: Int,
    val amountSar: Double,
    val rate: Double,
    val amountBdt: Double,
    val paidBdt: Double = 0.0,
    val transactionType: String = "SAR_GIVEN",
    // "SAR_GIVEN" | "SAR_DEPOSIT" | "SAR_RECEIVED" | "SAR_SETTLEMENT" | "BDT_WITHDRAW"
    val notes: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null,
    val syncStatus: Int = SyncStatus.PENDING_CREATE,
    val syncError: String? = null,
    val retryCount: Int = 0,
    val lastSyncAttemptAt: Long? = null
)

@Entity(tableName = "expenses_incomes")
data class ExpenseIncome(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val serverId: Int = 0,
    val title: String,
    val amount: Double,
    val currency: String = "BDT",
    val isExpense: Boolean = true,
    val category: String = "General",
    val timestamp: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null,
    val syncStatus: Int = SyncStatus.PENDING_CREATE,
    val syncError: String? = null,
    val retryCount: Int = 0,
    val lastSyncAttemptAt: Long? = null
)

@Entity(tableName = "daily_rates")
data class DailyRate(
    @PrimaryKey val date: String,   // ISO format "yyyy-MM-dd"
    val customerRate: Double,
    val supplierRate: Double
)

@Entity(tableName = "wallet_ledgers")
data class WalletLedger(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val serverId: Int = 0,
    val name: String,
    val timestamp: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null,
    val syncStatus: Int = SyncStatus.PENDING_CREATE,
    val syncError: String? = null,
    val retryCount: Int = 0,
    val lastSyncAttemptAt: Long? = null
)

@Entity(
    tableName = "wallet_batches",
    indices = [
        androidx.room.Index(value = ["ledgerId"]),
        androidx.room.Index(value = ["supplierId"]),
        androidx.room.Index(value = ["supplierDepositId"])
    ]
)
data class WalletBatch(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val serverId: Int = 0,
    val ledgerId: Int,
    val rate: Double,
    val initialBdt: Double,
    val remainingBdt: Double,
    val supplierId: Int = 0,
    val supplierDepositId: Int = 0,
    val notes: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null,
    val syncStatus: Int = SyncStatus.PENDING_CREATE,
    val syncError: String? = null,
    val retryCount: Int = 0,
    val lastSyncAttemptAt: Long? = null
)

