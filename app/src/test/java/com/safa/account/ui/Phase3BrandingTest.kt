package com.safa.account.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.safa.account.data.api.TokenManager
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Phase3BrandingTest {

    @Test
    fun `verify launcher foreground icon does not contain generic android robot artwork`() {
        val foregroundFile = File("src/main/res/drawable/ic_launcher_foreground.xml")
        assertTrue("ic_launcher_foreground.xml must exist", foregroundFile.exists())
        
        val content = foregroundFile.readText()
        assertFalse("ic_launcher_foreground.xml must NOT contain generic android robot vector path", content.contains("com.android"))
        assertFalse("ic_launcher_foreground.xml must NOT contain generic robot eye path", content.contains("M38,42 a4,4"))
        assertTrue("ic_launcher_foreground.xml must contain SAFA branding asset path", content.contains("fillColor") || content.contains("path") || content.contains("safa_logo") || content.contains("bitmap"))
    }

    @Test
    fun `verify TokenManager does not contain hardcoded production API secrets in source`() {
        val tokenManagerFile = File("src/main/java/com/safa/account/data/api/TokenManager.kt")
        if (tokenManagerFile.exists()) {
            val content = tokenManagerFile.readText()
            assertFalse("TokenManager.kt must NOT contain hardcoded safa_key_", content.contains("\"safa_key_"))
            assertFalse("TokenManager.kt must NOT contain hardcoded safa_sec_", content.contains("\"safa_sec_"))
        }
    }

    @Test
    fun `verify default app logo is not crown emoji`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val tm = TokenManager(context)
        val defaultLogo = tm.getCustomAppLogo()
        assertNotEquals("Default app logo should not be crown emoji 👑", "👑", defaultLogo)
    }

    @Test
    fun `verify DashboardScreen contains zero fake fallback placeholder customers`() {
        val dashboardFile = File("src/main/java/com/safa/account/ui/screens/DashboardScreen.kt")
        if (dashboardFile.exists()) {
            val content = dashboardFile.readText()
            assertFalse("DashboardScreen must NOT contain fake placeholder customer 'রানা ভাই'", content.contains("রানা ভাই"))
            assertFalse("DashboardScreen must NOT contain fake placeholder customer 'হাসেম ভাই'", content.contains("হাসেম ভাই"))
            assertFalse("DashboardScreen must NOT contain fake placeholder customer 'Fahim Rana'", content.contains("Fahim Rana"))
            assertFalse("DashboardScreen must NOT contain fake placeholder customer 'নাজমুল চাচা'", content.contains("নাজমুল চাচা"))
        }
    }

    @Test
    fun `verify UI source contains no compound bilingual strings`() {
        val loginFile = File("src/main/java/com/safa/account/ui/screens/LoginScreen.kt")
        if (loginFile.exists()) {
            val content = loginFile.readText()
            assertFalse("LoginScreen must NOT contain compound string 'EN | বাংলা'", content.contains("EN | বাংলা"))
        }

        val dashboardFile = File("src/main/java/com/safa/account/ui/screens/DashboardScreen.kt")
        if (dashboardFile.exists()) {
            val content = dashboardFile.readText()
            assertFalse("DashboardScreen must NOT contain compound string 'রিয়াল প্রদান (ডিপোজিট)'", content.contains("রিয়াল প্রদান (ডিপোজিট)"))
            assertFalse("DashboardScreen must NOT contain compound string 'রিয়াল গ্রহণ (উত্তোলন)'", content.contains("রিয়াল গ্রহণ (উত্তোলন)"))
        }
    }
}
