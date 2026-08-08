package com.safa.account.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safa.account.data.model.Supplier
import com.safa.account.data.model.SupplierDeposit
import com.safa.account.data.model.RemittanceTransaction
import com.safa.account.data.model.WalletLedger
import com.safa.account.ui.viewmodel.HundiViewModel
import com.safa.account.ui.BiometricTriggerButton
import com.safa.account.ui.screens.CalculatorDialog
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*

sealed class SupplierLedgerItem {
    abstract val timestamp: Long
    data class DepositItem(
        val id: Int,
        val amountSar: Double,
        val rate: Double,
        val amountBdt: Double,
        val paidBdt: Double,
        val notes: String,
        val transactionType: String,
        override val timestamp: Long
    ) : SupplierLedgerItem()

    data class DisbursedItem(
        val txId: Int,
        val receiverName: String,
        val amountSar: Double,
        val amountBdt: Double,
        val status: String,
        override val timestamp: Long
    ) : SupplierLedgerItem()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupplierScreen(
    viewModel: HundiViewModel,
    modifier: Modifier = Modifier,
    isProfileView: Boolean = false,
    isAddView: Boolean = false
) {
    val suppliers by viewModel.suppliers.collectAsStateWithLifecycle()
    val supplierDeposits by viewModel.supplierDeposits.collectAsStateWithLifecycle()
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()
    val lang by viewModel.currentLanguage.collectAsStateWithLifecycle()
    val foreignCur by viewModel.selectedForeignCurrency.collectAsStateWithLifecycle()
    val localCur by viewModel.selectedLocalCurrency.collectAsStateWithLifecycle()
    val currentOperator by viewModel.currentOperator.collectAsStateWithLifecycle()
    val walletLedgers by viewModel.walletLedgers.collectAsStateWithLifecycle()
    val isSupplierRateEnabled by viewModel.isSupplierRateEnabled.collectAsStateWithLifecycle()

    val selectedSupplierIdForProfile by viewModel.selectedSupplierIdForProfile.collectAsStateWithLifecycle()

    if (currentOperator != null && !currentOperator!!.canViewSuppliers) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Lock, contentDescription = "", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
                Text(text = viewModel.t("access_denied"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                Text(text = viewModel.t("permission_required"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
            }
        }
        return
    }

    var searchQuery by remember { mutableStateOf("") }
    var isCustomizerExpanded by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedSortOption by remember { mutableStateOf("Newest") } // Newest, Oldest, A-Z, Balance
    var selectedFilterStatus by remember { mutableStateOf("All") } // All, Has Balance, Owing

    // Registration inputs
    var nameInput by remember { mutableStateOf("") }
    var phoneInput by remember { mutableStateOf("") }
    var addressInput by remember { mutableStateOf("") }

    val context = androidx.compose.ui.platform.LocalContext.current
    val contactPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val uri = result.data?.data
            if (uri != null) {
                val projection = arrayOf(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER, android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val numberIndex = cursor.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER)
                        val nameIndex = cursor.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                        if (numberIndex >= 0) phoneInput = cursor.getString(numberIndex)
                        if (nameIndex >= 0) nameInput = cursor.getString(nameIndex)
                    }
                }
            }
        }
    }

    val filteredSuppliers = remember(suppliers, searchQuery, selectedSortOption, selectedFilterStatus, supplierDeposits) {
        var list = if (searchQuery.isBlank()) suppliers
        else suppliers.filter {
            it.name.contains(searchQuery, ignoreCase = true) || 
            it.phone.contains(searchQuery, ignoreCase = true) ||
            it.address.contains(searchQuery, ignoreCase = true)
        }

        // Helper to calculate ${localCur}due (Payable is positive, Receivable/prepaid is negative)
        fun getSupplierBdtDue(sId: Int): Double {
            val deposits = supplierDeposits.filter { it.supplierId == sId }
            val totalAcquiredBdt = deposits.filter { it.transactionType == "SAR_GIVEN" || it.transactionType == "SAR_DEPOSIT" }.sumOf { it.amountBdt }
            val totalPaidBdt = deposits.sumOf { it.paidBdt }
            return totalAcquiredBdt - totalPaidBdt
        }

        // Apply filters based on balance
        if (selectedFilterStatus != "All") {
            list = list.filter { supplier ->
                val bdtDue = getSupplierBdtDue(supplier.id)
                if (selectedFilterStatus == "Has Balance") {
                    bdtDue > 0.05 // Has advance balance/prepaid/receivable back to us (Paid Less)
                } else {
                    bdtDue < -0.05 // Owing / Payable (Paid More)
                }
            }
        }

        // Sort
        list = when (selectedSortOption) {
            "Oldest" -> list.sortedBy { it.timestamp }
            "A-Z" -> list.sortedBy { it.name.lowercase(java.util.Locale.ROOT) }
            "Balance" -> list.sortedByDescending { Math.abs(getSupplierBdtDue(it.id)) }
            else -> list.sortedByDescending { it.timestamp } // Newest
        }

        list
    }

    val currencyFormatter = remember { DecimalFormat("#,##0") }

    if (isAddView || showAddDialog) {
        androidx.activity.compose.BackHandler {
            if (showAddDialog) showAddDialog = false else viewModel.navigateBack()
        }
        AddSupplierPage(
            lang = lang,
            nameInput = nameInput,
            onNameChange = { nameInput = it },
            phoneInput = phoneInput,
            onPhoneChange = { phoneInput = it },
            addressInput = addressInput,
            onAddressChange = { addressInput = it },
            onContactPicker = {
                val intent = android.content.Intent(android.content.Intent.ACTION_PICK, android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
                contactPickerLauncher.launch(intent)
            },
            onCancel = {
                if (showAddDialog) showAddDialog = false else viewModel.navigateBack()
            },
            onSubmit = {
                viewModel.registerSupplier(nameInput, phoneInput, addressInput) {
                    nameInput = ""
                    phoneInput = ""
                    addressInput = ""
                    if (showAddDialog) showAddDialog = false else viewModel.navigateBack()
                }
            },
            viewModel = viewModel
        )
    } else if (isProfileView) {
        androidx.activity.compose.BackHandler {
            viewModel.selectSupplierProfile(null)
        }
        var lastActiveSupplier by remember { mutableStateOf<Supplier?>(null) }
        val activeSupplier = suppliers.find { it.id == selectedSupplierIdForProfile }
        if (activeSupplier != null) {
            lastActiveSupplier = activeSupplier
        }
        val displayedSupplier = activeSupplier ?: lastActiveSupplier

        if (displayedSupplier != null) {
            // Enter Supplier Detailed Profile Screen!
            SupplierProfileView(
                supplier = displayedSupplier,
                deposits = supplierDeposits.filter { it.supplierId == displayedSupplier.id },
                transactions = transactions.filter { it.supplierId == displayedSupplier.id && it.status != "Cancelled" },
                lang = lang,
                operatorPin = currentOperator?.pin ?: "",
                onBack = { viewModel.selectSupplierProfile(null) },
                onUpdate = { updated -> viewModel.updateSupplier(updated) },
                onDelete = { 
                    viewModel.deleteSupplier(displayedSupplier.id)
                    viewModel.selectSupplierProfile(null) 
                },
                viewModel = viewModel,
                modifier = modifier
            )
        } else {
            LaunchedEffect(Unit) {
                viewModel.selectSupplierProfile(null)
            }
        }
    } else {
        // Render classic compact Suppliers list (Summary is hidden until clicked inside)
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Spacer(modifier = Modifier.height(12.dp))

                // Compact layout screen title (choto layout)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CorporateFare,
                                contentDescription = "",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Text(
                            text = viewModel.t("supplier_mgmt"),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 17.sp,
                                letterSpacing = (-0.3).sp
                            ),
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1
                        )
                    }

                    // Elegant Add Supplier button at top right
                    Button(
                        onClick = { showAddDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "", modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (lang == "BN") "নতুন" else "Add",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Search field and customizer
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text(if (lang == "BN") "খুঁজুন..." else "Search...", fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "", modifier = Modifier.size(20.dp)) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "", modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        modifier = Modifier.weight(1f).height(48.dp).testTag("supplier_search_field"),
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                        )
                    )
                    
                    FilledIconButton(
                        onClick = { isCustomizerExpanded = !isCustomizerExpanded },
                        shape = RoundedCornerShape(12.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = if (isCustomizerExpanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer
                        ),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "",
                            tint = if (isCustomizerExpanded) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                
                AnimatedVisibility(visible = isCustomizerExpanded) {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = if (lang == "BN") "⚙️ ফিল্টার ও সেটিংস" else "⚙️ Filter & Settings",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Sort Option Dropdown
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (lang == "BN") "বাছাই করুন" else "Sort By",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.padding(bottom = 2.dp)
                                    )
                                    var sortMenuExpanded by remember { mutableStateOf(false) }
                                    Box {
                                        OutlinedButton(
                                            onClick = { sortMenuExpanded = true },
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = when (selectedSortOption) {
                                                    "Oldest" -> if (lang == "BN") "পুরাতন প্রথম" else "Oldest First"
                                                    "A-Z" -> if (lang == "BN") "নাম A-Z" else "Name A-Z"
                                                    "Balance" -> if (lang == "BN") "সর্বোচ্চ ব্যালেন্স" else "Highest Balance"
                                                    else -> if (lang == "BN") "নতুন প্রথম" else "Newest First"
                                                },
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        DropdownMenu(
                                            expanded = sortMenuExpanded,
                                            onDismissRequest = { sortMenuExpanded = false }
                                        ) {
                                            listOf("Newest", "Oldest", "A-Z", "Balance").forEach { option ->
                                                DropdownMenuItem(
                                                    text = {
                                                        Text(
                                                            when (option) {
                                                                "Oldest" -> if (lang == "BN") "পুরাতন প্রথম" else "Oldest First"
                                                                "A-Z" -> if (lang == "BN") "নাম A-Z" else "Name A-Z"
                                                                "Balance" -> if (lang == "BN") "সর্বোচ্চ ব্যালেন্স" else "Highest Balance"
                                                                else -> if (lang == "BN") "নতুন প্রথম" else "Newest First"
                                                            }
                                                        )
                                                    },
                                                    onClick = {
                                                        selectedSortOption = option
                                                        sortMenuExpanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }

                                // Filter Status Option Dropdown
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (lang == "BN") "ফিল্টার ক্যাটাগরি" else "Filter Balance",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.padding(bottom = 2.dp)
                                    )
                                    var filterMenuExpanded by remember { mutableStateOf(false) }
                                    Box {
                                        OutlinedButton(
                                            onClick = { filterMenuExpanded = true },
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = when (selectedFilterStatus) {
                                                    "Has Balance" -> if (lang == "BN") "শুধু ব্যালেন্স জমা" else "Has Active Deposit"
                                                    "Owing" -> if (lang == "BN") "শুধু বাকি/পাওনা" else "Owing Liability"
                                                    else -> if (lang == "BN") "সব সাপ্লায়ার" else "All Suppliers"
                                                },
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        DropdownMenu(
                                            expanded = filterMenuExpanded,
                                            onDismissRequest = { filterMenuExpanded = false }
                                        ) {
                                            listOf("All", "Has Balance", "Owing").forEach { option ->
                                                DropdownMenuItem(
                                                    text = {
                                                        Text(
                                                            when (option) {
                                                                "Has Balance" -> if (lang == "BN") "শুধু ব্যালেন্স জমা" else "Has Active Deposit"
                                                                "Owing" -> if (lang == "BN") "শুধু বাকি/পাওনা" else "Owing Liability"
                                                                else -> if (lang == "BN") "সব সাপ্লায়ার" else "All Suppliers"
                                                            }
                                                        )
                                                    },
                                                    onClick = {
                                                        selectedFilterStatus = option
                                                        filterMenuExpanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (filteredSuppliers.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.CorporateFare, contentDescription = "", modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
                            Text(
                                text = if (lang == "BN") "কোনো সাপ্লায়ার প্রোফাইল নেই।" else "No suppliers registered.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        items(filteredSuppliers, key = { it.id }) { supplier ->
                            val supplierDepositsList = remember(supplierDeposits) {
                                supplierDeposits.filter { it.supplierId == supplier.id }
                             }
                             val totalDepositedSar = supplierDepositsList.sumOf { it.amountSar }
                             val totalAcquiredBdt = supplierDepositsList.sumOf { it.amountBdt }
                             val totalPaidBdt = supplierDepositsList.sumOf { it.paidBdt }
                             val currentSupplierBdtDue = totalAcquiredBdt - totalPaidBdt

                            // Render highly clean, compact Supplier Item Card
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.selectSupplierProfile(supplier.id) // Go directly into detailed profile
                                    }
                                    .testTag("supplier_card_${supplier.id}"),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                                shape = RoundedCornerShape(16.dp),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            // Supplier custom avatar styling
                                            val avatarBg = remember(supplier.avatarColor) {
                                                try { Color(android.graphics.Color.parseColor(supplier.avatarColor)) } catch(e: Exception) { Color(0xFFFF9800) }
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .size(42.dp)
                                                    .clip(CircleShape)
                                                    .background(avatarBg.copy(alpha = 0.15f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = supplier.avatarEmoji.ifBlank { supplier.name.take(1).uppercase(Locale.ROOT) },
                                                    fontSize = 18.sp
                                                )
                                            }
                                            Column {
                                                Text(
                                                    text = supplier.name,
                                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.ExtraBold),
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Spacer(modifier = Modifier.height(1.dp))
                                                Text(
                                                    text = supplier.phone,
                                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                                    color = MaterialTheme.colorScheme.outline
                                                )
                                            }
                                        }

                                        Column(horizontalAlignment = Alignment.End) {
                                            val isDue = currentSupplierBdtDue < -0.05
                                            val isReceivable = currentSupplierBdtDue > 0.05
                                            Text(
                                                text = if (isDue) {
                                                    if (lang == "BN") "বকেয়া" else "Due"
                                                } else if (isReceivable) {
                                                    if (lang == "BN") "পাওনা" else "Receivable"
                                                } else {
                                                    if (lang == "BN") "কোনো বকেয়া নেই" else "No Due"
                                                },
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                color = if (isDue) Color(0xFFD32F2F) else if (isReceivable) Color(0xFF1565C0) else Color(0xFF2E7D32)
                                            )
                                            Spacer(modifier = Modifier.height(1.dp))
                                            Text(
                                                text = "৳ ${currencyFormatter.format(Math.abs(currentSupplierBdtDue))}",
                                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                                                color = if (isDue) Color(0xFFD32F2F) else if (isReceivable) Color(0xFF1565C0) else Color(0xFF2E7D32)
                                            )
                                        }
                                    }

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(1.dp)
                                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = if (lang == "BN") "সর্বমোট রিয়াল জমা" else "Total Riyal Deposited",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.outline
                                            )
                                            Text(
                                                text = "${currencyFormatter.format(totalDepositedSar)} ${foreignCur}",
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                text = if (lang == "BN") "সর্বমোট টাকা ক্রয়" else "Total Taka Buy",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.outline
                                            )
                                            Text(
                                                text = "৳ ${currencyFormatter.format(totalAcquiredBdt)} ${localCur}",
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
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
    }
}

/**
 * Beautiful full-screen Supplier Profile View with balances, smart audit ledgers, fast buys, secure PIN authorizers
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SupplierProfileView(
    supplier: Supplier,
    deposits: List<SupplierDeposit>,
    transactions: List<RemittanceTransaction>,
    lang: String,
    operatorPin: String,
    onBack: () -> Unit,
    onUpdate: (Supplier) -> Unit,
    onDelete: () -> Unit,
    viewModel: HundiViewModel,
    modifier: Modifier = Modifier
) {
    DisposableEffect(Unit) {
        viewModel.setSubPageActive(true)
        onDispose {
            viewModel.setSubPageActive(false)
        }
    }

    val currentOperator by viewModel.currentOperator.collectAsStateWithLifecycle()
    var isEditing by remember { mutableStateOf(false) }
    var selectedProfileTab by remember { mutableStateOf(0) }
    val walletLedgers by viewModel.walletLedgers.collectAsStateWithLifecycle()
    val walletBatches by viewModel.walletBatches.collectAsStateWithLifecycle()

    var editName by remember(supplier) { mutableStateOf(supplier.name) }
    var editPhone by remember(supplier) { mutableStateOf(supplier.phone) }
    var editAddress by remember(supplier) { mutableStateOf(supplier.address) }
    var editLicense by remember(supplier) { mutableStateOf(supplier.tradeLicense) }
    var editNotes by remember(supplier) { mutableStateOf(supplier.securityNotes) }
    var editColor by remember(supplier) { mutableStateOf(supplier.avatarColor) }
    var editEmoji by remember(supplier) { mutableStateOf(supplier.avatarEmoji) }

    // Fund screen states
    var showAddChoiceDialog by remember { mutableStateOf(false) }
    var isAddingFund by remember { mutableStateOf(false) }
    var buyAmountSarInput by remember { mutableStateOf("") }
    var buyRateInput by remember { mutableStateOf("32.5") }
    var buyNotesInput by remember { mutableStateOf("") }
    var buyPaidBdtInput by remember { mutableStateOf("") }
    var buyTransactionType by remember { mutableStateOf("SAR_GIVEN") }
    var selectedLedgerId by remember { mutableStateOf(0) }

    // Security authorizer states
    var showSecurityDialog by remember { mutableStateOf(false) }
    var actionToConfirm by remember { mutableStateOf("") } // "SAVE" or "DELETE"
    var pinCodeInput by remember { mutableStateOf("") }
    var pinErrorText by remember { mutableStateOf<String?>(null) }
    
    var expandedTxId by remember { mutableStateOf<Int?>(null) }
    var expandedDepId by remember { mutableStateOf<Int?>(null) }
    
    // Edit transaction dialog states
    var txToEdit by remember { mutableStateOf<RemittanceTransaction?>(null) }
    var editAmountSar by remember { mutableStateOf("") }
    var editCustomerRate by remember { mutableStateOf("") }
    var editSupplierRate by remember { mutableStateOf("") }
    var editReceiverName by remember { mutableStateOf("") }
    var editPhoneNum by remember { mutableStateOf("") }
    var editReceiverAccountType by remember { mutableStateOf("") }
    var editReceiverAccountNo by remember { mutableStateOf("") }
    var editTxNotes by remember { mutableStateOf("") }
    var editStatus by remember { mutableStateOf("") }
    var editSupplierId by remember { mutableStateOf<Int?>(null) }
    var isEditAmountCalCOpen by remember { mutableStateOf(false) }
    var editSarCollected by remember { mutableStateOf("") }
    var editBdtDisbursed by remember { mutableStateOf("") }

    // Edit deposit states
    var depositToEdit by remember { mutableStateOf<SupplierLedgerItem.DepositItem?>(null) }
    var editDepAmountSar by remember { mutableStateOf("") }
    var editDepRate by remember { mutableStateOf("") }
    var editDepNotes by remember { mutableStateOf("") }
    var editDepTxType by remember { mutableStateOf("") }
    var isEditDepCalCOpen by remember { mutableStateOf(false) }
    var depToDelete by remember { mutableStateOf<SupplierLedgerItem.DepositItem?>(null) }

    // PIN secure verification states for transactions
    var txToDelete by remember { mutableStateOf<RemittanceTransaction?>(null) }
    var showTxSecurityDialog by remember { mutableStateOf(false) }
    var txActionToConfirm by remember { mutableStateOf("") } // "EDIT", "DELETE", "STATUS_DELIVER", "STATUS_CANCEL", "STATUS_PENDING", "DELETE_DEP", "EDIT_DEP"
    var txPinCodeInput by remember { mutableStateOf("") }
    var txPinErrorText by remember { mutableStateOf<String?>(null) }

    val currencyFormatter = remember { DecimalFormat("#,##0") }

    val avatarColors = listOf(
        "#FF9800", "#3F51B5", "#2E7D32", "#C2185B", "#008080", "#1565C0", "#6A1B9A", "#455A64"
    )
    val avatarEmojis = listOf(
        "🏢", "🏦", "💼", "💰", "👑", "👤", "⭐", "⚡"
    )

    // Financial calculations
    val totalSarGiven = deposits.filter { it.transactionType == "SAR_GIVEN" || it.transactionType == "SAR_DEPOSIT" }.sumOf { it.amountSar }
    val totalSarReceived = deposits.filter { it.transactionType == "SAR_RECEIVED" || it.transactionType == "SAR_SETTLEMENT" }.sumOf { it.amountSar }
    val netSarOwedToUs = totalSarGiven - totalSarReceived

    val totalDepositedSar = totalSarGiven
    val totalAcquiredBdt = deposits.filter { it.transactionType == "SAR_GIVEN" || it.transactionType == "SAR_DEPOSIT" }.sumOf { it.amountBdt }
    val totalPaidBdt = deposits.sumOf { it.paidBdt }
    val currentSupplierBdtDue = totalAcquiredBdt - totalPaidBdt

    // Optimize and move compose computations to function scope (resolving previous compiler failures!)
    val unifiedLedger = remember(deposits, selectedProfileTab) {
        val items = mutableListOf<SupplierLedgerItem>()
        if (selectedProfileTab == 0) {
            deposits.forEach {
                items.add(SupplierLedgerItem.DepositItem(it.id, it.amountSar, it.rate, it.amountBdt, it.paidBdt, it.notes, it.transactionType, it.timestamp))
            }
        }
        items.sortedByDescending { it.timestamp }
    }

    val ledgerByDate = remember(unifiedLedger, lang) {
        unifiedLedger.groupBy {
            val sdf = SimpleDateFormat(if (lang == "BN") "d MMMM, yyyy" else "MMMM d, yyyy", Locale.US)
            sdf.format(Date(it.timestamp))
        }
    }

    val isSupplierRateEnabled by viewModel.isSupplierRateEnabled.collectAsStateWithLifecycle()
    val foreignCur by viewModel.selectedForeignCurrency.collectAsStateWithLifecycle()
    val localCur by viewModel.selectedLocalCurrency.collectAsStateWithLifecycle()

    if (isAddingFund) {
        AddFundPage(
            supplierName = supplier.name,
            lang = lang,
            buyTransactionType = buyTransactionType,
            onTransactionTypeChange = { buyTransactionType = it },
            buyAmountSarInput = buyAmountSarInput,
            onAmountChange = { buyAmountSarInput = it },
            buyRateInput = buyRateInput,
            onRateChange = { buyRateInput = it },
            buyNotesInput = buyNotesInput,
            onNotesChange = { buyNotesInput = it },
            buyPaidBdtInput = buyPaidBdtInput,
            onPaidBdtChange = { buyPaidBdtInput = it },
            walletLedgers = walletLedgers,
            selectedLedgerId = selectedLedgerId,
            onLedgerIdChange = { selectedLedgerId = it },
            isSupplierRateEnabled = isSupplierRateEnabled,
            onCancel = { isAddingFund = false },
            onSave = { date, doc ->
                val amount = buyAmountSarInput.toDoubleOrNull() ?: 0.0
                val rate = buyRateInput.toDoubleOrNull() ?: 0.0
                val paidBdt = buyPaidBdtInput.toDoubleOrNull() ?: 0.0
                
                // Incorporate doc nicely in notes
                val notesWithDoc = if (!doc.isNullOrBlank()) "$buyNotesInput \n[Attachment: $doc]".trim() else buyNotesInput
                
                viewModel.depositToSupplier(
                    supplierId = supplier.id,
                    amountSar = amount,
                    rate = rate,
                    paidBdt = paidBdt,
                    notes = notesWithDoc,
                    transactionType = buyTransactionType,
                    ledgerId = selectedLedgerId,
                    timestamp = date
                ) {
                    buyAmountSarInput = ""
                    buyNotesInput = ""
                    buyPaidBdtInput = ""
                    buyTransactionType = "SAR_GIVEN"
                    selectedLedgerId = 0
                    isAddingFund = false
                }
            }
        )
    } else {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .padding(bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
        // Slim, clean header row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onBack() },
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
                        imageVector = Icons.Default.CompareArrows,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Column {
                    Text(
                        text = if (lang == "BN") "সাপ্লায়ার খাতা প্রোফাইল" else "Supplier Pool Profile",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            letterSpacing = (-0.3).sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (lang == "BN") "পিছনে ফিরে যেতে এখানে চাপুন" else "Tap here to go back",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (!isEditing) {
                    IconButton(
                        onClick = { 
                            actionToConfirm = "DELETE"
                            pinCodeInput = ""
                            pinErrorText = null
                            showSecurityDialog = true 
                        },
                        modifier = Modifier.size(36.dp),
                        colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(18.dp))
                    }
                }
                
                IconButton(
                    onClick = { 
                        if (isEditing) {
                            actionToConfirm = "SAVE"
                            pinCodeInput = ""
                            pinErrorText = null
                            showSecurityDialog = true
                        } else {
                            isEditing = true
                        }
                    },
                    modifier = Modifier.size(36.dp),
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = if (isEditing) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                    )
                ) {
                    Icon(
                        imageVector = if (isEditing) Icons.Default.Save else Icons.Default.Edit,
                        contentDescription = "Edit Profile",
                        tint = if (isEditing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Avatar customizing card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            val avatarBgParsed = remember(editColor) {
                                try { Color(android.graphics.Color.parseColor(editColor)) } catch(e: Exception) { Color(0xFFFF9800) }
                            }
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(avatarBgParsed.copy(alpha = 0.15f))
                                    .border(2.dp, avatarBgParsed, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = editEmoji, fontSize = 24.sp)
                            }
                            
                            Column(modifier = Modifier.weight(1f)) {
                                if (!isEditing) {
                                    Text(
                                        text = supplier.name,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black)
                                    )
                                    Text(
                                        text = "Joined: ${SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date(supplier.timestamp))}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                } else {
                                    Text(
                                        text = if (lang == "BN") "লোগো কাস্টমাইজ করুন:" else "Customize logo:",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            if (!isEditing) {
                                Button(
                                    onClick = { showAddChoiceDialog = true },
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Icon(Icons.Filled.Add, contentDescription = "", modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(text = if (lang == "BN") "নতুন লেনদেন" else "New Transaction", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        if (!isEditing) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        if (currentSupplierBdtDue < -0.05) Color(0xFFFFECEB) else if (currentSupplierBdtDue > 0.05) Color(0xFFE3F2FD) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = if (currentSupplierBdtDue > 0.05) Icons.Default.Info else Icons.Default.Warning,
                                        contentDescription = "",
                                        tint = if (currentSupplierBdtDue < -0.05) Color(0xFFC62828) else if (currentSupplierBdtDue > 0.05) Color(0xFF1565C0) else MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = if (currentSupplierBdtDue > 0.05) {
                                            if (lang == "BN") "আমি পাবো / পাওনা (BDT)" else "Receivable (BDT)"
                                        } else if (currentSupplierBdtDue < -0.05) {
                                            if (lang == "BN") "আমি দেবো / বকেয়া (BDT)" else "Payable (BDT)"
                                        } else {
                                            if (lang == "BN") "কোনো বকেয়া নেই" else "No Due"
                                        },
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                        color = if (currentSupplierBdtDue < -0.05) Color(0xFFC62828) else if (currentSupplierBdtDue > 0.05) Color(0xFF1565C0) else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = "৳ ${currencyFormatter.format(Math.abs(currentSupplierBdtDue))}",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.ExtraBold),
                                    color = if (currentSupplierBdtDue < -0.05) Color(0xFFC62828) else if (currentSupplierBdtDue > 0.05) Color(0xFF1565C0) else Color(0xFF2E7D32)
                                )
                            }
                        }

                        if (isEditing) {
                            FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                                avatarEmojis.forEach { em ->
                                    Box(
                                        modifier = Modifier
                                            .padding(3.dp)
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(if (editEmoji == em) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                            .clickable { editEmoji = em },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(text = em, fontSize = 16.sp)
                                    }
                                }
                            }

                            FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                                avatarColors.forEach { colStr ->
                                    val colParsed = try { Color(android.graphics.Color.parseColor(colStr)) } catch(e: Exception) { Color.Gray }
                                    Box(
                                        modifier = Modifier
                                            .padding(3.dp)
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(colParsed)
                                            .clickable { editColor = colStr }
                                            .border(
                                                width = 2.dp,
                                                color = if (editColor == colStr) MaterialTheme.colorScheme.outline else Color.Transparent,
                                                shape = CircleShape
                                            )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Tab Switcher for different sub-registers (First turn initializer)
            if (!isEditing) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .padding(2.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        val tab1Bg = if (selectedProfileTab == 0) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                        val tab1Text = if (selectedProfileTab == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                        val tab3Bg = if (selectedProfileTab == 1) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                        val tab3Text = if (selectedProfileTab == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(32.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(tab1Bg)
                                .clickable { selectedProfileTab = 0 }
                                .padding(horizontal = 2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (lang == "BN") "লেনদেন সমূহ" else "Transactions",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                                color = tab1Text,
                                maxLines = 1
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(32.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(tab3Bg)
                                .clickable { selectedProfileTab = 1 }
                                .padding(horizontal = 2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (lang == "BN") "প্রোফাইল তথ্য" else "Profile Info",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                                color = tab3Text,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // Cards conditional content based on tabs
            if (isEditing) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedTextField(
                                value = editName,
                                onValueChange = { editName = it },
                                label = { Text(if (lang == "BN") "সাপ্লায়ার নাম" else "Supplier Name") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = editPhone,
                                onValueChange = { editPhone = it },
                                label = { Text(if (lang == "BN") "ফোন" else "Phone Number") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = editAddress,
                                onValueChange = { editAddress = it },
                                label = { Text(if (lang == "BN") "ঠিকানা" else "Office Address") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = editLicense,
                                onValueChange = { editLicense = it },
                                label = { Text(if (lang == "BN") "ট্রেড লাইসেন্স / রেজিঃ" else "Trade License / Tax ID") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = editNotes,
                                onValueChange = { editNotes = it },
                                label = { Text(if (lang == "BN") "অভ্যন্তরীণ খাতা নোটস" else "Secure Ledger Notes") },
                                minLines = 2,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            } else {
                if (selectedProfileTab == 1) {
                    // TAB 1 (Old Tab 2): EDITABLE PROFILE DETAILS READONLY PORTION
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = if (lang == "BN") "ব্যবসা ও সিকিউরিটি প্রোফাইল" else "Business & Security Coordinates",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                DetailFieldRow(lang = lang, labelBn = "ফোন", labelEn = "Phone Number", value = supplier.phone, icon = Icons.Default.Phone)
                                DetailFieldRow(lang = lang, labelBn = "অফিস ঠিকানা", labelEn = "Office Address", value = supplier.address.ifBlank { "N/A" }, icon = Icons.Default.Home)
                                DetailFieldRow(lang = lang, labelBn = "ট্রেড লাইসেন্স/রেজিস্ট্রেশন", labelEn = "Trade License / Tax ID", value = supplier.tradeLicense.ifBlank { "Unregistered" }, icon = Icons.Default.CardMembership)
                                DetailFieldRow(lang = lang, labelBn = "গোপন সিকিউরিটি নোটস", labelEn = "Security Ledger Notes", value = supplier.securityNotes.ifBlank { "None" }, icon = Icons.Default.Lock)
                            }
                        }
                    }
                }
            }

            // Audit Ledger Title (Conditional on profile tab)
            if (selectedProfileTab == 0) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (lang == "BN") "লেনদেন ও হিসাব খাতা" else "Supplier Fund Registry Audit",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            text = if (lang == "BN") "ডিপোজিট: ${deposits.size} টি" else "Records: ${deposits.size}",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                if (ledgerByDate.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                            Text(
                                text = if (lang == "BN") "কোনো ডিপোজিট রেকর্ড পাওয়া যায়নি।" else "No deposits or fund records found.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                } else {
                items(ledgerByDate.entries.toList(), key = { it.key }) { entry ->
                    val dateStr = entry.key
                    val itemsList = entry.value
                    Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = dateStr, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
                                val netChange = itemsList.filterIsInstance<SupplierLedgerItem.DepositItem>().sumOf { it.amountBdt } - 
                                            itemsList.filterIsInstance<SupplierLedgerItem.DisbursedItem>().sumOf { it.amountBdt }
                                Text(
                                    text = "Net: ৳${currencyFormatter.format(netChange)}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                                    color = if (netChange >= 0.0) Color(0xFF1B5E20) else Color(0xFFC62828)
                                )
                            }
                            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                            itemsList.forEach { ledgerItem ->
                                when (ledgerItem) {
                                    is SupplierLedgerItem.DepositItem -> {
                                        val isReceived = ledgerItem.transactionType == "SAR_RECEIVED" || ledgerItem.transactionType == "SAR_SETTLEMENT"
                                        val isSettlement = ledgerItem.transactionType == "BDT_SETTLEMENT"
                                        val labelText = if (isSettlement) {
                                            if (lang == "BN") "বকেয়া পরিশোধ" else "Dues Settled"
                                        } else {
                                            if (lang == "BN") "রিয়াল ক্রয় (Deposit)" else "Riyal Purchase"
                                        }
                                        val labelColor = if (isSettlement) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary
                                        val amountSign = if (isSettlement) "-" else "+"
                                        val isDepExpanded = expandedDepId == ledgerItem.id

                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { expandedDepId = if (isDepExpanded) null else ledgerItem.id }
                                                .padding(vertical = 4.dp)
                                        ) {
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                Column {
                                                    val associatedBatch = walletBatches.find { it.supplierDepositId == ledgerItem.id }
                                                    val walletLedgerName = if (associatedBatch != null) {
                                                        walletLedgers.find { it.id == associatedBatch.ledgerId }?.name
                                                    } else null

                                                    Text(
                                                        text = labelText,
                                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                                        color = labelColor
                                                    )
                                                    if (ledgerItem.notes.isNotBlank()) {
                                                        Text(text = ledgerItem.notes, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                                    }
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        Text(
                                                            text = if (isSettlement) "Time: ${SimpleDateFormat("hh:mm a", Locale.US).format(Date(ledgerItem.timestamp))}" else "Rate: ${ledgerItem.rate} | Time: ${SimpleDateFormat("hh:mm a", Locale.US).format(Date(ledgerItem.timestamp))}",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.outline
                                                        )
                                                        if (walletLedgerName != null) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                                                            ) {
                                                                Text(
                                                                    text = walletLedgerName,
                                                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                                Column(horizontalAlignment = Alignment.End) {
                                                    if (!isSettlement) {
                                                        Text(text = "SAR ${ledgerItem.amountSar}", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                                                    }
                                                    Text(text = "$amountSign৳${currencyFormatter.format(if (isSettlement) ledgerItem.paidBdt else ledgerItem.amountBdt)}", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = labelColor)
                                                }
                                            }

                                            if (isDepExpanded) {
                                                Spacer(modifier = Modifier.height(4.dp))
                                                if (!isSettlement) {
                                                    val dueBdt = ledgerItem.amountBdt - ledgerItem.paidBdt
                                                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                            Text(text = if (lang == "BN") "সর্বমোট (BDT):" else "Total (BDT):", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)
                                                            Text(text = "৳${currencyFormatter.format(ledgerItem.amountBdt)}", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                        }
                                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                            Text(text = if (lang == "BN") "পরিশোধিত (BDT):" else "Paid (BDT):", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)
                                                            Text(text = "৳${currencyFormatter.format(ledgerItem.paidBdt)}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                                                        }
                                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                            Text(
                                                                text = if (lang == "BN") {
                                                                    if (dueBdt < -0.05) "বকেয়া (BDT):" else "পাওনা (BDT):"
                                                                } else {
                                                                    if (dueBdt < -0.05) "Due / Payable (BDT):" else "Overpaid / Receivable (BDT):"
                                                                },
                                                                fontSize = 11.sp,
                                                                fontWeight = FontWeight.Black,
                                                                color = MaterialTheme.colorScheme.outline
                                                            )
                                                            Text(text = "৳${currencyFormatter.format(Math.abs(dueBdt))}", fontSize = 11.sp, fontWeight = FontWeight.Black, color = if (dueBdt < -0.05) Color(0xFFC62828) else if (dueBdt > 0.05) Color(0xFF1565C0) else Color(0xFF2E7D32))
                                                        }
                                                    }
                                                    Spacer(modifier = Modifier.height(6.dp))
                                                }
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                                        .padding(8.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = if (lang == "BN") "ফান্ড হিসাব সংশোধন / ডিলিট" else "Fund adjustment / Delete",
                                                        fontSize = 11.sp,
                                                        color = MaterialTheme.colorScheme.outline,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                        IconButton(
                                                            onClick = {
                                                                depositToEdit = ledgerItem
                                                                editDepAmountSar = ledgerItem.amountSar.toString()
                                                                editDepRate = ledgerItem.rate.toString()
                                                                editDepNotes = ledgerItem.notes
                                                                editDepTxType = ledgerItem.transactionType
                                                            },
                                                            modifier = Modifier.size(32.dp)
                                                        ) {
                                                            Icon(Icons.Default.Edit, contentDescription = "Edit Deposit", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                                        }
                                                        IconButton(
                                                            onClick = {
                                                                depToDelete = ledgerItem
                                                                txActionToConfirm = "DELETE_DEP"
                                                                txPinCodeInput = ""
                                                                txPinErrorText = null
                                                                showTxSecurityDialog = true
                                                            },
                                                            modifier = Modifier.size(32.dp)
                                                        ) {
                                                            Icon(Icons.Default.Delete, contentDescription = "Delete Deposit", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    is SupplierLedgerItem.DisbursedItem -> {
                                        val tx = transactions.find { it.id == ledgerItem.txId }
                                        if (tx != null) {
                                            val isExpanded = expandedTxId == tx.id
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { expandedTxId = if (isExpanded) null else tx.id }
                                                    .padding(vertical = 4.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(text = if (lang == "BN") "বিতরণ (লেনদেন)" else "Routed Transfer", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = Color(0xFFC62828))
                                                        Text(text = "Rcv: ${ledgerItem.receiverName}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                                        Text(text = "Status: ${ledgerItem.status} | Time: ${SimpleDateFormat("hh:mm a", Locale.US).format(Date(ledgerItem.timestamp))}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                                        
                                                        val sarDue = tx.amountSar - tx.sarCollected
                                                        val bdtDue = tx.amountBdt - tx.bdtDisbursed
                                                        if (sarDue > 0.05 || bdtDue > 0.05) {
                                                            Row(
                                                                modifier = Modifier.padding(top = 2.dp),
                                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                            ) {
                                                                if (sarDue > 0.05) {
                                                                    Surface(
                                                                        shape = RoundedCornerShape(4.dp),
                                                                        color = Color(0xFFFFF3E0),
                                                                        contentColor = Color(0xFFE65100)
                                                                    ) {
                                                                        Text(
                                                                            text = if (lang == "BN") "রিয়াল বাকি: ${foreignCur}${DecimalFormat("#.##").format(sarDue)}" else "Uncollected: ${DecimalFormat("#.##").format(sarDue)} ${foreignCur}",
                                                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                                                        )
                                                                    }
                                                                }
                                                                if (bdtDue > 0.05) {
                                                                    Surface(
                                                                        shape = RoundedCornerShape(4.dp),
                                                                        color = Color(0xFFEBEFFB),
                                                                        contentColor = Color(0xFF254B8C)
                                                                    ) {
                                                                        Text(
                                                                            text = if (lang == "BN") "টাকা বাকি: ৳${DecimalFormat("#,##0").format(bdtDue)}" else "Unpaid: ৳${DecimalFormat("#,##0").format(bdtDue)}",
                                                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                                                        )
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        Column(horizontalAlignment = Alignment.End) {
                                                            Text(text = "SAR ${ledgerItem.amountSar}", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                                                            Text(text = "-৳${currencyFormatter.format(ledgerItem.amountBdt)}", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = Color(0xFFC62828))
                                                        }

                                                        // Removed edit/delete action row as requested.
                                                    }
                                                }

                                                if (isExpanded) {
                                                    Spacer(modifier = Modifier.height(8.dp))
                                                    Column(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                                            .padding(10.dp),
                                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                            Text(text = if (lang == "BN") "একাউন্ট নম্বর:" else "Account Number:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)
                                                            Text(text = "${tx.receiverAccountNo} (${tx.receiverAccountType})", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                        }
                                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                            Text(text = if (lang == "BN") "সাপ্লায়ার রেট:" else "Supplier Rate:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)
                                                            Text(text = "${tx.supplierRate}", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                        }
                                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                            Text(text = if (lang == "BN") "গ্রাহক রেট:" else "Customer Rate:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)
                                                        }

                                                        Spacer(modifier = Modifier.height(4.dp))
                                                        Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 2.dp))
                                                        
                                                        val sarDue = tx.amountSar - tx.sarCollected
                                                        val bdtDue = tx.amountBdt - tx.bdtDisbursed
                                                        
                                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                            Column {
                                                                Text(text = if (lang == "BN") "রিয়াল গ্রহণ (SAR):" else "Riyal Collected (SAR):", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)
                                                                Text(text = "${tx.sarCollected} / ${tx.amountSar} ${foreignCur}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (sarDue <= 0.05) Color(0xFF2E7D32) else Color(0xFFE65100))
                                                            }
                                                            if (sarDue > 0.05) {
                                                                Button(
                                                                    onClick = {
                                                                        viewModel.updateTransactionStatus(tx.copy(sarCollected = tx.amountSar), tx.status)
                                                                    },
                                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800)),
                                                                    contentPadding = PaddingValues(horizontal = 10.dp),
                                                                    modifier = Modifier.height(24.dp)
                                                                ) {
                                                                    Text(if (lang == "BN") "আদায় সম্পন্ন" else "Collect All", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                                                }
                                                            }
                                                        }
                                                        
                                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                            Column {
                                                                Text(text = if (lang == "BN") "বিতরণ (BDT):" else "Disbursed BDT:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)
                                                                Text(text = "৳ ${DecimalFormat("#,##0").format(tx.bdtDisbursed)} / ৳ ${DecimalFormat("#,##0").format(tx.amountBdt)} ${localCur}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (bdtDue <= 0.05) Color(0xFF2E7D32) else Color(0xFF254B8C))
                                                            }
                                                            if (bdtDue > 0.05) {
                                                                Button(
                                                                    onClick = {
                                                                        viewModel.updateTransactionStatus(tx.copy(bdtDisbursed = tx.amountBdt), tx.status)
                                                                    },
                                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF254B8C)),
                                                                    contentPadding = PaddingValues(horizontal = 10.dp),
                                                                    modifier = Modifier.height(24.dp)
                                                                ) {
                                                                    Text(if (lang == "BN") "বিতরণ সম্পন্ন" else "Disburse All", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                                                }
                                                            }
                                                        }
                                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                            Spacer(modifier = Modifier.width(1.dp))
                                                            Text(text = "${tx.customerRate}", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                        }
                                                        if (tx.notes.isNotBlank()) {
                                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                                Text(text = if (lang == "BN") "মন্তব্য:" else "Notes:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)
                                                                Text(text = tx.notes, fontSize = 11.sp)
                                                            }
                                                        }
                                                        Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 4.dp))
                                                        
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                                IconButton(
                                                                    onClick = {
                                                                        txToEdit = tx
                                                                        editAmountSar = tx.amountSar.toString()
                                                                        editCustomerRate = tx.customerRate.toString()
                                                                        editSupplierRate = tx.supplierRate.toString()
                                                                        editReceiverName = tx.receiverName
                                                                        editPhoneNum = tx.receiverPhone
                                                                        editReceiverAccountType = tx.receiverAccountType
                                                                        editReceiverAccountNo = tx.receiverAccountNo
                                                                        editTxNotes = tx.notes
                                                                        editStatus = tx.status
                                                                        editSupplierId = tx.supplierId
                                                                    },
                                                                    modifier = Modifier.size(36.dp)
                                                                ) {
                                                                    Icon(Icons.Default.Edit, contentDescription = "", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                                                }
                                                                IconButton(
                                                                    onClick = {
                                                                        txToDelete = tx
                                                                        txActionToConfirm = "DELETE"
                                                                        txPinCodeInput = ""
                                                                        txPinErrorText = null
                                                                        showTxSecurityDialog = true
                                                                    },
                                                                    modifier = Modifier.size(36.dp)
                                                                ) {
                                                                    Icon(Icons.Default.Delete, contentDescription = "", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                                                }
                                                            }
                                                            
                                                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                                if (tx.status != "Cancelled" && tx.status != "Delivered") {
                                                                    Button(
                                                                        onClick = {
                                                                            txToEdit = tx
                                                                            txActionToConfirm = "STATUS_DELIVER"
                                                                            txPinCodeInput = ""
                                                                            txPinErrorText = null
                                                                            showTxSecurityDialog = true
                                                                        },
                                                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                                                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                                        modifier = Modifier.height(28.dp)
                                                                    ) {
                                                                        Text(if (lang == "BN") "ডেলিভারি" else "Deliver", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                                                    }
                                                                }

                                                                if (tx.status == "Pending") {
                                                                    OutlinedButton(
                                                                        onClick = {
                                                                            txToEdit = tx
                                                                            txActionToConfirm = "STATUS_CANCEL"
                                                                            txPinCodeInput = ""
                                                                            txPinErrorText = null
                                                                            showTxSecurityDialog = true
                                                                        },
                                                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                                        modifier = Modifier.height(28.dp)
                                                                    ) {
                                                                        Text(if (lang == "BN") "বাতিল" else "Cancel", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                                                                    }
                                                                } else if (tx.status == "Cancelled") {
                                                                    OutlinedButton(
                                                                        onClick = {
                                                                            txToEdit = tx
                                                                            txActionToConfirm = "STATUS_PENDING"
                                                                            txPinCodeInput = ""
                                                                            txPinErrorText = null
                                                                            showTxSecurityDialog = true
                                                                        },
                                                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                                        modifier = Modifier.height(28.dp)
                                                                    ) {
                                                                        Text(if (lang == "BN") "পেন্ডিং করুন" else "Pending", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        } else {
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                Column {
                                                    Text(text = if (lang == "BN") "বিতরণ (লেনদেন)" else "Routed Transfer", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = Color(0xFFC62828))
                                                    Text(text = "Rcv: ${ledgerItem.receiverName}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                                    Text(text = "Status: ${ledgerItem.status} | Time: ${SimpleDateFormat("hh:mm a", Locale.US).format(Date(ledgerItem.timestamp))}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                                }
                                                Column(horizontalAlignment = Alignment.End) {
                                                    Text(text = "SAR ${ledgerItem.amountSar}", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                                                    Text(text = "-৳${currencyFormatter.format(ledgerItem.amountBdt)}", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = Color(0xFFC62828))
                                                }
                                            }
                                        }
                                    }
                                }
                                if (itemsList.last() != ledgerItem) {
                                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                                }
                            }
                        }
                    }
                }
            }

            // Margin bottom
            item {
                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
}

    // Security authorizer PIN gate dialog
    if (showSecurityDialog) {
        AlertDialog(
            onDismissRequest = { showSecurityDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.VerifiedUser, contentDescription = "", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                    Text(
                        text = if (lang == "BN") "সিকিউরিটি ভেরিফিকেশন" else "Security Verification",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (lang == "BN") "প্রোফাইল পরিবর্তন সংরক্ষণ করতে ৪ সংখ্যার পাসকোড (PIN) কোডটি লিখুন।" 
                               else "Enter your 4-digit operator PIN to securely save profile updates.",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    if (currentOperator?.isBiometricEnabled == true) {
                        com.safa.account.ui.BiometricTriggerButton(
                            lang = lang,
                            onSuccess = {
                                if (actionToConfirm == "DELETE") {
                                    onDelete()
                                    showSecurityDialog = false
                                } else {
                                    val updatedSupplier = supplier.copy(
                                        name = editName,
                                        phone = editPhone,
                                        address = editAddress,
                                        tradeLicense = editLicense,
                                        securityNotes = editNotes,
                                        avatarColor = editColor,
                                        avatarEmoji = editEmoji
                                    )
                                    onUpdate(updatedSupplier)
                                    showSecurityDialog = false
                                    isEditing = false
                                }
                            },
                            onError = { err ->
                                pinErrorText = err
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Text(
                            text = if (lang == "BN") "অথবা পিন দিয়ে করুন:" else "Or verify using PIN:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }

                    OutlinedTextField(
                        value = pinCodeInput,
                        onValueChange = { 
                            if (it.length <= 4 && it.all { c -> c.isDigit() }) {
                                pinCodeInput = it
                            }
                        },
                        placeholder = { Text("PIN Passcode") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = "") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (pinErrorText != null) {
                        Text(
                            text = pinErrorText!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (pinCodeInput == operatorPin) {
                            if (actionToConfirm == "DELETE") {
                                onDelete()
                                showSecurityDialog = false
                            } else {
                                val updatedSupplier = supplier.copy(
                                    name = editName,
                                    phone = editPhone,
                                    address = editAddress,
                                    tradeLicense = editLicense,
                                    securityNotes = editNotes,
                                    avatarColor = editColor,
                                    avatarEmoji = editEmoji
                                )
                                onUpdate(updatedSupplier)
                                showSecurityDialog = false
                                isEditing = false
                            }
                        } else {
                            pinErrorText = if (lang == "BN") "❌ ভুল পিন কোড! পুনরায় চেষ্টা করুন" else "❌ Incorrect PIN! Try again."
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (actionToConfirm == "DELETE") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(if (lang == "BN") "ভেরিফাই করুন" else "Verify")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSecurityDialog = false }) {
                    Text(if (lang == "BN") "বাতিল" else "Cancel")
                }
            }
        )
    }

    if (txToEdit != null) {
        val suppliersList by viewModel.suppliers.collectAsStateWithLifecycle(initialValue = emptyList())
        AlertDialog(
            onDismissRequest = { txToEdit = null },
            title = {
                Text(
                    text = if (lang == "BN") "লেনদেন সংশোধন" else "Edit Remittance Ledger",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 350.dp)
                ) {
                    item {
                        Text(text = if (lang == "BN") "সাপ্লায়ার ফান্ড নির্বাচন" else "Select Supplier Fund", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        var expanded by remember { mutableStateOf(false) }
                        val activeSupplier = suppliersList.find { it.id == editSupplierId }
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { expanded = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(activeSupplier?.name ?: (if (lang == "BN") "সাপ্লায়ার সিলেক্ট করুন" else "Select Supplier Account"))
                            }
                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                suppliersList.forEach { sup ->
                                    DropdownMenuItem(
                                        text = { Text(sup.name) },
                                        onClick = {
                                            editSupplierId = sup.id
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    item {
                        OutlinedTextField(
                            value = editAmountSar,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(if (lang == "BN") "রিয়াল পরিমাণ (SAR)" else "Riyal Amount (SAR)") },
                            trailingIcon = {
                                Icon(Icons.Default.Calculate, contentDescription = "Calculate", modifier = Modifier.clickable { isEditAmountCalCOpen = true })
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = editCustomerRate,
                                onValueChange = { editCustomerRate = it },
                                label = { Text(if (lang == "BN") "গ্রাহক রেট" else "Cust. Rate") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = editSupplierRate,
                                onValueChange = { editSupplierRate = it },
                                label = { Text(if (lang == "BN") "ক্রয় রেট" else "Supp. Rate") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    item {
                        OutlinedTextField(
                            value = editReceiverName,
                            onValueChange = { editReceiverName = it },
                            label = { Text(if (lang == "BN") "প্রাপকের নাম" else "Receiver Name") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    item {
                        OutlinedTextField(
                            value = editPhoneNum,
                            onValueChange = { editPhoneNum = it },
                            label = { Text(if (lang == "BN") "প্রাপকের ফোন" else "Receiver Phone") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    item {
                        Text(text = if (lang == "BN") "টাকা পরিশোধের ক্ষেত্র" else "Payment Channel", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        val methods = listOf("Bkash", "Nagad", "Rocket", "Bank Transfer", "Cash")
                        FlowRow(modifier = Modifier.fillMaxWidth()) {
                            methods.forEach { m ->
                                FilterChip(
                                    selected = editReceiverAccountType == m,
                                    onClick = { editReceiverAccountType = m },
                                    label = { Text(m) },
                                    modifier = Modifier.padding(2.dp)
                                )
                            }
                        }
                    }

                    item {
                        OutlinedTextField(
                            value = editReceiverAccountNo,
                            onValueChange = { editReceiverAccountNo = it },
                            label = { Text(if (lang == "BN") "হিসাব নম্বর" else "Account Number") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = editSarCollected,
                                onValueChange = { editSarCollected = it },
                                label = { Text(if (lang == "BN") "রিয়াল গ্রহণ (SAR)" else "Riyal Received") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = editBdtDisbursed,
                                onValueChange = { editBdtDisbursed = it },
                                label = { Text(if (lang == "BN") "টাকা পাঠানো (BDT)" else "BDT Paid") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    item {
                        OutlinedTextField(
                            value = editTxNotes,
                            onValueChange = { editTxNotes = it },
                            label = { Text(if (lang == "BN") "অতিরিক্ত মন্তব্য" else "Notes / Description") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    item {
                        Text(text = if (lang == "BN") "স্ট্যাটাস পরিবর্তন" else "Transaction Status", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf("Pending", "Delivered", "Cancelled").forEach { statusLabel ->
                                FilterChip(
                                    selected = editStatus == statusLabel,
                                    onClick = { editStatus = statusLabel },
                                    label = { Text(statusLabel) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        txActionToConfirm = "EDIT"
                        txPinCodeInput = ""
                        txPinErrorText = null
                        showTxSecurityDialog = true
                    }
                ) {
                    Text(if (lang == "BN") "যাচাই করুন" else "Save Changes")
                }
            },
            dismissButton = {
                TextButton(onClick = { txToEdit = null }) {
                    Text(if (lang == "BN") "বাতিল" else "Cancel")
                }
            }
        )
    }

    if (isEditAmountCalCOpen) {
        CalculatorDialog(
            initialValue = editAmountSar,
            title = if (lang == "BN") "রিয়াল পরিমাণ (SAR)" else "Riyal Amount (SAR)",
            lang = lang,
            onDismiss = { isEditAmountCalCOpen = false },
            onConfirm = { result ->
                editAmountSar = result
                isEditAmountCalCOpen = false
            }
        )
    }

    if (depositToEdit != null) {
        AlertDialog(
            onDismissRequest = { depositToEdit = null },
            title = {
                Text(
                    text = if (lang == "BN") "ফান্ড হিসাব সংশোধন" else "Edit Supplier Fund / Deposit",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(text = if (lang == "BN") "ফান্ড ধরন" else "Fund Type", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf("SAR_GIVEN" to "Buy / Deposit", "SAR_RECEIVED" to "Received / Settlement").forEach { (typeKey, typeLabel) ->
                            FilterChip(
                                selected = editDepTxType == typeKey,
                                onClick = { editDepTxType = typeKey },
                                label = { Text(typeLabel) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    OutlinedTextField(
                        value = editDepAmountSar,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(if (lang == "BN") "রিয়াল পরিমাণ (SAR)" else "Riyal Amount (SAR)") },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Default.Calculate,
                                contentDescription = "",
                                modifier = Modifier.clickable { isEditDepCalCOpen = true }
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (isSupplierRateEnabled) {
                        OutlinedTextField(
                            value = editDepRate,
                            onValueChange = { editDepRate = it },
                            label = { Text(if (lang == "BN") "রেট ${localCur}" else "Exchange Rate ${localCur}") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        LaunchedEffect(Unit) { editDepRate = "1.0" }
                    }

                    OutlinedTextField(
                        value = editDepNotes,
                        onValueChange = { editDepNotes = it },
                        label = { Text(if (lang == "BN") "অতিরিক্ত মন্তব্য" else "Notes / Remarks") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        txActionToConfirm = "EDIT_DEP"
                        txPinCodeInput = ""
                        txPinErrorText = null
                        showTxSecurityDialog = true
                    }
                ) {
                    Text(if (lang == "BN") "যাচাই করুন" else "Save Changes")
                }
            },
            dismissButton = {
                TextButton(onClick = { depositToEdit = null }) {
                    Text(if (lang == "BN") "বাতিল" else "Cancel")
                }
            }
        )
    }

    if (isEditDepCalCOpen) {
        CalculatorDialog(
            initialValue = editDepAmountSar,
            title = if (lang == "BN") "রিয়াল পরিমাণ (SAR)" else "Riyal Amount (SAR)",
            lang = lang,
            onDismiss = { isEditDepCalCOpen = false },
            onConfirm = { result ->
                editDepAmountSar = result
                isEditDepCalCOpen = false
            }
        )
    }

    if (showAddChoiceDialog) {
        AlertDialog(
            onDismissRequest = { showAddChoiceDialog = false },
            title = {
                Text(
                    text = if (lang == "BN") "লেনদেনের ধরণ নির্বাচন করুন" else "Select Transaction Type",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Option 1: Buy ${foreignCur}(নতুন ফান্ড ক্রয়)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                buyTransactionType = "SAR_GIVEN"
                                isAddingFund = true
                                showAddChoiceDialog = false 
                            },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.TrendingDown,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (lang == "BN") "নতুন ফান্ড ক্রয়" else "New ${foreignCur}Purchase",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = if (lang == "BN") "সাপ্লায়ার থেকে নতুন রিয়াল বা ফান্ড ক্রয় করুন।" else "Purchase new ${foreignCur}funds from this supplier.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }

                    val isDue = currentSupplierBdtDue < -0.05
                    val isReceivable = currentSupplierBdtDue > 0.05

                    if (isDue || isReceivable) {
                        // Option 2: Due Paid or Receivable Settle (বকেয়া পরিশোধ / পাওনা আদায়)
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    buyTransactionType = "SAR_RECEIVED"
                                    isAddingFund = true
                                    showAddChoiceDialog = false 
                                },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(if (isDue) Color(0xFFFFECEB) else Color(0xFFE8F5E9)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PriceCheck,
                                        contentDescription = null,
                                        tint = if (isDue) Color(0xFFC62828) else Color(0xFF2E7D32),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (isDue) {
                                            if (lang == "BN") "বকেয়া মূল্য পরিশোধ" else "Clear Supplier Due"
                                        } else {
                                            if (lang == "BN") "পাওনা আদায়" else "Collect Receivable"
                                        },
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        color = if (isDue) Color(0xFFC62828) else Color(0xFF2E7D32)
                                    )
                                    Text(
                                        text = if (isDue) {
                                            if (lang == "BN") "সাপ্লায়ারের পূর্বের বকেয়া পরিশোধ বা ক্যাশ পেমেন্ট করুন।" else "Clear outstanding ${localCur}payables to this supplier."
                                        } else {
                                            if (lang == "BN") "সাপ্লায়ার থেকে আপনার পাওনা টাকা বুঝে নিন বা ক্যাশ গ্রহণ করুন।" else "Collect or settle your receivable from this supplier."
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAddChoiceDialog = false }) {
                    Text(if (lang == "BN") "বন্ধ করুন" else "Cancel")
                }
            }
        )
    }

    if (showTxSecurityDialog) {
        AlertDialog(
            onDismissRequest = { showTxSecurityDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.VerifiedUser, contentDescription = "", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                    Text(
                        text = if (lang == "BN") "সিকিউরিটি ভেরিফিকেশন" else "Security Challenge",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val descText = when (txActionToConfirm) {
                        "DELETE" -> if (lang == "BN") "লেনদেনটি চিরতরে মুছে ফেলার জন্য আপনার ৪-ডিজিটের সিকিউরিটি পিন দিন।" else "Enter your 4-digit operator PIN to permanently delete this transaction."
                        "EDIT" -> if (lang == "BN") "লেনদেনের পরিবর্তনসমূহ ডাটাবেজে সংরক্ষণ করতে পিন দিন।" else "Enter your 4-digit operator PIN to securely save transaction updates."
                        "DELETE_DEP" -> if (lang == "BN") "ফান্ড হিসাব চিরতরে মুছে ফেলার জন্য আপনার ৪-ডিজিটের পিন দিন।" else "Enter your 4-digit operator PIN to permanently delete this fund/deposit."
                        "EDIT_DEP" -> if (lang == "BN") "ফান্ড হিসাব সংশোধন সুরক্ষিত করতে আপনার ৪-ডিজিটের পিন দিন।" else "Enter your 4-digit operator PIN to securely edit this fund/deposit."
                        else -> if (lang == "BN") "লেনদেনের অবস্থা (Status) পরিবর্তন করতে পিন দিন।" else "Enter your PIN to apply status modification."
                    }
                    Text(text = descText, style = MaterialTheme.typography.bodyMedium)

                    if (currentOperator?.isBiometricEnabled == true) {
                        val onSuccessAction = {
                            when (txActionToConfirm) {
                                "DELETE" -> {
                                    val id = txToDelete?.id ?: txToEdit?.id
                                    if (id != null) {
                                        viewModel.deleteTransaction(id)
                                    }
                                    txToDelete = null
                                    txToEdit = null
                                    showTxSecurityDialog = false
                                }
                                "EDIT" -> {
                                    if (txToEdit != null) {
                                        val amt = editAmountSar.toDoubleOrNull() ?: 0.0
                                        val cRate = editCustomerRate.toDoubleOrNull() ?: 0.0
                                        val sRate = editSupplierRate.toDoubleOrNull() ?: 0.0
                                        val col = editSarCollected.toDoubleOrNull() ?: amt
                                        val dis = editBdtDisbursed.toDoubleOrNull() ?: (amt * cRate)
                                        val updatedTx = txToEdit!!.copy(
                                            amountSar = amt,
                                            customerRate = cRate,
                                            supplierRate = sRate,
                                            amountBdt = amt * cRate,
                                            receiverName = editReceiverName,
                                            receiverPhone = editPhoneNum,
                                            receiverAccountType = editReceiverAccountType,
                                            receiverAccountNo = editReceiverAccountNo,
                                            notes = editTxNotes,
                                            status = editStatus,
                                            supplierId = editSupplierId ?: txToEdit!!.supplierId,
                                            sarCollected = col,
                                            bdtDisbursed = dis
                                        )
                                        viewModel.updateTransactionStatus(updatedTx, editStatus)
                                    }
                                    txToEdit = null
                                    showTxSecurityDialog = false
                                }
                                "STATUS_DELIVER" -> {
                                    if (txToEdit != null) {
                                        viewModel.updateTransactionStatus(txToEdit!!, "Delivered")
                                    }
                                    txToEdit = null
                                    showTxSecurityDialog = false
                                }
                                "STATUS_CANCEL" -> {
                                    if (txToEdit != null) {
                                        viewModel.updateTransactionStatus(txToEdit!!, "Cancelled")
                                    }
                                    txToEdit = null
                                    showTxSecurityDialog = false
                                }
                                "STATUS_PENDING" -> {
                                    if (txToEdit != null) {
                                        viewModel.updateTransactionStatus(txToEdit!!, "Pending")
                                    }
                                    txToEdit = null
                                    showTxSecurityDialog = false
                                }
                                "DELETE_DEP" -> {
                                    if (depToDelete != null) {
                                        viewModel.deleteSupplierDeposit(depToDelete!!.id)
                                    }
                                    depToDelete = null
                                    showTxSecurityDialog = false
                                }
                                "EDIT_DEP" -> {
                                    if (depositToEdit != null) {
                                        val amt = editDepAmountSar.toDoubleOrNull() ?: 0.0
                                        val rateValue = editDepRate.toDoubleOrNull() ?: 0.0
                                        val updatedDep = SupplierDeposit(
                                            id = depositToEdit!!.id,
                                            supplierId = supplier.id,
                                            amountSar = amt,
                                            rate = rateValue,
                                            amountBdt = amt * rateValue,
                                            transactionType = editDepTxType,
                                            notes = editDepNotes,
                                            timestamp = depositToEdit!!.timestamp
                                        )
                                        viewModel.updateSupplierDeposit(updatedDep)
                                    }
                                    depositToEdit = null
                                    showTxSecurityDialog = false
                                }
                            }
                        }
                        com.safa.account.ui.BiometricTriggerButton(
                            lang = lang,
                            onSuccess = onSuccessAction,
                            onError = { err ->
                                txPinErrorText = err
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Text(
                            text = if (lang == "BN") "অথবা পিন দিয়ে করুন:" else "Or verify using PIN:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }

                    OutlinedTextField(
                        value = txPinCodeInput,
                        onValueChange = { 
                            if (it.length <= 4 && it.all { c -> c.isDigit() }) {
                                txPinCodeInput = it
                            }
                        },
                        placeholder = { Text("PIN") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = "") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (txPinErrorText != null) {
                        Text(
                            text = txPinErrorText!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (txPinCodeInput == operatorPin) {
                            when (txActionToConfirm) {
                                "DELETE" -> {
                                    val id = txToDelete?.id ?: txToEdit?.id
                                    if (id != null) {
                                        viewModel.deleteTransaction(id)
                                    }
                                    txToDelete = null
                                    txToEdit = null
                                    showTxSecurityDialog = false
                                }
                                "EDIT" -> {
                                    if (txToEdit != null) {
                                        val amt = editAmountSar.toDoubleOrNull() ?: 0.0
                                        val cRate = editCustomerRate.toDoubleOrNull() ?: 0.0
                                        val sRate = editSupplierRate.toDoubleOrNull() ?: 0.0
                                        val col = editSarCollected.toDoubleOrNull() ?: amt
                                        val dis = editBdtDisbursed.toDoubleOrNull() ?: (amt * cRate)
                                        val updatedTx = txToEdit!!.copy(
                                            amountSar = amt,
                                            customerRate = cRate,
                                            supplierRate = sRate,
                                            amountBdt = amt * cRate,
                                            receiverName = editReceiverName,
                                            receiverPhone = editPhoneNum,
                                            receiverAccountType = editReceiverAccountType,
                                            receiverAccountNo = editReceiverAccountNo,
                                            notes = editTxNotes,
                                            status = editStatus,
                                            supplierId = editSupplierId ?: txToEdit!!.supplierId,
                                            sarCollected = col,
                                            bdtDisbursed = dis
                                        )
                                        viewModel.updateTransactionStatus(updatedTx, editStatus)
                                    }
                                    txToEdit = null
                                    showTxSecurityDialog = false
                                }
                                "STATUS_DELIVER" -> {
                                    if (txToEdit != null) {
                                        viewModel.updateTransactionStatus(txToEdit!!, "Delivered")
                                    }
                                    txToEdit = null
                                    showTxSecurityDialog = false
                                }
                                "STATUS_CANCEL" -> {
                                    if (txToEdit != null) {
                                        viewModel.updateTransactionStatus(txToEdit!!, "Cancelled")
                                    }
                                    txToEdit = null
                                    showTxSecurityDialog = false
                                }
                                "STATUS_PENDING" -> {
                                    if (txToEdit != null) {
                                        viewModel.updateTransactionStatus(txToEdit!!, "Pending")
                                    }
                                    txToEdit = null
                                    showTxSecurityDialog = false
                                }
                                "DELETE_DEP" -> {
                                    if (depToDelete != null) {
                                        viewModel.deleteSupplierDeposit(depToDelete!!.id)
                                    }
                                    depToDelete = null
                                    showTxSecurityDialog = false
                                }
                                "EDIT_DEP" -> {
                                    if (depositToEdit != null) {
                                        val amt = editDepAmountSar.toDoubleOrNull() ?: 0.0
                                        val rateValue = editDepRate.toDoubleOrNull() ?: 0.0
                                        val updatedDep = SupplierDeposit(
                                            id = depositToEdit!!.id,
                                            supplierId = supplier.id,
                                            amountSar = amt,
                                            rate = rateValue,
                                            amountBdt = amt * rateValue,
                                            transactionType = editDepTxType,
                                            notes = editDepNotes,
                                            timestamp = depositToEdit!!.timestamp
                                        )
                                        viewModel.updateSupplierDeposit(updatedDep)
                                    }
                                    depositToEdit = null
                                    showTxSecurityDialog = false
                                }
                            }
                        } else {
                            txPinErrorText = if (lang == "BN") "❌ ভুল পিন কোড! পুনরায় চেষ্টা করুন" else "❌ Incorrect PIN! Try again."
                        }
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(if (lang == "BN") "নিশ্চিত করুন" else "Confirm")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTxSecurityDialog = false }) {
                    Text(if (lang == "BN") "বাতিল" else "Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFundPage(
    supplierName: String,
    lang: String,
    buyTransactionType: String,
    onTransactionTypeChange: (String) -> Unit,
    buyAmountSarInput: String,
    onAmountChange: (String) -> Unit,
    buyRateInput: String,
    onRateChange: (String) -> Unit,
    buyNotesInput: String,
    onNotesChange: (String) -> Unit,
    buyPaidBdtInput: String,
    onPaidBdtChange: (String) -> Unit,
    walletLedgers: List<WalletLedger>,
    selectedLedgerId: Int,
    onLedgerIdChange: (Int) -> Unit,
    isSupplierRateEnabled: Boolean = true,
    onCancel: () -> Unit,
    onSave: (Long, String?) -> Unit
) {
    val sarVal = buyAmountSarInput.toDoubleOrNull() ?: 0.0
    val rateVal = buyRateInput.toDoubleOrNull() ?: 0.0
    val bdtCalculated = sarVal * rateVal
    
    LaunchedEffect(sarVal, rateVal) {
        if (bdtCalculated > 0) {
            onPaidBdtChange(bdtCalculated.toString())
        } else {
            onPaidBdtChange("")
        }
    }
    
    val paidVal = buyPaidBdtInput.toDoubleOrNull() ?: 0.0
    val dueBdt = bdtCalculated - paidVal
    val currencyFormatter = remember { DecimalFormat("#,##0.00") }
    var currentStep by remember { mutableStateOf(1) }

    val mContext = androidx.compose.ui.platform.LocalContext.current
    var selectedTimestamp by remember { mutableStateOf(System.currentTimeMillis()) }
    var selectedDocumentName by remember { mutableStateOf<String?>(null) }
    val documentPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            selectedDocumentName = "doc_" + System.currentTimeMillis().toString().takeLast(6) + ".jpg"
        }
    }
    
    var showAmountCalc by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Step indicator & title
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = {
                    if (currentStep > 1) {
                        currentStep = 1
                    } else {
                        onCancel()
                    }
                }
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (lang == "BN") {
                        if (currentStep == 1) "ধাপ ১: বিবরণ প্রদান" else "ধাপ ২: পেমেন্ট"
                    } else {
                        if (currentStep == 1) "Step 1: Details" else "Step 2: Payment"
                    },
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = supplierName,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.width(48.dp))
        }

        LinearProgressIndicator(
            progress = if (currentStep == 1) 0.5f else 1.0f,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .height(6.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.outlineVariant
        )

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            if (currentStep == 1) {
                item {
                    val sdf = remember { java.text.SimpleDateFormat("dd MMM yyyy", Locale.US) }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(
                                    text = if (lang == "BN") "পরিমাণ (SAR)" else "Amount (SAR)",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.outline
                                )
                                Card(
                                    onClick = {
                                        val calendar = Calendar.getInstance().apply { timeInMillis = selectedTimestamp }
                                        val picker = android.app.DatePickerDialog(
                                            mContext,
                                            { _, year, month, dayOfMonth ->
                                                val selCal = Calendar.getInstance().apply {
                                                    set(Calendar.YEAR, year)
                                                    set(Calendar.MONTH, month)
                                                    set(Calendar.DAY_OF_MONTH, dayOfMonth)
                                                }
                                                selectedTimestamp = selCal.timeInMillis
                                            },
                                            calendar.get(Calendar.YEAR),
                                            calendar.get(Calendar.MONTH),
                                            calendar.get(Calendar.DAY_OF_MONTH)
                                        )
                                        picker.datePicker.maxDate = System.currentTimeMillis()
                                        picker.show()
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.5f))
                                ) {
                                    Row(modifier = Modifier.padding(horizontal=8.dp, vertical=4.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.DateRange, contentDescription="", modifier = Modifier.size(14.dp), tint=MaterialTheme.colorScheme.primary)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(sdf.format(Date(selectedTimestamp)), style=MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                            
                            Box(modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(
                                    value = buyAmountSarInput,
                                    onValueChange = {}, // managed by dialog
                                    readOnly = true,
                                    trailingIcon = { Icon(Icons.Default.Calculate, contentDescription = "Calculate", tint = MaterialTheme.colorScheme.primary) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                                    ),
                                    textStyle = MaterialTheme.typography.headlineLarge.copy(
                                        fontWeight = FontWeight.Black,
                                        fontSize = 32.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    ),
                                    placeholder = {
                                        Text(
                                            text = "0.00",
                                            style = MaterialTheme.typography.headlineLarge.copy(
                                                fontWeight = FontWeight.Black,
                                                fontSize = 32.sp,
                                                color = MaterialTheme.colorScheme.outlineVariant,
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                            ),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    },
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                // Invisible click overlay
                                Box(modifier = Modifier.matchParentSize().clickable { showAmountCalc = true })
                            }

                            OutlinedTextField(
                                value = buyNotesInput,
                                onValueChange = onNotesChange,
                                label = { Text(if (lang == "BN") "অতিরিক্ত মন্তব্য (নোট)" else "Notes / Description") },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = "") },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            
                            // Attachment document card option
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AttachFile,
                                            contentDescription = "",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Column {
                                            Text(
                                                text = if (lang == "BN") "ডকুমেন্ট আপলোড" else "Upload Document",
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            )
                                            Text(
                                                text = selectedDocumentName ?: (if (lang == "BN") "রিসিট বা কোনো ছবি যুক্ত করুন..." else "No attachment file selected"),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (selectedDocumentName != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                    if (selectedDocumentName == null) {
                                        TextButton(onClick = { documentPickerLauncher.launch("*/*") }) {
                                            Icon(Icons.Default.Add, contentDescription = "", modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(if (lang == "BN") "যুক্ত করুন" else "Add File")
                                        }
                                    } else {
                                        IconButton(onClick = { selectedDocumentName = null }) {
                                            Icon(Icons.Default.Close, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                item {
                    Button(
                        onClick = { currentStep = 2 },
                        modifier = Modifier.fillMaxWidth().height(36.dp),
                        shape = RoundedCornerShape(12.dp),
                        enabled = sarVal > 0
                    ) {
                        Text(if (lang == "BN") "পরবর্তী ধাপ" else "Next Step", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(Icons.Default.ArrowForward, contentDescription = "", modifier = Modifier.size(16.dp))
                    }
                }
            } else {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            if (isSupplierRateEnabled) {
                                OutlinedTextField(
                                    value = buyRateInput,
                                    onValueChange = onRateChange,
                                    label = { Text(if (lang == "BN") "বিনিময় রেট (BDT/SAR)" else "Exchange Rate") },
                                    leadingIcon = { Icon(Icons.Default.PriceChange, contentDescription = "") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else {
                                LaunchedEffect(Unit) { onRateChange("1.0") }
                            }

                            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = buyPaidBdtInput,
                                    onValueChange = onPaidBdtChange,
                                    label = { Text(if (lang == "BN") "গ্রহণ (BDT)" else "Received Amount (BDT)") },
                                    leadingIcon = { Icon(Icons.Default.Money, contentDescription = "") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            
                            if (Math.abs(dueBdt) > 0.05) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (dueBdt < -0.05) MaterialTheme.colorScheme.errorContainer.copy(alpha=0.3f) else MaterialTheme.colorScheme.primaryContainer.copy(alpha=0.3f))
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = if (dueBdt < -0.05) {
                                            if (lang == "BN") "বকেয়া (BDT):" else "Payable (BDT):"
                                        } else {
                                            if (lang == "BN") "পাওনা (BDT):" else "Receivable (BDT):"
                                        },
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        color = if (dueBdt < -0.05) Color(0xFFC62828) else Color(0xFF1565C0)
                                    )
                                    Text(
                                        text = "৳${currencyFormatter.format(Math.abs(dueBdt))}",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Black),
                                        color = if (dueBdt < -0.05) Color(0xFFC62828) else Color(0xFF1565C0)
                                    )
                                }
                            }
                            
                            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            
                            Text(text = if (lang == "BN") "ওয়ালেট" else "Wallet", style = MaterialTheme.typography.labelSmall)
                            var expandedSupDropdown by remember { mutableStateOf(false) }
                            val activeLedger = walletLedgers.find { it.id == selectedLedgerId }
                            
                            Box(modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(
                                    onClick = { expandedSupDropdown = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
                                ) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.AccountBalanceWallet, contentDescription = "", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = if (activeLedger != null) activeLedger.name else (if (lang == "BN") "ওয়ালেট নির্বাচন করুন" else "Select Wallet"),
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = "")
                                    }
                                }
                                
                                DropdownMenu(
                                    expanded = expandedSupDropdown,
                                    onDismissRequest = { expandedSupDropdown = false },
                                    modifier = Modifier.fillMaxWidth(0.85f)
                                ) {
                                    walletLedgers.forEach { ledgerNode ->
                                        val isSelected = ledgerNode.id == selectedLedgerId
                                        DropdownMenuItem(
                                            text = {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(Icons.Default.AccountBalanceWallet, contentDescription = "", tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(ledgerNode.name, color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                                                }
                                            },
                                            onClick = {
                                                onLedgerIdChange(ledgerNode.id)
                                                expandedSupDropdown = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Button(
                        onClick = {
                            if (sarVal > 0 && rateVal > 0 && selectedLedgerId != 0) {
                                onTransactionTypeChange("SAR_GIVEN")
                                onSave(selectedTimestamp, selectedDocumentName)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(36.dp),
                        shape = RoundedCornerShape(12.dp),
                        enabled = sarVal > 0 && rateVal > 0 && selectedLedgerId != 0
                    ) {
                        Text(if (lang == "BN") "সংরক্ষণ করুন" else "Save Record", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (showAmountCalc) {
        CalculatorDialog(
            initialValue = buyAmountSarInput,
            title = if (lang == "BN") "পরিমাণ (SAR)" else "Amount (SAR)",
            lang = lang,
            onDismiss = { showAmountCalc = false },
            onConfirm = { result -> 
                onAmountChange(result)
                showAmountCalc = false 
            }
        )
    }
}

@Composable
fun AddSupplierPage(
    lang: String,
    nameInput: String,
    onNameChange: (String) -> Unit,
    phoneInput: String,
    onPhoneChange: (String) -> Unit,
    addressInput: String,
    onAddressChange: (String) -> Unit,
    onContactPicker: () -> Unit,
    onCancel: () -> Unit,
    onSubmit: () -> Unit,
    viewModel: HundiViewModel
) {
    DisposableEffect(Unit) {
        viewModel.setSubPageActive(true)
        onDispose {
            viewModel.setSubPageActive(false)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Toolbar with Back button and Contacts pick actions
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onCancel) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
                Text(
                    text = if (lang == "BN") "নতুন সাপ্লায়ার যোগ" else "Add New Supplier Profile",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
            TextButton(onClick = onContactPicker) {
                Icon(Icons.Default.Contacts, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (lang == "BN") "কন্টাক্টস" else "Contacts", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
            }
        }

        // Horizontal visual divider for branding
        LinearProgressIndicator(
            progress = 1.0f,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .height(4.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.outlineVariant
        )

        // Fields Scroll Card Container
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Text(
                        text = if (lang == "BN") "সাপ্লায়ার বা মহাজন খাতা বিবরণ" else "Supplier Accounting Information",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                item {
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = onNameChange,
                        label = { Text(if (lang == "BN") "সাপ্লায়ার বা প্রতিষ্ঠানের নাম" else "Supplier or Business Name") },
                        placeholder = { Text("e.g. Robin Exchange") },
                        leadingIcon = { Icon(Icons.Default.CorporateFare, contentDescription = "", tint = MaterialTheme.colorScheme.primary) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("supplier_name_field")
                    )
                }

                item {
                    OutlinedTextField(
                        value = phoneInput,
                        onValueChange = onPhoneChange,
                        label = { Text(if (lang == "BN") "মোবাইল নম্বর" else "Contact Phone Number") },
                        placeholder = { Text("e.g. +88017xxxxxxxx / +966xxxxx") },
                        leadingIcon = { Icon(Icons.Default.Phone, contentDescription = "", tint = MaterialTheme.colorScheme.primary) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("supplier_phone_field")
                    )
                }

                item {
                    OutlinedTextField(
                        value = addressInput,
                        onValueChange = onAddressChange,
                        label = { Text(if (lang == "BN") "বর্তমান ঠিকানা (শাখা / অফিস)" else "Address (Branch / Office)") },
                        placeholder = { Text("e.g. Dhaka, Bangladesh / Riyadh, KSA") },
                        leadingIcon = { Icon(Icons.Default.Home, contentDescription = "", tint = MaterialTheme.colorScheme.primary) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("supplier_address_field")
                    )
                }
            }
        }

        // Action Toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onCancel,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
            ) {
                Text(
                    text = if (lang == "BN") "বাতিল" else "Cancel",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
            }

            Button(
                onClick = onSubmit,
                shape = RoundedCornerShape(12.dp),
                enabled = nameInput.isNotBlank() && phoneInput.isNotBlank(),
                modifier = Modifier
                    .weight(1.5f)
                    .height(36.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.Check, contentDescription = "")
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (lang == "BN") "সংরক্ষণ করুন" else "Save",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}
