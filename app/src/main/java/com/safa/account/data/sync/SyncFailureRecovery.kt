package com.safa.account.data.sync

import android.content.Context
import com.safa.account.data.api.TokenManager
import com.safa.account.data.local.LocalFirstStore

/**
 * Re-queues durable mutations that were previously frozen by an authentication
 * failure after a valid authenticated client/session becomes available again.
 *
 * Permission/validation failures are deliberately excluded: only HTTP 401
 * failures are recoverable here, so this cannot bypass account permissions.
 */
object SyncFailureRecovery {
    private val entities = listOf(
        "customers",
        "suppliers",
        "wallet_ledgers",
        "supplier_deposits",
        "wallet_batches",
        "transactions",
        "expenses_incomes",
    )

    fun recoverUnauthorized(context: Context, tokenManager: TokenManager): Int {
        if (tokenManager.getAccessToken().isNullOrBlank()) return 0
        if (RetrofitClientIdentity.effectiveApiKey(tokenManager.getApiKey()).isBlank()) return 0

        val store = LocalFirstStore(context.applicationContext)
        return try {
            var recovered = 0
            entities.forEach { entity ->
                store.getRecordPayloads(entity)
                    .asSequence()
                    .filter { it.syncStatus == LocalFirstStore.FAILED }
                    .filter { it.error?.trim()?.equals("HTTP 401", ignoreCase = true) == true }
                    .forEach { record ->
                        store.retry(entity, record.localId)
                        recovered++
                    }
            }
            recovered
        } finally {
            store.close()
        }
    }
}

/** Small seam that keeps recovery independently testable from Retrofit creation. */
internal object RetrofitClientIdentity {
    fun effectiveApiKey(storedKey: String): String =
        com.safa.account.data.api.RetrofitClient.effectiveApiKey(storedKey)
}
