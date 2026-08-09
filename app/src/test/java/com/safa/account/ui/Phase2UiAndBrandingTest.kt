package com.safa.account.ui

import android.content.Context
import com.safa.account.data.api.TokenManager
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.*

class Phase2UiAndBrandingTest {

    @Test
    fun testTokenManagerDarkThemeAndLanguagePersistenceKeys() {
        val mockContext: Context = mock()
        val mockPrefs: android.content.SharedPreferences = mock()
        val mockEditor: android.content.SharedPreferences.Editor = mock()

        whenever(mockContext.getSharedPreferences(any(), any())).thenReturn(mockPrefs)
        whenever(mockPrefs.edit()).thenReturn(mockEditor)
        whenever(mockEditor.putBoolean(any(), any())).thenReturn(mockEditor)
        whenever(mockEditor.putString(any(), any())).thenReturn(mockEditor)

        whenever(mockPrefs.getBoolean("app_dark_mode", false)).thenReturn(true)
        whenever(mockPrefs.getString("app_lang", "BN")).thenReturn("EN")
        whenever(mockPrefs.getString("app_name", "SAFA")).thenReturn("SAFA Enterprise")

        val tokenManager = TokenManager(mockContext)

        assertTrue("Dark mode preference should be retrieved from TokenManager", tokenManager.getDarkMode())
        assertEquals("Language preference should be EN", "EN", tokenManager.getLanguage())
        assertEquals("Custom app name should be SAFA Enterprise", "SAFA Enterprise", tokenManager.getCustomAppName())
    }

    @Test
    fun testProductionCredentialsAreNotHardcodedInApkSource() {
        val mockContext: Context = mock()
        val mockPrefs: android.content.SharedPreferences = mock()
        whenever(mockContext.getSharedPreferences(any(), any())).thenReturn(mockPrefs)

        val tokenManager = TokenManager(mockContext)

        val apiKey = tokenManager.getApiKey()
        val apiSecret = tokenManager.getApiSecret()

        assertNotNull("API key should not be null", apiKey)
        assertNotNull("API secret should not be null", apiSecret)
        assertFalse("API key default must NOT contain hardcoded safa_key_", apiKey.startsWith("safa_key_"))
        assertFalse("API secret default must NOT contain hardcoded safa_sec_", apiSecret.startsWith("safa_sec_"))
    }
}
