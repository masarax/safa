package com.safa.account.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safa.account.ui.viewmodel.AppScreen
import com.safa.account.ui.viewmodel.HundiViewModel
import java.text.DecimalFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.text.SimpleDateFormat
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.platform.LocalContext
import java.io.File
import java.io.FileWriter

data class ShortcutIconItem(
    val title: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val iconColor: Color,
    val bgTint: Color
)

sealed class UnifiedLedgerEntry {
    abstract val key: String
    abstract val timestamp: Long
    abstract val name: String
    abstract val details: String
    abstract val typeLabel: String
    abstract val amountBdt: Double
    abstract val amountSar: Double
    abstract val status: String
    abstract val notes: String

    data class CustomerTx(
        val tx: com.safa.account.data.model.RemittanceTransaction,
        override val name: String,
        override val timestamp: Long = tx.timestamp,
        override val key: String = "cust_tx_${tx.id}",
        override val details: String = "${tx.receiverAccountType} -> ${tx.receiverAccountNo}",
        override val typeLabel: String = "কাস্টমার বিক্রয়",
        override val amountBdt: Double = tx.amountBdt,
        override val amountSar: Double = tx.amountSar,
        override val status: String = tx.status,
        override val notes: String = tx.notes
    ) : UnifiedLedgerEntry()

