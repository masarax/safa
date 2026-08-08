package com.safa.account.ui.screens

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
import com.safa.account.ui.viewmodel.HundiViewModel
import com.safa.account.ui.BiometricTriggerButton
import com.safa.account.ui.screens.CalculatorDialog
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*
import java.io.File
import java.io.FileOutputStream
import android.widget.Toast

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerScreen(
    viewModel: HundiViewModel,
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
    
    // Check if a specific customer profile is globally selected
    val selectedCustomerIdForProfile by viewModel.selectedCustomerIdForProfile.collectAsStateWithLifecycle()

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
                val totalSarSpent = customerTxs.sumOf { it.amountSar }
                val totalSarCollected = customerTxs.sumOf { it.sarCollected }
                val totalDue = totalSarSpent - totalSarCollected
                if (selectedFilterStatus == "Due") {
                    totalDue > 0.05
                } else {
                    totalDue < -0.05
                }
            }
        }

        // Apply sorting
        list = when (selectedSortOption) {
            "Oldest" -> list.sortedBy { it.timestamp }
            "A-Z" -> list.sortedBy { it.name.lowercase(java.util.Locale.ROOT) }
            "Due" -> list.sortedByDescending { customer ->
                val customerTxs = transactions.filter { it.customerId == customer.id }
                customerTxs.sumOf { it.amountSar } - customerTxs.sumOf { it.sarCollected }
            }
            "Advance" -> list.sortedBy { customer ->
                val customerTxs = transactions.filter { it.customerId == customer.id }
                customerTxs.sumOf { it.amountSar } - customerTxs.sumOf { it.sarCollected }
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
                            maxLines = 1
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
                            text = if (lang == "BN") "নতুন" else "Add",
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
                        placeholder = { Text(if (lang == "BN") "খুঁজুন..." else "Search...", fontSize = 12.sp) },
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
                                                    "Due" -> if (lang == "BN") "সর্বোচ্চ বকেয়া" else "Highest Due"
                                                    "Advance" -> if (lang == "BN") "সর্বোচ্চ অগ্রিম" else "Highest Advance"
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
                                            listOf("Newest", "Oldest", "A-Z", "Due", "Advance").forEach { option ->
                                                DropdownMenuItem(
                                                    text = {
                                                        Text(
                                                            when (option) {
                                                                "Oldest" -> if (lang == "BN") "পুরাতন প্রথম" else "Oldest First"
                                                                "A-Z" -> if (lang == "BN") "নাম A-Z" else "Name A-Z"
                                                                "Due" -> if (lang == "BN") "সর্বোচ্চ বকেয়া" else "Highest Due"
                                                                "Advance" -> if (lang == "BN") "সর্বোচ্চ অগ্রিম" else "Highest Advance"
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
                                                    "Due" -> if (lang == "BN") "শুধু বকেয়া কাস্টমার" else "Due Customers"
                                                    "Advance" -> if (lang == "BN") "শুধু অগ্রিম কাস্টমার" else "Advance Customers"
                                                    else -> if (lang == "BN") "সব গ্রাহক" else "All Customers"
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
                                                                "Due" -> if (lang == "BN") "শুধু বকেয়া কাস্টমার" else "Due Customers"
                                                                "Advance" -> if (lang == "BN") "শুধু অগ্রিম কাস্টমার" else "Advance Customers"
                                                                else -> if (lang == "BN") "সব গ্রাহক" else "All Customers"
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
                                text = if (lang == "BN") "কোনো কাস্টমার পাওয়া যায়নি!" else "No customer profiles found.",
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
                        items(filteredCustomers, key = { it.id }) { customer ->
                            val customerTxs = remember(transactions) {
                                transactions.filter { it.customerId == customer.id }
                            }
                            val totalSarSpent = customerTxs.sumOf { it.amountSar }

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
                                 val totalSarCollected = remember(customerTxs) { customerTxs.sumOf { it.sarCollected } }
                                 val totalDue = remember(totalSarSpent, totalSarCollected) { totalSarSpent - totalSarCollected }
                                 val totalBdt = remember(customerTxs) { customerTxs.sumOf { it.amountBdt } }

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
                                                     color = MaterialTheme.colorScheme.onSurface
                                                 )
                                                 Spacer(modifier = Modifier.height(1.dp))
                                                 Text(
                                                     text = customer.phone,
                                                     style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                                     color = MaterialTheme.colorScheme.outline
                                                 )
                                             }
                                         }
                                         
                                         Column(horizontalAlignment = Alignment.End) {
                                             Text(
                                                 text = if (totalDue <= -0.05) {
                                                     if (lang == "BN") "কাস্টমার পাবে" else "Customer Owed"
                                                 } else {
                                                     if (lang == "BN") "মোট বকেয়া" else "Total Due"
                                                 },
                                                 style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                 color = if (totalDue > 0.05) Color(0xFFD32F2F) else if (totalDue <= -0.05) Color(0xFF1565C0) else Color(0xFF2E7D32)
                                             )
                                             Spacer(modifier = Modifier.height(1.dp))
                                             Text(
                                                 text = "${currencyFormatter.format(Math.abs(totalDue))} ${foreignCur}",
                                                 style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                                                 color = if (totalDue > 0.05) Color(0xFFD32F2F) else if (totalDue <= -0.05) Color(0xFF1565C0) else Color(0xFF2E7D32)
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
                                                 text = if (lang == "BN") "মোট লেনদেন ${foreignCur}" else "Total Trans. ${foreignCur}",
                                                 style = MaterialTheme.typography.labelSmall,
                                                 color = MaterialTheme.colorScheme.outline
                                             )
                                             Text(
                                                 text = "${currencyFormatter.format(totalSarSpent)} ${foreignCur}",
                                                 style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                                 color = MaterialTheme.colorScheme.onSurface
                                             )
                                         }
                                         Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                             Text(
                                                 text = if (lang == "BN") "মোট পাঠানো ${localCur}" else "Total Sent ${localCur}",
                                                 style = MaterialTheme.typography.labelSmall,
                                                 color = MaterialTheme.colorScheme.outline
                                             )
                                             Text(
                                                 text = "${currencyFormatter.format(totalBdt)} ${localCur}",
                                                 style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                                 color = MaterialTheme.colorScheme.onSurface
                                             )
                                         }
                                         Column(horizontalAlignment = Alignment.End) {
                                             Text(
                                                 text = if (lang == "BN") "লেনদেন সংখ্যা" else "Trans. Count",
                                                 style = MaterialTheme.typography.labelSmall,
                                                 color = MaterialTheme.colorScheme.outline
                                             )
                                             Text(
                                                 text = "${customerTxs.size}",
                                                 style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                                 color = MaterialTheme.colorScheme.onSurface
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
    viewModel: HundiViewModel
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
    var confirmAmountSar by remember { mutableStateOf(0.0) }
    var confirmCustomerRate by remember { mutableStateOf(32.10) }
    var confirmCollectedSar by remember { mutableStateOf(0.0) }
    var confirmDueCollectedSar by remember { mutableStateOf(0.0) }
    var confirmNewDueSar by remember { mutableStateOf(0.0) }
    var confirmTotalRemainingDueSar by remember { mutableStateOf(0.0) }
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

    val totalSpentSar = remember(transactions) { transactions.sumOf { it.amountSar } }
    val totalDisbursedBdt = remember(transactions) { transactions.sumOf { it.amountBdt } }
    val totalUncollectedSar = remember(transactions) {
        (transactions.sumOf { it.amountSar } - transactions.sumOf { it.sarCollected })
    }

    val transactionsByDate = remember(transactions, lang) {
        transactions.sortedByDescending { it.timestamp }.groupBy {
            val sdf = SimpleDateFormat(if (lang == "BN") "d MMMM, yyyy" else "MMMM d, yyyy", Locale.US)
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
        } else if (txToEdit != null) {
            EditTransactionPage(
                lang = lang,
                tx = txToEdit!!,
                suppliers = suppliers,
                editSupplierId = editSupplierId,
                onEditSupplierIdChange = { editSupplierId = it },
                editAmountSar = editAmountSar,
                onEditAmountSarChange = { newValue ->
                    editAmountSar = newValue
                    val s = newValue.toDoubleOrNull() ?: 0.0
                    val r = editCustomerRate.toDoubleOrNull() ?: 0.0
                    editBdtDisbursed = Math.round(s * r).toString()
                },
                editCustomerRate = editCustomerRate,
                onEditCustomerRateChange = { newValue ->
                    editCustomerRate = newValue
                    val s = editAmountSar.toDoubleOrNull() ?: 0.0
                    val r = newValue.toDoubleOrNull() ?: 0.0
                    editBdtDisbursed = Math.round(s * r).toString()
                },
                editSupplierRate = editSupplierRate,
                onEditSupplierRateChange = { editSupplierRate = it },
                editReceiverName = editReceiverName,
                onEditReceiverNameChange = { editReceiverName = it },
                editReceiverPhone = editReceiverPhone,
                onEditReceiverPhoneChange = { editReceiverPhone = it },
                editReceiverAccountType = editReceiverAccountType,
                onEditReceiverAccountTypeChange = { editReceiverAccountType = it },
                editReceiverAccountNo = editReceiverAccountNo,
                onEditReceiverAccountNoChange = { editReceiverAccountNo = it },
                editSarCollected = editSarCollected,
                onEditSarCollectedChange = { editSarCollected = it },
                editBdtDisbursed = editBdtDisbursed,
                onEditBdtDisbursedChange = { editBdtDisbursed = it },
                editTxNotes = editTxNotes,
                onEditTxNotesChange = { editTxNotes = it },
                editStatus = editStatus,
                onEditStatusChange = { editStatus = it },
                isRateBasedModeEnabled = isRateBasedModeEnabled,
                isSupplierRateEnabled = isSupplierRateEnabled,
                isEditAmountCalCOpen = isEditAmountCalCOpen,
                onIsEditAmountCalCOpenChange = { isEditAmountCalCOpen = it },
                onCancel = { txToEdit = null },
                onSave = {
                    txActionToConfirm = "EDIT"
                    txPinCodeInput = ""
                    txPinErrorText = null
                    showTxSecurityDialog = true
                }
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
                    val latestRate = batch?.rate ?: currentRatesState?.supplierRate ?: 32.00
                    
                    inputSupplierRate = latestRate.toString()
                    
                    val defaultCustRate = currentRatesState?.customerRate ?: 32.10
                    inputCustomerRate = defaultCustRate.toString()

                    inputSarCollected = inputAmountSar

                    val amountDouble = inputAmountSar.toDoubleOrNull() ?: 0.0
                    inputBdtDisbursed = Math.round(amountDouble * defaultCustRate).toString()
                },
                amountSar = inputAmountSar,
                onAmountChange = {
                    inputAmountSar = it
                    val amountDouble = it.toDoubleOrNull() ?: 0.0
                    val rateDouble = inputCustomerRate.toDoubleOrNull() ?: 32.10
                    inputBdtDisbursed = Math.round(amountDouble * rateDouble).toString()
                    inputSarCollected = it
                },
                customerRate = inputCustomerRate,
                onCustomerRateChange = {
                    inputCustomerRate = it
                    val amountDouble = inputAmountSar.toDoubleOrNull() ?: 0.0
                    val rateDouble = it.toDoubleOrNull() ?: 0.0
                    inputBdtDisbursed = Math.round(amountDouble * rateDouble).toString()
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
                isDueOnly = (inputAmountSar == "0" || inputAmountSar.trim() == "0.0"),
                isAdvanceReturn = isAdvanceReturn,
                selectedTimestamp = inputTimestamp,
                onSelectedTimestampChange = { inputTimestamp = it },
                isRateBasedModeEnabled = isRateBasedModeEnabled,
                isSupplierRateEnabled = isSupplierRateEnabled,
                onCancel = { isAddingTransaction = false },
                onSubmit = {
                    txActionToConfirm = "ADD_TX_PAGE"
                    txPinCodeInput = ""
                    txPinErrorText = null
                    showTxSecurityDialog = true
                }
            )
        }

        if (showAddTxChoiceDialog) {
            AlertDialog(
                onDismissRequest = { showAddTxChoiceDialog = false },
                title = {
                    Text(
                        text = if (lang == "BN") "লেনদেনের ধরণ নির্বাচন করুন" else "Select Transaction Type",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        val totalUncollectedSar = remember(transactions) {
                            (transactions.sumOf { it.amountSar } - transactions.sumOf { it.sarCollected })
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
                                        text = if (lang == "BN") "নতুন বিক্রয়" else "New Sale",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Text(
                                        text = if (lang == "BN") "নতুন কারেন্সি বিক্রয়। ২য় ধাপে বকেয়া আদায়ের অপশনও থাকবে।" else "Sell new currency. Collect old dues in Step 2.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                        }

                        if (totalUncollectedSar > 0.05) {
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
                                        inputNotes = if (lang == "BN") "বকেয়া আদায়" else "Due Payment"
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
                                            text = if (lang == "BN") "বকেয়া আদায়" else "Due Collection",
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

                        if (totalUncollectedSar <= -0.05) {
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
                                        inputNotes = if (lang == "BN") "পাওনা ফেরত" else "Advance Returned"
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
                                            text = if (lang == "BN") "পাওনা ফেরত" else "Advance Return",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                            color = Color(0xFF1565C0)
                                        )
                                        Text(
                                            text = if (lang == "BN") "কাস্টমারকে পাওনা ফেরত দিন (পাবে: ${foreignCur}${DecimalFormat("#.##").format(Math.abs(totalUncollectedSar))})।" else "Return customer's advanced balance (Owed: ${DecimalFormat("#.##").format(Math.abs(totalUncollectedSar))} ${foreignCur}).",
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
                        Text(if (lang == "BN") "বন্ধ করুন" else "Cancel")
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
                text = if (lang == "BN") "কাস্টমার প্রোফাইল" else "Customer Profile",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold, fontSize = 16.sp),
                color = MaterialTheme.colorScheme.onSurface
            )
            
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
                                        text = if (lang == "BN") "অবতার কাস্টমাইজ করুন:" else "Customize avatar:",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            if (!isEditing) {
                                Button(
                                    onClick = { 
                                        if (Math.abs(totalUncollectedSar) > 0.05) {
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
                                        text = if (lang == "BN") "নতুন লেনদেন" else "New Transaction",
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
                            (transactions.sumOf { it.amountSar } - transactions.sumOf { it.sarCollected })
                        }

                        Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (totalUncollectedSar > 0.05) Color(0xFFFFECEB) else if (totalUncollectedSar <= -0.05) Color(0xFFE3F2FD) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
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
                                    imageVector = if (totalUncollectedSar <= -0.05) Icons.Default.Info else Icons.Default.Warning,
                                    contentDescription = "",
                                    tint = if (totalUncollectedSar > 0.05) Color(0xFFC62828) else if (totalUncollectedSar <= -0.05) Color(0xFF1565C0) else MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = if (totalUncollectedSar <= -0.05) {
                                        if (lang == "BN") "কাস্টমার পাবে ${foreignCur}" else "Customer Owed ${foreignCur}"
                                    } else {
                                        if (lang == "BN") "মোট বকেয়া ${foreignCur}" else "Total Due ${foreignCur}"
                                    },
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (totalUncollectedSar > 0.05) Color(0xFFC62828) else if (totalUncollectedSar <= -0.05) Color(0xFF1565C0) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = "${DecimalFormat("#,##0.00").format(Math.abs(totalUncollectedSar))} ${foreignCur}",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.ExtraBold),
                                color = if (totalUncollectedSar > 0.05) Color(0xFFC62828) else if (totalUncollectedSar <= -0.05) Color(0xFF1565C0) else Color(0xFF2E7D32)
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
                            text = if (lang == "BN") "লেনদেন সমূহ" else "Transactions List",
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
                            text = if (lang == "BN") "কাস্টমার বিবরণী" else "Customer Info",
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
                            text = if (lang == "BN") "ব্যক্তিগত ও পরিচিতি বিবরণী" else "Contact & Privacy Details",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )

                        if (isEditing) {
                            OutlinedTextField(
                                value = editName,
                                onValueChange = { editName = it },
                                label = { Text(if (lang == "BN") "নাম" else "Full Name") },
                                leadingIcon = { Icon(Icons.Default.Person, contentDescription = "") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = editPhone,
                                onValueChange = { editPhone = it },
                                label = { Text(if (lang == "BN") "ফোন নাম্বার" else "Phone Number") },
                                leadingIcon = { Icon(Icons.Default.Phone, contentDescription = "") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = editAddress,
                                onValueChange = { editAddress = it },
                                label = { Text(if (lang == "BN") "ঠিকানা" else "Address") },
                                leadingIcon = { Icon(Icons.Default.Home, contentDescription = "") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = editNotes,
                                onValueChange = { editNotes = it },
                                label = { Text(if (lang == "BN") "প্রাইভেট সিকিউরিটি নোটস" else "Secure Security Notes") },
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
                                Text(if (lang == "BN") "কাস্টমার ডিলিট করুন" else "Delete Customer Profile")
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
                                        e.printStackTrace()
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
                                    text = if (lang == "BN") "মোট পাঠানো ভলিউম" else "Total Volume",
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
                                    text = if (lang == "BN") "মোট বিতরণ (টাকা)" else "Paid Out ${localCur}",
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
                        text = if (lang == "BN") "লেনদেন এবং হিসাব খাতা" else "Hundi Audit Logs",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = if (lang == "BN") "মোট লেনদেন: ${transactions.size} টি" else "Total Txs: ${transactions.size}",
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
                            text = if (lang == "BN") "কোনো লেনদেন পাওয়া যায়নি।" else "No transactions recorded for this customer.",
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
                                    val dailyBdt = txList.sumOf { it.amountBdt }
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
                                                
                                                val sarDue = tx.amountSar - tx.sarCollected
                                                val isDuePaymentTx = tx.receiverName == "Due Payment"
                                                val isAdvanceReturnTx = tx.receiverName == "Advance Return"
                                                
                                                if (!isDuePaymentTx && !isAdvanceReturnTx) {
                                                    if (sarDue > 0.05) {
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
                                                    } else if (sarDue <= -0.05) {
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
                                                                    text = if (lang == "BN") "কাস্টমার পাবে: ${foreignCur}${DecimalFormat("#.##").format(Math.abs(sarDue))}" else "Overpaid: ${DecimalFormat("#.##").format(Math.abs(sarDue))} ${foreignCur}",
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
                                                    if (tx.amountSar <= 0.05 && tx.sarCollected > 0.05) {
                                                        Text(text = "SAR ${tx.sarCollected}", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = Color(0xFF2E7D32))
                                                        Text(text = if (lang == "BN") "বকেয়া আদায়" else "Due Paid", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = Color(0xFF2E7D32))
                                                    } else if (tx.amountSar <= 0.05 && tx.sarCollected <= -0.05) {
                                                        Text(text = "SAR ${Math.abs(tx.sarCollected)}", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = Color(0xFF1565C0))
                                                        Text(text = if (lang == "BN") "রিয়াল ফেরত" else "Refunded", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = Color(0xFF1565C0))
                                                    } else {
                                                        Text(text = "SAR ${tx.amountSar}", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                                                        Text(text = "Rate: ${tx.customerRate}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
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
                                                
                                                val walletName = if (tx.amountSar <= 0.05) {
                                                    if (lang == "BN") "প্রযোজ্য নয় (বকেয়া আদায়)" else "N/A (Due Collected)"
                                                } else {
                                                    ledger?.name ?: "Unknown Wallet"
                                                }

                                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                    Text(text = if (lang == "BN") "ওয়ালেট লেজার:" else "Wallet/Pool Ledger:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)
                                                    Text(text = walletName, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                }
                                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                    Text(text = if (lang == "BN") "প্রাপক পাবে:" else "Payout Amount BDT:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)
                                                    Text(text = "৳ ${DecimalFormat("#,##0").format(tx.amountBdt)}", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color(0xFF1B5E20))
                                                }
                                                
                                                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 2.dp))
                                                
                                                val sarDue = tx.amountSar - tx.sarCollected
                                                val bdtDue = tx.amountBdt - tx.bdtDisbursed
                                                
                                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                    Column {
                                                        Text(text = if (lang == "BN") "রিয়াল গ্রহণ ${foreignCur}:" else "Riyal Collected ${foreignCur}:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)
                                                        if (tx.amountSar <= 0.05 && tx.sarCollected > 0.05) {
                                                            Text(text = "${tx.sarCollected} ${foreignCur}(${if (lang == "BN") "বকেয়া আদায় পরিশোধ" else "Outstanding Due Collected"})", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                                                        } else {
                                                            Text(text = "${tx.sarCollected} / ${tx.amountSar} ${foreignCur}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (sarDue <= 0.05) Color(0xFF2E7D32) else Color(0xFFE65100))
                                                        }
                                                    }
                                                }
                                                
                                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                    Column {
                                                        Text(text = if (lang == "BN") "বিতরণ ${localCur}:" else "Disbursed BDT:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)
                                                        Text(text = "৳ ${DecimalFormat("#,##0").format(tx.amountBdt)} ${localCur}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                                                    }
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
                                                                confirmDueCollectedSar = if (tx.amountSar == 0.0) tx.sarCollected else 0.0
                                                                confirmNewDueSar = tx.amountSar - tx.sarCollected
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
                                                            Text(if (lang == "BN") "শেয়ার" else "Share", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                        }

                                                        OutlinedButton(
                                                            onClick = {
                                                                txToEdit = tx
                                                                editAmountSar = tx.amountSar.toString()
                                                                editCustomerRate = tx.customerRate.toString()
                                                                editSupplierRate = tx.supplierRate.toString()
                                                                editReceiverName = tx.receiverName
                                                                editReceiverPhone = tx.receiverPhone
                                                                editReceiverAccountType = tx.receiverAccountType
                                                                editReceiverAccountNo = tx.receiverAccountNo
                                                                editTxNotes = tx.notes
                                                                editStatus = tx.status
                                                                editSupplierId = tx.supplierId
                                                                editSarCollected = tx.sarCollected.toString()
                                                                editBdtDisbursed = Math.round(tx.bdtDisbursed).toString()
                                                            },
                                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                            modifier = Modifier.height(28.dp),
                                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                                                        ) {
                                                            Icon(Icons.Default.Edit, contentDescription = "", modifier = Modifier.size(12.dp))
                                                            Spacer(modifier = Modifier.width(4.dp))
                                                            Text(if (lang == "BN") "এডিট" else "Edit", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                        }

                                                        OutlinedButton(
                                                            onClick = {
                                                                txToDelete = tx
                                                                txActionToConfirm = "DELETE"
                                                                txPinCodeInput = ""
                                                                txPinErrorText = null
                                                                showTxSecurityDialog = true
                                                            },
                                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                            modifier = Modifier.height(28.dp),
                                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                                        ) {
                                                            Icon(Icons.Default.Delete, contentDescription = "", modifier = Modifier.size(12.dp))
                                                            Spacer(modifier = Modifier.width(4.dp))
                                                            Text(if (lang == "BN") "ডিলিট" else "Delete", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                        }

                                                        if (tx.status != "Cancelled" && tx.status != "Delivered") {
                                                            Button(
                                                                onClick = {
                                                                    txToDelete = tx
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
                                                                    txToDelete = tx
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
                                                                    txToDelete = tx
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
                        text = if (lang == "BN") "প্রোফাইল পরিবর্তন সংরক্ষণ করতে আপনার ৪ সংখ্যার ওনার/অপেরাটর পাসকোড (PIN) লিখুন।" 
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
                                    val updatedCustomer = customer.copy(
                                        name = editName,
                                        phone = editPhone,
                                        address = editAddress,
                                        securityNotes = editNotes,
                                        avatarColor = editColor,
                                        avatarEmoji = editEmoji
                                    )
                                    onUpdate(updatedCustomer)
                                    showSecurityDialog = false
                                    isEditing = false
                                }
                            },
                            onError = { err ->
                                pinErrorText = err
                            }
                        )
                    } else {
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
                }
            },
            confirmButton = {
                if (currentOperator?.isBiometricEnabled != true) {
                    Button(
                        onClick = {
                            if (pinCodeInput == operatorPin) {
                                // Successfully Verified PIN! Proceed with database update
                                if (actionToConfirm == "DELETE") {
                                    onDelete()
                                    showSecurityDialog = false
                                } else {
                                    val updatedCustomer = customer.copy(
                                        name = editName,
                                        phone = editPhone,
                                        address = editAddress,
                                        securityNotes = editNotes,
                                        avatarColor = editColor,
                                        avatarEmoji = editEmoji
                                    )
                                    onUpdate(updatedCustomer)
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
                }
            },
            dismissButton = {
                TextButton(onClick = { showSecurityDialog = false }) {
                    Text(if (lang == "BN") "বাতিল" else "Cancel")
                }
            }
        )
    }

    if (isAmountCalCOpen) {
        CalculatorDialog(
            initialValue = inputAmountSar,
            title = if (lang == "BN") "রিয়াল পরিমাণ ${foreignCur}" else "Riyal Amount ${foreignCur}",
            lang = lang,
            onDismiss = { isAmountCalCOpen = false },
            onConfirm = { result ->
                inputAmountSar = result
                inputSarCollected = result
                val cr = inputCustomerRate.toDoubleOrNull() ?: 0.0
                val amt = result.toDoubleOrNull() ?: 0.0
                inputBdtDisbursed = Math.round(amt * cr).toString()
                isAmountCalCOpen = false
            }
        )
    }

    if (isEditAmountCalCOpen) {
        CalculatorDialog(
            initialValue = editAmountSar,
            title = if (lang == "BN") "রিয়াল পরিমাণ ${foreignCur}" else "Riyal Amount ${foreignCur}",
            lang = lang,
            onDismiss = { isEditAmountCalCOpen = false },
            onConfirm = { result ->
                editAmountSar = result
                val s = result.toDoubleOrNull() ?: 0.0
                val r = editCustomerRate.toDoubleOrNull() ?: 0.0
                editBdtDisbursed = Math.round(s * r).toString()
                isEditAmountCalCOpen = false
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
                        "EDIT" -> if (lang == "BN") "লেনদেনের তথ্য সংশোধন করতে আপনার ৪-ডিজিটের সিকিউরিটি পিন দিন।" else "Enter your 4-digit operator PIN to securely edit this transaction."
                        "STATUS_DELIVER" -> if (lang == "BN") "লেনদেনটি 'বিতরণ করা হয়েছে' করতে আপনার ৪-ডিজিটের সিকিউরিটি পিন দিন।" else "Enter your 4-digit operator PIN to mark this transaction as Delivered."
                        "STATUS_CANCEL" -> if (lang == "BN") "লেনদেনটি বাতিল করতে আপনার ৪-ডিজিটের সিকিউরিটি পিন দিন।" else "Enter your 4-digit operator PIN to mark this transaction as Cancelled."
                        "STATUS_PENDING" -> if (lang == "BN") "লেনদেনটি পেন্ডিং করতে আপনার ৪-ডিজিটের সিকিউরিটি পিন দিন।" else "Enter your 4-digit operator PIN to mark this transaction as Pending."
                        "ADD_TX_PAGE" -> if (lang == "BN") "নতুন লেনদেন প্রসেস করতে আপনার ৪-ডিজিটের পিন দিন।" else "Enter your 4-digit operator PIN to securely create this transaction."
                        else -> ""
                    }
                    Text(text = descText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    
                    if (currentOperator?.isBiometricEnabled == true) {
                        val onSuccessAction = {
                            when (txActionToConfirm) {
                                "ADD_TX_PAGE" -> {
                                    val amt = inputAmountSar.toDoubleOrNull() ?: 0.0
                                    val cRate = inputCustomerRate.toDoubleOrNull() ?: 32.10
                                    val batchId = selectedBatchId ?: 0
                                    val col = inputSarCollected.toDoubleOrNull() ?: amt
                                    val dis = inputBdtDisbursed.toDoubleOrNull() ?: (amt * cRate)
                                    val dueAmt = inputDueSarCollected.toDoubleOrNull() ?: 0.0
                                    val rcvStr = if (inputReceiverAccountType == "Cash") "Cash" else inputReceiverAccountNo

                                    confirmCustName = customer.name
                                    confirmAmountSar = amt
                                    confirmCustomerRate = cRate
                                    confirmCollectedSar = col
                                    confirmDueCollectedSar = dueAmt
                                    confirmNewDueSar = amt - col
                                    val dueAmtEffect = if (isAdvanceReturn || totalUncollectedSar <= -0.05) -dueAmt else dueAmt
                                    confirmTotalRemainingDueSar = totalUncollectedSar + confirmNewDueSar + dueAmtEffect
                                    confirmPaymentMethod = inputReceiverAccountType
                                    confirmRecipientNo = rcvStr
                                    confirmTimestamp = inputTimestamp
                                    confirmIsAdvanceReturn = isAdvanceReturn || (totalUncollectedSar <= -0.05 && dueAmt > 0.05)

                                    if (amt > 0.05 && batchId > 0 && rcvStr.isNotBlank()) {
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
                                            if (dueAmt > 0.05) {
                                                viewModel.createRemittance(
                                                    customerId = customer.id,
                                                    walletBatchId = 0,
                                                    amountSar = 0.0,
                                                    customerRate = 0.0,
                                                    receiverName = if (isAdvanceReturn) "Advance Return" else "Due Payment",
                                                    receiverPhone = "N/A",
                                                    receiverAccountType = "N/A",
                                                    receiverAccountNo = "N/A",
                                                    notes = if (isAdvanceReturn) (if (lang == "BN") "পাওনা ফেরত" else "Advance Returned") else (if (lang == "BN") "পূর্বের বকেয়া আদায় / পরিশোধ" else "Previous Due Payment / Recovery"),
                                                    sarCollected = dueAmtEffect,
                                                    bdtDisbursed = 0.0,
                                                    status = "Delivered",
                                                    timestamp = inputTimestamp
                                                ) {
                                                    // Reset states
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
                                                // Reset states
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
                                    } else if (amt <= 0.05 && dueAmt > 0.05) {
                                        viewModel.createRemittance(
                                            customerId = customer.id,
                                            walletBatchId = 0,
                                            amountSar = 0.0,
                                            customerRate = 0.0,
                                            receiverName = if (isAdvanceReturn) "Advance Return" else "Due Payment",
                                            receiverPhone = "N/A",
                                            receiverAccountType = "N/A",
                                            receiverAccountNo = "N/A",
                                            notes = if (isAdvanceReturn) (if (lang == "BN") "পাওনা ফেরত" else "Advance Returned") else (if (lang == "BN") "পূর্বের বকেয়া আদায় / পরিশোধ" else "Previous Due Payment / Recovery"),
                                            sarCollected = dueAmtEffect,
                                            bdtDisbursed = 0.0,
                                            status = "Delivered",
                                            timestamp = inputTimestamp
                                        ) {
                                            // Reset states
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
                                    showTxSecurityDialog = false
                                }
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
                                             receiverPhone = editReceiverPhone,
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
                                     if (txToDelete != null) {
                                         viewModel.updateTransactionStatus(txToDelete!!, "Delivered")
                                     }
                                     txToDelete = null
                                     showTxSecurityDialog = false
                                 }
                                 "STATUS_CANCEL" -> {
                                     if (txToDelete != null) {
                                         viewModel.updateTransactionStatus(txToDelete!!, "Cancelled")
                                     }
                                     txToDelete = null
                                     showTxSecurityDialog = false
                                 }
                                 "STATUS_PENDING" -> {
                                     if (txToDelete != null) {
                                         viewModel.updateTransactionStatus(txToDelete!!, "Pending")
                                     }
                                     txToDelete = null
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

                    Spacer(modifier = Modifier.height(4.dp))

                    OutlinedTextField(
                        value = txPinCodeInput,
                        onValueChange = { if (it.length <= 4) txPinCodeInput = it },
                        label = { Text(if (lang == "BN") "৪-ডিজিটের সিকিউরিটি পিন" else "4-digit Security PIN") },
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        isError = txPinErrorText != null
                    )
                    
                    if (txPinErrorText != null) {
                        Text(text = txPinErrorText ?: "", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (txPinCodeInput == operatorPin) {
                            when (txActionToConfirm) {
                                "ADD_TX_PAGE" -> {
                                    val amt = inputAmountSar.toDoubleOrNull() ?: 0.0
                                    val cRate = inputCustomerRate.toDoubleOrNull() ?: 32.10
                                    val batchId = selectedBatchId ?: 0
                                    val col = inputSarCollected.toDoubleOrNull() ?: amt
                                    val dis = inputBdtDisbursed.toDoubleOrNull() ?: (amt * cRate)
                                    val dueAmt = inputDueSarCollected.toDoubleOrNull() ?: 0.0
                                    val rcvStr = if (inputReceiverAccountType == "Cash") "Cash" else inputReceiverAccountNo

                                    confirmCustName = customer.name
                                    confirmAmountSar = amt
                                    confirmCustomerRate = cRate
                                    confirmCollectedSar = col
                                    confirmDueCollectedSar = dueAmt
                                    confirmNewDueSar = amt - col
                                    val dueAmtEffect = if (isAdvanceReturn || totalUncollectedSar <= -0.05) -dueAmt else dueAmt
                                    confirmTotalRemainingDueSar = totalUncollectedSar + confirmNewDueSar + dueAmtEffect
                                    confirmPaymentMethod = inputReceiverAccountType
                                    confirmRecipientNo = rcvStr
                                    confirmTimestamp = inputTimestamp
                                    confirmIsAdvanceReturn = isAdvanceReturn || (totalUncollectedSar <= -0.05 && dueAmt > 0.05)

                                    if (amt > 0.05 && batchId > 0 && rcvStr.isNotBlank()) {
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
                                            if (dueAmt > 0.05) {
                                                viewModel.createRemittance(
                                                    customerId = customer.id,
                                                    walletBatchId = 0,
                                                    amountSar = 0.0,
                                                    customerRate = 0.0,
                                                    receiverName = if (isAdvanceReturn) "Advance Return" else "Due Payment",
                                                    receiverPhone = "N/A",
                                                    receiverAccountType = "N/A",
                                                    receiverAccountNo = "N/A",
                                                    notes = if (isAdvanceReturn) (if (lang == "BN") "পাওনা ফেরত" else "Advance Returned") else (if (lang == "BN") "পূর্বের বকেয়া আদায় / পরিশোধ" else "Previous Due Payment / Recovery"),
                                                    sarCollected = dueAmtEffect,
                                                    bdtDisbursed = 0.0,
                                                    status = "Delivered",
                                                    timestamp = inputTimestamp
                                                ) {
                                                    // Reset states
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
                                                // Reset states
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
                                    } else if (amt <= 0.05 && dueAmt > 0.05) {
                                        viewModel.createRemittance(
                                            customerId = customer.id,
                                            walletBatchId = 0,
                                            amountSar = 0.0,
                                            customerRate = 0.0,
                                            receiverName = if (isAdvanceReturn) "Advance Return" else "Due Payment",
                                            receiverPhone = "N/A",
                                            receiverAccountType = "N/A",
                                            receiverAccountNo = "N/A",
                                            notes = if (isAdvanceReturn) (if (lang == "BN") "পাওনা ফেরত" else "Advance Returned") else (if (lang == "BN") "পূর্বের বকেয়া আদায় / পরিশোধ" else "Previous Due Payment / Recovery"),
                                            sarCollected = dueAmtEffect,
                                            bdtDisbursed = 0.0,
                                            status = "Delivered",
                                            timestamp = inputTimestamp
                                        ) {
                                            // Reset states
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
                                    showTxSecurityDialog = false
                                }
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
                                              receiverPhone = editReceiverPhone,
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
                                      if (txToDelete != null) {
                                          viewModel.updateTransactionStatus(txToDelete!!, "Delivered")
                                      }
                                      txToDelete = null
                                      showTxSecurityDialog = false
                                  }
                                  "STATUS_CANCEL" -> {
                                      if (txToDelete != null) {
                                          viewModel.updateTransactionStatus(txToDelete!!, "Cancelled")
                                      }
                                      txToDelete = null
                                      showTxSecurityDialog = false
                                  }
                                  "STATUS_PENDING" -> {
                                      if (txToDelete != null) {
                                          viewModel.updateTransactionStatus(txToDelete!!, "Pending")
                                      }
                                      txToDelete = null
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
    previousDueSar: Double,
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
                                    text = if (lang == "BN") "পরিমাণ ${foreignCur}" else "Amount ${foreignCur}",
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
                                label = { Text(if (lang == "BN") "অতিরিক্ত মন্তব্য (নোট)" else "Notes / Description") },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = "") },
                                placeholder = { Text(if (lang == "BN") "মন্তব্য লিখুন..." else "Enter comments...") },
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
                                                text = if (lang == "BN") "ডকুমেন্ট আপলোড" else "Upload Document",
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            )
                                            Text(
                                                text = selectedDocumentName ?: (if (lang == "BN") "রিসিট বা কোনো ছবি যুক্ত করুন..." else "No attachment file selected"),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (selectedDocumentName != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
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
                val isDueOnly = (amountSar == "0" || amountSar.trim() == "0.0" || amountSar.isBlank())

                // 1. PAYMENT METHOD
                item {
                    val mContext = androidx.compose.ui.platform.LocalContext.current
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (lang == "BN") "পেমেন্ট মেথড নির্বাচন করুন" else "Select Payout Method",
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
                                        "Bank Transfer" -> if (lang == "BN") "ব্যাংক হিসাব নম্বর" else "Bank Account Number"
                                        else -> if (lang == "BN") "$paymentMethod নম্বর (মোবাইল)" else "$paymentMethod Number (Mobile)"
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
                                text = if (lang == "BN") "ওয়ালেট নির্বাচন করুন" else "Select Wallet Account",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            
                            val groupedActiveBatches = walletBatches
                                .filter { it.remainingBdt > 0.05 }
                                .groupBy { Pair(it.ledgerId, it.rate) }
                            val activeBatches = groupedActiveBatches.map { (_, list) -> 
                                list.first().copy(remainingBdt = list.sumOf { it.remainingBdt }) 
                            }
                            if (activeBatches.isEmpty()) {
                                Text(
                                    text = if (lang == "BN") "কোনো সক্রিয় ওয়ালেট স্টক পাওয়া যায়নি!" else "No active wallet ledger stock available!",
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
                                                    ?: (if (lang == "BN") "ওয়ালেট লেজার সিলেক্ট করুন ▾" else "Select Wallet Ledger Account ▾"),
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
                                                    text = if (lang == "BN") "ওয়ালেট লেজার সিলেক্ট করুন" else "Select Wallet Ledger Account",
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
                                                    Text(if (lang == "BN") "বন্ধ করুন" else "Cancel")
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
                                                    text = if (lang == "BN") "ওয়ালেট ক্রয় রেট (অটো):" else "Wallet Cost Rate (Auto):",
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
                if (previousDueSar > 0.05 && !isAdvanceReturn) {
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
                                        text = if (lang == "BN") "পূর্বের বকেয়া আদায় / পরিশোধ" else "Pay Previous Outstanding Dues",
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
                                    text = if (lang == "BN") 
                                        "কাস্টমারের পূর্বের বকেয়া রিয়াল থেকে আজ কত রিয়াল জমা নেওয়া হচ্ছে তা এখানে লিখুন।" 
                                        else "Enter how many Riyals of previous outstanding dues are being collected/settled today.",
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
                                        label = { Text(if (lang == "BN") "বকেয়া আদায় পরিমাণ ${foreignCur}" else "Due Collected Amount ${foreignCur}") },
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
                if (previousDueSar <= -0.05 && (!isDueOnly || isAdvanceReturn)) {
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
                                        text = if (lang == "BN") "পাওনা ফেরত" else "Return Advanced Balance",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = Color(0xFF1565C0)
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    ) {
                                        Text(
                                            text = "${sarFormatter.format(Math.abs(previousDueSar))} ${foreignCur}Owed",
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                        )
                                    }
                                }

                                Text(
                                    text = if (lang == "BN") 
                                        "কাস্টমারকে কত রিয়াল ফেরত দিচ্ছেন তা নিচে লিখুন।" 
                                        else "Enter how many Riyals of advance balance are being returned.",
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
                                        label = { Text(if (lang == "BN") "ফেরত পরিমাণ ${foreignCur}" else "Return Amount ${foreignCur}") },
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
                        val amtSarVal = amountSar.toDoubleOrNull() ?: 0.0
                        val isAmtPresent = amtSarVal > 0.05
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
                                    text = if (lang == "BN") "বিক্রয় মূল্য ও রিয়াল হিসাব" else "Sales Price & Riyal Summary",
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
                                            label = { Text(if (lang == "BN") "বিক্রয় রেট ${localCur}" else "Selling Rate ${localCur}") },
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
                                        label = { Text(if (lang == "BN") "পরিমাণ ${foreignCur}" else "Amount ${foreignCur}") },
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
                                        label = { Text(if (lang == "BN") "গ্রহণ ${foreignCur}" else "Received ${foreignCur}") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.weight(1f)
                                    )

                                    // ${localCur}field (Disabled - Read-only calculated total, as requested! And rounded integers only)
                                    OutlinedTextField(
                                        value = bdtDisbursed,
                                        onValueChange = { },
                                        enabled = false, // Disabled/read-only as requested!
                                        label = { Text(if (lang == "BN") "বিতরণ ${localCur}" else "Disbursed ${localCur}") },
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
                    val sarVal = amountSar.toDoubleOrNull() ?: 0.0
                    val custRateVal = customerRate.toDoubleOrNull() ?: 0.0
                    val bdtTotal = Math.round(sarVal * custRateVal) // Whole ${localCur}amount
                    
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Total Payout ${localCur}Card
                        if (sarVal > 0.05) {
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
                                        text = if (lang == "BN") "প্রাপক পাবে:" else "Total Beneficiary Payout:",
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
                                    text = if (lang == "BN") "রিভিউ ও সাবমিট" else "Review Details",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(text = if (lang == "BN") "রিয়াল পরিমাণ:" else "SAR Amount:", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                                    Text(text = "$amountSar ${foreignCur}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                val amtVal = amountSar.toDoubleOrNull() ?: 0.0
                                val collectedVal = sarCollected.toDoubleOrNull() ?: 0.0
                                val newDueSar = amtVal - collectedVal
                                if (newDueSar > 0.01) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(text = if (lang == "BN") "নতুন বকেয়া ${foreignCur}:" else "New Outstanding Due ${foreignCur}:", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                                        Text(text = "${String.format("%.2f", newDueSar)} ${foreignCur}", fontSize = 12.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.error)
                                    }
                                }
                                if (previousDueSar > 0.05) {
                                    val dueAmtVal = dueSarCollected.toDoubleOrNull() ?: 0.0
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(text = if (lang == "BN") "বকেয়া আদায় ${foreignCur}:" else "Due Collected ${foreignCur}:", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                                        Text(text = "$dueAmtVal ${foreignCur}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                                    }
                                } else if (previousDueSar <= -0.05) {
                                    val dueAmtVal = dueSarCollected.toDoubleOrNull() ?: 0.0
                                    if (dueAmtVal > 0.0) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text(text = if (lang == "BN") "পাওনা ফেরত ${foreignCur}:" else "Advance Returned ${foreignCur}:", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                                            Text(text = "$dueAmtVal ${foreignCur}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                                        }
                                    }
                                }
                                if (paymentMethod != "Cash") {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(text = if (lang == "BN") "হিসাব নম্বর:" else "Account Number:", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                                        val displayAccountNo = if (recipientNo == "N/A" || recipientNo.isBlank()) "" else recipientNo
                                        Text(text = displayAccountNo, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(text = if (lang == "BN") "পেমেন্ট মেথড:" else "Payout Channel:", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                                    Text(text = paymentMethod, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                }
                                if (!isDueOnly) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(text = if (lang == "BN") "বিক্রয় রেট (৳):" else "Customer Rate:", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
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
                val amtDouble = amountSar.toDoubleOrNull() ?: 0.0
                val isDueOnly = (amountSar == "0" || amountSar.trim() == "0.0" || amountSar.isBlank()) && previousDueSar > 0.05
                val isAdvanceAct = (amountSar == "0" || amountSar.trim() == "0.0" || amountSar.isBlank()) && previousDueSar <= -0.05 && isAdvanceReturn
                val isRegularEligible = amtDouble > 0.05
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
                        text = if (lang == "BN") "পরের ধাপ" else "Next Step",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(Icons.Default.ArrowForward, contentDescription = "", modifier = Modifier.size(16.dp))
                }
            } else {
                val amtDoubleVal = amountSar.toDoubleOrNull() ?: 0.0
                val dueAmtDoubleVal = dueSarCollected.toDoubleOrNull() ?: 0.0
                val isNumberValid = (paymentMethod == "Cash") || recipientNo.isNotBlank()
                val isAdvanceAct = (amountSar == "0" || amountSar.trim() == "0.0" || amountSar.isBlank()) && previousDueSar <= -0.05 && isAdvanceReturn
                val isSubmitEnabled = if (isDueOnly || isAdvanceAct) {
                    dueAmtDoubleVal > 0.0 && isNumberValid
                } else {
                    selectedBatchId != null && amtDoubleVal > 0.0 && isNumberValid
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
                        text = if (lang == "BN") "লেনদেন সম্পন্ন করুন" else "Create Transaction",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }

    if (isDueCalcOpen) {
        CalculatorDialog(
            initialValue = dueSarCollected,
            title = if (lang == "BN") "বকেয়া আদায় পরিমাণ ${foreignCur}" else "Due Collected Amount ${foreignCur}",
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
                    text = if (lang == "BN") "লেনদেন সংশোধন" else "Modify Transaction Ledger",
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
                                text = if (lang == "BN") "লেনদেন হিসাব ও রেট" else "Exchange Rates & Amount",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        // ${foreignCur}Amount Input (With Calculator inline)
                        OutlinedTextField(
                            value = editAmountSar,
                            onValueChange = { onEditAmountSarChange(it) },
                            readOnly = true,
                            label = { Text(if (lang == "BN") "রিয়াল পরিমাণ ${foreignCur}" else "Riyal Amount ${foreignCur}") },
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
                                label = { Text(if (lang == "BN") "গ্রাহক রেট" else "Customer Rate") },
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
                                label = { Text(if (lang == "BN") "রিয়াল সংগ্রহ ${foreignCur}" else "Riyal Received") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = editBdtDisbursed,
                                onValueChange = { onEditBdtDisbursedChange(it) },
                                label = { Text(if (lang == "BN") "টাকা পাঠানো ${localCur}" else "BDT Disbursed") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Calculated Dynamic Summary Output (To help user understand)
                        val totalBdtDoubleVal = (editAmountSar.toDoubleOrNull() ?: 0.0) * (editCustomerRate.toDoubleOrNull() ?: 0.0)
                        if (totalBdtDoubleVal > 0.0) {
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
                                        text = if (lang == "BN") "প্রাপক পাবে (মোট):" else "Recipient Output (Total):",
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                        color = Color(0xFF1B5E20)
                                    )
                                    Text(
                                        text = "৳ ${decimalFormatter.format(totalBdtDoubleVal)}",
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
                                text = if (lang == "BN") "প্রাপকের বিবরণ" else "Receiver Details",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        OutlinedTextField(
                            value = editReceiverName,
                            onValueChange = { onEditReceiverNameChange(it) },
                            label = { Text(if (lang == "BN") "প্রাপকের নাম" else "Receiver Name") },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = editReceiverPhone,
                            onValueChange = { onEditReceiverPhoneChange(it) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            label = { Text(if (lang == "BN") "প্রাপকের ফোন" else "Receiver Phone") },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Payment Channel selection
                        Text(
                            text = if (lang == "BN") "পেমেন্ট চ্যানেল" else "Payout Provider Channel",
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
                            label = { Text(if (lang == "BN") "হিসাব / ওয়ালেট নম্বর" else "Account Number") },
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
                                text = if (lang == "BN") "স্ট্যাটাস ও মন্তব্য" else "Workflow Status & Remarks",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        // Notes textfield
                        OutlinedTextField(
                            value = editTxNotes,
                            onValueChange = { onEditTxNotesChange(it) },
                            label = { Text(if (lang == "BN") "অতিরিক্ত মন্তব্য" else "Notes / Description") },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Status Flow Check
                        Text(
                            text = if (lang == "BN") "লেনদেন অবস্থা পরিবর্তন করবেন?" else "Alter Transaction Status State?",
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
                text = if (lang == "BN") "পরিবর্তনসমূহ সংরক্ষণ করুন" else "Save Transaction Updates",
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
    amountSar: Double,
    customerRate: Double,
    dueCollectedSar: Double,
    newDueSar: Double,
    totalRemainingDueSar: Double,
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
        canvas.drawText(if (lang == "BN") "লেনদেনের ডিজিটাল রসিদ" else "Digital Transaction Receipt", 40f, 95f, paint)
        
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
        
        drawRow(if (lang == "BN") "তারিখ ও সময়:" else "Date & Time:", sdf.format(Date(timestamp)))
        drawRow(if (lang == "BN") "গ্রাহকের নাম:" else "Customer Name:", customerName)
        
        val calculatedTotalBdt = Math.round(amountSar * customerRate)
        if (amountSar > 0.05) {
            drawRow(if (lang == "BN") "নতুন লেনদেন ${foreignCur}:" else "New Remittance ${foreignCur}:", "$amountSar ${foreignCur}")
            drawRow(if (lang == "BN") "বিনিময় হার:" else "Conversion Rate:", "$customerRate BDT/SAR")
            drawRow(if (lang == "BN") "মোট প্রদান মূল্য ${localCur}:" else "Total Amount ${localCur}:", "BDT $calculatedTotalBdt")
        }
        
        drawRow(if (lang == "BN") "পেমেন্ট মেথড:" else "Payment Method:", paymentMethod)
        if (paymentMethod != "Cash" && recipientNo.isNotBlank() && recipientNo != "N/A") {
            drawRow(if (lang == "BN") "হিসাব নম্বর:" else "Account Number:", recipientNo)
        }
        
        if (dueCollectedSar > 0.05) {
            drawRow(if (isAdvanceReturn) (if (lang == "BN") "পাওনা ফেরত:" else "Advance Returned:") else (if (lang == "BN") "পূর্বের বকেয়া পরিশোধ:" else "Previous Due Paid:"), "$dueCollectedSar ${foreignCur}")
        }
        
        // Horizontal grey divider line
        paint.color = android.graphics.Color.LTGRAY
        canvas.drawLine(40f, currentY, 555f, currentY, paint)
        currentY += 30f
        
        if (newDueSar > 0.01) {
            drawRow(if (lang == "BN") "এই রসিদের বকেয়া:" else "Due on this receipt:", "$newDueSar ${foreignCur}")
        } else if (newDueSar < -0.01) {
            drawRow(if (lang == "BN") "আপনার জমা ব্যালেন্স:" else "Customer Surplus Credit:", "${-newDueSar} ${foreignCur}")
        }
        
        if (totalRemainingDueSar > 0.05) {
            drawRow(if (lang == "BN") "সর্বমোট বকেয়া:" else "Total Outstanding Due:", "$totalRemainingDueSar ${foreignCur}")
        } else if (totalRemainingDueSar < -0.05) {
            drawRow(if (lang == "BN") "কাস্টমার অতিরিক্ত পাবেন:" else "Customer refund credit:", "${-totalRemainingDueSar} ${foreignCur}")
        } else {
            drawRow(if (lang == "BN") "সর্বমোট বকেয়া:" else "Total Remaining Dues:", if (lang == "BN") "কোনো বকেয়া নেই" else "Zero Outstanding Dues")
        }
        
        // Bottom privacy note and divider
        canvas.drawLine(40f, currentY + 15f, 555f, currentY + 15f, paint)
        currentY += 45f
        
        paint.color = android.graphics.Color.GRAY
        paint.textSize = 12f
        canvas.drawText(
            if (lang == "BN") "উক্ত রসিদটি একটি বিশ্বস্ত ডিজিটাল সিস্টেম দ্বারা স্বয়ংক্রিয়ভাবে জেনারেট করা হয়েছে।" else "Receipt automatically compiled using secure ledger algorithms.",
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
        e.printStackTrace()
        return null
    }
}

// Helper to generate a genuine PNG image card of the receipt
fun generateImageReceipt(
    context: android.content.Context,
    customerName: String,
    amountSar: Double,
    customerRate: Double,
    dueCollectedSar: Double,
    newDueSar: Double,
    totalRemainingDueSar: Double,
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
        drawVisualRow(if (lang == "BN") "তারিখ ও সময়:" else "Date & Time:", sdf.format(Date(timestamp)))
        drawVisualRow(if (lang == "BN") "গ্রাহকের নাম:" else "Customer Name:", customerName)
        
        val calculatedTotalBdt = Math.round(amountSar * customerRate)
        if (amountSar > 0.05) {
            drawVisualRow(if (lang == "BN") "নতুন লেনদেন ${foreignCur}:" else "New Remittance ${foreignCur}:", "$amountSar ${foreignCur}")
            drawVisualRow(if (lang == "BN") "রেট:" else "Ex. Rate:", "$customerRate BDT/SAR")
            drawVisualRow(if (lang == "BN") "জমা মূল্য ${localCur}:" else "Total Amount ${localCur}:", "BDT $calculatedTotalBdt", highlight = true)
        }
        
        drawVisualRow(if (lang == "BN") "পেমেন্ট মেথড:" else "Payment Method:", paymentMethod)
        if (paymentMethod != "Cash" && recipientNo.isNotBlank() && recipientNo != "N/A") {
            drawVisualRow(if (lang == "BN") "হিসাব নম্বর:" else "Account No:", recipientNo)
        }
        
        if (dueCollectedSar > 0.05) {
            drawVisualRow(if (isAdvanceReturn) (if (lang == "BN") "পাওনা ফেরত:" else "Advance Returned:") else (if (lang == "BN") "পূর্বের বকেয়া পরিশোধ:" else "Previous Due Paid:"), "$dueCollectedSar ${foreignCur}")
        }
        
        // Solid divider line
        paint.color = 0xFFE2E8F0.toInt()
        paint.strokeWidth = 1.5f
        canvas.drawLine(55f, currentY + 10f, width - 55f, currentY + 10f, paint)
        currentY += 45f
        
        if (newDueSar > 0.01) {
            drawVisualRow(if (lang == "BN") "এই রসিদের বকেয়া:" else "Due on this receipt:", "$newDueSar ${foreignCur}")
        } else if (newDueSar < -0.01) {
            drawVisualRow(if (lang == "BN") "আপনার জমা ব্যালেন্স:" else "Surplus Balance:", "${-newDueSar} ${foreignCur}")
        }
        
        if (totalRemainingDueSar > 0.05) {
            drawVisualRow(if (lang == "BN") "সর্বমোট বকেয়া:" else "Total Outstanding Due:", "$totalRemainingDueSar ${foreignCur}")
        } else if (totalRemainingDueSar < -0.05) {
            drawVisualRow(if (lang == "BN") "কাস্টমার অতিরিক্ত পাবেন:" else "Customer due refund:", "${-totalRemainingDueSar} ${foreignCur}")
        } else {
            drawVisualRow(if (lang == "BN") "সর্বমোট বকেয়া:" else "Total Remaining Dues:", if (lang == "BN") "কোনো বকেয়া নেই" else "Zero Outstanding Dues")
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
            if (lang == "BN") "নির্ভুল ও বিশ্বস্ত লেনদেন খতিয়ান।" else "Compiled securely in client database.",
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
        e.printStackTrace()
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
    amountSar: Double,
    customerRate: Double,
    collectedSar: Double,
    dueCollectedSar: Double,
    newDueSar: Double,
    totalRemainingDueSar: Double,
    paymentMethod: String,
    recipientNo: String,
    timestamp: Long,
    isAdvanceReturn: Boolean = false,
    foreignCur: String = "SAR",
    localCur: String = "BDT",
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val calculatedTotalBdt = Math.round(amountSar * customerRate)
    
    val shareTextStr = remember(customerName, amountSar, newDueSar, dueCollectedSar, totalRemainingDueSar, lang, paymentMethod, recipientNo, timestamp) {
        val sb = java.lang.StringBuilder()
        val sdf = SimpleDateFormat("dd-MM-yyyy hh:mm a", Locale.getDefault())
        val formattedDate = sdf.format(Date(timestamp))
        if (lang == "BN") {
            sb.append("📢 SAFA Money Transfer\n")
            sb.append("=== লেনদেনের রসিদ ===\n\n")
            sb.append("তারিখ: $formattedDate\n")
            sb.append("গ্রাহক: $customerName\n")
            if (amountSar > 0.05) {
                sb.append("নতুন যুক্ত লেনদেন: $amountSar SAR\n")
                sb.append("বিনিময় হার: ৳ $customerRate\n")
                sb.append("বাংলাদেশ প্রদান মূল্য: ৳ $calculatedTotalBdt BDT\n")
            }
            sb.append("পেমেন্ট মেথড: $paymentMethod\n")
            if (paymentMethod != "Cash" && recipientNo.isNotBlank() && recipientNo != "N/A") {
                sb.append("হিসাব নম্বর: $recipientNo\n")
            }
            if (dueCollectedSar > 0.05) {
                sb.append(if (isAdvanceReturn) (if (lang == "BN") "পাওনা ফেরত: " else "Advance Return Paid: ") else (if (lang == "BN") "পূর্বের বকেয়া পরিশোধ: " else "Previous Due Paid: ")).append("$dueCollectedSar SAR\n")
            }
            sb.append("---------------------\n")
            if (newDueSar > 0.01) {
                sb.append("নতুন বকেয়া: $newDueSar SAR\n")
            } else if (newDueSar < -0.01) {
                sb.append("কাস্টমার অতিরিক্ত পাবেন: ${-newDueSar} SAR\n")
            }
            if (totalRemainingDueSar > 0.05) {
                sb.append("সর্বমোট বকেয়া: $totalRemainingDueSar SAR\n")
            } else if (totalRemainingDueSar < -0.05) {
                sb.append("কাস্টমার অতিরিক্ত পাবেন (সর্বমোট): ${-totalRemainingDueSar} SAR\n")
            } else {
                sb.append("গ্রাহকের আর কোনো বকেয়া নেই।\n")
            }
        } else {
            sb.append("📢 SAFA Money Transfer\n")
            sb.append("=== Transaction Receipt ===\n\n")
            sb.append("Date: $formattedDate\n")
            sb.append("Customer: $customerName\n")
            if (amountSar > 0.05) {
                sb.append("New Remittance: $amountSar SAR\n")
                sb.append("Rate: ৳ $customerRate\n")
                sb.append("Disbursed Total: ${localCur}$calculatedTotalBdt\n")
            }
            sb.append("Payment Method: $paymentMethod\n")
            if (paymentMethod != "Cash" && recipientNo.isNotBlank() && recipientNo != "N/A") {
                sb.append("Account No: $recipientNo\n")
            }
            if (dueCollectedSar > 0.05) {
                sb.append(if (isAdvanceReturn) "Advance Return Paid: " else "Previous Due Paid: ").append("$dueCollectedSar SAR\n")
            }
            sb.append("---------------------\n")
            if (newDueSar > 0.01) {
                sb.append("New Outstanding Due: $newDueSar SAR\n")
            } else if (newDueSar < -0.01) {
                sb.append("Customer Surplus Credit: ${-newDueSar} SAR\n")
            }
            if (totalRemainingDueSar > 0.05) {
                sb.append("Total Outstandings: $totalRemainingDueSar SAR\n")
            } else if (totalRemainingDueSar < -0.05) {
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
        val shareIntent = android.content.Intent.createChooser(sendIntent, if (lang == "BN") "রসিদ শেয়ার করুন" else "Share Receipt")
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
                    text = if (lang == "BN") "লেনদেন নিশ্চিতকরণ" else "Transaction Complete",
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
                    text = if (lang == "BN") "লেনদেন সফলভাবে সম্পন্ন হয়েছে!" else "Transaction Added!",
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
                        Text(text = if (lang == "BN") "তারিখ ও সময়:" else "Date & Time:", style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.outline))
                        Text(text = sdf.format(Date(timestamp)), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = if (lang == "BN") "গ্রাহকের নাম:" else "Customer Name:", style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.outline))
                        Text(text = customerName, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                    }
                    if (amountSar > 0.05) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = if (lang == "BN") "নতুন লেনদেন ${foreignCur}:" else "New Remittance ${foreignCur}:", style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.outline))
                            Text(text = "$amountSar ${foreignCur}", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = if (lang == "BN") "বিতরণ মূল্য ${localCur}:" else "Disbursed ${localCur}:", style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.outline))
                            Text(text = "৳ $calculatedTotalBdt ${localCur}", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary))
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = if (lang == "BN") "পেমেন্ট মেথড:" else "Payment Method:", style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.outline))
                            Text(text = paymentMethod, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                        }
                        if (paymentMethod != "Cash" && recipientNo.isNotBlank() && recipientNo != "N/A") {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(text = if (lang == "BN") "হিসাব নম্বর:" else "Account Number:", style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.outline))
                                Text(text = recipientNo, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                            }
                        }
                    }
                    if (dueCollectedSar > 0.05) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = if (isAdvanceReturn) (if (lang == "BN") "পাওনা ফেরত:" else "Advance Returned:") else (if (lang == "BN") "পূর্বের বকেয়া পরিশোধ:" else "Previous Due Paid:"), style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.outline))
                            Text(text = "$dueCollectedSar ${foreignCur}", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32)))
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    
                    if (newDueSar > 0.01) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = if (lang == "BN") "এই রসিদের বকেয়া:" else "Due on this receipt:", style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.outline))
                            Text(text = "$newDueSar ${foreignCur}", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error))
                        }
                    } else if (newDueSar < -0.01) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = if (lang == "BN") "আপনার জমা ব্যালেন্স (সারপ্লাস):" else "Your Surplus Balance:", style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.outline))
                            Text(text = "${-newDueSar} ${foreignCur}", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32)))
                        }
                    }
                    
                    if (totalRemainingDueSar > 0.05) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = if (lang == "BN") "গ্রাহকের মোট বকেয়া:" else "Customer Total Due:", style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.outline))
                            Text(text = "$totalRemainingDueSar ${foreignCur}", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.error))
                        }
                    } else if (totalRemainingDueSar < -0.05) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = if (lang == "BN") "কাস্টমার অতিরিক্ত পাবেন:" else "Customer Credit Due:", style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.outline))
                            Text(text = "${-totalRemainingDueSar} ${foreignCur}", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Black, color = Color(0xFF2E7D32)))
                        }
                    } else {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = if (lang == "BN") "সর্বমোট বকেয়া:" else "Total Remaining Dues:", style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.outline))
                            Text(text = if (lang == "BN") "কোনো বকেয়া নেই" else "No outstanding dues", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32)))
                        }
                    }
                }
            }

            Text(
                text = if (lang == "BN") "সাপ্লায়ার এবং ক্রয়ের গোপনীয় তথ্য এই রসিদে সংরক্ষণ করা হয়নি।" else "Supplier details and buying rates are omitted for privacy.",
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
                        text = if (lang == "BN") "টেক্সট" else "Text",
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
                            shareNativeFile(context, pdfUri, "application/pdf", if (lang == "BN") "PDF রসিদ শেয়ার করুন" else "Share PDF Receipt")
                        } else {
                            Toast.makeText(context, if (lang == "BN") "রসিদ তৈরি ব্যর্থ হয়েছে" else "Failed to compile PDF document", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.weight(1f).height(46.dp),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    Icon(imageVector = Icons.Default.Print, contentDescription = "", modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (lang == "BN") "পিডিএফ" else "PDF",
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
                            shareNativeFile(context, imgUri, "image/png", if (lang == "BN") "ছবি রসিদ শেয়ার করুন" else "Share Image Receipt")
                        } else {
                            Toast.makeText(context, if (lang == "BN") "রসিদ তৈরি ব্যর্থ হয়েছে" else "Failed to compile image asset", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.weight(1f).height(46.dp),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    Icon(imageVector = Icons.Default.Image, contentDescription = "", modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (lang == "BN") "ছবি" else "Image",
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
                    text = if (lang == "BN") "কাস্টমার খাতায় ফিরে যান" else "Return to Ledger",
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
                    text = if (lang == "BN") "নতুন কাস্টমার যোগ" else "Add New Customer Profile",
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
                        text = if (lang == "BN") "কাস্টমার খাতা বিবরণ" else "Customer Information",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                item {
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = onNameChange,
                        label = { Text(if (lang == "BN") "কাস্টমার বা প্রতিষ্ঠানের নাম" else "Customer or Business Name") },
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
                        label = { Text(if (lang == "BN") "মোবাইল নম্বর" else "Contact Phone Number") },
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
                        label = { Text(if (lang == "BN") "বর্তমান ঠিকানা (শাখা / অফিস)" else "Address (Branch / Office)") },
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
