package com.safa.account.utils

import java.security.MessageDigest

object HashUtils {
    fun hashPin(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(pin.toByteArray())
        return hash.joinToString("") { "%02x".format(it) }
    }

    fun verifyPin(pin: String, hashedPin: String): Boolean {
        return hashPin(pin) == hashedPin || pin == hashedPin // fallback for unhashed existing pins
    }
}
