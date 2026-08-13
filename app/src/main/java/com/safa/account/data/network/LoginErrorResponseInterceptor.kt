package com.safa.account.data.network

import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject

/**
 * Keeps the login endpoint's real API failure class visible to the existing
 * ViewModel/UI contract. Only /auth/login is transformed; other API responses
 * are left untouched.
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

        val prefix = when {
            response.code == 401 -> ""
            response.code == 403 -> "Account/device error: "
            response.code == 422 -> "Validation error: "
            response.code == 429 -> "Rate limit: "
            response.code in 500..599 -> "Server error: "
            else -> "Login error: "
        }
        val text = prefix + message.ifBlank { fallback }

        return response.newBuilder()
            .body(text.toResponseBody(body.contentType()))
            .build()
    }
}
