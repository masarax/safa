package com.safa.account.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safa.account.ui.viewmodel.HundiViewModel
import com.safa.account.ui.viewmodel.AppScreen
import java.io.File
import java.io.FileWriter
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    viewModel: HundiViewModel,
    modifier: Modifier = Modifier
) {
    val lang by viewModel.currentLanguage.collectAsStateWithLifecycle()
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()
    val expensesIncomes by viewModel.expensesIncomes.collectAsStateWithLifecycle()
    val currentOperator by viewModel.currentOperator.collectAsStateWithLifecycle()
    val stats by viewModel.financialStats.collectAsStateWithLifecycle()
    val foreignCur by viewModel.selectedForeignCurrency.collectAsStateWithLifecycle()
    val localCur by viewModel.selectedLocalCurrency.collectAsStateWithLifecycle()
    
    val context = LocalContext.current
    val currencyFormatter = remember { DecimalFormat("#,##0") }

    // Navigation and filters
    var selectedPeriod by remember { mutableStateOf("MONTHLY") } // TODAY, WEEKLY, MONTHLY, YEARLY, ALL_TIME
    var selectedReportType by remember { mutableStateOf("PROFIT_LOSS") } // PROFIT_LOSS, GENERAL_LEDGER, EXPENSES, CUSTOMER_SUMMARY
    var selectedFormat by remember { mutableStateOf("PDF") } // PDF, EXCEL, JPG, TEXT

    // Generated result states
    var generatedTextPreview by remember { mutableStateOf("") }
    var lastGeneratedPath by remember { mutableStateOf("") }
    var showSuccessToast by remember { mutableStateOf(false) }

    // Dynamic Report Calculations
    val targetTimeLimitMs = remember(selectedPeriod) {
        when (selectedPeriod) {
            "TODAY" -> 24 * 3600 * 1000L
            "WEEKLY" -> 7 * 24 * 3600 * 1000L
            "MONTHLY" -> 30 * 24 * 3600 * 1000L
            "YEARLY" -> 365 * 24 * 3600 * 1000L
            else -> Long.MAX_VALUE
        }
    }

    val curTime = System.currentTimeMillis()
    val periodTransactions = remember(transactions, targetTimeLimitMs) {
        if (targetTimeLimitMs == Long.MAX_VALUE) transactions
        else transactions.filter { (curTime - it.timestamp) <= targetTimeLimitMs && it.status != "Cancelled" }
    }

    val periodExpenses = remember(expensesIncomes, targetTimeLimitMs) {
        val list = if (targetTimeLimitMs == Long.MAX_VALUE) expensesIncomes
        else expensesIncomes.filter { (curTime - it.timestamp) <= targetTimeLimitMs }
        list.filter { it.isExpense }
    }

    // Calculations
    val totalVolumeSar = remember(periodTransactions) { periodTransactions.sumOf { it.amountSar } }
    val totalProfitBdt = remember(periodTransactions) { periodTransactions.sumOf { it.getProfitBdt() } }
    val totalExpenseBdt = remember(periodExpenses) { periodExpenses.sumOf { if (it.currency == localCur) it.amount else 0.0 } }
    val totalExpenseSar = remember(periodExpenses) { periodExpenses.sumOf { if (it.currency == foreignCur) it.amount else 0.0 } }
    val netRevenueBdt = remember(totalProfitBdt, totalExpenseBdt) { totalProfitBdt - totalExpenseBdt }

    // Function to generate report file
    val generateReportFile = {
        val titleText = when (selectedPeriod) {
            "TODAY" -> "SAFA HUB DAILY FINANCIAL STATEMENT"
            "WEEKLY" -> "SAFA HUB WEEKLY PERFORMANCE STATEMENT"
            "MONTHLY" -> "SAFA HUB MONTHLY P&L STATEMENT"
            "YEARLY" -> "SAFA HUB ANNUAL COMPREHENSIVE STATEMENT"
            else -> "SAFA HUB CUMULATIVE CONSOLIDATED STATEMENT"
        }

        val report = java.lang.StringBuilder()
        report.append("=========================================\n")
        report.append("         $titleText\n")
        report.append("=========================================\n")
        report.append("Generated On: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}\n")
        report.append("Export Format: $selectedFormat | System: Safa Hub Pro\n")
        report.append("Auditor: ${currentOperator?.username ?: "Manager"}\n")
        report.append("P&L Base Currency: $localCur\n")
        val isRateBasedMode = viewModel.isRateBasedModeEnabled.value
        
        report.append("-----------------------------------------\n")
        report.append("1. COMPREHENSIVE OVERVIEW:\n")
        report.append("-----------------------------------------\n")
        report.append("Total Riyal Handled:  ${foreignCur}${currencyFormatter.format(stats.totalSarReceived)}\n")
        report.append("Customer Due Pool:    $localCur  ${currencyFormatter.format(stats.totalBdtPending)}\n")
        report.append("Active Working Pool:  $localCur  ${currencyFormatter.format(stats.totalBoughtPoolBdt)}\n")
        report.append("-----------------------------------------\n")
        report.append("2. PERIOD MATCHED INSIGHTS (${selectedPeriod}):\n")
        report.append("-----------------------------------------\n")
        report.append("Settled Transactions: ${periodTransactions.size} Items\n")
        if (isRateBasedMode) {
            report.append("Arbitrage Volume:     ${foreignCur}${currencyFormatter.format(totalVolumeSar)}\n")
            report.append("Estimated Profit $localCur: $localCur  ${currencyFormatter.format(totalProfitBdt)}\n")
        }
        report.append("Period Expenses $localCur:  $localCur  ${currencyFormatter.format(totalExpenseBdt)}\n")
        report.append("Period Expenses $foreignCur:  ${foreignCur}${currencyFormatter.format(totalExpenseSar)}\n")
        report.append("-----------------------------------------\n")
        
        if (isRateBasedMode) {
            report.append("3. NET RETAINED BOTTOM-LINE:\n")
            report.append("-----------------------------------------\n")
            if (netRevenueBdt >= 0) {
                report.append("NET INCOME (SURPLUS):  $localCur  ${currencyFormatter.format(netRevenueBdt)} (PROFITABLE)\n")
            } else {
                report.append("NET INCOME (DEFICIT):  TK  ${currencyFormatter.format(Math.abs(netRevenueBdt))} (UNPROFITABLE)\n")
            }
        }
        report.append("=========================================\n")
        report.append("         END OF COMPREHENSIVE REPORT     \n")
        report.append("=========================================\n")

        val generatedText = report.toString()
        generatedTextPreview = generatedText

        val ext = selectedFormat.lowercase(Locale.ROOT)
        val fileName = "safa_statement_${selectedPeriod.lowercase(Locale.ROOT)}_${System.currentTimeMillis() % 100000}.$ext"
        val reportFile = File(context.cacheDir, fileName)
        try {
            val writer = FileWriter(reportFile)
            writer.write(generatedText)
            writer.close()
            lastGeneratedPath = reportFile.absolutePath
            showSuccessToast = true
        } catch (e: Exception) {
            lastGeneratedPath = "Error writing report file"
        }
    }

    // Trigger initial generation
    LaunchedEffect(selectedPeriod, selectedReportType, selectedFormat, transactions, expensesIncomes) {
        generateReportFile()
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.navigateTo(AppScreen.DASHBOARD) }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (lang == "BN") "আর্থিক রিপোর্ট" else "Reports",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color.White)
                )
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFFF8F9FA))
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 14.dp, bottom = 40.dp)
        ) {
            // Section 1: Time Period Filter Chips
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "TODAY" to if (lang == "BN") "আজ" else "Today",
                        "WEEKLY" to if (lang == "BN") "সপ্তাহ" else "Week",
                        "MONTHLY" to if (lang == "BN") "মাস" else "Month",
                        "YEARLY" to if (lang == "BN") "বছর" else "Year",
                        "ALL_TIME" to if (lang == "BN") "সব" else "All"
                    ).forEach { (periodKey, periodLabel) ->
                        val isSel = selectedPeriod == periodKey
                        FilterChip(
                            selected = isSel,
                            onClick = { selectedPeriod = periodKey },
                            label = { Text(periodLabel, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = Color.White
                            ),
                            shape = RoundedCornerShape(20.dp)
                        )
                    }
                }
            }

            // Section 2: Executive Summary KPI Cards
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Top Volume & Profit Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // ${foreignCur}Volume Card
                        Card(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, Color(0xFFE5E7EB))
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                    Text(
                                        text = if (lang == "BN") "মোট ভলিউম" else "Volume",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color(0xFF6B7280)
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "SAR ${currencyFormatter.format(totalVolumeSar)}",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        // Gross Profit Card
                        val isRateBasedModeForGross by viewModel.isRateBasedModeEnabled.collectAsStateWithLifecycle()
                        if (isRateBasedModeForGross) {
                            Card(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                border = BorderStroke(1.dp, Color(0xFFE5E7EB))
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Icon(Icons.Default.TrendingUp, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(16.dp))
                                        Text(
                                            text = if (lang == "BN") "গ্রস প্রফিট" else "Gross Profit",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = Color(0xFF6B7280)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "৳${currencyFormatter.format(totalProfitBdt)}",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = Color(0xFF2E7D32)
                                    )
                                }
                            }
                        }
                    }

                    // Net Income Hero Card
                    val isRateBasedMode by viewModel.isRateBasedModeEnabled.collectAsStateWithLifecycle()
                    if (isRateBasedMode) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (netRevenueBdt >= 0) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
                            ),
                            border = BorderStroke(1.dp, if (netRevenueBdt >= 0) Color(0xFFA5D6A7) else Color(0xFFEF9A9A))
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = if (lang == "BN") "নিট প্রফিট মার্জিন" else "Net Revenue",
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                                        color = if (netRevenueBdt >= 0) Color(0xFF1B5E20) else Color(0xFFC62828)
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "৳${currencyFormatter.format(netRevenueBdt)} ${localCur}",
                                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black),
                                        color = if (netRevenueBdt >= 0) Color(0xFF2E7D32) else Color(0xFFD32F2F)
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = if (netRevenueBdt >= 0) Color(0xFF2E7D32) else Color(0xFFC62828)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (netRevenueBdt >= 0) Icons.Default.CheckCircle else Icons.Default.Warning,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = if (netRevenueBdt >= 0) (if (lang == "BN") "লাভজনক" else "Profitable") else (if (lang == "BN") "ঘাটতি" else "Deficit"),
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Section 3: Visual Profit/Expense Breakdown Bar
            item {
                val isRateBasedMode by viewModel.isRateBasedModeEnabled.collectAsStateWithLifecycle()
                if (isRateBasedMode) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color(0xFFE5E7EB))
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = if (lang == "BN") "আয়-ব্যয় অনুপাত (BDT)" else "P&L Ratio",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )

                            val maxVal = Math.max(totalProfitBdt, totalExpenseBdt).toFloat().coerceAtLeast(1.0f)
                            val profitRatio = (totalProfitBdt.toFloat() / maxVal).coerceIn(0f, 1f)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(if (lang == "BN") "প্রফিট: ৳${currencyFormatter.format(totalProfitBdt)}" else "Profit: ৳${currencyFormatter.format(totalProfitBdt)}", style = MaterialTheme.typography.bodySmall, color = Color(0xFF2E7D32))
                                Text(if (lang == "BN") "ব্যয়: ৳${currencyFormatter.format(totalExpenseBdt)}" else "Expenses: ৳${currencyFormatter.format(totalExpenseBdt)}", style = MaterialTheme.typography.bodySmall, color = Color(0xFFC62828))
                            }

                            LinearProgressIndicator(
                                progress = { profitRatio },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = Color(0xFF2E7D32),
                                trackColor = Color(0xFFFFEBEE)
                            )
                        }
                    }
                }
            }

            // Section 4: Format Selector & Clean Paper Statement Preview Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0))
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
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.Receipt,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = if (lang == "BN") "অফিসিয়াল রিপোর্ট স্টেটমেন্ট" else "Official Financial Statement",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = Color(0xFF1E293B)
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("PDF", "EXCEL", "TXT").forEach { fmt ->
                                    val isSel = selectedFormat == fmt
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isSel) MaterialTheme.colorScheme.primary else Color(0xFFF1F5F9),
                                        modifier = Modifier.clickable { selectedFormat = fmt }
                                    ) {
                                        Text(
                                            text = fmt,
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = if (isSel) Color.White else Color(0xFF475569),
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Divider(color = Color(0xFFF1F5F9), thickness = 1.dp)

                        AnimatedContent(
                            targetState = generatedTextPreview,
                            label = "ReportPreviewAnimation"
                        ) { text ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFFAFAFA), RoundedCornerShape(10.dp))
                                    .border(1.dp, Color(0xFFF1F5F9), RoundedCornerShape(10.dp))
                                    .padding(14.dp)
                            ) {
                                Text(
                                    text = text,
                                    fontSize = 11.sp,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    color = Color(0xFF0F172A),
                                    lineHeight = 17.sp
                                )
                            }
                        }
                    }
                }
            }

            // Section 5: Concise Action Buttons
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "Remittance Statement")
                                putExtra(Intent.EXTRA_TEXT, generatedTextPreview)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Report"))
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (lang == "BN") "শেয়ার" else "Share", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }

                    OutlinedButton(
                        onClick = { generateReportFile() },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (lang == "BN") "ডাউনলোড" else "Download", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }

                    OutlinedButton(
                        onClick = {
                            val printIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, generatedTextPreview)
                            }
                            context.startActivity(Intent.createChooser(printIntent, "Print Report"))
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (lang == "BN") "প্রিন্ট" else "Print", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            // Toast feedback
            item {
                AnimatedVisibility(visible = showSuccessToast) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (lang == "BN") "রিপোর্ট তৈরি সম্পন্ন!" else "Report Generated!",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF1B5E20)
                            )
                            IconButton(onClick = { showSuccessToast = false }, modifier = Modifier.size(20.dp)) {
                                Icon(Icons.Default.Close, contentDescription = null, tint = Color(0xFF1B5E20))
                            }
                        }
                    }
                }
            }
        }
    }
}
