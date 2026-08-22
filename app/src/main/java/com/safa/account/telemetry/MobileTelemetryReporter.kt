package com.safa.account.telemetry

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.safa.account.BuildConfig
import com.safa.account.data.api.RetrofitClient
import com.safa.account.data.api.TokenManager
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Privacy-safe fleet telemetry.
 *
 * The queue stores only enumerated event metadata, release identity and a hash
 * of class/method/line stack coordinates. Throwable messages, tokens, account
 * identifiers, mobile numbers and business payloads are never persisted.
 */
object MobileTelemetryReporter {
    private const val PREFS_NAME = "safa_mobile_telemetry_v1"
    private const val QUEUE_KEY = "queue"
    private const val MAX_QUEUE = 100
    private const val WORK_NAME = "safa-mobile-telemetry-flush"
    private val installed = AtomicBoolean(false)
    @Volatile private var appContext: Context? = null

    fun install(context: Context) {
        appContext = context.applicationContext
        if (!installed.compareAndSet(false, true)) return
        installCrashHandler()
        startAnrWatchdog()
        scheduleFlush()
    }

    fun recordNonfatal(reason: String, throwable: Throwable?) {
        enqueue(
            eventType = "nonfatal",
            reason = safeReason(reason),
            stackFingerprint = throwable?.let(::stackFingerprint),
        )
    }

    fun recordSync(
        success: Boolean,
        durationMs: Long,
        reason: String = if (success) "completed" else "failed",
        bytes: Long = 0,
        pendingCount: Int = 0,
        oldestPendingSeconds: Long = 0,
    ) {
        enqueue(
            eventType = if (success) "sync_success" else "sync_failure",
            reason = safeReason(reason),
            durationMs = durationMs,
            bytes = bytes,
            pendingCount = pendingCount,
            oldestPendingSeconds = oldestPendingSeconds,
        )
    }

    fun recordRetry(reason: String) = enqueue("sync_retry", reason = safeReason(reason))
    fun recordAuthRefreshFailure(reason: String) = enqueue("auth_refresh_failure", reason = safeReason(reason))

    internal fun enqueue(
        eventType: String,
        reason: String = "none",
        stackFingerprint: String? = null,
        durationMs: Long = 0,
        bytes: Long = 0,
        pendingCount: Int = 0,
        oldestPendingSeconds: Long = 0,
    ) {
        val context = appContext ?: return
        if (eventType !in ALLOWED_EVENTS) return
        runCatching {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            synchronized(this) {
                val existing = JSONArray(prefs.getString(QUEUE_KEY, "[]") ?: "[]")
                val next = JSONArray()
                val start = (existing.length() - (MAX_QUEUE - 1)).coerceAtLeast(0)
                for (index in start until existing.length()) next.put(existing.getJSONObject(index))
                next.put(JSONObject().apply {
                    put("id", UUID.randomUUID().toString())
                    put("event_type", eventType)
                    put("release", BuildConfig.SAFA_RELEASE_VERSION_NAME.take(48))
                    put("reason", safeReason(reason))
                    if (!stackFingerprint.isNullOrBlank()) put("stack_fingerprint", stackFingerprint.take(64))
                    put("duration_ms", durationMs.coerceIn(0, 600_000))
                    put("bytes", bytes.coerceIn(0, 100_000_000))
                    put("pending_count", pendingCount.coerceIn(0, 1_000_000))
                    put("oldest_pending_seconds", oldestPendingSeconds.coerceIn(0, 31_536_000))
                })
                prefs.edit().putString(QUEUE_KEY, next.toString()).commit()
            }
            scheduleFlush()
        }
    }

    internal fun peek(context: Context): JSONObject? = synchronized(this) {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(QUEUE_KEY, "[]") ?: "[]"
        val array = JSONArray(raw)
        if (array.length() == 0) null else array.getJSONObject(0)
    }

