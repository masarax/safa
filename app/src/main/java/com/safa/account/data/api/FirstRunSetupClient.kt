package com.safa.account.data.api

import org.json.JSONObject
import java.net.URI

/**
 * Public first-run discovery for the Android login surface.
 *
 * The server health endpoint already returns HTTP 503 with
 * {status:"setup_required", phase:"database|admin"} while setup is pending.
 * This helper intentionally consumes only that public state and never receives
 * the deployment-owned setup code, database credentials, or browser claim.
 */
object FirstRunSetupClient {
    fun phaseFromHealthResponse(httpCode: Int, errorBody: String?): String? {
        if (httpCode != 503 || errorBody.isNullOrBlank()) return null
        val json = runCatching { JSONObject(errorBody) }.getOrNull() ?: return null
        if (json.optString("status") != "setup_required") return null
        return json.optString("phase").takeIf { it == "database" || it == "admin" }
    }

    fun webSetupUrl(apiBaseUrl: String): String {
        val normalized = if (apiBaseUrl.endsWith('/')) apiBaseUrl else "$apiBaseUrl/"
        val uri = URI(normalized)
        val scheme = uri.scheme ?: throw IllegalArgumentException("API base URL must include a scheme.")
        val authority = uri.rawAuthority?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("API base URL must include a host.")
        val path = uri.rawPath.orEmpty().trimEnd('/')
        val webPath = when {
            path.endsWith("/api/v1") -> path.removeSuffix("/api/v1")
            path.endsWith("/api") -> path.removeSuffix("/api")
            else -> path
        }.trimEnd('/')

        return "$scheme://$authority${if (webPath.isBlank()) "" else webPath}/setup"
    }
}
