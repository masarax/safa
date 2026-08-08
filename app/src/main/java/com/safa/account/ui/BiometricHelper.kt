package com.safa.account.ui

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

fun Context.findFragmentActivity(): FragmentActivity? {
    var currentContext = this
    while (currentContext is ContextWrapper) {
        if (currentContext is FragmentActivity) {
            return currentContext
        }
        currentContext = currentContext.baseContext
    }
    return null
}

@Composable
fun BiometricTriggerButton(
    lang: String,
    onSuccess: () -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findFragmentActivity() }
    var isAuthenticating by remember { mutableStateOf(false) }

    val currentOnSuccess by rememberUpdatedState(onSuccess)
    val currentOnError by rememberUpdatedState(onError)

    fun launchBiometricPrompt() {
        if (isAuthenticating) return
        val currentActivity = activity ?: context.findFragmentActivity()

        if (currentActivity == null) {
            currentOnError("Biometric authentication requires a FragmentActivity")
            return
        }

        isAuthenticating = true

        currentActivity.runOnUiThread {
            val biometricManager = BiometricManager.from(context)
            val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
            val canAuthenticate = biometricManager.canAuthenticate(authenticators)

            if (canAuthenticate == BiometricManager.BIOMETRIC_SUCCESS) {
                val executor = ContextCompat.getMainExecutor(context)
                val biometricPrompt = BiometricPrompt(
                    currentActivity,
                    executor,
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            super.onAuthenticationSucceeded(result)
                            isAuthenticating = false
                            currentOnSuccess()
                        }

                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                            super.onAuthenticationError(errorCode, errString)
                            isAuthenticating = false
                            // Ignore user cancellation (CANCELED = 10, USER_CANCELED = 13) without raising red error banners
                            if (errorCode != BiometricPrompt.ERROR_USER_CANCELED && errorCode != BiometricPrompt.ERROR_CANCELED) {
                                currentOnError(errString.toString())
                            }
                        }

                        override fun onAuthenticationFailed() {
                            super.onAuthenticationFailed()
                            isAuthenticating = false
                            currentOnError("Fingerprint not recognized. Please try again.")
                        }
                    }
                )

                val promptInfo = BiometricPrompt.PromptInfo.Builder()
                    .setTitle(if (lang == "BN") "সিকিউরিটি ভেরিফিকেশন" else "Security Verification")
                    .setSubtitle(if (lang == "BN") "ফিঙ্গারপ্রিন্ট বা লক স্ক্রিন ব্যবহার করুন" else "Scan fingerprint or enter screen lock")
                    .setAllowedAuthenticators(authenticators)
                    .build()

                try {
                    biometricPrompt.authenticate(promptInfo)
                } catch (e: Exception) {
                    isAuthenticating = false
                    currentOnError(e.localizedMessage ?: "Biometric prompt failed to launch")
                }
            } else {
                isAuthenticating = false
                val err = when (canAuthenticate) {
                    BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> "No biometric hardware on this device."
                    BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> "Biometric hardware is currently unavailable."
                    BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> "No fingerprint or lock credentials enrolled on this device."
                    else -> "Biometric authentication is not supported."
                }
                currentOnError(err)
            }
        }
    }

    // Auto-trigger immediately on launch
    LaunchedEffect(Unit) {
        launchBiometricPrompt()
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                .clickable {
                    launchBiometricPrompt()
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Fingerprint,
                contentDescription = "Scan Fingerprint",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = if (lang == "BN") "ফিঙ্গারপ্রিন্ট ব্যবহার করতে টাচ করুন" else "Touch to scan fingerprint",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}
