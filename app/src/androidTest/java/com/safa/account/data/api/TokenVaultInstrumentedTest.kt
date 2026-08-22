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
        clearFixturePreferences()
        AndroidKeystoreTokenVaultCipher().reset()
    }

    @After
    fun tearDown() {
        clearFixturePreferences()
        AndroidKeystoreTokenVaultCipher().reset()
    }

    private fun clearFixturePreferences() {
        check(
            context.getSharedPreferences(TokenVault.PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit()
        ) { "Unable to clear token-vault fixture preferences" }
        check(
            context.getSharedPreferences(TokenManager.METADATA_PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit()
        ) { "Unable to clear token metadata fixture preferences" }

        // Do not delete/recreate EncryptedSharedPreferences during fixture setup.
        // AndroidX Security keeps live keyset/preferences state in-process, and
        // deleting the backing file immediately before recreating it makes the
        // migration fixture nondeterministic on hosted emulators. Production
        // deletion is still exercised below by TokenManager migration itself.
        check(LegacySecurePreferences.encrypted(context).edit().clear().commit()) {
            "Unable to clear encrypted legacy fixture preferences"
        }
        check(LegacySecurePreferences.plain(context).edit().clear().commit()) {
            "Unable to clear plain legacy fixture preferences"
        }
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
        assertTrue(
            legacy.edit()
                .putString("auth_token", "legacy-access")
                .putString("refresh_token", "legacy-refresh")
                .putString("device_token", "legacy-device")
                .putString("session_token", "legacy-session")
                .putString("fingerprint_token", "legacy-fingerprint")
                .putString("last_mobile", "0500000000")
                .commit()
        )

        // Prove fixture creation independently from migration. A failure here is
        // an encrypted-preferences setup failure, not a TokenManager migration bug.
        assertEquals("legacy-access", legacy.getString("auth_token", null))
        assertEquals("legacy-refresh", legacy.getString("refresh_token", null))
        assertEquals("0500000000", legacy.getString("last_mobile", null))

        val manager = TokenManager(context)

        assertEquals("legacy-access", manager.getAccessToken())
        assertEquals("legacy-refresh", manager.getRefreshToken())
        assertEquals("legacy-device", manager.getDeviceToken())
        assertEquals("legacy-session", manager.getSessionToken())
        assertEquals("legacy-fingerprint", manager.getFingerprintToken())
        assertEquals("0500000000", manager.getLastMobile())

        // Migration must synchronously clear already-open encrypted handles and
        // remove the backing legacy preference store after the v3 vault verifies.
        assertTrue(legacy.all.isEmpty())
        assertTrue(
            context.getSharedPreferences(
                LegacySecurePreferences.ENCRYPTED_PREFS_NAME,
                Context.MODE_PRIVATE,
            ).all.isEmpty()
        )
    }
}
