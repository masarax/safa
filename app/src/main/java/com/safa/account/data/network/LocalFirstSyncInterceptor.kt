package com.safa.account.data.network

import com.safa.account.data.local.LocalFirstStore
import com.safa.account.data.sync.SyncSnapshotGuard
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID

/** Bridges the repository/outbox contract to the server reconciliation protocol. */
class LocalFirstSyncInterceptor(context: android.content.Context) : Interceptor {
    private val store = LocalFirstStore(context.applicationContext)
    private val entities = listOf("customers", "suppliers", "wallet_ledgers", "supplier_deposits", "wallet_batches", "transactions", "expenses_incomes")
    private val timestampFields = setOf("timestamp", "created_at", "updated_at", "deleted_at", "date")

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val path = original.url.encodedPath
        if (!path.endsWith("/sync/up")) {
            val response = chain.proceed(original)
            return if (path.endsWith("/sync/down")) captureAndFilterServerSnapshot(response) else response
        }
        val preparedJson = prepareUpload(readRequestBody(original))
        val preparedRequest = original.newBuilder().method(original.method, preparedJson.toRequestBody("application/json; charset=utf-8".toMediaType())).build()
        return reconcileUploadResponse(chain.proceed(preparedRequest), preparedJson)
    }

    private fun readRequestBody(request: okhttp3.Request): String {
        val body = request.body ?: return "{}"
        val buffer = Buffer(); body.writeTo(buffer); return buffer.readUtf8()
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
                if (sync.optString("mutation_id").isBlank()) sync.put("mutation_id", stableMutationId(entity, localId, operation, row))
                sync.put("base_version", baseVersion); sync.put("operation", operation)
                row.put("_sync", sync); row.put("server_id", serverId); row.put("sync_version", baseVersion); rows.put(i, row)
            }
        }
        return root.toString()
    }

    private fun reconcileUploadResponse(response: Response, preparedJson: String): Response {
        val responseBody = response.body ?: return response
        val bodyText = responseBody.string()
        val root = runCatching { JSONObject(bodyText) }.getOrElse { return response.newBuilder().body(bodyText.toResponseBody(responseBody.contentType())).build() }
        val requestRoot = runCatching { JSONObject(preparedJson) }.getOrElse { JSONObject() }
        captureAccepted(root)
        val conflicts = root.optJSONArray("conflicts")
        if (conflicts != null && conflicts.length() > 0) {
            val rejected = root.optJSONArray("rejected") ?: JSONArray().also { root.put("rejected", it) }
            val remainingConflicts = JSONArray()
            for (i in 0 until conflicts.length()) {
                val conflict = conflicts.optJSONObject(i) ?: continue
                val entity = conflict.optString("entity")
                val localId = conflict.optInt("local_id", 0)
                val serverId = conflict.optInt("server_id", 0)
                val serverVersion = conflict.optInt("server_version", 0)
                val requestRow = requestRoot.optJSONArray(entity)?.let { rows -> (0 until rows.length()).asSequence().mapNotNull { rows.optJSONObject(it) }.firstOrNull { it.optInt("local_id", 0) == localId } }
                if (entity.isBlank() || localId <= 0 || serverId <= 0 || serverVersion <= 0 || requestRow == null) {
                    remainingConflicts.put(conflict); continue
                }
                val operation = operationFor(requestRow, serverId)
                val rebasedStored = store.rebaseLatestProcessingOutbox(entity, localId, serverId, serverVersion, conflict.optString("server_snapshot").takeIf { it.isNotBlank() })
                if (rebasedStored) {
                    rejected.put(JSONObject().apply { put("entity", entity); put("local_id", localId); put("reason", "REBASED_CONFLICT: local mutation rebased on server version $serverVersion"); put("code", "CONFLICT_REBASED") })
                } else {
                    remainingConflicts.put(conflict)
                }
            }
            root.put("conflicts", remainingConflicts)
            if (remainingConflicts.length() == 0) root.put("status", "success")
        }
        return response.newBuilder().body(root.toString().toResponseBody("application/json; charset=utf-8".toMediaType())).build()
    }

    private fun captureAccepted(root: JSONObject) {
        val accepted = root.optJSONObject("accepted") ?: return
        entities.forEach { entity ->
            val rows = accepted.optJSONArray(entity) ?: return@forEach
            for (i in 0 until rows.length()) {
                val row = rows.optJSONObject(i) ?: continue
                val localId = row.optInt("local_id", 0); val serverId = row.optInt("server_id", 0); val version = row.optInt("sync_version", 0)
                if (localId > 0 && serverId > 0 && version >= 0) store.markSynced(entity, localId, serverId, version)
            }
        }
    }

    /** Filters delayed sync-down rows before they can reach AppRepository. */
    private fun captureAndFilterServerSnapshot(response: Response): Response {
        val responseBody = response.body ?: return response
        val bodyText = responseBody.string()
        val root = runCatching { JSONObject(bodyText) }.getOrElse { return response.newBuilder().body(bodyText.toResponseBody(responseBody.contentType())).build() }
        entities.forEach { entity ->
            val rows = root.optJSONArray(entity) ?: return@forEach
            val filtered = JSONArray()
            for (i in 0 until rows.length()) {
                val row = rows.optJSONObject(i) ?: continue
                normalizeTimestamps(row)
                val localId = resolveLocalId(entity, row)
                val serverId = row.optInt("id", row.optInt("server_id", 0))
                val incomingVersion = row.optInt("sync_version", 0)
                if (localId <= 0 || serverId <= 0) {
                    filtered.put(row)
                    continue
                }
                val localVersion = store.serverVersion(entity, localId).toLong()
                val pending = store.hasPending(entity, localId)
                if (SyncSnapshotGuard.decide(incomingVersion.toLong(), localVersion, pending) == SyncSnapshotGuard.Decision.APPLY) {
                    filtered.put(row)
                    store.recordServerVersion(entity, localId, serverId, incomingVersion)
                }
            }
            root.put(entity, filtered)
        }
        return response.newBuilder().body(root.toString().toResponseBody(responseBody.contentType() ?: "application/json".toMediaType())).build()
    }

    private fun resolveLocalId(entity: String, row: JSONObject): Int {
        row.optInt("local_id", 0).takeIf { it > 0 }?.let { return it }
        val serverId = row.optInt("id", row.optInt("server_id", 0))
        if (serverId <= 0) return 0
        return store.getRecordPayloads(entity).firstOrNull { it.serverId == serverId }?.localId ?: 0
    }

    private fun normalizeTimestamps(row: JSONObject) {
        val keys = row.keys()
        val values = mutableMapOf<String, Long>()
        while (keys.hasNext()) {
            val key = keys.next()
            if (key !in timestampFields) continue
            val normalized = SyncSnapshotGuard.parseTimestamp(row.opt(key)) ?: continue
            values[key] = normalized
        }
        values.forEach { (key, value) -> row.put(key, value) }
    }

    private fun operationFor(row: JSONObject, serverId: Int): String {
        row.optJSONObject("_sync")?.optString("operation")?.takeIf { it.isNotBlank() }?.let { return it.uppercase() }
        return when { row.optString("deleted_at").isNotBlank() -> "DELETE"; serverId > 0 -> "UPDATE"; else -> "CREATE" }
    }

    private fun stableMutationId(entity: String, localId: Int, operation: String, row: JSONObject): String {
        val normalized = JSONObject(row.toString()).apply { remove("_sync") }.toString()
        val digest = MessageDigest.getInstance("SHA-256").digest("$entity|$localId|$operation|$normalized".toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
