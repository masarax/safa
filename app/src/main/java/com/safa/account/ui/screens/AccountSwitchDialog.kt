package com.safa.account.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HomeWork
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.safa.account.data.api.AccountChoice
import com.safa.account.ui.viewmodel.SafaViewModel
import kotlinx.coroutines.launch

@Composable
fun AccountSwitchDialog(
    viewModel: SafaViewModel,
    onDismiss: () -> Unit
) {
    val lang by viewModel.currentLanguage.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var accounts by remember { mutableStateOf<List<AccountChoice>>(emptyList()) }
    var activeAccountId by remember { mutableStateOf(viewModel.tokenManager?.getActiveAccountId()) }
    var loading by remember { mutableStateOf(true) }
    var switching by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val result = viewModel.syncManager?.listAccounts()
        loading = false
        if (result?.isSuccess == true) {
            accounts = result.getOrDefault(emptyList())
            activeAccountId = viewModel.tokenManager?.getActiveAccountId()
        } else {
            error = if (lang == "BN") {
                "অ্যাকাউন্টের তথ্য লোড করা যায়নি। আবার চেষ্টা করুন।"
            } else {
                "Could not load account access. Please try again."
            }
        }
    }

    AlertDialog(
        modifier = Modifier.testTag("settings_account_switch_dialog"),
        onDismissRequest = { if (!switching) onDismiss() },
        title = {
            Text(
                if (lang == "BN") "ব্যবসার অ্যাকাউন্ট পরিবর্তন" else "Change business account",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (loading || switching) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                        Text(
                            if (switching) {
                                if (lang == "BN") "অ্যাকাউন্ট পরিবর্তন হচ্ছে…" else "Switching account…"
                            } else {
                                if (lang == "BN") "শেয়ার করা অ্যাকাউন্ট যাচাই হচ্ছে…" else "Checking shared accounts…"
                            }
                        )
                    }
                }

                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

                if (!loading && error == null) {
                    // Product model: one primary owned account is the user's
                    // normal workspace. Additional switch choices are only
                    // accounts explicitly shared by another user.
                    val owned = accounts.filter { it.isOwner }.take(1)
                    val shared = accounts.filterNot { it.isOwner }

                    owned.forEach { account ->
                        AccountChoiceButton(
                            account = account,
                            activeAccountId = activeAccountId,
                            lang = lang,
                            enabled = !switching,
                            label = if (lang == "BN") "আমার অ্যাকাউন্ট" else "My account",
                            onClick = accountClick@{
                                if (account.accountId == activeAccountId || switching) return@accountClick
                                val manager = viewModel.syncManager ?: return@accountClick
                                switching = true
                                error = null
                                scope.launch {
                                    val result = manager.switchAccount(account.accountId)
                                    switching = false
                                    if (result.isSuccess) {
                                        activeAccountId = result.getOrNull()
                                        onDismiss()
                                    } else {
                                        error = accountSwitchError(lang, result.exceptionOrNull()?.message)
                                    }
                                }
                            }
                        )
                    }

                    if (shared.isEmpty()) {
                        Text(
                            if (lang == "BN") {
                                "অন্য কোনো ইউজার এখনো আপনাকে তার অ্যাকাউন্টে এক্সেস দেয়নি।"
                            } else {
                                "No other user has shared an account with you yet."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    } else {
                        Text(
                            if (lang == "BN") "শেয়ার করা অ্যাকাউন্ট" else "Shared accounts",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        shared.forEach { account ->
                            AccountChoiceButton(
                                account = account,
                                activeAccountId = activeAccountId,
                                lang = lang,
                                enabled = !switching,
                                label = account.ownerName.ifBlank {
                                    if (lang == "BN") "শেয়ার করা অ্যাকাউন্ট" else "Shared account"
                                },
                                onClick = accountClick@{
                                    if (account.accountId == activeAccountId || switching) return@accountClick
                                    val manager = viewModel.syncManager ?: return@accountClick
                                    switching = true
                                    error = null
                                    scope.launch {
                                        val result = manager.switchAccount(account.accountId)
                                        switching = false
                                        if (result.isSuccess) {
                                            activeAccountId = result.getOrNull()
                                            onDismiss()
                                        } else {
                                            error = accountSwitchError(lang, result.exceptionOrNull()?.message)
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, enabled = !switching) {
                Text(if (lang == "BN") "বন্ধ" else "Close")
            }
        }
    )
}

@Composable
private fun AccountChoiceButton(
    account: AccountChoice,
    activeAccountId: Int?,
    lang: String,
    enabled: Boolean,
    label: String,
    onClick: () -> Unit
) {
    val selected = account.accountId == activeAccountId
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("settings_business_account_${account.accountId}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = when {
                    selected -> Icons.Default.CheckCircle
                    account.isOwner -> Icons.Default.HomeWork
                    else -> Icons.Default.AccountCircle
                },
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
                Text(
                    if (account.isOwner) {
                        if (lang == "BN") "নিজস্ব ব্যবসার অ্যাকাউন্ট" else "Owned business account"
                    } else {
                        if (lang == "BN") "শেয়ার করা এক্সেস" else "Shared access"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

private fun accountSwitchError(lang: String, message: String?): String {
    val hasPending = message?.contains("pending", ignoreCase = true) == true
    return if (hasPending) {
        if (lang == "BN") "অ্যাকাউন্ট পরিবর্তনের আগে পেন্ডিং পরিবর্তনগুলো সিঙ্ক করুন।"
        else "Sync pending changes before switching accounts."
    } else {
        if (lang == "BN") "এই অ্যাকাউন্টে পরিবর্তন করা যায়নি। এক্সেস এখনও আছে কি না যাচাই করুন।"
        else "Could not switch to this account. Check that access is still granted."
    }
}
