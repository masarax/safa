package com.safa.account.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.safa.account.ui.viewmodel.SafaViewModel
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource

private fun normalizeDigits(value: String): String = buildString(value.length) {
    value.forEach { ch ->
        when (ch) {
            in '০'..'৯' -> append(('0'.code + (ch.code - '০'.code)).toChar())
            in '٠'..'٩' -> append(('0'.code + (ch.code - '٠'.code)).toChar())
            else -> append(ch)
        }
    }
}

private fun serverErrorMessage(raw: String): String? {
    val match = Regex("\\\"message\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"").find(raw)
    return match?.groupValues?.getOrNull(1)
        ?.replace("\\u0027", "'")
        ?.replace("\\\\\\\"", "\\\"")
        ?.takeIf { it.isNotBlank() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(viewModel: SafaViewModel, modifier: Modifier = Modifier) {
    val operators by viewModel.operators.collectAsStateWithLifecycle()
    val currentLang by viewModel.currentLanguage.collectAsStateWithLifecycle()
    val tokenManager = viewModel.tokenManager
    val hasLocalQuickUnlockSession = tokenManager?.hasValidLocalSessionForQuickUnlock() == true
    val quickUnlockUserId = tokenManager?.getBiometricQuickUnlockUserId()

    var mobileInput by remember { mutableStateOf(tokenManager?.getLastMobile() ?: "") }
    var pinInput by remember { mutableStateOf("") }
    var loginError by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    Box(
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.End
        ) {
            FilledTonalButton(
                onClick = { viewModel.toggleLanguage() },
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                modifier = Modifier.height(36.dp).testTag("auth_lang_toggle")
            ) {
                Text(
                    text = if (currentLang == "BN") "English" else "বাংলা",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).align(Alignment.Center).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Box(
                modifier = Modifier.size(88.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = com.safa.account.R.drawable.safa_logo),
                    contentDescription = "SAFA Logo",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }

            Text(
                text = if (currentLang == "BN") "SAFA - সাফা" else "SAFA",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 22.sp),
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                OutlinedTextField(
                    value = mobileInput,
                    onValueChange = { mobileInput = it; loginError = null },
                    label = { Text(if (currentLang == "BN") "মোবাইল" else "Mobile") },
                    placeholder = { Text("01700000000") },
                    leadingIcon = { Icon(Icons.Default.Phone, contentDescription = "Mobile") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth().testTag("login_mobile_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = pinInput,
                    onValueChange = {
                        val normalized = normalizeDigits(it)
                        if (normalized.length <= 6 && normalized.all(Char::isDigit)) {
                            pinInput = normalized
                            loginError = null
                        }
                    },
                    label = { Text(if (currentLang == "BN") "পিন" else "PIN") },
                    leadingIcon = { Icon(Icons.Default.Security, contentDescription = "PIN") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth().testTag("login_pin_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                AnimatedVisibility(visible = loginError != null, enter = fadeIn(), exit = fadeOut()) {
                    Text(
                        text = loginError ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        textAlign = TextAlign.Center,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Quick unlock is allowed only when a real local session remains.
                // The operator is resolved from the local snapshot, so the feature works offline.
                val matchingOp = remember(mobileInput, operators, quickUnlockUserId) {
                    operators.find { it.mobile.trim() == mobileInput.trim() && it.mobile.isNotBlank() }
                        ?: quickUnlockUserId?.let { id -> operators.find { it.id == id } }
                }
                val biometricAllowed = hasLocalQuickUnlockSession && matchingOp?.isActive == true && matchingOp.isBiometricEnabled
                if (biometricAllowed) {
                    com.safa.account.ui.BiometricTriggerButton(
                        lang = currentLang,
                        autoLaunch = true,
                        onSuccess = {
                            viewModel.loginWithBiometric(matchingOp)
                        },
                        onError = { loginError = it }
                    )
                }

                Button(
                    onClick = {
                        val normalizedMobile = mobileInput.trim()
                        val normalizedPin = normalizeDigits(pinInput).trim()
                        if (normalizedMobile.isBlank()) {
                            loginError = if (currentLang == "BN") "মোবাইল দিন" else "Enter mobile"
                            return@Button
                        }
                        if (normalizedPin.length != 6) {
                            loginError = if (currentLang == "BN") "৬ ডিজিটের পিন দিন" else "Enter 6-digit PIN"
                            return@Button
                        }
                        isLoading = true
                        viewModel.loginWithServer(normalizedMobile, normalizedPin) { success, result ->
                            isLoading = false
                            if (!success) {
                                val parsed = result?.let(::serverErrorMessage)
                                loginError = parsed
                                    ?: result?.takeIf { it.isNotBlank() }
                                    ?: viewModel.t("invalid_credentials", currentLang)
                            }
                        }
                    },
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth().height(50.dp).testTag("login_submit_btn"),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = if (currentLang == "BN") "লগইন" else "Login",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
