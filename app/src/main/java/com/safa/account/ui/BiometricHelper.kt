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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

fun Context.findFragmentActivity(): FragmentActivity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is FragmentActivity) return current
        current = current.baseContext
    }
    return null
}

fun launchBiometricPrompt(context: Context, lang: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
    val activity = context.findFragmentActivity() ?: run { onError("Biometric authentication is unavailable"); return }
    activity.runOnUiThread {
        val manager = BiometricManager.from(context)
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG
        when (manager.canAuthenticate(authenticators)) {
            BiometricManager.BIOMETRIC_SUCCESS -> Unit
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> { onError(if (lang == "BN") "এই ডিভাইসে ফিঙ্গারপ্রিন্ট নেই।" else "This device has no biometric hardware."); return@runOnUiThread }
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> { onError(if (lang == "BN") "ফিঙ্গারপ্রিন্ট সেটআপ করা নেই।" else "No biometric is enrolled on this device."); return@runOnUiThread }
            else -> { onError(if (lang == "BN") "ফিঙ্গারপ্রিন্ট এখন ব্যবহার করা যাচ্ছে না।" else "Biometric authentication is unavailable."); return@runOnUiThread }
        }

        val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(context), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { onSuccess() }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (errorCode != BiometricPrompt.ERROR_USER_CANCELED && errorCode != BiometricPrompt.ERROR_CANCELED) onError(errString.toString())
            }
            override fun onAuthenticationFailed() { /* Keep prompt open; one failed scan must not log the user out. */ }
        })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(if (lang == "BN") "দ্রুত প্রবেশ" else "Quick Unlock")
            .setSubtitle(if (lang == "BN") "আপনার ফিঙ্গারপ্রিন্ট দিয়ে অ্যাপ খুলুন" else "Use your fingerprint to unlock SAFA")
            .setNegativeButtonText(if (lang == "BN") "PIN দিয়ে প্রবেশ" else "Use PIN")
            .setAllowedAuthenticators(authenticators)
            .build()
        runCatching { prompt.authenticate(info) }.onFailure { onError(it.localizedMessage ?: "Biometric prompt failed") }
    }
}

@Composable
fun BiometricTriggerButton(lang: String, onSuccess: () -> Unit, onError: (String) -> Unit, modifier: Modifier = Modifier, autoLaunch: Boolean = false) {
    val context = LocalContext.current
    var isAuthenticating by remember { mutableStateOf(false) }
    val currentOnSuccess by rememberUpdatedState(onSuccess)
    val currentOnError by rememberUpdatedState(onError)

    fun trigger() {
        if (isAuthenticating) return
        isAuthenticating = true
        launchBiometricPrompt(context, lang, onSuccess = { isAuthenticating = false; currentOnSuccess() }, onError = { isAuthenticating = false; currentOnError(it) })
    }

    LaunchedEffect(autoLaunch) { if (autoLaunch) trigger() }

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Box(Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)).clickable { trigger() }, contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Fingerprint, contentDescription = if (lang == "BN") "ফিঙ্গারপ্রিন্ট" else "Fingerprint", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(if (lang == "BN") "ফিঙ্গারপ্রিন্ট দিয়ে দ্রুত প্রবেশ" else "Quick unlock with fingerprint", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    }
}
