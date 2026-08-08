package com.safa.account.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.safa.account.data.model.Customer
import com.safa.account.data.model.RemittanceTransaction
import com.safa.account.data.model.Supplier
import com.safa.account.ui.viewmodel.HundiViewModel
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
import kotlin.random.Random

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionScreen(
    viewModel: HundiViewModel,
    modifier: Modifier = Modifier
) {
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()
    val customers by viewModel.customers.collectAsStateWithLifecycle()
    val suppliers by viewModel.suppliers.collectAsStateWithLifecycle()
    val liveRates by viewModel.currentRates.collectAsStateWithLifecycle()
    val lang by viewModel.currentLanguage.collectAsStateWithLifecycle()
    val walletBatches by viewModel.walletBatches.collectAsStateWithLifecycle()
    val walletLedgers by viewModel.walletLedgers.collectAsStateWithLifecycle()
    val foreignCur by viewModel.selectedForeignCurrency.collectAsStateWithLifecycle()
    val localCur by viewModel.selectedLocalCurrency.collectAsStateWithLifecycle()
    val currentOperator by viewModel.currentOperator.collectAsStateWithLifecycle()
    val operatorPin = currentOperator?.pin ?: ""

    if (currentOperator != null && !currentOperator!!.canViewTransactions) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Lock, contentDescription = "", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
                Text(text = viewModel.t("access_denied"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                Text(text = viewModel.t("permission_required"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
            }
        }
        return
    }

    // --- Customize state options (User customizable features) ---
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilterStatus by remember { mutableStateOf("All") } // "All", "Pending", "Delivered", "Cancelled"
    var selectedSortOption by remember { mutableStateOf("Newest") } // "Newest", "Oldest", "Max ${foreignCur}", "Max Profit"
    var selectedDateFilter by remember { mutableStateOf("All") } // "All", "Today", "Week", "Month"
    var isCompactDensity by remember { mutableStateOf(false) } // Luxurious Spacious vs Compact Table style
    var showStatsDashboard by remember { mutableStateOf(true) } // Option to show/hide dynamic KPI cards
    var isCustomizerExpanded by remember { mutableStateOf(false) } // Accordion open status

    // Dialog & overlay triggers
    var showAddDialog by remember { mutableStateOf(false) }
    var expandedTxId by remember { mutableStateOf<Int?>(null) }
    var activeCalcTarget by remember { mutableStateOf(CalcTargetTx.NONE) }
    var viewingReceiptTx by remember { mutableStateOf<RemittanceTransaction?>(null) }

    // Forms to add remittance
    var selectedCustomerId by remember { mutableStateOf(0) }
    var selectedBatchId by remember { mutableStateOf(0) }
    var sarAmountInput by remember { mutableStateOf("") }
    var customerRateInput by remember { mutableStateOf("") }
    var supplierRateInput by remember { mutableStateOf("") }
    var receiverAccountTypeInput by remember { mutableStateOf("bKash") } // "bKash", "Nagad", "Rocket", "Bank Account", "Cash"
    var receiverAccountNoInput by remember { mutableStateOf("") }
    var receiverNameInput by remember { mutableStateOf("") }
    var receiverPhoneInput by remember { mutableStateOf("") }
    var notesInput by remember { mutableStateOf("") }

    // Edit transaction state fields
    var editingTx by remember { mutableStateOf<RemittanceTransaction?>(null) }
    var editCustomerIdInput by remember { mutableStateOf(0) }
    var editReceiverNameInput by remember { mutableStateOf("") }
    var editReceiverPhoneInput by remember { mutableStateOf("") }
    var editSarAmountInput by remember { mutableStateOf("") }
    var editCustomerRateInput by remember { mutableStateOf("") }
    var editSupplierRateInput by remember { mutableStateOf("") }
    var editReceiverAccountTypeInput by remember { mutableStateOf("bKash") }
    var editReceiverAccountNoInput by remember { mutableStateOf("") }
    var editSupplierIdInput by remember { mutableStateOf(0) }
    var editNotesInput by remember { mutableStateOf("") }
    var editPinCodeInput by remember { mutableStateOf("") }
    var editPinErrorText by remember { mutableStateOf<String?>(null) }

    // Delete transaction confirmation state fields
    var deletingTx by remember { mutableStateOf<RemittanceTransaction?>(null) }

    if (showAddDialog) {
        androidx.activity.compose.BackHandler { showAddDialog = false }
    } else if (editingTx != null) {
        androidx.activity.compose.BackHandler { editingTx = null }
    } else if (viewingReceiptTx != null) {
        androidx.activity.compose.BackHandler { viewingReceiptTx = null }
    }
    var deletePinCodeInput by remember { mutableStateOf("") }
    var deletePinErrorText by remember { mutableStateOf<String?>(null) }

    val currencyFormatter = remember { DecimalFormat("#,##0.00") }
    val dateFormatter = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }

    // Handle pre-select callbacks from other pages
    val preselectCustomerId by viewModel.newTransactionPreselectCustomerId.collectAsStateWithLifecycle()

    LaunchedEffect(showAddDialog, liveRates) {
        if (showAddDialog && liveRates != null) {
            customerRateInput = liveRates!!.customerRate.toString()
            supplierRateInput = liveRates!!.supplierRate.toString()
        }
    }

    LaunchedEffect(preselectCustomerId) {
        if (preselectCustomerId != null) {
            selectedCustomerId = preselectCustomerId!!
            selectedBatchId = walletBatches.firstOrNull { it.remainingBdt > 0.05 }?.id ?: 0
            showAddDialog = true
            viewModel.clearTransactionPreselect()
        }
    }

    // Default receiver details to matches from customer when selected customer changes
    LaunchedEffect(selectedCustomerId, customers) {
        val cust = customers.find { it.id == selectedCustomerId }
        if (cust != null) {
            receiverNameInput = cust.name
            receiverPhoneInput = cust.phone
        }
    }

    // --- Dynamic Filters, Searches, and Sort implementation ---
    val filteredTxs = remember(transactions, selectedFilterStatus, searchQuery, selectedSortOption, selectedDateFilter, customers, suppliers) {
        var list = transactions

        // 1. Status Filter
        if (selectedFilterStatus != "All") {
            list = list.filter { it.status == selectedFilterStatus }
        }

        // 2. Date Filter
        val now = System.currentTimeMillis()
        val oneDayMs = 24 * 60 * 60 * 1000L
        list = when (selectedDateFilter) {
            "Today" -> list.filter { now - it.timestamp < oneDayMs }
            "Week" -> list.filter { now - it.timestamp < 7 * oneDayMs }
            "Month" -> list.filter { now - it.timestamp < 30 * oneDayMs }
            else -> list
        }

        // 3. Search Query (Fuzzy Search)
        if (searchQuery.isNotBlank()) {
            val q = searchQuery.trim().lowercase()
            list = list.filter { tx ->
                val custName = customers.find { it.id == tx.customerId }?.name?.lowercase() ?: ""
                val custPhone = customers.find { it.id == tx.customerId }?.phone?.lowercase() ?: ""
                val suppName = suppliers.find { it.id == tx.supplierId }?.name?.lowercase() ?: ""
                
                tx.receiverName.lowercase().contains(q) ||
                tx.receiverPhone.lowercase().contains(q) ||
                tx.receiverAccountNo.contains(q) ||
                tx.notes.lowercase().contains(q) ||
                tx.amountSar.toString().contains(q) ||
                tx.amountBdt.toString().contains(q) ||
                custName.contains(q) ||
                custPhone.contains(q) ||
                suppName.contains(q)
            }
        }

        // 4. Sort Options
        list = when (selectedSortOption) {
            "Oldest" -> list.sortedBy { it.timestamp }
            "Max ${foreignCur}" -> list.sortedByDescending { it.amountSar }
            "Min ${foreignCur}" -> list.sortedBy { it.amountSar }
            "Max Profit" -> list.sortedByDescending { it.getProfitBdt() }
            else -> list.sortedByDescending { it.timestamp } // "Newest"
        }

        list
    }

    // Dynamic Live calculated Stats Dashboard values
    val statsSarTotal = remember(filteredTxs) { filteredTxs.sumOf { it.amountSar } }
    val statsBdtTotal = remember(filteredTxs) { filteredTxs.sumOf { it.amountBdt } }
    val statsProfitTotal = remember(filteredTxs) { filteredTxs.sumOf { it.getProfitBdt() } }
    val statsPendingCount = remember(filteredTxs) { filteredTxs.count { it.status == "Pending" } }
    val statsDeliveredCount = remember(filteredTxs) { filteredTxs.count { it.status == "Delivered" } }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Spacer(modifier = Modifier.height(10.dp))

            // --- HEADER TITLE AND QUICK ADD TRIGGER ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
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
                            imageVector = Icons.Default.ReceiptLong,
                            contentDescription = "",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column {
                        Text(
                            text = if (lang == "BN") "লেনদেন সমূহ" else "Transactions",
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
                            text = if (lang == "BN") "ডিজিটাল হিসাব খাতা" else "Remittance Ledger",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }



            // --- 1. DYNAMIC STATS KPI DASHBOARD (Show/Hide Customizable) ---
            AnimatedVisibility(visible = showStatsDashboard) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // KPI: ${foreignCur}volume
                    KpiIndicatorCard(
                        title = if (lang == "BN") "সংগৃহীত রিয়াল" else "Total Received",
                        value = "SAR ${currencyFormatter.format(statsSarTotal)}",
                        subtitle = if (lang == "BN") "সক্রিয় কাস্টমার ফান্ড" else "Fuzzy matches",
                        icon = Icons.Default.MonetizationOn,
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                        textColor = MaterialTheme.colorScheme.primary
                    )

                    // KPI: ${localCur}volume disbursed
                    KpiIndicatorCard(
                        title = if (lang == "BN") "বিতরণকৃত টাকা" else "Total Disbursed",
                        value = "৳${currencyFormatter.format(statsBdtTotal)}",
                        subtitle = if (lang == "BN") "মোট আউটফ্লো ভলিউম" else "Ledger payout",
                        icon = Icons.Default.Payments,
                        containerColor = Color(0xFFE8F5E9),
                        textColor = Color(0xFF2E7D32)
                    )

                    // KPI: Net yield profit margins
                    KpiIndicatorCard(
                        title = if (lang == "BN") "মোট আনুমানিক মুনাফা" else "Expected Profit",
                        value = "৳${currencyFormatter.format(statsProfitTotal)}",
                        subtitle = if (lang == "BN") "রেট ডিফারেনশিয়াল আয়" else "Arbitrage margin",
                        icon = Icons.Default.TrendingUp,
                        containerColor = Color(0xFFE3F2FD),
                        textColor = Color(0xFF1565C0)
                    )

                    // KPI: Count states
                    KpiIndicatorCard(
                        title = if (lang == "BN") "স্ট্যাটাস অবস্থা" else "Status Tracking",
                        value = "D: $statsDeliveredCount | P: $statsPendingCount",
                        subtitle = if (lang == "BN") "বিতরণ বনাম অপেক্ষমান" else "Delivered vs Pending",
                        icon = Icons.Default.DoneAll,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        textColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // --- 2. FUZZY SEARCH AND FILTER DASHBOARD BUTTONS ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(if (lang == "BN") "খুঁজুন..." else "Search...", fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "", modifier = Modifier.size(20.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "", modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    ),
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp),
                    modifier = Modifier.weight(1f).height(48.dp)
                )

                // Advanced settings dropdown trigger
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
                        contentDescription = "Customize",
                        tint = if (isCustomizerExpanded) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // --- 3. EXPANDABLE CUSTOMIZER SETTINGS ACCORDION ---
            AnimatedVisibility(visible = isCustomizerExpanded) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = if (lang == "BN") "⚙️ ফিল্টার ও সেটিংস" else "⚙️ Filter & Settings",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Sort Choice
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (lang == "BN") "বাছাই করুন" else "Sort By",
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
                                                "Oldest" -> if (lang == "BN") "পুরাতন থেকে নতুন" else "Oldest First"
                                                "Max ${foreignCur}" -> if (lang == "BN") "রিয়াল বেশি" else "Max ${foreignCur}"
                                                "Min ${foreignCur}" -> if (lang == "BN") "রিয়াল কম" else "Min ${foreignCur}"
                                                "Max Profit" -> if (lang == "BN") "মুনাফা বেশি" else "Max Profit"
                                                else -> if (lang == "BN") "নতুন থেকে পুরাতন" else "Newest First"
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
                                        listOf("Newest", "Oldest", "Max ${foreignCur}", "Min ${foreignCur}", "Max Profit").forEach { option ->
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        when (option) {
                                                            "Oldest" -> if (lang == "BN") "পুরাতন থেকে নতুন" else "Oldest First"
                                                            "Max ${foreignCur}" -> if (lang == "BN") "সর্বোচ্চ রিয়াল" else "Highest Riyal"
                                                            "Min ${foreignCur}" -> if (lang == "BN") "সর্বনিম্ন রিয়াল" else "Lowest Riyal"
                                                            "Max Profit" -> if (lang == "BN") "সর্বোচ্চ মুনাফা" else "Highest Profit"
                                                            else -> if (lang == "BN") "নতুন থেকে পুরাতন" else "Newest First"
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

                            // Date Range Choice
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (lang == "BN") "তারিখ ফিল্টার" else "Date Filter",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.padding(bottom = 2.dp),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                var dateMenuExpanded by remember { mutableStateOf(false) }
                                Box {
                                    OutlinedButton(
                                        onClick = { dateMenuExpanded = true },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = when (selectedDateFilter) {
                                                "Today" -> if (lang == "BN") "আজকের" else "Today"
                                                "Week" -> if (lang == "BN") "শেষ ৭ দিন" else "Last 7 Days"
                                                "Month" -> if (lang == "BN") "শেষ ৩০ দিন" else "Last 30 Days"
                                                else -> if (lang == "BN") "সব সময়" else "All Time"
                                            },
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = dateMenuExpanded,
                                        onDismissRequest = { dateMenuExpanded = false }
                                    ) {
                                        listOf("All", "Today", "Week", "Month").forEach { range ->
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        when (range) {
                                                            "Today" -> if (lang == "BN") "আজকের" else "Today"
                                                            "Week" -> if (lang == "BN") "শেষ ৭ দিন" else "Last 7 Days"
                                                            "Month" -> if (lang == "BN") "শেষ ৩০ দিন" else "Last 30 Days"
                                                            else -> if (lang == "BN") "সব সময়" else "All Time"
                                                        },
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                },
                                                onClick = {
                                                    selectedDateFilter = range
                                                    dateMenuExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        // Toggles row: layout density and stats panel
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Density Setup selection
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = if (lang == "BN") "সংক্ষিপ্ত ভিউ:" else "Compact View:",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Switch(
                                    checked = isCompactDensity,
                                    onCheckedChange = { isCompactDensity = it },
                                    thumbContent = {
                                        Icon(
                                            imageVector = if (isCompactDensity) Icons.Default.GridOn else Icons.Default.ViewAgenda,
                                            contentDescription = null,
                                            modifier = Modifier.size(SwitchDefaults.IconSize)
                                        )
                                    },
                                    modifier = Modifier.scale(0.85f)
                                )
                            }

                            // Show Stats Toggle
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = if (lang == "BN") "পরিসংখ্যান (Stats):" else "Show KPI Stats:",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Switch(
                                    checked = showStatsDashboard,
                                    onCheckedChange = { showStatsDashboard = it },
                                    thumbContent = {
                                        Icon(
                                            imageVector = if (showStatsDashboard) Icons.Default.BarChart else Icons.Default.VisibilityOff,
                                            contentDescription = null,
                                            modifier = Modifier.size(SwitchDefaults.IconSize)
                                        )
                                    },
                                    modifier = Modifier.scale(0.85f)
                                )
                            }
                        }
                    }
                }
            }

            // --- 4. HORIZONTAL FILTER TABS SEGMENTED BAR ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf("All", "Pending", "Delivered", "Cancelled").forEach { status ->
                    val isSelected = selectedFilterStatus == status
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedFilterStatus = status },
                        label = {
                            Text(
                                text = when (status) {
                                    "All" -> if (lang == "BN") "সকল লেনদেন" else "All Remits"
                                    "Pending" -> if (lang == "BN") "অপেক্ষমান" else "Pending"
                                    "Delivered" -> if (lang == "BN") "বিতরিত" else "Delivered"
                                    "Cancelled" -> if (lang == "BN") "বাতিল" else "Cancelled"
                                    else -> status
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.testTag("filter_chip_$status")
                    )
                }
            }

            // --- 5. CORE TRANSACTIONS LEDGER LIST ---
            if (filteredTxs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SearchOff,
                            contentDescription = "",
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(50.dp)
                        )
                        Text(
                            text = if (lang == "BN") "কোনো লেনদেন রেকর্ড পাওয়া যায়নি।" else "No transactions matching your selection.",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.outline,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 76.dp)
                ) {
                    items(filteredTxs, key = { it.id }) { tx ->
                        val customer = customers.find { it.id == tx.customerId }
                        val supplier = suppliers.find { it.id == tx.supplierId }

                        // Compact rows vs Spacious Luxurious Card switching
                        if (isCompactDensity) {
                            TransactionCompactItemRow(
                                tx = tx,
                                customerName = customer?.name ?: "N/A",
                                isExpanded = expandedTxId == tx.id,
                                lang = lang,
                                dateFormatter = dateFormatter,
                                onClick = {
                                    expandedTxId = if (expandedTxId == tx.id) null else tx.id
                                },
                                onReceipt = { viewingReceiptTx = tx }
                            )
                        } else {
                            TransactionPremiumCard(
                                tx = tx,
                                customer = customer,
                                supplier = supplier,
                                isExpanded = expandedTxId == tx.id,
                                lang = lang,
                                currencyFormatter = currencyFormatter,
                                dateFormatter = dateFormatter,
                                onClick = {
                                    expandedTxId = if (expandedTxId == tx.id) null else tx.id
                                },
                                onUpdateStatus = { pendingTx, statusValue ->
                                    viewModel.updateTransactionStatus(pendingTx, statusValue)
                                },
                                onEdit = { editTarget ->
                                    editingTx = editTarget
                                    editCustomerIdInput = editTarget.customerId
                                    editReceiverNameInput = editTarget.receiverName
                                    editReceiverPhoneInput = editTarget.receiverPhone
                                    editSarAmountInput = editTarget.amountSar.toString()
                                    editCustomerRateInput = editTarget.customerRate.toString()
                                    editSupplierRateInput = editTarget.supplierRate.toString()
                                    editReceiverAccountTypeInput = editTarget.receiverAccountType
                                    editReceiverAccountNoInput = editTarget.receiverAccountNo
                                    editSupplierIdInput = editTarget.supplierId
                                    editNotesInput = editTarget.notes
                                    editPinCodeInput = ""
                                    editPinErrorText = null
                                },
                                onDelete = { deleteTarget ->
                                    deletingTx = deleteTarget
                                    deletePinCodeInput = ""
                                    deletePinErrorText = null
                                },
                                onReceipt = { viewingReceiptTx = tx },
                                foreignCur = foreignCur
                            )
                        }
                    }
                }
            }
        }

        // --- NEW TRANSACTION REGISTER DIALOG (Form styled beautifully) ---
        if (showAddDialog) {
            Dialog(
                onDismissRequest = { showAddDialog = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .heightIn(max = 760.dp)
                        .clip(RoundedCornerShape(24.dp)),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // Header row with back icon
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = { showAddDialog = false }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back",
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Column(modifier = Modifier.padding(start = 8.dp)) {
                                    Text(
                                        text = viewModel.t("new_remittance"),
                                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = if (lang == "BN") "লেনদেন ডাটা এন্ট্রি ফরম" else "Register new transaction record",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }

                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f, fill = false)
                            ) {
                                // Customer selector layout
                                item {
                                    Text(
                                        text = viewModel.t("select_customer"),
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(bottom = 4.dp)
                                    )
                                    if (selectedCustomerId == 0 && customers.isNotEmpty()) {
                                        selectedCustomerId = customers.first().id
                                    }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        for (customer in customers) {
                                            val isSelected = selectedCustomerId == customer.id
                                            FilterChip(
                                                selected = isSelected,
                                                onClick = { selectedCustomerId = customer.id },
                                                label = { Text("${customer.name} (${customer.phone})", fontWeight = FontWeight.Bold) },
                                                shape = RoundedCornerShape(10.dp)
                                            )
                                        }
                                    }
                                }

                                // Wallet Ledger/Batch selector layout
                                item {
                                    val activeBatches = remember(walletBatches, walletLedgers) {
                                        walletBatches.filter { it.remainingBdt > 0.05 }.map { batch ->
                                            val ledgerName = walletLedgers.find { it.id == batch.ledgerId }?.name ?: "Unknown Ledger"
                                            Pair(batch, ledgerName)
                                        }
                                    }

                                    Text(
                                        text = if (lang == "BN") "টাকার সোর্স ওয়ালেট খাতা (Wallet Source)" else "Select Wallet Ledger Source",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(bottom = 4.dp)
                                    )
                                    if (activeBatches.isEmpty()) {
                                        Surface(
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.errorContainer
                                        ) {
                                            Text(
                                                text = if (lang == "BN") "⚠️ ওয়ালেটে লেজারে কোনো সচল ফান্ড নেই! প্রথমে ওয়ালেটে ফান্ড যুক্ত করুন।" else "⚠️ No active funds in any wallet ledger! Configure a wallet deposit first.",
                                                modifier = Modifier.padding(12.dp),
                                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                                color = MaterialTheme.colorScheme.onErrorContainer
                                            )
                                        }
                                    } else {
                                        if (selectedBatchId == 0) {
                                            selectedBatchId = activeBatches.first().first.id
                                            supplierRateInput = activeBatches.first().first.rate.toString()
                                        }
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            for ((batch, ledgerName) in activeBatches) {
                                                val isSelected = selectedBatchId == batch.id
                                                FilterChip(
                                                    selected = isSelected,
                                                    onClick = { 
                                                        selectedBatchId = batch.id
                                                        supplierRateInput = batch.rate.toString()
                                                    },
                                                    label = {
                                                        Text(
                                                            text = "$ledgerName (Avail: ৳${batch.remainingBdt.toInt()} @ ${batch.rate})",
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    },
                                                    shape = RoundedCornerShape(10.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                // Saudi Amount Input with Calculator triggered
                                item {
                                    OutlinedTextField(
                                        value = sarAmountInput,
                                        onValueChange = {},
                                        readOnly = true,
                                        placeholder = { Text(viewModel.t("saudi_amount"), color = MaterialTheme.colorScheme.outline) },
                                        leadingIcon = {
                                            Icon(Icons.Default.MonetizationOn, contentDescription = "", tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(20.dp))
                                        },
                                        trailingIcon = {
                                            Icon(Icons.Default.Calculate, contentDescription = "Calculate", tint = MaterialTheme.colorScheme.primary)
                                        },
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("new_tx_sar_amount"),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                                        ),
                                        interactionSource = remember { MutableInteractionSource() }.also { src ->
                                            LaunchedEffect(src) {
                                                src.interactions.collect { interaction ->
                                                    if (interaction is androidx.compose.foundation.interaction.PressInteraction.Release) {
                                                        activeCalcTarget = CalcTargetTx.SAR_AMOUNT
                                                    }
                                                }
                                            }
                                        }
                                    )
                                }

                                 // Rates Grid Row (Customer + Supplier)
                                 item {
                                     val isRateBasedMode by viewModel.isRateBasedModeEnabled.collectAsStateWithLifecycle()
                                     if (isRateBasedMode) {
                                         Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                             OutlinedTextField(
                                                 value = customerRateInput,
                                                 onValueChange = {},
                                                 readOnly = true,
                                                 placeholder = { Text(viewModel.t("customer_assigned_rate"), color = MaterialTheme.colorScheme.outline) },
                                                 leadingIcon = {
                                                     Icon(Icons.Default.TrendingUp, contentDescription = "", tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(18.dp))
                                                 },
                                                 shape = RoundedCornerShape(14.dp),
                                                 modifier = Modifier
                                                     .weight(1f)
                                                     .testTag("new_tx_cust_rate"),
                                                 colors = OutlinedTextFieldDefaults.colors(
                                                     focusedBorderColor = MaterialTheme.colorScheme.primary,
                                                     unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                                                 ),
                                                 interactionSource = remember { MutableInteractionSource() }.also { src ->
                                                     LaunchedEffect(src) {
                                                         src.interactions.collect { interaction ->
                                                             if (interaction is androidx.compose.foundation.interaction.PressInteraction.Release) {
                                                                 activeCalcTarget = CalcTargetTx.CUSTOMER_RATE
                                                             }
                                                         }
                                                     }
                                                 }
                                             )
                                             
                                             val isSupplierRateEnabled by viewModel.isSupplierRateEnabled.collectAsStateWithLifecycle()
                                             if (isSupplierRateEnabled) {
                                                 OutlinedTextField(
                                                     value = supplierRateInput,
                                                     onValueChange = {},
                                                     readOnly = true,
                                                     placeholder = { Text(viewModel.t("supplier_rate_tx"), color = MaterialTheme.colorScheme.outline) },
                                                     leadingIcon = {
                                                         Icon(Icons.Default.TrendingDown, contentDescription = "", tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(18.dp))
                                                     },
                                                     shape = RoundedCornerShape(14.dp),
                                                     modifier = Modifier
                                                         .weight(1f)
                                                         .testTag("new_tx_supp_rate"),
                                                     colors = OutlinedTextFieldDefaults.colors(
                                                         focusedBorderColor = MaterialTheme.colorScheme.primary,
                                                         unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                                                     ),
                                                     interactionSource = remember { MutableInteractionSource() }.also { src ->
                                                         LaunchedEffect(src) {
                                                             src.interactions.collect { interaction ->
                                                                 if (interaction is androidx.compose.foundation.interaction.PressInteraction.Release) {
                                                                     activeCalcTarget = CalcTargetTx.SUPPLIER_RATE
                                                                 }
                                                             }
                                                         }
                                                     }
                                                 )
                                             }
                                         }
                                     } else {
                                         // Automatically force rates to 1.0 under the hood if hidden
                                         LaunchedEffect(Unit) {
                                             customerRateInput = "1.0"
                                             supplierRateInput = "1.0"
                                         }
                                     }
                                 }


                                // Interactive Calculations Panel with Estimated Earnings
                                item {
                                    val computedBdt = remember(sarAmountInput, customerRateInput) {
                                        val s = sarAmountInput.toDoubleOrNull() ?: 0.0
                                        val cr = customerRateInput.toDoubleOrNull() ?: 0.0
                                        s * cr
                                    }
                                    val computedProfitBdt = remember(sarAmountInput, customerRateInput, supplierRateInput) {
                                        val s = sarAmountInput.toDoubleOrNull() ?: 0.0
                                        val cr = customerRateInput.toDoubleOrNull() ?: 0.0
                                        val sr = supplierRateInput.toDoubleOrNull() ?: 0.0
                                        if (sr > 0) s * (sr - cr) else 0.0
                                    }

                                    if (computedBdt > 0.01) {
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(14.dp),
                                                verticalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(
                                                        text = "${viewModel.t("receiver_bdt_amount")}:",
                                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                                    )
                                                    Text(
                                                        text = "৳ ${currencyFormatter.format(computedBdt)}",
                                                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                                                        color = Color(0xFF2E7D32)
                                                    )
                                                }
                                                Divider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(
                                                        text = if (lang == "BN") "হিসাবকৃত মুনাফা (Estimated Profit):" else "Estimated Arbitrage Profit:",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.outline
                                                    )
                                                    Text(
                                                        text = "৳ ${currencyFormatter.format(computedProfitBdt)}",
                                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.ExtraBold),
                                                        color = if (computedProfitBdt >= 0) Color(0xFF2E7D32) else Color(0xFFC62828)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                // Payout Channel choice Chips List
                                item {
                                    Text(
                                        text = viewModel.t("payment_method"),
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(bottom = 4.dp)
                                    )
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        val payoutChannels = listOf("bKash", "Nagad", "Rocket", "Bank Account", "Cash")
                                        payoutChannels.forEach { channel ->
                                            val isCurrent = receiverAccountTypeInput == channel
                                            FilterChip(
                                                selected = isCurrent,
                                                onClick = { receiverAccountTypeInput = channel },
                                                label = { Text(channel) },
                                                modifier = Modifier.testTag("channel_chip_$channel")
                                            )
                                        }
                                    }
                                }

                                // Interactive target recipient fields for customizable delivery ledger
                                item {
                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Text(
                                            text = if (lang == "BN") "প্রাপকের বিবরণ (Recipient Details)" else "Recipient Payout Details",
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.primary
                                        )

                                        // Recipient Name
                                        OutlinedTextField(
                                            value = receiverNameInput,
                                            onValueChange = { receiverNameInput = it },
                                            placeholder = { Text(if (lang == "BN") "প্রাপকের নাম" else "Recipient Name") },
                                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = "", modifier = Modifier.size(20.dp)) },
                                            singleLine = true,
                                            shape = RoundedCornerShape(14.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        )

                                        // Recipient Phone
                                        OutlinedTextField(
                                            value = receiverPhoneInput,
                                            onValueChange = { receiverPhoneInput = it },
                                            placeholder = { Text(if (lang == "BN") "প্রাপকের ফোন নম্বর" else "Recipient Phone") },
                                            leadingIcon = { Icon(Icons.Default.Phone, contentDescription = "", modifier = Modifier.size(20.dp)) },
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                            shape = RoundedCornerShape(14.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        )

                                        // Recipient Account/Credit/Number
                                        if (receiverAccountTypeInput != "Cash") {
                                            OutlinedTextField(
                                                value = receiverAccountNoInput,
                                                onValueChange = { receiverAccountNoInput = it },
                                                placeholder = { Text(if (lang == "BN") "হিসাব/টাকা প্রধান নম্বর (${receiverAccountTypeInput})" else "Account / Transfer Number (${receiverAccountTypeInput})", color = MaterialTheme.colorScheme.outline) },
                                                leadingIcon = {
                                                    Icon(Icons.Default.CreditCard, contentDescription = "", tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(20.dp))
                                                },
                                                singleLine = true,
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                                shape = RoundedCornerShape(14.dp),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .testTag("new_tx_receiver_acc_no"),
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                                                )
                                            )
                                        }
                                    }
                                }

                                // Instruction notes input
                                item {
                                    OutlinedTextField(
                                        value = notesInput,
                                        onValueChange = { notesInput = it },
                                        placeholder = { Text(viewModel.t("notes"), color = MaterialTheme.colorScheme.outline) },
                                        leadingIcon = {
                                            Icon(Icons.AutoMirrored.Filled.Notes, contentDescription = "", tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(20.dp))
                                        },
                                        singleLine = true,
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("new_tx_notes"),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                                        )
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // Action confirm button
                            Button(
                                onClick = {
                                    val sar = sarAmountInput.toDoubleOrNull() ?: 0.0
                                    val cr = customerRateInput.toDoubleOrNull() ?: 32.0
                                    viewModel.createRemittance(
                                        customerId = selectedCustomerId,
                                        walletBatchId = selectedBatchId,
                                        amountSar = sar,
                                        customerRate = cr,
                                        receiverName = receiverNameInput,
                                        receiverPhone = receiverPhoneInput,
                                        receiverAccountType = receiverAccountTypeInput,
                                        receiverAccountNo = if (receiverAccountTypeInput == "Cash") "Cash Payout" else receiverAccountNoInput,
                                        notes = notesInput
                                    ) {
                                        sarAmountInput = ""
                                        receiverAccountNoInput = ""
                                        notesInput = ""
                                        receiverNameInput = ""
                                        receiverPhoneInput = ""
                                        showAddDialog = false
                                    }
                                },
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("submit_tx_btn"),
                                enabled = sarAmountInput.isNotBlank() && receiverNameInput.isNotBlank() && receiverPhoneInput.isNotBlank() && (receiverAccountTypeInput == "Cash" || receiverAccountNoInput.isNotBlank())
                            ) {
                                Text(
                                    text = if (lang == "BN") "লেনদেন রেকর্ড জমা দিন" else "Issue Remittance",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        color = Color.White,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                )
                            }
                        }
                    }
                }
        }

        // --- EDIT TRANSACTION DIALOG FORM ---
        if (editingTx != null) {
            Dialog(
                onDismissRequest = { editingTx = null },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .heightIn(max = 740.dp)
                        .clip(RoundedCornerShape(24.dp)),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // Header row with back trigger
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = { editingTx = null }) {
                                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                                }
                                Text(
                                    text = if (lang == "BN") "লেনদেন তথ্য সংশোধন" else "Edit Remittance Data",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                    modifier = Modifier.padding(start = 6.dp)
                                )
                            }

                            // Customer selection list
                            Text(
                                text = if (lang == "BN") "কাস্টমার পরিবর্তন করুন" else "Change Customer",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                customers.forEach { customer ->
                                    val isCurrent = editCustomerIdInput == customer.id
                                    FilterChip(
                                        selected = isCurrent,
                                        onClick = { editCustomerIdInput = customer.id },
                                        label = { Text("${customer.name} (${customer.phone})") },
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                }
                            }

                            // Recipient Name
                            Text(
                                text = if (lang == "BN") "প্রাপকের নাম" else "Recipient Name",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            OutlinedTextField(
                                value = editReceiverNameInput,
                                onValueChange = { editReceiverNameInput = it },
                                placeholder = { Text("Name") },
                                leadingIcon = {
                                    Icon(Icons.Default.Person, contentDescription = "", tint = MaterialTheme.colorScheme.outline)
                                },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                                )
                            )

                            // Recipient Phone
                            Text(
                                text = if (lang == "BN") "প্রাপকের ফোন নম্বর" else "Recipient Phone",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            OutlinedTextField(
                                value = editReceiverPhoneInput,
                                onValueChange = { editReceiverPhoneInput = it },
                                placeholder = { Text("Phone") },
                                leadingIcon = {
                                    Icon(Icons.Default.Phone, contentDescription = "", tint = MaterialTheme.colorScheme.outline)
                                },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                                )
                            )

                            // Saudi Amount Input
                            Text(
                                text = if (lang == "BN") "সৌদি রিয়াল (SAR) পরিমাণ" else "Saudi Amount (SAR)",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            OutlinedTextField(
                                value = editSarAmountInput,
                                onValueChange = { editSarAmountInput = it },
                                placeholder = { Text("0.00") },
                                leadingIcon = {
                                    Icon(Icons.Default.MonetizationOn, contentDescription = "", tint = MaterialTheme.colorScheme.outline)
                                },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                                )
                            )

                            // Rates Row (Customer + Supplier)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (lang == "BN") "কাস্টমার রেট" else "Customer Rate",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                    OutlinedTextField(
                                        value = editCustomerRateInput,
                                        onValueChange = { editCustomerRateInput = it },
                                        placeholder = { Text("0.00") },
                                        shape = RoundedCornerShape(14.dp),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = MaterialTheme.colorScheme.secondary,
                                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                                        )
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (lang == "BN") "সাপ্লায়ার রেট" else "Supplier Rate",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                    OutlinedTextField(
                                        value = editSupplierRateInput,
                                        onValueChange = { editSupplierRateInput = it },
                                        placeholder = { Text("0.00") },
                                        shape = RoundedCornerShape(14.dp),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = MaterialTheme.colorScheme.secondary,
                                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                                        )
                                    )
                                }
                            }

                            // Receiver Account Type Selection Chips
                            Text(
                                text = if (lang == "BN") "পেমেন্ট মাধ্যম" else "Payout Channel",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val payoutChannels = listOf("bKash", "Nagad", "Rocket", "Bank Account", "Cash")
                                payoutChannels.forEach { channel ->
                                    val isCurrent = editReceiverAccountTypeInput == channel
                                    FilterChip(
                                        selected = isCurrent,
                                        onClick = { editReceiverAccountTypeInput = channel },
                                        label = { Text(channel) },
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                }
                            }

                            // Receiver Account No (if not Cash)
                            if (editReceiverAccountTypeInput != "Cash") {
                                OutlinedTextField(
                                    value = editReceiverAccountNoInput,
                                    onValueChange = { editReceiverAccountNoInput = it },
                                    placeholder = { Text(if (lang == "BN") "একাউন্ট / নাম্বার" else "Account / Transfer Number") },
                                    leadingIcon = {
                                        Icon(Icons.Default.CreditCard, contentDescription = "", tint = MaterialTheme.colorScheme.outline)
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                                    )
                                )
                            }

                            // Supplier select
                            Text(
                                text = if (lang == "BN") "সাপ্লায়ার পরিবর্তন করুন" else "Change Supplier Account",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                suppliers.forEach { sup ->
                                    val isCurrent = editSupplierIdInput == sup.id
                                    FilterChip(
                                        selected = isCurrent,
                                        onClick = { editSupplierIdInput = sup.id },
                                        label = { Text(sup.name) },
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                }
                            }

                            // Instruction/Notes
                            OutlinedTextField(
                                value = editNotesInput,
                                onValueChange = { editNotesInput = it },
                                placeholder = { Text(if (lang == "BN") "মন্তব্য / বিশেষ নোট" else "Notes / Operational Instructions") },
                                leadingIcon = {
                                    Icon(Icons.Default.Notes, contentDescription = "", tint = MaterialTheme.colorScheme.outline)
                                },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                                )
                            )

                            // Dynamic calculations review
                            val previewBdt = remember(editSarAmountInput, editCustomerRateInput) {
                                val s = editSarAmountInput.toDoubleOrNull() ?: 0.0
                                    val cr = editCustomerRateInput.toDoubleOrNull() ?: 0.0
                                    s * cr
                            }
                            val previewProfit = remember(editSarAmountInput, editCustomerRateInput, editSupplierRateInput) {
                                val s = editSarAmountInput.toDoubleOrNull() ?: 0.0
                                val cr = editCustomerRateInput.toDoubleOrNull() ?: 0.0
                                val sr = editSupplierRateInput.toDoubleOrNull() ?: 0.0
                                s * (sr - cr)
                            }
                            if (previewBdt > 0.01) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f))
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = "৳ ${currencyFormatter.format(previewBdt)} (Payout Output)",
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "Net Arbitrage: ৳ ${currencyFormatter.format(previewProfit)}",
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                            color = if (previewProfit >= 0) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }

                            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                            // Mandatory security passcode
                            if (currentOperator?.isBiometricEnabled == true) {
                                com.safa.account.ui.BiometricTriggerButton(
                                    lang = lang,
                                    onSuccess = {
                                        val sar = editSarAmountInput.toDoubleOrNull() ?: 0.0
                                        val cr = editCustomerRateInput.toDoubleOrNull() ?: 0.0
                                        val sr = editSupplierRateInput.toDoubleOrNull() ?: 0.0
                                        val updatedTx = editingTx!!.copy(
                                            amountSar = sar,
                                            customerRate = cr,
                                            supplierRate = sr,
                                            amountBdt = sar * cr,
                                            receiverAccountType = editReceiverAccountTypeInput,
                                            receiverAccountNo = if (editReceiverAccountTypeInput == "Cash") "Cash Payout" else editReceiverAccountNoInput,
                                            supplierId = editSupplierIdInput,
                                            notes = editNotesInput,
                                            customerId = editCustomerIdInput,
                                            receiverName = editReceiverNameInput,
                                            receiverPhone = editReceiverPhoneInput
                                        )
                                        viewModel.updateTransactionStatus(updatedTx, editingTx!!.status)
                                        editingTx = null
                                    },
                                    onError = { err ->
                                        editPinErrorText = err
                                    }
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = if (lang == "BN") "অথবা পিন দিয়ে করুন:" else "Or verify using PIN:",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.outline
                                )
                            } else {
                                Text(
                                    text = if (lang == "BN") "মালিক বা ৪-ডিজিট সিকিউরিটি পিন দিন" else "Confirm 6-digit Security PIN",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            OutlinedTextField(
                                value = editPinCodeInput,
                                onValueChange = {
                                    if (it.length <= 6 && it.all { c -> c.isDigit() }) {
                                        editPinCodeInput = it
                                    }
                                },
                                placeholder = { Text("PIN") },
                                leadingIcon = {
                                    Icon(Icons.Default.Lock, contentDescription = "", tint = MaterialTheme.colorScheme.error)
                                },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.error,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                                )
                            )
                            if (editPinErrorText != null) {
                                Text(
                                    text = editPinErrorText!!,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                )
                            }

                            // Save Button
                            Button(
                                onClick = {
                                    val sar = editSarAmountInput.toDoubleOrNull() ?: 0.0
                                    val cr = editCustomerRateInput.toDoubleOrNull() ?: 0.0
                                    val sr = editSupplierRateInput.toDoubleOrNull() ?: 0.0
                                    val updatedTx = editingTx!!.copy(
                                        amountSar = sar,
                                        customerRate = cr,
                                        supplierRate = sr,
                                        amountBdt = sar * cr,
                                        receiverAccountType = editReceiverAccountTypeInput,
                                        receiverAccountNo = if (editReceiverAccountTypeInput == "Cash") "Cash Payout" else editReceiverAccountNoInput,
                                        supplierId = editSupplierIdInput,
                                        notes = editNotesInput,
                                        customerId = editCustomerIdInput,
                                        receiverName = editReceiverNameInput,
                                        receiverPhone = editReceiverPhoneInput
                                    )
                                    viewModel.updateTransactionStatus(updatedTx, editingTx!!.status)
                                    editingTx = null
                                },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                                enabled = editCustomerIdInput > 0 && editReceiverNameInput.isNotBlank() && editReceiverPhoneInput.isNotBlank() && editSarAmountInput.isNotBlank()
                            ) {
                                Text(
                                    text = if (lang == "BN") "সেভ" else "Save",
                                    style = MaterialTheme.typography.bodyLarge.copy(color = Color.White, fontWeight = FontWeight.Bold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
        }

        // --- DELETE TRANSACTION CONFIRM DIALOG ---
        if (deletingTx != null) {
            AlertDialog(
                onDismissRequest = { deletingTx = null },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = if (lang == "BN") "লেনদেন ডিলিট নিশ্চিতকরণ" else "Transaction Deletion",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                },
                text = {
                    Text(
                        text = if (lang == "BN") "আপনি কি নিশ্চিতভাবে এই লেনদেনটি মুছে দিতে চান?" 
                               else "Are you sure you want to delete this transaction record?",
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val txIdToDelete = deletingTx!!.id
                            viewModel.deleteTransaction(txIdToDelete)
                            deletingTx = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(if (lang == "BN") "হ্যাঁ, ডিলিট করুন" else "Yes, Confirm Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { deletingTx = null }) {
                        Text(if (lang == "BN") "বাতিল" else "Cancel")
                    }
                }
            )
        }

        // --- ACTIVE CALCULATOR OVERLAY DIALOG ---
        if (activeCalcTarget != CalcTargetTx.NONE) {
            val initial = when (activeCalcTarget) {
                CalcTargetTx.SAR_AMOUNT -> sarAmountInput
                CalcTargetTx.CUSTOMER_RATE -> customerRateInput
                CalcTargetTx.SUPPLIER_RATE -> supplierRateInput
                else -> ""
            }
            val title = when (activeCalcTarget) {
                CalcTargetTx.SAR_AMOUNT -> viewModel.t("saudi_amount")
                CalcTargetTx.CUSTOMER_RATE -> viewModel.t("customer_assigned_rate")
                CalcTargetTx.SUPPLIER_RATE -> viewModel.t("supplier_rate_tx")
                else -> ""
            }
            CalculatorDialog(
                initialValue = initial,
                title = title,
                lang = lang,
                onDismiss = { activeCalcTarget = CalcTargetTx.NONE },
                onConfirm = { result ->
                    when (activeCalcTarget) {
                        CalcTargetTx.SAR_AMOUNT -> sarAmountInput = result
                        CalcTargetTx.CUSTOMER_RATE -> customerRateInput = result
                        CalcTargetTx.SUPPLIER_RATE -> supplierRateInput = result
                        else -> {}
                    }
                    activeCalcTarget = CalcTargetTx.NONE
                }
            )
        }

        // --- INTERACTIVE DIGITAL INVOICE RECEIPT DIALOG ---
        if (viewingReceiptTx != null) {
            val tx = viewingReceiptTx!!
            val customer = customers.find { it.id == tx.customerId }
            val supplier = suppliers.find { it.id == tx.supplierId }
            DigitalReceiptDialog(
                tx = tx,
                customer = customer,
                supplier = supplier,
                lang = lang,
                onDismiss = { viewingReceiptTx = null },
                foreignCur = foreignCur
            )
        }
    }
}

// --- DYNAMIC REUSABLE CORE HELPER WIDGETS ---

@Composable
fun KpiIndicatorCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    containerColor: Color,
    textColor: Color
) {
    Card(
        modifier = Modifier
            .width(180.dp)
            .height(100.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = textColor.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = icon,
                    contentDescription = "",
                    tint = textColor,
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = textColor.copy(alpha = 0.64f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun TransactionPremiumCard(
    tx: RemittanceTransaction,
    customer: Customer?,
    supplier: Supplier?,
    isExpanded: Boolean,
    lang: String,
    currencyFormatter: DecimalFormat,
    dateFormatter: SimpleDateFormat,
    onClick: () -> Unit,
    onUpdateStatus: (RemittanceTransaction, String) -> Unit,
    onEdit: (RemittanceTransaction) -> Unit,
    onDelete: (RemittanceTransaction) -> Unit,
    onReceipt: () -> Unit,
    foreignCur: String = "SAR"
) {
    val isPending = tx.status != "Delivered" && tx.status != "Cancelled"
    val isCancelled = tx.status == "Cancelled"
    val cardColor = when (tx.status) {
        "Delivered" -> Color(0xFFF9FDF9)
        "Cancelled" -> Color(0xFFFFFAFA)
        else -> MaterialTheme.colorScheme.surface
    }

    val arrowRotation by animateFloatAsState(targetValue = if (isExpanded) 180f else 0f, label = "rotateChevron")

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("transaction_item_${tx.id}"),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (isExpanded) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Header: Recipient Initials avatar + Payout Status Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Receiver Avatar
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(
                                when (tx.status) {
                                    "Delivered" -> Color(0xFFE8F5E9)
                                    "Cancelled" -> Color(0xFFFFEBEE)
                                    else -> MaterialTheme.colorScheme.secondaryContainer
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tx.receiverName.firstOrNull()?.toString()?.uppercase() ?: "R",
                            fontWeight = FontWeight.Black,
                            color = when (tx.status) {
                                "Delivered" -> Color(0xFF2E7D32)
                                "Cancelled" -> Color(0xFFC62828)
                                else -> MaterialTheme.colorScheme.primary
                            }
                        )
                    }

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = tx.receiverName.ifBlank { "No Recipient Name" },
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "(${customer?.name ?: "Unknown"})",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            text = "${tx.receiverAccountType}: ${tx.receiverAccountNo}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                // Status pill Custom Widget
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = when (tx.status) {
                        "Delivered" -> Color(0xFFE8F5E9)
                        "Cancelled" -> Color(0xFFFFEBEE)
                        else -> Color(0xFFFFFBEE)
                    },
                    border = BorderStroke(
                        width = 1.dp,
                        color = when (tx.status) {
                            "Delivered" -> Color(0xFF81C784)
                            "Cancelled" -> Color(0xFFE57373)
                            else -> Color(0xFFFFD54F)
                        }
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(
                                    when (tx.status) {
                                        "Delivered" -> Color(0xFF2E7D32)
                                        "Cancelled" -> Color(0xFFC62828)
                                        else -> Color(0xFFF57C00)
                                    }
                                )
                        )
                        Text(
                            text = when (tx.status) {
                                "Delivered" -> if (lang == "BN") "বিতরিত" else "Delivered"
                                "Cancelled" -> if (lang == "BN") "বাতিল" else "Cancelled"
                                else -> if (lang == "BN") "অপেক্ষমান" else "Pending"
                            },
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = when (tx.status) {
                                    "Delivered" -> Color(0xFF1B5E20)
                                    "Cancelled" -> Color(0xFFC62828)
                                    else -> Color(0xFFE65100)
                                }
                            )
                        )
                    }
                }
            }

            // Monetary values & expand action chevron
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "SAR ${currencyFormatter.format(tx.amountSar)}",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold)
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.TrendingFlat,
                                contentDescription = "",
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier
                                    .padding(horizontal = 6.dp)
                                    .size(16.dp)
                            )
                            Text(
                                text = "৳ ${currencyFormatter.format(tx.amountBdt)}",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFF2E7D32)
                                )
                            )
                        }
                        Text(
                            text = dateFormatter.format(tx.timestamp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                IconButton(onClick = onClick) {
                    Icon(
                        imageVector = Icons.Default.ExpandMore,
                        contentDescription = "Expand",
                        modifier = Modifier.rotate(arrowRotation)
                    )
                }
            }

            // Expanded customized section details
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Profit Arbitrage breakdown card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = if (lang == "BN") "📊 এক্সচেঞ্জ ডিফারেন্স (Exchange Margin):" else "📊 Arbitrage Exchange Matrix:",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                )
                                Text(
                                    text = "${tx.receiverAccountType} payout",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                            Text(text = "• Cust Rate (গ্রাহক বিনিময় হার): ${tx.customerRate} BDT/SAR", style = MaterialTheme.typography.bodySmall)
                            Text(text = "• Cost Rate (ওয়ালেটে ক্রয় রেট): ${tx.supplierRate} BDT/SAR (${supplier?.name ?: "Market Pool"})", style = MaterialTheme.typography.bodySmall)
                            
                            val netProfitBdt = tx.getProfitBdt()
                            val netProfitSar = tx.getProfitSar()
                            val profitMarginPct = if (tx.customerRate > 0) ((tx.supplierRate - tx.customerRate) / tx.customerRate) * 100 else 0.0

                            Divider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = if (lang == "BN") "নেট আনুমানিক মুনাফা:" else "Net Estimated Yield:",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                                )
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "৳ ${currencyFormatter.format(netProfitBdt)}",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Black),
                                        color = if (netProfitBdt >= 0) Color(0xFF2E7D32) else Color(0xFFC62828)
                                    )
                                    Text(
                                        text = "(${currencyFormatter.format(netProfitSar)} ${foreignCur}| Margin: ${String.format(Locale.getDefault(), "%.2f", profitMarginPct)}%)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (netProfitBdt >= 0) Color(0xFF2E7D32) else Color(0xFFC62828)
                                    )
                                }
                            }
                        }
                    }

                    // Recipient Particular Detail fields
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = if (lang == "BN") "প্রাপকের ডেলিভারি বিবরণ:" else "Bangladesh Receiver Particulars:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Text(text = "Name: ${tx.receiverName.ifBlank { "N/A" }}", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                        Text(text = "Phone: ${tx.receiverPhone.ifBlank { "N/A" }}", style = MaterialTheme.typography.bodySmall)
                        if (tx.notes.isNotBlank()) {
                            Surface(
                                modifier = Modifier.padding(top = 4.dp),
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                            ) {
                                Row(modifier = Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Default.Info, contentDescription = "", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                    Text(
                                        text = "Notes: ${tx.notes}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }

                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    if (isPending) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = if (lang == "BN") "⚙️ কুইক স্ট্যাটাস সংশোধন:" else "⚙️ Quick Status Actions:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Delivered action button
                                Button(
                                    onClick = { onUpdateStatus(tx, "Delivered") },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE8F5E9), contentColor = Color(0xFF2E7D32)),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = "", modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(text = if (lang == "BN") "বিতরণ" else "Deliver", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }

                                // Cancelled action button
                                Button(
                                    onClick = { onUpdateStatus(tx, "Cancelled") },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFEBEE), contentColor = Color(0xFFC62828)),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.Cancel, contentDescription = "", modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(text = if (lang == "BN") "বাতিল" else "Cancel", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }

                    // Bottom Row: Clean View Receipt button spanning full-bleed
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Button(
                            onClick = onReceipt,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), contentColor = MaterialTheme.colorScheme.primary),
                            contentPadding = PaddingValues(vertical = 10.dp)
                        ) {
                            Icon(Icons.Default.Receipt, contentDescription = "", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = if (lang == "BN") "রসিদ" else "Receipt", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TransactionCompactItemRow(
    tx: RemittanceTransaction,
    customerName: String,
    isExpanded: Boolean,
    lang: String,
    dateFormatter: SimpleDateFormat,
    onClick: () -> Unit,
    onReceipt: () -> Unit
) {
    val statusColor = when (tx.status) {
        "Delivered" -> Color(0xFF2E7D32)
        "Cancelled" -> Color(0xFFC62828)
        else -> Color(0xFFE65100)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Name & phone compact
                Column(modifier = Modifier.weight(1.3f)) {
                    Text(
                        text = tx.receiverName,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "$customerName • ${dateFormatter.format(tx.timestamp).substringBefore(",")}",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Amount Flow
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text(
                        text = "SAR ${tx.amountSar.toInt()} ➔ ৳${tx.amountBdt.toInt()}",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black),
                        maxLines = 1
                    )
                    Text(
                        text = tx.receiverAccountType,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1
                    )
                }

                // Compact status bullet dot indicator
                Box(
                    modifier = Modifier
                        .padding(start = 10.dp)
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Acc No: ${tx.receiverAccountNo}", style = MaterialTheme.typography.labelSmall)
                        Text("Notes: ${tx.notes.ifBlank { "N/A" }}", style = MaterialTheme.typography.labelSmall)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Rate: customer ${tx.customerRate} | supplier ${tx.supplierRate}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        TextButton(
                            onClick = onReceipt,
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.height(24.dp)
                        ) {
                            Icon(Icons.Default.Receipt, null, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(if (lang == "BN") "রসিদ" else "Receipt", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DigitalReceiptDialog(
    tx: RemittanceTransaction,
    customer: Customer?,
    supplier: Supplier?,
    lang: String,
    onDismiss: () -> Unit,
    foreignCur: String = "SAR"
) {
    val currencyFormatter = remember { DecimalFormat("#,##0.00") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .padding(16.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Header Banner with Invoice stamp
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                            .padding(16.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            Icon(
                                imageVector = Icons.Default.ReceiptLong,
                                contentDescription = "",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (lang == "BN") "SAFA রসিদ বিবরণী" else "SAFA LEDGER RECEIPT",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "TXID: #REMIT-${tx.id}",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    // Sender Details block
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = if (lang == "BN") "প্রেরক (Sender Customer)" else "Sender Customer Details",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)
                        )
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = customer?.name ?: "Unknown Customer",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = customer?.phone ?: "No Registered Phone",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Bangladesh delivery details
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = if (lang == "BN") "প্রাপক" else "Recipient Payout Details",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)
                        )
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                val rxName = tx.receiverName.trim()
                                val dummyTerms = listOf("recipient", "due payment", "due", "payment", "n/a", "না")
                                val hasValidReceiverName = rxName.isNotBlank() && !dummyTerms.any { rxName.contains(it, ignoreCase = true) }

                                val rxPhone = tx.receiverPhone.trim()
                                val hasValidReceiverPhone = rxPhone.isNotBlank() && !dummyTerms.any { rxPhone.contains(it, ignoreCase = true) } && rxPhone != "N/A"

                                if (hasValidReceiverName) {
                                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                        Text(if (lang == "BN") "নাম:" else "Name:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                        Text(rxName, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                                    }
                                }
                                if (hasValidReceiverPhone) {
                                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                        Text(if (lang == "BN") "মোবাইল:" else "Phone:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                        Text(rxPhone, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Text(if (lang == "BN") "চ্যানেল:" else "Payout Channel:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                    Text(tx.receiverAccountType, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.ExtraBold), color = MaterialTheme.colorScheme.primary)
                                }
                                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Text(if (lang == "BN") "হিসাব নম্বর:" else "Account/Number:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                    Text(tx.receiverAccountNo, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                                }
                                if (tx.notes.isNotBlank()) {
                                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                        Text(if (lang == "BN") "নোট:" else "Note:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                        Text(tx.notes, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f).padding(start = 8.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                                    }
                                }
                            }
                        }
                    }

                    // Billing exchange calculations
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = if (lang == "BN") "হিসাব বিবরণী" else "Exchange Calculations Matrix",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)
                        )
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Text(if (lang == "BN") "রিয়াল পরিমাণ:" else "SAR Amount Sent:", style = MaterialTheme.typography.bodyMedium)
                                    Text("SAR ${currencyFormatter.format(tx.amountSar)}", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                                }
                                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Text(if (lang == "BN") "বিনিময় হার:" else "Customer Exchange Rate:", style = MaterialTheme.typography.bodyMedium)
                                    Text("৳ ${tx.customerRate} BDT/SAR", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                                }
                                Divider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (lang == "BN") "প্রদেয় টাকা:" else "Payable BDT:",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "৳ ${currencyFormatter.format(tx.amountBdt)}",
                                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                                        color = Color(0xFF2E7D32)
                                    )
                                }
                            }
                        }
                    }

                    // Receipt barcode illustration & footer metadata
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = when (tx.status) {
                                "Delivered" -> Color(0xFFE8F5E9)
                                "Cancelled" -> Color(0xFFFFEBEE)
                                else -> Color(0xFFFFF3E0)
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when (tx.status) {
                                                "Delivered" -> Color(0xFF2E7D32)
                                                "Cancelled" -> Color(0xFFC62828)
                                                else -> Color(0xFFF57C00)
                                            }
                                        )
                                )
                                Text(
                                    text = when (tx.status) {
                                        "Delivered" -> if (lang == "BN") "বিতরণ সম্পন্ন" else "Transaction Delivered"
                                        "Cancelled" -> if (lang == "BN") "বাতিলকৃত" else "Cancelled Record"
                                        else -> if (lang == "BN") "অপেক্ষমান" else "Pending Processing"
                                    },
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        color = when (tx.status) {
                                            "Delivered" -> Color(0xFF1B5E20)
                                            "Cancelled" -> Color(0xFFC62828)
                                            else -> Color(0xFFE65100)
                                        }
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Stylish Barcode mockup drawn via Canvas lines!
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(36.dp)
                        ) {
                            val barsCount = 42
                            val barSpacing = size.width / barsCount
                            val rand = Random(tx.id.toLong() + 1042)
                            for (i in 0 until barsCount) {
                                val thickness = if (rand.nextBoolean()) 4f else 8f
                                val barHeight = size.height * (0.85f + rand.dashFactor() * 0.15f)
                                drawLine(
                                    color = Color.DarkGray,
                                    start = Offset(i * barSpacing, 0f),
                                    end = Offset(i * barSpacing, barHeight),
                                    strokeWidth = thickness
                                )
                            }
                        }

                        Text(
                            text = SimpleDateFormat("dd-MM-yyyy hh:mm:ss a", Locale.getDefault()).format(tx.timestamp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        Text(
                            text = if (lang == "BN") "ডিজিটাল লেজার ভেরিফাইড রসিদ। কোনো স্বাক্ষরের প্রয়োজন নেই।" else "Digitally Verified Ledger Slip. No Physical Stamp Required.",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = MaterialTheme.colorScheme.outline,
                            textAlign = TextAlign.Center
                        )
                    }

                    // Action dismiss trigger & WhatsApp Voucher share
                    val context = androidx.compose.ui.platform.LocalContext.current
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                val voucherText = """
                                    📋 *SAFA Remittance Voucher*
                                    ----------------------------------------
                                    🆔 *Tx Ref:* #${String.format("%06d", tx.id)}
                                    👤 *Customer:* ${customer?.name ?: "N/A"}
                                    📱 *Phone:* ${customer?.phone ?: "N/A"}
                                    
                                    📍 *Receiver Details:*
                                    • Name: ${tx.receiverName}
                                    • Phone: ${tx.receiverPhone}
                                    • Account: ${tx.receiverAccountType} (${tx.receiverAccountNo})
                                    
                                    💵 *Financial Details:*
                                    • Sent Amount: ${foreignCur}${tx.amountSar}
                                    • Rate: ৳ ${tx.customerRate} BDT/SAR
                                    • Payable BDT: ৳ ${DecimalFormat("#,##0").format(tx.amountBdt)} BDT
                                    • Status: ${tx.status}
                                    ----------------------------------------
                                    ✨ *Digitally Verified Ledger Slip*
                                """.trimIndent()

                                try {
                                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                        data = android.net.Uri.parse("https://api.whatsapp.com/send?phone=${tx.receiverPhone.replace("+", "").replace(" ", "")}&text=${android.net.Uri.encode(voucherText)}")
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(android.content.Intent.EXTRA_TEXT, voucherText)
                                    }
                                    context.startActivity(android.content.Intent.createChooser(shareIntent, "Share Remittance Voucher"))
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (lang == "BN") "হোয়াটসঅ্যাপ ভাউচার" else "WhatsApp Voucher", color = Color.White, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (lang == "BN") "বন্ধ করুন" else "Close Receipt")
                        }
                    }
                }
            }
        }
}

// Inline helper extension for barcode layout randomized height offsets smoothly
private fun Random.dashFactor(): Float = nextFloat()

private enum class CalcTargetTx {
    NONE, SAR_AMOUNT, CUSTOMER_RATE, SUPPLIER_RATE
}
