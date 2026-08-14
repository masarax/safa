package com.safa.account.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import coil.compose.AsyncImage
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safa.account.data.model.OperatorAccount
import com.safa.account.ui.viewmodel.SafaViewModel
import kotlinx.coroutines.launch

enum class SettingsSubpage {
    MAIN, CURRENCY, BRANDING, LANGUAGE, USER_MGT, PIN_CHANGE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SafaViewModel,
    modifier: Modifier = Modifier
) {
    var currentPage by remember { mutableStateOf(SettingsSubpage.MAIN) }

    if (currentPage != SettingsSubpage.MAIN) {
        androidx.activity.compose.BackHandler {
            currentPage = SettingsSubpage.MAIN
        }
    }

    // Use a custom top bar inner handler for subpages if needed, 
    // but the global top bar handles the scaffold. 
    // We will just show back buttons in subpages.

    when (currentPage) {
        SettingsSubpage.MAIN -> SettingsMainPage(viewModel) { currentPage = it }
        SettingsSubpage.CURRENCY -> CurrencyPage(viewModel) { currentPage = SettingsSubpage.MAIN }
        SettingsSubpage.BRANDING -> BrandingPage(viewModel) { currentPage = SettingsSubpage.MAIN }
        SettingsSubpage.LANGUAGE -> LanguagePage(viewModel) { currentPage = SettingsSubpage.MAIN }
        SettingsSubpage.USER_MGT -> UserManagementPage(viewModel) { currentPage = SettingsSubpage.MAIN }
        SettingsSubpage.PIN_CHANGE -> PinChangePage(viewModel) { currentPage = SettingsSubpage.MAIN }
    }
}

