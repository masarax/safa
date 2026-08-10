package com.safa.account

import android.app.Activity
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.safa.account.data.api.TokenManager
import com.safa.account.data.database.AppDatabase
import com.safa.account.data.network.AutoSyncWorker
import com.safa.account.data.repository.AppRepository
import com.safa.account.ui.screens.*
import com.safa.account.ui.theme.MyApplicationTheme
import com.safa.account.ui.viewmodel.AppScreen
import com.safa.account.ui.viewmodel.NavDirection
import com.safa.account.ui.viewmodel.SafaViewModel
import com.safa.account.ui.viewmodel.SafaViewModelFactory

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        android.util.Log.i("SafaApp", "STARTUP_010_ACTIVITY_CREATED")
        super.onCreate(savedInstanceState)

        var initError: Throwable? = null
        var factory: SafaViewModelFactory? = null

        try {
            enableEdgeToEdge()
            val database = AppDatabase.getDatabase(applicationContext)
            val repository = AppRepository(
                operatorDao = database.operatorDao(),
                customerDao = database.customerDao(),
                supplierDao = database.supplierDao(),
                transactionDao = database.transactionDao(),
                supplierDepositDao = database.supplierDepositDao(),
                expenseIncomeDao = database.expenseIncomeDao(),
                dailyRateDao = database.dailyRateDao(),
                walletLedgerDao = database.walletLedgerDao(),
                walletBatchDao = database.walletBatchDao(),
                syncOutboxDao = database.syncOutboxDao()
            )
            val tokenManager = TokenManager(applicationContext)
            factory = SafaViewModelFactory(repository, tokenManager)
            AutoSyncWorker.schedulePeriodicSync(applicationContext)
        } catch (t: Throwable) {
            initError = t
            android.util.Log.e("SafaApp", "STARTUP_INIT_FAILED", t)
        }

        val resolvedFactory = factory
        val resolvedError = initError
        setContent {
            if (resolvedError != null || resolvedFactory == null) {
                MyApplicationTheme(darkTheme = false) {
                    StartupErrorScreen(resolvedError) { recreate() }
                }
            } else {
                val viewModel: SafaViewModel by viewModels { resolvedFactory }
                SafaRoot(viewModel)
            }
        }
    }
}

@Composable
private fun StartupErrorScreen(error: Throwable?, onRetry: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xFFFFF1F1)).padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "⚠️ SAFA Startup Diagnostic Error",
                style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF991B1B))
            )
            Text(
                "${error?.javaClass?.simpleName}: ${error?.message}",
                style = TextStyle(fontSize = 14.sp, color = Color(0xFF7F1D1D))
            )
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
            ) {
                Text("Retry Application Startup", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SafaRoot(viewModel: SafaViewModel) {
    val currentLanguage by viewModel.currentLanguage.collectAsStateWithLifecycle()
    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val currentOperator by viewModel.currentOperator.collectAsStateWithLifecycle()
    val isDarkMode by viewModel.isDarkMode.collectAsStateWithLifecycle()
    val isSubPageActive by viewModel.isSubPageActive.collectAsStateWithLifecycle()
    val navDirection by viewModel.navDirection.collectAsStateWithLifecycle()
    val activity = androidx.compose.ui.platform.LocalContext.current as? Activity

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
            BackHandler {
                if (!viewModel.navigateBack()) showExitDialog = true
            }
        }

        if (showExitDialog) {
            AlertDialog(
                onDismissRequest = { showExitDialog = false },
                title = { Text(if (currentLanguage == "BN") "অ্যাপ থেকে প্রস্থান" else "Exit Application") },
                text = { Text(if (currentLanguage == "BN") "আপনি কি নিশ্চিতভাবে অ্যাপ থেকে বের হতে চান?" else "Are you sure you want to exit the application?") },
                confirmButton = {
                    TextButton(onClick = { showExitDialog = false; activity?.finish() }) {
                        Text(if (currentLanguage == "BN") "হ্যাঁ" else "Yes")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showExitDialog = false }) {
                        Text(if (currentLanguage == "BN") "না" else "No")
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
                    if (showBars) SafaBottomNavigationBar(viewModel, currentScreen)
                }
            ) { innerPadding ->
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(innerPadding)
                ) {
                    AnimatedContent(
                        targetState = currentScreen,
                        transitionSpec = {
                            val backward = navDirection == NavDirection.BACKWARD
                            if (backward) {
                                slideInHorizontally(
                                    initialOffsetX = { -it },
                                    animationSpec = tween(160, easing = FastOutSlowInEasing)
                                ) + fadeIn(animationSpec = tween(110)) togetherWith
                                    slideOutHorizontally(
                                        targetOffsetX = { it },
                                        animationSpec = tween(160, easing = FastOutSlowInEasing)
                                    ) + fadeOut(animationSpec = tween(110))
                            } else {
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
                        label = "SafaScreenTransition",
                        modifier = Modifier.fillMaxSize()
                    ) { target ->
                        when (target) {
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
            Column {
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
