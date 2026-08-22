package com.safa.account.data.api

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.security.KeyStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TokenVaultInstrumentedTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(TokenVault.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences(TokenManager.METADATA_PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        LegacySecurePreferences.delete(context)
        runCatching { AndroidKeystoreTokenVaultCipher().reset() }
    }

    @After
    fun tearDown() {
        context.getSharedPreferences(TokenVault.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences(TokenManager.METADATA_PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        LegacySecurePreferences.delete(context)
        runCatching { AndroidKeystoreTokenVaultCipher().reset() }
    }

    @Test
    fun productionVaultUsesNonExportableAndroidKeystoreKey() {
        val vault = TokenVault(context)
        val generation = TokenGeneration(
            accessToken = "instrumented-access",
            refreshToken = "instrumented-refresh",
            deviceToken = "instrumented-device",
            sessionToken = "instrumented-session",
            fingerprintToken = "instrumented-fingerprint",
        )

        vault.write(generation)

        assertEquals(generation, vault.read())
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val key = keyStore.getKey("safa.auth.token.vault.v3", null)
        assertTrue(key != null)
        assertNull(key.encoded)
    }

    @Test
    fun realLegacyEncryptedPreferencesMigrateIntoKeystoreVault() {
        val legacy = LegacySecurePreferences.encrypted(context)
        legacy.edit()
            .putString("auth_token", "legacy-access")
            .putString("refresh_token", "legacy-refresh")
            .putString("device_token", "legacy-device")
            .putString("session_token", "legacy-session")
            .putString("fingerprint_token", "legacy-fingerprint")
            .putString("last_mobile", "0500000000")
            .commit()

        val manager = TokenManager(context)

        assertEquals("legacy-access", manager.getAccessToken())
        assertEquals("legacy-refresh", manager.getRefreshToken())
        assertEquals("legacy-device", manager.getDeviceToken())
        assertEquals("legacy-session", manager.getSessionToken())
        assertEquals("legacy-fingerprint", manager.getFingerprintToken())
        assertEquals("0500000000", manager.getLastMobile())
        assertTrue(context.getSharedPreferences(LegacySecurePreferences.ENCRYPTED_PREFS_NAME, Context.MODE_PRIVATE).all.isEmpty())
    }
}
