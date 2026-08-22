package com.safa.account.ui.screens
import com.safa.account.ui.localization.AndroidStringCatalog

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
                modifier = Modifier.heightIn(min = 48.dp).testTag("auth_lang_toggle")
            ) {
                Text(
                    text = AndroidStringCatalog.get(currentLang, "inline_loginscreen_5b8bea740d"),
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
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }

            Text(
                text = AndroidStringCatalog.get(currentLang, "inline_loginscreen_c3b058ac5d"),
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
                            text = AndroidStringCatalog.get(
                                currentLang,
                                if (databasePhase) "setup_database_required" else "setup_superadmin_required"
                            ),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = AndroidStringCatalog.get(
                                currentLang,
                                if (databasePhase) "setup_database_explanation" else "setup_superadmin_explanation"
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                        Button(
                            onClick = {
                                val setupUrl = runCatching { FirstRunSetupClient.webSetupUrl(apiBaseUrl) }.getOrNull()
                                if (setupUrl == null) {
                                    loginError = AndroidStringCatalog.get(currentLang, "inline_loginscreen_ef5009f2d8")
                                } else {
                                    runCatching {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(setupUrl)))
                                    }.onFailure {
                                        loginError = AndroidStringCatalog.get(currentLang, "inline_loginscreen_173357f030")
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(50.dp).testTag("first_run_setup_action"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                AndroidStringCatalog.get(
                                    currentLang,
                                    if (databasePhase) "setup_database_action" else "setup_superadmin_action"
                                ),
                                fontWeight = FontWeight.Bold
                            )
                        }
                        OutlinedButton(
                            onClick = { setupProbeGeneration++ },
                            modifier = Modifier.fillMaxWidth().testTag("first_run_setup_retry")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(AndroidStringCatalog.get(currentLang, "inline_loginscreen_fff0eecd17"))
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
                        label = { Text(AndroidStringCatalog.get(currentLang, "inline_loginscreen_bc6288dec7")) },
                        placeholder = { Text("01700000000") },
                        leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
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
                        label = { Text(AndroidStringCatalog.get(currentLang, "inline_loginscreen_dd561d689f")) },
                        leadingIcon = { Icon(Icons.Default.Security, contentDescription = null) },
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
                                        loginError = AndroidStringCatalog.get(currentLang, "inline_loginscreen_9d00c1fa78")
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
                                        loginError = AndroidStringCatalog.get(currentLang, "session_verification_failed")
                                    }
                                }
                            },
                            onError = { loginError = it }
                        )
                    }

                    Button(
                        onClick = {
                            if (tokenManager?.isLogoutInProgress() == true) {
                                loginError = AndroidStringCatalog.get(currentLang, "inline_loginscreen_48d93b1c66")
                                return@Button
                            }
                            val normalizedMobile = com.safa.account.data.api.MobileNumberNormalizer.normalize(mobileInput)
                            val normalizedPin = com.safa.account.data.api.MobileNumberNormalizer.normalizePin(pinInput)
                            if (normalizedMobile.isBlank()) {
                                loginError = AndroidStringCatalog.get(currentLang, "inline_loginscreen_4ca0f0ec8e")
                                return@Button
                            }
                            if (normalizedPin.length != 6) {
                                loginError = AndroidStringCatalog.get(currentLang, "inline_loginscreen_7320bb2444")
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
                                text = AndroidStringCatalog.get(currentLang, "inline_loginscreen_0e6bed32c2"),
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