package com.safa.account.data.model

import com.safa.account.data.money.MoneyMath
import java.math.BigDecimal

/**
 * Application/domain compatibility models.
 *
 * Laravel/MySQL is the server-authoritative source of accepted business state,
 * while Android keeps an encrypted durable local-first cache and mutation outbox
 * in LocalFirstStore. The local store survives process/device restarts and is
 * cleared at the authenticated account boundary. These models are serialized
 * into that canonical store by AppRepository rather than being Room entities.
 *
 * Money and rate properties are exact BigDecimal values. UI code may project
 * them to Float/Double only for drawing or platform formatting; domain
 * calculations, persistence and network synchronization stay fixed-scale.
 */

data class OperatorAccount(
    val id: Int = 0,
    val username: String = "",
    val role: String = "Staff",
    val pin: String = "",
    val mobile: String = "",
    val email: String = "",
    val isActivated: Boolean = false,
    val permissions: String = "",
    val isBiometricEnabled: Boolean = false,
    val isActive: Boolean = false,
    val canViewCustomers: Boolean = false,
    val canAddCustomers: Boolean = false,
    val canEditCustomers: Boolean = false,
    val canDeleteCustomers: Boolean = false,
    val canViewSuppliers: Boolean = false,
    val canAddSuppliers: Boolean = false,
    val canEditSuppliers: Boolean = false,
    val canDeleteSuppliers: Boolean = false,
    val canViewTransactions: Boolean = false,
    val canAddTransactions: Boolean = false,
    val canEditTransactions: Boolean = false,
    val canDeleteTransactions: Boolean = false,
    val canManageWallet: Boolean = false,
    val canManageExpenses: Boolean = false,
    val canViewReports: Boolean = false
)

object SyncStatus {
    const val PENDING_CREATE = 0
    const val SYNCED = 1
    const val PENDING_UPDATE = 2
    const val PENDING_DELETE = 3
    const val SYNC_FAILED = 4
}

data class Customer(
    val id: Int = 0,
    val serverId: Int = 0,
    val name: String = "",
    val phone: String = "",
    val address: String = "",
    val securityNotes: String = "",
    val avatarColor: String = "4280391411",
    val avatarEmoji: String = "👤",
    val timestamp: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null,
    val syncStatus: Int = SyncStatus.SYNCED,
    val syncError: String? = null,
    val retryCount: Int = 0,
    val lastSyncAttemptAt: Long? = null
)

data class Supplier(
    val id: Int = 0,
    val serverId: Int = 0,
    val name: String = "",
    val phone: String = "",
    val address: String = "",
    val tradeLicense: String = "",
    val securityNotes: String = "",
    val avatarColor: String = "4280391411",
    val avatarEmoji: String = "🏢",
    val timestamp: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null,
    val syncStatus: Int = SyncStatus.SYNCED,
    val syncError: String? = null,
    val retryCount: Int = 0,
    val lastSyncAttemptAt: Long? = null
)

data class RemittanceTransaction(
    val id: Int = 0,
    val serverId: Int = 0,
    val customerId: Int = 0,
    val supplierId: Int = 0,
    val amountSar: BigDecimal = MoneyMath.ZERO_AMOUNT,
    val customerRate: BigDecimal = MoneyMath.ZERO_RATE,
    val supplierRate: BigDecimal = MoneyMath.ZERO_RATE,
    val amountBdt: BigDecimal = MoneyMath.ZERO_AMOUNT,
    val sarCollected: BigDecimal = amountSar,
    val bdtDisbursed: BigDecimal = amountBdt,
    val receiverName: String = "",
    val receiverPhone: String = "",
    val receiverAccountType: String = "",
    val receiverAccountNo: String = "",
    val status: String = "Delivered",
    val operatorId: Int = 0,
    val walletBatchId: Int = 0,
    val notes: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null,
    val syncStatus: Int = SyncStatus.SYNCED,
    val syncError: String? = null,
    val retryCount: Int = 0,
    val lastSyncAttemptAt: Long? = null
) {
    fun getProfitBdt(): BigDecimal = MoneyMath.profitBdt(amountSar, customerRate, supplierRate)
    fun getProfitSar(): BigDecimal = MoneyMath.profitSar(amountSar, amountBdt, customerRate)
}

data class SupplierDeposit(
    val id: Int = 0,
    val serverId: Int = 0,
    val supplierId: Int = 0,
    val amountSar: BigDecimal = MoneyMath.ZERO_AMOUNT,
    val rate: BigDecimal = MoneyMath.ZERO_RATE,
    val amountBdt: BigDecimal = MoneyMath.ZERO_AMOUNT,
    val paidBdt: BigDecimal = MoneyMath.ZERO_AMOUNT,
    val transactionType: String = "SAR_GIVEN",
    val notes: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null,
    val syncStatus: Int = SyncStatus.SYNCED,
    val syncError: String? = null,
    val retryCount: Int = 0,
    val lastSyncAttemptAt: Long? = null
)

data class ExpenseIncome(
    val id: Int = 0,
    val serverId: Int = 0,
    val title: String = "",
    val amount: BigDecimal = MoneyMath.ZERO_AMOUNT,
    val currency: String = "BDT",
    val isExpense: Boolean = true,
    val category: String = "General",
    val timestamp: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null,
    val syncStatus: Int = SyncStatus.SYNCED,
    val syncError: String? = null,
    val retryCount: Int = 0,
    val lastSyncAttemptAt: Long? = null
)

data class DailyRate(
    val date: String = "",
    val customerRate: BigDecimal = MoneyMath.ZERO_RATE,
    val supplierRate: BigDecimal = MoneyMath.ZERO_RATE
)

data class WalletLedger(
    val id: Int = 0,
    val serverId: Int = 0,
    val name: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null,
    val syncStatus: Int = SyncStatus.SYNCED,
    val syncError: String? = null,
    val retryCount: Int = 0,
    val lastSyncAttemptAt: Long? = null
)

data class WalletBatch(
    val id: Int = 0,
    val serverId: Int = 0,
    val ledgerId: Int = 0,
    val rate: BigDecimal = MoneyMath.ZERO_RATE,
    val initialBdt: BigDecimal = MoneyMath.ZERO_AMOUNT,
    val remainingBdt: BigDecimal = MoneyMath.ZERO_AMOUNT,
    val supplierId: Int = 0,
    val supplierDepositId: Int = 0,
    val notes: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null,
    val syncStatus: Int = SyncStatus.SYNCED,
    val syncError: String? = null,
    val retryCount: Int = 0,
    val lastSyncAttemptAt: Long? = null
)

/**
 * Compatibility projection used by older ViewModel/UI code.
 *
 * The authoritative durable mutation state is stored encrypted in
 * LocalFirstStore.outbox. Network failures, retries and deferred edits remain
 * recoverable after app/process restart and are replayed by SafaSyncWorker.
 */
data class SyncOutbox(
    val id: Int = 0,
    val userId: Int = 0,
    val accountId: Int = 0,
    val entityType: String = "",
    val entityLocalId: Int = 0,
    val entityServerId: Int = 0,
    val operation: String = "",
    val payloadJson: String = "",
    val status: String = OutboxStatus.PENDING,
    val retryCount: Int = 0,
    val lastError: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

object OutboxStatus {
    const val PENDING = "PENDING"
    const val PROCESSING = "PROCESSING"
    const val SYNCED = "SYNCED"
    const val FAILED = "FAILED"
}

object OutboxOperation {
    const val CREATE = "CREATE"
    const val UPDATE = "UPDATE"
    const val DELETE = "DELETE"
}
