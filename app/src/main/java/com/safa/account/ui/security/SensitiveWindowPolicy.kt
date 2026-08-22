package com.safa.account.ui.security

import android.os.Build
import android.view.Window
import android.view.WindowManager

/**
 * SAFA is a financial/business application whose normal authenticated surfaces
 * expose balances, receiver details and account information. The secure default
 * therefore protects every activity, including authentication and recent-app
 * previews, rather than relying on each feature screen to remember a flag.
 */
object SensitiveWindowPolicy {
    fun apply(window: Window) {
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            window.setHideOverlayWindows(true)
        }
    }
}
