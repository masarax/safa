package com.safa.account.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.safa.account.data.api.MobileNumberNormalizer
import com.safa.account.data.api.dto.OperatorApiRequest
import com.safa.account.ui.viewmodel.SafaViewModel
import com.safa.account.utils.SafaLogger
import kotlinx.coroutines.launch

private data class ManagedUser(
    val id: Int,
    val name: String,
    val mobile: String,
    val email: String,
    val role: String,
    val roleLabel: String,
    val active: Boolean
)

private fun androidRoleLabel(role: String, lang: String): String = when (role.lowercase()) {
    "superadmin" -> if (lang == "BN") "সুপার অ্যাডমিন" else "Super Admin"
    "admin" -> if (lang == "BN") "অ্যাডমিন" else "Admin"
    "manager" -> if (lang == "BN") "বিজনেস ইউজার" else "Business User"
    else -> if (lang == "BN") "নরমাল ইউজার" else "Normal User"
}

@Composable
fun UserManagementDialog(viewModel: SafaViewModel, onDismiss: () -> Unit) {
    val lang by viewModel.currentLanguage.collectAsStateWithLifecycle()
    val currentOperator by viewModel.currentOperator.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val isSuperAdmin = currentOperator?.role?.equals("SuperAdmin", ignoreCase = true) == true
    var users by remember { mutableStateOf<List<ManagedUser>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<ManagedUser?>(null) }
    var showForm by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<ManagedUser?>(null) }
    val operationError = if (lang == "BN") "অনুরোধটি সম্পন্ন করা যায়নি। আবার চেষ্টা করুন।" else "The request could not be completed. Please try again."

    fun loadUsers() {
        loading = true
        error = null
        scope.launch {
            try {
                val response = viewModel.syncManager?.getApiService()?.getOperators()
                if (response?.isSuccessful == true) {
                    val rows = response.body()?.get("users") as? List<*>
                    users = rows.orEmpty().mapNotNull { raw ->
                        val map = raw as? Map<*, *> ?: return@mapNotNull null
                        val id = (map["id"] as? Number)?.toInt() ?: map["id"]?.toString()?.toIntOrNull() ?: 0
                        if (id <= 0 || id == currentOperator?.id) return@mapNotNull null
                        val role = map["role"]?.toString()?.lowercase().orEmpty().ifBlank { "user" }
                        ManagedUser(
                            id = id,
                            name = map["name"]?.toString().orEmpty(),
                            mobile = map["mobile"]?.toString().orEmpty(),
                            email = map["email"]?.toString().orEmpty(),
                            role = role,
                            roleLabel = map["role_label"]?.toString().orEmpty().ifBlank { androidRoleLabel(role, lang) },
                            active = map["is_activated"] == true || map["is_activated"]?.toString() == "1" || map["is_activated"]?.toString().equals("true", true)
                        )
                    }
                } else {
                    SafaLogger.warn("USER_MANAGEMENT", "User list rejected with HTTP ${response?.code() ?: 0}")
                    error = operationError
                }
            } catch (t: Throwable) {
                SafaLogger.error("USER_MANAGEMENT", "User list failed", t)
                error = operationError
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(currentOperator?.id) { loadUsers() }

    if (showForm) {
        UserEditDialog(
            lang = lang,
            existing = editing,
            saving = saving,
            isSuperAdmin = isSuperAdmin,
            onDismiss = { if (!saving) showForm = false },
            onSave = { name, mobile, email, role, pin, active ->
                saving = true
                error = null
                scope.launch {
                    try {
                        val api = viewModel.syncManager?.getApiService() ?: error("Server unavailable")
                        val request = OperatorApiRequest(
                            name = name,
                            mobile = mobile,
                            email = email.ifBlank { null },
                            role = role,
                            pin = pin.ifBlank { null },
                            isActivated = active,
                            permissions = emptyMap()
                        )
                        val response = if (editing == null) api.createOperator(request) else api.updateOperator(editing!!.id, request)
                        if (!response.isSuccessful) error("HTTP ${response.code()}")
                        showForm = false
                        editing = null
                        loadUsers()
                    } catch (t: Throwable) {
                        SafaLogger.error("USER_MANAGEMENT", "User save failed", t)
                        error = operationError
                    } finally {
                        saving = false
                    }
                }
            }
        )
        return
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { if (!saving) deleteTarget = null },
            title = { Text(if (lang == "BN") "ইউজার মুছবেন?" else "Delete user?", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error) },
            text = { Text(if (lang == "BN") "${target.name} এর অ্যাকাউন্ট মুছে যাবে।" else "${target.name}'s account will be deleted.") },
            confirmButton = {
                TextButton(
                    enabled = !saving,
                    onClick = {
                        saving = true
                        scope.launch {
                            try {
                                val response = viewModel.syncManager?.getApiService()?.deleteOperator(target.id, confirmed = true)
                                if (response?.isSuccessful != true) error("HTTP ${response?.code() ?: 0}")
                                deleteTarget = null
                                loadUsers()
                            } catch (t: Throwable) {
                                SafaLogger.error("USER_MANAGEMENT", "User deletion failed", t)
                                error = operationError
                            } finally {
                                saving = false
                            }
                        }
                    }
                ) { Text(if (lang == "BN") "মুছুন" else "Delete", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }, enabled = !saving) { Text(if (lang == "BN") "বাতিল" else "Cancel") } }
        )
        return
    }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        icon = { Icon(Icons.Default.Group, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text(if (lang == "BN") "ইউজার ম্যানেজমেন্ট" else "User Management", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (lang == "BN") "ভূমিকা অনুযায়ী অনুমতি সার্ভার থেকে নির্ধারিত হয়। এখানে অতিরিক্ত অনুমতি দিয়ে ভূমিকা অতিক্রম করা যায় না।"
                    else "Permissions are determined by server-side role presets and cannot be elevated with custom checkboxes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button(onClick = { editing = null; showForm = true }, enabled = !saving) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Text(if (lang == "BN") "নতুন" else "New", modifier = Modifier.padding(start = 4.dp))
                    }
                }
                if (loading) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator() }
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(users, key = { it.id }) { user ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(user.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${user.mobile} • ${user.roleLabel}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                Text(
                                    if (user.active) (if (lang == "BN") "সক্রিয়" else "Active") else (if (lang == "BN") "নিষ্ক্রিয়" else "Inactive"),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (user.active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                )
                            }
                            IconButton(onClick = { editing = user; showForm = true }, enabled = !saving) { Icon(Icons.Default.Edit, contentDescription = if (lang == "BN") "সম্পাদনা" else "Edit") }
                            IconButton(onClick = { deleteTarget = user }, enabled = !saving) { Icon(Icons.Default.Delete, contentDescription = if (lang == "BN") "মুছুন" else "Delete", tint = MaterialTheme.colorScheme.error) }
                        }
                        Divider()
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text(if (lang == "BN") "বন্ধ" else "Close") } }
    )
}

