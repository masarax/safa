package com.safa.account.ui.screens

import android.util.Base64
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
import org.json.JSONObject

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

/** Access JWT is usable only while its own exp claim is still valid. */
private fun isAccessTokenFresh(token: String?, minimumLifetimeSeconds: Long = 30): Boolean {
    if (token.isNullOrBlank()) return false
    return try {
        val parts = token.split('.')
        if (parts.size != 3) return false
        val payload = String(Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_WRAP), Charsets.UTF_8)
        val exp = JSONObject(payload).optLong("exp", 0L)
        exp > (System.currentTimeMillis() / 1000L) + minimumLifetimeSeconds
    } catch (_: Throwable) {
        false
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(viewModel: SafaViewModel, modifier: Modifier = Modifier) {
    val operators by viewModel.operators.collectAsStateWithLifecycle()
    val currentLang by viewModel.currentLanguage.collectAsStateWithLifecycle()
    val tokenManager = viewModel.tokenManager
    val coroutineScope = rememberCoroutineScope()

    // Quick unlock is tied to the explicitly enabled account operator plus a
    // complete encrypted resumable session. The access token must still be
    // cryptographically within its JWT lifetime; an expired token is first
    // allowed to go through the normal interceptor refresh path.
    val hasCompleteLocalSession = tokenManager?.let {
        !it.getAccessToken().isNullOrBlank() &&
            !it.getRefreshToken().isNullOrBlank() &&
            !it.getSessionToken().isNullOrBlank()
    } == true

    var mobileInput by remember { mutableStateOf(tokenManager?.getLastMobile() ?: "") }
    var pinInput by remember { mutableStateOf("") }
    var loginError by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    val matchingOp = remember(mobileInput, operators) {
        operators.find { it.mobile.trim() == mobileInput.trim() && it.mobile.isNotBlank() }
    }

    // Session restoration policy:
    // 1) Fresh access token -> restore immediately when biometric is disabled.
    // 2) Expired access token -> make one authenticated request so the shared
    //    interceptor can rotate the refresh token; only restore if that works.
    // 3) Biometric-enabled account -> never bypass the biometric prompt.
    LaunchedEffect(matchingOp, hasCompleteLocalSession) {
        val tm = tokenManager ?: return@LaunchedEffect
        val operator = matchingOp ?: return@LaunchedEffect
        if (!hasCompleteLocalSession || !operator.isActive) return@LaunchedEffect

        var sessionReady = isAccessTokenFresh(tm.getAccessToken())
        if (!sessionReady) {
            runCatching {
                val api = viewModel.syncManager?.getApiService()
                val response = api?.getOperators()
                sessionReady = response?.isSuccessful == true && isAccessTokenFresh(tm.getAccessToken())
            }
        }

        if (sessionReady && !operator.isBiometricEnabled) {
            viewModel.loginWithBiometric(operator)
        }
    }

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(16.dp)) {
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
            Box(modifier = Modifier.size(88.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
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

            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
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

                val biometricOperator = matchingOp?.takeIf {
                    hasCompleteLocalSession && it.isActive && it.isBiometricEnabled
                }

                if (biometricOperator != null) {
                    com.safa.account.ui.BiometricTriggerButton(
                        lang = currentLang,
                        autoLaunch = true,
                        onSuccess = {
                            // BiometricPrompt proves possession of the device
                            // biometric. The local operator flag proves this
                            // account explicitly enabled quick unlock. Before
                            // entering the app, also prove that the server
                            // session is still recoverable; an expired access
                            // token must rotate through the refresh endpoint.
                            coroutineScope.launch {
                                val tm = tokenManager
                                if (tm == null) {
                                    loginError = if (currentLang == "BN") "সেশন পাওয়া যায়নি" else "Session unavailable"
                                    return@launch
                                }
                                var sessionReady = isAccessTokenFresh(tm.getAccessToken())
                                if (!sessionReady) {
                                    runCatching {
                                        val api = viewModel.syncManager?.getApiService()
                                        val response = api?.getOperators()
                                        sessionReady = response?.isSuccessful == true && isAccessTokenFresh(tm.getAccessToken())
                                    }
                                }
                                if (sessionReady) {
                                    viewModel.loginWithBiometric(biometricOperator)
                                } else {
                                    loginError = if (currentLang == "BN") {
                                        "সেশন শেষ হয়েছে। আবার মোবাইল ও পিন দিয়ে লগইন করুন।"
                                    } else {
                                        "Session expired. Please sign in again with mobile and PIN."
                                    }
                                }
                            }
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
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
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
