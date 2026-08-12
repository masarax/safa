package com.safa.account.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.safa.account.ui.viewmodel.SafaViewModel
import kotlinx.coroutines.launch

@Composable
fun AccountSharingDialog(
    viewModel: SafaViewModel,
    onDismiss: () -> Unit
) {
    val lang by viewModel.currentLanguage.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var mobile by remember { mutableStateOf("") }
    var canViewCustomers by remember { mutableStateOf(true) }
    var canViewSuppliers by remember { mutableStateOf(true) }
    var canViewTransactions by remember { mutableStateOf(true) }
    var canManageWallet by remember { mutableStateOf(false) }
    var canManageExpenses by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        icon = { Icon(Icons.Default.Share, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = {
            Text(
                if (lang == "BN") "অ্যাকাউন্ট শেয়ার" else "Share Account",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    if (lang == "BN") "অন্য SAFA ইউজারের মোবাইল দিন।" else "Enter another SAFA user's mobile number.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
                OutlinedTextField(
                    value = mobile,
                    onValueChange = { mobile = it; error = null; success = null },
                    label = { Text(if (lang == "BN") "মোবাইল নম্বর" else "Mobile number") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    if (lang == "BN") "অনুমতি" else "Permissions",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = canViewCustomers, onClick = { canViewCustomers = !canViewCustomers }, label = { Text(if (lang == "BN") "কাস্টমার" else "Customers") })
                    FilterChip(selected = canViewSuppliers, onClick = { canViewSuppliers = !canViewSuppliers }, label = { Text(if (lang == "BN") "সাপ্লায়ার" else "Suppliers") })
                    FilterChip(selected = canViewTransactions, onClick = { canViewTransactions = !canViewTransactions }, label = { Text(if (lang == "BN") "লেনদেন" else "Transactions") })
                    FilterChip(selected = canManageWallet, onClick = { canManageWallet = !canManageWallet }, label = { Text(if (lang == "BN") "ওয়ালেট" else "Wallet") })
                    FilterChip(selected = canManageExpenses, onClick = { canManageExpenses = !canManageExpenses }, label = { Text(if (lang == "BN") "আয়/ব্যয়" else "Expenses") })
                }
                if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                if (success != null) Text(success!!, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val normalized = mobile.trim()
                    if (normalized.isBlank()) {
                        error = if (lang == "BN") "মোবাইল নম্বর দিন" else "Enter a mobile number"
                        return@Button
                    }
                    saving = true
                    scope.launch {
                        try {
                            val api = viewModel.syncManager?.getApiService()
                            if (api == null) {
                                error = if (lang == "BN") "সার্ভার সংযোগ নেই" else "Server connection unavailable"
                                saving = false
                                return@launch
                            }
                            val accountId = viewModel.tokenManager?.getActiveAccountId() ?: 1
                            val response = api.shareAccount(
                                mapOf(
                                    "mobile" to normalized,
                                    "account_id" to accountId,
                                    "permissions" to mapOf(
                                        "can_view_customers" to canViewCustomers,
                                        "can_view_suppliers" to canViewSuppliers,
                                        "can_view_transactions" to canViewTransactions,
                                        "can_manage_wallet" to canManageWallet,
                                        "can_manage_expenses" to canManageExpenses
                                    )
                                )
                            )
                            if (response.isSuccessful) {
                                success = if (lang == "BN") "অ্যাকাউন্ট শেয়ার হয়েছে" else "Account shared successfully"
                            } else {
                                error = "HTTP ${response.code()}"
                            }
                        } catch (t: Throwable) {
                            error = t.localizedMessage ?: if (lang == "BN") "শেয়ার ব্যর্থ" else "Share failed"
                        } finally {
                            saving = false
                        }
                    }
                },
                enabled = !saving
            ) {
                if (saving) CircularProgressIndicator(modifier = Modifier.padding(2.dp), strokeWidth = 2.dp)
                else Text(if (lang == "BN") "শেয়ার" else "Share")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) {
                Text(if (lang == "BN") "বাতিল" else "Cancel")
            }
        }
    )
}
