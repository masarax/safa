package com.safa.account.benchmark

import android.os.Bundle
import androidx.activity.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.safa.account.SafaBottomNavigationBar
import com.safa.account.SafaTopAppBar
import com.safa.account.data.repository.AppRepository
import com.safa.account.ui.screens.CustomerScreen
import com.safa.account.ui.screens.DashboardScreen
import com.safa.account.ui.screens.ExpenseScreen
import com.safa.account.ui.screens.SupplierScreen
import com.safa.account.ui.screens.TransactionScreen
import com.safa.account.ui.screens.WalletScreen
import com.safa.account.ui.theme.MyApplicationTheme
import com.safa.account.ui.viewmodel.AppScreen
import com.safa.account.ui.viewmodel.SafaViewModel
import com.safa.account.ui.viewmodel.SafaViewModelFactory
import kotlinx.coroutines.launch

/**
 * Benchmark-only local shell for deterministic production-screen journeys.
 *
 * This class is compiled only into the `benchmark` app variant. It constructs
 * [SafaViewModel] without a TokenManager, so the synthetic local operator cannot
 * contact authentication services or persist reusable auth tokens.
 */
class BenchmarkHostActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val repository = AppRepository(applicationContext)
        val viewModel = ViewModelProvider(
            this,
            SafaViewModelFactory(repository, tokenManager = null),
        )[SafaViewModel::class.java]

        lifecycleScope.launch {
            viewModel.setLanguage("EN")
            check(viewModel.restoreAuthenticatedSession(benchmarkOperator())) {
                "Benchmark operator contract was rejected"
            }
            setContent { BenchmarkRoot(viewModel) }
        }
    }

    private fun benchmarkOperator(): Map<String, Any?> = mapOf(
        "id" to 1,
        "name" to "Benchmark Operator",
        "mobile" to "+966000000000",
        "role" to "staff",
        "is_activated" to true,
        "permissions" to mapOf(
            "can_view_customers" to true,
            "can_add_customers" to true,
            "can_edit_customers" to true,
            "can_delete_customers" to true,
            "can_view_suppliers" to true,
            "can_add_suppliers" to true,
            "can_edit_suppliers" to true,
            "can_delete_suppliers" to true,
            "can_view_transactions" to true,
            "can_add_transactions" to true,
            "can_edit_transactions" to true,
            "can_delete_transactions" to true,
            "can_manage_wallet" to true,
            "can_manage_expenses" to true,
            "can_view_reports" to true,
        ),
    )
}

@Composable
private fun BenchmarkRoot(viewModel: SafaViewModel) {
    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val currentOperator by viewModel.currentOperator.collectAsStateWithLifecycle()
    val isMainScreen = currentScreen in setOf(
        AppScreen.DASHBOARD,
        AppScreen.CUSTOMERS,
        AppScreen.SUPPLIERS,
        AppScreen.WALLET,
        AppScreen.EXPENSES,
    )

    BackHandler(enabled = currentScreen != AppScreen.DASHBOARD) {
        viewModel.navigateBack()
    }

    MyApplicationTheme(darkTheme = false) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                if (isMainScreen) {
                    SafaTopAppBar(
                        viewModel = viewModel,
                        title = viewModel.t("app_title"),
                        operatorName = currentOperator?.username.orEmpty(),
                        onLogoutClick = {},
                    )
                }
            },
            bottomBar = {
                if (isMainScreen) SafaBottomNavigationBar(viewModel, currentScreen)
            },
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                when (currentScreen) {
                    AppScreen.DASHBOARD -> DashboardScreen(viewModel = viewModel)
                    AppScreen.CUSTOMERS -> CustomerScreen(
                        viewModel = viewModel,
                        isProfileView = false,
                        isAddView = false,
                    )
                    AppScreen.SUPPLIERS -> SupplierScreen(
                        viewModel = viewModel,
                        isProfileView = false,
                        isAddView = false,
                    )
                    AppScreen.TRANSACTIONS -> TransactionScreen(viewModel = viewModel)
                    AppScreen.WALLET -> WalletScreen(viewModel = viewModel)
                    AppScreen.EXPENSES -> ExpenseScreen(
                        viewModel = viewModel,
                        isAddingEntryView = false,
                    )
                    else -> DashboardScreen(viewModel = viewModel)
                }
            }
        }
    }
}
