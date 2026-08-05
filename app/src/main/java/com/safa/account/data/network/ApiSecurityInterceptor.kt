package com.safa.account.data.network

import okhttp3.Interceptor
import okhttp3.Response
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import okio.Buffer

class ApiSecurityInterceptor(
    private val apiKey: String,
    private val apiSecret: String
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val timestamp = (System.currentTimeMillis() / 1000).toString()
        val method = request.method
        val path = request.url.encodedPath
        
        var bodyString = ""
        request.body?.let {
            val buffer = Buffer()
            it.writeTo(buffer)
            bodyString = buffer.readUtf8()
        }

        val payload = method + path + timestamp + bodyString
        val signature = generateHmac(payload, apiSecret)

        val newRequest = request.newBuilder()
            .addHeader("X-SAFA-API-KEY", apiKey)
            .addHeader("X-SAFA-SIGNATURE", signature)
            .addHeader("X-SAFA-TIMESTAMP", timestamp)
            .build()

        return chain.proceed(newRequest)
    }

    private fun generateHmac(payload: String, secret: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        val secretKeySpec = SecretKeySpec(secret.toByteArray(), "HmacSHA256")
        mac.init(secretKeySpec)
        val hmacBytes = mac.doFinal(payload.toByteArray())
        return hmacBytes.joinToString("") { "%02x".format(it) }
    }
}
