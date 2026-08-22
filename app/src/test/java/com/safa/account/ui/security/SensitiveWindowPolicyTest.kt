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
@Config(sdk = [34])
class SensitiveWindowPolicyTest {
    @Test
    fun `financial activity is protected from ordinary screen capture`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()

        SensitiveWindowPolicy.apply(activity.window)

        assertTrue(
            activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0
        )
    }
}
