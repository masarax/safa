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
import com.safa.account.data.api.AuthLifecycleCoordinator
import com.safa.account.data.network.DeleteConfirmationCoordinator
import com.safa.account.data.network.DeleteConfirmationRequest
import com.safa.account.data.repository.AppRepository
import com.safa.account.ui.screens.*
import com.safa.account.ui.theme.MyApplicationTheme
import com.safa.account.ui.viewmodel.AppScreen
import com.safa.account.ui.viewmodel.NavDirection
import com.safa.account.ui.viewmodel.SafaViewModel
import com.safa.account.ui.viewmodel.SafaViewModelFactory
import com.safa.account.utils.SafaLogger

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
            if (BuildConfig.DEBUG) {
                SafaLogger.error("FATAL_STARTUP_ERROR", "support_id=$diagnosticId", t)
            } else {
                SafaLogger.error("FATAL_STARTUP_ERROR", "support_id=$diagnosticId")
            }
            setContent {
                MyApplicationTheme(darkTheme = false) {
                    StartupErrorScreen(
                        diagnosticId = diagnosticId,
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
    var showExitDialog by remember { mutableStateOf(false) }
    var previousOperatorId by remember { mutableStateOf<Int?>(currentOperator?.id) }
    var isRefreshing by remember { mutableStateOf(false) }
    var deleteConfirmation by remember { mutableStateOf<DeleteConfirmationRequest?>(null) }

    LaunchedEffect(currentOperator?.id) {
        val previous = previousOperatorId
        val current = currentOperator?.id
        if (previous != null && current == null) {
            viewModel.tokenManager?.let { AuthLifecycleCoordinator(it).logout() }
        }
        previousOperatorId = current
    }

    LaunchedEffect(Unit) {
        viewModel.tokenManager?.sessionInvalidated?.collect { viewModel.logout() }
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
            fun bool(key: String, default: Boolean = false): Boolean {
                val v = user[key] ?: return default
                return v == true || v.toString().equals("true", true) || v.toString() == "1"
            }
            @Suppress("UNCHECKED_CAST")
            val permissions = user["permissions"] as? Map<String, Any?> ?: emptyMap()
            fun perm(key: String): Boolean = permissions[key]?.let { it == true || it.toString().equals("true", true) || it.toString() == "1" } ?: false
            val role = user["role"]?.toString().orEmpty().let { raw ->
                when {
                    raw.equals("superadmin", true) || raw.equals("manager", true) || raw.equals("owner", true) -> "SuperAdmin"
                    raw.equals("admin", true) -> "Admin"
                    else -> "Staff"
                }
            }
            val userId = (user["id"] as? Number)?.toInt() ?: 0
            val mobile = user["mobile"]?.toString().orEmpty()
            val restored = com.safa.account.data.model.OperatorAccount(
                id = userId,
                username = user["name"]?.toString().orEmpty(),
                role = role,
                mobile = mobile,
                email = user["email"]?.toString().orEmpty(),
                isActivated = bool("is_activated", true),
                isActive = bool("is_activated", true),
                isBiometricEnabled = tm.isBiometricQuickUnlockBoundTo(userId, mobile),
                canViewCustomers = perm("can_view_customers"),
                canAddCustomers = perm("can_add_customers"),
                canEditCustomers = perm("can_edit_customers"),
                canDeleteCustomers = perm("can_delete_customers"),
                canViewSuppliers = perm("can_view_suppliers"),
                canAddSuppliers = perm("can_add_suppliers"),
                canEditSuppliers = perm("can_edit_suppliers"),
                canDeleteSuppliers = perm("can_delete_suppliers"),
                canViewTransactions = perm("can_view_transactions"),
                canAddTransactions = perm("can_add_transactions"),
                canEditTransactions = perm("can_edit_transactions"),
                canDeleteTransactions = perm("can_delete_transactions"),
                canManageWallet = perm("can_manage_wallet"),
                canManageExpenses = perm("can_manage_expenses"),
                canViewReports = perm("can_view_reports")
            )
            if (restored.id > 0 && restored.isActive) {
                tm.saveLastMobile(restored.mobile)
                viewModel.switchOperatorDirectly(restored)
            }
        }
    }

    val isMainScreen = currentScreen in setOf(AppScreen.DASHBOARD, AppScreen.CUSTOMERS, AppScreen.SUPPLIERS, AppScreen.WALLET, AppScreen.EXPENSES)
    val showBars = isMainScreen && !isSubPageActive

    MyApplicationTheme(darkTheme = isDarkMode) {
        if (currentScreen != AppScreen.LOCK_SCREEN) {
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

        if (currentScreen == AppScreen.LOCK_SCREEN) {
            LoginScreen(viewModel = viewModel)
        } else {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                topBar = { if (showBars) SafaTopAppBar(viewModel, viewModel.t("app_title"), currentOperator?.username ?: "") { viewModel.logout() } },
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
fun SafaTopAppBar(viewModel: SafaViewModel, title: String, operatorName: String, onLogoutClick: () -> Unit) {
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
private fun StartupErrorScreen(diagnosticId: String, onRetry: () -> Unit) {
    val context = LocalContext.current
    val language = context.resources.configuration.locales[0]?.language.orEmpty()
    val isBangla = language.equals("bn", ignoreCase = true)
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(24.dp)) {
            Text(if (isBangla) "SAFA চালু করা যায়নি" else "SAFA could not start", style = MaterialTheme.typography.headlineSmall)
            Text(if (isBangla) "অ্যাপটি চালু করতে সমস্যা হয়েছে। আবার চেষ্টা করুন।" else "The app could not be initialized. Please try again.")
            Text(if (isBangla) "সহায়তা আইডি: $diagnosticId" else "Support ID: $diagnosticId", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onRetry) { Text(if (isBangla) "আবার চেষ্টা করুন" else "Retry") }
        }
    }
}
