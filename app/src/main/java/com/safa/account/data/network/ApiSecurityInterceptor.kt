package com.safa.account.data.network

import com.safa.account.data.api.TokenManager
import okhttp3.FormBody
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.Buffer
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class ApiSecurityInterceptor(
    private val apiKey: String,
    private val apiSecret: String,
    private val tokenManager: TokenManager? = null
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        var response = chain.proceed(buildSecuredRequest(originalRequest))

        if (response.code == 401 && tokenManager != null && originalRequest.header("X-SAFA-RETRY") != "true") {
            val refreshToken = tokenManager.getRefreshToken()
            if (!refreshToken.isNullOrBlank()) {
                synchronized(this) {
                    val currentAccessToken = tokenManager.getAccessToken()
                    val requestAccessToken = originalRequest.header("Authorization")?.removePrefix("Bearer ")
                    val newToken = if (!currentAccessToken.isNullOrBlank() && currentAccessToken != requestAccessToken) currentAccessToken else performTokenRefresh(refreshToken)
                    if (!newToken.isNullOrBlank()) {
                        response.close()
                        response = chain.proceed(buildSecuredRequest(originalRequest.newBuilder().header("X-SAFA-RETRY", "true").build()))
                    }
                }
            }
        }
        return response
    }

    private fun buildSecuredRequest(request: Request): Request {
        val timestamp = (System.currentTimeMillis() / 1000).toString()
        val nonce = UUID.randomUUID().toString()
        var bodyString = ""
        request.body?.let {
            val buffer = Buffer()
            it.writeTo(buffer)
            bodyString = buffer.readUtf8()
        }

        val signature = generateHmac(request.method + request.url.encodedPath + timestamp + nonce + bodyString, apiSecret)
        val builder = request.newBuilder()
            .header("X-SAFA-API-KEY", apiKey)
            .header("X-SAFA-SIGNATURE", signature)
            .header("X-SAFA-TIMESTAMP", timestamp)
            .header("X-SAFA-NONCE", nonce)

        tokenManager?.let { tm ->
            tm.getAccessToken()?.takeIf { it.isNotBlank() }?.let { builder.header("Authorization", "Bearer $it") }
            tm.getRefreshToken()?.takeIf { it.isNotBlank() }?.let { builder.header("X-SAFA-REFRESH-TOKEN", it) }
            tm.getDeviceToken().takeIf { it.isNotBlank() }?.let { builder.header("X-SAFA-DEVICE-TOKEN", it) }
            tm.getSessionToken()?.takeIf { it.isNotBlank() }?.let { builder.header("X-SAFA-SESSION-TOKEN", it) }
            tm.getFingerprintToken().takeIf { it.isNotBlank() }?.let { builder.header("X-SAFA-FINGERPRINT-TOKEN", it) }
            tm.getActiveAccountId()?.let { builder.header("X-SAFA-ACCOUNT-ID", it.toString()) }
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
            .header("X-SAFA-REFRESH-TOKEN", refreshToken)
            .header("X-SAFA-DEVICE-TOKEN", deviceToken)
            .header("X-SAFA-FINGERPRINT-TOKEN", fingerprintToken)
            .build()

        return try {
            OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS).build().newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    if (response.code == 401 || response.code == 403) tokenManager.clearAllTokens()
                    return null
                }
                val json = JSONObject(response.body?.string().orEmpty())
                val access = json.optString("access_token", json.optString("token", ""))
                if (access.isBlank()) return null
                tokenManager.saveAccessToken(access)
                json.optString("refresh_token", refreshToken).takeIf { it.isNotBlank() }?.let(tokenManager::saveRefreshToken)
                json.optString("session_token", tokenManager.getSessionToken().orEmpty()).takeIf { it.isNotBlank() }?.let(tokenManager::saveSessionToken)
                access
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun generateHmac(payload: String, secret: String): String {
        if (secret.isEmpty()) return ""
        return try {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
            mac.doFinal(payload.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            ""
        }
    }
}
