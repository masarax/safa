package com.safa.account.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.safa.account.ui.viewmodel.SafaViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
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
    var ownedAccountId by remember { mutableStateOf<Int?>(null) }
    var resolvingOwnedAccount by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    // The current active account can be another user's shared workspace. Sharing
    // must always delegate the authenticated user's own account, never whichever
    // account happens to be active when Settings is opened.
    LaunchedEffect(Unit) {
        val result = viewModel.syncManager?.listAccounts()
        ownedAccountId = result
            ?.getOrNull()
            ?.firstOrNull { it.isOwner }
            ?.accountId
        resolvingOwnedAccount = false
        if (ownedAccountId == null) {
            error = if (lang == "BN") {
                "আপনার নিজস্ব ব্যবসার অ্যাকাউন্ট যাচাই করা যায়নি। আবার চেষ্টা করুন।"
            } else {
                "Your owned business account could not be verified. Please try again."
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        icon = { Icon(Icons.Default.Share, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = {
            Text(
                if (lang == "BN") "আমার অ্যাকাউন্টে এক্সেস দিন" else "Share my account access",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (resolvingOwnedAccount) {
                    CircularProgressIndicator(strokeWidth = 2.dp)
                    Text(
                        if (lang == "BN") "আপনার নিজস্ব অ্যাকাউন্ট যাচাই হচ্ছে…" else "Verifying your owned account…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                Text(
                    if (lang == "BN") "যে SAFA ইউজারকে আপনার ব্যবসার অ্যাকাউন্টে এক্সেস দিতে চান তার মোবাইল দিন।" else "Enter the mobile number of the SAFA user you want to grant access to your business account.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
                OutlinedTextField(
                    value = mobile,
                    onValueChange = { mobile = it; if (!resolvingOwnedAccount && ownedAccountId != null) error = null; success = null },
                    label = { Text(if (lang == "BN") "মোবাইল নম্বর" else "Mobile number") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    enabled = !resolvingOwnedAccount && ownedAccountId != null && !saving,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    if (lang == "BN") "অনুমতি" else "Permissions",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = canViewCustomers, enabled = !saving, onClick = { canViewCustomers = !canViewCustomers }, label = { Text(if (lang == "BN") "কাস্টমার" else "Customers") })
                    FilterChip(selected = canViewSuppliers, enabled = !saving, onClick = { canViewSuppliers = !canViewSuppliers }, label = { Text(if (lang == "BN") "সাপ্লায়ার" else "Suppliers") })
                    FilterChip(selected = canViewTransactions, enabled = !saving, onClick = { canViewTransactions = !canViewTransactions }, label = { Text(if (lang == "BN") "লেনদেন" else "Transactions") })
                    FilterChip(selected = canManageWallet, enabled = !saving, onClick = { canManageWallet = !canManageWallet }, label = { Text(if (lang == "BN") "ওয়ালেট" else "Wallet") })
                    FilterChip(selected = canManageExpenses, enabled = !saving, onClick = { canManageExpenses = !canManageExpenses }, label = { Text(if (lang == "BN") "আয়/ব্যয়" else "Expenses") })
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
                    val accountId = ownedAccountId
                    if (accountId == null) {
                        error = if (lang == "BN") "আপনার নিজস্ব অ্যাকাউন্ট পাওয়া যায়নি" else "Your owned account is unavailable"
                        return@Button
                    }
                    saving = true
                    scope.launch {
                        try {
                            val api = viewModel.syncManager?.getApiService()
                            if (api == null) {
                                error = if (lang == "BN") "সার্ভার সংযোগ নেই" else "Server connection unavailable"
                                return@launch
                            }
                            val response = api.shareAccount(
                                mapOf(
                                    "mobile" to normalized,
                                    "account_id" to accountId,
                                    "permissions_override" to mapOf(
                                        "can_view_customers" to canViewCustomers,
                                        "can_view_suppliers" to canViewSuppliers,
                                        "can_view_transactions" to canViewTransactions,
                                        "can_manage_wallet" to canManageWallet,
                                        "can_manage_expenses" to canManageExpenses
                                    )
                                )
                            )
                            if (response.isSuccessful) {
                                success = if (lang == "BN") "আপনার অ্যাকাউন্টের এক্সেস দেওয়া হয়েছে" else "Your account access was shared successfully"
                            } else {
                                error = if (lang == "BN") "অ্যাকাউন্ট এক্সেস দেওয়া যায়নি" else "Account access could not be shared"
                            }
                        } catch (_: Throwable) {
                            error = if (lang == "BN") "অ্যাকাউন্ট এক্সেস দেওয়া যায়নি" else "Account access could not be shared"
                        } finally {
                            saving = false
                        }
                    }
                },
                enabled = !saving && !resolvingOwnedAccount && ownedAccountId != null
            ) {
                if (saving) CircularProgressIndicator(modifier = Modifier.padding(2.dp), strokeWidth = 2.dp)
                else Text(if (lang == "BN") "এক্সেস দিন" else "Grant access")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) {
                Text(if (lang == "BN") "বাতিল" else "Cancel")
            }
        }
    )
}