    internal fun acknowledge(context: Context, id: String) = synchronized(this) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val array = JSONArray(prefs.getString(QUEUE_KEY, "[]") ?: "[]")
        val next = JSONArray()
        for (index in 0 until array.length()) {
            val item = array.getJSONObject(index)
            if (item.optString("id") != id) next.put(item)
        }
        prefs.edit().putString(QUEUE_KEY, next.toString()).commit()
    }

    internal fun payload(item: JSONObject): Map<String, Any> = buildMap {
        put("event_type", item.getString("event_type"))
        put("release", item.getString("release"))
        put("reason", item.optString("reason", "none"))
        item.optString("stack_fingerprint").takeIf { it.isNotBlank() }?.let { put("stack_fingerprint", it) }
        put("duration_ms", item.optLong("duration_ms", 0).coerceIn(0, 600_000))
        put("bytes", item.optLong("bytes", 0).coerceIn(0, 100_000_000))
        put("pending_count", item.optInt("pending_count", 0).coerceIn(0, 1_000_000))
        put("oldest_pending_seconds", item.optLong("oldest_pending_seconds", 0).coerceIn(0, 31_536_000))
    }

    private fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            enqueue("crash", reason = "uncaught", stackFingerprint = stackFingerprint(throwable))
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun startAnrWatchdog() {
        val mainHandler = Handler(Looper.getMainLooper())
        val heartbeat = java.util.concurrent.atomic.AtomicLong(System.nanoTime())
        val reported = AtomicBoolean(false)
        Thread({
            while (!Thread.currentThread().isInterrupted) {
                mainHandler.post { heartbeat.set(System.nanoTime()); reported.set(false) }
                try {
                    Thread.sleep(5_000)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                }
                val blockedMs = (System.nanoTime() - heartbeat.get()) / 1_000_000
                if (blockedMs >= 10_000 && reported.compareAndSet(false, true)) {
                    enqueue("anr", reason = "main_thread_blocked", durationMs = blockedMs)
                }
            }
        }, "safa-anr-watchdog").apply { isDaemon = true; start() }
    }

    private fun scheduleFlush() {
        val context = appContext ?: return
        runCatching {
            val request = OneTimeWorkRequestBuilder<TelemetryFlushWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
        }
    }

    private fun stackFingerprint(throwable: Throwable): String {
        val material = buildString {
            append(throwable.javaClass.name)
            throwable.stackTrace.take(8).forEach { frame ->
                append('|').append(frame.className).append('.').append(frame.methodName).append(':').append(frame.lineNumber)
            }
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(material.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .take(32)
    }

    private fun safeReason(value: String): String = value.trim()
        .replace(Regex("[^A-Za-z0-9_.:-]"), "_")
        .take(64)
        .ifBlank { "none" }

    private val ALLOWED_EVENTS = setOf("crash", "anr", "nonfatal", "sync_success", "sync_failure", "sync_retry", "auth_refresh_failure")
}

private interface MobileTelemetryApi {
    @POST("telemetry/mobile")
    suspend fun send(@Body payload: Map<String, @JvmSuppressWildcards Any>): Response<Map<String, String>>
}

class TelemetryFlushWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        MobileTelemetryReporter.install(applicationContext)
        val item = MobileTelemetryReporter.peek(applicationContext) ?: return Result.success()
        return try {
            val tokenManager = TokenManager(applicationContext)
            val api = RetrofitClient.getInstance(
                tokenManager.getBaseUrl(),
                tokenManager.getApiKey(),
                tokenManager.getApiSecret(),
                tokenManager,
            ).create(MobileTelemetryApi::class.java)
            val response = api.send(MobileTelemetryReporter.payload(item))
            if (response.isSuccessful) {
                MobileTelemetryReporter.acknowledge(applicationContext, item.getString("id"))
                if (MobileTelemetryReporter.peek(applicationContext) != null) {
                    WorkManager.getInstance(applicationContext).enqueue(
                        OneTimeWorkRequestBuilder<TelemetryFlushWorker>()
                            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                            .build()
                    )
                }
                Result.success()
            } else if (response.code() in 400..499 || runAttemptCount >= 5) {
                // Invalid/obsolete telemetry must never become a permanent work backlog.
                MobileTelemetryReporter.acknowledge(applicationContext, item.getString("id"))
                Result.success()
            } else {
                Result.retry()
            }
        } catch (_: Throwable) {
            if (runAttemptCount >= 5) Result.success() else Result.retry()
        }
    }
}
