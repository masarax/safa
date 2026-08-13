package com.safa.account.data.sync

import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

object SyncSnapshotGuard {
    enum class Decision { APPLY, IGNORE }

    fun decide(incomingVersion: Long, localVersion: Long, hasPendingMutation: Boolean): Decision {
        if (hasPendingMutation) return Decision.IGNORE
        return if (incomingVersion < localVersion) Decision.IGNORE else Decision.APPLY
    }

    fun parseTimestamp(value: Any?, nowMillis: Long = System.currentTimeMillis()): Long? {
        if (value == null) return null
        if (value is Number) return normalizeEpoch(value.toLong(), nowMillis)
        val raw = value.toString().trim()
        if (raw.isEmpty()) return null
        raw.toLongOrNull()?.let { return normalizeEpoch(it, nowMillis) }
        return try {
            Instant.parse(raw).toEpochMilli()
        } catch (_: DateTimeParseException) {
            try {
                OffsetDateTime.parse(raw, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant().toEpochMilli()
            } catch (_: DateTimeParseException) {
                try {
                    LocalDateTime.parse(raw, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                        .atOffset(ZoneOffset.UTC).toInstant().toEpochMilli()
                } catch (_: DateTimeParseException) {
                    null
                }
            }
        }
    }

    private fun normalizeEpoch(value: Long, nowMillis: Long): Long? {
        if (value <= 0L) return null
        val millis = if (value < 2_000_000_000L) value * 1000L else value
        return millis.takeIf { it <= nowMillis + 24L * 60L * 60L * 1000L }
    }
}
