package com.safa.account.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

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

@Entity(tableName = "sync_outbox")
data class SyncOutbox(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userId: Int = 0,
    val accountId: Int = 0,
    val entityType: String, // "CUSTOMER", "SUPPLIER", "TRANSACTION", "SUPPLIER_DEPOSIT", "EXPENSE_INCOME", "WALLET_LEDGER", "WALLET_BATCH"
    val entityLocalId: Int,
    val entityServerId: Int = 0,
    val operation: String, // "CREATE", "UPDATE", "DELETE"
    val payloadJson: String,
    val status: String = OutboxStatus.PENDING,
    val retryCount: Int = 0,
    val lastError: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
