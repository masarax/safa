package com.safa.account
import com.safa.account.ui.localization.AndroidStringCatalog

import android.animation.ValueAnimator
import android.os.Build
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
import com.safa.account.ui.components.StartupFailurePolicy
import com.safa.account.ui.components.StartupFailurePresentation
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
    val animationsEnabled = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ValueAnimator.areAnimatorsEnabled()
        } else {
            true
        }
    }
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

    // Fresh logins already persist the server-issued owned account ID. Only
    // legacy/restored sessions that genuinely lack account context need this
    // silent compatibility bootstrap; normal login never waits for /accounts.
    LaunchedEffect(currentOperator?.id) {
        val operator = currentOperator ?: return@LaunchedEffect
        if (!operator.isActive) return@LaunchedEffect
        val tm = viewModel.tokenManager ?: return@LaunchedEffect
        if (tm.getActiveAccountId() != null) return@LaunchedEffect

        viewModel.repository.clearLocalPresentation()
        viewModel.syncManager?.listAccounts()?.exceptionOrNull()?.let { error ->
            SafaLogger.error("ACCOUNT_BOOTSTRAP_FAILED", "Automatic account bootstrap failed", error)
        }
    }

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
        if (currentScreen != AppScreen.LOCK_SCREEN) {
            BackHandler { if (!viewModel.navigateBack()) showExitDialog = true }
        }

        if (showExitDialog) {
            AlertDialog(
                onDismissRequest = { showExitDialog = false },
                title = { Text(text = AndroidStringCatalog.get(currentLanguage, "inline_mainactivity_5eb55a54e9"), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                text = { Text(text = AndroidStringCatalog.get(currentLanguage, "inline_mainactivity_f530537e68"), style = MaterialTheme.typography.bodyMedium) },
                confirmButton = { TextButton(onClick = { showExitDialog = false; onExit() }) { Text(text = AndroidStringCatalog.get(currentLanguage, "inline_mainactivity_23cb748919"), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
                dismissButton = { TextButton(onClick = { showExitDialog = false }) { Text(text = AndroidStringCatalog.get(currentLanguage, "inline_mainactivity_e4e921c93d"), fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis) } }
            )
        }

        deleteConfirmation?.let { request ->
            AlertDialog(
                onDismissRequest = { DeleteConfirmationCoordinator.resolve(request.id, false); deleteConfirmation = null },
                title = { Text(if (currentLanguage == "BN") "ডাটা মুছে ফেলবেন?" else request.title, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium) },
                text = { Text(if (currentLanguage == "BN") "এই কাজটি পূর্বাবস্থায় ফেরানো যাবে না।" else request.message, style = MaterialTheme.typography.bodyMedium) },
                confirmButton = { TextButton(onClick = { DeleteConfirmationCoordinator.resolve(request.id, true); deleteConfirmation = null }) { Text(AndroidStringCatalog.get(currentLanguage, "inline_mainactivity_f737c0b14e"), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) } },
                dismissButton = { TextButton(onClick = { DeleteConfirmationCoordinator.resolve(request.id, false); deleteConfirmation = null }) { Text(AndroidStringCatalog.get(currentLanguage, "inline_mainactivity_4755950621")) } }
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
                                if (!animationsEnabled) {
                                    fadeIn(animationSpec = tween(0)) togetherWith fadeOut(animationSpec = tween(0))
                                } else {
                                    val isBackward = navDirection == NavDirection.BACKWARD
                                    if (isBackward) {
                                        slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(160, easing = FastOutSlowInEasing)) + fadeIn(animationSpec = tween(110)) togetherWith slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(160, easing = FastOutSlowInEasing)) + fadeOut(animationSpec = tween(110))
                                    } else {
                                        slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(160, easing = FastOutSlowInEasing)) + fadeIn(animationSpec = tween(110)) togetherWith slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween(160, easing = FastOutSlowInEasing)) + fadeOut(animationSpec = tween(110))
                                    }
                                }
                            },
                            label = "SafaTransition",
                            modifier = Modifier.fillMaxSize()
                        ) { targetScreen ->
                            val operator = currentOperator
                            when (targetScreen) {
                                AppScreen.DASHBOARD -> DashboardScreen(viewModel = viewModel)
                                AppScreen.CUSTOMERS -> if (operator?.canViewCustomers == true) CustomerScreen(viewModel = viewModel, isProfileView = false, isAddView = false) else DashboardScreen(viewModel)
                                AppScreen.CUSTOMER_PROFILE -> if (operator?.canViewCustomers == true) CustomerScreen(viewModel = viewModel, isProfileView = true, isAddView = false) else DashboardScreen(viewModel)
                                AppScreen.CUSTOMER_ADD -> if (operator?.canAddCustomers == true) CustomerScreen(viewModel = viewModel, isProfileView = false, isAddView = true) else DashboardScreen(viewModel)
                                AppScreen.SUPPLIERS -> if (operator?.canViewSuppliers == true) SupplierScreen(viewModel = viewModel, isProfileView = false, isAddView = false) else DashboardScreen(viewModel)
                                AppScreen.SUPPLIER_PROFILE -> if (operator?.canViewSuppliers == true) SupplierScreen(viewModel = viewModel, isProfileView = true, isAddView = false) else DashboardScreen(viewModel)
                                AppScreen.SUPPLIER_ADD -> if (operator?.canAddSuppliers == true) SupplierScreen(viewModel = viewModel, isProfileView = false, isAddView = true) else DashboardScreen(viewModel)
                                AppScreen.TRANSACTIONS -> if (operator?.canViewTransactions == true) TransactionScreen(viewModel = viewModel) else DashboardScreen(viewModel)
                                AppScreen.WALLET -> if (operator?.canManageWallet == true) WalletScreen(viewModel = viewModel) else DashboardScreen(viewModel)
                                AppScreen.EXPENSES -> if (operator?.canManageExpenses == true) ExpenseScreen(viewModel = viewModel, isAddingEntryView = false) else DashboardScreen(viewModel)
                                AppScreen.EXPENSE_ADD -> if (operator?.canManageExpenses == true) ExpenseScreen(viewModel = viewModel, isAddingEntryView = true) else DashboardScreen(viewModel)
                                AppScreen.SETTINGS -> RoleAwareSettingsScreen(viewModel = viewModel)
                                AppScreen.REPORTS -> if (operator?.canViewReports == true) ReportsScreen(viewModel = viewModel) else DashboardScreen(viewModel)
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
                    Text(text = AndroidStringCatalog.get(currentLang, "inline_mainactivity_64a08dc8e8"), style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium, fontSize = 11.sp, color = contentOnGoldColor.copy(alpha = 0.8f)), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        },
        actions = {
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = { viewModel.toggleDarkMode() }, modifier = Modifier.testTag("appbar_theme_toggle").size(36.dp)) { Icon(imageVector = if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode, contentDescription = AndroidStringCatalog.get(currentLang, "inline_mainactivity_08cc868b45"), tint = contentOnGoldColor, modifier = Modifier.size(18.dp)) }
            IconButton(onClick = { viewModel.toggleLanguage() }, modifier = Modifier.testTag("appbar_lang_toggle").size(36.dp)) { Icon(imageVector = Icons.Default.Language, contentDescription = AndroidStringCatalog.get(currentLang, "inline_mainactivity_0df4700dea"), tint = contentOnGoldColor, modifier = Modifier.size(18.dp)) }
            IconButton(onClick = onLogoutClick, modifier = Modifier.testTag("appbar_logout_btn").size(36.dp)) { Icon(imageVector = Icons.Default.ExitToApp, contentDescription = AndroidStringCatalog.get(currentLang, "inline_mainactivity_408a03a0f0"), tint = if (isDarkMode) Color(0xFFF36666) else Color(0xFF860A0A), modifier = Modifier.size(18.dp)) }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = goldBgColor), modifier = Modifier.fillMaxWidth()
    )
}

