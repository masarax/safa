package com.safa.account.data.network

import com.safa.account.data.local.LocalFirstStore
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.Buffer
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID

/**
 * Bridges the existing repository/outbox contract to the server reconciliation
 * protocol without leaking sync concerns into UI code.
 *
 * Responsibilities:
 * - add stable mutation_id + base_version before /sync/up is signed
 * - persist server revisions received from /sync/down
 * - convert stale-write conflicts into a local rebase + deferred retry
 * - keep the existing repository acknowledgement contract compatible
 */
class LocalFirstSyncInterceptor(context: android.content.Context) : Interceptor {
    private val store = LocalFirstStore(context.applicationContext)

    private val entities = listOf(
        "customers",
        "suppliers",
        "wallet_ledgers",
        "supplier_deposits",
        "wallet_batches",
        "transactions",
        "expenses_incomes"
    )

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val path = original.url.encodedPath
        if (!path.endsWith("/sync/up")) {
            val response = chain.proceed(original)
            return if (path.endsWith("/sync/down")) captureServerVersions(response) else response
        }

        val preparedJson = prepareUpload(readRequestBody(original))
        val preparedRequest = original.newBuilder()
            .method(original.method, preparedJson.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        val response = chain.proceed(preparedRequest)
        return reconcileUploadResponse(response, preparedJson)
    }

    private fun readRequestBody(request: okhttp3.Request): String {
        val body = request.body ?: return "{}"
        val buffer = Buffer()
        body.writeTo(buffer)
        return buffer.readUtf8()
    }

    private fun prepareUpload(raw: String): String {
        val root = runCatching { JSONObject(raw) }.getOrElse { JSONObject() }
        entities.forEach { entity ->
            val rows = root.optJSONArray(entity) ?: return@forEach
            for (i in 0 until rows.length()) {
                val row = rows.optJSONObject(i) ?: continue
                val localId = row.optInt("local_id", 0)
                if (localId <= 0) continue
                val serverId = row.optInt("server_id", 0)
                val operation = operationFor(row, serverId)
                val baseVersion = if (serverId > 0) store.serverVersion(entity, localId) else 0
                val sync = row.optJSONObject("_sync") ?: JSONObject()
                if (sync.optString("mutation_id").isBlank()) {
                    sync.put("mutation_id", stableMutationId(entity, localId, operation, row))
                }
                sync.put("base_version", baseVersion)
                sync.put("operation", operation)
                row.put("_sync", sync)
                row.put("server_id", serverId)
                row.put("sync_version", baseVersion)
                rows.put(i, row)
            }
        }
        return root.toString()
    }

    private fun reconcileUploadResponse(response: Response, preparedJson: String): Response {
        val bodyText = response.body?.string() ?: return response
        val root = runCatching { JSONObject(bodyText) }.getOrElse {
            return response.newBuilder().body(bodyText.toRequestBody("application/json".toMediaType())).build()
        }
        val requestRoot = runCatching { JSONObject(preparedJson) }.getOrElse { JSONObject() }

        captureAccepted(root)
        val conflicts = root.optJSONArray("conflicts")
        if (conflicts != null && conflicts.length() > 0) {
            val accepted = root.optJSONObject("accepted") ?: JSONObject().also { root.put("accepted", it) }
            val remainingConflicts = JSONArray()
            for (i in 0 until conflicts.length()) {
                val conflict = conflicts.optJSONObject(i) ?: continue
                val entity = conflict.optString("entity")
                val localId = conflict.optInt("local_id", 0)
                val serverId = conflict.optInt("server_id", 0)
                val serverVersion = conflict.optInt("server_version", 0)
                val requestRow = requestRoot.optJSONArray(entity)?.let { rows ->
                    (0 until rows.length()).asSequence().mapNotNull { rows.optJSONObject(it) }.firstOrNull { it.optInt("local_id", 0) == localId }
                }

                if (entity.isBlank() || localId <= 0 || serverVersion <= 0 || requestRow == null) {
                    remainingConflicts.put(conflict)
                    continue
                }

                val operation = operationFor(requestRow, serverId)
                val rebased = JSONObject(requestRow.toString()).apply {
                    put("server_id", serverId)
                    put("sync_version", serverVersion)
                    put("_sync", JSONObject().apply {
                        put("mutation_id", UUID.randomUUID().toString())
                        put("base_version", serverVersion)
                        put("operation", operation)
                    })
                }

                store.recordServerVersion(entity, localId, serverId, serverVersion)
                store.enqueue(entity, localId, serverId, operation, rebased.toString())

                val rows = accepted.optJSONArray(entity) ?: JSONArray().also { accepted.put(entity, it) }
                rows.put(JSONObject().apply {
                    put("local_id", localId)
                    put("server_id", serverId)
                    put("sync_version", serverVersion)
                    put("mutation_id", conflict.optString("mutation_id"))
                    put("operation", operation)
                    put("rebased", true)
                })
            }
            root.put("conflicts", remainingConflicts)
            if (remainingConflicts.length() == 0) root.put("status", "success")
        }

        return response.newBuilder()
            .body(root.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
    }

    private fun captureAccepted(root: JSONObject) {
        val accepted = root.optJSONObject("accepted") ?: return
        entities.forEach { entity ->
            val rows = accepted.optJSONArray(entity) ?: return@forEach
            for (i in 0 until rows.length()) {
                val row = rows.optJSONObject(i) ?: continue
                val localId = row.optInt("local_id", 0)
                val serverId = row.optInt("server_id", 0)
                val version = row.optInt("sync_version", 0)
                if (localId > 0 && serverId > 0 && version >= 0) {
                    store.markSynced(entity, localId, serverId, version)
                }
            }
        }
    }

    private fun captureServerVersions(response: Response): Response {
        val bodyText = response.body?.string() ?: return response
        val root = runCatching { JSONObject(bodyText) }.getOrElse {
            return response.newBuilder().body(bodyText.toRequestBody("application/json".toMediaType())).build()
        }
        entities.forEach { entity ->
            val rows = root.optJSONArray(entity) ?: return@forEach
            for (i in 0 until rows.length()) {
                val row = rows.optJSONObject(i) ?: continue
                val localId = row.optInt("local_id", 0)
                val serverId = row.optInt("id", row.optInt("server_id", 0))
                val version = row.optInt("sync_version", 0)
                if (localId > 0 && serverId > 0) store.recordServerVersion(entity, localId, serverId, version)
            }
        }
        return response.newBuilder()
            .body(root.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
    }

    private fun operationFor(row: JSONObject, serverId: Int): String {
        row.optJSONObject("_sync")?.optString("operation")?.takeIf { it.isNotBlank() }?.let { return it.uppercase() }
        return when {
            !row.optString("deleted_at").isNullOrBlank() -> "DELETE"
            serverId > 0 -> "UPDATE"
            else -> "CREATE"
        }
    }

    private fun stableMutationId(entity: String, localId: Int, operation: String, row: JSONObject): String {
        val normalized = JSONObject(row.toString()).apply { remove("_sync") }.toString()
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$entity|$localId|$operation|$normalized".toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
