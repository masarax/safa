package com.safa.account.data.api

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TokenVaultMigrationTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(TokenVault.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences(TokenManager.METADATA_PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences(LegacySecurePreferences.ENCRYPTED_PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences(LegacySecurePreferences.PLAIN_PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun tokenGenerationRoundTripsAsOneAuthenticatedCiphertextWithoutPlaintextSecrets() {
        val generation = TokenGeneration(
            accessToken = "access-secret",
            refreshToken = "refresh-secret",
            deviceToken = "device-secret",
            sessionToken = "session-secret",
            fingerprintToken = "fingerprint-secret",
        )
        val vault = TokenVault(context)

        vault.write(generation)

        assertEquals(generation, TokenVault(context).read())
        val ciphertext = vault.rawCiphertextForTest().orEmpty()
        listOf("access-secret", "refresh-secret", "device-secret", "session-secret", "fingerprint-secret")
            .forEach { secret -> assertFalse(ciphertext.contains(secret)) }
    }

    @Test
    fun ciphertextModificationAndWrongAssociatedDataFailClosed() {
        val cipher = RobolectricTokenVaultCipher()
        val encoded = cipher.encrypt(TokenVault.KEY_GENERATION, "sensitive".toByteArray())

        expectVaultFailure { cipher.decrypt("different-field", encoded) }

        val vault = TokenVault(context, cipher)
        vault.write(TokenGeneration(accessToken = "access-secret"))
        val prefs = context.getSharedPreferences(TokenVault.PREFS_NAME, Context.MODE_PRIVATE)
        val original = prefs.getString(TokenVault.KEY_GENERATION, null)!!
        val tampered = original.replaceFirst("ct\":\"", "ct\":\"A")
        prefs.edit().putString(TokenVault.KEY_GENERATION, tampered).commit()

        expectVaultFailure { vault.read() }
    }

    @Test
    fun legacyEncryptedGenerationMigratesOnceAndLeavesNoSecretInMetadataPreferences() {
        val access = jwtWithPayload("""{"account_id":42}""")
        val legacy = context.getSharedPreferences(LegacySecurePreferences.ENCRYPTED_PREFS_NAME, Context.MODE_PRIVATE)
        legacy.edit()
            .putString("auth_token", access)
            .putString("refresh_token", "refresh-secret")
            .putString("device_token", "device-secret")
            .putString("session_token", "session-secret")
            .putString("fingerprint_token", "fingerprint-secret")
            .putString("last_mobile", "0500000000")
            .putInt("active_account_id", 42)
            .commit()

        val manager = TokenManager(context)

        assertEquals(access, manager.getAccessToken())
        assertEquals("refresh-secret", manager.getRefreshToken())
        assertEquals("device-secret", manager.getDeviceToken())
        assertEquals("session-secret", manager.getSessionToken())
        assertEquals("fingerprint-secret", manager.getFingerprintToken())
        assertEquals(42, manager.getActiveAccountId())
        assertEquals("0500000000", manager.getLastMobile())
        assertTrue(legacy.all.isEmpty())

        val metadata = context.getSharedPreferences(TokenManager.METADATA_PREFS_NAME, Context.MODE_PRIVATE)
        val metadataText = metadata.all.toString()
        listOf(access, "refresh-secret", "device-secret", "session-secret", "fingerprint-secret")
            .forEach { secret -> assertFalse(metadataText.contains(secret)) }

        val encrypted = TokenVault(context).rawCiphertextForTest().orEmpty()
        listOf(access, "refresh-secret", "device-secret", "session-secret", "fingerprint-secret")
            .forEach { secret -> assertFalse(encrypted.contains(secret)) }

        assertEquals(access, TokenManager(context).getAccessToken())
    }

    @Test
    fun crashAfterVerifiedVaultWriteKeepsLegacyGenerationRecoverableForIdempotentRerun() {
        val legacy = context.getSharedPreferences(LegacySecurePreferences.ENCRYPTED_PREFS_NAME, Context.MODE_PRIVATE)
        legacy.edit()
            .putString("auth_token", "access-before-crash")
            .putString("refresh_token", "refresh-before-crash")
            .putString("session_token", "session-before-crash")
            .commit()
        val vault = TokenVault(context)

        try {
            TokenManager(context, vault) { throw InjectedMigrationCrash() }
            throw AssertionError("Injected migration crash was not raised")
        } catch (_: InjectedMigrationCrash) {
            // Expected: new ciphertext is durable but legacy is not deleted yet.
        }

        assertEquals("access-before-crash", vault.read().accessToken)
        assertEquals("access-before-crash", legacy.getString("auth_token", null))

        val recovered = TokenManager(context)
        assertEquals("access-before-crash", recovered.getAccessToken())
        assertEquals("refresh-before-crash", recovered.getRefreshToken())
        assertEquals("session-before-crash", recovered.getSessionToken())
        assertTrue(legacy.all.isEmpty())
    }

    @Test
    fun corruptedRuntimeVaultClearsSessionBindingAndReturnsNoCredential() {
        val manager = TokenManager(context)
        manager.saveAllTokens(
            accessToken = jwtWithPayload("""{"account_id":88}"""),
            refreshToken = "refresh-secret",
            deviceToken = "device-secret",
            sessionToken = "session-secret",
            fingerprintToken = "fingerprint-secret",
        )
        manager.enableBiometricQuickUnlock(7, "0500000000")
        assertEquals(88, manager.getActiveAccountId())

        val vaultPrefs = context.getSharedPreferences(TokenVault.PREFS_NAME, Context.MODE_PRIVATE)
        vaultPrefs.edit().putString(TokenVault.KEY_GENERATION, "{\"v\":3,\"iv\":\"broken\",\"ct\":\"broken\"}").commit()

        val recovered = TokenManager(context)

        assertNull(recovered.getAccessToken())
        assertNull(recovered.getRefreshToken())
        assertNull(recovered.getSessionToken())
        assertNull(recovered.getActiveAccountId())
        assertFalse(recovered.isBiometricQuickUnlockEnabled())
    }

    @Test
    fun clearAllTokensPreservesDeviceIdentityButRemovesAuthenticatedSessionGeneration() {
        val manager = TokenManager(context)
        manager.saveAllTokens(
            accessToken = "access-secret",
            refreshToken = "refresh-secret",
            deviceToken = "device-secret",
            sessionToken = "session-secret",
            fingerprintToken = "fingerprint-secret",
        )

        manager.clearAllTokens()

        assertNull(manager.getAccessToken())
        assertNull(manager.getRefreshToken())
        assertNull(manager.getSessionToken())
        assertEquals("device-secret", manager.getDeviceToken())
        assertEquals("fingerprint-secret", manager.getFingerprintToken())
    }

    private fun expectVaultFailure(block: () -> Unit) {
        try {
            block()
            throw AssertionError("Expected token vault authentication failure")
        } catch (_: TokenVaultException) {
            // Expected.
        }
    }

    private fun jwtWithPayload(payload: String): String {
        val header = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("{}".toByteArray(Charsets.UTF_8))
        val body = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(payload.toByteArray(Charsets.UTF_8))
        return "$header.$body.signature"
    }

    private class InjectedMigrationCrash : RuntimeException()
}
