package com.safa.account

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.safa.account.data.database.AppDatabase
import com.safa.account.data.repository.AppRepository
import com.safa.account.ui.theme.SafaTheme
import com.safa.account.ui.viewmodel.SafaViewModel
import com.safa.account.ui.viewmodel.SafaViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        android.util.Log.i("SafaApp", "STARTUP_010_ACTIVITY_CREATED")
        super.onCreate(savedInstanceState)
        android.util.Log.i("SafaApp", "STARTUP_020_AFTER_SUPER_ON_CREATE")

        var initError: Throwable? = null
        var factory: SafaViewModelFactory? = null

        try {
            android.util.Log.i("SafaApp", "STARTUP_030_BEFORE_EDGE_TO_EDGE")
            try {
                enableEdgeToEdge()
            } catch (e: Throwable) {
                android.util.Log.w("SafaApp", "enableEdgeToEdge warning: ${e.message}")
            }
            android.util.Log.i("SafaApp", "STARTUP_040_AFTER_EDGE_TO_EDGE")

            android.util.Log.i("SafaApp", "STARTUP_050_BEFORE_KEYSTORE")
            // AppDatabase no longer needs a CoroutineScope. Passing lifecycleScope
            // here after the factory API was simplified caused the compile error.
            val database = AppDatabase.getDatabase(applicationContext)
            android.util.Log.i("SafaApp", "STARTUP_100_AFTER_ROOM")

            android.util.Log.i("SafaApp", "STARTUP_110_BEFORE_REPOSITORY")
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
            android.util.Log.i("SafaApp", "STARTUP_120_AFTER_REPOSITORY")

            factory = SafaViewModelFactory(applicationContext, repository)
            android.util.Log.i("SafaApp", "STARTUP_130_AFTER_FACTORY")
        } catch (e: Throwable) {
            initError = e
            android.util.Log.e("SafaApp", "STARTUP_INIT_FAILED", e)
        }

        val resolvedFactory = factory
        setContent {
            SafaTheme {
                val viewModel = if (resolvedFactory != null) {
                    androidx.lifecycle.viewmodel.compose.viewModel<SafaViewModel>(factory = resolvedFactory)
                } else {
                    null
                }
                SafaRoot(
                    viewModel = viewModel,
                    initError = initError
                )
            }
        }
    }
}
