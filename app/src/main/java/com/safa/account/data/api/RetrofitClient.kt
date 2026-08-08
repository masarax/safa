package com.safa.account.data.api

import com.safa.account.data.network.ApiSecurityInterceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    @Volatile private var instance: Retrofit? = null

    fun getInstance(
        baseUrl: String,
        apiKey: String,
        apiSecret: String,
        tokenManager: TokenManager? = null
    ): Retrofit {
        val normalizedUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        val current = instance
        if (current != null && current.baseUrl().toString() == normalizedUrl) {
            return current
        }
        return synchronized(this) {
            val currentInSync = instance
            if (currentInSync != null && currentInSync.baseUrl().toString() == normalizedUrl) {
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
                    .also { instance = it }
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
    }
}
