package com.safa.account.data.network

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Keeps authentication on the canonical unversioned Laravel auth surface.
 *
 * Business/sync traffic uses /api/v1, but authentication is intentionally
 * served by /api/auth. Older Android code built one Retrofit client for both,
 * which caused auth requests to inherit /api/v1 and pass through the generic
 * Laravel v1 proxy. Rewriting only the auth path preserves the versioned
 * business contract while making login/session/logout use the same stable
 * authentication service directly.
 */
class CanonicalAuthEndpointInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val canonicalPath = canonicalAuthPath(request.url.encodedPath)
        if (canonicalPath == request.url.encodedPath) return chain.proceed(request)

        val canonicalUrl = request.url.newBuilder()
            .encodedPath(canonicalPath)
            .build()

        return chain.proceed(request.newBuilder().url(canonicalUrl).build())
    }

    companion object {
        private const val VERSIONED_AUTH_SEGMENT = "/api/v1/auth/"
        private const val CANONICAL_AUTH_SEGMENT = "/api/auth/"

        internal fun canonicalAuthPath(path: String): String {
            val index = path.indexOf(VERSIONED_AUTH_SEGMENT)
            if (index < 0) return path

            return path.substring(0, index) +
                CANONICAL_AUTH_SEGMENT +
                path.substring(index + VERSIONED_AUTH_SEGMENT.length)
        }
    }
}