@Composable
fun SettingsMainPage(viewModel: SafaViewModel, onNavigate: (SettingsSubpage) -> Unit) {
    val lang by viewModel.currentLanguage.collectAsStateWithLifecycle()
    val activeOperator by viewModel.currentOperator.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp)
    ) {
        item {
            Text(
                text = if (lang == "BN") "সেটিংস" else "Settings",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        // Active User Header Profile Card (With Integrated Account Dropdown if multiple accounts exist)
        item {
            val operators by viewModel.operators.collectAsStateWithLifecycle()
            var showAccountDropdown by remember { mutableStateOf(false) }
            val hasMultipleAccounts = operators.size > 1

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .then(
                        if (hasMultipleAccounts) {
                            Modifier.clickable { showAccountDropdown = !showAccountDropdown }
                        } else {
                            Modifier
                        }
                    ),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                ),
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                )
            ) {
                Box {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = activeOperator?.username ?: "Staff Account",
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(top = 2.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (activeOperator?.role == "Owner" || activeOperator?.role == "SuperAdmin") 
                                                Color(0xFF2E7D32).copy(alpha = 0.15f) 
                                            else 
                                                MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
                                        )
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = activeOperator?.role?.uppercase() ?: "STAFF",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 9.sp
                                        ),
                                        color = if (activeOperator?.role == "Owner" || activeOperator?.role == "SuperAdmin") Color(0xFF2E7D32) else MaterialTheme.colorScheme.secondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    text = "ID: #${activeOperator?.id ?: 1}",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = MaterialTheme.colorScheme.outline,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        if (hasMultipleAccounts) {
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Switch Account",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    if (hasMultipleAccounts) {
                        DropdownMenu(
                            expanded = showAccountDropdown,
                            onDismissRequest = { showAccountDropdown = false },
                            modifier = Modifier
                                .fillMaxWidth(0.9f)
                                .background(MaterialTheme.colorScheme.surface)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        ) {
                            Text(
                                text = if (lang == "BN") "অ্যাকাউন্ট সুইচার (১-ট্যাপ)" else "Switch Account",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                            Divider()
                            operators.forEach { op ->
                                val isCurrent = op.id == activeOperator?.id
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (isCurrent) Icons.Default.CheckCircle else Icons.Default.AccountCircle,
                                                contentDescription = null,
                                                tint = if (isCurrent) Color(0xFF2E7D32) else MaterialTheme.colorScheme.outline,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Column {
                                                Text(
                                                    text = op.username,
                                                    style = MaterialTheme.typography.bodyMedium.copy(
                                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                                                    )
                                                )
                                                Text(
                                                    text = "${op.role} • #${op.id}",
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontSize = 10.sp,
                                                        color = MaterialTheme.colorScheme.outline
                                                    )
                                                )
                                            }
                                        }
                                    },
                                    onClick = {
                                        showAccountDropdown = false
                                        viewModel.requestOperatorSwitch(op)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Section 1: System Settings Category
        item {
            Text(
                text = if (lang == "BN") "সিস্টেম কনফিগারেশন" else "System Configuration",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                ),
                color = MaterialTheme.colorScheme.outline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
            )
        }

        item {
            val isRateEnabled by viewModel.isRateBasedModeEnabled.collectAsStateWithLifecycle()
            Surface(
                onClick = { viewModel.setRateBasedModeEnabled(!isRateEnabled) },
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Calculate,
                            contentDescription = "",
                            tint = if (isRateEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = if (lang == "BN") "রেট ভিত্তিক হিসাব মোড" else "Rate-Based Mode",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (lang == "BN") "সাপ্লায়ার ক্রয় রেট ও প্রফিট মার্জিন গণনাকারী মোড" else "Calculate supplier buying rates & profit margins",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                color = MaterialTheme.colorScheme.outline,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Switch(
                        checked = isRateEnabled,
                        onCheckedChange = { viewModel.setRateBasedModeEnabled(it) }
                    )
                }
            }
        }
        
        item {
            val isSupplierRateEnabled by viewModel.isSupplierRateEnabled.collectAsStateWithLifecycle()
            val isRateEnabled by viewModel.isRateBasedModeEnabled.collectAsStateWithLifecycle()
            if (isRateEnabled) {
                Surface(
                    onClick = { viewModel.setSupplierRateEnabled(!isSupplierRateEnabled) },
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Business,
                                contentDescription = "",
                                tint = if (isSupplierRateEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(20.dp)
                            )
                            Column {
                                Text(
                                    text = if (lang == "BN") "সাপ্লায়ার রেট" else "Supplier Rate",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (lang == "BN") "সাপ্লায়ারদের জন্য কাস্টম রেট" else "Enable supplier custom rates",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                    color = MaterialTheme.colorScheme.outline,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Switch(
                            checked = isSupplierRateEnabled,
                            onCheckedChange = { viewModel.setSupplierRateEnabled(it) }
                        )
                    }
                }
            }
        }

        item {
            val isWalletRateEnabled by viewModel.isWalletRateEnabled.collectAsStateWithLifecycle()
            val isRateEnabled by viewModel.isRateBasedModeEnabled.collectAsStateWithLifecycle()
            if (isRateEnabled) {
                Surface(
                    onClick = { viewModel.setWalletRateEnabled(!isWalletRateEnabled) },
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountBalanceWallet,
                                contentDescription = "",
                                tint = if (isWalletRateEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(20.dp)
                            )
                            Column {
                                Text(
                                    text = if (lang == "BN") "ওয়ালেট রেট" else "Wallet Rate",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (lang == "BN") "ওয়ালেট রিচার্জের জন্য কাস্টম রেট" else "Enable wallet custom rates",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                    color = MaterialTheme.colorScheme.outline,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Switch(
                            checked = isWalletRateEnabled,
                            onCheckedChange = { viewModel.setWalletRateEnabled(it) }
                        )
                    }
                }
            }
        }

        item {
            SettingsMenuItem(
                icon = Icons.Default.MonetizationOn,
                title = if (lang == "BN") "কারেন্সি ও প্রতীক" else "Currency & Symbols",
                onClick = { onNavigate(SettingsSubpage.CURRENCY) }
            )
        }

        item {
            SettingsMenuItem(
                icon = Icons.Default.Image,
                title = if (lang == "BN") "লোগো ও নাম" else "App Logo & Name",
                onClick = { onNavigate(SettingsSubpage.BRANDING) }
            )
        }

        item {
            SettingsMenuItem(
                icon = Icons.Default.Language,
                title = if (lang == "BN") "ভাষা" else "Language",
                onClick = { onNavigate(SettingsSubpage.LANGUAGE) }
            )
        }

        // Section 2: Security and Accounts Category
        item {
            Text(
                text = if (lang == "BN") "অপারেটর ও নিরাপত্তা" else "Operator & Security",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                ),
                color = MaterialTheme.colorScheme.outline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
            )
        }

        if (activeOperator?.role == "SuperAdmin") {
            item {
                SettingsMenuItem(
                    icon = Icons.Default.People,
                    title = viewModel.t("manage_operators"),
                    onClick = { onNavigate(SettingsSubpage.USER_MGT) }
                )
            }
        }

        item {
            SettingsMenuItem(
                icon = Icons.Default.Lock,
                title = if (lang == "BN") "পিন পরিবর্তন" else "Change PIN",
                onClick = { onNavigate(SettingsSubpage.PIN_CHANGE) }
            )
        }

        item {
            val activeOp by viewModel.currentOperator.collectAsStateWithLifecycle()
            val isBioEnabled = activeOp?.isBiometricEnabled ?: false
            val context = androidx.compose.ui.platform.LocalContext.current
            
            val toggleBiometric = { targetChecked: Boolean ->
                if (targetChecked) {
                    com.safa.account.ui.launchBiometricPrompt(
                        context = context,
                        lang = lang,
                        onSuccess = {
                            val updated = activeOp?.copy(isBiometricEnabled = true)
                            if (updated != null) {
                                viewModel.updateOperator(updated)
                            }
                            viewModel.setBiometricEnabled(true)
                        },
                        onError = { /* Biometric cancelled or failed */ }
                    )
                } else {
                    val updated = activeOp?.copy(isBiometricEnabled = false)
                    if (updated != null) {
                        viewModel.updateOperator(updated)
                    }
                    viewModel.setBiometricEnabled(false)
                }
            }

            Surface(
                onClick = { toggleBiometric(!isBioEnabled) },
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fingerprint,
                            contentDescription = "",
                            tint = if (isBioEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = if (lang == "BN") "ফিঙ্গর প্রিন্ট লাগাও" else "Fingerprint Lock",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (lang == "BN") "অপেরেশন সফল করতে মোবাইল এর লকস্ক্রিন লক ব্যবহার করি" else "Use mobile lock screen fingerprint to verify actions",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                color = MaterialTheme.colorScheme.outline,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Switch(
                        checked = isBioEnabled,
                        onCheckedChange = { checked ->
                            toggleBiometric(checked)
                        }
                    )
                }
            }
        }

        // Modern logout card
        item {
            Button(
                onClick = { viewModel.logout() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .height(42.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                    contentColor = MaterialTheme.colorScheme.error
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f)),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ExitToApp,
                    contentDescription = "",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (lang == "BN") "লগআউট" else "Logout Account",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Live Dynamic App Name & Version Footer
        item {
            val customAppName by viewModel.customAppName.collectAsStateWithLifecycle()
            val appVersion by viewModel.appVersion.collectAsStateWithLifecycle()
            val customAppLogo by viewModel.customAppLogo.collectAsStateWithLifecycle()
            val customAppLogoUri by viewModel.customAppLogoUri.collectAsStateWithLifecycle()

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp, bottom = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val logoUri = customAppLogoUri ?: if (customAppLogo.startsWith("content://") || customAppLogo.startsWith("file://") || customAppLogo.startsWith("http")) customAppLogo else null
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    if (logoUri != null) {
                        AsyncImage(
                            model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                                .data(logoUri)
                                .crossfade(true)
                                .build(),
                            contentDescription = "Logo",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    } else {
                        Text(text = customAppLogo, fontSize = 18.sp)
                    }
                }
                Text(
                    text = customAppName,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (lang == "BN") "ভার্সন $appVersion" else "Version $appVersion",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun SettingsMenuItem(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha=0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = "", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(Icons.Default.ChevronRight, contentDescription = "", tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(20.dp))
        }
    }
}

// ... Additional Subpages will be implemented in a follow-up call to manage string size ...

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurrencyPage(viewModel: SafaViewModel, onBack: () -> Unit) {
    val lang by viewModel.currentLanguage.collectAsStateWithLifecycle()
    val baseForeignCurrency by viewModel.selectedForeignCurrency.collectAsStateWithLifecycle()
    val baseLocalCurrency by viewModel.selectedLocalCurrency.collectAsStateWithLifecycle()

    var selectedForeignCurrency by remember { mutableStateOf(baseForeignCurrency) }
    var selectedLocalCurrency by remember { mutableStateOf(baseLocalCurrency) }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(16.dp)) {
        PageHeader(title = if (lang == "BN") "কারেন্সি ও প্রতীক" else "Currency & Symbols", icon = Icons.Default.MonetizationOn, onBack = onBack)

        Card(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = selectedForeignCurrency,
                    onValueChange = { selectedForeignCurrency = it },
                    label = { Text(if (lang == "BN") "বৈদেশিক কারেন্সি (যেমন: SAR)" else "Foreign Currency (e.g. SAR)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = selectedLocalCurrency,
                    onValueChange = { selectedLocalCurrency = it },
                    label = { Text(if (lang == "BN") "লোকাল কারেন্সি (যেমন: BDT)" else "Local Currency (e.g. BDT)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = { 
                        viewModel.updateCurrenciesOnServer(selectedLocalCurrency, selectedForeignCurrency)
                        onBack()
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(if (lang == "BN") "সংরক্ষণ করুন" else "Save Changes")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrandingPage(viewModel: SafaViewModel, onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val lang by viewModel.currentLanguage.collectAsStateWithLifecycle()
    val customAppName by viewModel.customAppName.collectAsStateWithLifecycle()
    val customAppLogo by viewModel.customAppLogo.collectAsStateWithLifecycle()
    val customAppLogoUri by viewModel.customAppLogoUri.collectAsStateWithLifecycle()

    var tempAppName by remember { mutableStateOf(customAppName) }
    var tempAppLogo by remember { mutableStateOf(customAppLogoUri ?: customAppLogo) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            tempAppLogo = it.toString()
            viewModel.uploadAppLogoToServer(context, it)
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(16.dp)) {
        PageHeader(title = if (lang == "BN") "ব্র্যান্ডিং" else "Branding", icon = Icons.Default.Palette, onBack = onBack)

        Card(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = tempAppName,
                    onValueChange = { tempAppName = it },
                    label = { Text(if (lang == "BN") "অ্যাপের নাম" else "App Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                // Logo Preview
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        if (tempAppLogo.startsWith("content://") || tempAppLogo.startsWith("file://") || tempAppLogo.startsWith("http")) {
                            AsyncImage(
                                model = coil.request.ImageRequest.Builder(context)
                                    .data(tempAppLogo)
                                    .crossfade(true)
                                    .size(256) // limit size to prevent OOM
                                    .build(),
                                contentDescription = "Logo",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                            )
                        } else {
                            Text(text = tempAppLogo, fontSize = 28.sp)
                        }
                    }
                    
                    Column(modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = tempAppLogo,
                            onValueChange = { tempAppLogo = it },
                            label = { Text(if (lang == "BN") "ইমোজি লোগো" else "Emoji Logo") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                }

                OutlinedButton(
                    onClick = { launcher.launch("image/*") },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.ImageSearch, contentDescription = "")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (lang == "BN") "ছবি আপলোড করুন" else "Upload Picture Logo")
                }

                Button(
                    onClick = { 
                        viewModel.updateCustomAppNameOnServer(tempAppName)
                        if (tempAppLogo.startsWith("content://") || tempAppLogo.startsWith("file://")) {
                            viewModel.uploadAppLogoToServer(context, Uri.parse(tempAppLogo))
                        } else if (tempAppLogo.startsWith("http")) {
                            viewModel.updateCustomAppLogoUri(tempAppLogo)
                            viewModel.updateConfigOnServer(mapOf("app_name" to tempAppName, "app_logo_url" to tempAppLogo))
                        } else {
                            viewModel.updateCustomAppLogo(tempAppLogo)
                            viewModel.updateCustomAppLogoUri(null)
                            viewModel.updateConfigOnServer(mapOf("app_name" to tempAppName, "app_logo" to tempAppLogo))
                        }
                        onBack()
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(if (lang == "BN") "সংরক্ষণ করুন" else "Save Changes")
                }
            }
        }
    }
}

@Composable
fun LanguagePage(viewModel: SafaViewModel, onBack: () -> Unit) {
    val lang by viewModel.currentLanguage.collectAsStateWithLifecycle()
    
    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(16.dp)) {
        PageHeader(title = if (lang == "BN") "ভাষা পরিবর্তন" else "Change Language", icon = Icons.Default.Language, onBack = onBack)

        Card(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { if (lang != "BN") viewModel.toggleLanguage() }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = lang == "BN", onClick = { if (lang != "BN") viewModel.toggleLanguage() })
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("বাংলা (Bengali)", style = MaterialTheme.typography.bodyLarge)
                }
                
                Divider()
                
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { if (lang != "EN") viewModel.toggleLanguage() }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = lang == "EN", onClick = { if (lang != "EN") viewModel.toggleLanguage() })
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("English", style = MaterialTheme.typography.bodyLarge)
                }
                
                Divider()

                OutlinedButton(
                    onClick = { /* Placeholder for adding new language via intent or future update */ },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (lang == "BN") "নতুন ভাষা যুক্ত করুন" else "Add Another Language")
                }
            }
        }
    }
}

@Composable
fun PageHeader(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector = androidx.compose.material.icons.Icons.Default.Settings,
    onBack: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onBack() }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    letterSpacing = (-0.3).sp
                ),
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun UserManagementPage(viewModel: SafaViewModel, onBack: () -> Unit) {
    val lang by viewModel.currentLanguage.collectAsStateWithLifecycle()
    val operators by viewModel.operators.collectAsStateWithLifecycle()
    val activeOperator by viewModel.currentOperator.collectAsStateWithLifecycle()

    var isAddingOperator by remember { mutableStateOf(false) }
    var expandedOperatorId by remember { mutableStateOf<Int?>(null) }
    var editingOperator by remember { mutableStateOf<com.safa.account.data.model.OperatorAccount?>(null) }
    var showDeleteConfirmByOp by remember { mutableStateOf<com.safa.account.data.model.OperatorAccount?>(null) }
    var managementError by remember { mutableStateOf<String?>(null) }

    if (activeOperator?.role != "SuperAdmin") {
        Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(16.dp)) {
            PageHeader(title = viewModel.t("manage_operators"), icon = Icons.Default.People, onBack = onBack)
            Text(
                if (lang == "BN") "শুধু SuperAdmin অপারেটর ব্যবস্থাপনা করতে পারবেন।" else "Only a SuperAdmin can manage operators.",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
        return
    }

    LaunchedEffect(Unit) {
        viewModel.fetchOperatorsFromServer()
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(16.dp)) {
        PageHeader(title = viewModel.t("manage_operators"), icon = Icons.Default.People, onBack = onBack)
        managementError?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
        }

        if (isAddingOperator) {
            var newName by remember { mutableStateOf("") }
            var newMobile by remember { mutableStateOf("") }
            var newEmail by remember { mutableStateOf("") }
            var newPin by remember { mutableStateOf("") }
            var newRole by remember { mutableStateOf("Staff") }
            var errorMsg by remember { mutableStateOf<String?>(null) }

            var canViewCustomers by remember { mutableStateOf(true) }
            var canAddCustomers by remember { mutableStateOf(true) }
            var canEditCustomers by remember { mutableStateOf(true) }
            var canDeleteCustomers by remember { mutableStateOf(true) }
            var canViewSuppliers by remember { mutableStateOf(true) }
            var canAddSuppliers by remember { mutableStateOf(true) }
            var canEditSuppliers by remember { mutableStateOf(true) }
            var canDeleteSuppliers by remember { mutableStateOf(true) }
            var canViewTransactions by remember { mutableStateOf(true) }
            var canAddTransactions by remember { mutableStateOf(true) }
            var canEditTransactions by remember { mutableStateOf(true) }
            var canDeleteTransactions by remember { mutableStateOf(true) }
            var canManageWallet by remember { mutableStateOf(true) }
            var canManageExpenses by remember { mutableStateOf(true) }
            var canViewReports by remember { mutableStateOf(true) }

            Card(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        if (lang == "BN") "নতুন অপারেটর তৈরি করুন" else "Create New Operator",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it; errorMsg = null },
                        label = { Text(viewModel.t("full_name")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = newMobile,
                        onValueChange = { newMobile = it; errorMsg = null },
                        label = { Text(viewModel.t("mobile_number")) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = newEmail,
                        onValueChange = { newEmail = it; errorMsg = null },
                        label = { Text(viewModel.t("email")) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = newPin,
                        onValueChange = {
                            val normalized = com.safa.account.data.api.MobileNumberNormalizer.normalizePin(it)
                            if (normalized.length <= 6) { newPin = normalized; errorMsg = null }
                        },
                        label = { Text(viewModel.t("enter_pin")) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(if (lang == "BN") "রোল" else "Role", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = newRole == "Manager", onClick = { newRole = "Manager" }, label = { Text("Manager") })
                        FilterChip(selected = newRole == "Staff", onClick = { newRole = "Staff" }, label = { Text("Staff") })
                    }

                    Text(
                        if (lang == "BN") "১৫টি দানাদার RBAC অনুমতিসমূহ" else "15 Granular RBAC Permissions",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )

                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = canViewCustomers, onClick = { canViewCustomers = !canViewCustomers }, label = { Text("View Cust", fontSize = 11.sp) })
                        FilterChip(selected = canAddCustomers, onClick = { canAddCustomers = !canAddCustomers }, label = { Text("Add Cust", fontSize = 11.sp) })
                        FilterChip(selected = canEditCustomers, onClick = { canEditCustomers = !canEditCustomers }, label = { Text("Edit Cust", fontSize = 11.sp) })
                        FilterChip(selected = canDeleteCustomers, onClick = { canDeleteCustomers = !canDeleteCustomers }, label = { Text("Del Cust", fontSize = 11.sp) })
                        FilterChip(selected = canViewSuppliers, onClick = { canViewSuppliers = !canViewSuppliers }, label = { Text("View Supp", fontSize = 11.sp) })
                        FilterChip(selected = canAddSuppliers, onClick = { canAddSuppliers = !canAddSuppliers }, label = { Text("Add Supp", fontSize = 11.sp) })
                        FilterChip(selected = canEditSuppliers, onClick = { canEditSuppliers = !canEditSuppliers }, label = { Text("Edit Supp", fontSize = 11.sp) })
                        FilterChip(selected = canDeleteSuppliers, onClick = { canDeleteSuppliers = !canDeleteSuppliers }, label = { Text("Del Supp", fontSize = 11.sp) })
                        FilterChip(selected = canViewTransactions, onClick = { canViewTransactions = !canViewTransactions }, label = { Text("View Tx", fontSize = 11.sp) })
                        FilterChip(selected = canAddTransactions, onClick = { canAddTransactions = !canAddTransactions }, label = { Text("Add Tx", fontSize = 11.sp) })
                        FilterChip(selected = canEditTransactions, onClick = { canEditTransactions = !canEditTransactions }, label = { Text("Edit Tx", fontSize = 11.sp) })
                        FilterChip(selected = canDeleteTransactions, onClick = { canDeleteTransactions = !canDeleteTransactions }, label = { Text("Del Tx", fontSize = 11.sp) })
                        FilterChip(selected = canManageWallet, onClick = { canManageWallet = !canManageWallet }, label = { Text("Wallet", fontSize = 11.sp) })
                        FilterChip(selected = canManageExpenses, onClick = { canManageExpenses = !canManageExpenses }, label = { Text("Expenses", fontSize = 11.sp) })
                        FilterChip(selected = canViewReports, onClick = { canViewReports = !canViewReports }, label = { Text("Reports", fontSize = 11.sp) })
                    }

                    if (errorMsg != null) {
                        Text(text = errorMsg!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
                        OutlinedButton(onClick = { isAddingOperator = false }, modifier = Modifier.weight(1f)) {
                            Text(if (lang == "BN") "বাতিল" else "Cancel", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Button(
                            onClick = {
                                if (newName.isBlank() || newMobile.isBlank() || newPin.length != 6) {
                                    errorMsg = if (lang == "BN") "সব তথ্য দিন এবং পিন ৬ সংখ্যার হতে হবে" else "Fill name, mobile, and 6-digit PIN"
                                    return@Button
                                }
                                val permsMap = mapOf(
                                    "can_view_customers" to canViewCustomers,
                                    "can_add_customers" to canAddCustomers,
                                    "can_edit_customers" to canEditCustomers,
                                    "can_delete_customers" to canDeleteCustomers,
                                    "can_view_suppliers" to canViewSuppliers,
                                    "can_add_suppliers" to canAddSuppliers,
                                    "can_edit_suppliers" to canEditSuppliers,
                                    "can_delete_suppliers" to canDeleteSuppliers,
                                    "can_view_transactions" to canViewTransactions,
                                    "can_add_transactions" to canAddTransactions,
                                    "can_edit_transactions" to canEditTransactions,
                                    "can_delete_transactions" to canDeleteTransactions,
                                    "can_manage_wallet" to canManageWallet,
                                    "can_manage_expenses" to canManageExpenses,
                                    "can_view_reports" to canViewReports
                                )
                                viewModel.createOperatorOnServer(
                                    name = newName,
                                    mobile = newMobile,
                                    email = newEmail,
                                    role = newRole,
                                    pin = newPin,
                                    permissionsMap = permsMap
                                ) { success, message ->
                                    if (success) isAddingOperator = false else errorMsg = message
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (lang == "BN") "সংরক্ষণ" else "Save", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        } else {
            Button(
                onClick = { isAddingOperator = true },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.PersonAdd, contentDescription = "")
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (lang == "BN") "নতুন অপারেটর যুক্ত করুন" else "Add New Operator")
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(operators, key = { it.id }) { op ->
                    val isExpanded = expandedOperatorId == op.id
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Box(
                                        modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Person, contentDescription = "", tint = MaterialTheme.colorScheme.primary)
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(op.username, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("Role: ${op.role} | ${op.mobile}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                                IconButton(onClick = { expandedOperatorId = if (isExpanded) null else op.id }) {
                                    Icon(if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = "Expand")
                                }
                            }

                            if (isExpanded) {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(if (lang == "BN") "১৫টি দানাদার RBAC অনুমতিসমূহ" else "15 RBAC Permissions", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        IconButton(onClick = { editingOperator = op }) {
                                            Icon(Icons.Default.Edit, contentDescription = "Edit Operator", tint = MaterialTheme.colorScheme.primary)
                                        }
                                        if (op.id != activeOperator?.id) {
                                            IconButton(onClick = { showDeleteConfirmByOp = op }) {
                                                Icon(Icons.Default.Delete, contentDescription = "Delete Operator", tint = MaterialTheme.colorScheme.error)
                                            }
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))

                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    FilterChip(selected = op.canViewCustomers, onClick = { viewModel.updateOperatorOnServer(op.copy(canViewCustomers = !op.canViewCustomers)) }, label = { Text("View Cust", fontSize = 11.sp) })
                                    FilterChip(selected = op.canAddCustomers, onClick = { viewModel.updateOperatorOnServer(op.copy(canAddCustomers = !op.canAddCustomers)) }, label = { Text("Add Cust", fontSize = 11.sp) })
                                    FilterChip(selected = op.canEditCustomers, onClick = { viewModel.updateOperatorOnServer(op.copy(canEditCustomers = !op.canEditCustomers)) }, label = { Text("Edit Cust", fontSize = 11.sp) })
                                    FilterChip(selected = op.canDeleteCustomers, onClick = { viewModel.updateOperatorOnServer(op.copy(canDeleteCustomers = !op.canDeleteCustomers)) }, label = { Text("Del Cust", fontSize = 11.sp) })
                                    FilterChip(selected = op.canViewSuppliers, onClick = { viewModel.updateOperatorOnServer(op.copy(canViewSuppliers = !op.canViewSuppliers)) }, label = { Text("View Supp", fontSize = 11.sp) })
                                    FilterChip(selected = op.canAddSuppliers, onClick = { viewModel.updateOperatorOnServer(op.copy(canAddSuppliers = !op.canAddSuppliers)) }, label = { Text("Add Supp", fontSize = 11.sp) })
                                    FilterChip(selected = op.canEditSuppliers, onClick = { viewModel.updateOperatorOnServer(op.copy(canEditSuppliers = !op.canEditSuppliers)) }, label = { Text("Edit Supp", fontSize = 11.sp) })
                                    FilterChip(selected = op.canDeleteSuppliers, onClick = { viewModel.updateOperatorOnServer(op.copy(canDeleteSuppliers = !op.canDeleteSuppliers)) }, label = { Text("Del Supp", fontSize = 11.sp) })
                                    FilterChip(selected = op.canViewTransactions, onClick = { viewModel.updateOperatorOnServer(op.copy(canViewTransactions = !op.canViewTransactions)) }, label = { Text("View Tx", fontSize = 11.sp) })
                                    FilterChip(selected = op.canAddTransactions, onClick = { viewModel.updateOperatorOnServer(op.copy(canAddTransactions = !op.canAddTransactions)) }, label = { Text("Add Tx", fontSize = 11.sp) })
                                    FilterChip(selected = op.canEditTransactions, onClick = { viewModel.updateOperatorOnServer(op.copy(canEditTransactions = !op.canEditTransactions)) }, label = { Text("Edit Tx", fontSize = 11.sp) })
                                    FilterChip(selected = op.canDeleteTransactions, onClick = { viewModel.updateOperatorOnServer(op.copy(canDeleteTransactions = !op.canDeleteTransactions)) }, label = { Text("Del Tx", fontSize = 11.sp) })
                                    FilterChip(selected = op.canManageWallet, onClick = { viewModel.updateOperatorOnServer(op.copy(canManageWallet = !op.canManageWallet)) }, label = { Text("Wallet", fontSize = 11.sp) })
                                    FilterChip(selected = op.canManageExpenses, onClick = { viewModel.updateOperatorOnServer(op.copy(canManageExpenses = !op.canManageExpenses)) }, label = { Text("Expenses", fontSize = 11.sp) })
                                    FilterChip(selected = op.canViewReports, onClick = { viewModel.updateOperatorOnServer(op.copy(canViewReports = !op.canViewReports)) }, label = { Text("Reports", fontSize = 11.sp) })
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (editingOperator != null) {
        var usernameInput by remember(editingOperator) { mutableStateOf(editingOperator!!.username) }
        var pinInput by remember(editingOperator) { mutableStateOf("") }
        var mobileInput by remember(editingOperator) { mutableStateOf(editingOperator!!.mobile) }
        var roleInput by remember(editingOperator) { mutableStateOf(editingOperator!!.role) }

        var canViewCustomers by remember(editingOperator) { mutableStateOf(editingOperator!!.canViewCustomers) }
        var canAddCustomers by remember(editingOperator) { mutableStateOf(editingOperator!!.canAddCustomers) }
        var canEditCustomers by remember(editingOperator) { mutableStateOf(editingOperator!!.canEditCustomers) }
        var canDeleteCustomers by remember(editingOperator) { mutableStateOf(editingOperator!!.canDeleteCustomers) }
        var canViewSuppliers by remember(editingOperator) { mutableStateOf(editingOperator!!.canViewSuppliers) }
        var canAddSuppliers by remember(editingOperator) { mutableStateOf(editingOperator!!.canAddSuppliers) }
        var canEditSuppliers by remember(editingOperator) { mutableStateOf(editingOperator!!.canEditSuppliers) }
        var canDeleteSuppliers by remember(editingOperator) { mutableStateOf(editingOperator!!.canDeleteSuppliers) }
        var canViewTransactions by remember(editingOperator) { mutableStateOf(editingOperator!!.canViewTransactions) }
        var canAddTransactions by remember(editingOperator) { mutableStateOf(editingOperator!!.canAddTransactions) }
        var canEditTransactions by remember(editingOperator) { mutableStateOf(editingOperator!!.canEditTransactions) }
        var canDeleteTransactions by remember(editingOperator) { mutableStateOf(editingOperator!!.canDeleteTransactions) }
        var canManageWallet by remember(editingOperator) { mutableStateOf(editingOperator!!.canManageWallet) }
        var canManageExpenses by remember(editingOperator) { mutableStateOf(editingOperator!!.canManageExpenses) }
        var canViewReports by remember(editingOperator) { mutableStateOf(editingOperator!!.canViewReports) }

        AlertDialog(
            onDismissRequest = { editingOperator = null },
            title = {
                Text(
                    text = if (lang == "BN") "ইউজার তথ্য ও অনুমতি পরিবর্তন" else "Edit Operator Details & RBAC",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    OutlinedTextField(
                        value = usernameInput,
                        onValueChange = { usernameInput = it },
                        label = { Text(viewModel.t("full_name")) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = mobileInput,
                        onValueChange = { mobileInput = it },
                        label = { Text(viewModel.t("mobile_number")) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = {
                            val normalized = com.safa.account.data.api.MobileNumberNormalizer.normalizePin(it)
                            if (normalized.length <= 6) pinInput = normalized
                        },
                        label = { Text(if (lang == "BN") "নতুন ৬-ডিজিটের পিন (ঐচ্ছিক)" else "New 6-digit PIN (Optional)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Role", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = roleInput == "Manager", onClick = { roleInput = "Manager" }, label = { Text("Manager") })
                        FilterChip(selected = roleInput == "Staff", onClick = { roleInput = "Staff" }, label = { Text("Staff") })
                    }

                    Text(if (lang == "BN") "দানাদার অনুমতিসমূহ (Granular RBAC)" else "Granular RBAC Permissions", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)

                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = canViewCustomers, onClick = { canViewCustomers = !canViewCustomers }, label = { Text("View Cust", fontSize = 11.sp) })
                        FilterChip(selected = canAddCustomers, onClick = { canAddCustomers = !canAddCustomers }, label = { Text("Add Cust", fontSize = 11.sp) })
                        FilterChip(selected = canEditCustomers, onClick = { canEditCustomers = !canEditCustomers }, label = { Text("Edit Cust", fontSize = 11.sp) })
                        FilterChip(selected = canDeleteCustomers, onClick = { canDeleteCustomers = !canDeleteCustomers }, label = { Text("Del Cust", fontSize = 11.sp) })
                        FilterChip(selected = canViewSuppliers, onClick = { canViewSuppliers = !canViewSuppliers }, label = { Text("View Supp", fontSize = 11.sp) })
                        FilterChip(selected = canAddSuppliers, onClick = { canAddSuppliers = !canAddSuppliers }, label = { Text("Add Supp", fontSize = 11.sp) })
                        FilterChip(selected = canEditSuppliers, onClick = { canEditSuppliers = !canEditSuppliers }, label = { Text("Edit Supp", fontSize = 11.sp) })
                        FilterChip(selected = canDeleteSuppliers, onClick = { canDeleteSuppliers = !canDeleteSuppliers }, label = { Text("Del Supp", fontSize = 11.sp) })
                        FilterChip(selected = canViewTransactions, onClick = { canViewTransactions = !canViewTransactions }, label = { Text("View Tx", fontSize = 11.sp) })
                        FilterChip(selected = canAddTransactions, onClick = { canAddTransactions = !canAddTransactions }, label = { Text("Add Tx", fontSize = 11.sp) })
                        FilterChip(selected = canEditTransactions, onClick = { canEditTransactions = !canEditTransactions }, label = { Text("Edit Tx", fontSize = 11.sp) })
                        FilterChip(selected = canDeleteTransactions, onClick = { canDeleteTransactions = !canDeleteTransactions }, label = { Text("Del Tx", fontSize = 11.sp) })
                        FilterChip(selected = canManageWallet, onClick = { canManageWallet = !canManageWallet }, label = { Text("Wallet", fontSize = 11.sp) })
                        FilterChip(selected = canManageExpenses, onClick = { canManageExpenses = !canManageExpenses }, label = { Text("Expenses", fontSize = 11.sp) })
                        FilterChip(selected = canViewReports, onClick = { canViewReports = !canViewReports }, label = { Text("Reports", fontSize = 11.sp) })
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val original = editingOperator
                        if (original != null && usernameInput.isNotBlank()) {
                            val updated = original.copy(
                                username = usernameInput,
                                mobile = mobileInput,
                                pin = "",
                                role = roleInput,
                                canViewCustomers = canViewCustomers,
                                canAddCustomers = canAddCustomers,
                                canEditCustomers = canEditCustomers,
                                canDeleteCustomers = canDeleteCustomers,
                                canViewSuppliers = canViewSuppliers,
                                canAddSuppliers = canAddSuppliers,
                                canEditSuppliers = canEditSuppliers,
                                canDeleteSuppliers = canDeleteSuppliers,
                                canViewTransactions = canViewTransactions,
                                canAddTransactions = canAddTransactions,
                                canEditTransactions = canEditTransactions,
                                canDeleteTransactions = canDeleteTransactions,
                                canManageWallet = canManageWallet,
                                canManageExpenses = canManageExpenses,
                                canViewReports = canViewReports
                            )
                            viewModel.updateOperatorOnServer(updated, pinInput.takeIf { it.length == 6 }) { success, message ->
                                if (success) editingOperator = null else managementError = message
                            }
                        }
                    }
                ) {
                    Text(if (lang == "BN") "আপডেট করুন" else "Update", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            },
            dismissButton = {
                TextButton(onClick = { editingOperator = null }) {
                    Text(if (lang == "BN") "বাতিল" else "Cancel", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        )
    }

    if (showDeleteConfirmByOp != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmByOp = null },
            title = {
                Text(
                    text = if (lang == "BN") "ইউজার মুছে ফেলতে চান?" else "Delete User Account?",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Text(
                    text = if (lang == "BN")
                        "আপনি কি নিশ্চিতভাবে '${showDeleteConfirmByOp!!.username}' ইউজারটি মুছে ফেলতে চান? এই অ্যাকশনটি রিভার্স করা যাবে না।"
                        else
                        "Are you sure you want to permanently delete user '${showDeleteConfirmByOp!!.username}'? This action is irreversible."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val victim = showDeleteConfirmByOp
                        if (victim != null) {
                            viewModel.deleteOperatorOnServer(victim) { success, message ->
                                if (success) showDeleteConfirmByOp = null else managementError = message
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(if (lang == "BN") "হ্যাঁ, মুছে ফেলুন" else "Yes, Delete", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmByOp = null }) {
                    Text(if (lang == "BN") "বাতিল" else "Cancel", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PinChangePage(viewModel: SafaViewModel, onBack: () -> Unit) {
    val lang by viewModel.currentLanguage.collectAsStateWithLifecycle()
    
    var oldPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var successMsg by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(16.dp)) {
        PageHeader(title = if (lang == "BN") "পিন পরিবর্তন" else "Change PIN", icon = Icons.Default.Lock, onBack = onBack)

        Card(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = oldPin,
                    onValueChange = {
                        val normalized = com.safa.account.data.api.MobileNumberNormalizer.normalizePin(it)
                        if (normalized.length <= 6) oldPin = normalized
                        errorMsg = null; successMsg = null
                    },
                    label = { Text(if (lang == "BN") "বর্তমান পিন" else "Current PIN") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = newPin,
                    onValueChange = {
                        val normalized = com.safa.account.data.api.MobileNumberNormalizer.normalizePin(it)
                        if (normalized.length <= 6) newPin = normalized
                        errorMsg = null; successMsg = null
                    },
                    label = { Text(if (lang == "BN") "নতুন পিন" else "New PIN") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorMsg != null) {
                    Text(text = errorMsg!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                if (successMsg != null) {
                    Text(text = successMsg!!, color = Color(0xFF2E7D32), style = MaterialTheme.typography.bodySmall)
                }

                Button(
                    onClick = {
                        if (oldPin.length == 6 && newPin.length == 6 && oldPin != newPin) {
                            isSubmitting = true
                            viewModel.updateOperatorPin(oldPin, newPin) { success, message ->
                                isSubmitting = false
                                if (success) {
                                    successMsg = if (lang == "BN") "পিন সফলভাবে পরিবর্তন করা হয়েছে!" else "PIN changed successfully!"
                                    oldPin = ""
                                    newPin = ""
                                } else {
                                    errorMsg = message
                                }
                            }
                        } else {
                            errorMsg = if (lang == "BN") "বর্তমান ও নতুন পিন আলাদা ৬ সংখ্যার হতে হবে" else "Current and new PIN must be different six-digit PINs"
                        }
                    },
                    enabled = !isSubmitting,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        if (isSubmitting) {
                            if (lang == "BN") "আপডেট হচ্ছে…" else "Updating…"
                        } else if (lang == "BN") "পিন আপডেট করুন" else "Update PIN",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
