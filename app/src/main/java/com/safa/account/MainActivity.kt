package com.safa.account

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.safa.account.data.api.TokenManager
import com.safa.account.data.database.AppDatabase
import com.safa.account.data.repository.AppRepository
import com.safa.account.data.network.AutoSyncWorker
import com.safa.account.ui.*
import com.safa.account.ui.screens.auth.LoginScreen
import com.safa.account.ui.screens.dashboard.DashboardScreen
import com.safa.account.ui.screens.customers.CustomerScreen
import com.safa.account.ui.screens.suppliers.SupplierScreen
import com.safa.account.ui.screens.transactions.TransactionScreen
import com.safa.account.ui.screens.wallet.WalletScreen
import com.safa.account.ui.screens.expense.ExpenseIncomeScreen
import com.safa.account.ui.screens.rates.DailyRateScreen
import com.safa.account.ui.screens.profile.ProfileScreen
import com.safa.account.ui.screens.settings.SettingsScreen
import com.safa.account.ui.screens.reports.ReportsScreen
import com.safa.account.ui.screens.splash.SplashScreen
import com.safa.account.ui.viewmodel.SafaViewModel
import com.safa.account.ui.viewmodel.SafaViewModelFactory
import com.safa.account.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val database = AppDatabase.getDatabase(applicationContext)
        val repository = AppRepository(
            database.operatorDao(),
            database.customerDao(),
            database.supplierDao(),
            database.transactionDao(),
            database.supplierDepositDao(),
            database.expenseIncomeDao(),
            database.dailyRateDao(),
            database.walletLedgerDao(),
            database.walletBatchDao(),
            database.syncOutboxDao()
        )
        val tokenManager = TokenManager(applicationContext)
        val viewModel = SafaViewModelFactory(repository, tokenManager).create(SafaViewModel::class.java)

        AutoSyncWorker.schedulePeriodicSync(applicationContext)

        setContent {
            MyApplicationTheme {
                SafaRoot(viewModel)
            }
        }
    }
}

@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun SafaRoot(viewModel: SafaViewModel) {
    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val isAuthenticated by viewModel.isAuthenticated.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val currentOperator by viewModel.currentOperator.collectAsStateWithLifecycle()
    var showLogoutConfirmation by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.initialize()
    }

    LaunchedEffect(isAuthenticated) {
        if (isAuthenticated) viewModel.triggerFullSync { _, _ -> }
    }

    if (!isAuthenticated && !isLoading) {
        LoginScreen(viewModel)
        return
    }

    if (isLoading) {
        SplashScreen()
        return
    }

    Scaffold(
        topBar = {
            if (currentScreen != AppScreen.LOGIN) {
                SafaTopAppBar(
                    viewModel = viewModel,
                    title = currentScreen.title,
                    operatorName = currentOperator?.name ?: "",
                    onLogoutClick = { showLogoutConfirmation = true }
                )
            }
        },
        bottomBar = {
            if (currentScreen in setOf(
                    AppScreen.DASHBOARD,
                    AppScreen.CUSTOMERS,
                    AppScreen.SUPPLIERS,
                    AppScreen.TRANSACTIONS,
                    AppScreen.WALLET
                )) {
                SafaBottomNavigationBar(viewModel, currentScreen)
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            AnimatedContent(
                targetState = currentScreen,
                transitionSpec = {
                    (slideInHorizontally(
                        initialOffsetX = { -it },
                        animationSpec = tween(160, easing = FastOutSlowInEasing)
                    ) + fadeIn(animationSpec = tween(160))) togetherWith
                        (slideOutHorizontally(
                            targetOffsetX = { it },
                            animationSpec = tween(160, easing = FastOutSlowInEasing)
                        ) + fadeOut(animationSpec = tween(160)))
                },
                label = "screenTransition"
            ) { screen ->
                when (screen) {
                    AppScreen.DASHBOARD -> DashboardScreen(viewModel)
                    AppScreen.CUSTOMERS -> CustomerScreen(viewModel, isProfileView = false, isAddView = false)
                    AppScreen.SUPPLIERS -> SupplierScreen(viewModel, isProfileView = false, isAddView = false)
                    AppScreen.TRANSACTIONS -> TransactionScreen(viewModel)
                    AppScreen.WALLET -> WalletScreen(viewModel)
                    AppScreen.EXPENSE_INCOME -> ExpenseIncomeScreen(viewModel, isProfileView = false, isAddView = false)
                    AppScreen.DAILY_RATES -> DailyRateScreen(viewModel)
                    AppScreen.PROFILE -> ProfileScreen(viewModel)
                    AppScreen.SETTINGS -> SettingsScreen(viewModel)
                    AppScreen.REPORTS -> ReportsScreen(viewModel)
                    else -> DashboardScreen(viewModel)
                }
            }
        }
    }

    BackHandler {
        if (currentScreen != AppScreen.DASHBOARD) {
            viewModel.navigateTo(AppScreen.DASHBOARD)
        } else {
            showLogoutConfirmation = true
        }
    }

    if (showLogoutConfirmation) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirmation = false },
            title = { Text("Logout") },
            text = { Text("Are you sure you want to logout?") },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutConfirmation = false
                    viewModel.logout()
                }) { Text("Logout") }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirmation = false }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SafaTopAppBar(
    viewModel: SafaViewModel,
    title: String,
    operatorName: String,
    onLogoutClick: () -> Unit
) {
    val isDarkMode by viewModel.isDarkMode.collectAsStateWithLifecycle()
    TopAppBar(
        title = {
            androidx.compose.foundation.layout.Column {
                Text(if (title.isBlank()) "SAFA" else title)
                if (operatorName.isNotBlank()) {
                    Text("Operator: $operatorName", style = MaterialTheme.typography.labelSmall)
                }
            }
        },
        actions = {
            IconButton(onClick = { viewModel.toggleDarkMode() }) {
                Icon(
                    imageVector = if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                    contentDescription = "Theme"
                )
            }
            IconButton(onClick = onLogoutClick) {
                Icon(Icons.Default.ReceiptLong, contentDescription = "Logout")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SafaBottomNavigationBar(viewModel: SafaViewModel, currentScreen: AppScreen) {
    val items = listOf(
        Triple(AppScreen.DASHBOARD, Icons.Default.Home, "Home"),
        Triple(AppScreen.CUSTOMERS, Icons.Default.People, "Customers"),
        Triple(AppScreen.SUPPLIERS, Icons.Default.Store, "Suppliers"),
        Triple(AppScreen.TRANSACTIONS, Icons.Default.ReceiptLong, "Transactions"),
        Triple(AppScreen.WALLET, Icons.Default.AccountBalanceWallet, "Wallet")
    )
    NavigationBar {
        items.forEach { (screen, icon, label) ->
            NavigationBarItem(
                selected = currentScreen == screen,
                onClick = { viewModel.navigateTo(screen) },
                icon = { Icon(icon, contentDescription = label) },
                label = { Text(label) }
            )
        }
    }
}
