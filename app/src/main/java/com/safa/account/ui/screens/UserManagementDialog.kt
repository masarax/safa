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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    val active: Boolean,
    val permissions: Map<String, Boolean>
)

@Composable
fun UserManagementDialog(viewModel: SafaViewModel, onDismiss: () -> Unit) {
    val lang by viewModel.currentLanguage.collectAsStateWithLifecycle()
    val currentOperator by viewModel.currentOperator.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
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
        scope.launch {
            try {
                val response = viewModel.syncManager?.getApiService()?.getOperators()
                if (response?.isSuccessful == true) {
                    val rows = response.body()?.get("users") as? List<*>
                    users = rows.orEmpty().mapNotNull { raw ->
                        val map = raw as? Map<*, *> ?: return@mapNotNull null
                        fun bool(key: String) = map[key] == true || map[key]?.toString().equals("true", true) || map[key]?.toString() == "1"
                        @Suppress("UNCHECKED_CAST")
                        val perms = (map["permissions"] as? Map<String, Any?>).orEmpty().mapValues { (_, v) -> v == true || v.toString().equals("true", true) || v.toString() == "1" }
                        ManagedUser(
                            id = (map["id"] as? Number)?.toInt() ?: 0,
                            name = map["name"]?.toString().orEmpty(),
                            mobile = map["mobile"]?.toString().orEmpty(),
                            email = map["email"]?.toString().orEmpty(),
                            role = map["role"]?.toString() ?: "user",
                            active = bool("is_activated"),
                            permissions = perms
                        )
                    }.filter { it.id != currentOperator?.id }
                } else {
                    SafaLogger.warn("USER_MANAGEMENT", "Operator list rejected with HTTP ${response?.code() ?: 0}")
                    error = operationError
                }
            } catch (t: Throwable) {
                SafaLogger.error("USER_MANAGEMENT", "Operator list failed", t)
                error = operationError
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) { loadUsers() }

    if (showForm) {
        UserEditDialog(
            lang = lang,
            existing = editing,
            saving = saving,
            onDismiss = { if (!saving) showForm = false },
            onSave = { name, mobile, email, role, pin, active, permissions ->
                saving = true
                scope.launch {
                    try {
                        val api = viewModel.syncManager?.getApiService()
                            ?: error("Server unavailable")
                        val request = OperatorApiRequest(
                            name = name,
                            mobile = mobile,
                            email = email.ifBlank { null },
                            role = role,
                            pin = pin.ifBlank { null },
                            isActivated = active,
                            permissions = permissions
                        )
                        val response = if (editing == null) api.createOperator(request) else api.updateOperator(editing!!.id, request)
                        if (!response.isSuccessful) error("HTTP ${response.code()}")
                        showForm = false
                        loadUsers()
                    } catch (t: Throwable) {
                        SafaLogger.error("USER_MANAGEMENT", "Operator save failed", t)
                        error = operationError
                    } finally {
                        saving = false
                    }
                }
            }
        )
        return
    }

    if (deleteTarget != null) {
        val target = deleteTarget!!
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(if (lang == "BN") "ইউজার মুছবেন?" else "Delete User?", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error) },
            text = { Text(if (lang == "BN") "${target.name} স্থায়ীভাবে মুছে যাবে।" else "${target.name} will be permanently deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    deleteTarget = null
                    saving = true
                    scope.launch {
                        try {
                            val response = viewModel.syncManager?.getApiService()?.deleteOperator(target.id, confirmed = true)
                            if (response?.isSuccessful != true) error("HTTP ${response?.code() ?: 0}") else loadUsers()
                        } catch (t: Throwable) {
                            SafaLogger.error("USER_MANAGEMENT", "Operator deletion failed", t)
                            error = operationError
                        } finally { saving = false }
                    }
                }) { Text(if (lang == "BN") "মুছুন" else "Delete", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text(if (lang == "BN") "বাতিল" else "Cancel") } }
        )
        return
    }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        icon = { Icon(Icons.Default.Group, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text(if (lang == "BN") "ইউজার ম্যানেজমেন্ট" else "User Management", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button(onClick = { editing = null; showForm = true }) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Text(if (lang == "BN") "নতুন" else "New", modifier = Modifier.padding(start = 4.dp))
                    }
                }
                if (loading) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator() }
                }
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(users, key = { it.id }) { user ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(user.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${user.mobile} • ${user.role}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                Text(if (user.active) (if (lang == "BN") "সক্রিয়" else "Active") else (if (lang == "BN") "নিষ্ক্রিয়" else "Inactive"), style = MaterialTheme.typography.labelSmall, color = if (user.active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                            }
                            IconButton(onClick = { editing = user; showForm = true }) { Icon(Icons.Default.Edit, contentDescription = if (lang == "BN") "সম্পাদনা" else "Edit") }
                            IconButton(onClick = { deleteTarget = user }) { Icon(Icons.Default.Delete, contentDescription = if (lang == "BN") "মুছুন" else "Delete", tint = MaterialTheme.colorScheme.error) }
                        }
                        Divider()
                    }
                }
                if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(if (lang == "BN") "বন্ধ" else "Close") } }
    )
}

