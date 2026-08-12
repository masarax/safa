package com.safa.account.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.safa.account.data.api.TokenManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Phase2UiAndBrandingTest {

    @Test
    fun testTokenManagerDarkThemeAndLanguagePersistenceKeys() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val tokenManager = TokenManager(context)

        tokenManager.saveDarkMode(true)
        tokenManager.saveLanguage("EN")
        tokenManager.saveCustomAppName("SAFA Enterprise")

        assertTrue("Dark mode preference should persist", tokenManager.getDarkMode())
        assertEquals("Language preference should be EN", "EN", tokenManager.getLanguage())
        assertEquals("Custom app name should persist", "SAFA Enterprise", tokenManager.getCustomAppName())
    }

    @Test
    fun testProductionCredentialsAreNotHardcodedInApkSource() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val tokenManager = TokenManager(context)

        val apiKey = tokenManager.getApiKey()
        val apiSecret = tokenManager.getApiSecret()

        assertNotNull("API key must be available for the public client", apiKey)
        assertFalse("API key must not be treated as an HMAC secret", apiKey.contains("safa_sec_"))
        assertTrue("API secret must never be embedded in the APK", apiSecret.isEmpty())
    }
}