@Composable
private fun UserEditDialog(
    lang: String,
    existing: ManagedUser?,
    saving: Boolean,
    isSuperAdmin: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String, Boolean) -> Unit
) {
    var name by remember(existing) { mutableStateOf(existing?.name.orEmpty()) }
    var mobile by remember(existing) { mutableStateOf(existing?.mobile.orEmpty()) }
    var email by remember(existing) { mutableStateOf(existing?.email.orEmpty()) }
    var role by remember(existing) { mutableStateOf(existing?.role ?: "user") }
    var pin by remember(existing) { mutableStateOf("") }
    var active by remember(existing) { mutableStateOf(existing?.active ?: true) }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(if (lang == "BN") (if (existing == null) "নতুন ইউজার" else "ইউজার সম্পাদনা") else (if (existing == null) "New User" else "Edit User"), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text(if (lang == "BN") "নাম" else "Name") }, singleLine = true)
                OutlinedTextField(mobile, { mobile = it }, label = { Text(if (lang == "BN") "মোবাইল" else "Mobile") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                OutlinedTextField(email, { email = it }, label = { Text(if (lang == "BN") "ইমেইল" else "Email") }, singleLine = true)
                OutlinedTextField(
                    pin,
                    {
                        val normalized = MobileNumberNormalizer.normalizePin(it)
                        if (normalized.length <= 6) pin = normalized
                    },
                    label = { Text(if (lang == "BN") "৬-ডিজিট পিন${if (existing != null) " (পরিবর্তন করলে দিন)" else ""}" else "6-digit PIN${if (existing != null) " (optional reset)" else ""}") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                )

                Text(if (lang == "BN") "ভূমিকা" else "Role", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (isSuperAdmin) {
                        FilterChip(selected = role == "admin", onClick = { role = "admin" }, label = { Text(if (lang == "BN") "অ্যাডমিন" else "Admin") })
                    }
                    FilterChip(selected = role == "manager", onClick = { role = "manager" }, label = { Text(if (lang == "BN") "বিজনেস" else "Business") })
                    FilterChip(selected = role == "user", onClick = { role = "user" }, label = { Text(if (lang == "BN") "নরমাল" else "Normal") })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = active, onClick = { active = !active }, label = { Text(if (lang == "BN") "সক্রিয়" else "Active") })
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !saving && name.isNotBlank() && mobile.isNotBlank() && (existing != null || pin.length == 6) && (pin.isBlank() || pin.length == 6),
                onClick = { onSave(name, mobile, email, role, pin, active) }
            ) {
                if (saving) CircularProgressIndicator(modifier = Modifier.padding(2.dp), strokeWidth = 2.dp)
                else Text(if (lang == "BN") "সংরক্ষণ" else "Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text(if (lang == "BN") "বাতিল" else "Cancel") } }
    )
}
