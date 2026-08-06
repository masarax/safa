package com.safa.account.ui

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager

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
    val activity = context.findFragmentActivity()
    var isAuthenticating by remember { mutableStateOf(false) }
    
    val triggerBiometric = {
        isAuthenticating = true
        if (activity != null) {
            val biometricManager = BiometricManager.from(context)
            val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
            val canAuthenticate = biometricManager.canAuthenticate(authenticators)
            
            if (canAuthenticate == BiometricManager.BIOMETRIC_SUCCESS) {
                val executor = ContextCompat.getMainExecutor(context)
                val biometricPrompt = BiometricPrompt(
                activity,
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        super.onAuthenticationSucceeded(result)
                        activity.runOnUiThread {
                            isAuthenticating = false
                            onSuccess()
                        }
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        super.onAuthenticationError(errorCode, errString)
                        activity.runOnUiThread {
                            isAuthenticating = false
                            onError(errString.toString())
                        }
                    }

                    override fun onAuthenticationFailed() {
                        super.onAuthenticationFailed()
                        activity.runOnUiThread {
                            isAuthenticating = false
                            onError("Authentication failed. Please try again.")
                        }
                    }
                }
            )

            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle(if (lang == "BN") "সিকিউরিটি ভেরিফিকেশন" else "Security Verification")
                .setSubtitle(if (lang == "BN") "ফিঙ্গারপ্রিন্ট বা লক স্ক্রিন ব্যবহার করুন" else "Verify using fingerprint or lock screen")
                .setAllowedAuthenticators(authenticators)
                .build()

            try {
                biometricPrompt.authenticate(promptInfo)
            } catch (e: Exception) {
                isAuthenticating = false
                onError(e.localizedMessage ?: "Biometric authentication unavailable")
            }
            } else {
                isAuthenticating = false
                when (canAuthenticate) {
                    BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> onError("No biometric features available on this device.")
                    BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> onError("Biometric features are currently unavailable.")
                    BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> onError("No biometric credential enrolled.")
                    else -> onError("Biometric authentication is not supported.")
                }
            }
        } else {
            isAuthenticating = false
            onError("Biometric authentication requires a FragmentActivity")
        }
    }

    // Automatically trigger on first launch with 250ms beautiful simulation delay
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(250)
        triggerBiometric()
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                .clickable { 
                    triggerBiometric()
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
