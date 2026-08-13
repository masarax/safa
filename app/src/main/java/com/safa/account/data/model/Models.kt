package com.safa.account.data.model

/**
 * Application/domain compatibility models.
 *
 * Laravel/MySQL is the server-authoritative source of accepted business state,
 * while Android keeps an encrypted durable local-first cache and mutation outbox
 * in LocalFirstStore. The local store survives process/device restarts and is
 * cleared at the authenticated account boundary. These models are serialized
 * into that canonical store by AppRepository rather than being Room entities.
 */

data class OperatorAccount(
    val id: Int = 0,
    val username: String = "",
    val role: String = "Staff",
    val pin: String = "",
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
    val amountSar: Double = 0.0,
    val customerRate: Double = 0.0,
    val supplierRate: Double = 0.0,
    val amountBdt: Double = 0.0,
    val sarCollected: Double = amountSar,
    val bdtDisbursed: Double = amountBdt,
    val receiverName: String = "",
    val receiverPhone: String = "",
    val receiverAccountType: String = "",
    val receiverAccountNo: String = "",
    val status: String = "Pending",
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
    fun getProfitBdt(): Double = (customerRate - supplierRate) * amountSar
    fun getProfitSar(): Double = if (customerRate > 0.0) amountSar - (amountBdt / customerRate) else 0.0
}

data class SupplierDeposit(
    val id: Int = 0,
    val serverId: Int = 0,
    val supplierId: Int = 0,
    val amountSar: Double = 0.0,
    val rate: Double = 0.0,
    val amountBdt: Double = 0.0,
    val paidBdt: Double = 0.0,
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
    val amount: Double = 0.0,
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
    val customerRate: Double = 0.0,
    val supplierRate: Double = 0.0
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
    val rate: Double = 0.0,
    val initialBdt: Double = 0.0,
    val remainingBdt: Double = 0.0,
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
