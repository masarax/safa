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
import com.safa.account.ui.viewmodel.HundiViewModel
import com.safa.account.ui.viewmodel.HundiViewModelFactory

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
        val factory = HundiViewModelFactory(repository, tokenManager)

        // Schedule periodic background sync when internet is connected
        com.safa.account.data.network.AutoSyncWorker.schedulePeriodicSync(applicationContext)

        enableEdgeToEdge()

        setContent {
            val viewModel: HundiViewModel by viewModels { factory }
            
            val currentLanguage by viewModel.currentLanguage.collectAsState()
            val currentScreen by viewModel.currentScreen.collectAsState()
            val currentOperator by viewModel.currentOperator.collectAsState()
            val isDarkMode by viewModel.isDarkMode.collectAsState()
            val isSubPageActive by viewModel.isSubPageActive.collectAsState()
            val navDirection by viewModel.navDirection.collectAsState()

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
                                style = MaterialTheme.typography.titleMedium
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
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = { showExitDialog = false }
                            ) {
                                Text(
                                    text = if (currentLanguage == "BN") "না" else "No",
                                    fontWeight = FontWeight.Medium
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
                                HundiTopAppBar(
                                    viewModel = viewModel,
                                    title = viewModel.t("app_title"),
                                    operatorName = currentOperator?.username ?: "",
                                    onLogoutClick = { viewModel.logout() }
                                )
                            }
                        },
                        bottomBar = {
                            if (!isKeyboardVisible && showBars) {
                                HundiBottomNavigationBar(
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
fun HundiTopAppBar(
    viewModel: HundiViewModel,
    title: String,
    operatorName: String,
    onLogoutClick: () -> Unit
) {
    val currentLang by viewModel.currentLanguage.collectAsState()
    val isDarkMode by viewModel.isDarkMode.collectAsState()

    // Premium Gold color palette matching TallyKhata Gold header status
    val goldBgColor = if (isDarkMode) Color(0xFF1B1812) else Color(0xFFD7A84B)
    val contentOnGoldColor = if (isDarkMode) Color(0xFFE5C158) else Color(0xFF3E2700)
    val dropdownBgColor = if (isDarkMode) Color(0xFF2B261D) else Color(0xFFFFFFFF)
    val dropdownTextColor = if (isDarkMode) Color(0xFFFFFFFF) else Color(0xFF3E2700)

    val operators by viewModel.operators.collectAsState()
    var showAccountMenu by remember { mutableStateOf(false) }

    TopAppBar(
        title = {
            Box {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .padding(vertical = 4.dp)
                        .clickable { showAccountMenu = !showAccountMenu }
                ) {
                    // Crown dynamic indicator
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(if (isDarkMode) Color(0xFF2D2513) else Color(0xFFFFF6DF)),
                        contentAlignment = Alignment.Center
                    ) {
                        val customAppLogo by viewModel.customAppLogo.collectAsState()
                        Text(text = customAppLogo, fontSize = 14.sp)
                    }

                    Column {
                        val customAppName by viewModel.customAppName.collectAsState()
                        Text(
                            text = customAppName,
                            style = TextStyle(
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Black,
                                color = contentOnGoldColor
                            )
                        )
                        Text(
                            text = if (currentLang == "BN") "অপারেটর: $operatorName" else "Operator: $operatorName",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.Medium,
                                fontSize = 11.sp,
                                color = contentOnGoldColor.copy(alpha = 0.8f)
                            )
                        )
                    }
                }

                DropdownMenu(
                    expanded = showAccountMenu,
                    onDismissRequest = { showAccountMenu = false },
                    modifier = Modifier
                        .background(dropdownBgColor)
                        .border(1.dp, if (isDarkMode) Color(0xFF222222) else Color(0xFFEEEEEE), RoundedCornerShape(8.dp))
                ) {
                    Text(
                        text = if (currentLang == "BN") "ব্যবহারকারী নির্বাচন করুন" else "Switch Active Account",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = dropdownTextColor.copy(alpha = 0.6f)
                        ),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    
                    operators.forEach { op ->
                        DropdownMenuItem(
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = if (op.username == operatorName) Icons.Default.CheckCircle else Icons.Default.AccountCircle,
                                        contentDescription = null,
                                        tint = if (op.username == operatorName) Color(0xFF43A047) else dropdownTextColor.copy(alpha = 0.5f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Column {
                                        Text(
                                            text = op.username,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = dropdownTextColor
                                            )
                                        )
                                        Text(
                                            text = if (op.role == "ADMIN") "🔒 Admin" else "👥 Staff",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = dropdownTextColor.copy(alpha = 0.5f),
                                                fontSize = 9.sp
                                            )
                                        )
                                    }
                                }
                            },
                            onClick = {
                                showAccountMenu = false
                                viewModel.switchOperatorDirectly(op)
                            }
                        )
                    }

                    Divider(color = if (isDarkMode) Color(0xFF42392B) else Color(0xFFEEEEEE))

                    DropdownMenuItem(
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = null,
                                    tint = if (isDarkMode) Color(0xFFE5C158) else Color(0xFF3E2700),
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = if (currentLang == "BN") "সেটিংস" else "Settings",
                                    color = dropdownTextColor,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        },
                        onClick = {
                            showAccountMenu = false
                            viewModel.navigateTo(AppScreen.SETTINGS)
                        }
                    )

                    Divider(color = if (isDarkMode) Color(0xFF42392B) else Color(0xFFEEEEEE))

                    DropdownMenuItem(
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Logout,
                                    contentDescription = null,
                                    tint = Color(0xFFE53935),
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = if (currentLang == "BN") "লগআউট করুন" else "Lock / Logout",
                                    color = Color(0xFFE53935),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        },
                        onClick = {
                            showAccountMenu = false
                            onLogoutClick()
                        }
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
fun HundiBottomNavigationBar(
    viewModel: HundiViewModel,
    currentScreen: AppScreen
) {
    val isDarkMode by viewModel.isDarkMode.collectAsState()

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
                            maxLines = 1
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
