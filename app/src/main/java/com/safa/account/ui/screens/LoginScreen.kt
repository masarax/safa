package com.safa.account.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safa.account.data.model.OperatorAccount
import com.safa.account.ui.viewmodel.HundiViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    viewModel: HundiViewModel,
    modifier: Modifier = Modifier
) {
    val operators by viewModel.operators.collectAsStateWithLifecycle()
    val selectedOperator by viewModel.selectedLoginOperator.collectAsStateWithLifecycle()
    val pinBuffer by viewModel.pinBuffer.collectAsStateWithLifecycle()
    val pinError by viewModel.pinError.collectAsStateWithLifecycle()
    val currentLang by viewModel.currentLanguage.collectAsStateWithLifecycle()

    var showOpSelector by remember { mutableStateOf(false) }

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
                    text = if (currentLang == "BN") "ENGLISH (EN)" else "বাংলা (BN)",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // App visual header
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

            if (selectedOperator == null) {
                // If no operator is selected yet, show selector buttons
                Text(
                    text = viewModel.t("select_operator"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline
                )

                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    operators.forEach { op ->
                        Card(
                            onClick = { viewModel.selectLoginOperator(op) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("op_select_${op.username.lowercase()}"),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    if (op.role == "Owner") {
                                        Icon(Icons.Filled.Star, contentDescription = "Owner", tint = Color(0xFFFFD700))
                                    }
                                    Text(
                                        text = op.username,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                }
                                AssistChip(
                                    onClick = {},
                                    label = { Text(if (op.role == "Owner") viewModel.t("role_owner") else viewModel.t("role_staff")) }
                                )
                            }
                        }
                    }
                }
            } else {
                // Operator is selected, ask for PIN code
                Text(
                    text = "${viewModel.t("enter_pin")} (${selectedOperator?.username})",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )

                // PIN Digits Bullets Indicator
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (i in 0 until 4) {
                        val active = pinBuffer.length > i
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(
                                    if (active) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant
                                )
                        )
                    }
                }

                if (selectedOperator?.isBiometricEnabled == true) {
                    com.safa.account.ui.BiometricTriggerButton(
                        lang = currentLang,
                        onSuccess = { viewModel.loginWithBiometric(selectedOperator!!) },
                        onError = { viewModel.setPinError(it) }
                    )
                }

                // PIN error feedback
                AnimatedVisibility(
                    visible = pinError != null,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Text(
                        text = pinError ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        modifier = Modifier.padding(vertical = 4.dp),
                        textAlign = TextAlign.Center
                    )
                }

                // 4x3 keypad
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(280.dp)
                ) {
                    val keys = listOf(
                        listOf("1", "2", "3"),
                        listOf("4", "5", "6"),
                        listOf("7", "8", "9"),
                        listOf("Clear", "0", "Delete")
                    )

                    keys.forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            row.forEach { key ->
                                val isAction = key == "Clear" || key == "Delete"
                                Button(
                                    onClick = {
                                        when (key) {
                                            "Clear" -> viewModel.selectLoginOperator(selectedOperator!!)
                                            "Delete" -> viewModel.deletePinDigit()
                                            else -> viewModel.appendPinDigit(key[0])
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(56.dp)
                                        .testTag("pin_key_$key"),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isAction) MaterialTheme.colorScheme.secondaryContainer
                                        else MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = if (isAction) MaterialTheme.colorScheme.onSecondaryContainer
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        text = if (key == "Clear") "CLR" else if (key == "Delete") "DEL" else key,
                                        fontSize = if (isAction) 12.sp else 18.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    // Back to operator choosing button
                    TextButton(
                        onClick = { viewModel.logout() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp)
                            .testTag("back_to_operators")
                    ) {
                        Text(
                            text = if (currentLang == "BN") "← অ্যাকাউন্ট পরিবর্তন করুন" else "← Change Operator",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }
    }
}
