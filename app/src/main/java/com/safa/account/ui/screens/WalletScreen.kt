package com.safa.account.ui.screens
import com.safa.account.ui.localization.AndroidStringCatalog

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safa.account.data.model.WalletBatch
import com.safa.account.data.model.WalletLedger
import com.safa.account.ui.viewmodel.SafaViewModel
import com.safa.account.data.money.MoneyMath
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletScreen(
    viewModel: SafaViewModel,
    modifier: Modifier = Modifier
) {
    val lang by viewModel.currentLanguage.collectAsStateWithLifecycle()
    val foreignCur by viewModel.selectedForeignCurrency.collectAsStateWithLifecycle()
    val localCur by viewModel.selectedLocalCurrency.collectAsStateWithLifecycle()
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()
    val walletBatches by viewModel.walletBatches.collectAsStateWithLifecycle()
    val walletLedgers by viewModel.walletLedgers.collectAsStateWithLifecycle()
    val isDarkMode by viewModel.isDarkMode.collectAsStateWithLifecycle()

    val currentOperator by viewModel.currentOperator.collectAsStateWithLifecycle()

    if (currentOperator != null && !currentOperator!!.canManageWallet) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Lock, contentDescription = "", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
                Text(text = viewModel.t("access_denied"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                Text(text = viewModel.t("permission_required"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
            }
        }
        return
    }

    val currencyFormatter = remember { DecimalFormat("#,##0.00") }
    val dateFormatter = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }

    // Dialog state
    var showAddLedgerDialog by remember { mutableStateOf(false) }
    var newLedgerName by remember { mutableStateOf("") }

    var showEditLedgerDialog by remember { mutableStateOf<WalletLedger?>(null) }
    var editLedgerNameInput by remember { mutableStateOf("") }

    var showDeleteConfirmDialog by remember { mutableStateOf<WalletLedger?>(null) }
    var showDeletionBlockedDialog by remember { mutableStateOf<WalletLedger?>(null) }

    var showAddFundDialog by remember { mutableStateOf<WalletLedger?>(null) }
    var addFundBdtAmount by remember { mutableStateOf("") }
    var addFundRate by remember { mutableStateOf("32.5") }
    var addFundNotes by remember { mutableStateOf("") }

    var showDeductFundDialog by remember { mutableStateOf<WalletLedger?>(null) }
    var deductFundBdtAmount by remember { mutableStateOf("") }

    // Expand/Collapse state of ledger detail cards
    var expandedLedgerId by remember { mutableStateOf<Int?>(null) }

    // Computations
    val activeTxs = transactions.filter { it.status != "Cancelled" }
    val totalSarFromCustomers = activeTxs.fold(MoneyMath.ZERO_AMOUNT) { total, tx -> MoneyMath.add(total, tx.amountSar) }
    val totalBdtEquivFromCustomers = activeTxs.fold(MoneyMath.ZERO_AMOUNT) { total, tx -> MoneyMath.add(total, tx.amountBdt) }

    // Aggregate overall Wallet cash pool balance from state
    val totalWalletBdtBalance = walletBatches.fold(MoneyMath.ZERO_AMOUNT) { total, batch -> MoneyMath.add(total, batch.remainingBdt) }

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

            // Section Label & New Ledger button
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountBalanceWallet,
                            contentDescription = "",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Text(
                        text = AndroidStringCatalog.get(lang, "inline_walletscreen_e7c71289c5"),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 17.sp,
                            letterSpacing = (-0.3).sp
                        ),
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Button(
                    onClick = { showAddLedgerDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.height(34.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "", modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = AndroidStringCatalog.get(lang, "inline_walletscreen_5c1ca5db47"),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = Color.White),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                if (walletLedgers.isEmpty()) {
                    item {
                    Text(
                        text = AndroidStringCatalog.get(lang, "inline_walletscreen_aad15554bf"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
            }

            // Draw Wallet Ledgers list in-between
            items(walletLedgers, key = { it.id }) { ledger ->
                val batches = walletBatches.filter { it.ledgerId == ledger.id }
                val ledgerBalance = batches.fold(MoneyMath.ZERO_AMOUNT) { total, batch -> MoneyMath.add(total, batch.remainingBdt) }
                val isExpanded = expandedLedgerId == ledger.id

                val totalInitial = batches.fold(MoneyMath.ZERO_AMOUNT) { total, batch -> MoneyMath.add(total, batch.initialBdt) }
                val totalSpent = totalInitial - ledgerBalance
                val activeBatches = batches.filter { it.remainingBdt.signum() > 0 }
                val weightedRate = MoneyMath.weightedRate(activeBatches.map { it.remainingBdt to it.rate })

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expandedLedgerId = if (isExpanded) null else ledger.id }
                        .testTag("ledger_card_${ledger.id}"),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isExpanded) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface
                    ),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp, 
                        if (isExpanded) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = ledger.name,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    IconButton(
                                        onClick = {
                                            editLedgerNameInput = ledger.name
                                            showEditLedgerDialog = ledger
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Edit Ledger Name",
                                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = AndroidStringCatalog.get(lang, "inline_walletscreen_bb077162a5"),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "৳ ${currencyFormatter.format(ledgerBalance)}",
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                                    color = if (ledgerBalance.signum() > 0) Color(0xFF2E7D32) else MaterialTheme.colorScheme.outline,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Icon(
                                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = "",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        // Expanded drawer with deposits/withdraws & detailed sub-accounts
                        if (isExpanded) {
                            Spacer(modifier = Modifier.height(14.dp))
                            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                            Spacer(modifier = Modifier.height(10.dp))

                             // Beautiful, spacious Add / Deduct triggers row (Anti-text cutting fix!)
                             Row(
                                 modifier = Modifier.fillMaxWidth(),
                                 horizontalArrangement = Arrangement.spacedBy(8.dp)
                             ) {
                                 Button(
                                     onClick = { showAddFundDialog = ledger },
                                     colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                                     shape = RoundedCornerShape(10.dp),
                                     contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                     modifier = Modifier.weight(1f).height(36.dp)
                                 ) {
                                     Icon(Icons.Default.AddCircle, contentDescription = "", modifier = Modifier.size(14.dp))
                                     Spacer(modifier = Modifier.width(3.dp))
                                     Text(
                                         text = AndroidStringCatalog.get(lang, "inline_walletscreen_a324f6b630"), 
                                         style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
                                         maxLines = 1,
                                         overflow = TextOverflow.Ellipsis
                                     )
                                 }

                                 Button(
                                     onClick = { showDeductFundDialog = ledger },
                                     colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                                     shape = RoundedCornerShape(10.dp),
                                     contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                     modifier = Modifier.weight(1f).height(36.dp)
                                 ) {
                                     Icon(Icons.Default.RemoveCircle, contentDescription = "", modifier = Modifier.size(14.dp))
                                     Spacer(modifier = Modifier.width(3.dp))
                                     Text(
                                         text = AndroidStringCatalog.get(lang, "inline_walletscreen_471077bea8"), 
                                         style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
                                         maxLines = 1,
                                         overflow = TextOverflow.Ellipsis
                                     )
                                 }
                             }

                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedButton(
                                onClick = { 
                                    if (ledgerBalance.signum() > 0) {
                                        showDeletionBlockedDialog = ledger
                                    } else {
                                        showDeleteConfirmDialog = ledger
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFC62828)),
                                modifier = Modifier.fillMaxWidth().height(36.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFC62828).copy(alpha = 0.3f)),
                                contentPadding = PaddingValues(vertical = 0.dp, horizontal = 12.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete Ledger", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = AndroidStringCatalog.get(lang, "inline_walletscreen_8468393758"), 
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = AndroidStringCatalog.get(lang, "inline_walletscreen_4fde26e3dc"),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 12.sp),
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )

                            if (activeBatches.isEmpty()) {
                                Text(
                                    text = AndroidStringCatalog.get(lang, "inline_walletscreen_2af5044c9b"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    val groupedBatches = activeBatches.groupBy { it.rate }

                                    groupedBatches.forEach { (rate, items) ->
                                        val combinedRemaining = MoneyMath.sumAmounts(items.map { it.remainingBdt })
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha=0.3f), RoundedCornerShape(10.dp))
                                                .padding(10.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "Rate: @ $rate",
                                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Black, fontSize = 13.sp),
                                                    color = MaterialTheme.colorScheme.primary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f, fill = false)
                                                )
                                                Text(
                                                    text = "Total Remaining: ৳${currencyFormatter.format(combinedRemaining)}",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                    color = if (combinedRemaining.signum() > 0) Color(0xFF2E7D32) else MaterialTheme.colorScheme.outline,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }

                                            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), modifier = Modifier.padding(bottom = 4.dp))

                                            items.forEach { batch ->
                                                Row(
                                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                                        Text(
                                                            text = dateFormatter.format(Date(batch.timestamp)),
                                                            fontSize = 10.sp,
                                                            color = MaterialTheme.colorScheme.outline,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                        if (batch.notes.isNotBlank()) {
                                                            Text(
                                                                text = "Notes: ${batch.notes}",
                                                                fontSize = 9.sp,
                                                                color = MaterialTheme.colorScheme.outlineVariant,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                        }
                                                    }
                                                    Column(horizontalAlignment = Alignment.End) {
                                                        Text(
                                                            text = "In: ৳${currencyFormatter.format(batch.initialBdt)}",
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.onSurface,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                        Text(
                                                            text = "Bal: ৳${currencyFormatter.format(batch.remainingBdt)}",
                                                            fontSize = 10.sp,
                                                            color = if (batch.remainingBdt.signum() > 0) Color(0xFF2E7D32) else MaterialTheme.colorScheme.outline,
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
        }
    }
}

    // --- Dialog: Create Ledger ---
    if (showAddLedgerDialog) {
        AlertDialog(
            containerColor = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
            onDismissRequest = { showAddLedgerDialog = false },
            title = { Text(AndroidStringCatalog.get(lang, "inline_walletscreen_4fa00bf998")) },
            text = {
                OutlinedTextField(
                    value = newLedgerName,
                    onValueChange = { newLedgerName = it },
                    label = { Text(AndroidStringCatalog.get(lang, "inline_walletscreen_7b7df83518")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.registerWalletLedger(newLedgerName) {
                            newLedgerName = ""
                            showAddLedgerDialog = false
                        }
                    },
                    enabled = newLedgerName.isNotBlank()
                ) {
                    Text(AndroidStringCatalog.get(lang, "inline_walletscreen_3dcb44550e"), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddLedgerDialog = false }) {
                    Text(AndroidStringCatalog.get(lang, "inline_walletscreen_c74fa9cbbd"), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        )
    }

    // --- Dialog: Edit Ledger Name ---
    if (showEditLedgerDialog != null) {
        val ledger = showEditLedgerDialog!!
        AlertDialog(
            onDismissRequest = { showEditLedgerDialog = null },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
            content = {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .padding(16.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Text(
                            text = AndroidStringCatalog.get(lang, "inline_walletscreen_da5c044421"),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black, fontSize = 15.sp),
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Text(
                            text = AndroidStringCatalog.get(lang, "inline_walletscreen_ebb5bce5b0"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        OutlinedTextField(
                            value = editLedgerNameInput,
                            onValueChange = { editLedgerNameInput = it },
                            label = { Text(AndroidStringCatalog.get(lang, "inline_walletscreen_b59787da20")) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showEditLedgerDialog = null },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f).height(40.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Text(AndroidStringCatalog.get(lang, "inline_walletscreen_c74fa9cbbd"), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }

                            Button(
                                onClick = {
                                    viewModel.updateWalletLedgerName(ledger.id, editLedgerNameInput) {
                                        showEditLedgerDialog = null
                                    }
                                },
                                enabled = editLedgerNameInput.isNotBlank(),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f).height(40.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Text(AndroidStringCatalog.get(lang, "inline_walletscreen_ce9658bee0"), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        )
    }

    // --- Dialog: Confirm Delete Ledger ---
    if (showDeleteConfirmDialog != null) {
        val ledger = showDeleteConfirmDialog!!
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = null },
            icon = { Icon(Icons.Default.Delete, contentDescription = "", tint = MaterialTheme.colorScheme.error) },
            title = { Text(AndroidStringCatalog.get(lang, "inline_walletscreen_7364501e9b")) },
            text = {
                Text(
                    text = AndroidStringCatalog.get(lang, "inline_walletscreen_9ee6510bfc")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteWalletLedger(ledger.id) {
                            showDeleteConfirmDialog = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(AndroidStringCatalog.get(lang, "inline_walletscreen_acc97d70c8"), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = null }) {
                    Text(AndroidStringCatalog.get(lang, "inline_walletscreen_c74fa9cbbd"), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        )
    }

    // --- Dialog: Deletion Blocked Alert ---
    if (showDeletionBlockedDialog != null) {
        val ledger = showDeletionBlockedDialog!!
        AlertDialog(
            onDismissRequest = { showDeletionBlockedDialog = null },
            icon = { Icon(Icons.Default.Warning, contentDescription = "", tint = Color(0xFFE65100)) },
            title = { Text(AndroidStringCatalog.get(lang, "inline_walletscreen_b7426e830b")) },
            text = {
                Text(
                    text = AndroidStringCatalog.get(lang, "inline_walletscreen_4b86f62f55")
                )
            },
            confirmButton = {
                Button(
                    onClick = { showDeletionBlockedDialog = null },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(AndroidStringCatalog.get(lang, "inline_walletscreen_4b235f4c63"), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        )
    }

    // --- Dialog: Deposit Fund in Wallet ---
    if (showAddFundDialog != null) {
        val ledger = showAddFundDialog!!
        AlertDialog(
            onDismissRequest = { showAddFundDialog = null },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
            content = {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .padding(16.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE8F5E9)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddBusiness,
                                contentDescription = "",
                                tint = Color(0xFF2E7D32),
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Text(
                            text = AndroidStringCatalog.get(lang, "inline_walletscreen_9d204ef604"),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black, fontSize = 15.sp),
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Text(
                            text = AndroidStringCatalog.get(lang, "inline_walletscreen_0c4742773c"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        OutlinedTextField(
                            value = addFundBdtAmount,
                            onValueChange = { addFundBdtAmount = it },
                            label = { Text(AndroidStringCatalog.get(lang, "inline_walletscreen_56586ca2eb")) },
                            singleLine = true,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        val isRateBasedMode by viewModel.isRateBasedModeEnabled.collectAsStateWithLifecycle()
                        val isWalletRateEnabled by viewModel.isWalletRateEnabled.collectAsStateWithLifecycle()
                        if (isRateBasedMode && isWalletRateEnabled) {
                            OutlinedTextField(
                                value = addFundRate,
                                onValueChange = { addFundRate = it },
                                label = { Text(AndroidStringCatalog.get(lang, "inline_walletscreen_f2a34e8824")) },
                                placeholder = { Text("32.5") },
                                singleLine = true,
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            LaunchedEffect(Unit) { addFundRate = "1.0" }
                        }

                        OutlinedTextField(
                            value = addFundNotes,
                            onValueChange = { addFundNotes = it },
                            label = { Text(AndroidStringCatalog.get(lang, "inline_walletscreen_5604ce2844")) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showAddFundDialog = null },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f).height(40.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Text(AndroidStringCatalog.get(lang, "inline_walletscreen_c74fa9cbbd"), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }

                            Button(
                                onClick = {
                                    val amount = addFundBdtAmount.toBigDecimalOrNull() ?: MoneyMath.ZERO_AMOUNT
                                    val rate = addFundRate.toBigDecimalOrNull() ?: MoneyMath.ZERO_RATE
                                    viewModel.addMoneyToWallet(ledger.id, amount, rate, addFundNotes) {
                                        addFundBdtAmount = ""
                                        addFundNotes = ""
                                        showAddFundDialog = null
                                    }
                                },
                                enabled = addFundBdtAmount.isNotBlank(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f).height(40.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Text(AndroidStringCatalog.get(lang, "inline_walletscreen_b957c429f8"), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        )
    }

    // --- Dialog: Deduct Fund manually from Wallet (Withdraw Stock) ---
    if (showDeductFundDialog != null) {
        val ledger = showDeductFundDialog!!
        val maxAvailable = walletBatches.filter { it.ledgerId == ledger.id }
            .fold(MoneyMath.ZERO_AMOUNT) { total, batch -> MoneyMath.add(total, batch.remainingBdt) }
        val enteredAmount = deductFundBdtAmount.toBigDecimalOrNull() ?: MoneyMath.ZERO_AMOUNT
        val isOverLimit = enteredAmount > maxAvailable

        AlertDialog(
            onDismissRequest = { 
                deductFundBdtAmount = ""
                showDeductFundDialog = null 
            },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
            content = {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .padding(16.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFFEBEE)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.RemoveCircleOutline,
                                contentDescription = "",
                                tint = Color(0xFFC62828),
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Text(
                            text = AndroidStringCatalog.get(lang, "inline_walletscreen_208d3d52c0"),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black, fontSize = 15.sp),
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        // Highlighted available balance display
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                                .padding(vertical = 10.dp, horizontal = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (lang == "BN") {
                                    "বর্তমানে উপলব্ধ ব্যালেন্স: ৳ ${currencyFormatter.format(maxAvailable)}"
                                } else {
                                    "Currently Available: ৳ ${currencyFormatter.format(maxAvailable)}"
                                },
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.ExtraBold, fontSize = 13.sp),
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Text(
                            text = AndroidStringCatalog.get(lang, "inline_walletscreen_6ff16c3060"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                            textAlign = TextAlign.Center
                        )

                        OutlinedTextField(
                            value = deductFundBdtAmount,
                            onValueChange = { deductFundBdtAmount = it },
                            label = { Text(AndroidStringCatalog.get(lang, "inline_walletscreen_71847e6397")) },
                            singleLine = true,
                            isError = isOverLimit,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (isOverLimit) {
                            Text(
                                text = if (lang == "BN") {
                                    "❌ অপর্যাপ্ত ব্যালেন্স! আপনার সর্বোচ্চ সীমা ৳${currencyFormatter.format(maxAvailable)}"
                                } else {
                                    "❌ Insufficient Balance! Maximum limit is ৳${currencyFormatter.format(maxAvailable)}"
                                },
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = { 
                                    deductFundBdtAmount = ""
                                    showDeductFundDialog = null 
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f).height(40.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Text(AndroidStringCatalog.get(lang, "inline_walletscreen_c74fa9cbbd"), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }

                            Button(
                                onClick = {
                                    val amount = deductFundBdtAmount.toBigDecimalOrNull() ?: MoneyMath.ZERO_AMOUNT
                                    viewModel.deductMoneyFromWalletLedger(ledger.id, amount) {
                                        deductFundBdtAmount = ""
                                        showDeductFundDialog = null
                                    }
                                },
                                enabled = deductFundBdtAmount.isNotBlank() && !isOverLimit && enteredAmount.signum() > 0,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f).height(40.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Text(AndroidStringCatalog.get(lang, "inline_walletscreen_db67bec88e"), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        )
    }
}
