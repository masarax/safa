package com.safa.account.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

// ─── Room Entities (persisted to SQLCipher-encrypted DB) ─────────────────────

@Entity(tableName = "operators")
data class OperatorAccount(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val username: String,
    val role: String,       // "Owner" | "Staff"
    val pin: String,        // stored as plain text locally; see security note in ENTERPRISE_SECURITY_AND_CRYPTO_SPEC.md
    val isActive: Boolean = true
)

@Entity(tableName = "customers")
data class Customer(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val phone: String,
    val address: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "suppliers")
data class Supplier(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val phone: String = "",
    val address: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "transactions")
data class RemittanceTransaction(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
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
    val timestamp: Long = System.currentTimeMillis()
) {
    fun getProfitBdt(): Double = (customerRate - supplierRate) * amountSar
    fun getProfitSar(): Double = amountSar - (amountBdt / customerRate)
}

@Entity(tableName = "supplier_deposits")
data class SupplierDeposit(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val supplierId: Int,
    val amountSar: Double,
    val rate: Double,
    val amountBdt: Double,
    val paidBdt: Double = 0.0,
    val transactionType: String = "SAR_GIVEN",
    // "SAR_GIVEN" | "SAR_DEPOSIT" | "SAR_RECEIVED" | "SAR_SETTLEMENT" | "BDT_WITHDRAW"
    val notes: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "expenses_incomes")
data class ExpenseIncome(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val amount: Double,
    val currency: String = "BDT",
    val isExpense: Boolean = true,
    val category: String = "General",
    val timestamp: Long = System.currentTimeMillis()
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
    val name: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "wallet_batches")
data class WalletBatch(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val ledgerId: Int,
    val rate: Double,
    val initialBdt: Double,
    val remainingBdt: Double,
    val supplierId: Int = 0,
    val supplierDepositId: Int = 0,
    val notes: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
