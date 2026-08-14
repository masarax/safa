package com.safa.account.utils

import android.util.Log

/** Diagnostics must never alter application control flow or expose Throwable messages. */
object SafaLogger {
    private const val TAG = "SAFA_DIAGNOSTIC"
    private const val MAX_STACK_FRAMES = 8

    fun log(tag: String, message: String) {
        runCatching { Log.i(TAG, [$tag] $message") }
    }

    fun error(tag: String, message: String, throwable: Throwable? = null) {
        val diagnostic = throwable?.let { " | ${safeThrowableSummary(it)}" }.orEmpty()
        runCatching { Log.e(TAG, "[$tag] $message$diagnostic") }
    }

    /**
     * Produce useful crash correlation data without calling Throwable.message,
     * toString(), printStackTrace(), or Log overloads that render raw exception
     * messages. Class/method/line metadata is enough to identify the code path.
     */
    internal fun safeThrowableSummary(throwable: Throwable): String {
        val type = throwable.javaClass.name
        val frames = throwable.stackTrace
            .take(MAX_STACK_FRAMES)
            .joinToString(separator = " <- ") { frame ->
                "${frame.className}.${frame.methodName}:${frame.lineNumber}"
            }
        val causeTypes = generateSequence(throwable.cause) { it.cause }
            .take(4)
            .map { it.javaClass.name }
            .toList()
            .joinToString(separator = " <- ")

        return buildString {
            append("exception=")
            append(type)
            if (frames.isNotBlank()) {
                append(" stack=")
                append(frames)
            }
            if (causeTypes.isNotBlank()) {
                append(" causes=")
                append(causeTypes)
            }
        }
    }

    fun warn(tag: String, message: String) {
        runCatching { Log.w(TAG, [$tag] $message") }
    }
}
