package com.safa.account.data.network

import android.util.Log
import org.json.JSONObject
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import javax.net.ssl.SSLException

sealed interface ApiLoginError {
    val code: String
    val message: String

    data class Credentials(override val message: String) : ApiLoginError { override val code = "INVALID_CREDENTIALS" }
    data class Authorization(override val code: String, override val message: String) : ApiLoginError
    data class Validation(override val code: String, override val message: String) : ApiLoginError
    data class Throttled(override val message: String, val retryAfterSeconds: Long? = null) : ApiLoginError { override val code = "RATE_LIMITED" }
    data class Server(override val message: String) : ApiLoginError { override val code = "SERVER_ERROR" }
    data class Network(override val code: String, override val message: String) : ApiLoginError
    data class Unexpected(override val message: String) : ApiLoginError { override val code = "UNEXPECTED_RESPONSE" }
}

object ApiLoginErrorParser {
    fun fromHttp(status: Int, rawBody: String, retryAfter: String? = null): ApiLoginError {
        val (backendCode, backendMessage) = parseBody(rawBody)
        val error = when (status) {
            401 -> ApiLoginError.Credentials("Mobile number or PIN is incorrect.")
            403 -> when (backendCode) {
                "DEVICE_REVOKED" -> ApiLoginError.Authorization("DEVICE_REVOKED", "This device is revoked for this account.")
                "ACCOUNT_INACTIVE" -> ApiLoginError.Authorization("ACCOUNT_INACTIVE", "This account is inactive. Please contact an administrator.")
                else -> ApiLoginError.Authorization("FORBIDDEN", "Authentication is not allowed for this account or device.")
            }
            422 -> ApiLoginError.Validation(backendCode ?: "VALIDATION_FAILED", backendMessage ?: "Please check the mobile number and PIN format.")
            429 -> ApiLoginError.Throttled(backendMessage ?: "Too many login attempts. Please wait and try again.", retryAfter?.toLongOrNull())
            in 500..599 -> ApiLoginError.Server("The authentication server is unavailable. Please try again later.")
            else -> ApiLoginError.Unexpected(backendMessage ?: "The server returned an unexpected login response.")
        }
        debug(status, error.code)
        return error
    }

    fun fromThrowable(t: Throwable): ApiLoginError {
        val error = when (t) {
            is SocketTimeoutException -> ApiLoginError.Network("TIMEOUT", "Connection timed out. Check your connection and try again.")
            is ConnectException -> ApiLoginError.Network("CONNECTIVITY", "Unable to connect to the server. Check your internet connection.")
            is SSLException -> ApiLoginError.Network("TLS", "A secure connection to the server could not be established.")
            is IOException -> ApiLoginError.Network("NETWORK", "Network connection failed. Check your internet connection and try again.")
            else -> ApiLoginError.Unexpected("Unable to complete login right now. Please try again.")
        }
        debug(null, error.code)
        return error
    }

    private fun parseBody(raw: String): Pair<String?, String?> = runCatching {
        if (raw.isBlank()) return Pair(null, null)
        val root = JSONObject(raw)
        val nested = root.optJSONObject("error")
        val code = nested?.optString("code")?.trim()?.takeIf { it.isNotBlank() }
            ?: root.optString("code").trim().takeIf { it.isNotBlank() }
        val message = nested?.optString("message")?.trim()?.takeIf { it.isNotBlank() }
            ?: root.optString("message").trim().takeIf { it.isNotBlank() }
        Pair(code, message)
    }.getOrDefault(Pair(null, null))

    private fun debug(status: Int?, code: String) {
        if (com.safa.account.BuildConfig.DEBUG) {
            runCatching { Log.d("SafaLogin", "login failure classified: status=${status ?: "network"}, code=$code") }
        }
    }
}
