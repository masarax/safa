package com.safa.account.data.network

import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject

/**
 * Keeps the login endpoint's failure class visible to the existing UI
 * contract. Only /auth/login responses are transformed.
 */
class LoginErrorResponseInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        if (!request.url.encodedPath.endsWith("/auth/login") || response.isSuccessful) {
            return response
        }

        val body = response.body ?: return response
        val raw = body.string()
        val message = runCatching {
            val json = JSONObject(raw)
            json.optString("message").trim().ifBlank {
                json.optJSONObject("errors")?.let { errors ->
                    errors.keys().asSequence().mapNotNull { key ->
                        errors.optJSONArray(key)?.optString(0)?.takeIf { it.isNotBlank() }
                    }.firstOrNull()
                } ?: ""
            }
        }.getOrDefault("")

        val fallback = when (response.code) {
            401 -> "Mobile number or PIN is incorrect."
            403 -> "Authentication was denied for this account or device."
            422 -> "The login request is invalid."
            429 -> "Too many login attempts. Please wait and try again."
            in 500..599 -> "The authentication server is unavailable. Please try again later."
            else -> "Login failed (HTTP ${response.code})."
        }

        val text = when {
            response.code == 401 -> fallback
            response.code == 403 -> "Account/device error: ${message.ifBlank { fallback }}"
            response.code == 422 -> "Validation error: ${message.ifBlank { fallback }}"
            response.code == 429 -> "Rate limit: ${message.ifBlank { fallback }}"
            response.code in 500..599 -> "Server error: ${message.ifBlank { fallback }}"
            else -> "Login error: ${message.ifBlank { fallback }}"
        }

        return response.newBuilder()
            .body(text.toResponseBody(body.contentType()))
            .build()
    }
}
