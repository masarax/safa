package com.safa.account.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.safa.account.ui.viewmodel.HundiViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    viewModel: HundiViewModel,
    modifier: Modifier = Modifier
) {
    val operators by viewModel.operators.collectAsStateWithLifecycle()
    val currentLang by viewModel.currentLanguage.collectAsStateWithLifecycle()

    var mobileInput by remember { mutableStateOf("") }
    var pinInput by remember { mutableStateOf("") }
    var loginError by remember { mutableStateOf<String?>(null) }
    var showActivationFlow by remember { mutableStateOf(false) }

    // Activation form fields
    var actName by remember { mutableStateOf("") }
    var actEmail by remember { mutableStateOf("") }
    var actMobile by remember { mutableStateOf("") }
    var actPin by remember { mutableStateOf("") }
    var actError by remember { mutableStateOf<String?>(null) }

    // If database is completely clean (zero operators), automatically prompt SuperAdmin activation
    LaunchedEffect(operators) {
        if (operators.isEmpty()) {
            showActivationFlow = true
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        // Top Language Switcher Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.End
        ) {
            FilledTonalButton(
                onClick = { viewModel.toggleLanguage() },
                modifier = Modifier.testTag("auth_lang_toggle")
            ) {
                Text(
                    text = "[ EN | বাংলা ]",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // App visual header icon
            Card(
                shape = CircleShape,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.size(80.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Lock",
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Text(
                text = viewModel.t("login_title"),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp
                ),
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )

            // Mobile + PIN Input Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Mobile Number Input
                    OutlinedTextField(
                        value = mobileInput,
                        onValueChange = {
                            mobileInput = it
                            loginError = null
                        },
                        label = { Text(viewModel.t("mobile_number")) },
                        placeholder = { Text(viewModel.t("enter_mobile_ph")) },
                        leadingIcon = {
                            Icon(Icons.Default.Phone, contentDescription = "Mobile")
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("login_mobile_input"),
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Security PIN Input
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = {
                            if (it.length <= 4) {
                                pinInput = it
                                loginError = null
                            }
                        },
                        label = { Text(viewModel.t("enter_pin")) },
                        leadingIcon = {
                            Icon(Icons.Default.Security, contentDescription = "PIN")
                        },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("login_pin_input"),
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Error feedback
                    AnimatedVisibility(
                        visible = loginError != null,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Text(
                            text = loginError ?: "",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            textAlign = TextAlign.Center
                        )
                    }

                    // Biometric option if matching operator has biometric enabled
                    val matchingOp = remember(mobileInput, operators) {
                        operators.find { it.mobile == mobileInput.trim() && it.mobile.isNotBlank() }
                    }
                    if (matchingOp != null && matchingOp.isBiometricEnabled) {
                        com.safa.account.ui.BiometricTriggerButton(
                            lang = currentLang,
                            onSuccess = { viewModel.loginWithBiometric(matchingOp) },
                            onError = { loginError = it }
                        )
                    }

                    // Login Button
                    Button(
                        onClick = {
                            if (mobileInput.isBlank()) {
                                loginError = if (currentLang == "BN") "মোবাইল নম্বর দিন" else "Please enter mobile number"
                                return@Button
                            }
                            if (pinInput.length < 4) {
                                loginError = viewModel.t("pin_incorrect")
                                return@Button
                            }
                            viewModel.loginWithMobileAndPin(mobileInput, pinInput) { success, result ->
                                if (!success) {
                                    if (result == "NEEDS_ACTIVATION") {
                                        actMobile = mobileInput
                                        actPin = pinInput
                                        showActivationFlow = true
                                    } else {
                                        loginError = result ?: viewModel.t("invalid_credentials")
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("login_submit_btn"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = viewModel.t("login_button"),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    // SuperAdmin Activation Button
                    TextButton(
                        onClick = {
                            actMobile = mobileInput
                            actPin = pinInput
                            showActivationFlow = true
                        },
                        modifier = Modifier.testTag("trigger_super_admin_activation")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AdminPanelSettings,
                            contentDescription = "",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = viewModel.t("activate_super_admin"),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }
    }

    // SuperAdmin 1-Time Activation Dialog / Sheet
    if (showActivationFlow) {
        AlertDialog(
            onDismissRequest = {
                if (operators.isNotEmpty()) showActivationFlow = false
            },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AdminPanelSettings,
                        contentDescription = "",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = viewModel.t("activation_title"),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = viewModel.t("activation_desc"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )

                    // Full Name
                    OutlinedTextField(
                        value = actName,
                        onValueChange = { actName = it; actError = null },
                        label = { Text(viewModel.t("full_name")) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("act_name_input"),
                        shape = RoundedCornerShape(10.dp)
                    )

                    // Email Address
                    OutlinedTextField(
                        value = actEmail,
                        onValueChange = { actEmail = it; actError = null },
                        label = { Text(viewModel.t("email")) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("act_email_input"),
                        shape = RoundedCornerShape(10.dp)
                    )

                    // Mobile Number
                    OutlinedTextField(
                        value = actMobile,
                        onValueChange = { actMobile = it; actError = null },
                        label = { Text(viewModel.t("mobile_number")) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("act_mobile_input"),
                        shape = RoundedCornerShape(10.dp)
                    )

                    // 4-Digit Security PIN
                    OutlinedTextField(
                        value = actPin,
                        onValueChange = {
                            if (it.length <= 4) {
                                actPin = it
                                actError = null
                            }
                        },
                        label = { Text(viewModel.t("enter_pin")) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("act_pin_input"),
                        shape = RoundedCornerShape(10.dp)
                    )

                    if (actError != null) {
                        Text(
                            text = actError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (actName.isBlank()) {
                            actError = if (currentLang == "BN") "পূর্ণ নাম দিন" else "Enter full name"
                            return@Button
                        }
                        if (actMobile.isBlank()) {
                            actError = if (currentLang == "BN") "মোবাইল নম্বর দিন" else "Enter mobile number"
                            return@Button
                        }
                        if (actPin.length != 4 || !actPin.all { it.isDigit() }) {
                            actError = if (currentLang == "BN") "পিন অবশ্যই ৪ ডিজিটের হতে হবে" else "PIN must be 4 digits"
                            return@Button
                        }

                        viewModel.activateSuperAdmin(
                            name = actName,
                            email = actEmail,
                            mobile = actMobile,
                            pin = actPin
                        ) {
                            showActivationFlow = false
                        }
                    },
                    modifier = Modifier.testTag("act_submit_btn")
                ) {
                    Text(viewModel.t("complete_activation"))
                }
            },
            dismissButton = {
                if (operators.isNotEmpty()) {
                    TextButton(onClick = { showActivationFlow = false }) {
                        Text(if (currentLang == "BN") "বাতিল" else "Cancel")
                    }
                }
            }
        )
    }
}
