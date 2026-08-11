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
 * Adds public SAFA client identity and authenticated session headers.
 * Server secrets never ship in the Android APK.
 *
 * Access-token refresh is serialized per TokenManager so concurrent 401s do
 * not race each other and consume the same single-use refresh token twice.
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

        var response = chain.proceed(
            buildSecuredRequest(originalRequest, includeAuthTokens = !isLoginRequest && !isRefreshRequest)
        )

        if (
            !isLoginRequest &&
            !isRefreshRequest &&
            response.code == 401 &&
            tokenManager != null &&
            originalRequest.header("X-SAFA-RETRY") != "true"
        ) {
            synchronized(tokenManager) {
                // Another request may have refreshed the token while this
                // request was waiting for the lock. Reuse it when possible.
                val requestAccessToken = originalRequest.header("Authorization")?.removePrefix("Bearer ")
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
                            originalRequest.newBuilder()
                                .header("X-SAFA-RETRY", "true")
                                .build(),
                            includeAuthTokens = true
                        )
                    )
                } else if (response.code == 401) {
                    // A 401 means the authenticated session could not be
                    // recovered. Preserve that response for the caller while
                    // clearing local credentials. A 403 is authorization/
                    // permission failure and must never destroy a valid session.
                    tokenManager.clearAllTokens()
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
