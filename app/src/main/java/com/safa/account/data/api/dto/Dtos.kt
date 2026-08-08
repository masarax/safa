package com.safa.account.data.api.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class LoginRequest(
    @Json(name = "username") val username: String,
    @Json(name = "password") val password: String
)

@JsonClass(generateAdapter = true)
data class LoginResponse(
    @Json(name = "token") val token: String,
    @Json(name = "message") val message: String? = null
)

@JsonClass(generateAdapter = true)
data class SyncUpPayload(
    @Json(name = "transactions") val transactions: List<Map<String, Any?>> = emptyList(),
    @Json(name = "customers") val customers: List<Map<String, Any?>> = emptyList(),
    @Json(name = "suppliers") val suppliers: List<Map<String, Any?>> = emptyList(),
    @Json(name = "supplier_deposits") val supplierDeposits: List<Map<String, Any?>> = emptyList(),
    @Json(name = "expenses_incomes") val expensesIncomes: List<Map<String, Any?>> = emptyList(),
    @Json(name = "wallet_batches") val walletBatches: List<Map<String, Any?>> = emptyList(),
    @Json(name = "wallet_ledgers") val walletLedgers: List<Map<String, Any?>> = emptyList()
)

@JsonClass(generateAdapter = true)
data class SyncDownResponse(
    @Json(name = "status") val status: String = "",
    @Json(name = "transactions") val transactions: List<Map<String, Any?>> = emptyList(),
    @Json(name = "customers") val customers: List<Map<String, Any?>> = emptyList(),
    @Json(name = "suppliers") val suppliers: List<Map<String, Any?>> = emptyList(),
    @Json(name = "supplier_deposits") val supplierDeposits: List<Map<String, Any?>> = emptyList(),
    @Json(name = "expenses_incomes") val expensesIncomes: List<Map<String, Any?>> = emptyList(),
    @Json(name = "wallet_batches") val walletBatches: List<Map<String, Any?>> = emptyList(),
    @Json(name = "wallet_ledgers") val walletLedgers: List<Map<String, Any?>> = emptyList()
)
