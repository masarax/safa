package com.safa.account

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.safa.account.R
import com.safa.account.data.api.AccountChoice
import com.safa.account.data.api.AuthLifecycleCoordinator
import com.safa.account.data.network.DeleteConfirmationCoordinator
import com.safa.account.data.network.DeleteConfirmationRequest
import com.safa.account.data.repository.AppRepository
import com.safa.account.ui.components.StartupFailurePolicy
import com.safa.account.ui.components.StartupFailurePresentation
import com.safa.account.ui.screens.*
import com.safa.account.ui.theme.MyApplicationTheme
import com.safa.account.ui.viewmodel.AppScreen
import com.safa.account.ui.viewmodel.NavDirection
import com.safa.account.ui.viewmodel.SafaViewModel
import com.safa.account.ui.viewmodel.SafaViewModelFactory
import com.safa.account.utils.SafaLogger
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        try {
            val repository = AppRepository(applicationContext)
            val tokenManager = com.safa.account.data.api.TokenManager(applicationContext)
            val factory = SafaViewModelFactory(repository, tokenManager)
            val viewModel = ViewModelProvider(this, factory)[SafaViewModel::class.java]

            setContent {
                SafaRoot(viewModel = viewModel, onExit = { finish() })
            }
        } catch (t: Throwable) {
            val diagnosticId = java.util.UUID.randomUUID().toString().take(8).uppercase()
            val language = resources.configuration.locales[0]?.language.orEmpty()
            val presentation = StartupFailurePolicy.presentation(language, diagnosticId, t)
            SafaLogger.error(
                "FATAL_STARTUP_ERROR",
                "support_id=$diagnosticId",
                StartupFailurePolicy.diagnosticThrowable(BuildConfig.DEBUG, t)
            )
            setContent {
                MyApplicationTheme(darkTheme = false) {
                    StartupErrorScreen(
                        presentation = presentation,
                        onRetry = { recreate() }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun SafaRoot(viewModel: SafaViewModel, onExit: () -> Unit) {
    val currentLanguage by viewModel.currentLanguage.collectAsStateWithLifecycle()
    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val currentOperator by viewModel.currentOperator.collectAsStateWithLifecycle()
    val isDarkMode by viewModel.isDarkMode.collectAsStateWithLifecycle()
    val isSubPageActive by viewModel.isSubPageActive.collectAsStateWithLifecycle()
    val navDirection by viewModel.navDirection.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var showExitDialog by remember { mutableStateOf(false) }
    var previousOperatorId by remember { mutableStateOf<Int?>(currentOperator?.id) }
    var isRefreshing by remember { mutableStateOf(false) }
    var deleteConfirmation by remember { mutableStateOf<DeleteConfirmationRequest?>(null) }
    var activeAccountId by remember { mutableStateOf(viewModel.tokenManager?.getActiveAccountId()) }
    var accountChoices by remember { mutableStateOf<List<AccountChoice>>(emptyList()) }
    var showAccountDialog by remember { mutableStateOf(false) }
    var accountLoading by remember { mutableStateOf(false) }
    var accountSwitching by remember { mutableStateOf(false) }
    var accountError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(currentOperator?.id) {
        val previous = previousOperatorId
        val current = currentOperator?.id
        if (previous != null && current == null) {
            viewModel.tokenManager?.let { AuthLifecycleCoordinator(it).logout() }
            activeAccountId = null
            accountChoices = emptyList()
            showAccountDialog = false
            accountError = null
        }
        previousOperatorId = current
    }

    LaunchedEffect(Unit) {
        viewModel.tokenManager?.sessionInvalidated?.collect { viewModel.logout() }
    }

    LaunchedEffect(currentOperator?.id) {
        val operator = currentOperator ?: return@LaunchedEffect
        if (!operator.isActive) return@LaunchedEffect
        val tm = viewModel.tokenManager ?: return@LaunchedEffect
        activeAccountId = tm.getActiveAccountId()
        if (activeAccountId == null) {
            // Never expose a previously cached account while this authenticated
            // session has not selected its canonical business account yet.
            viewModel.repository.clearLocalPresentation()
        }
        val manager = viewModel.syncManager ?: return@LaunchedEffect
        accountLoading = true
        accountError = null
        val result = manager.listAccounts()
        accountLoading = false
        if (result.isSuccess) {
            accountChoices = result.getOrDefault(emptyList())
            activeAccountId = tm.getActiveAccountId()
            showAccountDialog = activeAccountId == null
        } else if (activeAccountId == null) {
            accountError = if (currentLanguage == "BN") "অ্যাকাউন্ট তালিকা লোড করা যায়নি। আবার চেষ্টা করুন।" else "Could not load your accounts. Please retry."
            showAccountDialog = true
        }
    }

    // Persist the account's quick-unlock preference only after an authenticated
    // operator is present. This keeps the setting available across app restarts
    // without writing a biometric secret or PIN to the server/local database.
    LaunchedEffect(currentOperator?.id) {
        viewModel.isBiometricEnabled.collect { enabled ->
            val operator = viewModel.currentOperator.value ?: return@collect
            if (enabled) viewModel.tokenManager?.enableBiometricQuickUnlock(operator.id, operator.mobile)
            else viewModel.tokenManager?.disableBiometricQuickUnlock()
        }
    }

    LaunchedEffect(Unit) {
        DeleteConfirmationCoordinator.requests.collect { request -> deleteConfirmation = request }
    }

    // Resume a valid server session after process restart/app relaunch. When
    // biometric quick-unlock is enabled, the interceptor blocks this request
    // until the fingerprint gate has approved the existing session.
    LaunchedEffect(currentOperator?.id, currentScreen) {
        if (currentOperator != null || currentScreen != AppScreen.LOCK_SCREEN) return@LaunchedEffect
        val tm = viewModel.tokenManager ?: return@LaunchedEffect
        if (tm.getAccessToken().isNullOrBlank() || tm.getRefreshToken().isNullOrBlank() || tm.getSessionToken().isNullOrBlank()) return@LaunchedEffect

        runCatching {
            val response = viewModel.syncManager?.getApiService()?.getCurrentSession()
            if (response?.isSuccessful != true) return@runCatching
            val map = response.body() ?: return@runCatching
            @Suppress("UNCHECKED_CAST")
            val user = map["user"] as? Map<String, Any?> ?: return@runCatching
            if (!viewModel.restoreAuthenticatedSession(user)) tm.notifySessionInvalidated()
        }
    }

    val isMainScreen = currentScreen in setOf(AppScreen.DASHBOARD, AppScreen.CUSTOMERS, AppScreen.SUPPLIERS, AppScreen.WALLET, AppScreen.EXPENSES)
    val showBars = isMainScreen && !isSubPageActive

    MyApplicationTheme(darkTheme = isDarkMode) {
        if (currentScreen != AppScreen.LOCK_SCREEN && !showAccountDialog) {
            BackHandler { if (!viewModel.navigateBack()) showExitDialog = true }
        }

        if (showExitDialog) {
            AlertDialog(
                onDismissRequest = { showExitDialog = false },
                title = { Text(text = if (currentLanguage == "BN") "অ্যাপ থেকে প্রস্থান" else "Exit Application", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                text = { Text(text = if (currentLanguage == "BN") "আপনি কি নিশ্চিতভাবে অ্যাপ থেকে বের হতে চান?" else "Are you sure you want to exit the application?", style = MaterialTheme.typography.bodyMedium) },
                confirmButton = { TextButton(onClick = { showExitDialog = false; onExit() }) { Text(text = if (currentLanguage == "BN") "হ্যাঁ, বের হব" else "Yes, Exit", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
                dismissButton = { TextButton(onClick = { showExitDialog = false }) { Text(text = if (currentLanguage == "BN") "না" else "No", fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis) } }
            )
        }

        deleteConfirmation?.let { request ->
            AlertDialog(
                onDismissRequest = { DeleteConfirmationCoordinator.resolve(request.id, false); deleteConfirmation = null },
                title = { Text(if (currentLanguage == "BN") "ডাটা মুছে ফেলবেন?" else request.title, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium) },
                text = { Text(if (currentLanguage == "BN") "এই কাজটি পূর্বাবস্থায় ফেরানো যাবে না।" else request.message, style = MaterialTheme.typography.bodyMedium) },
                confirmButton = { TextButton(onClick = { DeleteConfirmationCoordinator.resolve(request.id, true); deleteConfirmation = null }) { Text(if (currentLanguage == "BN") "মুছে ফেলুন" else "Delete", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) } },
                dismissButton = { TextButton(onClick = { DeleteConfirmationCoordinator.resolve(request.id, false); deleteConfirmation = null }) { Text(if (currentLanguage == "BN") "বাতিল" else "Cancel") } }
            )
        }

        if (showAccountDialog && currentOperator != null) {
            AlertDialog(
                onDismissRequest = {
                    if (activeAccountId != null && !accountSwitching) showAccountDialog = false
                },
                title = {
                    Text(
                        if (currentLanguage == "BN") "ব্যবসার অ্যাকাউন্ট নির্বাচন" else "Select business account",
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (accountLoading || accountSwitching) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                                Text(if (accountSwitching) {
                                    if (currentLanguage == "BN") "অ্যাকাউন্ট পরিবর্তন হচ্ছে…" else "Switching account…"
                                } else {
                                    if (currentLanguage == "BN") "অ্যাকাউন্ট লোড হচ্ছে…" else "Loading accounts…"
                                })
                            }
                        }
                        accountError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        accountChoices.forEach { account ->
                            val selected = account.accountId == activeAccountId
                            TextButton(
                                onClick = {
                                    if (accountSwitching) return@TextButton
                                    if (selected) {
                                        showAccountDialog = false
                                        return@TextButton
                                    }
                                    val manager = viewModel.syncManager ?: return@TextButton
                                    accountSwitching = true
                                    accountError = null
                                    scope.launch {
                                        val result = manager.switchAccount(account.accountId)
                                        accountSwitching = false
                                        if (result.isSuccess) {
                                            activeAccountId = result.getOrNull()
                                            showAccountDialog = false
                                        } else {
                                            val pending = result.exceptionOrNull()?.message?.contains("pending", ignoreCase = true) == true
                                            accountError = if (pending) {
                                                if (currentLanguage == "BN") "আগে বর্তমান অ্যাকাউন্টের পেন্ডিং পরিবর্তন সিঙ্ক করুন।" else "Sync pending changes before switching accounts."
                                            } else {
                                                if (currentLanguage == "BN") "অ্যাকাউন্ট পরিবর্তন করা যায়নি। আবার চেষ্টা করুন।" else "Could not switch account. Please retry."
                                            }
                                        }
                                    }
                                },
                                enabled = !accountSwitching,
                                modifier = Modifier.fillMaxWidth().testTag("business_account_${account.accountId}")
                            ) {
                                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Icon(
                                        if (selected) Icons.Default.CheckCircle else Icons.Default.AccountBalance,
                                        contentDescription = null,
                                        tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(account.ownerName.ifBlank { if (currentLanguage == "BN") "অ্যাকাউন্ট #${account.accountId}" else "Account #${account.accountId}" }, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
                                        Text("${account.role} • #${account.accountId}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                    }
                                }
                            }
                        }
                        if (!accountLoading && accountChoices.isEmpty()) {
                            Text(if (currentLanguage == "BN") "কোনো অনুমোদিত অ্যাকাউন্ট পাওয়া যায়নি।" else "No authorized account is available.")
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val manager = viewModel.syncManager ?: return@TextButton
                            accountLoading = true
                            accountError = null
                            scope.launch {
                                val result = manager.listAccounts()
                                accountLoading = false
                                if (result.isSuccess) {
                                    accountChoices = result.getOrDefault(emptyList())
                                    activeAccountId = viewModel.tokenManager?.getActiveAccountId()
                                    if (activeAccountId != null && accountChoices.size <= 1) showAccountDialog = false
                                } else {
                                    accountError = if (currentLanguage == "BN") "অ্যাকাউন্ট তালিকা লোড করা যায়নি।" else "Could not load account list."
                                }
                            }
                        },
                        enabled = !accountLoading && !accountSwitching
                    ) { Text(if (currentLanguage == "BN") "রিফ্রেশ" else "Refresh") }
                },
                dismissButton = {
                    if (activeAccountId != null) {
                        TextButton(onClick = { showAccountDialog = false }, enabled = !accountSwitching) {
                            Text(if (currentLanguage == "BN") "বন্ধ" else "Close")
                        }
                    }
                }
            )
        }

        if (currentScreen == AppScreen.LOCK_SCREEN) {
            LoginScreen(viewModel = viewModel)
        } else {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                topBar = {
                    if (showBars) SafaTopAppBar(
                        viewModel = viewModel,
                        title = viewModel.t("app_title"),
                        operatorName = currentOperator?.username ?: "",
                        onAccountClick = {
                            showAccountDialog = true
                            accountLoading = true
                            accountError = null
                            scope.launch {
                                val result = viewModel.syncManager?.listAccounts()
                                accountLoading = false
                                if (result?.isSuccess == true) {
                                    accountChoices = result.getOrDefault(emptyList())
                                    activeAccountId = viewModel.tokenManager?.getActiveAccountId()
                                } else {
                                    accountError = if (currentLanguage == "BN") "অ্যাকাউন্ট তালিকা লোড করা যায়নি।" else "Could not load account list."
                                }
                            }
                        },
                        onLogoutClick = { viewModel.logout() }
                    )
                },
                bottomBar = { if (showBars) SafaBottomNavigationBar(viewModel, currentScreen) }
            ) { innerPadding ->
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = { if (!isRefreshing) { isRefreshing = true; viewModel.triggerFullSync { _, _ -> isRefreshing = false } } },
                    modifier = Modifier.fillMaxSize().padding(innerPadding)
                ) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).testTag("safa_refresh_container")) {
                        AnimatedContent(
                            targetState = currentScreen,
                            transitionSpec = {
                                val isBackward = navDirection == NavDirection.BACKWARD
                                if (isBackward) {
                                    slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(160, easing = FastOutSlowInEasing)) + fadeIn(animationSpec = tween(110)) togetherWith slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(160, easing = FastOutSlowInEasing)) + fadeOut(animationSpec = tween(110))
                                } else {
                                    slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(160, easing = FastOutSlowInEasing)) + fadeIn(animationSpec = tween(110)) togetherWith slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween(160, easing = FastOutSlowInEasing)) + fadeOut(animationSpec = tween(110))
                                }
                            },
                            label = "SqueezeTransition",
                            modifier = Modifier.fillMaxSize()
                        ) { targetScreen ->
                            when (targetScreen) {
                                AppScreen.DASHBOARD -> DashboardScreen(viewModel = viewModel)
                                AppScreen.CUSTOMERS -> CustomerScreen(viewModel = viewModel, isProfileView = false, isAddView = false)
                                AppScreen.CUSTOMER_PROFILE -> CustomerScreen(viewModel = viewModel, isProfileView = true, isAddView = false)
                                AppScreen.CUSTOMER_ADD -> CustomerScreen(viewModel = viewModel, isProfileView = false, isAddView = true)
                                AppScreen.SUPPLIERS -> SupplierScreen(viewModel = viewModel, isProfileView = false, isAddView = false)
                                AppScreen.SUPPLIER_PROFILE -> SupplierScreen(viewModel = viewModel, isProfileView = true, isAddView = false)
                                AppScreen.SUPPLIER_ADD -> SupplierScreen(viewModel = viewModel, isProfileView = false, isAddView = true)
                                AppScreen.TRANSACTIONS -> TransactionScreen(viewModel = viewModel)
                                AppScreen.WALLET -> WalletScreen(viewModel = viewModel)
                                AppScreen.EXPENSES -> ExpenseScreen(viewModel = viewModel, isAddingEntryView = false)
                                AppScreen.EXPENSE_ADD -> ExpenseScreen(viewModel = viewModel, isAddingEntryView = true)
                                AppScreen.SETTINGS -> SettingsScreen(viewModel = viewModel)
                                AppScreen.REPORTS -> ReportsScreen(viewModel = viewModel)
                                AppScreen.LOCK_SCREEN -> LoginScreen(viewModel = viewModel)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SafaTopAppBar(
    viewModel: SafaViewModel,
    title: String,
    operatorName: String,
    onAccountClick: () -> Unit,
    onLogoutClick: () -> Unit
) {
    val currentLang by viewModel.currentLanguage.collectAsStateWithLifecycle()
    val isDarkMode by viewModel.isDarkMode.collectAsStateWithLifecycle()
    val goldBgColor = if (isDarkMode) Color(0xFF1B1812) else Color(0xFFD7A84B)
    val contentOnGoldColor = if (isDarkMode) Color(0xFFE5C158) else Color(0xFF3E2700)
    val customAppLogo by viewModel.customAppLogo.collectAsStateWithLifecycle()
    val customAppLogoUri by viewModel.customAppLogoUri.collectAsStateWithLifecycle()
    val customAppName by viewModel.customAppName.collectAsStateWithLifecycle()

    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp).clickable { viewModel.navigateTo(AppScreen.SETTINGS) }) {
                Box(Modifier.size(28.dp).clip(CircleShape).background(if (isDarkMode) Color(0xFF2D2513) else Color(0xFFFFF6DF)), contentAlignment = Alignment.Center) {
                    val logoUri = customAppLogoUri ?: if (customAppLogo.startsWith("content://") || customAppLogo.startsWith("file://") || customAppLogo.startsWith("http")) customAppLogo else null
                    if (logoUri != null) AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(logoUri).crossfade(true).build(), contentDescription = "Logo", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop) else androidx.compose.foundation.Image(painter = androidx.compose.ui.res.painterResource(id = R.drawable.safa_logo), contentDescription = "SAFA Logo", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                }
                Column {
                    Text(text = customAppName, style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Black, color = contentOnGoldColor), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(text = if (currentLang == "BN") "অপারেটর: $operatorName" else "Operator: $operatorName", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium, fontSize = 11.sp, color = contentOnGoldColor.copy(alpha = 0.8f)), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        },
        actions = {
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = onAccountClick, modifier = Modifier.testTag("appbar_account_switch").size(36.dp)) { Icon(imageVector = Icons.Default.SwitchAccount, contentDescription = "Switch Account", tint = contentOnGoldColor, modifier = Modifier.size(18.dp)) }
            IconButton(onClick = { viewModel.toggleDarkMode() }, modifier = Modifier.testTag("appbar_theme_toggle").size(36.dp)) { Icon(imageVector = if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode, contentDescription = "Switch Theme", tint = contentOnGoldColor, modifier = Modifier.size(18.dp)) }
            IconButton(onClick = { viewModel.toggleLanguage() }, modifier = Modifier.testTag("appbar_lang_toggle").size(36.dp)) { Icon(imageVector = Icons.Default.Language, contentDescription = "Switch Language", tint = contentOnGoldColor, modifier = Modifier.size(18.dp)) }
            IconButton(onClick = onLogoutClick, modifier = Modifier.testTag("appbar_logout_btn").size(36.dp)) { Icon(imageVector = Icons.Default.ExitToApp, contentDescription = "Logout", tint = if (isDarkMode) Color(0xFFF36666) else Color(0xFF860A0A), modifier = Modifier.size(18.dp)) }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = goldBgColor), modifier = Modifier.fillMaxWidth()
    )
}

@Composable
fun SafaBottomNavigationBar(viewModel: SafaViewModel, currentScreen: AppScreen) {
    val isDarkMode by viewModel.isDarkMode.collectAsStateWithLifecycle()
    androidx.compose.material3.Surface(modifier = Modifier.fillMaxWidth(), color = if (isDarkMode) Color(0xFF1E2638) else Color(0xFFFAF8F5), tonalElevation = 0.dp, shadowElevation = 0.dp) {
        NavigationBar(containerColor = Color.Transparent, tonalElevation = 0.dp, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).size(height = 64.dp, width = androidx.compose.ui.unit.Dp.Infinity)) {
            val navItems = listOf(Triple(AppScreen.DASHBOARD, Icons.Default.Home, "dashboard"), Triple(AppScreen.CUSTOMERS, Icons.Default.People, "customers"), Triple(AppScreen.SUPPLIERS, Icons.Default.AccountBalance, "suppliers"), Triple(AppScreen.WALLET, Icons.Default.AccountBalanceWallet, "wallet"), Triple(AppScreen.EXPENSES, Icons.Default.Payments, "expenses"))
            navItems.forEach { item ->
                val screen = item.first; val icon = item.second; val key = item.third; val isSelected = currentScreen == screen
                NavigationBarItem(
                    selected = isSelected, onClick = { viewModel.navigateTo(screen) },
                    icon = { Icon(imageVector = icon, contentDescription = viewModel.t(key), modifier = Modifier.size(20.dp)) },
                    label = { Text(text = viewModel.t(key), style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium, fontSize = 10.sp), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = if (isDarkMode) Color(0xFF6EA8FF) else Color(0xFFA82222), selectedTextColor = if (isDarkMode) Color(0xFF6EA8FF) else Color(0xFFA82222), indicatorColor = if (isDarkMode) Color(0xFF1E293B) else Color(0xFFFFEBEE), unselectedIconColor = if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF666666), unselectedTextColor = if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF666666)), modifier = Modifier.testTag("bottom_nav_$key")
                )
            }
        }
    }
}

@Composable
private fun StartupErrorScreen(presentation: StartupFailurePresentation, onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(24.dp)) {
            Text(presentation.title, style = MaterialTheme.typography.headlineSmall)
            Text(presentation.message)
            Text(presentation.supportLabel, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onRetry) { Text(presentation.retryLabel) }
        }
    }
}
