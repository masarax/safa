package com.safa.account.data.api

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.json.JSONObject

internal data class TokenGeneration(
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val deviceToken: String? = null,
    val sessionToken: String? = null,
    val fingerprintToken: String? = null,
) {
    fun isEmpty(): Boolean = listOf(accessToken, refreshToken, deviceToken, sessionToken, fingerprintToken)
        .all { it.isNullOrBlank() }
}

internal class TokenVaultException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

internal interface TokenVaultCipher {
    fun encrypt(field: String, plaintext: ByteArray): String
    fun decrypt(field: String, encodedCiphertext: String): ByteArray
    fun reset()
}

/**
 * Versioned Android credential vault.
 *
 * All authentication credentials are persisted as one AEAD-protected generation,
 * so a crash can expose either the previous generation or the next generation,
 * never a mixture of access/refresh/session/device/fingerprint credentials.
 */
internal class TokenVault(
    context: Context,
    private val cipher: TokenVaultCipher = TokenVaultCipherFactory.create(),
) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun read(): TokenGeneration {
        val encoded = prefs.getString(KEY_GENERATION, null)?.takeIf { it.isNotBlank() }
            ?: return TokenGeneration()
        return try {
            decode(cipher.decrypt(KEY_GENERATION, encoded))
        } catch (error: TokenVaultException) {
            throw error
        } catch (error: Exception) {
            throw TokenVaultException("Unable to authenticate token vault generation", error)
        }
    }

    fun write(generation: TokenGeneration) {
        if (generation.isEmpty()) {
            if (!prefs.edit().remove(KEY_GENERATION).commit()) {
                throw TokenVaultException("Unable to clear token vault generation")
            }
            return
        }

        val encoded = cipher.encrypt(KEY_GENERATION, encode(generation))
        if (!prefs.edit().putString(KEY_GENERATION, encoded).commit()) {
            throw TokenVaultException("Unable to persist token vault generation")
        }
        if (read() != generation) {
            throw TokenVaultException("Token vault generation verification failed")
        }
    }

    fun update(block: (TokenGeneration) -> TokenGeneration) = write(block(read()))

    fun clear() {
        if (!prefs.edit().remove(KEY_GENERATION).commit()) {
            throw TokenVaultException("Unable to clear token vault generation")
        }
    }

    fun reset() {
        if (!prefs.edit().remove(KEY_GENERATION).commit()) {
            throw TokenVaultException("Unable to reset token vault generation")
        }
        cipher.reset()
    }

    internal fun rawCiphertextForTest(): String? = prefs.getString(KEY_GENERATION, null)

    private fun encode(generation: TokenGeneration): ByteArray = JSONObject().apply {
        put("version", VAULT_VERSION)
        putNullable("access_token", generation.accessToken)
        putNullable("refresh_token", generation.refreshToken)
        putNullable("device_token", generation.deviceToken)
        putNullable("session_token", generation.sessionToken)
        putNullable("fingerprint_token", generation.fingerprintToken)
    }.toString().toByteArray(StandardCharsets.UTF_8)

    private fun decode(bytes: ByteArray): TokenGeneration {
        val json = JSONObject(String(bytes, StandardCharsets.UTF_8))
        if (json.optInt("version", 0) != VAULT_VERSION) {
            throw TokenVaultException("Unsupported token vault generation")
        }
        return TokenGeneration(
            accessToken = json.nullableString("access_token"),
            refreshToken = json.nullableString("refresh_token"),
            deviceToken = json.nullableString("device_token"),
            sessionToken = json.nullableString("session_token"),
            fingerprintToken = json.nullableString("fingerprint_token"),
        )
    }

    private fun JSONObject.putNullable(key: String, value: String?) {
        if (value == null) put(key, JSONObject.NULL) else put(key, value)
    }

    private fun JSONObject.nullableString(key: String): String? =
        if (!has(key) || isNull(key)) null else optString(key, null)

    companion object {
        internal const val PREFS_NAME = "safa_token_vault_v3"
        internal const val KEY_GENERATION = "auth_generation"
        private const val VAULT_VERSION = 3
    }
}

private object TokenVaultCipherFactory {
    fun create(): TokenVaultCipher = if (Build.FINGERPRINT == "robolectric") {
        RobolectricTokenVaultCipher()
    } else {
        AndroidKeystoreTokenVaultCipher()
    }
}

internal class AndroidKeystoreTokenVaultCipher : TokenVaultCipher {
    override fun encrypt(field: String, plaintext: ByteArray): String = try {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        cipher.updateAAD(aad(field))
        val ciphertext = cipher.doFinal(plaintext)
        envelope(cipher.iv, ciphertext)
    } catch (error: Exception) {
        throw TokenVaultException("Unable to encrypt token vault generation", error)
    }

    override fun decrypt(field: String, encodedCiphertext: String): ByteArray = try {
        val (iv, ciphertext) = parseEnvelope(encodedCiphertext)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(GCM_TAG_BITS, iv))
        cipher.updateAAD(aad(field))
        cipher.doFinal(ciphertext)
    } catch (error: Exception) {
        throw TokenVaultException("Unable to authenticate token vault generation", error)
    }

    override fun reset() {
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (keyStore.containsAlias(KEY_ALIAS)) keyStore.deleteEntry(KEY_ALIAS)
        } catch (error: Exception) {
            throw TokenVaultException("Unable to reset token vault key", error)
        }
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "safa.auth.token.vault.v3"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
    }
}

/** Test-only runtime seam selected strictly by Robolectric's build fingerprint. */
internal class RobolectricTokenVaultCipher : TokenVaultCipher {
    private val key = SecretKeySpec(
        MessageDigest.getInstance("SHA-256").digest("safa-robolectric-token-vault-v3".toByteArray()),
        "AES",
    )
    private val random = SecureRandom()

    override fun encrypt(field: String, plaintext: ByteArray): String {
        val iv = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        cipher.updateAAD(aad(field))
        return envelope(iv, cipher.doFinal(plaintext))
    }

    override fun decrypt(field: String, encodedCiphertext: String): ByteArray = try {
        val (iv, ciphertext) = parseEnvelope(encodedCiphertext)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        cipher.updateAAD(aad(field))
        cipher.doFinal(ciphertext)
    } catch (error: Exception) {
        throw TokenVaultException("Unable to authenticate token vault generation", error)
    }

    override fun reset() = Unit
}

private fun aad(field: String): ByteArray = "safa-token-vault|v3|$field".toByteArray(StandardCharsets.UTF_8)

private fun envelope(iv: ByteArray, ciphertext: ByteArray): String = JSONObject().apply {
    put("v", 3)
    put("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
    put("ct", Base64.encodeToString(ciphertext, Base64.NO_WRAP))
}.toString()

private fun parseEnvelope(encoded: String): Pair<ByteArray, ByteArray> {
    val json = JSONObject(encoded)
    if (json.optInt("v", 0) != 3) throw TokenVaultException("Unsupported token vault envelope")
    val iv = Base64.decode(json.getString("iv"), Base64.DEFAULT)
    val ciphertext = Base64.decode(json.getString("ct"), Base64.DEFAULT)
    if (iv.size != 12 || ciphertext.isEmpty()) throw TokenVaultException("Invalid token vault envelope")
    return iv to ciphertext
}
