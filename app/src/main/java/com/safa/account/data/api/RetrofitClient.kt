package com.safa.account.data.api

import com.safa.account.BuildConfig
import com.safa.account.data.network.ApiSecurityInterceptor
import com.safa.account.data.network.LocalFirstSyncInterceptor
import com.safa.account.data.network.LoginErrorResponseInterceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.net.URI
import java.util.concurrent.TimeUnit

object RetrofitClient {

    @Volatile private var instance: Retrofit? = null
    @Volatile private var instanceConfig: String? = null

    @Volatile private var healthInstance: Retrofit? = null
    @Volatile private var healthInstanceConfig: String? = null

    fun getInstance(
        baseUrl: String,
        apiKey: String,
        apiSecret: String,
        tokenManager: TokenManager? = null
    ): Retrofit {
        val normalizedUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        val configKey = "$normalizedUrl\u0000$apiKey\u0000$apiSecret"
        val current = instance
        if (current != null && instanceConfig == configKey) return current

        return synchronized(this) {
            val currentInSync = instance
            if (currentInSync != null && instanceConfig == configKey) {
                currentInSync
            } else {
                val logging = HttpLoggingInterceptor().apply {
                    level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
                    redactHeader("Authorization")
                    redactHeader("X-SAFA-API-KEY")
                    redactHeader("X-SAFA-SIGNATURE")
                    redactHeader("X-SAFA-REFRESH-TOKEN")
                    redactHeader("X-SAFA-DEVICE-TOKEN")
                    redactHeader("X-SAFA-SESSION-TOKEN")
                    redactHeader("X-SAFA-FINGERPRINT-TOKEN")
                }
                val security = ApiSecurityInterceptor(apiKey, apiSecret, tokenManager)
                val clientBuilder = OkHttpClient.Builder()
                tokenManager?.getContext()?.let { context ->
                    clientBuilder.addInterceptor(LocalFirstSyncInterceptor(context))
                }
                val client = clientBuilder
                    .addInterceptor(security)
                    .addInterceptor(LoginErrorResponseInterceptor())
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

    /**
     * Builds a deliberately minimal Retrofit client for the unauthenticated
     * health endpoint. The supplied URL may be either the API base
     * (https://host/api/) or the host root (https://host/).
     */
    fun getHealthApiService(baseUrl: String): ApiService {
        val healthBaseUrl = healthBaseUrl(baseUrl)
        val current = healthInstance
        if (current != null && healthInstanceConfig == healthBaseUrl) return current.create(ApiService::class.java)

        return synchronized(this) {
            val currentInSync = healthInstance
            if (currentInSync != null && healthInstanceConfig == healthBaseUrl) {
                currentInSync.create(ApiService::class.java)
            } else {
                val client = OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS)
                    .build()

                Retrofit.Builder()
                    .baseUrl(healthBaseUrl)
                    .client(client)
                    .addConverterFactory(MoshiConverterFactory.create())
                    .build()
                    .also {
                        healthInstance = it
                        healthInstanceConfig = healthBaseUrl
                    }
                    .create(ApiService::class.java)
            }
        }
    }

    private fun healthBaseUrl(baseUrl: String): String {
        val normalized = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        return try {
            val uri = URI(normalized)
            val scheme = uri.scheme ?: "https"
            val authority = uri.rawAuthority ?: throw IllegalArgumentException("Invalid base URL")
            val path = uri.rawPath.orEmpty().trimEnd('/')
            val apiPath = if (path.endsWith("/api")) path else "$path/api"
            "$scheme://$authority${if (apiPath == "/api") "/api/" else "$apiPath/"}"
        } catch (_: Exception) {
            "https://safa.masarax.com/api/"
        }
    }

    fun getApiService(
        baseUrl: String,
        apiKey: String,
        apiSecret: String,
        tokenManager: TokenManager? = null
    ): ApiService = getInstance(baseUrl, apiKey, apiSecret, tokenManager).create(ApiService::class.java)

    fun clearCache() {
        instance = null
        instanceConfig = null
        healthInstance = null
        healthInstanceConfig = null
    }
}
