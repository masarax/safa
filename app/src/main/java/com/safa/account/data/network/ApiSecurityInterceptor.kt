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
        val requestWithHeaders = buildSecuredRequest(originalRequest)

        val response = chain.proceed(requestWithHeaders)

        // Automatic 401 Unauthorized handling & Token Refresh Retry
        if (response.code == 401 && tokenManager != null && originalRequest.header("X-SAFA-RETRY") != "true") {
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
                        val retriedRequest = buildSecuredRequest(
                            originalRequest.newBuilder()
                                .header("X-SAFA-RETRY", "true")
                                .build()
                        )
                        return chain.proceed(retriedRequest)
                    }
                }
            }
        }

        return response
    }

    private fun buildSecuredRequest(request: Request): Request {
        val timestamp = (System.currentTimeMillis() / 1000).toString()
        val nonce = UUID.randomUUID().toString()
        val method = request.method
        val path = request.url.encodedPath

        var bodyString = ""
        request.body?.let {
            val buffer = Buffer()
            it.writeTo(buffer)
            bodyString = buffer.readUtf8()
        }

        val payload = method + path + timestamp + nonce + bodyString
        val signature = generateHmac(payload, apiSecret)

        val builder = request.newBuilder()
            .header("X-SAFA-API-KEY", apiKey)
            .header("X-SAFA-SIGNATURE", signature)
            .header("X-SAFA-TIMESTAMP", timestamp)
            .header("X-SAFA-NONCE", nonce)

        tokenManager?.let { tm ->
            tm.getAccessToken()?.takeIf { it.isNotBlank() }?.let { accessToken ->
                builder.header("Authorization", "Bearer $accessToken")
            }
            tm.getRefreshToken()?.takeIf { it.isNotBlank() }?.let { refreshToken ->
                builder.header("X-SAFA-REFRESH-TOKEN", refreshToken)
            }
            tm.getDeviceToken().takeIf { it.isNotBlank() }?.let { deviceToken ->
                builder.header("X-SAFA-DEVICE-TOKEN", deviceToken)
            }
            tm.getSessionToken()?.takeIf { it.isNotBlank() }?.let { sessionToken ->
                builder.header("X-SAFA-SESSION-TOKEN", sessionToken)
            }
            tm.getFingerprintToken().takeIf { it.isNotBlank() }?.let { fingerprintToken ->
                builder.header("X-SAFA-FINGERPRINT-TOKEN", fingerprintToken)
            }
        }

        return builder.build()
    }

    private fun performTokenRefresh(refreshToken: String): String? {
        val baseUrl = tokenManager?.getBaseUrl() ?: return null
        val refreshUrl = if (baseUrl.endsWith("/")) "${baseUrl}auth/refresh" else "$baseUrl/auth/refresh"

        val deviceToken = tokenManager.getDeviceToken()
        val fingerprintToken = tokenManager.getFingerprintToken()

        val refreshRequestBody = FormBody.Builder()
            .add("refresh_token", refreshToken)
            .add("device_token", deviceToken)
            .build()

        val refreshRequest = Request.Builder()
            .url(refreshUrl)
            .post(refreshRequestBody)
            .header("X-SAFA-API-KEY", apiKey)
            .header("X-SAFA-REFRESH-TOKEN", refreshToken)
            .header("X-SAFA-DEVICE-TOKEN", deviceToken)
            .header("X-SAFA-FINGERPRINT-TOKEN", fingerprintToken)
            .build()

        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()

        return try {
            val refreshResponse = client.newCall(refreshRequest).execute()
            if (refreshResponse.isSuccessful) {
                val responseBody = refreshResponse.body?.string()
                refreshResponse.close()
                if (!responseBody.isNullOrBlank()) {
                    val json = JSONObject(responseBody)
                    val newAccessToken = json.optString("access_token", json.optString("token", ""))
                    val newRefreshToken = json.optString("refresh_token", refreshToken)
                    val newSessionToken = json.optString("session_token", tokenManager.getSessionToken() ?: "")

                    if (newAccessToken.isNotBlank()) {
                        tokenManager.saveAccessToken(newAccessToken)
                        if (newRefreshToken.isNotBlank()) tokenManager.saveRefreshToken(newRefreshToken)
                        if (newSessionToken.isNotBlank()) tokenManager.saveSessionToken(newSessionToken)
                        return newAccessToken
                    }
                }
            } else {
                refreshResponse.close()
                if (refreshResponse.code == 401 || refreshResponse.code == 403) {
                    tokenManager.clearAllTokens()
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun generateHmac(payload: String, secret: String): String {
        if (secret.isEmpty()) return ""
        return try {
            val mac = Mac.getInstance("HmacSHA256")
            val secretKeySpec = SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256")
            mac.init(secretKeySpec)
            val hmacBytes = mac.doFinal(payload.toByteArray(Charsets.UTF_8))
            hmacBytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            ""
        }
    }
}
