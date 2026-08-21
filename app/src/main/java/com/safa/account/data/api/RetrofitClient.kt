package com.safa.account.data.api

import com.safa.account.BuildConfig
import com.safa.account.data.money.MoneyPayloadNormalizer
import com.safa.account.data.network.ApiSecurityInterceptor
import com.safa.account.data.network.CanonicalAuthEndpointInterceptor
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

    internal fun effectiveApiKey(apiKey: String): String =
        apiKey.trim().ifBlank { BuildConfig.SAFA_API_KEY.trim() }

    fun getInstance(baseUrl: String, apiKey: String, apiSecret: String, tokenManager: TokenManager? = null): Retrofit {
        val normalizedUrl = versionedApiBaseUrl(baseUrl)
        val clientApiKey = effectiveApiKey(apiKey)
        require(clientApiKey.isNotBlank()) { "SAFA mobile API client identifier is not configured." }
        val configKey = "$normalizedUrl\u0000$clientApiKey\u0000$apiSecret"
        val current = instance
        if (current != null && instanceConfig == configKey) return current
        return synchronized(this) {
            val currentInSync = instance
            if (currentInSync != null && instanceConfig == configKey) currentInSync else {
                val logging = HttpLoggingInterceptor().apply {
                    level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
                    redactHeader("Authorization"); redactHeader("X-SAFA-API-KEY"); redactHeader("X-SAFA-SIGNATURE"); redactHeader("X-SAFA-REFRESH-TOKEN"); redactHeader("X-SAFA-DEVICE-TOKEN"); redactHeader("X-SAFA-SESSION-TOKEN"); redactHeader("X-SAFA-FINGERPRINT-TOKEN")
                }
                val clientBuilder = OkHttpClient.Builder()
                tokenManager?.getContext()?.let { clientBuilder.addInterceptor(LocalFirstSyncInterceptor(it)) }
                val client = clientBuilder
                    .addInterceptor(MoneyPayloadNormalizer())
                    .addInterceptor(CanonicalAuthEndpointInterceptor())
                    .addInterceptor(ApiSecurityInterceptor(clientApiKey, apiSecret, tokenManager))
                    .addInterceptor(LoginErrorResponseInterceptor())
                    .addInterceptor(logging)
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .build()
                Retrofit.Builder().baseUrl(normalizedUrl).client(client).addConverterFactory(MoshiConverterFactory.create()).build().also { instance = it; instanceConfig = configKey }
            }
        }
    }

    fun getHealthApiService(baseUrl: String): ApiService {
        val healthBaseUrl = healthBaseUrl(baseUrl)
        val current = healthInstance
        if (current != null && healthInstanceConfig == healthBaseUrl) return current.create(ApiService::class.java)
        return synchronized(this) {
            val currentInSync = healthInstance
            if (currentInSync != null && healthInstanceConfig == healthBaseUrl) currentInSync.create(ApiService::class.java) else {
                val client = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS).build()
                Retrofit.Builder().baseUrl(healthBaseUrl).client(client).addConverterFactory(MoshiConverterFactory.create()).build().also { healthInstance = it; healthInstanceConfig = healthBaseUrl }.create(ApiService::class.java)
            }
        }
    }

    private fun versionedApiBaseUrl(baseUrl: String): String {
        val normalized = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        val uri = runCatching { URI(normalized) }.getOrNull() ?: return normalized
        val path = uri.rawPath.orEmpty().trimEnd('/')
        if (path.endsWith("/v1")) return normalized
        return if (path.endsWith("/api")) normalized.removeSuffix("/") + "/v1/" else normalized.removeSuffix("/") + "/api/v1/"
    }

    internal fun healthBaseUrl(baseUrl: String): String {
        val normalized = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        val uri = try {
            URI(normalized)
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid API base URL.", e)
        }

        val scheme = uri.scheme?.lowercase() ?: throw IllegalArgumentException("API base URL must include a scheme.")
        val authority = uri.rawAuthority?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("API base URL must include a host.")
        if (uri.userInfo != null) throw IllegalArgumentException("API base URL must not contain user credentials.")
        if (scheme != "https" && !(BuildConfig.DEBUG && scheme == "http")) throw IllegalArgumentException("HTTPS is required for the API base URL.")
        if (scheme != "https" && scheme != "http") throw IllegalArgumentException("Unsupported API URL scheme.")

        val path = uri.rawPath.orEmpty().trimEnd('/')
        val apiPath = when {
            path.endsWith("/api/v1") -> path.removeSuffix("/v1")
            path.endsWith("/api") -> path
            path.isBlank() || path == "/" -> "/api"
            else -> "$path/api"
        }
        return "$scheme://$authority${apiPath.trimEnd('/')}/"
    }

    fun getApiService(baseUrl: String, apiKey: String, apiSecret: String, tokenManager: TokenManager? = null): ApiService = getInstance(baseUrl, apiKey, apiSecret, tokenManager).create(ApiService::class.java)
    fun clearCache() { instance = null; instanceConfig = null; healthInstance = null; healthInstanceConfig = null }
}
