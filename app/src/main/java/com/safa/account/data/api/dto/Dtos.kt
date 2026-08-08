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

@JsonClass(generateAdapter = true)
data class GraphQlRequest(
    @Json(name = "query") val query: String,
    @Json(name = "variables") val variables: Map<String, Any?>? = null,
    @Json(name = "operationName") val operationName: String? = null
)

@JsonClass(generateAdapter = true)
data class GraphQlErrorLocation(
    @Json(name = "line") val line: Int? = null,
    @Json(name = "column") val column: Int? = null
)

@JsonClass(generateAdapter = true)
data class GraphQlError(
    @Json(name = "message") val message: String = "",
    @Json(name = "locations") val locations: List<GraphQlErrorLocation>? = null,
    @Json(name = "path") val path: List<Any>? = null,
    @Json(name = "extensions") val extensions: Map<String, Any?>? = null
)

@JsonClass(generateAdapter = true)
data class GraphQlResponse(
    @Json(name = "data") val data: Map<String, Any?>? = null,
    @Json(name = "errors") val errors: List<GraphQlError>? = null
)

@JsonClass(generateAdapter = true)
data class MobilePinLoginRequest(
    @Json(name = "mobile") val mobile: String,
    @Json(name = "pin") val pin: String
)

@JsonClass(generateAdapter = true)
data class ActivateSuperAdminRequest(
    @Json(name = "name") val name: String,
    @Json(name = "email") val email: String,
    @Json(name = "mobile") val mobile: String,
    @Json(name = "pin") val pin: String,
    @Json(name = "new_pin") val newPin: String = pin
)

@JsonClass(generateAdapter = true)
data class OperatorApiRequest(
    @Json(name = "name") val name: String,
    @Json(name = "mobile") val mobile: String,
    @Json(name = "email") val email: String? = null,
    @Json(name = "role") val role: String,
    @Json(name = "pin") val pin: String? = null,
    @Json(name = "is_activated") val isActivated: Boolean = true,
    @Json(name = "permissions") val permissions: Map<String, Boolean> = emptyMap()
)

