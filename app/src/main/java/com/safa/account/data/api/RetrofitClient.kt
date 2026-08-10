package com.safa.account.data.api

import com.safa.account.data.network.ApiSecurityInterceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

object RetrofitClient {

    @Volatile private var instance: Retrofit? = null
    @Volatile private var instanceConfig: String? = null

    fun getInstance(
        baseUrl: String,
        apiKey: String,
        apiSecret: String,
        tokenManager: TokenManager? = null
    ): Retrofit {
        val normalizedUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        val configKey = "$normalizedUrl\u0000$apiKey\u0000$apiSecret"
        val current = instance
        if (current != null && instanceConfig == configKey) {
            return current
        }
        return synchronized(this) {
            val currentInSync = instance
            if (currentInSync != null && instanceConfig == configKey) {
                currentInSync
            } else {
                val logging = HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BODY
                }
                val security = ApiSecurityInterceptor(apiKey, apiSecret, tokenManager)
                val client = OkHttpClient.Builder()
                    .addInterceptor(security)
                    .addInterceptor(logging)
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .build()

                Retrofit.Builder()
                    .baseUrl(normalizedUrl)
                    .client(client)
                    .addConverterFactory(MoshiConverterFactory.create())
                    .build()
                    .also {
                        instance = it
                        instanceConfig = configKey
                    }
            }
        }
    }

    fun getApiService(
        baseUrl: String,
        apiKey: String,
        apiSecret: String,
        tokenManager: TokenManager? = null
    ): ApiService {
        return getInstance(baseUrl, apiKey, apiSecret, tokenManager).create(ApiService::class.java)
    }

    fun clearCache() {
        instance = null
        instanceConfig = null
    }
}