    data class SupplierTx(
        val dep: com.safa.account.data.model.SupplierDeposit,
        override val name: String,
        override val timestamp: Long = dep.timestamp,
        override val key: String = "supp_tx_${dep.id}",
        override val details: String = dep.notes,
        override val typeLabel: String = when (dep.transactionType) {
            "SAR_GIVEN" -> "রিয়াল প্রদান (ডিপোজিট)"
            "SAR_DEPOSIT" -> "রিয়াল প্রদান (ডিপোজিট)"
            "SAR_RECEIVED" -> "রিয়াল গ্রহণ (উত্তোলন)"
            "SAR_SETTLEMENT" -> "রিয়াল গ্রহণ (উত্তোলন)"
            "BDT_WITHDRAW" -> "তহবিল উত্তোলন"
            else -> "তহবিল বিবরণ"
        },
        override val amountBdt: Double = dep.amountBdt,
        override val amountSar: Double = dep.amountSar,
        override val status: String = "Delivered",
        override val notes: String = dep.notes
    ) : UnifiedLedgerEntry()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: HundiViewModel,
    modifier: Modifier = Modifier
) {
    val stats by viewModel.financialStats.collectAsStateWithLifecycle()
    val rawRates by viewModel.currentRates.collectAsStateWithLifecycle()
    val operator by viewModel.currentOperator.collectAsStateWithLifecycle()
    val lang by viewModel.currentLanguage.collectAsStateWithLifecycle()
    val customAppName by viewModel.customAppName.collectAsStateWithLifecycle()
    val expensesIncomes by viewModel.expensesIncomes.collectAsStateWithLifecycle()
    val customers by viewModel.customers.collectAsStateWithLifecycle()
    val suppliers by viewModel.suppliers.collectAsStateWithLifecycle()
    val supplierDeposits by viewModel.supplierDeposits.collectAsStateWithLifecycle()
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()
    val foreignCurrency by viewModel.selectedForeignCurrency.collectAsStateWithLifecycle()
    val localCurrency by viewModel.selectedLocalCurrency.collectAsStateWithLifecycle()

    val currencyFormatter = remember { DecimalFormat("#,##0") }

    // Interactive Search, Filter, and Custom Reports downloadable state
    var searchQuery by remember { mutableStateOf("") }
    var currentFilterType by remember { mutableStateOf("ALL") } // "ALL", "HAS_BALANCE", "A_Z"
    var showFilterDialog by remember { mutableStateOf(false) }
    var showReportsDialog by remember { mutableStateOf(false) }
    var showDownloadSuccessDialog by remember { mutableStateOf(false) }
    var generatedReportText by remember { mutableStateOf("") }
    var generatedReportPath by remember { mutableStateOf("") }
    var selectedReportPeriod by remember { mutableStateOf("MONTHLY") } // "TODAY", "WEEKLY", "MONTHLY", "YEARLY", "ALL"
    var selectedReportFormat by remember { mutableStateOf("PDF") } // "PDF", "EXCEL", "IMAGE"
    var selectedDashboardTab by remember { mutableStateOf(0) }

    // Forms to change rates
    var customerRateInput by remember { mutableStateOf("") }
    var supplierRateInput by remember { mutableStateOf("") }
    var isEditingRates by remember { mutableStateOf(false) }

    // Periodic filters ("This Month Only" vs "All Time Total")
    var selectedPeriodMonthOnly by remember { mutableStateOf(true) }
    // Capsule filters ("ALL", "INCOME", "EXPENSE")
    var activeLedgerFilter by remember { mutableStateOf("ALL") }

    // Dialog form triggers
    var showAddLedgerDialog by remember { mutableStateOf(false) }

    // Date/Time values for new record form
    var txTimestampInput by remember { mutableStateOf(System.currentTimeMillis()) }
    var txTitleInput by remember { mutableStateOf("") }
    var txAmountInput by remember { mutableStateOf("") }
    var txIsExpenseInput by remember { mutableStateOf(true) }
    var txCategoryInput by remember { mutableStateOf("Tea & Food") }

    LaunchedEffect(rawRates) {
        rawRates?.let {
            customerRateInput = it.customerRate.toString()
            supplierRateInput = it.supplierRate.toString()
        }
    }

    // Dynamic month name in Bengali or English
    val monthNameLabel = remember {
        val cal = Calendar.getInstance()
        if (lang == "BN") {
            when (cal.get(Calendar.MONTH)) {
                Calendar.JANUARY -> "জানুয়ারী"
                Calendar.FEBRUARY -> "ফেব্রুয়ারী"
                Calendar.MARCH -> "মার্চ"
                Calendar.APRIL -> "এপ্রিল"
                Calendar.MAY -> "মে"
                Calendar.JUNE -> "জুন"
                Calendar.JULY -> "জুলাই"
                Calendar.AUGUST -> "আগস্ট"
                Calendar.SEPTEMBER -> "সেপ্টেম্বর"
                Calendar.OCTOBER -> "অক্টোবর"
                Calendar.NOVEMBER -> "নভেম্বর"
                Calendar.DECEMBER -> "ডিসেম্বর"
                else -> "জানুয়ারী"
            }
        } else {
            SimpleDateFormat("MMMM", Locale.US).format(cal.time)
        }
    }

    // Filter elements in expensesIncomes
    val currentCal = Calendar.getInstance()
    val curMon = currentCal.get(Calendar.MONTH)
    val curYr = currentCal.get(Calendar.YEAR)

    // Helper functions for Bengali locale numbers
    fun toBnNum(num: Int): String {
        if (lang != "BN") return num.toString()
        val bnDigits = listOf('০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯')
        return num.toString().map { if (it.isDigit()) bnDigits[it - '0'] else it }.joinToString("")
    }

    fun toBnFloatString(amount: Double): String {
        val rounded = amount.toInt()
        return currencyFormatter.format(rounded)
    }

    val filteredPeriodExpenses = remember(expensesIncomes, selectedPeriodMonthOnly) {
        if (selectedPeriodMonthOnly) {
            expensesIncomes.filter {
                val c = Calendar.getInstance().apply { timeInMillis = it.timestamp }
                c.get(Calendar.MONTH) == curMon && c.get(Calendar.YEAR) == curYr
            }
        } else {
            expensesIncomes
        }
    }

    val periodIncomeBdt = remember(filteredPeriodExpenses) {
        filteredPeriodExpenses.filter { !it.isExpense }.sumOf { if (it.currency == "SAR") it.amount * 32.5 else it.amount }
    }
    val periodExpenseBdt = remember(filteredPeriodExpenses) {
        filteredPeriodExpenses.filter { it.isExpense }.sumOf { if (it.currency == "SAR") it.amount * 32.5 else it.amount }
    }
    val periodBalanceBdt = remember(periodIncomeBdt, periodExpenseBdt) {
        periodIncomeBdt - periodExpenseBdt
    }

    val filteredLedgerItems = remember(filteredPeriodExpenses, activeLedgerFilter) {
        when (activeLedgerFilter) {
            "INCOME" -> filteredPeriodExpenses.filter { !it.isExpense }
            "EXPENSE" -> filteredPeriodExpenses.filter { it.isExpense }
            else -> filteredPeriodExpenses
        }
    }

    val finalCustomersList = remember(customers) {
        if (customers.isNotEmpty()) {
            customers
        } else {
            // Visual placeholder list mirroring screenshot when fresh
            listOf(
                com.safa.account.data.model.Customer(id = 991, name = "রানা ভাই", phone = "01700112233", address = "১ ঘণ্টা"),
                com.safa.account.data.model.Customer(id = 992, name = "হাসেম ভাই", phone = "01700112244", address = "২ ঘণ্টা"),
                com.safa.account.data.model.Customer(id = 993, name = "Fahim Rana", phone = "01511223344", address = "১ দিন"),
                com.safa.account.data.model.Customer(id = 994, name = "নাজমুল চাচা", phone = "01999887766", address = "২ দিন")
            )
        }
    }

    val filteredCustomersList = remember(finalCustomersList, searchQuery, currentFilterType, transactions) {
        var list = if (searchQuery.isBlank()) {
            finalCustomersList
        } else {
            finalCustomersList.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                it.phone.contains(searchQuery, ignoreCase = true) ||
                it.address.contains(searchQuery, ignoreCase = true)
            }
        }
        when (currentFilterType) {
            "HAS_BALANCE" -> {
                list.filter { customer ->
                    val customerTxs = transactions.filter { it.customerId == customer.id }
                    val totalDueAmount = customerTxs.sumOf { it.amountSar } - customerTxs.sumOf { it.sarCollected }
                    java.lang.Math.abs(totalDueAmount) > 0.05
                }
            }
            "A_Z" -> {
                list.sortedBy { it.name }
            }
            else -> list
        }
    }

    val unifiedTransactionsList = remember(transactions, supplierDeposits, customers, suppliers) {
        val items = mutableListOf<UnifiedLedgerEntry>()
        transactions.forEach { tx ->
            val customerName = customers.find { it.id == tx.customerId }?.name ?: "Unknown Customer"
            items.add(UnifiedLedgerEntry.CustomerTx(tx, customerName))
        }
        supplierDeposits.forEach { dep ->
            val supplierName = suppliers.find { it.id == dep.supplierId }?.name ?: "Unknown Supplier"
            items.add(UnifiedLedgerEntry.SupplierTx(dep, supplierName))
        }
        items.sortedByDescending { it.timestamp }
    }

    val filteredUnifiedTransactionsList = remember(unifiedTransactionsList, searchQuery) {
        if (searchQuery.isBlank()) {
            unifiedTransactionsList
        } else {
            unifiedTransactionsList.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                it.details.contains(searchQuery, ignoreCase = true) ||
                it.notes.contains(searchQuery, ignoreCase = true) ||
                it.typeLabel.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    val groupedUnifiedTransactionsByDate = remember(filteredUnifiedTransactionsList, lang) {
        val itemsToShow = filteredUnifiedTransactionsList.take(5)
        itemsToShow.groupBy { entry ->
            val sdf = SimpleDateFormat("dd MMMM, yyyy", if (lang == "BN") Locale("bn") else Locale.US)
            sdf.format(Date(entry.timestamp))
        }
    }

    val isDarkMode by viewModel.isDarkMode.collectAsStateWithLifecycle()
    val screenBgColor = MaterialTheme.colorScheme.background
    val cardBgColor = if (isDarkMode) Color(0xFF1A1F2D) else Color(0xFFFFFFFF)
    val cardBorder = if (isDarkMode) Color(0xFF2E3748) else Color(0xFFE5E7EB) // Light, clean gray card borders
    val textPrimary = if (isDarkMode) Color.White else Color(0xFF111111)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(screenBgColor)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 8.dp)
        ) {
            // --- TALLYKHATA STYLE COLOURED SHORTCUTS GRID (8 SECTIONS for Remittance Business) ---
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 1.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBgColor),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, cardBorder)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        val gridItems = listOf(
                            // Title, Icon, IconColor, Circle/Background tint color
                            ShortcutIconItem(if (lang == "BN") "লেনদেন সমূহ" else "Transactions", Icons.Default.Send, Color(0xFFE53935), Color(0xFFFFEBEE)),
                            ShortcutIconItem(if (lang == "BN") "আয়/ব্যায়" else "Income/Expense", Icons.Default.AccountBalanceWallet, Color(0xFFFB8C00), Color(0xFFFFF3E0)),
                            ShortcutIconItem(if (lang == "BN") "সাপ্লায়ার" else "Supplier", Icons.Default.CompareArrows, Color(0xFF43A047), Color(0xFFE8F5E9)),
                            ShortcutIconItem(if (lang == "BN") "আজকের রেট" else "Exchange Rates", Icons.Default.TrendingUp, Color(0xFFE91E63), Color(0xFFFCE4EC)),
                            ShortcutIconItem(if (lang == "BN") "রিয়াল স্টক" else "Riyal Stock", Icons.Default.MonetizationOn, Color(0xFFFBC02D), Color(0xFFFFFDE7)),
                            ShortcutIconItem(if (lang == "BN") "লাভ-ক্ষতি" else "Profit/Loss", Icons.Default.Assessment, Color(0xFF00796B), Color(0xFFE0F2F1)),
                            ShortcutIconItem(if (lang == "BN") "সকল লেনদেন" else "All Trans.", Icons.Default.Receipt, Color(0xFF3F51B5), Color(0xFFE8EAF6)),
                            ShortcutIconItem(if (lang == "BN") "গ্রাহকগণ" else "Customers", Icons.Default.People, Color(0xFF8D6E63), Color(0xFFEFEBE9))
                        )

                        val rows = gridItems.chunked(4)
                        rows.forEachIndexed { rIdx, rowItems ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                rowItems.forEach { item ->
                                    val (title, icon, iconColor, bgTint) = item
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                when (title) {
                                                    "গ্রাহকগণ", "Customers" -> viewModel.navigateTo(AppScreen.CUSTOMERS)
                                                    "লেনদেন সমূহ", "Transactions" -> viewModel.navigateTo(AppScreen.TRANSACTIONS)
                                                    "রিয়াল স্টক", "Riyal Stock" -> viewModel.navigateTo(AppScreen.WALLET)
                                                    "সকল লেনদেন", "All Trans." -> viewModel.navigateTo(AppScreen.TRANSACTIONS)
                                                    "সাপ্লায়ার", "Supplier" -> viewModel.navigateTo(AppScreen.SUPPLIERS)
                                                    "আয়/ব্যায়", "Income/Expense" -> viewModel.navigateTo(AppScreen.EXPENSES)
                                                    "আজকের রেট", "Exchange Rates" -> viewModel.navigateTo(AppScreen.SETTINGS)
                                                    "লাভ-ক্ষতি", "Profit/Loss" -> viewModel.navigateTo(AppScreen.REPORTS)
                                                }
                                            }
                                            .padding(vertical = 4.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(46.dp)
                                                .clip(CircleShape)
                                                .background(if (isDarkMode) Color(0xFF2C2C2C) else bgTint)
                                                .border(1.dp, iconColor.copy(alpha = 0.15f), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = icon,
                                                contentDescription = title,
                                                tint = iconColor,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = title,
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                                            color = if (isDarkMode) Color(0xFFE0E0E0) else Color(0xFF444444),
                                            textAlign = TextAlign.Center,
                                            maxLines = 1,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // --- TALLYKHATA STYLE ROUNDED SEARCH BAR & ACTIONS PANEL ---
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 1.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Sleek, compact search box
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .background(cardBgColor, RoundedCornerShape(20.dp))
                            .border(1.dp, cardBorder.copy(alpha = 0.8f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = if (isDarkMode) Color.LightGray else Color.Gray,
                                modifier = Modifier.size(16.dp)
                            )
                            val textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp)
                            androidx.compose.foundation.text.BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                singleLine = true,
                                textStyle = textStyle.copy(color = textPrimary),
                                decorationBox = { innerTextField ->
                                    if (searchQuery.isEmpty()) {
                                        Text(
                                            text = if (lang == "BN") "লেনদেন খুঁজুন..." else "Search transactions...",
                                            style = textStyle,
                                            color = Color.Gray
                                        )
                                    }
                                    innerTextField()
                                },
                                modifier = Modifier.weight(1f).testTag("dashboard_search_input")
                            )
                            if (searchQuery.isNotEmpty()) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear",
                                    tint = if (isDarkMode) Color.LightGray else Color.Gray,
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clickable { searchQuery = "" }
                                )
                            }
                        }
                    }

                    // Compact filter tune icon
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(cardBgColor, CircleShape)
                            .border(1.dp, cardBorder, CircleShape)
                            .clickable { showFilterDialog = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Filter",
                            tint = if (currentFilterType != "ALL") MaterialTheme.colorScheme.primary else (if (isDarkMode) Color.White else Color.DarkGray),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Compact download/report icon
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(cardBgColor, CircleShape)
                            .border(1.dp, cardBorder, CircleShape)
                            .clickable { viewModel.navigateTo(AppScreen.REPORTS) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Export",
                            tint = if (isDarkMode) Color.White else Color.DarkGray,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // --- UNIFIED RECENT TRANSACTION HISTORY LIST ---
            item {
                Text(
                    text = if (lang == "BN") "রিসেন্ট লেনদেন খাতা" else "Recent Transaction History",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isDarkMode) Color.White else Color(0xFF222222)
                    ),
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                )
            }

            if (filteredUnifiedTransactionsList.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.ReceiptLong, contentDescription = "", tint = Color.Gray.copy(alpha = 0.5f), modifier = Modifier.size(40.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (lang == "BN") "কোনো লেনদেন পাওয়া যায়নি।" else "No transactions found.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            } else {
                groupedUnifiedTransactionsByDate.forEach { (dateHeader, entriesForDate) ->
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarToday,
                                contentDescription = "",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = dateHeader,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 1.dp)
                                .testTag("recent_transactions_group_${dateHeader}"),
                            colors = CardDefaults.cardColors(containerColor = cardBgColor),
                            shape = RoundedCornerShape(12.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, cardBorder)
                        ) {
                            Column {
                                entriesForDate.forEachIndexed { itemIndex, unifiedEntry ->
                                    val actsAsCustomer = unifiedEntry is UnifiedLedgerEntry.CustomerTx
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                when (unifiedEntry) {
                                                    is UnifiedLedgerEntry.CustomerTx -> {
                                                        viewModel.selectCustomerProfile(unifiedEntry.tx.customerId)
                                                    }
                                                    is UnifiedLedgerEntry.SupplierTx -> {
                                                        viewModel.selectSupplierProfile(unifiedEntry.dep.supplierId)
                                                    }
                                                }
                                            }
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        if (actsAsCustomer) Color(0xFFE3F2FD) else Color(0xFFE8F5E9)
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = if (actsAsCustomer) Icons.Default.SendToMobile else Icons.Default.SwapHoriz,
                                                    contentDescription = "",
                                                    tint = if (actsAsCustomer) Color(0xFF1565C0) else Color(0xFF2E7D32),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                            
                                            Column {
                                                Text(
                                                    text = unifiedEntry.name,
                                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                                    color = textPrimary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                
                                                val rateText = when (unifiedEntry) {
                                                    is UnifiedLedgerEntry.CustomerTx -> {
                                                        if (lang == "BN") "রেট: ${unifiedEntry.tx.customerRate}" else "Rate: ${unifiedEntry.tx.customerRate}"
                                                    }
                                                    is UnifiedLedgerEntry.SupplierTx -> {
                                                        val dep = unifiedEntry.dep
                                                        if (dep.transactionType == "BDT_SETTLEMENT") {
                                                            if (lang == "BN") "বকেয়া পরিশোধ" else "Settle Dues"
                                                        } else {
                                                            if (lang == "BN") "রেট: ${dep.rate}" else "Rate: ${dep.rate}"
                                                        }
                                                    }
                                                }
                                                val timeStr = SimpleDateFormat("hh:mm a", Locale.US).format(Date(unifiedEntry.timestamp))
                                                Text(
                                                    text = "$rateText • $timeStr",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (isDarkMode) Color.LightGray else Color.Gray
                                                )
                                            }
                                        }
                                        
                                        val sarAmountText = when (unifiedEntry) {
                                            is UnifiedLedgerEntry.CustomerTx -> {
                                                "$foreignCurrency ${DecimalFormat("#,##0").format(unifiedEntry.tx.amountSar)}"
                                            }
                                            is UnifiedLedgerEntry.SupplierTx -> {
                                                val dep = unifiedEntry.dep
                                                if (dep.transactionType == "BDT_SETTLEMENT") {
                                                    "$localCurrency ${DecimalFormat("#,##0").format(dep.paidBdt)}"
                                                } else {
                                                    "$foreignCurrency ${DecimalFormat("#,##0").format(dep.amountSar)}"
                                                }
                                            }
                                        }
                                        
                                        val amountColor = when (unifiedEntry) {
                                            is UnifiedLedgerEntry.CustomerTx -> {
                                                val dueSar = unifiedEntry.tx.amountSar - unifiedEntry.tx.sarCollected
                                                if (dueSar > 0.05) Color(0xFFC62828) else Color(0xFF2E7D32)
                                            }
                                            is UnifiedLedgerEntry.SupplierTx -> {
                                                val dep = unifiedEntry.dep
                                                if (dep.transactionType == "BDT_SETTLEMENT") {
                                                    Color(0xFF2E7D32)
                                                } else {
                                                    Color(0xFF1565C0)
                                                }
                                            }
                                        }
                                        
                                        Text(
                                            text = sarAmountText,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Black),
                                            color = amountColor
                                        )
                                    }
                                    if (itemIndex < entriesForDate.size - 1) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(1.dp)
                                                .background(cardBorder.copy(alpha = 0.5f))
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // System Reserves Summary Collapsible
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = if (lang == "BN") "ব্যালেন্স শিট ও সাপ্লায়ার রিজার্ভ" else "Ledger Reserves Details",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(text = viewModel.t("total_sar_received"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${foreignCurrency} ${currencyFormatter.format(stats.totalSarReceived)}",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(text = "Current Fund Stock ${localCurrency}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "৳ ${currencyFormatter.format(stats.totalBoughtPoolBdt)}",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    }
                }
            }
        }

        val context = LocalContext.current

        // --- FILTER SELECTION DIALOG (TUNE OPTION) ---
        if (showFilterDialog) {
            AlertDialog(
                onDismissRequest = { showFilterDialog = false },
                title = { Text(if (lang == "BN") "কাস্টমার ফিল্টার" else "Customer List Filter") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    currentFilterType = "ALL"
                                    showFilterDialog = false
                                }
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(selected = currentFilterType == "ALL", onClick = { currentFilterType = "ALL"; showFilterDialog = false })
                            Text(if (lang == "BN") "সব কাস্টমার" else "All Customers", modifier = Modifier.padding(start = 8.dp))
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    currentFilterType = "HAS_BALANCE"
                                    showFilterDialog = false
                                }
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(selected = currentFilterType == "HAS_BALANCE", onClick = { currentFilterType = "HAS_BALANCE"; showFilterDialog = false })
                            Text(if (lang == "BN") "বকেয়া ব্যালেন্সধারী" else "With Active Balance", modifier = Modifier.padding(start = 8.dp))
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    currentFilterType = "A_Z"
                                    showFilterDialog = false
                                }
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(selected = currentFilterType == "A_Z", onClick = { currentFilterType = "A_Z"; showFilterDialog = false })
                            Text(if (lang == "BN") "বর্ণানুক্রমিক (A-Z)" else "Sort Alphabetically (A-Z)", modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showFilterDialog = false }) {
                        Text(if (lang == "BN") "বন্ধ করুন" else "Close", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            )
        }

        // --- EXPANDED TRANSACTION REPORT & DOWNLOAD DIALOG ---
        if (showReportsDialog) {
            AlertDialog(
                onDismissRequest = { showReportsDialog = false },
                title = { 
                    Text(
                        text = if (lang == "BN") "বিস্তারিত রিপোর্ট ডাউনলোড" else "Detailed Reports Center",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    ) 
                },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (lang == "BN") "লেনদেন খতিয়ান, লাভ-ক্ষতির হিসেব এবং ব্যবসার বিস্তারিত রিপোর্ট ডাউনলোড ও শেয়ার করুন।" 
                                   else "Generate and download professional business ledgers, statements and P&L breakdown files.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        
                        // Range selection
                        Text(
                            text = if (lang == "BN") "১. সময়সীমা নির্বাচন করুন:" else "1. Select Period Range:",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        
                        // Flexible horizontal flow
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                                listOf("TODAY", "WEEKLY", "MONTHLY").forEach { period ->
                                    val label = when (period) {
                                        "TODAY" -> if (lang == "BN") "আজকের" else "Today"
                                        "WEEKLY" -> if (lang == "BN") "সাপ্তাহিক" else "Weekly"
                                        else -> if (lang == "BN") "মাসিক" else "Monthly"
                                    }
                                    val active = selectedReportPeriod == period
                                    OutlinedButton(
                                        onClick = { selectedReportPeriod = period },
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = if (active) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                            contentColor = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                        ),
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                                listOf("YEARLY", "ALL").forEach { period ->
                                    val label = when (period) {
                                        "YEARLY" -> if (lang == "BN") "বাৎসরিক" else "Yearly"
                                        else -> if (lang == "BN") "সব সময়ের" else "All Time"
                                    }
                                    val active = selectedReportPeriod == period
                                    OutlinedButton(
                                        onClick = { selectedReportPeriod = period },
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = if (active) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                            contentColor = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                        ),
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Format selection
                        Text(
                            text = if (lang == "BN") "২. পিডিএফ / এক্সেল / ইমেজ ফরম্যাট:" else "2. Select File Format:",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            listOf("PDF", "EXCEL", "IMAGE").forEach { format ->
                                val active = selectedReportFormat == format
                                val formatLabel = when (format) {
                                    "PDF" -> "PDF (.pdf)"
                                    "EXCEL" -> "Excel (.xlsx)"
                                    else -> "Image (.png)"
                                }
                                OutlinedButton(
                                    onClick = { selectedReportFormat = format },
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = if (active) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                                        contentColor = if (active) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
                                    ),
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(formatLabel, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val formatExt = when (selectedReportFormat) {
                                "PDF" -> "pdf"
                                "EXCEL" -> "xlsx"
                                else -> "png"
                            }
                            val titleText = when (selectedReportPeriod) {
                                "TODAY" -> "SAFA HUB TODAY BUSINESS INSIGHT"
                                "WEEKLY" -> "SAFA HUB WEEKLY REPORT"
                                "MONTHLY" -> "SAFA HUB MONTHLY P&L STATUS"
                                "YEARLY" -> "SAFA HUB YEARLY GENERAL LEDGER"
                                else -> "SAFA HUB CUMULATIVE GENERAL STMT"
                            }
                            
                            val report = StringBuilder()
                            report.append("=========================================\n")
                            report.append("          $titleText\n")
                            report.append("=========================================\n")
                            report.append("Printed At: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}\n")
                            report.append("Format: $selectedReportFormat | System: $customAppName\n")
                            report.append("Operator: ${operator?.username ?: "Unknown"}\n")
                            report.append("Language: $lang\n")
                            report.append("-----------------------------------------\n")
                            report.append("BUSINESS OVERVIEW STATS:\n")
                            report.append("-----------------------------------------\n")
                            report.append("Total Riyal Recvd:    ${foreignCurrency} ${stats.totalSarReceived}\n")
                            report.append("Total Outstandings:   TK  ${stats.totalBdtPending}\n")
                            report.append("Total Payable Pool:   TK  ${Math.abs(stats.supplierUnsettledBdt)}\n")
                            report.append("Total Active Capital: TK  ${stats.totalBoughtPoolBdt}\n")
                            report.append("-----------------------------------------\n")
                            
                            // Period-matched localized filter simulations
                            val targetTimeLimitMs = when (selectedReportPeriod) {
                                "TODAY" -> 24 * 3600 * 1000L
                                "WEEKLY" -> 7 * 24 * 3600 * 1000L
                                "MONTHLY" -> 30 * 24 * 3600 * 1000L
                                "YEARLY" -> 365 * 24 * 3600 * 1000L
                                else -> Long.MAX_VALUE
                            }
                            val curTime = System.currentTimeMillis()
                            val filteredTxs = transactions.filter { (curTime - it.timestamp) <= targetTimeLimitMs }
                            
                            val totalSentSar = filteredTxs.sumOf { it.amountSar }
                            var totalCustomerBdtPaid = 0.0
                            var totalSupplierBdtValue = 0.0
                            filteredTxs.forEach { tx ->
                                totalCustomerBdtPaid += tx.amountSar * tx.customerRate
                                totalSupplierBdtValue += tx.amountSar * tx.supplierRate
                            }
                            
                            val profitLossEst = totalSupplierBdtValue - totalCustomerBdtPaid
                            
                            report.append("PERIOD EXPORT ANALYSIS (${selectedReportPeriod}):\n")
                            report.append("-----------------------------------------\n")
                            report.append("Transactions Found:     ${filteredTxs.size} Items\n")
                            report.append("Volume Processed (${foreignCurrency}): ${foreignCurrency} $totalSentSar\n")
                            report.append("Customer ${localCurrency} Paid:      TK  $totalCustomerBdtPaid\n")
                            report.append("Supplier ${localCurrency} Value:     TK  $totalSupplierBdtValue\n")
                            if (profitLossEst >= 0) {
                                report.append("Estimated Net Profit:   TK  $profitLossEst (EARNINGS)\n")
                            } else {
                                report.append("Estimated Net Loss:     TK  ${Math.abs(profitLossEst)} (DEFICIT)\n")
                            }
                            report.append("-----------------------------------------\n")
                            report.append("          END OF STATEMENT REPORT        \n")
                            report.append("=========================================\n")
                            
                            val textResult = report.toString()
                            generatedReportText = textResult
                            
                            val fileName = "safa_report_${selectedReportPeriod.lowercase()}.$formatExt"
                            val reportFile = File(context.cacheDir, fileName)
                            try {
                                val writer = FileWriter(reportFile)
                                writer.write(textResult)
                                writer.close()
                                generatedReportPath = reportFile.absolutePath
                            } catch (e: Exception) {
                                generatedReportPath = "Internal Storage/cache/$fileName"
                            }
                            
                            showReportsDialog = false
                            showDownloadSuccessDialog = true
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(if (lang == "BN") "রিপোর্ট তৈরি করুন" else "Generate Statement", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showReportsDialog = false }) {
                        Text(if (lang == "BN") "বাতিল" else "Cancel", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            )
        }

        // --- DOWNLOAD SUCCESS DISPLAY DIALOG ---
        if (showDownloadSuccessDialog) {
            AlertDialog(
                onDismissRequest = { showDownloadSuccessDialog = false },
                properties = DialogProperties(usePlatformDefaultWidth = false),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .clip(RoundedCornerShape(24.dp)),
                content = {
                    Card(
                        modifier = Modifier.fillMaxWidth().testTag("generated_dialog_view"),
                        shape = RoundedCornerShape(24.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(20.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFE8F5E9)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = "", tint = Color(0xFF2E7D32))
                                }
                                Text(
                                    text = if (lang == "BN") "রিপোর্ট ডাউনলোড সফল!" else "Download Complete!",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                text = if (lang == "BN") "ফাইলটি সফলভাবে সেভ করা হয়েছে এবং প্রিন্ট করার জন্য প্রস্তুত:" 
                                       else "Report compiled successfully. Saved to cache path:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                    .padding(8.dp)
                            ) {
                                Text(
                                    text = generatedReportPath,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    maxLines = 1
                                )
                            }
                            
                            // Printable Console Display
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.Black)
                                    .padding(10.dp)
                            ) {
                                LazyColumn(modifier = Modifier.fillMaxSize()) {
                                    item {
                                        Text(
                                            text = generatedReportText,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                                color = Color.Green
                                            ),
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                            
                            Button(
                                onClick = { showDownloadSuccessDialog = false },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(if (lang == "BN") "ঠিক আছে" else "Close Viewer", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            )
        }
    }
}

@Composable
fun CustomSegmentButton(
    text: String,
    isActive: Boolean,
    activeColor: Color,
    activeTextColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isActive) activeColor else Color.Transparent)
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.ExtraBold,
                color = if (isActive) activeTextColor else MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
    }
}

@Composable
fun ServiceShortcutGrid(
    viewModel: HundiViewModel,
    onEditRatesClick: () -> Unit
) {
    val lang by viewModel.currentLanguage.collectAsStateWithLifecycle()
    val services = listOf(
        ShortcutItem(
            title = if (lang == "BN") "গ্রাহকগণ" else "Customers",
            icon = Icons.Default.People,
            color = Color(0xFFF26722),
            onClick = { viewModel.navigateTo(AppScreen.CUSTOMERS) }
        ),
        ShortcutItem(
            title = if (lang == "BN") "সাপ্লায়ার" else "Suppliers",
            icon = Icons.Default.AccountBalance,
            color = Color(0xFF1E88E5),
            onClick = { viewModel.navigateTo(AppScreen.SUPPLIERS) }
        ),
        ShortcutItem(
            title = if (lang == "BN") "লেনদেন সমূহ" else "All Transactions",
            icon = Icons.Default.ReceiptLong,
            color = Color(0xFF43A047),
            onClick = { viewModel.navigateTo(AppScreen.TRANSACTIONS) }
        ),
        ShortcutItem(
            title = if (lang == "BN") "খরচ ও আয়" else "Bills & Exp",
            icon = Icons.Default.LocalCafe,
            color = Color(0xFFE67E22),
            onClick = { viewModel.navigateTo(AppScreen.EXPENSES) }
        ),
        ShortcutItem(
            title = if (lang == "BN") "রেট আপডেট" else "Update Rates",
            icon = Icons.Default.ShowChart,
            color = Color(0xFF8E44AD),
            onClick = onEditRatesClick
        ),
        ShortcutItem(
            title = if (lang == "BN") "সেটিংস" else "Settings",
            icon = Icons.Default.Settings,
            color = Color(0xFF607D8B),
            onClick = { viewModel.navigateTo(AppScreen.SETTINGS) }
        )
    )

    Card(
        modifier = Modifier.fillMaxWidth().testTag("shortcut_services_card"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = if (lang == "BN") "আমাদের সেবাসমূহ" else "SAFA Quick Services",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            val rows = services.chunked(3)
            rows.forEachIndexed { index, rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    rowItems.forEach { item ->
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { item.onClick() }
                                .padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(item.color.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.title,
                                    tint = item.color,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                fontSize = 11.sp
                            )
                        }
                    }
                    if (rowItems.size < 3) {
                        repeat(3 - rowItems.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
                if (index < rows.size - 1) {
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }
}

data class ShortcutItem(
    val title: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val color: Color,
    val onClick: () -> Unit
)
