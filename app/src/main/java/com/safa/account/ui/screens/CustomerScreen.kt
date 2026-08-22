package com.safa.account.ui.screens
import com.safa.account.ui.localization.AndroidStringCatalog

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safa.account.data.model.Customer
import com.safa.account.data.model.RemittanceTransaction
import com.safa.account.data.money.MoneyMath
import com.safa.account.ui.viewmodel.SafaViewModel
import com.safa.account.ui.BiometricTriggerButton
import com.safa.account.ui.screens.CalculatorDialog
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.math.BigDecimal
import java.util.*
import java.io.File
import java.io.FileOutputStream
import android.widget.Toast

private val CUSTOMER_MONEY_THRESHOLD: BigDecimal = MoneyMath.amount("0.05")
private val CUSTOMER_DETAIL_THRESHOLD: BigDecimal = MoneyMath.amount("0.01")
private fun isDueOnlyAmount(value: String): Boolean =
    runCatching { MoneyMath.isZeroAmount(value) }.getOrDefault(false)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerScreen(
    viewModel: SafaViewModel,
    modifier: Modifier = Modifier,
    isProfileView: Boolean = false,
    isAddView: Boolean = false
) {
    val customers by viewModel.customers.collectAsStateWithLifecycle()
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()
    val lang by viewModel.currentLanguage.collectAsStateWithLifecycle()
    val currentOperator by viewModel.currentOperator.collectAsStateWithLifecycle()
    val foreignCur by viewModel.selectedForeignCurrency.collectAsStateWithLifecycle()
    val localCur by viewModel.selectedLocalCurrency.collectAsStateWithLifecycle()
    val isRateBasedModeEnabled by viewModel.isRateBasedModeEnabled.collectAsStateWithLifecycle()
    val isSupplierRateEnabled by viewModel.isSupplierRateEnabled.collectAsStateWithLifecycle()
    
    val selectedCustomerIdForProfile by viewModel.selectedCustomerIdForProfile.collectAsStateWithLifecycle()

    if (currentOperator != null && !currentOperator!!.canViewCustomers) {
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
    var selectedSortOption by remember { mutableStateOf("Newest") } // Newest, Oldest, A-Z, Due, Advance
    var selectedFilterStatus by remember { mutableStateOf("All") } // All, Due, Advance

    // Forms to add customer
    var nameInput by remember { mutableStateOf("") }
    var phoneInput by remember { mutableStateOf("") }
    var addressInput by remember { mutableStateOf("") }

    val mContext = androidx.compose.ui.platform.LocalContext.current
    val contactPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val uri = result.data?.data
            if (uri != null) {
                val projection = arrayOf(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER, android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                mContext.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
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

    val filteredCustomers = remember(customers, searchQuery, selectedSortOption, selectedFilterStatus, transactions) {
        var list = if (searchQuery.isBlank()) customers
        else customers.filter {
            it.name.contains(searchQuery, ignoreCase = true) || 
            it.phone.contains(searchQuery, ignoreCase = true) ||
            it.address.contains(searchQuery, ignoreCase = true)
        }

        // Apply filters based on calculated balance
        if (selectedFilterStatus != "All") {
            list = list.filter { customer ->
                val customerTxs = transactions.filter { it.customerId == customer.id }
                val totalSarSpent = customerTxs.fold(MoneyMath.ZERO_AMOUNT) { total, tx -> MoneyMath.add(total, tx.amountSar) }
                val totalSarCollected = customerTxs.fold(MoneyMath.ZERO_AMOUNT) { total, tx -> MoneyMath.add(total, tx.sarCollected) }
                val totalDue = MoneyMath.subtract(totalSarSpent, totalSarCollected)
                if (selectedFilterStatus == "Due") {
                    totalDue > CUSTOMER_MONEY_THRESHOLD
                } else {
                    totalDue < CUSTOMER_MONEY_THRESHOLD.negate()
                }
            }
        }

        // Apply sorting
        list = when (selectedSortOption) {
            "Oldest" -> list.sortedBy { it.timestamp }
            "A-Z" -> list.sortedBy { it.name.lowercase(java.util.Locale.ROOT) }
            "Due" -> list.sortedByDescending { customer ->
                val customerTxs = transactions.filter { it.customerId == customer.id }
                MoneyMath.subtract(
                    customerTxs.fold(MoneyMath.ZERO_AMOUNT) { total, tx -> MoneyMath.add(total, tx.amountSar) },
                    customerTxs.fold(MoneyMath.ZERO_AMOUNT) { total, tx -> MoneyMath.add(total, tx.sarCollected) }
                )
            }
            "Advance" -> list.sortedBy { customer ->
                val customerTxs = transactions.filter { it.customerId == customer.id }
                MoneyMath.subtract(
                    customerTxs.fold(MoneyMath.ZERO_AMOUNT) { total, tx -> MoneyMath.add(total, tx.amountSar) },
                    customerTxs.fold(MoneyMath.ZERO_AMOUNT) { total, tx -> MoneyMath.add(total, tx.sarCollected) }
                )
            }
            else -> list.sortedByDescending { it.timestamp } // Newest First
        }

        list
    }

    val currencyFormatter = remember { DecimalFormat("#,##0") }

    if (isAddView || showAddDialog) {
        androidx.activity.compose.BackHandler {
            if (showAddDialog) showAddDialog = false else viewModel.navigateBack()
        }
        AddCustomerPage(
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
                viewModel.registerCustomer(nameInput, phoneInput, addressInput) {
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
            viewModel.selectCustomerProfile(null)
        }
        var lastActiveCustomer by remember { mutableStateOf<Customer?>(null) }
        val activeCustomer = customers.find { it.id == selectedCustomerIdForProfile }
        if (activeCustomer != null) {
            lastActiveCustomer = activeCustomer
        }
        val displayedCustomer = activeCustomer ?: lastActiveCustomer

        if (displayedCustomer != null) {
            // Render beautiful detailed Customer Profile View
            CustomerProfileView(
                customer = displayedCustomer,
                transactions = transactions.filter { it.customerId == displayedCustomer.id },
                lang = lang,
                operatorPin = currentOperator?.pin ?: "",
                onBack = { viewModel.selectCustomerProfile(null) },
                onUpdate = { updated -> 
                    viewModel.updateCustomer(updated)
                },
                onDelete = {
                    viewModel.deleteCustomer(displayedCustomer.id)
                    viewModel.selectCustomerProfile(null)
                },
                viewModel = viewModel
            )
        } else {
            // Autoreset if somehow not found
            LaunchedEffect(Unit) {
                viewModel.selectCustomerProfile(null)
            }
        }
    } else {
        // Render classic Customers List view with search and custom action triggers
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

                // Compact screen title (choto layout) with Add Customer button on the right
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
                                .size(32.dp) // smaller header icon box
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.People,
                                contentDescription = "",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp) // smaller icon
                            )
                        }
                        Text(
                            text = viewModel.t("customer_mgmt"),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 17.sp, // beautiful smaller font size
                                letterSpacing = (-0.3).sp
                            ),
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Elegant Add Customer button at top right
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
                            text = AndroidStringCatalog.get(lang, "inline_customerscreen_dffe3c0716"),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = Color.White),
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
                        placeholder = { Text(AndroidStringCatalog.get(lang, "inline_customerscreen_cd4ccd465f"), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "", modifier = Modifier.size(20.dp)) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "", modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        modifier = Modifier.weight(1f).height(48.dp).testTag("customer_search_field"),
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
                                text = AndroidStringCatalog.get(lang, "inline_customerscreen_ed2f3bd877"),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Sort Option Dropdown
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = AndroidStringCatalog.get(lang, "inline_customerscreen_def73680df"),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.padding(bottom = 2.dp),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
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
                                                    "Oldest" -> AndroidStringCatalog.get(lang, "inline_customerscreen_e0e188a0c3")
                                                    "A-Z" -> AndroidStringCatalog.get(lang, "inline_customerscreen_2ca9cf5aad")
                                                    "Due" -> AndroidStringCatalog.get(lang, "inline_customerscreen_d1e717904c")
                                                    "Advance" -> AndroidStringCatalog.get(lang, "inline_customerscreen_9ac66d7220")
                                                    else -> AndroidStringCatalog.get(lang, "inline_customerscreen_0edb739064")
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
                                            listOf("Newest", "Oldest", "A-Z", "Due", "Advance").forEach { option ->
                                                DropdownMenuItem(
                                                    text = {
                                                        Text(
                                                            when (option) {
                                                                "Oldest" -> AndroidStringCatalog.get(lang, "inline_customerscreen_e0e188a0c3")
                                                                "A-Z" -> AndroidStringCatalog.get(lang, "inline_customerscreen_2ca9cf5aad")
                                                                "Due" -> AndroidStringCatalog.get(lang, "inline_customerscreen_d1e717904c")
                                                                "Advance" -> AndroidStringCatalog.get(lang, "inline_customerscreen_9ac66d7220")
                                                                else -> AndroidStringCatalog.get(lang, "inline_customerscreen_0edb739064")
                                                            },
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
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
                                        text = AndroidStringCatalog.get(lang, "inline_customerscreen_8aa03a6e91"),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.padding(bottom = 2.dp),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
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
                                                    "Due" -> AndroidStringCatalog.get(lang, "inline_customerscreen_11cd60c23c")
                                                    "Advance" -> AndroidStringCatalog.get(lang, "inline_customerscreen_af4a1cc0fc")
                                                    else -> AndroidStringCatalog.get(lang, "inline_customerscreen_3be0d774e8")
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
                                            listOf("All", "Due", "Advance").forEach { option ->
                                                DropdownMenuItem(
                                                    text = {
                                                        Text(
                                                            when (option) {
                                                                "Due" -> AndroidStringCatalog.get(lang, "inline_customerscreen_11cd60c23c")
                                                                "Advance" -> AndroidStringCatalog.get(lang, "inline_customerscreen_af4a1cc0fc")
                                                                else -> AndroidStringCatalog.get(lang, "inline_customerscreen_3be0d774e8")
                                                            },
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
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

                if (filteredCustomers.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.People, contentDescription = "", modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
                            Text(
                                text = AndroidStringCatalog.get(lang, "inline_customerscreen_b894e5f946"),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.outline,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        items(filteredCustomers, key = { it.id }) { customer ->
                            val customerTxs = remember(transactions) {
                                transactions.filter { it.customerId == customer.id }
                            }
                            val totalSarSpent = customerTxs.fold(MoneyMath.ZERO_AMOUNT) { total, tx -> MoneyMath.add(total, tx.amountSar) }

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.selectCustomerProfile(customer.id) // Go directly into detailed profile
                                    }
                                    .testTag("customer_card_${customer.id}"),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                                shape = RoundedCornerShape(16.dp),
                                border = androidx.compose.foundation.BorderStroke(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                            ) {
                                 val totalSarCollected = remember(customerTxs) {
                                     customerTxs.fold(MoneyMath.ZERO_AMOUNT) { total, tx -> MoneyMath.add(total, tx.sarCollected) }
                                 }
                                 val totalDue = remember(totalSarSpent, totalSarCollected) { MoneyMath.subtract(totalSarSpent, totalSarCollected) }
                                 val totalBdt = remember(customerTxs) {
                                     customerTxs.fold(MoneyMath.ZERO_AMOUNT) { total, tx -> MoneyMath.add(total, tx.amountBdt) }
                                 }

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
                                             // Customer customizable avatar
                                             val avatarBg = remember(customer.avatarColor) {
                                                 try { Color(android.graphics.Color.parseColor(customer.avatarColor)) } catch(e: Exception) { Color(0xFF3F51B5) }
                                             }
                                             Box(
                                                 modifier = Modifier
                                                     .size(42.dp)
                                                     .clip(CircleShape)
                                                     .background(avatarBg.copy(alpha = 0.15f)),
                                                 contentAlignment = Alignment.Center
                                             ) {
                                                 Text(
                                                     text = customer.avatarEmoji.ifBlank { customer.name.take(1).uppercase(Locale.ROOT) },
                                                     fontSize = 18.sp
                                                 )
                                             }
                                             Column {
                                                 Text(
                                                     text = customer.name,
                                                     style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.ExtraBold),
                                                     color = MaterialTheme.colorScheme.onSurface,
                                                     maxLines = 1,
                                                     overflow = TextOverflow.Ellipsis
                                                 )
                                                 Spacer(modifier = Modifier.height(1.dp))
                                                 Text(
                                                     text = customer.phone,
                                                     style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                                     color = MaterialTheme.colorScheme.outline,
                                                     maxLines = 1,
                                                     overflow = TextOverflow.Ellipsis
                                                 )
                                             }
                                         }
                                         
                                         Column(horizontalAlignment = Alignment.End) {
                                             Text(
                                                 text = if (totalDue <= CUSTOMER_MONEY_THRESHOLD.negate()) {
                                                     AndroidStringCatalog.get(lang, "inline_customerscreen_4f46a2660b")
                                                 } else {
                                                     AndroidStringCatalog.get(lang, "inline_customerscreen_e9bb06d8b8")
                                                 },
                                                 style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                 color = if (totalDue > CUSTOMER_MONEY_THRESHOLD) Color(0xFFD32F2F) else if (totalDue <= CUSTOMER_MONEY_THRESHOLD.negate()) Color(0xFF1565C0) else Color(0xFF2E7D32),
                                                 maxLines = 1,
                                                 overflow = TextOverflow.Ellipsis
                                             )
                                             Spacer(modifier = Modifier.height(1.dp))
                                             Text(
                                                 text = "${currencyFormatter.format(totalDue.abs())} ${foreignCur}",
                                                 style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                                                 color = if (totalDue > CUSTOMER_MONEY_THRESHOLD) Color(0xFFD32F2F) else if (totalDue <= CUSTOMER_MONEY_THRESHOLD.negate()) Color(0xFF1565C0) else Color(0xFF2E7D32),
                                                 maxLines = 1,
                                                 overflow = TextOverflow.Ellipsis
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
                                         Column(modifier = Modifier.weight(1f)) {
                                             Text(
                                                 text = AndroidStringCatalog.get(lang, "inline_customerscreen_e3aab99a49"),
                                                 style = MaterialTheme.typography.labelSmall,
                                                 color = MaterialTheme.colorScheme.outline,
                                                 maxLines = 1,
                                                 overflow = TextOverflow.Ellipsis
                                             )
                                             Text(
                                                 text = "${currencyFormatter.format(totalSarSpent)} ${foreignCur}",
                                                 style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                                 color = MaterialTheme.colorScheme.onSurface,
                                                 maxLines = 1,
                                                 overflow = TextOverflow.Ellipsis
                                             )
                                         }
                                         Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                             Text(
                                                 text = AndroidStringCatalog.get(lang, "inline_customerscreen_a0292a457f"),
                                                 style = MaterialTheme.typography.labelSmall,
                                                 color = MaterialTheme.colorScheme.outline,
                                                 maxLines = 1,
                                                 overflow = TextOverflow.Ellipsis
                                             )
                                             Text(
                                                 text = "${currencyFormatter.format(totalBdt)} ${localCur}",
                                                 style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                                 color = MaterialTheme.colorScheme.onSurface,
                                                 maxLines = 1,
                                                 overflow = TextOverflow.Ellipsis
                                             )
                                         }
                                         Column(horizontalAlignment = Alignment.End, modifier = Modifier.weight(1f)) {
                                             Text(
                                                 text = AndroidStringCatalog.get(lang, "inline_customerscreen_3982854841"),
                                                 style = MaterialTheme.typography.labelSmall,
                                                 color = MaterialTheme.colorScheme.outline,
                                                 maxLines = 1,
                                                 overflow = TextOverflow.Ellipsis
                                             )
                                             Text(
                                                 text = "${customerTxs.size}",
                                                 style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                                 color = MaterialTheme.colorScheme.onSurface,
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
            }
        }
    }
}

/**
 * Beautiful full-screen Customer Profile View with secure pin validations, avatar customisations
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CustomerProfileView(
    customer: Customer,
    transactions: List<RemittanceTransaction>,
    lang: String,
    operatorPin: String,
    onBack: () -> Unit,
    onUpdate: (Customer) -> Unit,
    onDelete: () -> Unit,
    viewModel: SafaViewModel
) {
    DisposableEffect(Unit) {
        viewModel.setSubPageActive(true)
        onDispose {
            viewModel.setSubPageActive(false)
        }
    }

    val currentOperator by viewModel.currentOperator.collectAsStateWithLifecycle()
    val foreignCur by viewModel.selectedForeignCurrency.collectAsStateWithLifecycle()
    val localCur by viewModel.selectedLocalCurrency.collectAsStateWithLifecycle()
    val isRateBasedModeEnabled by viewModel.isRateBasedModeEnabled.collectAsStateWithLifecycle()
    val isSupplierRateEnabled by viewModel.isSupplierRateEnabled.collectAsStateWithLifecycle()
    // Editing status state
    var isEditing by remember { mutableStateOf(false) }
    
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )
    
    val mContext = androidx.compose.ui.platform.LocalContext.current
    
    // Editable state fields
    var editName by remember(customer) { mutableStateOf(customer.name) }
    var editPhone by remember(customer) { mutableStateOf(customer.phone) }
    var editAddress by remember(customer) { mutableStateOf(customer.address) }
    var editNotes by remember(customer) { mutableStateOf(customer.securityNotes) }
    var editColor by remember(customer) { mutableStateOf(customer.avatarColor) }
    var editEmoji by remember(customer) { mutableStateOf(customer.avatarEmoji) }

    // Security Gate variables
    var showSecurityDialog by remember { mutableStateOf(false) }
    
    var expandedTxId by remember { mutableStateOf<Int?>(null) }
    
    // Switch between Transactions (0) and Customer Info (1)
    var selectedProfileTab by remember { mutableStateOf(0) }
    
    LaunchedEffect(isEditing) {
        if (isEditing) {
            selectedProfileTab = 1
        }
    }
    
    // Add transaction list/page states
    var isAddingTransaction by remember { mutableStateOf(false) }
    var isAdvanceReturn by remember { mutableStateOf(false) }
    var showAddTxChoiceDialog by remember { mutableStateOf(false) }
    val suppliers by viewModel.suppliers.collectAsStateWithLifecycle(initialValue = emptyList())
    val supplierDeposits by viewModel.supplierDeposits.collectAsStateWithLifecycle(initialValue = emptyList())
    val currentRatesState by viewModel.currentRates.collectAsStateWithLifecycle()
    val walletBatches by viewModel.walletBatches.collectAsStateWithLifecycle()
    val walletLedgers by viewModel.walletLedgers.collectAsStateWithLifecycle()
    var selectedBatchId by remember { mutableStateOf<Int?>(null) }
    var inputAmountSar by remember { mutableStateOf("") }
    var inputCustomerRate by remember { mutableStateOf("32.10") }
    var inputSupplierRate by remember { mutableStateOf("32.00") }
    var inputReceiverName by remember { mutableStateOf("") }
    var inputReceiverPhone by remember { mutableStateOf("") }
    var inputReceiverAccountType by remember { mutableStateOf("Bkash") }
    var inputReceiverAccountNo by remember { mutableStateOf("") }
    var inputNotes by remember { mutableStateOf("") }
    var selectedDocumentName by remember { mutableStateOf<String?>(null) }
    var isAmountCalCOpen by remember { mutableStateOf(false) }
    var inputSarCollected by remember { mutableStateOf("") }
    var inputBdtDisbursed by remember { mutableStateOf("") }
    var inputDueSarCollected by remember { mutableStateOf("") }
    var inputTimestamp by remember { mutableStateOf(System.currentTimeMillis()) }

    // Confirmation page states
    var showConfirmationPage by remember { mutableStateOf(false) }
    var confirmCustName by remember { mutableStateOf("") }
    var confirmAmountSar by remember { mutableStateOf(MoneyMath.ZERO_AMOUNT) }
    var confirmCustomerRate by remember { mutableStateOf(MoneyMath.rate("32.10")) }
    var confirmCollectedSar by remember { mutableStateOf(MoneyMath.ZERO_AMOUNT) }
    var confirmDueCollectedSar by remember { mutableStateOf(MoneyMath.ZERO_AMOUNT) }
    var confirmNewDueSar by remember { mutableStateOf(MoneyMath.ZERO_AMOUNT) }
    var confirmTotalRemainingDueSar by remember { mutableStateOf(MoneyMath.ZERO_AMOUNT) }
    var confirmPaymentMethod by remember { mutableStateOf("Cash") }
    var confirmRecipientNo by remember { mutableStateOf("") }
    var confirmTimestamp by remember { mutableStateOf(System.currentTimeMillis()) }
    var confirmIsAdvanceReturn by remember { mutableStateOf(false) }

    // Edit transaction dialog states
    var txToEdit by remember { mutableStateOf<RemittanceTransaction?>(null) }
    var editAmountSar by remember { mutableStateOf("") }
    var editCustomerRate by remember { mutableStateOf("") }
    var editSupplierRate by remember { mutableStateOf("") }
    var editReceiverName by remember { mutableStateOf("") }
    var editReceiverPhone by remember { mutableStateOf("") }
    var editReceiverAccountType by remember { mutableStateOf("") }
    var editReceiverAccountNo by remember { mutableStateOf("") }
    var editTxNotes by remember { mutableStateOf("") }
    var editStatus by remember { mutableStateOf("") }
    var editSupplierId by remember { mutableStateOf<Int?>(null) }
    var isEditAmountCalCOpen by remember { mutableStateOf(false) }
    var editSarCollected by remember { mutableStateOf("") }
    var editBdtDisbursed by remember { mutableStateOf("") }

    // PIN secure verification states for transactions
    var txToDelete by remember { mutableStateOf<RemittanceTransaction?>(null) }
    var showTxSecurityDialog by remember { mutableStateOf(false) }
    var txActionToConfirm by remember { mutableStateOf("") } // "EDIT", "DELETE", "STATUS_DELIVER", "STATUS_CANCEL", "STATUS_PENDING"
    var txPinCodeInput by remember { mutableStateOf("") }
    var txPinErrorText by remember { mutableStateOf<String?>(null) }
    var actionToConfirm by remember { mutableStateOf("") } // "SAVE" or "DELETE"
    var pinCodeInput by remember { mutableStateOf("") }
    var pinErrorText by remember { mutableStateOf<String?>(null) }

    // Color swatches for background
    val avatarColors = listOf(
        "#3F51B5", "#2E7D32", "#C2185B", "#FF9800", "#008080", "#1565C0", "#6A1B9A", "#455A64"
    )
    val avatarEmojis = listOf(
        "👤", "🧑‍💼", "👑", "💰", "⭐", "💼", "⚡", "❤️", "🏡"
    )

    val totalSpentSar = remember(transactions) {
        transactions.fold(MoneyMath.ZERO_AMOUNT) { total, tx -> MoneyMath.add(total, tx.amountSar) }
    }
    val totalDisbursedBdt = remember(transactions) {
        transactions.fold(MoneyMath.ZERO_AMOUNT) { total, tx -> MoneyMath.add(total, tx.amountBdt) }
    }
    val totalUncollectedSar = remember(transactions) {
        MoneyMath.subtract(
            transactions.fold(MoneyMath.ZERO_AMOUNT) { total, tx -> MoneyMath.add(total, tx.amountSar) },
            transactions.fold(MoneyMath.ZERO_AMOUNT) { total, tx -> MoneyMath.add(total, tx.sarCollected) }
        )
    }

    val transactionsByDate = remember(transactions, lang) {
        transactions.sortedByDescending { it.timestamp }.groupBy {
            val sdf = SimpleDateFormat(AndroidStringCatalog.get(lang, "inline_customerscreen_6ef987f556"), Locale.US)
            sdf.format(Date(it.timestamp))
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (showConfirmationPage) {
            TransactionConfirmationPage(
                lang = lang,
                customerName = confirmCustName,
                amountSar = confirmAmountSar,
                customerRate = confirmCustomerRate,
                collectedSar = confirmCollectedSar,
                dueCollectedSar = confirmDueCollectedSar,
                newDueSar = confirmNewDueSar,
                totalRemainingDueSar = confirmTotalRemainingDueSar,
                paymentMethod = confirmPaymentMethod,
                recipientNo = confirmRecipientNo,
                timestamp = confirmTimestamp,
                isAdvanceReturn = confirmIsAdvanceReturn,
                foreignCur = foreignCur,
                localCur = localCur,
                onDismiss = { showConfirmationPage = false }
            )
        } else if (isAddingTransaction) {
            AddTransactionStepPage(
                lang = lang,
                walletBatches = walletBatches,
                walletLedgers = walletLedgers,
                selectedBatchId = selectedBatchId,
                onSelectedBatchChange = { batchId ->
                    selectedBatchId = batchId
                    val batch = walletBatches.find { it.id == batchId }
                    val latestRate = batch?.rate ?: currentRatesState?.supplierRate ?: MoneyMath.rate("32.00")
                    
                    inputSupplierRate = MoneyMath.rateDisplayString(latestRate)
                    
                    val defaultCustRate = currentRatesState?.customerRate ?: MoneyMath.rate("32.10")
                    inputCustomerRate = MoneyMath.rateDisplayString(defaultCustRate)

                    inputSarCollected = inputAmountSar

                    val amount = inputAmountSar.toBigDecimalOrNull() ?: MoneyMath.ZERO_AMOUNT
                    inputBdtDisbursed = MoneyMath.multiply(amount, defaultCustRate).toPlainString()
                },
                amountSar = inputAmountSar,
                onAmountChange = {
                    inputAmountSar = it
                    val amount = it.toBigDecimalOrNull() ?: MoneyMath.ZERO_AMOUNT
                    val rate = inputCustomerRate.toBigDecimalOrNull() ?: MoneyMath.rate("32.10")
                    inputBdtDisbursed = MoneyMath.multiply(amount, rate).toPlainString()
                    inputSarCollected = it
                },
                customerRate = inputCustomerRate,
                onCustomerRateChange = {
                    inputCustomerRate = it
                    val amount = inputAmountSar.toBigDecimalOrNull() ?: MoneyMath.ZERO_AMOUNT
                    val rate = it.toBigDecimalOrNull() ?: MoneyMath.ZERO_RATE
                    inputBdtDisbursed = MoneyMath.multiply(amount, rate).toPlainString()
                },
                supplierRate = inputSupplierRate,
                onSupplierRateChange = { inputSupplierRate = it },
                recipientNo = inputReceiverAccountNo,
                onRecipientNoChange = { inputReceiverAccountNo = it },
                paymentMethod = inputReceiverAccountType,
                onPaymentMethodChange = { inputReceiverAccountType = it },
                notes = inputNotes,
                onNotesChange = { inputNotes = it },
                selectedDocumentName = selectedDocumentName,
                onSelectedDocumentNameChange = { selectedDocumentName = it },
                sarCollected = inputSarCollected,
                onSarCollectedChange = { inputSarCollected = it },
                bdtDisbursed = inputBdtDisbursed,
                onBdtDisbursedChange = { inputBdtDisbursed = it },
                previousDueSar = totalUncollectedSar,
                dueSarCollected = inputDueSarCollected,
                onDueSarCollectedChange = { inputDueSarCollected = it },
                isCalcOpen = isAmountCalCOpen,
                onCalcOpenChange = { isAmountCalCOpen = it },
                isDueOnly = isDueOnlyAmount(inputAmountSar),
                isAdvanceReturn = isAdvanceReturn,
                selectedTimestamp = inputTimestamp,
                onSelectedTimestampChange = { inputTimestamp = it },
                isRateBasedModeEnabled = isRateBasedModeEnabled,
                isSupplierRateEnabled = isSupplierRateEnabled,
                onCancel = { isAddingTransaction = false; txToEdit = null },
                onSubmit = {
                    val amt = inputAmountSar.toBigDecimalOrNull() ?: MoneyMath.ZERO_AMOUNT
                    val cRate = inputCustomerRate.toBigDecimalOrNull() ?: MoneyMath.rate("32.10")
                    val batchId = selectedBatchId ?: 0
                    val col = inputSarCollected.toBigDecimalOrNull() ?: amt
                    val dis = inputBdtDisbursed.toBigDecimalOrNull() ?: MoneyMath.multiply(amt, cRate)
                    val dueAmt = inputDueSarCollected.toBigDecimalOrNull() ?: MoneyMath.ZERO_AMOUNT
                    val rcvStr = if (inputReceiverAccountType == "Cash") "Cash" else inputReceiverAccountNo
                    val editingTx = txToEdit

                    if (editingTx != null) {
                        val selectedBatch = walletBatches.find { it.id == batchId }
                        val updated = editingTx.copy(
                            supplierId = selectedBatch?.supplierId ?: editingTx.supplierId,
                            amountSar = amt,
                            customerRate = cRate,
                            supplierRate = inputSupplierRate.toBigDecimalOrNull() ?: selectedBatch?.rate ?: editingTx.supplierRate,
                            amountBdt = MoneyMath.multiply(amt, cRate),
                            receiverName = editingTx.receiverName,
                            receiverPhone = rcvStr,
                            receiverAccountType = inputReceiverAccountType,
                            receiverAccountNo = rcvStr,
                            notes = inputNotes,
                            sarCollected = col,
                            bdtDisbursed = dis,
                            timestamp = inputTimestamp,
                            walletBatchId = batchId,
                        )
                        confirmCustName = customer.name
                        confirmAmountSar = amt
                        confirmCustomerRate = cRate
                        confirmCollectedSar = col
                        confirmDueCollectedSar = MoneyMath.ZERO_AMOUNT
                        confirmNewDueSar = MoneyMath.subtract(amt, col)
                        confirmTotalRemainingDueSar = totalUncollectedSar
                        confirmPaymentMethod = inputReceiverAccountType
                        confirmRecipientNo = rcvStr
                        confirmTimestamp = inputTimestamp
                        confirmIsAdvanceReturn = editingTx.receiverName == "Advance Return"
                        viewModel.updateRemittance(updated) {
                            txToEdit = null
                            inputAmountSar = ""
                            inputReceiverPhone = ""
                            inputReceiverAccountNo = ""
                            inputNotes = ""
                            inputSarCollected = ""
                            inputBdtDisbursed = ""
                            inputDueSarCollected = ""
                            inputTimestamp = System.currentTimeMillis()
                            isAddingTransaction = false
                            showConfirmationPage = true
                        }
                    } else {

                    confirmCustName = customer.name
                    confirmAmountSar = amt
                    confirmCustomerRate = cRate
                    confirmCollectedSar = col
                    confirmDueCollectedSar = dueAmt
                    confirmNewDueSar = MoneyMath.subtract(amt, col)
                    val dueAmtEffect = if (isAdvanceReturn || totalUncollectedSar <= CUSTOMER_MONEY_THRESHOLD.negate()) dueAmt.negate() else dueAmt
                    confirmTotalRemainingDueSar = MoneyMath.subtract(
                        MoneyMath.add(totalUncollectedSar, confirmNewDueSar),
                        dueAmtEffect
                    )
                    confirmPaymentMethod = inputReceiverAccountType
                    confirmRecipientNo = rcvStr
                    confirmTimestamp = inputTimestamp
                    confirmIsAdvanceReturn = isAdvanceReturn || (totalUncollectedSar <= CUSTOMER_MONEY_THRESHOLD.negate() && dueAmt > CUSTOMER_MONEY_THRESHOLD)

                    if (amt > CUSTOMER_MONEY_THRESHOLD && batchId > 0 && rcvStr.isNotBlank()) {
                        viewModel.createRemittance(
                            customerId = customer.id,
                            walletBatchId = batchId,
                            amountSar = amt,
                            customerRate = cRate,
                            receiverName = "Recipient",
                            receiverPhone = rcvStr,
                            receiverAccountType = inputReceiverAccountType,
                            receiverAccountNo = rcvStr,
                            notes = inputNotes,
                            sarCollected = col,
                            bdtDisbursed = dis,
                            timestamp = inputTimestamp
                        ) {
                            if (dueAmt > CUSTOMER_MONEY_THRESHOLD) {
                                viewModel.createRemittance(
                                    customerId = customer.id,
                                    walletBatchId = 0,
                                    amountSar = MoneyMath.ZERO_AMOUNT,
                                    customerRate = MoneyMath.ZERO_RATE,
                                    receiverName = if (isAdvanceReturn) "Advance Return" else "Due Payment",
                                    receiverPhone = "N/A",
                                    receiverAccountType = "N/A",
                                    receiverAccountNo = "N/A",
                                    notes = if (isAdvanceReturn) (AndroidStringCatalog.get(lang, "inline_customerscreen_aa32889e82")) else (AndroidStringCatalog.get(lang, "inline_customerscreen_755d9d96ac")),
                                    sarCollected = dueAmtEffect,
                                    bdtDisbursed = MoneyMath.ZERO_AMOUNT,
                                    status = "Delivered",
                                    timestamp = inputTimestamp
                                ) {
                                    inputAmountSar = ""
                                    inputReceiverPhone = ""
                                    inputReceiverAccountNo = ""
                                    inputNotes = ""
                                    inputSarCollected = ""
                                    inputBdtDisbursed = ""
                                    inputDueSarCollected = ""
                                    inputTimestamp = System.currentTimeMillis()
                                    isAddingTransaction = false
                                    showConfirmationPage = true
                                }
                            } else {
                                inputAmountSar = ""
                                inputReceiverPhone = ""
                                inputReceiverAccountNo = ""
                                inputNotes = ""
                                inputSarCollected = ""
                                inputBdtDisbursed = ""
                                inputDueSarCollected = ""
                                inputTimestamp = System.currentTimeMillis()
                                isAddingTransaction = false
                                showConfirmationPage = true
                            }
                        }
                    } else if (amt <= CUSTOMER_MONEY_THRESHOLD && dueAmt > CUSTOMER_MONEY_THRESHOLD) {
                        viewModel.createRemittance(
                            customerId = customer.id,
                            walletBatchId = 0,
                            amountSar = MoneyMath.ZERO_AMOUNT,
                            customerRate = MoneyMath.ZERO_RATE,
                            receiverName = if (isAdvanceReturn) "Advance Return" else "Due Payment",
                            receiverPhone = "N/A",
                            receiverAccountType = "N/A",
                            receiverAccountNo = "N/A",
                            notes = if (isAdvanceReturn) (AndroidStringCatalog.get(lang, "inline_customerscreen_aa32889e82")) else (AndroidStringCatalog.get(lang, "inline_customerscreen_755d9d96ac")),
                            sarCollected = dueAmtEffect,
                            bdtDisbursed = MoneyMath.ZERO_AMOUNT,
                            status = "Delivered",
                            timestamp = inputTimestamp
                        ) {
                            inputAmountSar = ""
                            inputReceiverPhone = ""
                            inputReceiverAccountNo = ""
                            inputNotes = ""
                            inputSarCollected = ""
                            inputBdtDisbursed = ""
                            inputDueSarCollected = ""
                            inputTimestamp = System.currentTimeMillis()
                            isAddingTransaction = false
                            showConfirmationPage = true
                        }
                    }
                    }
                }
            )
        }

        if (showAddTxChoiceDialog) {
            AlertDialog(
                onDismissRequest = { showAddTxChoiceDialog = false },
                title = {
                    Text(
                        text = AndroidStringCatalog.get(lang, "inline_customerscreen_98bfb45d9d"),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        val totalUncollectedSar = remember(transactions) {
                            MoneyMath.subtract(
                                transactions.fold(MoneyMath.ZERO_AMOUNT) { total, tx -> MoneyMath.add(total, tx.amountSar) },
                                transactions.fold(MoneyMath.ZERO_AMOUNT) { total, tx -> MoneyMath.add(total, tx.sarCollected) }
                            )
                        }

                        // Option 1: New Sale (নতুন বিক্রয়)
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showAddTxChoiceDialog = false
                                    selectedBatchId = null
                                    inputAmountSar = ""
                                    inputCustomerRate = "32.10"
                                    inputSupplierRate = "32.00"
                                    inputReceiverName = ""
                                    inputReceiverPhone = ""
                                    inputReceiverAccountType = "Bkash"
                                    inputReceiverAccountNo = ""
                                    inputNotes = ""
                                    inputSarCollected = ""
                                    inputBdtDisbursed = ""
                                    inputDueSarCollected = ""
                                    isAddingTransaction = true
                                    isAdvanceReturn = false
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
                                        imageVector = Icons.Default.TrendingUp,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = AndroidStringCatalog.get(lang, "inline_customerscreen_a126cd34a4"),
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Text(
                                        text = AndroidStringCatalog.get(lang, "inline_customerscreen_9d6e8cb361"),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                        }

                        if (totalUncollectedSar > CUSTOMER_MONEY_THRESHOLD) {
                            // Option 2: Due Collection (বকেয়া আদায়)
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showAddTxChoiceDialog = false
                                        selectedBatchId = null
                                        inputAmountSar = "0"
                                        inputCustomerRate = "0"
                                        inputSupplierRate = "0"
                                        inputReceiverName = "Due Payment"
                                        inputReceiverPhone = "N/A"
                                        inputReceiverAccountType = "Cash"
                                        inputReceiverAccountNo = "N/A"
                                        inputNotes = AndroidStringCatalog.get(lang, "inline_customerscreen_f5199a75a0")
                                        inputSarCollected = "0"
                                        inputBdtDisbursed = "0"
                                        inputDueSarCollected = ""
                                        isAddingTransaction = true
                                        isAdvanceReturn = false
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
                                            .background(Color(0xFFE8F5E9)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PriceCheck,
                                            contentDescription = null,
                                            tint = Color(0xFF2E7D32),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = AndroidStringCatalog.get(lang, "inline_customerscreen_f132f0087b"),
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                            color = Color(0xFF2E7D32)
                                        )
                                        Text(
                                            text = if (lang == "BN") "পূর্বের বকেয়া রিয়াল আদায় করুন (বকেয়া: ${foreignCur}${DecimalFormat("#.##").format(totalUncollectedSar)})।" else "Collect previous outstanding dues (Owed: ${DecimalFormat("#.##").format(totalUncollectedSar)} ${foreignCur}).",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                }
                            }
                        }

                        if (totalUncollectedSar <= CUSTOMER_MONEY_THRESHOLD.negate()) {
                            // Option 3: Advance Return (পাওনা ফেরত)
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showAddTxChoiceDialog = false
                                        selectedBatchId = null
                                        inputAmountSar = "0"
                                        inputCustomerRate = "0"
                                        inputSupplierRate = "0"
                                        inputReceiverName = "Advance Return"
                                        inputReceiverPhone = "N/A"
                                        inputReceiverAccountType = "Cash"
                                        inputReceiverAccountNo = "N/A"
                                        inputNotes = AndroidStringCatalog.get(lang, "inline_customerscreen_aa32889e82")
                                        inputSarCollected = "0"
                                        inputBdtDisbursed = "0"
                                        inputDueSarCollected = ""
                                        isAddingTransaction = true
                                        isAdvanceReturn = true
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
                                            .background(Color(0xFFE3F2FD)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CurrencyExchange,
                                            contentDescription = null,
                                            tint = Color(0xFF1565C0),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = AndroidStringCatalog.get(lang, "inline_customerscreen_2e1008cee2"),
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                            color = Color(0xFF1565C0)
                                        )
                                        Text(
                                            text = if (lang == "BN") "কাস্টমারকে পাওনা ফেরত দিন (পাবে: ${foreignCur}${DecimalFormat("#.##").format(totalUncollectedSar.abs())})।" else "Return customer's advanced balance (Owed: ${DecimalFormat("#.##").format(totalUncollectedSar.abs())} ${foreignCur}).",
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
                    TextButton(onClick = { showAddTxChoiceDialog = false }) {
                        Text(AndroidStringCatalog.get(lang, "inline_customerscreen_0d3658f984"))
                    }
                }
            )
        }

        if (txToEdit != null || isAddingTransaction || showConfirmationPage) {
            // Edit profile, create transaction UI, or confirmation page takes over the screen completely
        } else {
            Column(
                modifier = Modifier
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
            IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", modifier = Modifier.size(18.dp))
            }
            Text(
                text = AndroidStringCatalog.get(lang, "inline_customerscreen_e9edee9585"),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold, fontSize = 16.sp),
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (!isEditing) {
                    IconButton(
                        onClick = { onDelete() },
                        modifier = Modifier.size(36.dp),
                        colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(18.dp))
                    }
                }
                
                IconButton(
                    onClick = { 
                        if (isEditing) {
                            val updatedCustomer = customer.copy(
                                name = editName,
                                phone = editPhone,
                                address = editAddress,
                                securityNotes = editNotes,
                                avatarColor = editColor,
                                avatarEmoji = editEmoji
                            )
                            onUpdate(updatedCustomer)
                            isEditing = false
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
                        contentDescription = "Action",
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
            // Main avatar & interactive customizers (Compact Row Layout)
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
                            // Render Avatar Circle dynamically
                            val avatarColorParsed = remember(editColor) {
                                try { Color(android.graphics.Color.parseColor(editColor)) } catch(e: Exception) { Color(0xFF3F51B5) }
                            }
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(avatarColorParsed.copy(alpha = 0.15f))
                                    .border(2.dp, avatarColorParsed, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = editEmoji, fontSize = 24.sp)
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                if (!isEditing) {
                                    Text(
                                        text = customer.name,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "Joined: ${SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date(customer.timestamp))}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                } else {
                                    Text(
                                        text = AndroidStringCatalog.get(lang, "inline_customerscreen_b20c9b4631"),
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            if (!isEditing) {
                                Button(
                                    onClick = { 
                                        if (totalUncollectedSar.abs() > CUSTOMER_MONEY_THRESHOLD) {
                                            showAddTxChoiceDialog = true
                                        } else {
                                            showAddTxChoiceDialog = false
                                            selectedBatchId = null
                                            inputAmountSar = ""
                                            inputCustomerRate = "32.10"
                                            inputSupplierRate = "32.00"
                                            inputReceiverName = ""
                                            inputReceiverPhone = ""
                                            inputReceiverAccountType = "Bkash"
                                            inputReceiverAccountNo = ""
                                            inputNotes = ""
                                            inputSarCollected = ""
                                            inputBdtDisbursed = ""
                                            inputDueSarCollected = ""
                                            isAddingTransaction = true
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.height(34.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "", modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = AndroidStringCatalog.get(lang, "inline_customerscreen_8c3c780098"),
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = Color.White, fontSize = 11.sp),
                                        maxLines = 1
                                    )
                                }
                            }
                        }

                        // Editing controls for color and emoji
                        if (isEditing) {
                            // Emoji Swatches
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center
                            ) {
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

                            // Color Swatches
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                avatarColors.forEach { colString ->
                                    val colParsed = try { Color(android.graphics.Color.parseColor(colString)) } catch(e: Exception) { Color.Gray }
                                    Box(
                                        modifier = Modifier
                                            .padding(3.dp)
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(colParsed)
                                            .clickable { editColor = colString }
                                            .border(
                                                width = 2.dp,
                                                color = if (editColor == colString) MaterialTheme.colorScheme.outline else Color.Transparent,
                                                shape = CircleShape
                                            )
                                    )
                                }
                            }
                        }

                        val totalUncollectedSar = remember(transactions) {
                            MoneyMath.subtract(
                                transactions.fold(MoneyMath.ZERO_AMOUNT) { total, tx -> MoneyMath.add(total, tx.amountSar) },
                                transactions.fold(MoneyMath.ZERO_AMOUNT) { total, tx -> MoneyMath.add(total, tx.sarCollected) }
                            )
                        }

                        Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (totalUncollectedSar > CUSTOMER_MONEY_THRESHOLD) Color(0xFFFFECEB) else if (totalUncollectedSar <= CUSTOMER_MONEY_THRESHOLD.negate()) Color(0xFFE3F2FD) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
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
                                    imageVector = if (totalUncollectedSar <= CUSTOMER_MONEY_THRESHOLD.negate()) Icons.Default.Info else Icons.Default.Warning,
                                    contentDescription = "",
                                    tint = if (totalUncollectedSar > CUSTOMER_MONEY_THRESHOLD) Color(0xFFC62828) else if (totalUncollectedSar <= CUSTOMER_MONEY_THRESHOLD.negate()) Color(0xFF1565C0) else MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = if (totalUncollectedSar <= CUSTOMER_MONEY_THRESHOLD.negate()) {
                                        AndroidStringCatalog.get(lang, "inline_customerscreen_f8f07fea86")
                                    } else {
                                        AndroidStringCatalog.get(lang, "inline_customerscreen_106cf9feae")
                                    },
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (totalUncollectedSar > CUSTOMER_MONEY_THRESHOLD) Color(0xFFC62828) else if (totalUncollectedSar <= CUSTOMER_MONEY_THRESHOLD.negate()) Color(0xFF1565C0) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = "${DecimalFormat("#,##0.00").format(totalUncollectedSar.abs())} ${foreignCur}",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.ExtraBold),
                                color = if (totalUncollectedSar > CUSTOMER_MONEY_THRESHOLD) Color(0xFFC62828) else if (totalUncollectedSar <= CUSTOMER_MONEY_THRESHOLD.negate()) Color(0xFF1565C0) else Color(0xFF2E7D32)
                            )
                        }
                    }
                }
            }

            // Tab Switcher for Transactions vs Info
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
                    val tab2Bg = if (selectedProfileTab == 1) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                    val tab2Text = if (selectedProfileTab == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                    
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(32.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(tab1Bg)
                            .clickable { selectedProfileTab = 0 }
                            .padding(horizontal = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = AndroidStringCatalog.get(lang, "inline_customerscreen_07a4e2bb0a"),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                            color = tab1Text
                        )
                    }
                    
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(32.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(tab2Bg)
                            .clickable { selectedProfileTab = 1 }
                            .padding(horizontal = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = AndroidStringCatalog.get(lang, "inline_customerscreen_fa09237958"),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                            color = tab2Text
                        )
                    }
                }
            }

            if (selectedProfileTab == 1) {
                // Editable profile text details cards
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
                            text = AndroidStringCatalog.get(lang, "inline_customerscreen_c526442d66"),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )

                        if (isEditing) {
                            OutlinedTextField(
                                value = editName,
                                onValueChange = { editName = it },
                                label = { Text(AndroidStringCatalog.get(lang, "inline_customerscreen_468765c3b2")) },
                                leadingIcon = { Icon(Icons.Default.Person, contentDescription = "") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = editPhone,
                                onValueChange = { editPhone = it },
                                label = { Text(AndroidStringCatalog.get(lang, "inline_customerscreen_4ce6ccf57c")) },
                                leadingIcon = { Icon(Icons.Default.Phone, contentDescription = "") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = editAddress,
                                onValueChange = { editAddress = it },
                                label = { Text(AndroidStringCatalog.get(lang, "inline_customerscreen_02f013d7f6")) },
                                leadingIcon = { Icon(Icons.Default.Home, contentDescription = "") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = editNotes,
                                onValueChange = { editNotes = it },
                                label = { Text(AndroidStringCatalog.get(lang, "inline_customerscreen_48ffd594a2")) },
                                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = "") },
                                minLines = 2,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedButton(
                                onClick = {
                                    actionToConfirm = "DELETE"
                                    pinCodeInput = ""
                                    pinErrorText = null
                                    showSecurityDialog = true
                                },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().height(48.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(AndroidStringCatalog.get(lang, "inline_customerscreen_f650e3479e"))
                            }
                        } else {
                            // Non-editing read view (looks very secure and neat)
                            DetailFieldRow(
                                lang = lang,
                                labelBn = "মোবাইল ফোন",
                                labelEn = "Mobile Phone",
                                value = customer.phone,
                                icon = Icons.Default.Phone,
                                onClick = {
                                    try {
                                        val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${customer.phone.trim()}"))
                                        mContext.startActivity(dialIntent)
                                    } catch (e: Exception) {
                                        com.safa.account.utils.SafaLogger.error("PHONE_DIAL", "Unable to open dialer", e)
                                    }
                                }
                            )
                            DetailFieldRow(lang = lang, labelBn = "ঠিকানা", labelEn = "Address", value = customer.address.ifBlank { "N/A" }, icon = Icons.Default.Home)
                            DetailFieldRow(lang = lang, labelBn = "সিকিউরিটি নোটস", labelEn = "Security Audit Notes", value = customer.securityNotes.ifBlank { "None" }, icon = Icons.Default.Lock)
                        }
                    }
                }
            } // close item
            } // close if (selectedProfileTab == 1)

            if (selectedProfileTab == 0) {
                // Stats summaries
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = AndroidStringCatalog.get(lang, "inline_customerscreen_c1b6465c33"),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = "SAR ${DecimalFormat("#,##0.00").format(totalSpentSar)}",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                                Text(
                                    text = AndroidStringCatalog.get(lang, "inline_customerscreen_52a4485823"),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = "৳ ${DecimalFormat("#,##0").format(totalDisbursedBdt)}",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                    color = Color(0xFF2E7D32)
                                )
                            }
                        }
                    }
                }

                // Beautiful historical list segregated by date
                item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = AndroidStringCatalog.get(lang, "inline_customerscreen_17d0811674"),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = AndroidStringCatalog.get(lang, "inline_customerscreen_bdc99ef7ee"),
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            if (transactions.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = AndroidStringCatalog.get(lang, "inline_customerscreen_457da6122b"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            } else {
                items(transactionsByDate.entries.toList(), key = { it.key }) { entry ->
                    val dateText = entry.key
                    val txList = entry.value
                    Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = dateText, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
                                    val dailyBdt = txList.fold(MoneyMath.ZERO_AMOUNT) { total, tx -> MoneyMath.add(total, tx.amountBdt) }
                                    Text(text = "৳${DecimalFormat("#,##0").format(dailyBdt)}", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = Color(0xFF2E7D32))
                                }

                                txList.forEach { tx ->
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
                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    val isPending = tx.status != "Delivered" && tx.status != "Cancelled"
                                                    Box(
                                                        modifier = Modifier
                                                            .size(6.dp)
                                                            .clip(CircleShape)
                                                            .background(
                                                                color = if (isPending) Color(0xFFFB8C00).copy(alpha = pulseAlpha) else when (tx.status) {
                                                                    "Delivered" -> Color(0xFF43A047)
                                                                    "Cancelled" -> Color(0xFFE53935)
                                                                    else -> Color(0xFFFB8C00)
                                                                }
                                                            )
                                                    )
                                                    Text(text = tx.receiverName, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                                                }
                                                Text(text = "Rcv No: ${tx.receiverAccountNo} (${tx.receiverAccountType})", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                                
                                                val sarDue = MoneyMath.subtract(tx.amountSar, tx.sarCollected)
                                                val isDuePaymentTx = tx.receiverName == "Due Payment"
                                                val isAdvanceReturnTx = tx.receiverName == "Advance Return"
                                                
                                                if (!isDuePaymentTx && !isAdvanceReturnTx) {
                                                    if (sarDue > CUSTOMER_MONEY_THRESHOLD) {
                                                        Row(
                                                            modifier = Modifier.padding(top = 2.dp),
                                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                        ) {
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
                                                    } else if (sarDue <= CUSTOMER_MONEY_THRESHOLD.negate()) {
                                                        Row(
                                                            modifier = Modifier.padding(top = 2.dp),
                                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                        ) {
                                                            Surface(
                                                                shape = RoundedCornerShape(4.dp),
                                                                color = Color(0xFFE3F2FD),
                                                                contentColor = Color(0xFF1565C0)
                                                            ) {
                                                                Text(
                                                                    text = if (lang == "BN") "কাস্টমার পাবে: ${foreignCur}${DecimalFormat("#.##").format(sarDue.abs())}" else "Overpaid: ${DecimalFormat("#.##").format(sarDue.abs())} ${foreignCur}",
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
                                                    if (tx.amountSar <= CUSTOMER_MONEY_THRESHOLD && tx.sarCollected > CUSTOMER_MONEY_THRESHOLD) {
                                                        Text(text = "SAR ${tx.sarCollected}", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = Color(0xFF2E7D32))
                                                        Text(text = AndroidStringCatalog.get(lang, "inline_customerscreen_a6ba94f36b"), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = Color(0xFF2E7D32))
                                                    } else if (tx.amountSar <= CUSTOMER_MONEY_THRESHOLD && tx.sarCollected <= CUSTOMER_MONEY_THRESHOLD.negate()) {
                                                        Text(text = "SAR ${tx.sarCollected.abs()}", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = Color(0xFF1565C0))
                                                        Text(text = AndroidStringCatalog.get(lang, "inline_customerscreen_c7e56806e4"), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = Color(0xFF1565C0))
                                                    } else {
                                                        Text(text = "SAR ${tx.amountSar}", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                                                        Text(text = "Rate: ${MoneyMath.rateDisplayString(tx.customerRate)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                                    }
                                                }
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
                                                val batch = walletBatches.find { it.id == tx.walletBatchId }
                                                val ledger = walletLedgers.find { it.id == batch?.ledgerId }
                                                
                                                val walletName = if (tx.amountSar <= CUSTOMER_MONEY_THRESHOLD) {
                                                    AndroidStringCatalog.get(lang, "inline_customerscreen_7e68c4389b")
                                                } else {
                                                    ledger?.name ?: "Unknown Wallet"
                                                }

                                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                    Text(text = AndroidStringCatalog.get(lang, "inline_customerscreen_408ffc9247"), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)
                                                    Text(text = walletName, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                }
                                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                    Text(text = AndroidStringCatalog.get(lang, "inline_customerscreen_ec1ab45050"), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)
                                                    Text(text = "৳ ${DecimalFormat("#,##0").format(tx.amountBdt)}", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color(0xFF1B5E20))
                                                }
                                                
                                                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 2.dp))
                                                
                                                val sarDue = MoneyMath.subtract(tx.amountSar, tx.sarCollected)
                                                val bdtDue = MoneyMath.subtract(tx.amountBdt, tx.bdtDisbursed)
                                                
                                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                    Column {
                                                        Text(text = AndroidStringCatalog.get(lang, "inline_customerscreen_0498e7cd29"), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)
                                                        if (tx.amountSar <= CUSTOMER_MONEY_THRESHOLD && tx.sarCollected > CUSTOMER_MONEY_THRESHOLD) {
                                                            Text(text = "${tx.sarCollected} ${foreignCur}(${AndroidStringCatalog.get(lang, "inline_customerscreen_4b8ea35973")})", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                                                        } else {
                                                            Text(text = "${tx.sarCollected} / ${tx.amountSar} ${foreignCur}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (sarDue <= CUSTOMER_MONEY_THRESHOLD) Color(0xFF2E7D32) else Color(0xFFE65100))
                                                        }
                                                    }
                                                }
                                                
                                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                    Column {
                                                        Text(text = AndroidStringCatalog.get(lang, "inline_customerscreen_87fa7ba614"), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)
                                                        Text(text = "৳ ${DecimalFormat("#,##0").format(tx.amountBdt)} ${localCur}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                                                    }
                                                }

                                                if (tx.notes.isNotBlank()) {
                                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                        Text(text = AndroidStringCatalog.get(lang, "inline_customerscreen_45f489d950"), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)
                                                        Text(text = tx.notes, fontSize = 11.sp)
                                                    }
                                                }
                                                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 4.dp))
                                                
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.End,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Row(
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState())
                                                    ) {
                                                        // Share, Edit and Delete buttons relocated to expanded view
                                                        OutlinedButton(
                                                            onClick = {
                                                                confirmCustName = customer.name
                                                                confirmAmountSar = tx.amountSar
                                                                confirmCustomerRate = tx.customerRate
                                                                confirmCollectedSar = tx.sarCollected
                                                                confirmDueCollectedSar = if (tx.amountSar.signum() == 0) tx.sarCollected.abs() else MoneyMath.ZERO_AMOUNT
                                                                confirmNewDueSar = MoneyMath.subtract(tx.amountSar, tx.sarCollected)
                                                                confirmTotalRemainingDueSar = totalUncollectedSar // approximate snapshot
                                                                confirmPaymentMethod = tx.receiverAccountType
                                                                confirmRecipientNo = tx.receiverAccountNo
                                                                confirmTimestamp = tx.timestamp
                                                                confirmIsAdvanceReturn = tx.receiverName == "Advance Return"
                                                                showConfirmationPage = true
                                                            },
                                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                            modifier = Modifier.height(28.dp),
                                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                                        ) {
                                                            Icon(Icons.Default.Share, contentDescription = "", modifier = Modifier.size(12.dp))
                                                            Spacer(modifier = Modifier.width(4.dp))
                                                            Text(AndroidStringCatalog.get(lang, "inline_customerscreen_fc6320ee85"), fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                        }

                                                        OutlinedButton(
                                                            onClick = {
                                                                txToEdit = tx
                                                                selectedBatchId = tx.walletBatchId.takeIf { it > 0 }
                                                                inputAmountSar = MoneyMath.amountString(tx.amountSar)
                                                                inputCustomerRate = MoneyMath.rateDisplayString(tx.customerRate)
                                                                inputSupplierRate = MoneyMath.rateDisplayString(tx.supplierRate)
                                                                inputReceiverName = tx.receiverName
                                                                inputReceiverPhone = tx.receiverPhone
                                                                inputReceiverAccountType = tx.receiverAccountType
                                                                inputReceiverAccountNo = tx.receiverAccountNo
                                                                inputNotes = tx.notes
                                                                inputSarCollected = MoneyMath.amountString(tx.sarCollected)
                                                                inputBdtDisbursed = MoneyMath.amountString(tx.bdtDisbursed)
                                                                inputDueSarCollected = ""
                                                                inputTimestamp = tx.timestamp
                                                                isAdvanceReturn = tx.receiverName == "Advance Return"
                                                                isAddingTransaction = true
                                                            },
                                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                            modifier = Modifier.height(28.dp),
                                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                                                        ) {
                                                            Icon(Icons.Default.Edit, contentDescription = "", modifier = Modifier.size(12.dp))
                                                            Spacer(modifier = Modifier.width(4.dp))
                                                            Text(AndroidStringCatalog.get(lang, "inline_customerscreen_ef70a6b821"), fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                        }

                                                        OutlinedButton(
                                                            onClick = {
                                                                viewModel.deleteTransaction(tx.id)
                                                            },
                                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                            modifier = Modifier.height(28.dp),
                                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                                        ) {
                                                            Icon(Icons.Default.Delete, contentDescription = "", modifier = Modifier.size(12.dp))
                                                            Spacer(modifier = Modifier.width(4.dp))
                                                            Text(AndroidStringCatalog.get(lang, "inline_customerscreen_26e8941ff1"), fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                        }

                                                        if (tx.status != "Cancelled" && tx.status != "Delivered") {
                                                            Button(
                                                                onClick = {
                                                                    viewModel.updateTransactionStatus(tx, "Delivered")
                                                                },
                                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                                modifier = Modifier.height(28.dp)
                                                            ) {
                                                                Text(AndroidStringCatalog.get(lang, "inline_customerscreen_07385a0989"), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                            }
                                                        }

                                                        if (tx.status == "Pending") {
                                                            OutlinedButton(
                                                                onClick = {
                                                                    viewModel.updateTransactionStatus(tx, "Cancelled")
                                                                },
                                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                                modifier = Modifier.height(28.dp)
                                                            ) {
                                                                Text(AndroidStringCatalog.get(lang, "inline_customerscreen_e3952b8349"), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                                                            }
                                                        } else if (tx.status == "Cancelled") {
                                                            OutlinedButton(
                                                                onClick = {
                                                                    viewModel.updateTransactionStatus(tx, "Pending")
                                                                },
                                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                                modifier = Modifier.height(28.dp)
                                                            ) {
                                                                Text(AndroidStringCatalog.get(lang, "inline_customerscreen_23886d7cf0"), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                             }
                                                         }
                                                     }
                                                 }
                                             }
                                         }
                                         if (txList.last() != tx) {
                                             Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 4.dp))
                                         }
                                     }
                                 }
                             }
                         }
                     }

                     // Margin bottom anchor
                     item {
                            Spacer(modifier = Modifier.height(40.dp))
                        }
                    }
                }
            }
        }
    }

    // Secure Gate verification Dialog


    if (isAmountCalCOpen) {
        CalculatorDialog(
            initialValue = inputAmountSar,
            title = AndroidStringCatalog.get(lang, "inline_customerscreen_704f504756"),
            lang = lang,
            onDismiss = { isAmountCalCOpen = false },
            onConfirm = { result ->
                inputAmountSar = result
                inputSarCollected = result
                val cr = inputCustomerRate.toBigDecimalOrNull() ?: MoneyMath.ZERO_RATE
                val amt = result.toBigDecimalOrNull() ?: MoneyMath.ZERO_AMOUNT
                inputBdtDisbursed = MoneyMath.multiply(amt, cr).toPlainString()
                isAmountCalCOpen = false
            }
        )
    }

    if (isEditAmountCalCOpen) {
        CalculatorDialog(
            initialValue = editAmountSar,
            title = AndroidStringCatalog.get(lang, "inline_customerscreen_704f504756"),
            lang = lang,
            onDismiss = { isEditAmountCalCOpen = false },
            onConfirm = { result ->
                editAmountSar = result
                val s = result.toBigDecimalOrNull() ?: MoneyMath.ZERO_AMOUNT
                val r = editCustomerRate.toBigDecimalOrNull() ?: MoneyMath.ZERO_RATE
                editBdtDisbursed = MoneyMath.multiply(s, r).toPlainString()
            }
        )
    }
}
}

@Composable
fun DetailFieldRow(
    lang: String,
    labelBn: String,
    labelEn: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: (() -> Unit)? = null
) {
    val baseModifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(8.dp))
        .padding(vertical = 4.dp)
        
    val modifier = if (onClick != null) {
        baseModifier
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f))
            .clickable(onClick = onClick)
            .padding(8.dp)
    } else {
        baseModifier
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = "",
            tint = if (onClick != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
            modifier = Modifier.size(16.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (lang == "BN") labelBn else labelEn,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.outline
            )
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium,
                    color = if (onClick != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            )
        }
        if (onClick != null) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(Color(0xFF2E7D32)) // Clean beautiful Material green
                    .clickable(onClick = onClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Call,
                    contentDescription = "Call",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionStepPage(
    lang: String,
    walletBatches: List<com.safa.account.data.model.WalletBatch>,
    walletLedgers: List<com.safa.account.data.model.WalletLedger>,
    selectedBatchId: Int?,
    onSelectedBatchChange: (Int) -> Unit,
    amountSar: String,
    onAmountChange: (String) -> Unit,
    customerRate: String,
    onCustomerRateChange: (String) -> Unit,
    supplierRate: String,
    onSupplierRateChange: (String) -> Unit,
    recipientNo: String,
    onRecipientNoChange: (String) -> Unit,
    paymentMethod: String,
    onPaymentMethodChange: (String) -> Unit,
    notes: String,
    onNotesChange: (String) -> Unit,
    selectedDocumentName: String?,
    onSelectedDocumentNameChange: (String?) -> Unit,
    sarCollected: String,
    onSarCollectedChange: (String) -> Unit,
    bdtDisbursed: String,
    onBdtDisbursedChange: (String) -> Unit,
    previousDueSar: BigDecimal,
    dueSarCollected: String,
    onDueSarCollectedChange: (String) -> Unit,
    isCalcOpen: Boolean,
    onCalcOpenChange: (Boolean) -> Unit,
    isDueOnly: Boolean,
    isAdvanceReturn: Boolean = false,
    selectedTimestamp: Long,
    onSelectedTimestampChange: (Long) -> Unit,
    isRateBasedModeEnabled: Boolean = true,
    isSupplierRateEnabled: Boolean = true,
    foreignCur: String = "SAR",
    localCur: String = "BDT",
    onCancel: () -> Unit,
    onSubmit: () -> Unit
) {
    var currentStep by remember(isDueOnly) { mutableStateOf(if (isDueOnly) 2 else 1) }
    var isDueCalcOpen by remember { mutableStateOf(false) }
    val bdtFormatter = remember { DecimalFormat("#,##0") }
    val sarFormatter = remember { DecimalFormat("#,##0.00") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Step indicator & title
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
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
            Text(
                text = if (lang == "BN") {
                    if (currentStep == 1) "ধাপ ১: বিবরণ প্রদান" else "ধাপ ২: ওয়ালেট ও পেমেন্ট চ্যানেল"
                } else {
                    if (currentStep == 1) "Step 1: Details" else "Step 2: Wallet & Rates"
                },
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
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
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            if (currentStep == 1) {
                // STEP 1 CONTENT: Unified compact layout grouped nicely
                item {
                    val mContext = androidx.compose.ui.platform.LocalContext.current
                    val documentPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
                    ) { uri ->
                        if (uri != null) {
                            onSelectedDocumentNameChange("doc_" + System.currentTimeMillis().toString().takeLast(6) + ".jpg")
                        }
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(16.dp),
                        border = null,
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = AndroidStringCatalog.get(lang, "inline_customerscreen_3759ad6328"),
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.outline
                                )
                                if (!isDueOnly) {
                                    // Beautiful past date picker chip
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
                                                    onSelectedTimestampChange(selCal.timeInMillis)
                                                },
                                                calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)
                                            )
                                            picker.datePicker.maxDate = System.currentTimeMillis()
                                            picker.show()
                                        },
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(Icons.Default.Event, contentDescription = "", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                                            Text(
                                                text = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date(selectedTimestamp)),
                                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onCalcOpenChange(true) }
                            ) {
                                OutlinedTextField(
                                    value = amountSar,
                                    onValueChange = { },
                                    readOnly = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                        disabledTextColor = MaterialTheme.colorScheme.primary,
                                        disabledPlaceholderColor = MaterialTheme.colorScheme.outlineVariant,
                                        disabledBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                        disabledTrailingIconColor = MaterialTheme.colorScheme.primary
                                    ),
                                    textStyle = MaterialTheme.typography.headlineLarge.copy(
                                        fontWeight = FontWeight.Black,
                                        fontSize = 32.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    ),
                                    trailingIcon = {
                                        IconButton(
                                            onClick = { onCalcOpenChange(true) },
                                            modifier = Modifier.size(48.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Calculate,
                                                contentDescription = "Calculator",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(28.dp)
                                            )
                                        }
                                    },
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
                                
                                // Clean overlay to intercept click on entire text field bounds
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .clickable { onCalcOpenChange(true) }
                                )
                            }

                            OutlinedTextField(
                                value = notes,
                                onValueChange = onNotesChange,
                                label = { Text(AndroidStringCatalog.get(lang, "inline_customerscreen_0fee4d639d")) },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = "") },
                                placeholder = { Text(AndroidStringCatalog.get(lang, "inline_customerscreen_ea02bdbfb9")) },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(4.dp))

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
                                                text = AndroidStringCatalog.get(lang, "inline_customerscreen_f2aec4b443"),
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            )
                                            Text(
                                                text = selectedDocumentName ?: (AndroidStringCatalog.get(lang, "inline_customerscreen_766975bb5b")),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (selectedDocumentName != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                            )
                                        }
                                    }
                                    if (selectedDocumentName == null) {
                                        TextButton(onClick = { documentPickerLauncher.launch("*/*") }) {
                                            Icon(Icons.Default.Add, contentDescription = "", modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(AndroidStringCatalog.get(lang, "inline_customerscreen_a841a640d4"))
                                        }
                                    } else {
                                        IconButton(onClick = { onSelectedDocumentNameChange(null) }) {
                                            Icon(Icons.Default.Close, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // STEP 2: Unified list where payment methods, supplier, and due collections are at the top, and sales rate/reviews are on the bottom
                val isDueOnly = isDueOnlyAmount(amountSar)

                // 1. PAYMENT METHOD
                item {
                    val mContext = androidx.compose.ui.platform.LocalContext.current
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = AndroidStringCatalog.get(lang, "inline_customerscreen_594a8f6ed9"),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        if (isDueOnly) {
                            // Beautiful past date picker chip
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
                                            onSelectedTimestampChange(selCal.timeInMillis)
                                        },
                                        calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)
                                    )
                                    picker.datePicker.maxDate = System.currentTimeMillis()
                                    picker.show()
                                },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(Icons.Default.Event, contentDescription = "", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                                    Text(
                                        text = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date(selectedTimestamp)),
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    val methods = listOf("Cash", "Bkash", "Nagad", "Rocket", "Bank Transfer")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        methods.forEach { method ->
                            val isSelected = paymentMethod == method
                            Card(
                                modifier = Modifier
                                    .padding(vertical = 4.dp)
                                    .clickable {
                                        onPaymentMethodChange(method)
                                        if (method != "Cash" && (recipientNo == "N/A" || recipientNo.isBlank())) {
                                            onRecipientNoChange("")
                                        }
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                                ),
                                border = androidx.compose.foundation.BorderStroke(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = method,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = if (isSelected) FontWeight.Black else FontWeight.Medium,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                        )
                                    )
                                }
                            }
                        }
                    }

                    if (paymentMethod != "Cash") {
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = if (recipientNo == "N/A") "" else recipientNo,
                            onValueChange = onRecipientNoChange,
                            label = { 
                                Text(
                                    text = when (paymentMethod) {
                                        "Bank Transfer" -> AndroidStringCatalog.get(lang, "inline_customerscreen_f8f7088737")
                                        else -> AndroidStringCatalog.get(lang, "inline_customerscreen_a4985ef8ae")
                                    }
                                ) 
                            },
                            leadingIcon = { 
                                Icon(
                                    imageVector = if (paymentMethod == "Bank Transfer") Icons.Default.AccountBalance else Icons.Default.Phone, 
                                    contentDescription = ""
                                ) 
                            },
                            trailingIcon = {
                                val cleanNum = if (recipientNo == "N/A") "" else recipientNo
                                if (cleanNum.isNotEmpty()) {
                                    IconButton(onClick = { onRecipientNoChange("") }) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Clear",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            },
                            placeholder = { 
                                Text(
                                    if (paymentMethod == "Bank Transfer") "e.g. 1234567890" else "e.g. 017xxxxxxxx"
                                ) 
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = if (paymentMethod == "Bank Transfer") KeyboardType.Number else KeyboardType.Phone),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // 2. SUPPLIER SELECT + RATE GROUP (KROY RATE DISPLAYED AUTOMATICALLY)
                if (!isDueOnly) {
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = AndroidStringCatalog.get(lang, "inline_customerscreen_979e3f4016"),
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            
                            val groupedActiveBatches = walletBatches
                                .filter { it.remainingBdt > CUSTOMER_MONEY_THRESHOLD }
                                .groupBy { Pair(it.ledgerId, it.rate) }
                            val activeBatches = groupedActiveBatches.map { (_, list) -> 
                                list.first().copy(
                                    remainingBdt = list.fold(MoneyMath.ZERO_AMOUNT) { total, batch ->
                                        MoneyMath.add(total, batch.remainingBdt)
                                    }
                                )
                            }
                            if (activeBatches.isEmpty()) {
                                Text(
                                    text = AndroidStringCatalog.get(lang, "inline_customerscreen_b5aac5b451"),
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            } else {
                                var expandedSupDropdown by remember { mutableStateOf(false) }
                                val activeBatch = walletBatches.find { it.id == selectedBatchId }
                                val activeLedger = activeBatch?.let { b -> walletLedgers.find { it.id == b.ledgerId } }
                                
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    OutlinedButton(
                                        onClick = { expandedSupDropdown = true },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = activeLedger?.let { "${it.name} (Rate: ৳${activeBatch.rate} | Stock: ৳${activeBatch.remainingBdt.toInt()})" } 
                                                    ?: (AndroidStringCatalog.get(lang, "inline_customerscreen_2340e0c8d8")),
                                                fontWeight = FontWeight.ExtraBold,
                                                color = if (activeLedger != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                            )
                                            Icon(Icons.Default.ArrowDropDown, contentDescription = "", tint = MaterialTheme.colorScheme.primary)
                                        }
                                    }

                                    if (expandedSupDropdown) {
                                        AlertDialog(
                                            onDismissRequest = { expandedSupDropdown = false },
                                            title = {
                                                Text(
                                                    text = AndroidStringCatalog.get(lang, "inline_customerscreen_6defdcf615"),
                                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                                )
                                            },
                                            text = {
                                                Column(
                                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .verticalScroll(rememberScrollState())
                                                ) {
                                                    activeBatches.forEach { batch ->
                                                        val ledger = walletLedgers.find { it.id == batch.ledgerId }
                                                        val isSelected = batch.id == selectedBatchId
                                                        Card(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .clickable {
                                                                    onSelectedBatchChange(batch.id)
                                                                    expandedSupDropdown = false
                                                                },
                                                            colors = CardDefaults.cardColors(
                                                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                                                            ),
                                                            border = androidx.compose.foundation.BorderStroke(
                                                                width = 1.dp,
                                                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                                            ),
                                                            shape = RoundedCornerShape(12.dp)
                                                        ) {
                                                            Row(
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .padding(12.dp),
                                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                                verticalAlignment = Alignment.CenterVertically
                                                             ) {
                                                                Row(
                                                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                                    verticalAlignment = Alignment.CenterVertically
                                                                ) {
                                                                    Box(
                                                                        modifier = Modifier
                                                                            .size(36.dp)
                                                                            .clip(CircleShape)
                                                                            .background(
                                                                                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) 
                                                                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                                                                            ),
                                                                        contentAlignment = Alignment.Center
                                                                    ) {
                                                                        Icon(
                                                                            imageVector = Icons.Default.AccountBalanceWallet,
                                                                            contentDescription = null,
                                                                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                                                            modifier = Modifier.size(18.dp)
                                                                        )
                                                                    }
                                                                    Column {
                                                                        Text(
                                                                            text = ledger?.name ?: "Unknown Ledger",
                                                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                                        )
                                                                        Text(
                                                                            text = "Stock: ৳${batch.remainingBdt} | Rate: ৳${batch.rate}",
                                                                            style = MaterialTheme.typography.labelSmall,
                                                                            color = MaterialTheme.colorScheme.outline
                                                                        )
                                                                    }
                                                                }
                                                                if (isSelected) {
                                                                    Icon(
                                                                        imageVector = Icons.Default.Check,
                                                                        contentDescription = null,
                                                                        tint = MaterialTheme.colorScheme.primary,
                                                                        modifier = Modifier.size(18.dp)
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            },
                                            confirmButton = {
                                                TextButton(onClick = { expandedSupDropdown = false }) {
                                                    Text(AndroidStringCatalog.get(lang, "inline_customerscreen_0d3658f984"))
                                                }
                                            }
                                        )
                                    }
                                }

                                // Rate Group: Displays the buying rate (Kroy Rate ${localCur}) automatically of the selected wallet batch
                                if (activeBatch != null) {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f)),
                                        shape = RoundedCornerShape(12.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f))
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.TrendingUp,
                                                    contentDescription = "",
                                                    tint = MaterialTheme.colorScheme.secondary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Text(
                                                    text = AndroidStringCatalog.get(lang, "inline_customerscreen_765ab7aa3c"),
                                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Text(
                                                text = "৳ $supplierRate ${localCur}",
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Black),
                                                color = MaterialTheme.colorScheme.secondary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Previous Due Payment Card (only shown if there are previous dues and not returning advance)
                if (previousDueSar > CUSTOMER_MONEY_THRESHOLD && !isAdvanceReturn) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)),
                            shape = RoundedCornerShape(14.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = AndroidStringCatalog.get(lang, "inline_customerscreen_eb4a0c28fa"),
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.errorContainer,
                                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                                    ) {
                                        Text(
                                            text = "${sarFormatter.format(previousDueSar)} ${foreignCur}Due",
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                        )
                                    }
                                }

                                Text(
                                    text = AndroidStringCatalog.get(lang, "inline_customerscreen_1e43775e1e"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { isDueCalcOpen = true }
                                ) {
                                    OutlinedTextField(
                                        value = dueSarCollected,
                                        onValueChange = { },
                                        readOnly = true,
                                        enabled = false,
                                        label = { Text(AndroidStringCatalog.get(lang, "inline_customerscreen_20ccc5dca0")) },
                                        placeholder = { Text("0.00") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        leadingIcon = { Icon(Icons.Default.PriceCheck, contentDescription = "") },
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                            disabledBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                            disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                // Advance Return Card (shown if there is advance balance, whether explicitly Advance Return or along with a new sale)
                if (previousDueSar <= CUSTOMER_MONEY_THRESHOLD.negate() && (!isDueOnly || isAdvanceReturn)) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD).copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(14.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF64B5F6).copy(alpha = 0.5f))
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = AndroidStringCatalog.get(lang, "inline_customerscreen_5fdc35b4dd"),
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = Color(0xFF1565C0)
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    ) {
                                        Text(
                                            text = "${sarFormatter.format(previousDueSar.abs())} ${foreignCur}Owed",
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                        )
                                    }
                                }

                                Text(
                                    text = AndroidStringCatalog.get(lang, "inline_customerscreen_7167f05872"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { isDueCalcOpen = true }
                                ) {
                                    OutlinedTextField(
                                        value = dueSarCollected,
                                        onValueChange = { },
                                        readOnly = true,
                                        enabled = false,
                                        label = { Text(AndroidStringCatalog.get(lang, "inline_customerscreen_73bb2ca55d")) },
                                        placeholder = { Text("0.00") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        leadingIcon = { Icon(Icons.Default.CurrencyExchange, contentDescription = "") },
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                            disabledBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                            disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                // A. BIKROY RATE (CUSTOMER RATE) + RIYAL (DISABLE) Group Card
                if (!isDueOnly) {
                    item {
                        val amtSarVal = amountSar.toBigDecimalOrNull() ?: MoneyMath.ZERO_AMOUNT
                        val isAmtPresent = amtSarVal > CUSTOMER_MONEY_THRESHOLD
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = if (isAmtPresent) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(14.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = AndroidStringCatalog.get(lang, "inline_customerscreen_0f99c363b6"),
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (isAmtPresent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    // Sales Rate (Bikroy Rate) - editable customer rate
                                    if (isRateBasedModeEnabled) {
                                        OutlinedTextField(
                                            value = customerRate,
                                            onValueChange = onCustomerRateChange,
                                            enabled = isAmtPresent,
                                            label = { Text(AndroidStringCatalog.get(lang, "inline_customerscreen_75601aa3c1")) },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.weight(1f)
                                        )
                                    } else {
                                        LaunchedEffect(Unit) { onCustomerRateChange("1.0") }
                                    }

                                    // Riyal Amount (Disabled - Read-only / display style only)
                                    OutlinedTextField(
                                        value = "$amountSar ${foreignCur}",
                                        onValueChange = { },
                                        enabled = false, // Disabled as requested!
                                        label = { Text(AndroidStringCatalog.get(lang, "inline_customerscreen_3759ad6328")) },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                            disabledBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                            disabledLabelColor = MaterialTheme.colorScheme.outline
                                        ),
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                // Riyal Received & Bangladesh Payout ${localCur}inside a single horizontal Row side-by-side as requested
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    // Riyal Received ${foreignCur} - editable, restored as requested
                                    OutlinedTextField(
                                        value = sarCollected,
                                        onValueChange = { inputStr ->
                                            if (inputStr.all { it.isDigit() || it == '.' }) {
                                                onSarCollectedChange(inputStr)
                                            }
                                        },
                                        enabled = isAmtPresent,
                                        label = { Text(AndroidStringCatalog.get(lang, "inline_customerscreen_5630bce913")) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.weight(1f)
                                    )

                                    // ${localCur}field (Disabled - Read-only calculated total, as requested! And rounded integers only)
                                    OutlinedTextField(
                                        value = bdtDisbursed,
                                        onValueChange = { },
                                        enabled = false, // Disabled/read-only as requested!
                                        label = { Text(AndroidStringCatalog.get(lang, "inline_customerscreen_46699e3019")) },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                            disabledBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                            disabledLabelColor = MaterialTheme.colorScheme.outline
                                        ),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }

                // B. ${localCur}Total & SUMMARY cards
                item {
                    val sarVal = amountSar.toBigDecimalOrNull() ?: MoneyMath.ZERO_AMOUNT
                    val custRateVal = customerRate.toBigDecimalOrNull() ?: MoneyMath.ZERO_RATE
                    val bdtTotal = MoneyMath.multiply(sarVal, custRateVal)
                    
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Total Payout ${localCur}Card
                        if (sarVal > CUSTOMER_MONEY_THRESHOLD) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                                shape = RoundedCornerShape(14.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF81C784).copy(alpha = 0.5f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = AndroidStringCatalog.get(lang, "inline_customerscreen_3d0eda7bfb"),
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = Color(0xFF1B5E20)
                                    )
                                    Text(
                                        text = "৳ ${bdtFormatter.format(bdtTotal)} ${localCur}",
                                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                                        color = Color(0xFF1B5E20)
                                    )
                                }
                            }
                        }

                        // Detailed summary card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                            shape = RoundedCornerShape(14.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = AndroidStringCatalog.get(lang, "inline_customerscreen_53b8d1aa04"),
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(text = AndroidStringCatalog.get(lang, "inline_customerscreen_f989466287"), fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                                    Text(text = "$amountSar ${foreignCur}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                val amtVal = amountSar.toBigDecimalOrNull() ?: MoneyMath.ZERO_AMOUNT
                                val collectedVal = sarCollected.toBigDecimalOrNull() ?: MoneyMath.ZERO_AMOUNT
                                val newDueSar = MoneyMath.subtract(amtVal, collectedVal)
                                if (newDueSar > CUSTOMER_DETAIL_THRESHOLD) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(text = AndroidStringCatalog.get(lang, "inline_customerscreen_7424cd3ff8"), fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                                        Text(text = "${String.format("%.2f", newDueSar)} ${foreignCur}", fontSize = 12.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.error)
                                    }
                                }
                                if (previousDueSar > CUSTOMER_MONEY_THRESHOLD) {
                                    val dueAmtVal = dueSarCollected.toBigDecimalOrNull() ?: MoneyMath.ZERO_AMOUNT
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(text = AndroidStringCatalog.get(lang, "inline_customerscreen_7fceb532b9"), fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                                        Text(text = "$dueAmtVal ${foreignCur}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                                    }
                                } else if (previousDueSar <= CUSTOMER_MONEY_THRESHOLD.negate()) {
                                    val dueAmtVal = dueSarCollected.toBigDecimalOrNull() ?: MoneyMath.ZERO_AMOUNT
                                    if (dueAmtVal.signum() > 0) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text(text = AndroidStringCatalog.get(lang, "inline_customerscreen_7fae42c59f"), fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                                            Text(text = "$dueAmtVal ${foreignCur}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                                        }
                                    }
                                }
                                if (paymentMethod != "Cash") {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(text = AndroidStringCatalog.get(lang, "inline_customerscreen_cd1e0b92ff"), fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                                        val displayAccountNo = if (recipientNo == "N/A" || recipientNo.isBlank()) "" else recipientNo
                                        Text(text = displayAccountNo, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(text = AndroidStringCatalog.get(lang, "inline_customerscreen_84af4f8f16"), fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                                    Text(text = paymentMethod, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                }
                                if (!isDueOnly) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(text = AndroidStringCatalog.get(lang, "inline_customerscreen_7b1bdbed32"), fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                                        Text(text = "৳ $customerRate", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // F. Sticky Bottom Action Buttons (docked firmly outside the scroll views for 100% constant visibility)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (currentStep == 1) {
                val amount = amountSar.toBigDecimalOrNull() ?: MoneyMath.ZERO_AMOUNT
                val isDueOnly = amount.signum() == 0 && previousDueSar > CUSTOMER_MONEY_THRESHOLD
                val isAdvanceAct = amount.signum() == 0 && previousDueSar <= CUSTOMER_MONEY_THRESHOLD.negate() && isAdvanceReturn
                val isRegularEligible = amount > CUSTOMER_MONEY_THRESHOLD
                val isStep1NextEnabled = isDueOnly || isAdvanceAct || isRegularEligible

                Button(
                    onClick = { currentStep = 2 },
                    enabled = isStep1NextEnabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(
                        text = AndroidStringCatalog.get(lang, "inline_customerscreen_eb59ec36d0"),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(Icons.Default.ArrowForward, contentDescription = "", modifier = Modifier.size(16.dp))
                }
            } else {
                val amount = amountSar.toBigDecimalOrNull() ?: MoneyMath.ZERO_AMOUNT
                val dueAmount = dueSarCollected.toBigDecimalOrNull() ?: MoneyMath.ZERO_AMOUNT
                val isNumberValid = (paymentMethod == "Cash") || recipientNo.isNotBlank()
                val isAdvanceAct = amount.signum() == 0 && previousDueSar <= CUSTOMER_MONEY_THRESHOLD.negate() && isAdvanceReturn
                val isSubmitEnabled = if (isDueOnly || isAdvanceAct) {
                    dueAmount.signum() > 0 && isNumberValid
                } else {
                    selectedBatchId != null && amount.signum() > 0 && isNumberValid
                }

                Button(
                    onClick = onSubmit,
                    enabled = isSubmitEnabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                ) {
                    Icon(Icons.Default.Check, contentDescription = "", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = AndroidStringCatalog.get(lang, "inline_customerscreen_dd15dada89"),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }

    if (isDueCalcOpen) {
        CalculatorDialog(
            initialValue = dueSarCollected,
            title = AndroidStringCatalog.get(lang, "inline_customerscreen_20ccc5dca0"),
            lang = lang,
            onDismiss = { isDueCalcOpen = false },
            onConfirm = { result ->
                onDueSarCollectedChange(result)
                isDueCalcOpen = false
            }
        )
    }
}

/**
 * Modern full-page transaction editor that replaces the outdated pop-up dialog modal.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditTransactionPage(
    lang: String,
    tx: com.safa.account.data.model.RemittanceTransaction,
    suppliers: List<com.safa.account.data.model.Supplier>,
    editSupplierId: Int?,
    onEditSupplierIdChange: (Int?) -> Unit,
    editAmountSar: String,
    onEditAmountSarChange: (String) -> Unit,
    editCustomerRate: String,
    onEditCustomerRateChange: (String) -> Unit,
    editSupplierRate: String,
    onEditSupplierRateChange: (String) -> Unit,
    editReceiverName: String,
    onEditReceiverNameChange: (String) -> Unit,
    editReceiverPhone: String,
    onEditReceiverPhoneChange: (String) -> Unit,
    editReceiverAccountType: String,
    onEditReceiverAccountTypeChange: (String) -> Unit,
    editReceiverAccountNo: String,
    onEditReceiverAccountNoChange: (String) -> Unit,
    editSarCollected: String,
    onEditSarCollectedChange: (String) -> Unit,
    editBdtDisbursed: String,
    onEditBdtDisbursedChange: (String) -> Unit,
    editTxNotes: String,
    onEditTxNotesChange: (String) -> Unit,
    editStatus: String,
    onEditStatusChange: (String) -> Unit,
    isRateBasedModeEnabled: Boolean = true,
    isSupplierRateEnabled: Boolean = true,
    foreignCur: String = "SAR",
    localCur: String = "BDT",
    isEditAmountCalCOpen: Boolean,
    onIsEditAmountCalCOpenChange: (Boolean) -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit
) {
    val decimalFormatter = remember { DecimalFormat("#,##0") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Upper App-Bar Header Rows
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = onCancel, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", modifier = Modifier.size(20.dp))
                }
                Text(
                    text = AndroidStringCatalog.get(lang, "inline_customerscreen_f4e373cfe9"),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            // Small indicator tag of current status
            val badgeColor = when (editStatus) {
                "Delivered" -> Color(0xFF2E7D32)
                "Cancelled" -> Color(0xFFC62828)
                else -> Color(0xFFE65100)
            }
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = badgeColor.copy(alpha = 0.12f),
                contentColor = badgeColor
            ) {
                Text(
                    text = editStatus,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                )
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Card 2: Financial Matrix (Calculation details first at top as requested)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.Calculate, contentDescription = "", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            Text(
                                text = AndroidStringCatalog.get(lang, "inline_customerscreen_ccc0085f03"),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        // ${foreignCur}Amount Input (With Calculator inline)
                        OutlinedTextField(
                            value = editAmountSar,
                            onValueChange = { onEditAmountSarChange(it) },
                            readOnly = true,
                            label = { Text(AndroidStringCatalog.get(lang, "inline_customerscreen_704f504756")) },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Calculate,
                                    contentDescription = "Calculator",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.clickable { onIsEditAmountCalCOpenChange(true) }
                                )
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Customer Rate
                        if (isRateBasedModeEnabled) {
                            OutlinedTextField(
                                value = editCustomerRate,
                                onValueChange = { onEditCustomerRateChange(it) },
                                label = { Text(AndroidStringCatalog.get(lang, "inline_customerscreen_59bf1dc769")) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            LaunchedEffect(Unit) { onEditCustomerRateChange("1.0") }
                        }

                        // Real-time custom collected ${foreignCur}/ ${localCur}tracking moved up to Calculation matrix
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = editSarCollected,
                                onValueChange = { onEditSarCollectedChange(it) },
                                label = { Text(AndroidStringCatalog.get(lang, "inline_customerscreen_16c5328a35")) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = editBdtDisbursed,
                                onValueChange = { onEditBdtDisbursedChange(it) },
                                label = { Text(AndroidStringCatalog.get(lang, "inline_customerscreen_a275e13aef")) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Calculated Dynamic Summary Output (To help user understand)
                        val totalBdt = MoneyMath.multiply(
                            editAmountSar.toBigDecimalOrNull() ?: MoneyMath.ZERO_AMOUNT,
                            editCustomerRate.toBigDecimalOrNull() ?: MoneyMath.ZERO_RATE
                        )
                        if (totalBdt.signum() > 0) {
                            Surface(
                                color = Color(0xFFE8F5E9),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = AndroidStringCatalog.get(lang, "inline_customerscreen_68563b492c"),
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                        color = Color(0xFF1B5E20)
                                    )
                                    Text(
                                        text = "৳ ${decimalFormatter.format(totalBdt)}",
                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Black),
                                        color = Color(0xFF1B5E20)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Card 3: Recipient Payee Info
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.Person, contentDescription = "", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            Text(
                                text = AndroidStringCatalog.get(lang, "inline_customerscreen_e1554f0d9c"),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        OutlinedTextField(
                            value = editReceiverName,
                            onValueChange = { onEditReceiverNameChange(it) },
                            label = { Text(AndroidStringCatalog.get(lang, "inline_customerscreen_f44683f2d9")) },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = editReceiverPhone,
                            onValueChange = { onEditReceiverPhoneChange(it) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            label = { Text(AndroidStringCatalog.get(lang, "inline_customerscreen_f592fbed68")) },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Payment Channel selection
                        Text(
                            text = AndroidStringCatalog.get(lang, "inline_customerscreen_6e2b5a4908"),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.outline
                        )
                        val channels = listOf("Bkash", "Nagad", "Rocket", "Bank Transfer", "Cash")
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            channels.forEach { ch ->
                                FilterChip(
                                    selected = editReceiverAccountType == ch,
                                    onClick = { onEditReceiverAccountTypeChange(ch) },
                                    label = { Text(ch, fontWeight = FontWeight.Bold) }
                                )
                            }
                        }

                        OutlinedTextField(
                            value = editReceiverAccountNo,
                            onValueChange = { onEditReceiverAccountNoChange(it) },
                            label = { Text(AndroidStringCatalog.get(lang, "inline_customerscreen_9f0f35249b")) },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Card 4: Statuses & Notes
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.EditCalendar, contentDescription = "", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            Text(
                                text = AndroidStringCatalog.get(lang, "inline_customerscreen_168b24e627"),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        // Notes textfield
                        OutlinedTextField(
                            value = editTxNotes,
                            onValueChange = { onEditTxNotesChange(it) },
                            label = { Text(AndroidStringCatalog.get(lang, "inline_customerscreen_abe29fbe0f")) },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Status Flow Check
                        Text(
                            text = AndroidStringCatalog.get(lang, "inline_customerscreen_fd7518647f"),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.outline
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("Pending", "Delivered", "Cancelled").forEach { itemState ->
                                FilterChip(
                                    selected = editStatus == itemState,
                                    onClick = { onEditStatusChange(itemState) },
                                    label = { Text(itemState, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Action Buttons bottom area
        Button(
            onClick = onSave,
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.CheckCircle, contentDescription = "", modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = AndroidStringCatalog.get(lang, "inline_customerscreen_2d9be001aa"),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.ExtraBold),
                color = Color.White
            )
        }
    }
}

// Helper to generate a genuine PDF receipt page to share
fun generatePdfReceipt(
    context: android.content.Context,
    customerName: String,
    amountSar: BigDecimal,
    customerRate: BigDecimal,
    dueCollectedSar: BigDecimal,
    newDueSar: BigDecimal,
    totalRemainingDueSar: BigDecimal,
    paymentMethod: String,
    recipientNo: String,
    timestamp: Long,
    lang: String,
    isAdvanceReturn: Boolean = false,
    foreignCur: String = "SAR",
    localCur: String = "BDT"
): Uri? {
    try {
        val pdfDocument = android.graphics.pdf.PdfDocument()
        val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 standard
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas
        val paint = android.graphics.Paint()
        
        // Header Green Block
        paint.color = 0xFF2E7D32.toInt()
        canvas.drawRect(0f, 0f, 595f, 130f, paint)
        
        // Title Text
        paint.color = android.graphics.Color.WHITE
        paint.textSize = 24f
        paint.isFakeBoldText = true
        canvas.drawText("SAFA Money Transfer", 40f, 60f, paint)
        
        // Subtitle
        paint.textSize = 13f
        paint.isFakeBoldText = false
        canvas.drawText(AndroidStringCatalog.get(lang, "inline_customerscreen_bf895dc046"), 40f, 95f, paint)
        
        // Timestamp on right
        paint.textSize = 13f
        paint.textAlign = android.graphics.Paint.Align.RIGHT
        val sdf = SimpleDateFormat("dd-MM-yyyy hh:mm a", Locale.getDefault())
        canvas.drawText(sdf.format(Date(timestamp)), 555f, 60f, paint)
        
        // Setup Body
        paint.textAlign = android.graphics.Paint.Align.LEFT
        paint.color = android.graphics.Color.BLACK
        paint.textSize = 15f
        var currentY = 180f
        
        fun drawRow(label: String, valStr: String) {
            paint.color = android.graphics.Color.DKGRAY
            canvas.drawText(label, 40f, currentY, paint)
            paint.color = android.graphics.Color.BLACK
            paint.isFakeBoldText = true
            paint.textAlign = android.graphics.Paint.Align.RIGHT
            canvas.drawText(valStr, 555f, currentY, paint)
            paint.textAlign = android.graphics.Paint.Align.LEFT
            paint.isFakeBoldText = false
            currentY += 32f
        }
        
        drawRow(AndroidStringCatalog.get(lang, "inline_customerscreen_e09a5cd027"), sdf.format(Date(timestamp)))
        drawRow(AndroidStringCatalog.get(lang, "inline_customerscreen_5645acea96"), customerName)
        
        val calculatedTotalBdt = MoneyMath.multiply(amountSar, customerRate)
        if (amountSar > CUSTOMER_MONEY_THRESHOLD) {
            drawRow(AndroidStringCatalog.get(lang, "inline_customerscreen_75213bad55"), "$amountSar ${foreignCur}")
            drawRow(AndroidStringCatalog.get(lang, "inline_customerscreen_901fcf2e6b"), "$customerRate BDT/SAR")
            drawRow(AndroidStringCatalog.get(lang, "inline_customerscreen_e6de184d2e"), "BDT $calculatedTotalBdt")
        }
        
        drawRow(AndroidStringCatalog.get(lang, "inline_customerscreen_f0d6009e5c"), paymentMethod)
        if (paymentMethod != "Cash" && recipientNo.isNotBlank() && recipientNo != "N/A") {
            drawRow(AndroidStringCatalog.get(lang, "inline_customerscreen_cd1e0b92ff"), recipientNo)
        }
        
        if (dueCollectedSar > CUSTOMER_MONEY_THRESHOLD) {
            drawRow(if (isAdvanceReturn) (AndroidStringCatalog.get(lang, "inline_customerscreen_5f002413bb")) else (AndroidStringCatalog.get(lang, "inline_customerscreen_3f1bbaecc3")), "$dueCollectedSar ${foreignCur}")
        }
        
        // Horizontal grey divider line
        paint.color = android.graphics.Color.LTGRAY
        canvas.drawLine(40f, currentY, 555f, currentY, paint)
        currentY += 30f
        
        if (newDueSar > CUSTOMER_DETAIL_THRESHOLD) {
            drawRow(AndroidStringCatalog.get(lang, "inline_customerscreen_7e0cff546e"), "$newDueSar ${foreignCur}")
        } else if (newDueSar < CUSTOMER_DETAIL_THRESHOLD.negate()) {
            drawRow(AndroidStringCatalog.get(lang, "inline_customerscreen_b00533264d"), "${-newDueSar} ${foreignCur}")
        }
        
        if (totalRemainingDueSar > CUSTOMER_MONEY_THRESHOLD) {
            drawRow(AndroidStringCatalog.get(lang, "inline_customerscreen_1f3d8f2dbd"), "$totalRemainingDueSar ${foreignCur}")
        } else if (totalRemainingDueSar < CUSTOMER_MONEY_THRESHOLD.negate()) {
            drawRow(AndroidStringCatalog.get(lang, "inline_customerscreen_bb8290ecab"), "${-totalRemainingDueSar} ${foreignCur}")
        } else {
            drawRow(AndroidStringCatalog.get(lang, "inline_customerscreen_7617323277"), AndroidStringCatalog.get(lang, "inline_customerscreen_045944a2b1"))
        }
        
        // Bottom privacy note and divider
        canvas.drawLine(40f, currentY + 15f, 555f, currentY + 15f, paint)
        currentY += 45f
        
        paint.color = android.graphics.Color.GRAY
        paint.textSize = 12f
        canvas.drawText(
            AndroidStringCatalog.get(lang, "inline_customerscreen_0d8cdf29ff"),
            40f, currentY, paint
        )
        
        pdfDocument.finishPage(page)
        
        val cacheFile = File(context.cacheDir, "receipt_print.pdf")
        val outStream = FileOutputStream(cacheFile)
        pdfDocument.writeTo(outStream)
        outStream.flush()
        outStream.close()
        pdfDocument.close()
        
        return androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            cacheFile
        )
    } catch (e: Exception) {
        com.safa.account.utils.SafaLogger.error("RECEIPT_PDF", "Receipt PDF generation failed", e)
        return null
    }
}

// Helper to generate a genuine PNG image card of the receipt
fun generateImageReceipt(
    context: android.content.Context,
    customerName: String,
    amountSar: BigDecimal,
    customerRate: BigDecimal,
    dueCollectedSar: BigDecimal,
    newDueSar: BigDecimal,
    totalRemainingDueSar: BigDecimal,
    paymentMethod: String,
    recipientNo: String,
    timestamp: Long,
    lang: String,
    isAdvanceReturn: Boolean = false,
    foreignCur: String = "SAR",
    localCur: String = "BDT"
): Uri? {
    try {
        val width = 600
        val height = 780
        val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint()
        
        // Clean grey bg to frame the card beautifully
        canvas.drawColor(0xFFF4F6F4.toInt())
        
        // Receipt Inner Card shape
        paint.color = android.graphics.Color.WHITE
        paint.style = android.graphics.Paint.Style.FILL
        canvas.drawRoundRect(24f, 24f, width - 24f, height - 24f, 20f, 20f, paint)
        
        // Simple subtle card outline
        paint.color = 0xFFE2E8F0.toInt()
        paint.style = android.graphics.Paint.Style.STROKE
        paint.strokeWidth = 2f
        canvas.drawRoundRect(24f, 24f, width - 24f, height - 24f, 20f, 20f, paint)
        
        // Reset paint to fill
        paint.style = android.graphics.Paint.Style.FILL
        
        // Inner Green Accent Header ribbon
        paint.color = 0xFF1B5E20.toInt()
        canvas.drawRoundRect(44f, 44f, width - 44f, 130f, 12f, 12f, paint)
        
        // Text inside Ribbon
        paint.color = android.graphics.Color.WHITE
        paint.textSize = 21f
        paint.isFakeBoldText = true
        paint.textAlign = android.graphics.Paint.Align.CENTER
        canvas.drawText("SAFA Money Transfer", width / 2f, 94f, paint)
        
        // Detail Lines
        paint.textAlign = android.graphics.Paint.Align.LEFT
        paint.textSize = 15f
        paint.isFakeBoldText = false
        var currentY = 195f
        
        fun drawVisualRow(lbl: String, valS: String, highlight: Boolean = false) {
            paint.color = if (highlight) 0xFF1976D2.toInt() else android.graphics.Color.DKGRAY
            paint.isFakeBoldText = false
            canvas.drawText(lbl, 55f, currentY, paint)
            
            paint.color = if (highlight) 0xFF2E7D32.toInt() else android.graphics.Color.BLACK
            paint.isFakeBoldText = true
            paint.textAlign = android.graphics.Paint.Align.RIGHT
            canvas.drawText(valS, width - 55f, currentY, paint)
            
            paint.textAlign = android.graphics.Paint.Align.LEFT
            currentY += 34f
        }
        
        val sdf = SimpleDateFormat("dd-MM-yyyy hh:mm a", Locale.getDefault())
        drawVisualRow(AndroidStringCatalog.get(lang, "inline_customerscreen_e09a5cd027"), sdf.format(Date(timestamp)))
        drawVisualRow(AndroidStringCatalog.get(lang, "inline_customerscreen_5645acea96"), customerName)
        
        val calculatedTotalBdt = MoneyMath.multiply(amountSar, customerRate)
        if (amountSar > CUSTOMER_MONEY_THRESHOLD) {
            drawVisualRow(AndroidStringCatalog.get(lang, "inline_customerscreen_75213bad55"), "$amountSar ${foreignCur}")
            drawVisualRow(AndroidStringCatalog.get(lang, "inline_customerscreen_5b125d2d0e"), "$customerRate BDT/SAR")
            drawVisualRow(AndroidStringCatalog.get(lang, "inline_customerscreen_973d56a2ea"), "BDT $calculatedTotalBdt", highlight = true)
        }
        
        drawVisualRow(AndroidStringCatalog.get(lang, "inline_customerscreen_f0d6009e5c"), paymentMethod)
        if (paymentMethod != "Cash" && recipientNo.isNotBlank() && recipientNo != "N/A") {
            drawVisualRow(AndroidStringCatalog.get(lang, "inline_customerscreen_54e1eeccf5"), recipientNo)
        }
        
        if (dueCollectedSar > CUSTOMER_MONEY_THRESHOLD) {
            drawVisualRow(if (isAdvanceReturn) (AndroidStringCatalog.get(lang, "inline_customerscreen_5f002413bb")) else (AndroidStringCatalog.get(lang, "inline_customerscreen_3f1bbaecc3")), "$dueCollectedSar ${foreignCur}")
        }
        
        // Solid divider line
        paint.color = 0xFFE2E8F0.toInt()
        paint.strokeWidth = 1.5f
        canvas.drawLine(55f, currentY + 10f, width - 55f, currentY + 10f, paint)
        currentY += 45f
        
        if (newDueSar > CUSTOMER_DETAIL_THRESHOLD) {
            drawVisualRow(AndroidStringCatalog.get(lang, "inline_customerscreen_7e0cff546e"), "$newDueSar ${foreignCur}")
        } else if (newDueSar < CUSTOMER_DETAIL_THRESHOLD.negate()) {
            drawVisualRow(AndroidStringCatalog.get(lang, "inline_customerscreen_ec44b6a55e"), "${-newDueSar} ${foreignCur}")
        }
        
        if (totalRemainingDueSar > CUSTOMER_MONEY_THRESHOLD) {
            drawVisualRow(AndroidStringCatalog.get(lang, "inline_customerscreen_1f3d8f2dbd"), "$totalRemainingDueSar ${foreignCur}")
        } else if (totalRemainingDueSar < CUSTOMER_MONEY_THRESHOLD.negate()) {
            drawVisualRow(AndroidStringCatalog.get(lang, "inline_customerscreen_6c40219b13"), "${-totalRemainingDueSar} ${foreignCur}")
        } else {
            drawVisualRow(AndroidStringCatalog.get(lang, "inline_customerscreen_7617323277"), AndroidStringCatalog.get(lang, "inline_customerscreen_045944a2b1"))
        }
        
        // Rounded bottom informational alert box
        currentY += 15f
        paint.color = 0xFFF1F8E9.toInt() // highly subtle soft light green tint
        canvas.drawRoundRect(55f, currentY, width - 55f, currentY + 54f, 10f, 10f, paint)
        
        paint.color = 0xFF2E7D32.toInt()
        paint.textSize = 13f
        paint.isFakeBoldText = true
        paint.textAlign = android.graphics.Paint.Align.CENTER
        canvas.drawText(
            AndroidStringCatalog.get(lang, "inline_customerscreen_0b151be1cc"),
            width / 2f, currentY + 32f, paint
        )
        
        val cacheFile = File(context.cacheDir, "receipt_share.png")
        val stream = FileOutputStream(cacheFile)
        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
        stream.flush()
        stream.close()
        
        return androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            cacheFile
        )
    } catch (e: Exception) {
        com.safa.account.utils.SafaLogger.error("RECEIPT_IMAGE", "Receipt image generation failed", e)
        return null
    }
}

fun shareNativeFile(context: android.content.Context, fileUri: Uri, mimeType: String, logTitle: String) {
    val intent = android.content.Intent().apply {
        action = android.content.Intent.ACTION_SEND
        putExtra(android.content.Intent.EXTRA_STREAM, fileUri)
        type = mimeType
        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(android.content.Intent.createChooser(intent, logTitle))
}

@Composable
fun TransactionConfirmationPage(
    lang: String,
    customerName: String,
    amountSar: BigDecimal,
    customerRate: BigDecimal,
    collectedSar: BigDecimal,
    dueCollectedSar: BigDecimal,
    newDueSar: BigDecimal,
    totalRemainingDueSar: BigDecimal,
    paymentMethod: String,
    recipientNo: String,
    timestamp: Long,
    isAdvanceReturn: Boolean = false,
    foreignCur: String = "SAR",
    localCur: String = "BDT",
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val calculatedTotalBdt = MoneyMath.multiply(amountSar, customerRate)
    
    val shareTextStr = remember(customerName, amountSar, newDueSar, dueCollectedSar, totalRemainingDueSar, lang, paymentMethod, recipientNo, timestamp) {
        val sb = java.lang.StringBuilder()
        val sdf = SimpleDateFormat("dd-MM-yyyy hh:mm a", Locale.getDefault())
        val formattedDate = sdf.format(Date(timestamp))
        if (lang == "BN") {
            sb.append("📢 SAFA Money Transfer\n")
            sb.append("=== লেনদেনের রসিদ ===\n\n")
            sb.append("তারিখ: $formattedDate\n")
            sb.append("গ্রাহক: $customerName\n")
            if (amountSar > CUSTOMER_MONEY_THRESHOLD) {
                sb.append("নতুন যুক্ত লেনদেন: $amountSar SAR\n")
                sb.append("বিনিময় হার: ৳ $customerRate\n")
                sb.append("বাংলাদেশ প্রদান মূল্য: ৳ $calculatedTotalBdt BDT\n")
            }
            sb.append("পেমেন্ট মেথড: $paymentMethod\n")
            if (paymentMethod != "Cash" && recipientNo.isNotBlank() && recipientNo != "N/A") {
                sb.append("হিসাব নম্বর: $recipientNo\n")
            }
            if (dueCollectedSar > CUSTOMER_MONEY_THRESHOLD) {
                sb.append(if (isAdvanceReturn) (AndroidStringCatalog.get(lang, "inline_customerscreen_4c776e39d3")) else (AndroidStringCatalog.get(lang, "inline_customerscreen_fa577a01c0"))).append("$dueCollectedSar SAR\n")
            }
            sb.append("---------------------\n")
            if (newDueSar > CUSTOMER_DETAIL_THRESHOLD) {
                sb.append("নতুন বকেয়া: $newDueSar SAR\n")
            } else if (newDueSar < CUSTOMER_DETAIL_THRESHOLD.negate()) {
                sb.append("কাস্টমার অতিরিক্ত পাবেন: ${-newDueSar} SAR\n")
            }
            if (totalRemainingDueSar > CUSTOMER_MONEY_THRESHOLD) {
                sb.append("সর্বমোট বকেয়া: $totalRemainingDueSar SAR\n")
            } else if (totalRemainingDueSar < CUSTOMER_MONEY_THRESHOLD.negate()) {
                sb.append("কাস্টমার অতিরিক্ত পাবেন (সর্বমোট): ${-totalRemainingDueSar} SAR\n")
            } else {
                sb.append("গ্রাহকের আর কোনো বকেয়া নেই।\n")
            }
        } else {
            sb.append("📢 SAFA Money Transfer\n")
            sb.append("=== Transaction Receipt ===\n\n")
            sb.append("Date: $formattedDate\n")
            sb.append("Customer: $customerName\n")
            if (amountSar > CUSTOMER_MONEY_THRESHOLD) {
                sb.append("New Remittance: $amountSar SAR\n")
                sb.append("Rate: ৳ $customerRate\n")
                sb.append("Disbursed Total: ${localCur}$calculatedTotalBdt\n")
            }
            sb.append("Payment Method: $paymentMethod\n")
            if (paymentMethod != "Cash" && recipientNo.isNotBlank() && recipientNo != "N/A") {
                sb.append("Account No: $recipientNo\n")
            }
            if (dueCollectedSar > CUSTOMER_MONEY_THRESHOLD) {
                sb.append(if (isAdvanceReturn) "Advance Return Paid: " else "Previous Due Paid: ").append("$dueCollectedSar SAR\n")
            }
            sb.append("---------------------\n")
            if (newDueSar > CUSTOMER_DETAIL_THRESHOLD) {
                sb.append("New Outstanding Due: $newDueSar SAR\n")
            } else if (newDueSar < CUSTOMER_DETAIL_THRESHOLD.negate()) {
                sb.append("Customer Surplus Credit: ${-newDueSar} SAR\n")
            }
            if (totalRemainingDueSar > CUSTOMER_MONEY_THRESHOLD) {
                sb.append("Total Outstandings: $totalRemainingDueSar SAR\n")
            } else if (totalRemainingDueSar < CUSTOMER_MONEY_THRESHOLD.negate()) {
                sb.append("Customer Credit: ${-totalRemainingDueSar} SAR\n")
            } else {
                sb.append("Zero Outstanding Dues.\n")
            }
        }
        sb.toString()
    }

    fun triggerShareDirect(text: String) {
        val sendIntent = android.content.Intent().apply {
            action = android.content.Intent.ACTION_SEND
            putExtra(android.content.Intent.EXTRA_TEXT, text)
            type = "text/plain"
        }
        val shareIntent = android.content.Intent.createChooser(sendIntent, AndroidStringCatalog.get(lang, "inline_customerscreen_8c4e311c44"))
        context.startActivity(shareIntent)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Unbelievably compact inline navigation row - saves massive height
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = AndroidStringCatalog.get(lang, "inline_customerscreen_a736edcf1a"),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            
            // Success Badge Row instead of giant vertical elements
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFE8F5E9))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Success",
                    tint = Color(0xFF2E7D32),
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = AndroidStringCatalog.get(lang, "inline_customerscreen_6605ed4861"),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFF1B5E20)
                )
            }

            // Preview card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val sdf = SimpleDateFormat("dd-MM-yyyy hh:mm a", Locale.getDefault())
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = AndroidStringCatalog.get(lang, "inline_customerscreen_e09a5cd027"), style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.outline))
                        Text(text = sdf.format(Date(timestamp)), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = AndroidStringCatalog.get(lang, "inline_customerscreen_5645acea96"), style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.outline))
                        Text(text = customerName, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                    }
                    if (amountSar > CUSTOMER_MONEY_THRESHOLD) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = AndroidStringCatalog.get(lang, "inline_customerscreen_75213bad55"), style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.outline))
                            Text(text = "$amountSar ${foreignCur}", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = AndroidStringCatalog.get(lang, "inline_customerscreen_94923f5a72"), style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.outline))
                            Text(text = "৳ $calculatedTotalBdt ${localCur}", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary))
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = AndroidStringCatalog.get(lang, "inline_customerscreen_f0d6009e5c"), style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.outline))
                            Text(text = paymentMethod, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                        }
                        if (paymentMethod != "Cash" && recipientNo.isNotBlank() && recipientNo != "N/A") {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(text = AndroidStringCatalog.get(lang, "inline_customerscreen_cd1e0b92ff"), style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.outline))
                                Text(text = recipientNo, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                            }
                        }
                    }
                    if (dueCollectedSar > CUSTOMER_MONEY_THRESHOLD) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = if (isAdvanceReturn) (AndroidStringCatalog.get(lang, "inline_customerscreen_5f002413bb")) else (AndroidStringCatalog.get(lang, "inline_customerscreen_3f1bbaecc3")), style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.outline))
                            Text(text = "$dueCollectedSar ${foreignCur}", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32)))
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    
                    if (newDueSar > CUSTOMER_DETAIL_THRESHOLD) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = AndroidStringCatalog.get(lang, "inline_customerscreen_7e0cff546e"), style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.outline))
                            Text(text = "$newDueSar ${foreignCur}", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error))
                        }
                    } else if (newDueSar < CUSTOMER_DETAIL_THRESHOLD.negate()) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = AndroidStringCatalog.get(lang, "inline_customerscreen_0ebb5518a2"), style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.outline))
                            Text(text = "${-newDueSar} ${foreignCur}", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32)))
                        }
                    }
                    
                    if (totalRemainingDueSar > CUSTOMER_MONEY_THRESHOLD) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = AndroidStringCatalog.get(lang, "inline_customerscreen_70daa7fb2c"), style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.outline))
                            Text(text = "$totalRemainingDueSar ${foreignCur}", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.error))
                        }
                    } else if (totalRemainingDueSar < CUSTOMER_MONEY_THRESHOLD.negate()) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = AndroidStringCatalog.get(lang, "inline_customerscreen_deae734bb4"), style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.outline))
                            Text(text = "${-totalRemainingDueSar} ${foreignCur}", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Black, color = Color(0xFF2E7D32)))
                        }
                    } else {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = AndroidStringCatalog.get(lang, "inline_customerscreen_7617323277"), style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.outline))
                            Text(text = AndroidStringCatalog.get(lang, "inline_customerscreen_3c3889dd0d"), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32)))
                        }
                    }
                }
            }

            Text(
                text = AndroidStringCatalog.get(lang, "inline_customerscreen_591a96d516"),
                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.outline),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            // Dynamic Share Buttons that generate real content types aligned horizontally!
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { triggerShareDirect(shareTextStr) },
                    modifier = Modifier.weight(1f).height(46.dp),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    Icon(imageVector = Icons.Default.Share, contentDescription = "", modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = AndroidStringCatalog.get(lang, "inline_customerscreen_e128aff38f"),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }

                OutlinedButton(
                    onClick = { 
                        val pdfUri = generatePdfReceipt(
                            context = context,
                            customerName = customerName,
                            amountSar = amountSar,
                            customerRate = customerRate,
                            dueCollectedSar = dueCollectedSar,
                            newDueSar = newDueSar,
                            totalRemainingDueSar = totalRemainingDueSar,
                            paymentMethod = paymentMethod,
                            recipientNo = recipientNo,
                            timestamp = timestamp,
                            lang = lang,
                            isAdvanceReturn = isAdvanceReturn
                        )
                        if (pdfUri != null) {
                            shareNativeFile(context, pdfUri, "application/pdf", AndroidStringCatalog.get(lang, "inline_customerscreen_0a720dc7f2"))
                        } else {
                            Toast.makeText(context, AndroidStringCatalog.get(lang, "inline_customerscreen_a386945d13"), Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.weight(1f).height(46.dp),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    Icon(imageVector = Icons.Default.Print, contentDescription = "", modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = AndroidStringCatalog.get(lang, "inline_customerscreen_349b576bb0"),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }

                OutlinedButton(
                    onClick = { 
                        val imgUri = generateImageReceipt(
                            context = context,
                            customerName = customerName,
                            amountSar = amountSar,
                            customerRate = customerRate,
                            dueCollectedSar = dueCollectedSar,
                            newDueSar = newDueSar,
                            totalRemainingDueSar = totalRemainingDueSar,
                            paymentMethod = paymentMethod,
                            recipientNo = recipientNo,
                            timestamp = timestamp,
                            lang = lang,
                            isAdvanceReturn = isAdvanceReturn
                        )
                        if (imgUri != null) {
                            shareNativeFile(context, imgUri, "image/png", AndroidStringCatalog.get(lang, "inline_customerscreen_998e2982bc"))
                        } else {
                            Toast.makeText(context, AndroidStringCatalog.get(lang, "inline_customerscreen_430e6d35e5"), Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.weight(1f).height(46.dp),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    Icon(imageVector = Icons.Default.Image, contentDescription = "", modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = AndroidStringCatalog.get(lang, "inline_customerscreen_d6801dd174"),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().height(44.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
            ) {
                Text(
                    text = AndroidStringCatalog.get(lang, "inline_customerscreen_1981827092"),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}

@Composable
fun AddCustomerPage(
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
    viewModel: SafaViewModel
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
                    text = AndroidStringCatalog.get(lang, "inline_customerscreen_44790f9f7e"),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
            TextButton(onClick = onContactPicker) {
                Icon(Icons.Default.Contacts, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(AndroidStringCatalog.get(lang, "inline_customerscreen_22320b7621"), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
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
                        text = AndroidStringCatalog.get(lang, "inline_customerscreen_851e3f5dc1"),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                item {
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = onNameChange,
                        label = { Text(AndroidStringCatalog.get(lang, "inline_customerscreen_75c0728e42")) },
                        placeholder = { Text("e.g. Robin") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = "", tint = MaterialTheme.colorScheme.primary) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("customer_name_field")
                    )
                }

                item {
                    OutlinedTextField(
                        value = phoneInput,
                        onValueChange = onPhoneChange,
                        label = { Text(AndroidStringCatalog.get(lang, "inline_customerscreen_39457d1dd6")) },
                        placeholder = { Text("e.g. +88017xxxxxxxx / +966xxxxx") },
                        leadingIcon = { Icon(Icons.Default.Phone, contentDescription = "", tint = MaterialTheme.colorScheme.primary) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("customer_phone_field")
                    )
                }

                item {
                    OutlinedTextField(
                        value = addressInput,
                        onValueChange = onAddressChange,
                        label = { Text(AndroidStringCatalog.get(lang, "inline_customerscreen_73d90a8f75")) },
                        placeholder = { Text("e.g. Dhaka, Bangladesh / Riyadh, KSA") },
                        leadingIcon = { Icon(Icons.Default.Home, contentDescription = "", tint = MaterialTheme.colorScheme.primary) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("customer_address_field")
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
                    text = AndroidStringCatalog.get(lang, "inline_customerscreen_e3952b8349"),
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
                    text = AndroidStringCatalog.get(lang, "inline_customerscreen_e80930b66f"),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}
