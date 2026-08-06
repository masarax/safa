package com.safa.account.data.api

import com.safa.account.data.network.ApiSecurityInterceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    @Volatile private var instance: Retrofit? = null

    fun getInstance(baseUrl: String, apiKey: String, apiSecret: String): Retrofit {
        return instance ?: synchronized(this) {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }
            val security = ApiSecurityInterceptor(apiKey, apiSecret)
            val client = OkHttpClient.Builder()
                .addInterceptor(security)
                .addInterceptor(logging)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()

            Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(MoshiConverterFactory.create())
                .build()
                .also { instance = it }
        }
    }

    fun getApiService(baseUrl: String, apiKey: String, apiSecret: String): ApiService {
        return getInstance(baseUrl, apiKey, apiSecret).create(ApiService::class.java)
    }

    fun clearCache() {
        instance = null
    }
}
