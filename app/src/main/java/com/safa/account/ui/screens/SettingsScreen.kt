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
import com.safa.account.ui.viewmodel.HundiViewModel
import kotlinx.coroutines.launch

enum class SettingsSubpage {
    MAIN, CURRENCY, BRANDING, LANGUAGE, USER_MGT, PIN_CHANGE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: HundiViewModel,
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
fun SettingsMainPage(viewModel: HundiViewModel, onNavigate: (SettingsSubpage) -> Unit) {
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
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        // Active User Header Profile Card (Stunning Visual Card)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                ),
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                )
            ) {
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
                            color = MaterialTheme.colorScheme.onSurface
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
                                        if (activeOperator?.role == "Owner") 
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
                                    color = if (activeOperator?.role == "Owner") Color(0xFF2E7D32) else MaterialTheme.colorScheme.secondary
                                )
                            }
                            Text(
                                text = "ID: #${activeOperator?.id ?: 1}",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.outline
                            )
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
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = if (lang == "BN") "সাপ্লায়ার ক্রয় রেট ও প্রফিট মার্জিন গণনাকারী মোড" else "Calculate supplier buying rates & profit margins",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                color = MaterialTheme.colorScheme.outline
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
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = if (lang == "BN") "সাপ্লায়ারদের জন্য কাস্টম রেট" else "Enable supplier custom rates",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                    color = MaterialTheme.colorScheme.outline
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
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = if (lang == "BN") "ওয়ালেট রিচার্জের জন্য কাস্টম রেট" else "Enable wallet custom rates",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                    color = MaterialTheme.colorScheme.outline
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

        val isOwnerOrAdmin = activeOperator?.role == "Owner" || activeOperator?.role == "Admin"

        // Section 2: Security and Accounts Category
        item {
            Text(
                text = if (lang == "BN") "অপারেটর ও নিরাপত্তা" else "Operator & Security",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                ),
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
            )
        }

        if (activeOperator?.role == "Owner") {
            item {
                SettingsMenuItem(
                    icon = Icons.Default.People,
                    title = if (lang == "BN") "ব্যবহার কারি ম্যানেজম্যান" else "User Management",
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
            
            Surface(
                onClick = {
                    val updated = activeOp?.copy(isBiometricEnabled = !isBioEnabled)
                    if (updated != null) {
                        viewModel.updateOperator(updated)
                    }
                },
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
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = if (lang == "BN") "অপেরেশন সফল করতে মোবাইল এর লকস্ক্রিন লক ব্যবহার করি" else "Use mobile lock screen fingerprint to verify actions",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                    Switch(
                        checked = isBioEnabled,
                        onCheckedChange = { checked ->
                            val updated = activeOp?.copy(isBiometricEnabled = checked)
                            if (updated != null) {
                                viewModel.updateOperator(updated)
                            }
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
                    .height(38.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                    contentColor = MaterialTheme.colorScheme.error
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f)),
                shape = RoundedCornerShape(10.dp)
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
                    )
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
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha=0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = "", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                }
                Text(text = title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
            }
            Icon(Icons.Default.ChevronRight, contentDescription = "", tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(20.dp))
        }
    }
}

