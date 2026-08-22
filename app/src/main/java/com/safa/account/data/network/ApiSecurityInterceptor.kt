package com.safa.account.data.network

import com.safa.account.data.api.TokenManager
import okhttp3.FormBody
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Adds public SAFA client identity and authenticated session headers.
 * Server secrets never ship in the Android APK.
 *
 * The interceptor is intentionally non-interactive. A destructive request must
 * already be confirmed by the application/domain layer; otherwise it is
 * rejected immediately without calling the network or waiting for Activity UI.
 */
class ApiSecurityInterceptor(
    private val apiKey: String,
    @Suppress("UNUSED_PARAMETER") private val legacyApiSecret: String = "",
    private val tokenManager: TokenManager? = null,
    private val refreshOverride: ((String?) -> String?)? = null
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val path = originalRequest.url.encodedPath
        val isLoginRequest = path.endsWith("/auth/login")
        val isRefreshRequest = path.endsWith("/auth/refresh")
        val isSessionCheck = path.endsWith("/auth/session")
        val isDeleteRequest = originalRequest.method.equals("DELETE", ignoreCase = true)
        val deleteConfirmed = originalRequest.header("X-SAFA-DELETE-CONFIRM") == "true" ||
            originalRequest.url.queryParameter("confirmed").equals("true", ignoreCase = true)

        if (isSessionCheck && tokenManager?.isBiometricQuickUnlockEnabled() == true && !tokenManager.isBiometricUnlockApproved()) {
            return localJsonResponse(
                originalRequest,
                401,
                "Biometric unlock required",
                "{\"status\":\"biometric_required\",\"message\":\"Biometric unlock required.\"}"
            )
        }

        if (isDeleteRequest && !deleteConfirmed) {
            return localJsonResponse(
                originalRequest,
                428,
                "Delete confirmation required",
                "{\"status\":\"confirmation_required\",\"message\":\"Confirm the delete before sending it.\"}"
            )
        }

        val securedRequest = buildSecuredRequest(
            originalRequest,
            includeAuthTokens = !isLoginRequest && !isRefreshRequest
        )
        var response = chain.proceed(securedRequest)

        if (
            !isLoginRequest &&
            !isRefreshRequest &&
            response.code == 401 &&
            tokenManager != null &&
            !isSessionCheck &&
            originalRequest.header("X-SAFA-RETRY") != "true"
        ) {
            synchronized(tokenManager) {
                // Compare against the token that actually reached the server.
                // Retrofit's original request normally has no Authorization header
                // because this interceptor adds it immediately before proceed().
                val requestAccessToken = securedRequest.header("Authorization")?.removePrefix("Bearer ")
                val currentAccessToken = tokenManager.getAccessToken()
                val newToken = if (!currentAccessToken.isNullOrBlank() && currentAccessToken != requestAccessToken) {
                    // Another request already completed refresh rotation while this
                    // request was in flight. Reuse the newer stored token set.
                    currentAccessToken
                } else {
                    val refreshToken = tokenManager.getRefreshToken()
                    // A test override returning null explicitly represents refresh
                    // failure. Do not fall through into the real network refresh in
                    // that case; production calls use performTokenRefresh directly.
                    if (refreshOverride != null) {
                        refreshOverride.invoke(refreshToken)
                    } else {
                        performTokenRefresh(refreshToken)
                    }
                }

                if (!newToken.isNullOrBlank()) {
                    response.close()
                    response = chain.proceed(
                        buildSecuredRequest(
                            originalRequest.newBuilder().header("X-SAFA-RETRY", "true").build(),
                            includeAuthTokens = true
                        )
                    )
                } else if (response.code == 401) {
                    tokenManager.notifySessionInvalidated()
                }
            }
        }

        return response
    }

    private fun localJsonResponse(request: Request, code: Int, message: String, body: String): Response =
        Response.Builder()
            .request(request)
            .protocol(okhttp3.Protocol.HTTP_1_1)
            .code(code)
            .message(message)
            .body(body.toResponseBody("application/json".toMediaTypeOrNull()))
            .build()

    private fun buildSecuredRequest(request: Request, includeAuthTokens: Boolean): Request {
        val builder = request.newBuilder()
            .header("X-SAFA-API-KEY", apiKey)
            .header("X-SAFA-CLIENT", "android")

        tokenManager?.let { tm ->
            tm.getDeviceToken().takeIf { it.isNotBlank() }?.let { builder.header("X-SAFA-DEVICE-TOKEN", it) }
            tm.getFingerprintToken().takeIf { it.isNotBlank() }?.let { builder.header("X-SAFA-FINGERPRINT-TOKEN", it) }
        }

        if (includeAuthTokens) {
            tokenManager?.let { tm ->
                tm.getAccessToken()?.takeIf { it.isNotBlank() }?.let { builder.header("Authorization", "Bearer $it") }
                tm.getRefreshToken()?.takeIf { it.isNotBlank() }?.let { builder.header("X-SAFA-REFRESH-TOKEN", it) }
                tm.getSessionToken()?.takeIf { it.isNotBlank() }?.let { builder.header("X-SAFA-SESSION-TOKEN", it) }
                tm.getActiveAccountId()?.let { builder.header("X-SAFA-ACCOUNT-ID", it.toString()) }
            }
        }
        return builder.build()
    }

    private fun performTokenRefresh(refreshToken: String?): String? {
        val tm = tokenManager ?: return null
        val token = refreshToken?.takeIf { it.isNotBlank() } ?: return null
        val baseUrl = tm.getBaseUrl()
        val refreshUrl = if (baseUrl.endsWith("/")) "${baseUrl}auth/refresh" else "$baseUrl/auth/refresh"
        val deviceToken = tm.getDeviceToken()
        val fingerprintToken = tm.getFingerprintToken()

        val request = Request.Builder()
            .url(refreshUrl)
            .post(
                FormBody.Builder()
                    .add("refresh_token", token)
                    .add("device_token", deviceToken)
                    .add("fingerprint_token", fingerprintToken)
                    .build()
            )
            .header("X-SAFA-API-KEY", apiKey)
            .header("X-SAFA-CLIENT", "android")
            .header("X-SAFA-REFRESH-TOKEN", token)
            .header("X-SAFA-DEVICE-TOKEN", deviceToken)
            .header("X-SAFA-FINGERPRINT-TOKEN", fingerprintToken)
            .build()

        return try {
            OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build()
                .newCall(request)
                .execute()
                .use { response ->
                    if (!response.isSuccessful) return null
                    val json = JSONObject(response.body?.string().orEmpty())
                    persistRefreshGeneration(tm, token, json)
                }
        } catch (_: Exception) {
            null
        }
    }
}

