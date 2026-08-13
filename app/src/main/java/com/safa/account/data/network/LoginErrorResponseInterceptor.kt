package com.safa.account.data.network

import android.util.Log
import com.safa.account.BuildConfig
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.json.JSONObject

/**
 * Keeps login HTTP status codes intact while converting Laravel failures into a
 * small, safe JSON envelope consumed by the existing login UI.
 *
 * No request body, PIN, token, device secret or backend stack trace is logged.
 */
class LoginErrorResponseInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val isLoginRequest = request.url.encodedPath.endsWith("/auth/login")

        if (!isLoginRequest) return chain.proceed(request)

        val response = try {
            chain.proceed(request)
        } catch (t: Throwable) {
            val networkError = ApiLoginErrorParser.fromThrowable(t)
            if (BuildConfig.DEBUG) {
                Log.d("SafaLogin", "login request failed before HTTP response: code=${networkError.code}")
            }
            throw LoginNetworkException(networkError)
        }

        val rawBody = runCatching { response.peekBody(MAX_PEEK_BYTES).string() }.getOrDefault("")

        if (response.isSuccessful) {
            val validTokenEnvelope = runCatching {
                val root = JSONObject(rawBody)
                val tokens = root.optJSONObject("tokens")
                !tokens?.optString("access_token").isNullOrBlank()
            }.getOrDefault(false)

            if (!validTokenEnvelope) {
                val body = JSONObject()
                    .put("code", "UNEXPECTED_RESPONSE")
                    .put("message", "The server returned an unexpected login response.")
                    .put("http_status", response.code)
                    .toString()
                return response.newBuilder()
                    .code(502)
                    .message("Unexpected login response")
                    .body(body.toResponseBody(JSON_MEDIA_TYPE))
                    .build()
            }

            return response
        }

        val classified = ApiLoginErrorParser.fromHttp(
            status = response.code,
            rawBody = rawBody,
            retryAfter = response.header("Retry-After")
        )

        val body = JSONObject()
            .put("code", classified.code)
            .put("message", classified.message)
            .put("http_status", response.code)
            .apply {
                if (classified is ApiLoginError.Throttled && classified.retryAfterSeconds != null) {
                    put("retry_after_seconds", classified.retryAfterSeconds)
                }
            }
            .toString()

        if (BuildConfig.DEBUG) {
            Log.d("SafaLogin", "login HTTP failure classified: status=${response.code}, code=${classified.code}")
        }

        return response.newBuilder()
            .body(body.toResponseBody(JSON_MEDIA_TYPE))
            .build()
    }

    companion object {
        private const val MAX_PEEK_BYTES = 256L * 1024L
        private val JSON_MEDIA_TYPE = "application/json".toMediaTypeOrNull()
    }
}