// ... Additional Subpages will be implemented in a follow-up call to manage string size ...

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurrencyPage(viewModel: HundiViewModel, onBack: () -> Unit) {
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
                        viewModel.updateSelectedForeignCurrency(selectedForeignCurrency)
                        viewModel.updateSelectedLocalCurrency(selectedLocalCurrency)
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
fun BrandingPage(viewModel: HundiViewModel, onBack: () -> Unit) {
    val lang by viewModel.currentLanguage.collectAsStateWithLifecycle()
    val customAppName by viewModel.customAppName.collectAsStateWithLifecycle()
    val customAppLogo by viewModel.customAppLogo.collectAsStateWithLifecycle()

    var tempAppName by remember { mutableStateOf(customAppName) }
    var tempAppLogo by remember { mutableStateOf(customAppLogo) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            tempAppLogo = it.toString()
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
                        if (tempAppLogo.startsWith("content://") || tempAppLogo.startsWith("http")) {
                            AsyncImage(
                                model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
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
                        viewModel.updateCustomAppName(tempAppName)
                        viewModel.updateCustomAppLogo(tempAppLogo)
                        if (tempAppLogo.startsWith("content://") || tempAppLogo.startsWith("file://")) {
                            viewModel.updateCustomAppLogoUri(tempAppLogo)
                        } else {
                            viewModel.updateCustomAppLogoUri(null)
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
fun LanguagePage(viewModel: HundiViewModel, onBack: () -> Unit) {
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
        Column {
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
            Text(
                text = "পিছনে ফিরে যেতে এখানে চাপুন • Tap to go back",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun UserManagementPage(viewModel: HundiViewModel, onBack: () -> Unit) {
    val lang by viewModel.currentLanguage.collectAsStateWithLifecycle()
    val operators by viewModel.operators.collectAsStateWithLifecycle()
    var isAddingOperator by remember { mutableStateOf(false) }
    var expandedOperatorId by remember { mutableStateOf<Int?>(null) }

    var editingOperator by remember { mutableStateOf<com.safa.account.data.model.OperatorAccount?>(null) }
    var showDeleteConfirmByOp by remember { mutableStateOf<com.safa.account.data.model.OperatorAccount?>(null) }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(16.dp)) {
        PageHeader(title = if (lang == "BN") "ব্যবহার কারি ম্যানেজম্যান" else "User Management", icon = Icons.Default.People, onBack = onBack)

        if (isAddingOperator) {
            var newUsername by remember { mutableStateOf("") }
            var newPin by remember { mutableStateOf("") }
            var newRole by remember { mutableStateOf("Staff") }
            var perms by remember { mutableStateOf(setOf("edit", "create", "delete", "update")) }

            Card(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(if (lang == "BN") "নতুন ইউজার তৈরি করুন" else "Create New User", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    OutlinedTextField(value = newUsername, onValueChange = { newUsername = it }, label = { Text(if (lang == "BN") "ইউজারনেম" else "Username") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = newPin, onValueChange = { if (it.length <= 4) newPin = it }, label = { Text(if (lang == "BN") "৪-ডিজিটের পিন" else "4-Digit PIN") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), modifier = Modifier.fillMaxWidth())
                    
                    Text(if (lang == "BN") "রোল" else "Role", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = newRole == "Owner", onClick = { newRole = "Owner" }, label = { Text("Owner") })
                        FilterChip(selected = newRole == "Staff", onClick = { newRole = "Staff" }, label = { Text("Staff") })
                    }

                    Text(if (lang == "BN") "অনুমতি (Permissions)" else "Permissions", style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("create" to "তৈরি করুন (Create)", "edit" to "এডিট করুন (Edit)", "delete" to "ডিলিট করুন (Delete)", "update" to "আপডেট করুন (Update)").forEach { (key, label) ->
                            FilterChip(
                                selected = perms.contains(key),
                                onClick = {
                                    perms = if (perms.contains(key)) perms - key else perms + key
                                },
                                label = { Text(label, fontSize = 12.sp) }
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
                        OutlinedButton(onClick = { isAddingOperator = false }, modifier = Modifier.weight(1f)) {
                            Text(if (lang == "BN") "বাতিল" else "Cancel", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Button(
                            onClick = { 
                                viewModel.addOperator(newUsername, newPin, newRole, perms.joinToString(",")) {
                                    isAddingOperator = false
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
                Text(if (lang == "BN") "নতুন ইউজার যুক্ত করুন" else "Add New User")
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(operators, key = { it.id }) { op ->
                    val isExpanded = expandedOperatorId == op.id
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha=0.3f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha=0.1f)), contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Person, contentDescription = "", tint = MaterialTheme.colorScheme.primary)
                                    }
                                    Column {
                                        Text(op.username, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        Text("Role: ${op.role}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
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
                                    Text(if (lang == "BN") "অনুমতিসমূহ" else "Permissions", style = MaterialTheme.typography.labelMedium)
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        IconButton(onClick = { editingOperator = op }) {
                                            Icon(Icons.Default.Edit, contentDescription = "Edit User", tint = MaterialTheme.colorScheme.primary)
                                        }
                                        val curOp by viewModel.currentOperator.collectAsStateWithLifecycle()
                                        if (op.id != curOp?.id) {
                                            IconButton(onClick = { showDeleteConfirmByOp = op }) {
                                                Icon(Icons.Default.Delete, contentDescription = "Delete User", tint = MaterialTheme.colorScheme.error)
                                            }
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                val opPerms = op.permissions.split(",").map { it.trim() }.toSet()
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf("create" to "Create", "edit" to "Edit", "delete" to "Delete", "update" to "Update").forEach { (key, label) ->
                                        FilterChip(
                                            selected = opPerms.contains(key),
                                            onClick = {
                                                val newPerms = if (opPerms.contains(key)) opPerms - key else opPerms + key
                                                viewModel.updateOperator(op.copy(permissions = newPerms.joinToString(",")))
                                            },
                                            label = { Text(label, fontSize = 12.sp) }
                                        )
                                    }
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
        var roleInput by remember(editingOperator) { mutableStateOf(editingOperator!!.role) }
        var permsInput by remember(editingOperator) { mutableStateOf(editingOperator!!.permissions.split(",").map { it.trim() }.toSet()) }

        AlertDialog(
            onDismissRequest = { editingOperator = null },
            title = {
                Text(
                    text = if (lang == "BN") "ইউজার তথ্য পরিবর্তন" else "Edit Operator Details",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = usernameInput,
                        onValueChange = { usernameInput = it },
                        label = { Text("Username") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { if (it.length <= 4) pinInput = it },
                        label = { Text("4-Digit PIN") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Role", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = roleInput == "Owner", onClick = { roleInput = "Owner" }, label = { Text("Owner") })
                        FilterChip(selected = roleInput == "Staff", onClick = { roleInput = "Staff" }, label = { Text("Staff") })
                    }
                    Text(if (lang == "BN") "অনুমতিসমূহ" else "Permissions", style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("create" to "Create", "edit" to "Edit", "delete" to "Delete", "update" to "Update").forEach { (key, label) ->
                            FilterChip(
                                selected = permsInput.contains(key),
                                onClick = {
                                    permsInput = if (permsInput.contains(key)) permsInput - key else permsInput + key
                                },
                                label = { Text(label, fontSize = 12.sp) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val original = editingOperator
                        if (original != null && usernameInput.isNotBlank() && pinInput.length == 4) {
                            val updated = original.copy(
                                username = usernameInput,
                                pin = pinInput,
                                role = roleInput,
                                permissions = permsInput.joinToString(",")
                            )
                            viewModel.updateOperator(updated) {
                                editingOperator = null
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
                            viewModel.deleteOperatorAccount(victim) {
                                showDeleteConfirmByOp = null
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
fun PinChangePage(viewModel: HundiViewModel, onBack: () -> Unit) {
    val lang by viewModel.currentLanguage.collectAsStateWithLifecycle()
    
    var oldPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var successMsg by remember { mutableStateOf<String?>(null) }

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
                    onValueChange = { if (it.length <= 4) oldPin = it; errorMsg = null; successMsg = null },
                    label = { Text(if (lang == "BN") "বর্তমান পিন" else "Current PIN") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = newPin,
                    onValueChange = { if (it.length <= 4) newPin = it; errorMsg = null; successMsg = null },
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
                        if (oldPin.length == 4 && newPin.length == 4) {
                            if (com.safa.account.utils.HashUtils.verifyPin(oldPin, viewModel.currentOperator.value?.pin ?: "")) {
                                viewModel.updateOperatorPin(newPin) {
                                    successMsg = if (lang == "BN") "পিন সফলভাবে পরিবর্তন করা হয়েছে!" else "PIN changed successfully!"
                                    oldPin = ""
                                    newPin = ""
                                }
                            } else {
                                errorMsg = if (lang == "BN") "বর্তমান পিন ভুল!" else "Current PIN is incorrect!"
                            }
                        } else {
                            errorMsg = if (lang == "BN") "পিন অবশ্যই ৪ ডিজিটের হতে হবে" else "PIN must be 4 digits"
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(if (lang == "BN") "পিন আপডেট করুন" else "Update PIN")
                }
            }
        }
    }
}
