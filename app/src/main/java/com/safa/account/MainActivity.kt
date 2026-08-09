package com.safa.account

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.safa.account.data.database.AppDatabase
import com.safa.account.data.repository.AppRepository
import com.safa.account.ui.screens.*
import com.safa.account.ui.theme.MyApplicationTheme
import com.safa.account.ui.viewmodel.AppScreen
import com.safa.account.ui.viewmodel.NavDirection
import com.safa.account.ui.viewmodel.SafaViewModel
import com.safa.account.ui.viewmodel.SafaViewModelFactory

class MainActivity : FragmentActivity() {

    @OptIn(ExperimentalLayoutApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Setup local Room DB reactive layer & API TokenManager
        val database = AppDatabase.getDatabase(applicationContext, lifecycleScope)
        val repository = AppRepository(
            operatorDao           = database.operatorDao(),
            customerDao           = database.customerDao(),
            supplierDao           = database.supplierDao(),
            transactionDao        = database.transactionDao(),
            supplierDepositDao    = database.supplierDepositDao(),
            expenseIncomeDao      = database.expenseIncomeDao(),
            dailyRateDao          = database.dailyRateDao(),
            walletLedgerDao       = database.walletLedgerDao(),
            walletBatchDao        = database.walletBatchDao(),
        )
        val tokenManager = com.safa.account.data.api.TokenManager(applicationContext)
        val factory = SafaViewModelFactory(repository, tokenManager)

        // Schedule periodic background sync when internet is connected
        try {
            com.safa.account.data.network.AutoSyncWorker.schedulePeriodicSync(applicationContext)
        } catch (e: Exception) {
            android.util.Log.e("SafaApp", "Failed to schedule AutoSyncWorker: ${e.message}")
        }

        enableEdgeToEdge()

        setContent {
            val viewModel: SafaViewModel by viewModels { factory }
            
            val currentLanguage by viewModel.currentLanguage.collectAsStateWithLifecycle()
            val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
            val currentOperator by viewModel.currentOperator.collectAsStateWithLifecycle()
            val isDarkMode by viewModel.isDarkMode.collectAsStateWithLifecycle()
            val isSubPageActive by viewModel.isSubPageActive.collectAsStateWithLifecycle()
            val navDirection by viewModel.navDirection.collectAsStateWithLifecycle()

            val isMainScreen = currentScreen in listOf(
                AppScreen.DASHBOARD,
                AppScreen.CUSTOMERS,
                AppScreen.SUPPLIERS,
                AppScreen.WALLET,
                AppScreen.EXPENSES
            )
            val showBars = isMainScreen && !isSubPageActive

            MyApplicationTheme(darkTheme = isDarkMode) {
                var showExitDialog by remember { mutableStateOf(false) }

                if (currentScreen != AppScreen.LOCK_SCREEN) {
                    androidx.activity.compose.BackHandler {
                        val handled = viewModel.navigateBack()
                        if (!handled) {
                            showExitDialog = true
                        }
                    }
                }

                if (showExitDialog) {
                    AlertDialog(
                        onDismissRequest = { showExitDialog = false },
                        title = {
                            Text(
                                text = if (currentLanguage == "BN") "অ্যাপ থেকে প্রস্থান" else "Exit Application",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        },
                        text = {
                            Text(
                                text = if (currentLanguage == "BN") "আপনি কি নিশ্চিতভাবে অ্যাপ থেকে বের হতে চান?" else "Are you sure you want to exit the application?",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    showExitDialog = false
                                    this@MainActivity.finish()
                                }
                            ) {
                                Text(
                                    text = if (currentLanguage == "BN") "হ্যাঁ, বের হব" else "Yes, Exit",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = { showExitDialog = false }
                            ) {
                                Text(
                                    text = if (currentLanguage == "BN") "না" else "No",
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                        }
                    )
                }

                if (currentScreen == AppScreen.LOCK_SCREEN) {
                    LoginScreen(viewModel = viewModel)
                } else {
                    val isKeyboardVisible = WindowInsets.isImeVisible
                    
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        topBar = {
                            if (showBars) {
                                SafaTopAppBar(
                                    viewModel = viewModel,
                                    title = viewModel.t("app_title"),
                                    operatorName = currentOperator?.username ?: "",
                                    onLogoutClick = { viewModel.logout() }
                                )
                            }
                        },
                        bottomBar = {
                            if (!isKeyboardVisible && showBars) {
                                SafaBottomNavigationBar(
                                    viewModel = viewModel,
                                    currentScreen = currentScreen
                                )
                            }
                        }
                    ) { innerPadding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background)
                                .padding(
                                    top = innerPadding.calculateTopPadding(),
                                    bottom = if (isKeyboardVisible) 0.dp else innerPadding.calculateBottomPadding()
                                )
                                .imePadding()
                        ) {
                            AnimatedContent(
                                targetState = currentScreen,
                                transitionSpec = {
                                    val isBackward = navDirection == NavDirection.BACKWARD
                                    if (isBackward) {
                                        // Slide back (left to right swap)
                                        slideInHorizontally(
                                            initialOffsetX = { -it },
                                            animationSpec = tween(160, easing = FastOutSlowInEasing)
                                        ) + fadeIn(animationSpec = tween(110)) togetherWith
                                        slideOutHorizontally(
                                            targetOffsetX = { it },
                                            animationSpec = tween(160, easing = FastOutSlowInEasing)
                                        ) + fadeOut(animationSpec = tween(110))
                                    } else {
                                        // Slide forward (right to left swap)
                                        slideInHorizontally(
                                            initialOffsetX = { it },
                                            animationSpec = tween(160, easing = FastOutSlowInEasing)
                                        ) + fadeIn(animationSpec = tween(110)) togetherWith
                                        slideOutHorizontally(
                                            targetOffsetX = { -it },
                                            animationSpec = tween(160, easing = FastOutSlowInEasing)
                                        ) + fadeOut(animationSpec = tween(110))
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
                                    else -> DashboardScreen(viewModel = viewModel)
                                }
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

    // Premium Gold color palette matching TallyKhata Gold header status
    val goldBgColor = if (isDarkMode) Color(0xFF1B1812) else Color(0xFFD7A84B)
    val contentOnGoldColor = if (isDarkMode) Color(0xFFE5C158) else Color(0xFF3E2700)
    val dropdownBgColor = if (isDarkMode) Color(0xFF2B261D) else Color(0xFFFFFFFF)
    val dropdownTextColor = if (isDarkMode) Color(0xFFFFFFFF) else Color(0xFF3E2700)

    val operators by viewModel.operators.collectAsStateWithLifecycle()
    var showAccountMenu by remember { mutableStateOf(false) }

    TopAppBar(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .padding(vertical = 4.dp)
                    .clickable { viewModel.navigateTo(AppScreen.SETTINGS) }
            ) {
                // Crown dynamic indicator
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(if (isDarkMode) Color(0xFF2D2513) else Color(0xFFFFF6DF)),
                    contentAlignment = Alignment.Center
                ) {
                    val customAppLogo by viewModel.customAppLogo.collectAsStateWithLifecycle()
                    val customAppLogoUri by viewModel.customAppLogoUri.collectAsStateWithLifecycle()
                    val logoUri = customAppLogoUri ?: if (customAppLogo.startsWith("content://") || customAppLogo.startsWith("file://") || customAppLogo.startsWith("http")) customAppLogo else null

                    if (logoUri != null) {
                        coil.compose.AsyncImage(
                            model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                                .data(logoUri)
                                .crossfade(true)
                                .build(),
                            contentDescription = "Logo",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    } else {
                        androidx.compose.foundation.Image(
                            painter = androidx.compose.ui.res.painterResource(id = R.drawable.ic_launcher_foreground),
                            contentDescription = "SAFA Logo",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Fit
                        )
                    }
                }

                Column {
                    val customAppName by viewModel.customAppName.collectAsStateWithLifecycle()
                    Text(
                        text = customAppName,
                        style = TextStyle(
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            color = contentOnGoldColor
                        ),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (currentLang == "BN") "অপারেটর: $operatorName" else "Operator: $operatorName",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 11.sp,
                            color = contentOnGoldColor.copy(alpha = 0.8f)
                        ),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }
        },
        actions = {

            Spacer(modifier = Modifier.width(4.dp))

            // Dynamic Option controllers (Theme & Language Toggle)
            IconButton(
                onClick = { viewModel.toggleDarkMode() },
                modifier = Modifier.testTag("appbar_theme_toggle").size(36.dp)
            ) {
                Icon(
                    imageVector = if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                    contentDescription = "Switch Theme",
                    tint = contentOnGoldColor,
                    modifier = Modifier.size(18.dp)
                )
            }

            IconButton(
                onClick = { viewModel.toggleLanguage() },
                modifier = Modifier.testTag("appbar_lang_toggle").size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Language,
                    contentDescription = "Switch Language",
                    tint = contentOnGoldColor,
                    modifier = Modifier.size(18.dp)
                )
            }

            IconButton(
                onClick = onLogoutClick,
                modifier = Modifier.testTag("appbar_logout_btn").size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ExitToApp,
                    contentDescription = "Logout",
                    tint = if (isDarkMode) Color(0xFFF36666) else Color(0xFF860A0A),
                    modifier = Modifier.size(18.dp)
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = goldBgColor
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
fun SafaBottomNavigationBar(
    viewModel: SafaViewModel,
    currentScreen: AppScreen
) {
    val isDarkMode by viewModel.isDarkMode.collectAsStateWithLifecycle()

    androidx.compose.material3.Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (isDarkMode) Color(0xFF1E2638) else Color(0xFFFAF8F5),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        NavigationBar(
            containerColor = Color.Transparent,
            tonalElevation = 0.dp,
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 16.dp)
                .height(64.dp)
        ) {
            val navItems = listOf(
                Triple(AppScreen.DASHBOARD, Icons.Default.Home, "dashboard"),
                Triple(AppScreen.CUSTOMERS, Icons.Default.People, "customers"),
                Triple(AppScreen.SUPPLIERS, Icons.Default.AccountBalance, "suppliers"),
                Triple(AppScreen.WALLET, Icons.Default.AccountBalanceWallet, "wallet"),
                Triple(AppScreen.EXPENSES, Icons.Default.Payments, "expenses")
            )

            navItems.forEach { item ->
                val screen = item.first
                val icon = item.second
                val key = item.third
                val isSelected = currentScreen == screen

                NavigationBarItem(
                    selected = isSelected,
                    onClick = { viewModel.navigateTo(screen) },
                    icon = {
                        Icon(
                            imageVector = icon,
                            contentDescription = viewModel.t(key),
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    label = {
                        Text(
                            text = viewModel.t(key),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                fontSize = 10.sp
                            ),
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = if (isDarkMode) Color(0xFF6EA8FF) else Color(0xFFA82222), // Crimson icon
                        selectedTextColor = if (isDarkMode) Color(0xFF6EA8FF) else Color(0xFFA82222), // Crimson text
                        indicatorColor = if (isDarkMode) Color(0xFF1E293B) else Color(0xFFFFEBEE), // Soft pink/rose pill indicator
                        unselectedIconColor = if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF666666),
                        unselectedTextColor = if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF666666)
                    ),
                    modifier = Modifier.testTag("bottom_nav_$key")
                )
            }
        }
    }
}
