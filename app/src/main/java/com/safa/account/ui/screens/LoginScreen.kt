package com.safa.account.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.safa.account.data.api.FirstRunSetupClient
import com.safa.account.data.api.RetrofitClient
import com.safa.account.ui.viewmodel.SafaViewModel
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(viewModel: SafaViewModel, modifier: Modifier = Modifier) {
    val operators by viewModel.operators.collectAsStateWithLifecycle()
    val currentLang by viewModel.currentLanguage.collectAsStateWithLifecycle()
    val viewModelBiometricEnabled by viewModel.isBiometricEnabled.collectAsStateWithLifecycle()
    val apiBaseUrl by viewModel.apiBaseUrl.collectAsStateWithLifecycle()
    val tokenManager = viewModel.tokenManager
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    val hasCompleteLocalSession = tokenManager?.let {
        !it.isLogoutInProgress() &&
            !it.getAccessToken().isNullOrBlank() &&
            !it.getRefreshToken().isNullOrBlank() &&
            !it.getSessionToken().isNullOrBlank()
    } == true

    var mobileInput by remember { mutableStateOf(tokenManager?.getLastMobile() ?: "") }
    var pinInput by remember { mutableStateOf("") }
    var loginError by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var setupPhase by remember(apiBaseUrl) { mutableStateOf<String?>(null) }
    var setupProbeGeneration by remember(apiBaseUrl) { mutableIntStateOf(0) }

    // First-run setup detection stays active, but the routine health probe is
    // silent. A normal installation shows the login form immediately and only
    // a real setup-required response replaces it with the setup action.
    LaunchedEffect(apiBaseUrl, setupProbeGeneration) {
        setupPhase = runCatching {
            val response = RetrofitClient.getHealthApiService(apiBaseUrl).checkServerHealth()
            if (response.isSuccessful) {
                null
            } else {
                FirstRunSetupClient.phaseFromHealthResponse(
                    httpCode = response.code(),
                    errorBody = response.errorBody()?.string()
                )
            }
        }.getOrNull()
    }

    val matchingOp = remember(mobileInput, operators) {
        operators.find { it.mobile.trim() == mobileInput.trim() && it.mobile.isNotBlank() }
    }

    val biometricEnabled =
        tokenManager?.isBiometricQuickUnlockEnabled() == true ||
            viewModelBiometricEnabled ||
            matchingOp?.isBiometricEnabled == true

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

            if (setupPhase != null) {
                val databasePhase = setupPhase == "database"
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("first_run_setup_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = if (databasePhase) Icons.Default.Storage else Icons.Default.AdminPanelSettings,
                            contentDescription = null,
                            modifier = Modifier.size(38.dp),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = if (currentLang == "BN") {
                                if (databasePhase) "প্রথমবার ডাটাবেজ সেটআপ দরকার" else "প্রথম SuperAdmin সেটআপ শেষ করুন"
                            } else {
                                if (databasePhase) "First-time database setup required" else "Finish first SuperAdmin setup"
                            },
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = if (currentLang == "BN") {
                                if (databasePhase) {
                                    "লগইনের আগে server database initialize করতে হবে। নিচের button cPanel-protected setup page খুলবে; private setup code অ্যাপের মধ্যে দেখানো বা সংরক্ষণ করা হয় না।"
                                } else {
                                    "ডাটাবেজ প্রস্তুত হয়েছে। যে browser session দিয়ে migration চালানো হয়েছে, সেই session-এ প্রথম SuperAdmin তৈরি করে সেটআপ সম্পন্ন করুন।"
                                }
                            } else {
                                if (databasePhase) {
                                    "The server database must be initialized before sign-in. The button opens the protected setup page; the private setup code is never shown or stored in the Android app."
                                } else {
                                    "The database is prepared. Finish creating the first SuperAdmin in the same browser session that ran the migration."
                                }
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                        Button(
                            onClick = {
                                val setupUrl = runCatching { FirstRunSetupClient.webSetupUrl(apiBaseUrl) }.getOrNull()
                                if (setupUrl == null) {
                                    loginError = if (currentLang == "BN") "সেটআপ URL তৈরি করা যায়নি।" else "Could not create the setup URL."
                                } else {
                                    runCatching {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(setupUrl)))
                                    }.onFailure {
                                        loginError = if (currentLang == "BN") "সেটআপ পেইজ খোলা যায়নি।" else "Could not open the setup page."
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(50.dp).testTag("first_run_setup_action"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                if (currentLang == "BN") {
                                    if (databasePhase) "ডাটাবেজ সেটআপ খুলুন" else "SuperAdmin সেটআপ খুলুন"
                                } else {
                                    if (databasePhase) "Open Database Setup" else "Open SuperAdmin Setup"
                                },
                                fontWeight = FontWeight.Bold
                            )
                        }
                        OutlinedButton(
                            onClick = { setupProbeGeneration++ },
                            modifier = Modifier.fillMaxWidth().testTag("first_run_setup_retry")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(if (currentLang == "BN") "সেটআপ অবস্থা আবার যাচাই করুন" else "Check setup status again")
                        }
                    }
                }

                AnimatedVisibility(visible = loginError != null, enter = fadeIn(), exit = fadeOut()) {
                    Text(
                        text = loginError ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        textAlign = TextAlign.Center
                    )
                }
            } else {
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
                            val normalized = com.safa.account.data.api.MobileNumberNormalizer.normalizePin(it)
                            if (normalized.length <= 6) {
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
                        hasCompleteLocalSession && biometricEnabled && it.isActive
                    }

                    if (biometricOperator != null) {
                        com.safa.account.ui.BiometricTriggerButton(
                            lang = currentLang,
                            autoLaunch = true,
                            onSuccess = {
                                coroutineScope.launch {
                                    val tm = tokenManager
                                    if (tm == null || !hasCompleteLocalSession) {
                                        loginError = if (currentLang == "BN") "সেশন পাওয়া যায়নি" else "Session unavailable"
                                        return@launch
                                    }

                                    // Biometric is only the local unlock factor. It does
                                    // not authenticate the user to the server by itself.
                                    // Approve the already-authenticated session, then let
                                    // the interceptor refresh/revalidate it server-side.
                                    tm.approveBiometricUnlock()
                                    val sessionReady = runCatching {
                                        val response = viewModel.syncManager?.getApiService()?.getCurrentSession()
                                        @Suppress("UNCHECKED_CAST")
                                        val user = response?.body()?.get("user") as? Map<String, Any?>
                                        response?.isSuccessful == true && user != null &&
                                            viewModel.restoreAuthenticatedSession(user)
                                    }.getOrDefault(false)

                                    if (!sessionReady) {
                                        tm.revokeBiometricUnlockApproval()
                                        loginError = if (currentLang == "BN") {
                                            "সেশন যাচাই করা যায়নি। আবার মোবাইল ও পিন দিয়ে লগইন করুন।"
                                        } else {
                                            "The session could not be verified. Sign in again with mobile and PIN."
                                        }
                                    }
                                }
                            },
                            onError = { loginError = it }
                        )
                    }

                    Button(
                        onClick = {
                            if (tokenManager?.isLogoutInProgress() == true) {
                                loginError = if (currentLang == "BN") "লগআউট শেষ হচ্ছে। এক মুহূর্ত পরে আবার চেষ্টা করুন।" else "Finishing sign out. Try again in a moment."
                                return@Button
                            }
                            val normalizedMobile = com.safa.account.data.api.MobileNumberNormalizer.normalize(mobileInput)
                            val normalizedPin = com.safa.account.data.api.MobileNumberNormalizer.normalizePin(pinInput)
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
                                    loginError = result?.takeIf { it.isNotBlank() }
                                        ?: viewModel.t("invalid_credentials", currentLang)
                                    setupProbeGeneration++
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
}