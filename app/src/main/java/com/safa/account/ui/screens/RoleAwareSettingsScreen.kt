package com.safa.account.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwitchAccount
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.safa.account.ui.launchBiometricPrompt
import com.safa.account.ui.viewmodel.SafaViewModel

private enum class SafeSettingsPage { MAIN, CURRENCY, BRANDING, LANGUAGE, PIN }

private fun roleLabel(role: String, lang: String): String = when (role.lowercase()) {
    "superadmin" -> if (lang == "BN") "সুপার অ্যাডমিন" else "Super Admin"
    "admin" -> if (lang == "BN") "অ্যাডমিন" else "Admin"
    "manager" -> if (lang == "BN") "বিজনেস ইউজার" else "Business User"
    else -> if (lang == "BN") "নরমাল ইউজার" else "Normal User"
}

@Composable
fun RoleAwareSettingsScreen(viewModel: SafaViewModel) {
    val lang by viewModel.currentLanguage.collectAsStateWithLifecycle()
    val operator by viewModel.currentOperator.collectAsStateWithLifecycle()
    val darkMode by viewModel.isDarkMode.collectAsStateWithLifecycle()
    val rateMode by viewModel.isRateBasedModeEnabled.collectAsStateWithLifecycle()
    val supplierRate by viewModel.isSupplierRateEnabled.collectAsStateWithLifecycle()
    val walletRate by viewModel.isWalletRateEnabled.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val normalizedRole = operator?.role?.lowercase().orEmpty()
    val isAdmin = normalizedRole == "admin" || normalizedRole == "superadmin"
    var page by remember { mutableStateOf(SafeSettingsPage.MAIN) }
    var showUsers by remember { mutableStateOf(false) }
    var showAccountSwitch by remember { mutableStateOf(false) }
    var showAccountSharing by remember { mutableStateOf(false) }

    if (showUsers) {
        UserManagementDialog(viewModel = viewModel, onDismiss = { showUsers = false })
    }
    if (showAccountSwitch) {
        AccountSwitchDialog(viewModel = viewModel, onDismiss = { showAccountSwitch = false })
    }
    if (showAccountSharing) {
        AccountSharingDialog(viewModel = viewModel, onDismiss = { showAccountSharing = false })
    }

    when (page) {
        SafeSettingsPage.CURRENCY -> if (isAdmin) CurrencyPage(viewModel) { page = SafeSettingsPage.MAIN } else page = SafeSettingsPage.MAIN
        SafeSettingsPage.BRANDING -> if (isAdmin) BrandingPage(viewModel) { page = SafeSettingsPage.MAIN } else page = SafeSettingsPage.MAIN
        SafeSettingsPage.LANGUAGE -> LanguagePage(viewModel) { page = SafeSettingsPage.MAIN }
        SafeSettingsPage.PIN -> PinChangePage(viewModel) { page = SafeSettingsPage.MAIN }
        SafeSettingsPage.MAIN -> LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(top = 18.dp, bottom = 24.dp)
        ) {
            item {
                Text(
                    text = if (lang == "BN") "সেটিংস" else "Settings",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        androidx.compose.material3.Icon(Icons.Default.AccountCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(operator?.username.orEmpty().ifBlank { if (lang == "BN") "SAFA ইউজার" else "SAFA User" }, fontWeight = FontWeight.Bold)
                            Text(roleLabel(operator?.role.orEmpty(), lang), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                        androidx.compose.material3.Icon(Icons.Default.Badge, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
                    }
                }
            }

            item {
                Text(
                    if (lang == "BN") "ব্যবসার অ্যাকাউন্ট" else "Business account",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            item {
                SettingsMenuItem(
                    Icons.Default.SwitchAccount,
                    if (lang == "BN") "অ্যাকাউন্ট পরিবর্তন" else "Change account"
                ) { showAccountSwitch = true }
            }
            item {
                SettingsMenuItem(
                    Icons.Default.Share,
                    if (lang == "BN") "আমার অ্যাকাউন্টে এক্সেস দিন" else "Share my account access"
                ) { showAccountSharing = true }
            }

            if (isAdmin) {
                item {
                    Text(
                        if (lang == "BN") "অ্যাডমিন কনফিগারেশন" else "Admin configuration",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                item {
                    SettingsToggleCard(
                        title = if (lang == "BN") "রেট ভিত্তিক মোড" else "Rate-based mode",
                        subtitle = if (lang == "BN") "ব্যবসার রেট গণনা সক্রিয় করুন" else "Enable business rate calculations",
                        checked = rateMode,
                        onCheckedChange = viewModel::setRateBasedModeEnabled
                    )
                }
                if (rateMode) {
                    item {
                        SettingsToggleCard(
                            title = if (lang == "BN") "সাপ্লায়ার রেট" else "Supplier rate",
                            subtitle = if (lang == "BN") "সাপ্লায়ার রেট ফিচার" else "Supplier rate feature",
                            checked = supplierRate,
                            onCheckedChange = viewModel::setSupplierRateEnabled
                        )
                    }
                    item {
                        SettingsToggleCard(
                            title = if (lang == "BN") "ওয়ালেট রেট" else "Wallet rate",
                            subtitle = if (lang == "BN") "ওয়ালেট রেট ফিচার" else "Wallet rate feature",
                            checked = walletRate,
                            onCheckedChange = viewModel::setWalletRateEnabled
                        )
                    }
                }
                item { SettingsMenuItem(Icons.Default.MonetizationOn, if (lang == "BN") "কারেন্সি ও প্রতীক" else "Currency & Symbols") { page = SafeSettingsPage.CURRENCY } }
                item { SettingsMenuItem(Icons.Default.Image, if (lang == "BN") "অ্যাপ লোগো ও নাম" else "App Logo & Name") { page = SafeSettingsPage.BRANDING } }
                item { SettingsMenuItem(Icons.Default.People, if (lang == "BN") "ইউজার ম্যানেজমেন্ট" else "User Management") { showUsers = true } }
            }

            item {
                Text(
                    if (lang == "BN") "ব্যক্তিগত ও নিরাপত্তা" else "Personal & security",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            item { SettingsMenuItem(Icons.Default.Language, if (lang == "BN") "ভাষা" else "Language") { page = SafeSettingsPage.LANGUAGE } }
            item { SettingsMenuItem(Icons.Default.Lock, if (lang == "BN") "পিন পরিবর্তন" else "Change PIN") { page = SafeSettingsPage.PIN } }
            item {
                SettingsToggleCard(
                    title = if (lang == "BN") "ডার্ক মোড" else "Dark mode",
                    subtitle = if (lang == "BN") "ডিভাইসে থিম সংরক্ষণ করুন" else "Store the theme preference on this device",
                    checked = darkMode,
                    icon = Icons.Default.DarkMode,
                    onCheckedChange = { viewModel.setDarkMode(it) }
                )
            }
            item {
                val biometricEnabled = operator?.isBiometricEnabled == true
                SettingsToggleCard(
                    title = if (lang == "BN") "বায়োমেট্রিক কুইক আনলক" else "Biometric quick unlock",
                    subtitle = if (lang == "BN") "বিদ্যমান সার্ভার সেশন যাচাইয়ের আগে ডিভাইস বায়োমেট্রিক ব্যবহার করুন" else "Use device biometrics before restoring the existing server session",
                    checked = biometricEnabled,
                    icon = Icons.Default.Fingerprint,
                    onCheckedChange = { enabled ->
                        if (!enabled) {
                            operator?.copy(isBiometricEnabled = false)?.let(viewModel::updateOperator)
                            viewModel.setBiometricEnabled(false)
                        } else {
                            launchBiometricPrompt(
                                context = context,
                                lang = lang,
                                onSuccess = {
                                    operator?.copy(isBiometricEnabled = true)?.let(viewModel::updateOperator)
                                    viewModel.setBiometricEnabled(true)
                                },
                                onError = { }
                            )
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun SettingsToggleCard(
    title: String,
    subtitle: String,
    checked: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Default.Tune,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            androidx.compose.material3.Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}
