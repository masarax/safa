package com.safa.account.data.network

import com.safa.account.data.api.TokenManager
import okhttp3.FormBody
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Adds the public SAFA API client identifier and authenticated session headers.
 *
 * The Android APK is an untrusted client: it must never contain a server secret.
 * Request authenticity after login comes from the short-lived access token and
 * active server-side session, while the API key is only a public client id.
 */
class ApiSecurityInterceptor(
    private val apiKey: String,
    @Suppress("UNUSED_PARAMETER") private val legacyApiSecret: String = "",
    private val tokenManager: TokenManager? = null
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val isLoginRequest = originalRequest.url.encodedPath.endsWith("/auth/login")
        var response = chain.proceed(buildSecuredRequest(originalRequest, includeAuthTokens = !isLoginRequest))

        if (!isLoginRequest && response.code == 401 && tokenManager != null && originalRequest.header("X-SAFA-RETRY") != "true") {
            val refreshToken = tokenManager.getRefreshToken()
            if (!refreshToken.isNullOrBlank()) {
                synchronized(this) {
                    val currentAccessToken = tokenManager.getAccessToken()
                    val requestAccessToken = originalRequest.header("Authorization")?.removePrefix("Bearer ")
                    val newToken = if (!currentAccessToken.isNullOrBlank() && currentAccessToken != requestAccessToken) {
                        currentAccessToken
                    } else {
                        performTokenRefresh(refreshToken)
                    }
                    if (!newToken.isNullOrBlank()) {
                        response.close()
                        response = chain.proceed(
                            buildSecuredRequest(
                                originalRequest.newBuilder().header("X-SAFA-RETRY", "true").build(),
                                includeAuthTokens = true
                            )
                        )
                    }
                }
            }
        }
        return response
    }

    private fun buildSecuredRequest(request: Request, includeAuthTokens: Boolean): Request {
        val builder = request.newBuilder()
            .header("X-SAFA-API-KEY", apiKey)
            .header("X-SAFA-CLIENT", "android")

        if (includeAuthTokens) {
            tokenManager?.let { tm ->
                tm.getAccessToken()?.takeIf { it.isNotBlank() }?.let { builder.header("Authorization", "Bearer $it") }
                tm.getRefreshToken()?.takeIf { it.isNotBlank() }?.let { builder.header("X-SAFA-REFRESH-TOKEN", it) }
                tm.getDeviceToken().takeIf { it.isNotBlank() }?.let { builder.header("X-SAFA-DEVICE-TOKEN", it) }
                tm.getSessionToken()?.takeIf { it.isNotBlank() }?.let { builder.header("X-SAFA-SESSION-TOKEN", it) }
                tm.getFingerprintToken().takeIf { it.isNotBlank() }?.let { builder.header("X-SAFA-FINGERPRINT-TOKEN", it) }
                tm.getActiveAccountId()?.let { builder.header("X-SAFA-ACCOUNT-ID", it.toString()) }
            }
        }
        return builder.build()
    }

    private fun performTokenRefresh(refreshToken: String): String? {
        val baseUrl = tokenManager?.getBaseUrl() ?: return null
        val refreshUrl = if (baseUrl.endsWith("/")) "${baseUrl}auth/refresh" else "$baseUrl/auth/refresh"
        val deviceToken = tokenManager.getDeviceToken()
        val fingerprintToken = tokenManager.getFingerprintToken()
        val request = Request.Builder()
            .url(refreshUrl)
            .post(FormBody.Builder().add("refresh_token", refreshToken).add("device_token", deviceToken).build())
            .header("X-SAFA-API-KEY", apiKey)
            .header("X-SAFA-CLIENT", "android")
            .header("X-SAFA-REFRESH-TOKEN", refreshToken)
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
                    if (!response.isSuccessful) {
                        if (response.code == 401 || response.code == 403) tokenManager.clearAllTokens()
                        return null
                    }
                    val json = JSONObject(response.body?.string().orEmpty())
                    val access = json.optString("access_token", json.optString("token", ""))
                    if (access.isBlank()) return null
                    tokenManager.saveAccessToken(access)
                    json.optString("refresh_token", refreshToken)
                        .takeIf { it.isNotBlank() }
                        ?.let(tokenManager::saveRefreshToken)
                    json.optString("session_token", tokenManager.getSessionToken().orEmpty())
                        .takeIf { it.isNotBlank() }
                        ?.let(tokenManager::saveSessionToken)
                    access
                }
        } catch (_: Exception) {
            null
        }
    }
}