@Composable
private fun UserEditDialog(
    lang: String,
    existing: ManagedUser?,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String, Boolean, Map<String, Boolean>) -> Unit
) {
    var name by remember(existing) { mutableStateOf(existing?.name.orEmpty()) }
    var mobile by remember(existing) { mutableStateOf(existing?.mobile.orEmpty()) }
    var email by remember(existing) { mutableStateOf(existing?.email.orEmpty()) }
    var role by remember(existing) { mutableStateOf(existing?.role ?: "user") }
    var pin by remember { mutableStateOf("") }
    var active by remember(existing) { mutableStateOf(existing?.active ?: true) }
    var permissionView by remember(existing) { mutableStateOf(existing?.permissions?.get("can_view_customers") ?: false) }
    var permissionEdit by remember(existing) { mutableStateOf(existing?.permissions?.get("can_edit_customers") ?: false) }
    var permissionDelete by remember(existing) { mutableStateOf(existing?.permissions?.get("can_delete_customers") ?: false) }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(if (lang == "BN") (if (existing == null) "নতুন ইউজার" else "ইউজার সম্পাদনা") else (if (existing == null) "New User" else "Edit User"), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text(if (lang == "BN") "নাম" else "Name") }, singleLine = true)
                OutlinedTextField(mobile, { mobile = it }, label = { Text(if (lang == "BN") "মোবাইল" else "Mobile") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                OutlinedTextField(email, { email = it }, label = { Text(if (lang == "BN") "ইমেইল" else "Email") }, singleLine = true)
                OutlinedTextField(pin, {
                    val normalized = com.safa.account.data.api.MobileNumberNormalizer.normalizePin(it)
                    if (normalized.length <= 6) pin = normalized
                }, label = { Text(if (lang == "BN") "৬-ডিজিট পিন${if (existing != null) " (পরিবর্তন করলে দিন)" else ""}" else "6-digit PIN${if (existing != null) " (optional)" else ""}") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = role == "admin", onClick = { role = "admin" }, label = { Text(if (lang == "BN") "অ্যাডমিন" else "Admin") })
                    FilterChip(selected = role == "user", onClick = { role = "user" }, label = { Text(if (lang == "BN") "ইউজার" else "User") })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = permissionView, onClick = { permissionView = !permissionView }, label = { Text(if (lang == "BN") "দেখুন" else "View") })
                    FilterChip(selected = permissionEdit, onClick = { permissionEdit = !permissionEdit }, label = { Text(if (lang == "BN") "এডিট" else "Edit") })
                    FilterChip(selected = permissionDelete, onClick = { permissionDelete = !permissionDelete }, label = { Text(if (lang == "BN") "ডিলিট" else "Delete") })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = active, onClick = { active = !active }, label = { Text(if (lang == "BN") "সক্রিয়" else "Active") })
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !saving && name.isNotBlank() && mobile.isNotBlank() && (existing != null || pin.length == 6),
                onClick = onSave.bind(name, mobile, email, role, pin, active, mapOf(
                    "can_view_customers" to permissionView,
                    "can_edit_customers" to permissionEdit,
                    "can_delete_customers" to permissionDelete
                ))
            ) { if (saving) CircularProgressIndicator(modifier = Modifier.padding(2.dp), strokeWidth = 2.dp) else Text(if (lang == "BN") "সংরক্ষণ" else "Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text(if (lang == "BN") "বাতিল" else "Cancel") } }
    )
}

private fun <A, B, C, D, E, F, G, R> ((A, B, C, D, E, F, G) -> R).bind(a: A, b: B, c: C, d: D, e: E, f: F, g: G): () -> R = { invoke(a, b, c, d, e, f, g) }
