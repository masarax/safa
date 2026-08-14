package com.safa.account.data.sync

import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

object SyncSnapshotGuard {
    enum class Decision { APPLY, IGNORE }

    private val laravelSqlTimestamp = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    fun decide(incomingVersion: Long, localVersion: Long, hasPendingMutation: Boolean): Decision {
        if (hasPendingMutation) return Decision.IGNORE
        return if (incomingVersion < localVersion) Decision.IGNORE else Decision.APPLY
    }

    /**
     * Canonical server-time policy: all zone-less Laravel timestamps are UTC.
     * Epoch seconds and milliseconds are normalized to milliseconds. Invalid or
     * implausible future values are rejected rather than replaced with local now.
     */
    fun parseTimestamp(value: Any?, nowMillis: Long = System.currentTimeMillis()): Long? {
        if (value == null) return null
        if (value is Number) return normalizeEpoch(value.toLong(), nowMillis)
        val raw = value.toString().trim()
        if (raw.isEmpty()) return null
        raw.toLongOrNull()?.let { return normalizeEpoch(it, nowMillis) }

        val parsed = parseInstant(raw) ?: return null
        return normalizeEpoch(parsed.toEpochMilli(), nowMillis)
    }

    private fun parseInstant(raw: String): Instant? {
        try {
            return Instant.parse(raw)
        } catch (_: DateTimeParseException) {
            // Continue through supported Laravel/API variants.
        }
        try {
            return OffsetDateTime.parse(raw, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant()
        } catch (_: DateTimeParseException) {
            // Continue.
        }
        try {
            return LocalDateTime.parse(raw, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                .atOffset(ZoneOffset.UTC).toInstant()
        } catch (_: DateTimeParseException) {
            // Continue.
        }
        return try {
            LocalDateTime.parse(raw, laravelSqlTimestamp)
                .atOffset(ZoneOffset.UTC).toInstant()
        } catch (_: DateTimeParseException) {
            null
        }
    }

    private fun normalizeEpoch(value: Long, nowMillis: Long): Long? {
        if (value <= 0L) return null
        val millis = if (value < 2_000_000_000L) value * 1000L else value
        val allowance = 86_400_000L
        val upperBound = if (nowMillis >= Long.MAX_VALUE - allowance) Long.MAX_VALUE else nowMillis + allowance
        return millis.takeIf { it <= upperBound }
    }
}
