package com.safa.account.data.network

import android.util.Log
import com.squareup.moshi.JsonReader
import okio.Buffer
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import javax.net.ssl.SSLException

sealed interface ApiLoginError {
    val code: String
    val message: String

    data class Credentials(override val message: String) : ApiLoginError {
        override val code = "INVALID_CREDENTIALS"
    }

    data class Authorization(override val code: String, override val message: String) : ApiLoginError
    data class Validation(override val code: String, override val message: String) : ApiLoginError

    data class Throttled(
        override val message: String,
        val retryAfterSeconds: Long? = null
    ) : ApiLoginError {
        override val code = "RATE_LIMITED"
    }

    data class Server(override val message: String) : ApiLoginError {
        override val code = "SERVER_ERROR"
    }

    data class Network(override val code: String, override val message: String) : ApiLoginError

    data class Unexpected(override val message: String) : ApiLoginError {
        override val code = "UNEXPECTED_RESPONSE"
    }
}

class LoginNetworkException(
    val error: ApiLoginError.Network
) : IOException(error.message) {
    override fun printStackTrace() {
        if (com.safa.account.BuildConfig.DEBUG) {
            Log.d("SafaLogin", "login network failure: code=${error.code}")
        }
    }
}

object ApiLoginErrorParser {
    fun fromHttp(status: Int, rawBody: String, retryAfter: String? = null): ApiLoginError {
        val parsed = parseBody(rawBody)
        val error = when (status) {
            401 -> ApiLoginError.Credentials("Mobile number or PIN is incorrect.")
            403 -> when (parsed.code?.uppercase()) {
                "DEVICE_REVOKED", "REVOKED_DEVICE", "DEVICE_INACTIVE" ->
                    ApiLoginError.Authorization("DEVICE_REVOKED", "This device is revoked for this account.")
                "ACCOUNT_INACTIVE", "INACTIVE_ACCOUNT", "ACCOUNT_DISABLED" ->
                    ApiLoginError.Authorization("ACCOUNT_INACTIVE", "This account is inactive. Please contact an administrator.")
                else -> ApiLoginError.Authorization("FORBIDDEN", "Authentication is not allowed for this account or device.")
            }
            422 -> ApiLoginError.Validation(
                parsed.code ?: "VALIDATION_FAILED",
                "Please check the mobile number and PIN format."
            )
            429 -> ApiLoginError.Throttled(
                "Too many login attempts. Please wait and try again.",
                parseRetryAfter(retryAfter)
            )
            in 500..599 -> ApiLoginError.Server("The authentication server is unavailable. Please try again later.")
            else -> ApiLoginError.Unexpected("The server returned an unexpected login response.")
        }
        debug(status, error.code)
        return error
    }

    fun fromThrowable(t: Throwable): ApiLoginError.Network {
        val error = when (t) {
            is LoginNetworkException -> t.error
            is SocketTimeoutException -> ApiLoginError.Network("TIMEOUT", "Connection timed out. Check your connection and try again.")
            is ConnectException -> ApiLoginError.Network("CONNECTIVITY", "Unable to connect to the server. Check your internet connection.")
            is SSLException -> ApiLoginError.Network("TLS", "A secure connection to the server could not be established.")
            is IOException -> ApiLoginError.Network("NETWORK", "Network connection failed. Check your internet connection and try again.")
            else -> ApiLoginError.Network("NETWORK", "Network connection failed. Check your internet connection and try again.")
        }
        debug(null, error.code)
        return error
    }

    private data class ParsedBody(val code: String?, val message: String?)

    private fun parseBody(raw: String): ParsedBody = runCatching {
        if (raw.isBlank()) return ParsedBody(null, null)
        val value = JsonReader.of(Buffer().writeUtf8(raw)).use { it.readJsonValue() }
        val root = value as? Map<*, *> ?: return ParsedBody(null, null)
        val nested = root["error"] as? Map<*, *>
        val code = firstText(
            nested?.get("code"), root["code"], nested?.get("error_code"), root["error_code"]
        )
        val message = firstText(
            nested?.get("message"), root["message"], validationMessage(root["errors"]), validationMessage(nested?.get("errors"))
        )
        ParsedBody(code, message)
    }.getOrDefault(ParsedBody(null, null))

    private fun validationMessage(errors: Any?): String? {
        val map = errors as? Map<*, *> ?: return null
        map.values.forEach { value ->
            when (value) {
                is List<*> -> value.asSequence().mapNotNull { it?.toString()?.trim() }.firstOrNull { it.isNotBlank() }?.let { return it }
                is String -> value.trim().takeIf { it.isNotBlank() }?.let { return it }
            }
        }
        return null
    }

    private fun firstText(vararg values: Any?): String? = values
        .asSequence()
        .mapNotNull { it?.toString()?.trim() }
        .firstOrNull { it.isNotBlank() }

    private fun parseRetryAfter(value: String?): Long? {
        val seconds = value?.trim()?.toLongOrNull()
        return seconds?.takeIf { it >= 0 }
    }

    private fun debug(status: Int?, code: String) {
        if (com.safa.account.BuildConfig.DEBUG) {
            runCatching { Log.d("SafaLogin", "login failure classified: status=${status ?: "network"}, code=$code") }
        }
    }
}
