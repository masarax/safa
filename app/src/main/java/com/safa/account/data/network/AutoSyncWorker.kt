package com.safa.account.data.network

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.safa.account.data.api.SyncManager
import com.safa.account.data.api.TokenManager
import com.safa.account.data.database.AppDatabase
import com.safa.account.data.repository.AppRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class AutoSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        return@withContext try {
            val db = AppDatabase.getDatabase(applicationContext, GlobalScope)
            val repository = AppRepository(
                operatorDao           = db.operatorDao(),
                customerDao           = db.customerDao(),
                supplierDao           = db.supplierDao(),
                transactionDao        = db.transactionDao(),
                supplierDepositDao    = db.supplierDepositDao(),
                expenseIncomeDao      = db.expenseIncomeDao(),
                dailyRateDao          = db.dailyRateDao(),
                walletLedgerDao       = db.walletLedgerDao(),
                walletBatchDao        = db.walletBatchDao(),
            )
            val tokenManager = TokenManager(applicationContext)
            val syncManager = SyncManager(repository, tokenManager)

            val res = syncManager.syncAll()
            if (res.isSuccess) {
                Result.success()
            } else {
                Result.retry()
            }
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        fun schedulePeriodicSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val syncRequest = PeriodicWorkRequestBuilder<AutoSyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "SafaAutoSyncWorker",
                androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                syncRequest
            )
        }
    }
}
