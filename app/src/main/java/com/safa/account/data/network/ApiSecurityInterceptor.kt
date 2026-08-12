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
 * Destructive DELETE operations require explicit UI confirmation before the
 * request is sent. Biometric quick-unlock sessions also cannot be queried or
 * used until the current process has passed the device biometric gate.
 */
class ApiSecurityInterceptor(
    private val apiKey: String,
    @Suppress("UNUSED_PARAMETER") private val legacyApiSecret: String = "",
    private val tokenManager: TokenManager? = null
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val path = originalRequest.url.encodedPath
        val isLoginRequest = path.endsWith("/auth/login")
        val isRefreshRequest = path.endsWith("/auth/refresh")
        val isSessionCheck = path.endsWith("/auth/session")
        val isDeleteRequest = originalRequest.method.equals("DELETE", ignoreCase = true)
        val alreadyConfirmed = originalRequest.header("X-SAFA-DELETE-CONFIRM") == "true"

        if (isSessionCheck && tokenManager?.isBiometricQuickUnlockEnabled() == true && !tokenManager.isBiometricUnlockApproved()) {
            return Response.Builder()
                .request(originalRequest)
                .protocol(okhttp3.Protocol.HTTP_1_1)
                .code(401)
                .message("Biometric unlock required")
                .body("{\"status\":\"biometric_required\",\"message\":\"Biometric unlock required.\"}".toResponseBody("application/json".toMediaTypeOrNull()))
                .build()
        }

        if (isDeleteRequest && !alreadyConfirmed) {
            val confirmed = DeleteConfirmationCoordinator.awaitConfirmation(
                title = "Delete data?",
                message = "This action cannot be undone."
            )
            if (!confirmed) {
                return Response.Builder()
                    .request(originalRequest)
                    .protocol(okhttp3.Protocol.HTTP_1_1)
                    .code(499)
                    .message("Delete cancelled by user")
                    .body("{\"status\":\"cancelled\",\"message\":\"Delete cancelled by user.\"}".toResponseBody("application/json".toMediaTypeOrNull()))
                    .build()
            }
        }

        val securedRequest = if (isDeleteRequest && !alreadyConfirmed) {
            val confirmedUrl = originalRequest.url.newBuilder().setQueryParameter("confirmed", "true").build()
            originalRequest.newBuilder()
                .url(confirmedUrl)
                .header("X-SAFA-DELETE-CONFIRM", "true")
                .build()
        } else originalRequest

        var response = chain.proceed(
            buildSecuredRequest(securedRequest, includeAuthTokens = !isLoginRequest && !isRefreshRequest)
        )

        if (
            !isLoginRequest &&
            !isRefreshRequest &&
            response.code == 401 &&
            tokenManager != null &&
            !isSessionCheck &&
            securedRequest.header("X-SAFA-RETRY") != "true"
        ) {
            synchronized(tokenManager) {
                val requestAccessToken = securedRequest.header("Authorization")?.removePrefix("Bearer ")
                val currentAccessToken = tokenManager.getAccessToken()
                val newToken = if (!currentAccessToken.isNullOrBlank() && currentAccessToken != requestAccessToken) {
                    currentAccessToken
                } else {
                    performTokenRefresh(tokenManager.getRefreshToken())
                }

                if (!newToken.isNullOrBlank()) {
                    response.close()
                    response = chain.proceed(
                        buildSecuredRequest(
                            securedRequest.newBuilder().header("X-SAFA-RETRY", "true").build(),
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
                    val access = json.optString("access_token").ifBlank {
                        json.optJSONObject("tokens")?.optString("access_token").orEmpty()
                    }
                    if (access.isBlank()) return null
                    val tokens = json.optJSONObject("tokens")
                    tm.saveAccessToken(access)
                    tokens?.optString("refresh_token")?.takeIf { it.isNotBlank() }?.let(tm::saveRefreshToken)
                    tokens?.optString("session_token")?.takeIf { it.isNotBlank() }?.let(tm::saveSessionToken)
                    tokens?.optString("device_token")?.takeIf { it.isNotBlank() }?.let(tm::saveDeviceToken)
                    tokens?.optString("fingerprint_token")?.takeIf { it.isNotBlank() }?.let(tm::saveFingerprintToken)
                    access
                }
        } catch (_: Exception) {
            null
        }
    }
}
