package com.safa.account.data.network

import android.util.Log
import okhttp3.Interceptor
import okhttp3.Response

/** Preserves the original structured login error body for typed parsing upstream. */
class LoginErrorResponseInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        if (request.url.encodedPath.endsWith("/auth/login") && !response.isSuccessful) {
            // Keep HTTP status and Laravel JSON untouched. Never log request bodies or secrets.
            if (com.safa.account.BuildConfig.DEBUG) {
                Log.d("SafaLogin", "login HTTP failure: status=${response.code}")
            }
        }
        return response
    }
}