@Composable
fun SafaBottomNavigationBar(viewModel: SafaViewModel, currentScreen: AppScreen) {
    val isDarkMode by viewModel.isDarkMode.collectAsStateWithLifecycle()
    val operator by viewModel.currentOperator.collectAsStateWithLifecycle()
    androidx.compose.material3.Surface(modifier = Modifier.fillMaxWidth(), color = if (isDarkMode) Color(0xFF1E2638) else Color(0xFFFAF8F5), tonalElevation = 0.dp, shadowElevation = 0.dp) {
        NavigationBar(containerColor = Color.Transparent, tonalElevation = 0.dp, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).size(height = 64.dp, width = androidx.compose.ui.unit.Dp.Infinity)) {
            val navItems = buildList {
                add(Triple(AppScreen.DASHBOARD, Icons.Default.Home, "dashboard"))
                if (operator?.canViewCustomers == true) add(Triple(AppScreen.CUSTOMERS, Icons.Default.People, "customers"))
                if (operator?.canViewSuppliers == true) add(Triple(AppScreen.SUPPLIERS, Icons.Default.AccountBalance, "suppliers"))
                if (operator?.canManageWallet == true) add(Triple(AppScreen.WALLET, Icons.Default.AccountBalanceWallet, "wallet"))
                if (operator?.canManageExpenses == true) add(Triple(AppScreen.EXPENSES, Icons.Default.Payments, "expenses"))
            }
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
