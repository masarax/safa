package com.safa.account

import android.os.Bundle
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
            android.util.Log.i("SafaApp", "STARTUP_050_BEFORE_ROOM")

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
                syncOutboxDao = database.syncOutboxDao(),
            )

            val tokenManager = TokenManager(applicationContext)
            factory = SafaViewModelFactory(repository, tokenManager)

            try {
                AutoSyncWorker.schedulePeriodicSync(applicationContext)
            } catch (e: Throwable) {
                android.util.Log.e("SafaApp", "Failed to schedule AutoSyncWorker", e)
            }

            android.util.Log.i("SafaApp", "STARTUP_100_INITIALIZATION_COMPLETE")
        } catch (t: Throwable) {
            initError = t
            android.util.Log.e("SafaApp", "STARTUP_INIT_FAILED", t)
        }

        val resolvedFactory = factory
        val resolvedError = initError

        setContent {
            if (resolvedError != null || resolvedFactory == null) {
                MyApplicationTheme(darkTheme = false) {
                    StartupErrorScreen(
                        error = resolvedError,
                        onRetry = { recreate() }
                    )
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
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFFF1F1))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "⚠️ SAFA Startup Diagnostic Error",
                style = TextStyle(
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF991B1B)
                )
            )
            Text(
                text = "${error?.javaClass?.simpleName}: ${error?.message}",
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
                if (!viewModel.navigateBack()) showExitDialog = true
            }
        }

        if (showExitDialog) {
            AlertDialog(
                onDismissRequest = { showExitDialog = false },
                title = {
                    Text(if (currentLanguage == "BN") "অ্যাপ থেকে প্রস্থান" else "Exit Application")
                },
                text = {
                    Text(if (currentLanguage == "BN") "আপনি কি নিশ্চিতভাবে অ্যাপ থেকে বের হতে চান?" else "Are you sure you want to exit the application?")
                },
                confirmButton = {
                    TextButton(onClick = {
                        showExitDialog = false
                        // BackHandler will be replaced by activity finish through the dispatcher.
                        // The system back action is intentionally used after confirmation.
                        viewModel.logout()
                    }) {
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
                    if (showBars) {
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
                        .padding(innerPadding)
                        .imePadding()
                ) {
                    AnimatedContent(
                        targetState = currentScreen,
                        transitionSpec = {
                            val backward = navDirection == NavDirection.BACKWARD
                            if (backward) {
                                slideInHorizontally(
                                    initialOffsetX = { -it },
                                    animationSpec = tween(160, easing = FastOutSlowInEasing)
                                ) + fadeIn(tween(110)) togetherWith
                                    slideOutHorizontally(
                                        targetOffsetX = { it },
                                        animationSpec = tween(160, easing = FastOutSlowInEasing)
                                    ) + fadeOut(tween(110))
                            } else {
                                slideInHorizontally(
                                    initialOffsetX = { it },
                                    animationSpec = tween(160, easing = FastOutSlowInEasing)
                                ) + fadeIn(tween(110)) togetherWith
                                    slideOutHorizontally(
                                        targetOffsetX = { -it },
                                        animationSpec = tween(160, easing = FastOutSlowInEasing)
                                    ) + fadeOut(tween(110))
                            }
                        },
                        label = "SafaScreenTransition",
                        modifier = Modifier.fillMaxSize()
                    ) { target ->
                        when (target) {
                            AppScreen.DASHBOARD -> DashboardScreen(viewModel)
                            AppScreen.CUSTOMERS -> CustomerScreen(viewModel, false, false)
                            AppScreen.CUSTOMER_PROFILE -> CustomerScreen(viewModel, true, false)
                            AppScreen.CUSTOMER_ADD -> CustomerScreen(viewModel, false, true)
                            AppScreen.SUPPLIERS -> SupplierScreen(viewModel, false, false)
                            AppScreen.SUPPLIER_PROFILE -> SupplierScreen(viewModel, true, false)
                            AppScreen.SUPPLIER_ADD -> SupplierScreen(viewModel, false, true)
                            AppScreen.TRANSACTIONS -> TransactionScreen(viewModel)
                            AppScreen.WALLET -> WalletScreen(viewModel)
                            AppScreen.EXPENSES -> ExpenseScreen(viewModel, false)
                            AppScreen.EXPENSE_ADD -> ExpenseScreen(viewModel, true)
                            AppScreen.SETTINGS -> SettingsScreen(viewModel)
                            AppScreen.REPORTS -> ReportsScreen(viewModel)
                            else -> DashboardScreen(viewModel)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun SafaTopAppBar(
    viewModel: SafaViewModel,
    title: String,
    operatorName: String,
    onLogoutClick: () -> Unit
) {
    androidx.compose.material3.TopAppBar(
        title = { Text(if (title.isBlank()) "SAFA" else title) },
        actions = {
            androidx.compose.material3.IconButton(onClick = onLogoutClick) {
                androidx.compose.material3.Icon(
                    imageVector = androidx.compose.material.icons.Icons.Default.ExitToApp,
                    contentDescription = "Logout"
                )
            }
        }
    )
}
