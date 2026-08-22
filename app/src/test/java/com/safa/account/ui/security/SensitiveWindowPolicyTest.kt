package com.safa.account.ui.security

import android.app.Activity
import android.view.WindowManager
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class SensitiveWindowPolicyTest {
    @Test
    @Config(sdk = [30])
    fun `financial activity is protected from ordinary screen capture`() {
        // API 30 exercises FLAG_SECURE without invoking Android 12's
        // HIDE_OVERLAY_WINDOWS permission check, which Robolectric cannot model
        // for the framework Activity used by this isolated unit test. The real
        // API 35 application launch remains covered by connected/release smoke.
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()

        SensitiveWindowPolicy.apply(activity.window)

        assertTrue(
            activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0
        )
    }
}