/**
 * Validate a refresh response completely before mutating local credentials, then
 * persist the resulting generation through TokenManager's single-edit path.
 *
 * A refresh response is allowed to omit unchanged session/device/fingerprint
 * values. The old refresh token is used only as a fallback when the server does
 * not rotate it. If another flow has already replaced that refresh token while
 * the request was in flight, this stale response is discarded and the newer
 * access token is returned instead.
 */
internal fun persistRefreshGeneration(
    tokenManager: TokenManager,
    expectedRefreshToken: String,
    responseJson: JSONObject
): String? {
    val tokens = responseJson.optJSONObject("tokens") ?: responseJson
    val accessToken = tokens.optString("access_token")
        .ifBlank { responseJson.optString("access_token") }
        .takeIf { it.isNotBlank() }
        ?: return null

    val rotatedRefreshToken = tokens.optString("refresh_token")
        .ifBlank { responseJson.optString("refresh_token") }
        .takeIf { it.isNotBlank() }
    val sessionToken = tokens.optString("session_token")
        .ifBlank { responseJson.optString("session_token") }
        .takeIf { it.isNotBlank() }
    val deviceToken = tokens.optString("device_token")
        .ifBlank { responseJson.optString("device_token") }
        .takeIf { it.isNotBlank() }
    val fingerprintToken = tokens.optString("fingerprint_token")
        .ifBlank { responseJson.optString("fingerprint_token") }
        .takeIf { it.isNotBlank() }

    synchronized(tokenManager) {
        val currentRefreshToken = tokenManager.getRefreshToken()
        if (currentRefreshToken != expectedRefreshToken) {
            // Login, logout, or a newer refresh generation won the race while the
            // network request was in flight. Never overwrite that newer state.
            return tokenManager.getAccessToken()
        }

        val effectiveRefreshToken = rotatedRefreshToken ?: currentRefreshToken
        if (effectiveRefreshToken.isNullOrBlank()) return null

        tokenManager.saveAllTokens(
            accessToken = accessToken,
            refreshToken = effectiveRefreshToken,
            deviceToken = deviceToken ?: tokenManager.getDeviceToken(),
            sessionToken = sessionToken ?: tokenManager.getSessionToken(),
            fingerprintToken = fingerprintToken ?: tokenManager.getFingerprintToken()
        )
    }

    return accessToken
}
