package com.safa.account.utils

import android.util.Log

object SafaLogger {
    private const val TAG = "SAFA_DIAGNOSTIC"

    fun log(tag: String, message: String) {
        Log.i(TAG, "[$tag] $message")
    }

    fun error(tag: String, message: String, throwable: Throwable? = null) {
        Log.e(TAG, "[$tag] $message", throwable)
    }

    fun warn(tag: String, message: String) {
        Log.w(TAG, "[$tag] $message")
    }
}
