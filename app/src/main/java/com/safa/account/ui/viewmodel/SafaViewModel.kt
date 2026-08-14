package com.safa.account.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.safa.account.data.api.RetrofitClient
import com.safa.account.data.api.SyncManager
import com.safa.account.data.api.SyncState
import com.safa.account.data.api.TokenManager
import com.safa.account.data.api.dto.LoginRequest
import com.safa.account.data.api.toEntity
import com.safa.account.data.model.*
import com.safa.account.data.money.MoneyMath
import com.safa.account.data.repository.AppRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.*

enum class AppScreen {
    LOCK_SCREEN,
    DASHBOARD,
    CUSTOMERS,
    SUPPLIERS,
    TRANSACTIONS,
    WALLET,
    EXPENSES,
    SETTINGS,
    REPORTS,
    CUSTOMER_PROFILE,
    SUPPLIER_PROFILE,
    CUSTOMER_ADD,
    SUPPLIER_ADD,
    EXPENSE_ADD
}

enum class NavDirection {
    FORWARD,
    BACKWARD
}

data class FinancialStats(
    val totalSarReceived: BigDecimal = MoneyMath.ZERO_AMOUNT,
    val totalBdtDelivered: BigDecimal = MoneyMath.ZERO_AMOUNT,
    val totalBdtPending: BigDecimal = MoneyMath.ZERO_AMOUNT,
    val totalProfitBdt: BigDecimal = MoneyMath.ZERO_AMOUNT,
    val totalProfitSar: BigDecimal = MoneyMath.ZERO_AMOUNT,
    val totalPaidToSuppliersSar: BigDecimal = MoneyMath.ZERO_AMOUNT,
    val totalBoughtPoolBdt: BigDecimal = MoneyMath.ZERO_AMOUNT,
    val totalExpensesBdt: BigDecimal = MoneyMath.ZERO_AMOUNT,
    val totalOtherIncomeBdt: BigDecimal = MoneyMath.ZERO_AMOUNT,
    val supplierUnsettledSar: BigDecimal = MoneyMath.ZERO_AMOUNT,
    val supplierUnsettledBdt: BigDecimal = MoneyMath.ZERO_AMOUNT
)

class SafaViewModel(
    val repository: AppRepository,
    val tokenManager: TokenManager? = null
) : ViewModel() {

    private fun safeConnectionFailure(): String =
        if (_currentLanguage.value == "BN") "সার্ভারের সাথে সংযোগ করা যায়নি। আবার চেষ্টা করুন।"
        else "Could not connect to the server. Please try again."

    private fun safeSyncFailure(): String =
        if (_currentLanguage.value == "BN") "সিঙ্ক সম্পন্ন করা যায়নি। আবার চেষ্টা করুন।"
        else "Sync could not be completed. Please try again."

    private fun safeServerFailure(action: String, status: Int): String =
        if (_currentLanguage.value == "BN") "সার্ভার অনুরোধটি সম্পন্ন করা যায়নি (HTTP $status)।"
        else "$action could not be completed (HTTP $status)."

    private fun safeLoginFailure(status: Int): String = when (status) {
        401 -> t("invalid_credentials")
        403 -> if (_currentLanguage.value == "BN") "এই অ্যাকাউন্ট বা ডিভাইসে লগইনের অনুমতি নেই।" else "Login is not allowed for this account or device."
        422 -> if (_currentLanguage.value == "BN") "মোবাইল নম্বর ও ৬ সংখ্যার পিন যাচাই করুন।" else "Check the mobile number and 6-digit PIN."
        429 -> if (_currentLanguage.value == "BN") "অনেকবার চেষ্টা করা হয়েছে। কিছুক্ষণ পরে আবার চেষ্টা করুন।" else "Too many attempts. Please wait and try again."
        in 500..599 -> if (_currentLanguage.value == "BN") "লগইন সার্ভার সাময়িকভাবে অনুপলব্ধ।" else "The login server is temporarily unavailable."
        else -> if (_currentLanguage.value == "BN") "লগইন সম্পন্ন করা যায়নি। আবার চেষ্টা করুন।" else "Login could not be completed. Please try again."
    }

    private fun localRole(serverRole: String?): String = when (serverRole?.trim()?.lowercase()) {
        "superadmin" -> "SuperAdmin"
        "manager" -> "Manager"
        "admin" -> "Admin"
        else -> "Staff"
    }

    private fun permissionValue(permissions: Map<String, Any?>, key: String): Boolean =
        permissions[key].let { it == true || it?.toString() == "1" || it?.toString().equals("true", true) }

    private fun authenticatedOperator(userMap: Map<String, Any?>): OperatorAccount? {
        val userId = (userMap["id"] as? Number)?.toInt()
            ?: userMap["id"]?.toString()?.toIntOrNull() ?: 0
        val mobile = userMap["mobile"]?.toString()?.trim().orEmpty()
        val rawRole = userMap["role"]?.toString()?.trim()?.lowercase().orEmpty()
        val activated = userMap["is_activated"].let {
            it == true || it?.toString() == "1" || it?.toString().equals("true", true)
        }
        if (userId <= 0 || mobile.isBlank() || !activated || rawRole !in setOf("superadmin", "manager", "admin", "staff", "user")) {
            return null
        }

        @Suppress("UNCHECKED_CAST")
        val permissions = userMap["permissions"] as? Map<String, Any?> ?: emptyMap()
        return OperatorAccount(
            id = userId,
            username = userMap["name"]?.toString()?.trim().orEmpty().ifBlank { "Operator" },
            role = localRole(rawRole),
            pin = "",
            mobile = mobile,
            email = userMap["email"]?.toString().orEmpty(),
            isActivated = true,
            isActive = true,
            isBiometricEnabled = tokenManager?.isBiometricQuickUnlockBoundTo(userId, mobile) == true,
            canViewCustomers = permissionValue(permissions, "can_view_customers"),
            canAddCustomers = permissionValue(permissions, "can_add_customers"),
            canEditCustomers = permissionValue(permissions, "can_edit_customers"),
            canDeleteCustomers = permissionValue(permissions, "can_delete_customers"),
            canViewSuppliers = permissionValue(permissions, "can_view_suppliers"),
            canAddSuppliers = permissionValue(permissions, "can_add_suppliers"),
            canEditSuppliers = permissionValue(permissions, "can_edit_suppliers"),
            canDeleteSuppliers = permissionValue(permissions, "can_delete_suppliers"),
            canViewTransactions = permissionValue(permissions, "can_view_transactions"),
            canAddTransactions = permissionValue(permissions, "can_add_transactions"),
            canEditTransactions = permissionValue(permissions, "can_edit_transactions"),
            canDeleteTransactions = permissionValue(permissions, "can_delete_transactions"),
            canManageWallet = permissionValue(permissions, "can_manage_wallet"),
            canManageExpenses = permissionValue(permissions, "can_manage_expenses"),
            canViewReports = permissionValue(permissions, "can_view_reports")
        )
    }

    val syncManager: SyncManager? = tokenManager?.let { SyncManager(repository, it) }
    val syncState: StateFlow<SyncState> = syncManager?.syncState ?: MutableStateFlow(SyncState.Idle)

    private val _apiBaseUrl = MutableStateFlow(tokenManager?.getBaseUrl() ?: "https://safa.masarax.com/api/")
    val apiBaseUrl: StateFlow<String> = _apiBaseUrl.asStateFlow()

    fun updateApiBaseUrl(newUrl: String) {
        tokenManager?.saveBaseUrl(newUrl)
        RetrofitClient.clearCache()
        _apiBaseUrl.value = tokenManager?.getBaseUrl() ?: newUrl
    }

    fun checkServerHealth(onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            if (syncManager != null) {
                val res = syncManager.checkServerHealth()
                if (res.isSuccess) {
                    onResult(true, res.getOrDefault("Connected"))
                } else {
                    res.exceptionOrNull()?.let { com.safa.account.utils.SafaLogger.error("HEALTH", "Server health check failed", it) }
                    onResult(false, safeConnectionFailure())
                }
            } else {
                onResult(false, "TokenManager not configured")
            }
        }
    }

    fun triggerFullSync(onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            if (syncManager != null) {
                val res = syncManager.syncAll()
                if (res.isSuccess) {
                    onResult(true, res.getOrDefault("Sync Completed"))
                } else {
                    res.exceptionOrNull()?.let { com.safa.account.utils.SafaLogger.error("SYNC", "Manual sync failed", it) }
                    onResult(false, safeSyncFailure())
                }
            } else {
                onResult(false, "TokenManager not configured")
            }
        }
    }

    // Language Toggle: "BN" (Bengali) or "EN" (English)
    private val _currentLanguage = MutableStateFlow(tokenManager?.getLanguage() ?: "BN")
    val currentLanguage: StateFlow<String> = _currentLanguage.asStateFlow()

    fun setLanguage(lang: String) {
        _currentLanguage.value = lang
        tokenManager?.saveLanguage(lang)
    }

    // Dark Mode Toggle (Default: loaded from TokenManager)
    private val _isDarkMode = MutableStateFlow(tokenManager?.getDarkMode() ?: false)
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    fun toggleDarkMode() {
        val newMode = !_isDarkMode.value
        _isDarkMode.value = newMode
        tokenManager?.saveDarkMode(newMode)
    }

    fun setDarkMode(isDark: Boolean) {
        _isDarkMode.value = isDark
        tokenManager?.saveDarkMode(isDark)
    }

    // Persistent currencies (SAR vs BDT customize settings)
    private val _selectedForeignCurrency = MutableStateFlow(tokenManager?.getForeignCurrency() ?: "SAR")
    val selectedForeignCurrency: StateFlow<String> = _selectedForeignCurrency.asStateFlow()

    private val _selectedLocalCurrency = MutableStateFlow(tokenManager?.getLocalCurrency() ?: "BDT")
    val selectedLocalCurrency: StateFlow<String> = _selectedLocalCurrency.asStateFlow()

    // Biometric Security Toggle
    private val _isBiometricEnabled = MutableStateFlow(false)
    val isBiometricEnabled: StateFlow<Boolean> = _isBiometricEnabled.asStateFlow()

    fun setBiometricEnabled(enabled: Boolean) {
        _isBiometricEnabled.value = enabled
    }

    fun updateSelectedForeignCurrency(currency: String) {
        _selectedForeignCurrency.value = currency
        tokenManager?.saveForeignCurrency(currency)
    }

    fun updateSelectedLocalCurrency(currency: String) {
        _selectedLocalCurrency.value = currency
        tokenManager?.saveLocalCurrency(currency)
    }

    // Dynamic Rate-Based Operational Mode Feature Toggle
    private val _isRateBasedModeEnabled = MutableStateFlow(tokenManager?.getRateFeatureEnabled() ?: true)
    val isRateBasedModeEnabled: StateFlow<Boolean> = _isRateBasedModeEnabled.asStateFlow()

    fun setRateBasedModeEnabled(enabled: Boolean) {
        _isRateBasedModeEnabled.value = enabled
        tokenManager?.saveRateFeatureEnabled(enabled)
    }

    private val _isSupplierRateEnabled = MutableStateFlow(tokenManager?.getSupplierRateEnabled() ?: true)
    val isSupplierRateEnabled: StateFlow<Boolean> = _isSupplierRateEnabled.asStateFlow()

    fun setSupplierRateEnabled(enabled: Boolean) {
        _isSupplierRateEnabled.value = enabled
        tokenManager?.saveSupplierRateEnabled(enabled)
    }

    private val _isWalletRateEnabled = MutableStateFlow(tokenManager?.getWalletRateEnabled() ?: true)
    val isWalletRateEnabled: StateFlow<Boolean> = _isWalletRateEnabled.asStateFlow()

    fun setWalletRateEnabled(enabled: Boolean) {
        _isWalletRateEnabled.value = enabled
        tokenManager?.saveWalletRateEnabled(enabled)
    }

    // Dynamic App Name & Logo Customization
    private val _customAppName = MutableStateFlow(tokenManager?.getCustomAppName() ?: "SAFA")
    val customAppName: StateFlow<String> = _customAppName.asStateFlow()

    private val _customAppLogo = MutableStateFlow(tokenManager?.getCustomAppLogo() ?: "SAFA")
    val customAppLogo: StateFlow<String> = _customAppLogo.asStateFlow()

    private val _customAppLogoUri = MutableStateFlow<String?>(tokenManager?.getCustomAppLogoUri())
    val customAppLogoUri: StateFlow<String?> = _customAppLogoUri.asStateFlow()

    private val _appVersion = MutableStateFlow(tokenManager?.getAppVersion() ?: "1.0")
    val appVersion: StateFlow<String> = _appVersion.asStateFlow()

    fun updateCustomAppName(name: String) {
        _customAppName.value = name
        tokenManager?.saveCustomAppName(name)
    }

    fun updateCustomAppLogo(logo: String) {
        _customAppLogo.value = logo
        tokenManager?.saveCustomAppLogo(logo)
    }

    fun updateCustomAppLogoUri(uri: String?) {
        _customAppLogoUri.value = uri
        tokenManager?.saveCustomAppLogoUri(uri)
    }

    fun updateConfigOnServer(config: Map<String, Any?>) {
        viewModelScope.launch {
            try {
                val api = syncManager?.getApiService() ?: return@launch
                api.updateConfig(config)
            } catch (e: Exception) {
                com.safa.account.utils.SafaLogger.error("REMOTE_CONFIG", "Server configuration update failed", e)
            }
        }
    }

    fun updateCustomAppNameOnServer(name: String) {
        updateCustomAppName(name)
        updateConfigOnServer(mapOf("app_name" to name))
    }

    fun updateCurrenciesOnServer(local: String, foreign: String) {
        updateSelectedLocalCurrency(local)
        updateSelectedForeignCurrency(foreign)
        updateConfigOnServer(mapOf("local_currency" to local, "foreign_currency" to foreign))
    }

    fun uploadAppLogoToServer(context: android.content.Context, uri: Uri) {
        viewModelScope.launch {
            try {
                val contentResolver = context.contentResolver
                val inputStream = contentResolver.openInputStream(uri) ?: return@launch
                val bytes = inputStream.readBytes()
                inputStream.close()

                val mimeType = contentResolver.getType(uri) ?: "image/jpeg"
                val mediaType = mimeType.toMediaTypeOrNull()
                val requestFile = RequestBody.create(mediaType, bytes)
                val part = MultipartBody.Part.createFormData("logo", "logo.jpg", requestFile)

                val api = syncManager?.getApiService() ?: return@launch
                val response = api.uploadLogo(part)
                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    val logoUrl = body["logo_url"]?.toString()
                        ?: body["url"]?.toString()
                        ?: body["path"]?.toString()
                        ?: body["app_logo_url"]?.toString()
                    if (!logoUrl.isNullOrBlank()) {
                        val fullUrl = if (logoUrl.startsWith("http")) logoUrl else "https://safa.masarax.com$logoUrl"
                        updateCustomAppLogoUri(fullUrl)
                        updateConfigOnServer(mapOf("app_logo_url" to fullUrl))
                    }
                }
            } catch (e: Exception) {
                com.safa.account.utils.SafaLogger.error("LOGO_UPLOAD", "Logo upload failed", e)
            }
        }
    }

    fun fetchRemoteConfig() {
        viewModelScope.launch {
            try {
                val api = syncManager?.getApiService() ?: return@launch
                val response = api.getRemoteConfig()
                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    @Suppress("UNCHECKED_CAST")
                    val dataMap = (body["config"] as? Map<String, Any?>) ?: body

                    val name = dataMap["app_name"]?.toString()
                    if (!name.isNullOrBlank()) {
                        updateCustomAppName(name)
                    }

                    val logoUrl = dataMap["app_logo_url"]?.toString() ?: dataMap["app_logo"]?.toString()
                    if (!logoUrl.isNullOrBlank()) {
                        val fullUrl = if (logoUrl.startsWith("http") || logoUrl.startsWith("content://")) logoUrl else "https://safa.masarax.com$logoUrl"
                        updateCustomAppLogoUri(fullUrl)
                    }

                    val version = dataMap["app_version"]?.toString() ?: dataMap["version"]?.toString()
                    if (!version.isNullOrBlank()) {
                        _appVersion.value = version
                        tokenManager?.saveAppVersion(version)
                    }

                    val localCurr = dataMap["local_currency"]?.toString() ?: dataMap["local_curr"]?.toString()
                    if (!localCurr.isNullOrBlank()) {
                        updateSelectedLocalCurrency(localCurr)
                    }

                    val foreignCurr = dataMap["foreign_currency"]?.toString() ?: dataMap["foreign_curr"]?.toString()
                    if (!foreignCurr.isNullOrBlank()) {
                        updateSelectedForeignCurrency(foreignCurr)
                    }
                }
            } catch (e: Exception) {
                com.safa.account.utils.SafaLogger.error("REMOTE_CONFIG", "Remote configuration fetch failed", e)
            }
        }
    }

    // Database master reset function
    fun resetDatabase(onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                transactions.value.forEach { repository.deleteTransactionById(it.id) }
                supplierDeposits.value.forEach { repository.deleteSupplierDepositById(it.id) }
                expensesIncomes.value.forEach { repository.deleteExpenseIncomeById(it.id) }
                walletBatches.value.forEach { repository.deleteWalletBatchById(it.id) }
                walletLedgers.value.forEach { repository.deleteWalletLedgerById(it.id) }
                customers.value.forEach { repository.deleteCustomerById(it.id) }
                suppliers.value.forEach { repository.deleteSupplierById(it.id) }
                onComplete()
            } catch (e: java.lang.Exception) {
                com.safa.account.utils.SafaLogger.error("DATABASE_RESET", "Database reset failed", e)
            }
        }
    }

    // Passcode protection / Multi-user login
    private val _currentOperator = MutableStateFlow<OperatorAccount?>(null)
    val currentOperator: StateFlow<OperatorAccount?> = _currentOperator.asStateFlow()

    private val _selectedLoginOperator = MutableStateFlow<OperatorAccount?>(null)
    val selectedLoginOperator: StateFlow<OperatorAccount?> = _selectedLoginOperator.asStateFlow()

    private val _pinBuffer = MutableStateFlow("")
    val pinBuffer: StateFlow<String> = _pinBuffer.asStateFlow()

    private val _pinError = MutableStateFlow<String?>(null)
    val pinError: StateFlow<String?> = _pinError.asStateFlow()

    fun setPinError(error: String?) {
        _pinError.value = error
    }

    // Screen State
    private val _currentScreen = MutableStateFlow(AppScreen.LOCK_SCREEN)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    private val _navDirection = MutableStateFlow(NavDirection.FORWARD)
    val navDirection: StateFlow<NavDirection> = _navDirection.asStateFlow()

    private val _screenHistory = MutableStateFlow<List<AppScreen>>(listOf(AppScreen.DASHBOARD))
    val screenHistory: StateFlow<List<AppScreen>> = _screenHistory.asStateFlow()

    private val _isSubPageActive = MutableStateFlow(false)
    val isSubPageActive: StateFlow<Boolean> = combine(_isSubPageActive, _currentScreen) { localActive, screen ->
        localActive || screen in listOf(
            AppScreen.CUSTOMER_PROFILE,
            AppScreen.SUPPLIER_PROFILE,
            AppScreen.CUSTOMER_ADD,
            AppScreen.SUPPLIER_ADD,
            AppScreen.EXPENSE_ADD
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun setSubPageActive(active: Boolean) {
        _isSubPageActive.value = active
    }

    // Global profile navigation anchors
    private val _selectedCustomerIdForProfile = MutableStateFlow<Int?>(null)
    val selectedCustomerIdForProfile: StateFlow<Int?> = _selectedCustomerIdForProfile.asStateFlow()

    private val _newTransactionPreselectCustomerId = MutableStateFlow<Int?>(null)
    val newTransactionPreselectCustomerId: StateFlow<Int?> = _newTransactionPreselectCustomerId.asStateFlow()

    private val _selectedSupplierIdForProfile = MutableStateFlow<Int?>(null)
    val selectedSupplierIdForProfile: StateFlow<Int?> = _selectedSupplierIdForProfile.asStateFlow()

    fun selectCustomerProfile(id: Int?) {
        _selectedCustomerIdForProfile.value = id
        if (id != null) {
            navigateTo(AppScreen.CUSTOMER_PROFILE)
        } else {
            if (_currentScreen.value == AppScreen.CUSTOMER_PROFILE) {
                navigateBack()
            }
        }
    }

    fun startTransactionForCustomer(id: Int) {
        _newTransactionPreselectCustomerId.value = id
        navigateTo(AppScreen.TRANSACTIONS)
    }

    fun clearTransactionPreselect() {
        _newTransactionPreselectCustomerId.value = null
    }

    fun selectSupplierProfile(id: Int?) {
        _selectedSupplierIdForProfile.value = id
        if (id != null) {
            navigateTo(AppScreen.SUPPLIER_PROFILE)
        } else {
            if (_currentScreen.value == AppScreen.SUPPLIER_PROFILE) {
                navigateBack()
            }
        }
    }

    fun updateCustomer(customer: Customer, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            val updatedStatus = if (customer.syncStatus == SyncStatus.SYNCED) SyncStatus.PENDING_UPDATE else customer.syncStatus
            repository.updateCustomer(customer.copy(syncStatus = updatedStatus))
            syncManager?.syncAll()
            onComplete()
        }
    }

    fun updateSupplier(supplier: Supplier, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            val updatedStatus = if (supplier.syncStatus == SyncStatus.SYNCED) SyncStatus.PENDING_UPDATE else supplier.syncStatus
            repository.updateSupplier(supplier.copy(syncStatus = updatedStatus))
            syncManager?.syncAll()
            onComplete()
        }
    }

    // Lists representing reactive Flows
    val operators: StateFlow<List<OperatorAccount>> = repository.allOperators
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val customers: StateFlow<List<Customer>> = repository.allCustomers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val suppliers: StateFlow<List<Supplier>> = repository.allSuppliers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val transactions: StateFlow<List<RemittanceTransaction>> = repository.allTransactions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val supplierDeposits: StateFlow<List<SupplierDeposit>> = repository.allSupplierDeposits
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val expensesIncomes: StateFlow<List<ExpenseIncome>> = repository.allExpensesIncomes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val dailyRatesList: StateFlow<List<DailyRate>> = repository.allDailyRates
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val walletLedgers: StateFlow<List<WalletLedger>> = repository.allWalletLedgers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val walletBatches: StateFlow<List<WalletBatch>> = repository.allWalletBatches
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Daily Rates ---
    private val _currentRates = MutableStateFlow<DailyRate?>(null)
    val currentRates: StateFlow<DailyRate?> = _currentRates.asStateFlow()

    // Financial Summaries Derived State
    val financialStats: StateFlow<FinancialStats> = combine(
        transactions, supplierDeposits, expensesIncomes
    ) { txs, deposits, expenses ->
        
        var totalSar = MoneyMath.ZERO_AMOUNT
        var totalDeliveredBdt = MoneyMath.ZERO_AMOUNT
        var totalPendingBdt = MoneyMath.ZERO_AMOUNT
        var profitBdt = MoneyMath.ZERO_AMOUNT
        var profitSar = MoneyMath.ZERO_AMOUNT
        var bdtFromSupplierPoolUsed = MoneyMath.ZERO_AMOUNT

        for (tx in txs) {
            val amtSar = tx.amountSar
            val amtBdt = tx.amountBdt
            totalSar = MoneyMath.add(totalSar, amtSar)
            if (tx.status == "Delivered") {
                totalDeliveredBdt = MoneyMath.add(totalDeliveredBdt, amtBdt)
            } else if (tx.status == "Pending") {
                totalPendingBdt = MoneyMath.add(totalPendingBdt, amtBdt)
            }
            if (tx.status != "Cancelled") {
                profitBdt = MoneyMath.add(profitBdt, tx.getProfitBdt())
                profitSar = MoneyMath.add(profitSar, tx.getProfitSar())
                bdtFromSupplierPoolUsed = MoneyMath.add(bdtFromSupplierPoolUsed, amtBdt)
            }
        }

        var totalPaidSupplierSar = MoneyMath.ZERO_AMOUNT
        var totalBoughtBdt = MoneyMath.ZERO_AMOUNT

        for (dep in deposits) {
            if (dep.transactionType == "SAR_DEPOSIT" || dep.transactionType == "SAR_GIVEN") {
                totalPaidSupplierSar = MoneyMath.add(totalPaidSupplierSar, dep.amountSar)
                totalBoughtBdt = MoneyMath.add(totalBoughtBdt, dep.amountBdt)
            } else if (dep.transactionType == "BDT_WITHDRAW") {
                totalBoughtBdt = MoneyMath.subtract(totalBoughtBdt, dep.amountBdt)
            }
        }

        var totalExp = MoneyMath.ZERO_AMOUNT
        var totalInc = MoneyMath.ZERO_AMOUNT
        
        val currentRate = _currentRates.value?.supplierRate ?: MoneyMath.rate("32.5")

        for (item in expenses) {
            val amt = if (item.currency == "SAR") MoneyMath.multiply(item.amount, currentRate) else item.amount
            if (item.isExpense) {
                totalExp = MoneyMath.add(totalExp, amt)
            } else {
                totalInc = MoneyMath.add(totalInc, amt)
            }
        }

        val outstandingBdt = MoneyMath.subtract(totalBoughtBdt, bdtFromSupplierPoolUsed)
        val outstandingSar = if (outstandingBdt.signum() != 0 && currentRate.signum() != 0) {
            MoneyMath.subtract(totalPaidSupplierSar, MoneyMath.divideAmountByRate(bdtFromSupplierPoolUsed, currentRate))
        } else {
            MoneyMath.ZERO_AMOUNT
        }

        FinancialStats(
            totalSarReceived = totalSar,
            totalBdtDelivered = totalDeliveredBdt,
            totalBdtPending = totalPendingBdt,
            totalProfitBdt = profitBdt,
            totalProfitSar = profitSar,
            totalPaidToSuppliersSar = totalPaidSupplierSar,
            totalBoughtPoolBdt = totalBoughtBdt,
            totalExpensesBdt = totalExp,
            totalOtherIncomeBdt = totalInc,
            supplierUnsettledSar = outstandingSar,
            supplierUnsettledBdt = outstandingBdt
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FinancialStats())

    init {
        // Automatically check/load rates for today on startup
        try {
            refreshTodayRates()
        } catch (e: Throwable) {
            com.safa.account.utils.SafaLogger.error("STARTUP_RATES", "Rate initialization failed; using deterministic fallback", e)
        }
        try {
            fetchRemoteConfig()
        } catch (e: Throwable) {
            com.safa.account.utils.SafaLogger.error("STARTUP_CONFIG", "Remote configuration initialization failed", e)
        }
        try {
            fetchOperatorsFromServer()
        } catch (e: Throwable) {
            com.safa.account.utils.SafaLogger.error("STARTUP_OPERATORS", "Operator initialization failed", e)
        }
        try {
            triggerFullSync()
        } catch (e: Throwable) {
            com.safa.account.utils.SafaLogger.error("STARTUP_SYNC", "Startup sync initialization failed", e)
        }
    }

    fun refreshTodayRates() {
        viewModelScope.launch {
            val dateStr = com.safa.account.ui.util.FormatUtils.formatDateIso(System.currentTimeMillis())
            val existing = repository.getDailyRateByDate(dateStr)
            if (existing != null) {
                _currentRates.value = existing
            } else {
                // If not set yet, use latest rate set ever or default baseline
                val allRates = dailyRatesList.value
                if (allRates.isNotEmpty()) {
                    val latest = allRates.maxByOrNull { it.date } ?: return@launch
                    _currentRates.value = DailyRate(dateStr, latest.customerRate, latest.supplierRate)
                } else {
                    _currentRates.value = DailyRate(
                        dateStr,
                        MoneyMath.rate("32.0"),
                        MoneyMath.rate("32.5")
                    ) // Standard baseline
                }
            }
        }
    }

    // --- Translations (BN/EN) ---
    val bnMap = mapOf(
        "app_title" to "SAFA",
        "dashboard" to "ড্যাশবোর্ড",
        "wallet" to "ওয়ালেট", // Wallet
        "customers" to "কাস্টমার", // Customers
        "suppliers" to "সাপ্লায়ার",
        "transactions" to "লেনদেন", // Transactions
        "expenses" to "আয়/ব্যায়",
        "settings" to "সেটিংস",
        "login_title" to "SAFA - সাফা",
        "select_operator" to "ইউজার নির্বাচন করুন", // Select User
        "enter_pin" to "৬-ডিজিটের পিন", // 6-Digit PIN
        "pin_incorrect" to "ভুল পিন! আবার চেষ্টা করুন।", // Wrong PIN! Try again.
        "logout" to "লগ আউট",
        "change_role" to "রোল পরিবর্তন", // Change Role
        "role_owner" to "মালিক",
        "role_staff" to "স্টাফ",
        "operator_blocked" to "অ্যাকাউন্ট স্থগিত।", // Account suspended.
        "access_denied" to "অনুমতি নেই",
        "permission_required" to "অনুমতি প্রয়োজন।", // Permission required.
        "no_account_found" to "অ্যাকাউন্ট পাওয়া যায়নি।", // Account not found.
        "activation_title" to "সুপার-এডমিন অ্যাক্টিভেশন",
        "activation_desc" to "অ্যাডমিন অ্যাকাউন্ট সেটআপ করুন।", // Set up Admin Account.
        "full_name" to "পূর্ণ নাম",
        "email" to "ইমেইল", // Email
        "complete_activation" to "অ্যাক্টিভেশন সম্পন্ন", // Complete Activation
        "login_button" to "লগইন", // Log In
        "mobile_number" to "মোবাইল নম্বর",
        "enter_mobile_ph" to "যেমন: 01700000000",
        "activate_super_admin" to "সুপার-এডমিন অ্যাক্টিভেশন",
        "invalid_credentials" to "ভুল মোবাইল বা পিন।", // Wrong mobile or PIN.

        "total_sar_received" to "মোট প্রাপ্ত বৈদেশিক", // Total Received Foreign
        "total_bdt_delivered" to "মোট বিতরণকৃত লোকাল", // Total Local Disbursed
        "total_bdt_pending" to "মোট পেন্ডিং লোকাল", // Total Local Pending
        "estimated_profit" to "আজকের আনুমানিক লাভ", // Estimated Profit Today
        "net_profit_bdt" to "নিট লাভ (লোকাল)", // Net Profit (Local)
        "net_profit_sar" to "নিট লাভ (বৈদেশিক)", // Net Profit (Foreign)
        "daily_operating_rates" to "দৈনিক বিনিময় হার", // Daily Exchange Rates
        "customer_sale_rate" to "কাস্টমার রেট", // Customer Rate
        "supplier_buy_rate" to "সাপ্লায়ার রেট (খরচ)", // Supplier Rate (Cost)

        "customer_mgmt" to "কাস্টমার তালিকা", // Customer Directory
        "add_customer" to "নতুন কাস্টমার যোগ করুন", // Add New Customer
        "customer_name" to "কাস্টমারের নাম", // Customer Name
        "phone_number" to "মোবাইল নম্বর",
        "address" to "ঠিকানা", // Address
        "save_customer" to "কাস্টমার নিবন্ধন", // Register Customer
        "total_customers" to "মোট কাস্টমার", // Total Customers

        "supplier_mgmt" to "সাপ্লায়ার", // Suppliers
        "add_supplier" to "নতুন সাপ্লায়ার যোগ করুন", // Add New Supplier
        "supplier_name" to "সাপ্লায়ারের নাম", // Supplier Name
        "save_supplier" to "সাপ্লায়ার সংরক্ষণ", // Save Supplier
        "buy_bdt_pool" to "লোকাল ফান্ড কিনুন", // Acquire Local Fund
        "amount_sar" to "ডেবিট বৈদেশিক", // Debit Foreign Amount
        "rate_applied" to "বিনিময় হার", // Exchange Rate
        "purchase_success" to "লোকাল ফান্ড সফল।", // Local Fund Processed.
        "total_deposited_sar" to "মোট জমা বৈদেশিক", // Total Deposited Foreign
        "acquired_bdt" to "অর্জিত লোকাল", // Acquired Local Funds
        "pool_balance" to "সাপ্লায়ার ফান্ড", // Supplier Funds

        "new_remittance" to "নতুন লেনদেন", // New Transaction
        "select_customer" to "কাস্টমার নির্বাচন", // Select Customer
        "select_supplier" to "সাপ্লায়ার নির্বাচন", // Select Supplier
        "saudi_amount" to "প্রাপ্ত বৈদেশিক", // Received Foreign Amount
        "customer_assigned_rate" to "কাস্টমার রেট", // Customer Rate
        "supplier_rate_tx" to "সাপ্লায়ার রেট", // Supplier Rate
        "receiver_bdt_amount" to "প্রাপকের লোকাল", // Local Payout
        "receiver_name" to "প্রাপকের নাম", // Receiver Name
        "receiver_phone" to "প্রাপকের মোবাইল", // Receiver Mobile
        "payment_method" to "পেমেন্ট চ্যানেল",
        "receiver_account_no" to "অ্যাকাউন্ট নং", // Account No
        "notes" to "নোট", // Notes
        "status" to "স্ট্যাটাস", // Status
        "status_pending" to "পেন্ডিং", // Pending
        "status_delivered" to "ডেলিভার্ড", // Delivered
        "status_cancelled" to "বাতিল", // Cancelled
        "save_transaction" to "লেনদেন সংরক্ষণ", // Save Transaction

        "expenses_overhead" to "খরচ ও অন্যান্য", // Expenses & Others
        "add_expense_income" to "আয়/ব্যয় যোগ করুন", // Add Expense/Income
        "title" to "বিবরণ", // Description
        "amount" to "পরিমাণ",
        "is_expense" to "ধরণ", // Type
        "expense" to "ব্যয়", // Expense
        "income" to "আয়", // Income
        "category" to "ক্যাটাগরি",
        "save_record" to "রেকর্ড সংরক্ষণ", // Save Record

        "update_daily_rates" to "বিনিময় হার প্রকাশ", // Publish Exchange Rates
        "rate_saved" to "হার সংরক্ষিত!", // Rates Saved!
        "operator_list" to "অপারেটর ও কর্মী", // Operators & Staff
        "create_new_operator" to "নতুন স্টাফ অ্যাকাউন্ট", // Create Staff Account
        "pinCode" to "৬-ডিজিটের পিন", // 6-Digit PIN
        "role" to "রোল", // Role
        "unsettled_supp" to "সাপ্লায়ার দেনা/পাওনা",
        "view_customers" to "কাস্টমার দেখুন",
        "add_customers" to "কাস্টমার যোগ করুন",
        "edit_customers" to "কাস্টমার সম্পাদন",
        "delete_customers" to "কাস্টমার মুছুন",
        "view_suppliers" to "সাপ্লায়ার দেখুন",
        "add_suppliers" to "সাপ্লায়ার যোগ করুন",
        "edit_suppliers" to "সাপ্লায়ার সম্পাদন",
        "delete_suppliers" to "সাপ্লায়ার মুছুন",
        "view_transactions" to "লেনদেন দেখুন",
        "add_transactions" to "লেনদেন যোগ করুন",
        "edit_transactions" to "লেনদেন সম্পাদন",
        "delete_transactions" to "লেনদেন মুছুন",
        "manage_wallet" to "ওয়ালেট পরিচালনা",
        "manage_expenses" to "আয়/ব্যয় পরিচালনা",
        "view_reports" to "রিপোর্টস দেখুন"
    )

    val enMap = mapOf(
        "app_title" to "SAFA",
        "dashboard" to "Dashboard",
        "wallet" to "Wallet",
        "customers" to "Customers",
        "suppliers" to "Suppliers",
        "transactions" to "Transactions",
        "expenses" to "Expenses & Inc.",
        "settings" to "Settings",
        "login_title" to "SAFA Security Lock",
        "select_operator" to "Select Registered Operator",
        "enter_pin" to "Enter 6-Digit Security PIN",
        "pin_incorrect" to "Incorrect PIN! Please try again.",
        "logout" to "Log Out",
        "change_role" to "Change Role",
        "role_owner" to "Owner / Admin",
        "role_staff" to "Staff / Operator",
        "operator_blocked" to "Account is currently suspended.",
        "access_denied" to "Access Denied",
        "permission_required" to "You do not have permission to access this feature.",
        "no_account_found" to "No account found with this mobile number",
        "activation_title" to "SuperAdmin 1-Time Activation",
        "activation_desc" to "Set up your initial administrator account",
        "full_name" to "Full Name",
        "email" to "Email Address",
        "complete_activation" to "Complete Activation",
        "login_button" to "Log In",
        "mobile_number" to "Mobile Number",
        "enter_mobile_ph" to "e.g. 01700000000",
        "activate_super_admin" to "Activate SuperAdmin",
        
        "total_sar_received" to "Total Received Foreign",
        "total_bdt_delivered" to "Total Local Disbursed",
        "total_bdt_pending" to "Total Local Pending",
        "estimated_profit" to "Total Estimated Profit Today",
        "net_profit_bdt" to "Net Profit (Local)",
        "net_profit_sar" to "Net Profit (Foreign)",
        "daily_operating_rates" to "Live Daily Exchange Rates",
        "customer_sale_rate" to "Customer Exchange Rate",
        "supplier_buy_rate" to "Supplier Exchange Rate (Cost)",
        
        "customer_mgmt" to "Customer Directory",
        "add_customer" to "Add New Customer Profile",
        "customer_name" to "Customer Full Name",
        "phone_number" to "Saudi Mobile Number",
        "address" to "Address/Workplace",
        "save_customer" to "Register Customer",
        "total_customers" to "Total Registered Customers",
        
        "supplier_mgmt" to "Suppliers",
        "add_supplier" to "Add Local Supplier Forex",
        "supplier_name" to "Supplier Forex Group Name",
        "save_supplier" to "Save Supplier Detail",
        "buy_bdt_pool" to "Acquire Local Deal",
        "amount_sar" to "Debit Foreign Amount",
        "rate_applied" to "Exchange Rate (e.g., 32.50)",
        "purchase_success" to "Local Pool successfully processed",
        "total_deposited_sar" to "Total Deposited Foreign",
        "acquired_bdt" to BdtSymbol() + " Acquired Local Funds",
        "pool_balance" to "Supplier Outstanding Funds",
        
        "new_remittance" to "New Transaction",
        "select_customer" to "Search/Select Customer",
        "select_supplier" to "Select Active Local Supplier",
        "saudi_amount" to "Received Foreign Amount",
        "customer_assigned_rate" to "Customer Exchange Rate",
        "supplier_rate_tx" to "Supplier Rate Applied",
        "receiver_bdt_amount" to "Local Payout",
        "receiver_name" to "Receiver Full Name",
        "receiver_phone" to "Receiver Mobile No",
        "payment_method" to "Payout Channel",
        "receiver_account_no" to "Account No (bKash/Nagad/Bank)",
        "notes" to "Additional Instructions / Delivery Address",
        "status" to "Delivery Status",
        "status_pending" to "Pending Transmit",
        "status_delivered" to "Delivered to Target",
        "status_cancelled" to "Cancelled Transmit",
        "save_transaction" to "Commit & Save Transaction",
        
        "expenses_overhead" to "Operating overhead & Miscellaneous",
        "add_expense_income" to "Log Operating Expense/Income",
        "title" to "Description (e.g. Office Rent, Coffee bills)",
        "amount" to "Amount",
        "is_expense" to "Transaction Class",
        "expense" to "Operating Expense",
        "income" to "Operating Credit",
        "category" to "Category",
        "save_record" to "Log Item",
        
        "update_daily_rates" to "Publish Live Exchange Rates",
        "rate_saved" to "Rates published successfully!",
        "operator_list" to "Authorized Operators & Personnel",
        "create_new_operator" to "Provision New Staff Account",
        "pinCode" to "6-Digit PIN Access",
        "role" to "User Authorization Role",
        "unsettled_supp" to "Net Supplier Credit Obligations",
        "manage_operators" to "Operator & Staff User Management",
        "operator_management_desc" to "Control 15 granular RBAC permissions for server users",
        "view_customers" to "View Customers",
        "add_customers" to "Add Customers",
        "edit_customers" to "Edit Customers",
        "delete_customers" to "Delete Customers",
        "view_suppliers" to "View Suppliers",
        "add_suppliers" to "Add Suppliers",
        "edit_suppliers" to "Edit Suppliers",
        "delete_suppliers" to "Delete Suppliers",
        "view_transactions" to "View Transactions",
        "add_transactions" to "Add Transactions",
        "edit_transactions" to "Edit Transactions",
        "delete_transactions" to "Delete Transactions",
        "manage_wallet" to "Manage Wallet",
        "manage_expenses" to "Manage Expenses",
        "view_reports" to "View Reports",
        "invalid_credentials" to "Invalid mobile or PIN"
    )

    fun t(key: String, lang: String = _currentLanguage.value): String {
        val strings = if (lang == "BN") bnMap else enMap
        var value = strings[key] ?: key
        val foreign = _selectedForeignCurrency.value
        val local = _selectedLocalCurrency.value
        value = value.replace("রিয়াল", "($foreign)")
            .replace("রিয়াল", "($foreign)")
            .replace("টাকা", "($local)")
            .replace("Saudi Riyal", "($foreign)")
            .replace("Saudi", "($foreign)")
            .replace("Riyal", "($foreign)")
            .replace("Taka", "($local)")
            .replace("BDT", "($local)")
            .replace("SAR", "($foreign)")
            .replace("৳", "($local)")
        return value
    }

    fun BdtSymbol(): String {
        return "(${_selectedLocalCurrency.value})"
    }

    fun SarSymbol(): String {
        return "(${_selectedForeignCurrency.value})"
    }

    fun toggleLanguage() {
        val newLang = if (_currentLanguage.value == "BN") "EN" else "BN"
        _currentLanguage.value = newLang
        tokenManager?.saveLanguage(newLang)
    }

    // --- Screen Navigation Control ---
    fun navigateTo(screen: AppScreen) {
        _navDirection.value = NavDirection.FORWARD
        if (_currentOperator.value == null && screen != AppScreen.LOCK_SCREEN) {
            _currentScreen.value = AppScreen.LOCK_SCREEN
            _screenHistory.value = listOf(AppScreen.LOCK_SCREEN)
        } else {
            if (screen == AppScreen.LOCK_SCREEN) {
                _selectedCustomerIdForProfile.value = null
                _selectedSupplierIdForProfile.value = null
                _currentScreen.value = screen
                _screenHistory.value = listOf(AppScreen.LOCK_SCREEN)
            } else if (screen == AppScreen.DASHBOARD) {
                _selectedCustomerIdForProfile.value = null
                _selectedSupplierIdForProfile.value = null
                _currentScreen.value = screen
                _screenHistory.value = listOf(AppScreen.DASHBOARD)
            } else {
                _currentScreen.value = screen
                val currentHistory = _screenHistory.value.toMutableList()
                if (currentHistory.contains(screen)) {
                    currentHistory.remove(screen)
                }
                currentHistory.add(screen)
                _screenHistory.value = currentHistory
            }
        }
    }

    fun navigateBack(): Boolean {
        // If there's screen history we can slide to, go back one step.
        // Otherwise return false to let the activity exit warning dialog render.
        _navDirection.value = NavDirection.BACKWARD
        val currentHistory = _screenHistory.value.toMutableList()
        if (currentHistory.size > 1) {
            currentHistory.removeAt(currentHistory.lastIndex)
            _screenHistory.value = currentHistory
            val previousScreen = currentHistory.last()
            _currentScreen.value = previousScreen
            
            // Sync selected profile states
            if (previousScreen != AppScreen.CUSTOMER_PROFILE) {
                _selectedCustomerIdForProfile.value = null
            }
            if (previousScreen != AppScreen.SUPPLIER_PROFILE) {
                _selectedSupplierIdForProfile.value = null
            }
            return true
        }
        return false
    }

    // --- Authentication Pin-lock Business Logic ---
    fun selectLoginOperator(operator: OperatorAccount) {
        _selectedLoginOperator.value = operator
        _pinBuffer.value = ""
        _pinError.value = null
    }

    // Legacy PIN pad functions removed - all auth is server-driven
    fun appendPinDigit(digit: Char) { /* Disabled: server-driven auth only */ }
    fun deletePinDigit() { /* Disabled: server-driven auth only */ }

    fun loginWithServer(mobile: String, pin: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            if (mobile.isBlank() || pin.length < 6) {
                onResult(false, t("pin_incorrect"))
                return@launch
            }
            try {
                val api = syncManager?.getApiService()
                if (api != null) {
                    val req = com.safa.account.data.api.dto.MobilePinLoginRequest(mobile = mobile.trim(), pin = pin)
                    val response = api.login(req)
                    if (response.isSuccessful && response.body() != null) {
                        val body = response.body()!!
                        @Suppress("UNCHECKED_CAST")
                        val userMap = body["user"] as? Map<String, Any?>
                        @Suppress("UNCHECKED_CAST")
                        val tokens = body["tokens"] as? Map<String, Any?>
                        val requiredTokens = listOf("access_token", "refresh_token", "device_token", "session_token", "fingerprint_token")
                        @Suppress("UNCHECKED_CAST")
                        val topLevelPermissions = body["permissions"] as? Map<String, Any?>
                        val authenticatedUser = userMap?.let { raw ->
                            if (raw["permissions"] is Map<*, *> || topLevelPermissions == null) raw
                            else raw + ("permissions" to topLevelPermissions)
                        }
                        val op = authenticatedUser?.let(::authenticatedOperator)
                        if (op == null || tokens == null || requiredTokens.any { tokens[it]?.toString().isNullOrBlank() }) {
                            com.safa.account.utils.SafaLogger.warn("LOGIN", "Login response failed the authenticated identity contract")
                            tokenManager?.clearAllTokens()
                            onResult(false, safeLoginFailure(502))
                            return@launch
                        }

                        val existing = operators.value.find { it.id == op.id }
                            ?: repository.getOperatorByMobile(op.mobile)
                        if (existing != null && existing.id != op.id) {
                            repository.removeOperatorLocally(existing)
                        }
                        repository.insertOperator(op)
                        tokenManager?.saveAllTokens(
                            accessToken = tokens["access_token"]?.toString(),
                            refreshToken = tokens["refresh_token"]?.toString(),
                            deviceToken = tokens["device_token"]?.toString(),
                            sessionToken = tokens["session_token"]?.toString(),
                            fingerprintToken = tokens["fingerprint_token"]?.toString()
                        )
                        _isBiometricEnabled.value = op.isBiometricEnabled
                        _currentOperator.value = op
                        _selectedLoginOperator.value = op
                        _pinError.value = null
                        tokenManager?.saveLastMobile(op.mobile)
                        fetchOperatorsFromServer()
                        fetchRemoteConfig()
                        triggerFullSync()
                        navigateTo(AppScreen.DASHBOARD)
                        onResult(true, null)
                        return@launch
                    } else {
                        com.safa.account.utils.SafaLogger.warn("LOGIN", "Login rejected with HTTP ${response.code()}")
                        onResult(false, safeLoginFailure(response.code()))
                        return@launch
                    }
                } else {
                    onResult(false, safeConnectionFailure())
                    return@launch
                }
            } catch (e: Exception) {
                com.safa.account.utils.SafaLogger.error("LOGIN", "Login request failed", e)
                onResult(false, com.safa.account.data.network.ApiLoginErrorParser.fromThrowable(e).message)
                return@launch
            }
        }
    }

    // loginWithMobileAndPin() REMOVED: All authentication is server-driven via loginWithServer()

    fun fetchOperatorsFromServer() {
        viewModelScope.launch {
            if (_currentOperator.value?.role != "SuperAdmin") return@launch
            try {
                val api = syncManager?.getApiService() ?: return@launch
                val res = api.getOperators()
                if (res.isSuccessful && res.body() != null) {
                    val body = res.body()!!
                    @Suppress("UNCHECKED_CAST")
                    val rawOps = (body["users"] as? List<Map<String, Any?>>)
                        ?: (body["operators"] as? List<Map<String, Any?>>)
                        ?: return@launch
                    val currentOps = operators.value
                    val validIds = rawOps.mapNotNull { (it["id"] as? Number)?.toInt() }.filter { it > 0 }

                    rawOps.forEach { opMap ->
                        val serverId = (opMap["id"] as? Number)?.toInt() ?: 0
                        if (serverId <= 0) return@forEach
                        val mobile = opMap["mobile"]?.toString()?.trim() ?: ""
                        val name = opMap["name"]?.toString() ?: "Operator"
                        val email = opMap["email"]?.toString() ?: ""
                        val roleStr = opMap["role"]?.toString() ?: "Staff"
                        val isActivated = opMap["is_activated"].let {
                            it == true || it?.toString() == "1" || it?.toString().equals("true", true)
                        }
                        @Suppress("UNCHECKED_CAST")
                        val permsMap = opMap["permissions"] as? Map<String, Any?> ?: emptyMap()

                        val existing = currentOps.find {
                            (serverId > 0 && it.id == serverId) ||
                            (mobile.isNotBlank() && it.mobile.trim() == mobile)
                        }
                        val op = OperatorAccount(
                            id = serverId,
                            username = name,
                            role = localRole(roleStr),
                            pin = "",
                            mobile = mobile,
                            email = email,
                            isActivated = isActivated,
                            isActive = isActivated,
                            canViewCustomers = permissionValue(permsMap, "can_view_customers"),
                            canAddCustomers = permissionValue(permsMap, "can_add_customers"),
                            canEditCustomers = permissionValue(permsMap, "can_edit_customers"),
                            canDeleteCustomers = permissionValue(permsMap, "can_delete_customers"),
                            canViewSuppliers = permissionValue(permsMap, "can_view_suppliers"),
                            canAddSuppliers = permissionValue(permsMap, "can_add_suppliers"),
                            canEditSuppliers = permissionValue(permsMap, "can_edit_suppliers"),
                            canDeleteSuppliers = permissionValue(permsMap, "can_delete_suppliers"),
                            canViewTransactions = permissionValue(permsMap, "can_view_transactions"),
                            canAddTransactions = permissionValue(permsMap, "can_add_transactions"),
                            canEditTransactions = permissionValue(permsMap, "can_edit_transactions"),
                            canDeleteTransactions = permissionValue(permsMap, "can_delete_transactions"),
                            canManageWallet = permissionValue(permsMap, "can_manage_wallet"),
                            canManageExpenses = permissionValue(permsMap, "can_manage_expenses"),
                            canViewReports = permissionValue(permsMap, "can_view_reports")
                        )
                        if (existing != null && existing.id != serverId) repository.removeOperatorLocally(existing)
                        repository.insertOperator(op)
                    }

                    val authenticatedId = _currentOperator.value?.id
                    currentOps.forEach { localOp ->
                        if (localOp.id != authenticatedId && localOp.id !in validIds) {
                            repository.removeOperatorLocally(localOp)
                        }
                    }
                } else {
                    com.safa.account.utils.SafaLogger.warn("OPERATORS", "Operator refresh rejected with HTTP ${res.code()}")
                }
            } catch (e: Exception) {
                com.safa.account.utils.SafaLogger.error("OPERATORS", "Operator refresh failed", e)
            }
        }
    }

    fun createOperatorOnServer(
        name: String,
        mobile: String,
        email: String,
        role: String,
        pin: String,
        permissionsMap: Map<String, Boolean>,
        onResult: (Boolean, String?) -> Unit
    ) {
        viewModelScope.launch {
            if (_currentOperator.value?.role != "SuperAdmin") {
                onResult(false, t("access_denied"))
                return@launch
            }
            try {
                val api = syncManager?.getApiService()
                if (api == null) {
                    onResult(false, safeConnectionFailure())
                    return@launch
                }
                val apiRole = when (role.trim().lowercase()) {
                    "manager", "owner" -> "manager"
                    "admin" -> "admin"
                    "user" -> "user"
                    else -> "staff"
                }
                val req = com.safa.account.data.api.dto.OperatorApiRequest(
                    name = name.trim(),
                    mobile = mobile.trim(),
                    email = email.ifBlank { null },
                    role = apiRole,
                    pin = pin,
                    isActivated = true,
                    permissions = permissionsMap
                )
                val response = api.createOperator(req)
                if (!response.isSuccessful) {
                    com.safa.account.utils.SafaLogger.warn("OPERATOR_CREATE", "Operator creation rejected with HTTP ${response.code()}")
                    onResult(false, safeServerFailure("Create operator", response.code()))
                    return@launch
                }
                fetchOperatorsFromServer()
                onResult(true, null)
            } catch (e: Exception) {
                com.safa.account.utils.SafaLogger.error("OPERATOR_CREATE", "Server operator creation failed", e)
                onResult(false, safeConnectionFailure())
            }
        }
    }

    fun updateOperatorOnServer(
        op: OperatorAccount,
        newPin: String? = null,
        onResult: (Boolean, String?) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            if (_currentOperator.value?.role != "SuperAdmin") {
                onResult(false, t("access_denied"))
                return@launch
            }
            try {
                val api = syncManager?.getApiService()
                if (api == null || op.id <= 0) {
                    onResult(false, safeConnectionFailure())
                    return@launch
                }
                val apiRole = when (op.role.trim().lowercase()) {
                    "manager", "owner" -> "manager"
                    "admin" -> "admin"
                    "user" -> "user"
                    else -> "staff"
                }
                val permsMap = mapOf(
                    "can_view_customers" to op.canViewCustomers,
                    "can_add_customers" to op.canAddCustomers,
                    "can_edit_customers" to op.canEditCustomers,
                    "can_delete_customers" to op.canDeleteCustomers,
                    "can_view_suppliers" to op.canViewSuppliers,
                    "can_add_suppliers" to op.canAddSuppliers,
                    "can_edit_suppliers" to op.canEditSuppliers,
                    "can_delete_suppliers" to op.canDeleteSuppliers,
                    "can_view_transactions" to op.canViewTransactions,
                    "can_add_transactions" to op.canAddTransactions,
                    "can_edit_transactions" to op.canEditTransactions,
                    "can_delete_transactions" to op.canDeleteTransactions,
                    "can_manage_wallet" to op.canManageWallet,
                    "can_manage_expenses" to op.canManageExpenses,
                    "can_view_reports" to op.canViewReports
                )
                val req = com.safa.account.data.api.dto.OperatorApiRequest(
                    name = op.username,
                    mobile = op.mobile,
                    email = op.email.ifBlank { null },
                    role = apiRole,
                    pin = newPin.takeIf { !it.isNullOrBlank() && it.length == 6 },
                    isActivated = op.isActivated,
                    permissions = permsMap
                )
                val response = api.updateOperator(op.id, req)
                if (!response.isSuccessful) {
                    com.safa.account.utils.SafaLogger.warn("OPERATOR_UPDATE", "Operator update rejected with HTTP ${response.code()}")
                    onResult(false, safeServerFailure("Update operator", response.code()))
                    return@launch
                }
                repository.updateOperator(op.copy(role = localRole(apiRole), pin = ""))
                onResult(true, null)
            } catch (e: Exception) {
                com.safa.account.utils.SafaLogger.error("OPERATOR_UPDATE", "Server operator update failed", e)
                onResult(false, safeConnectionFailure())
            }
        }
    }

    fun deleteOperatorOnServer(op: OperatorAccount, onResult: (Boolean, String?) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            if (_currentOperator.value?.role != "SuperAdmin") {
                onResult(false, t("access_denied"))
                return@launch
            }
            try {
                val api = syncManager?.getApiService()
                if (api == null || op.id <= 0) {
                    onResult(false, safeConnectionFailure())
                    return@launch
                }
                val response = api.deleteOperator(op.id, confirmed = true)
                if (!response.isSuccessful) {
                    com.safa.account.utils.SafaLogger.warn("OPERATOR_DELETE", "Operator deletion rejected with HTTP ${response.code()}")
                    onResult(false, safeServerFailure("Delete operator", response.code()))
                    return@launch
                }
                repository.removeOperatorLocally(op)
                onResult(true, null)
            } catch (e: Exception) {
                com.safa.account.utils.SafaLogger.error("OPERATOR_DELETE", "Server operator deletion failed", e)
                onResult(false, safeConnectionFailure())
            }
        }
    }

    fun logout() {
        _currentOperator.value = null
        _selectedLoginOperator.value = null
        _pinBuffer.value = ""
        _pinError.value = null
        navigateTo(AppScreen.LOCK_SCREEN)
    }

    /** Persist and expose only identity returned by the live authenticated session endpoint. */
    suspend fun restoreAuthenticatedSession(userMap: Map<String, Any?>): Boolean {
        val operator = authenticatedOperator(userMap) ?: return false
        val existing = operators.value.find { it.id == operator.id }
            ?: repository.getOperatorByMobile(operator.mobile)
        if (existing != null && existing.id != operator.id) repository.removeOperatorLocally(existing)
        repository.insertOperator(operator)
        _isBiometricEnabled.value = operator.isBiometricEnabled
        _currentOperator.value = operator
        _selectedLoginOperator.value = operator
        _pinError.value = null
        _pinBuffer.value = ""
        tokenManager?.saveLastMobile(operator.mobile)
        fetchOperatorsFromServer()
        fetchRemoteConfig()
        triggerFullSync()
        navigateTo(AppScreen.DASHBOARD)
        return true
    }

    /** Operator changes require a fresh server-authenticated session. */
    fun requestOperatorSwitch(operator: OperatorAccount) {
        if (operator.id == _currentOperator.value?.id) return
        tokenManager?.saveLastMobile(operator.mobile)
        logout()
    }


    // --- Business Functions ---

    // 1. Save Customer
    fun registerCustomer(name: String, phone: String, address: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            if (name.isBlank() || phone.isBlank()) return@launch
            val ctx = tokenManager?.getContext()
            val isOnline = com.safa.account.utils.ConnectivityMonitor.isOnline(ctx)

            if (isOnline && syncManager != null) {
                com.safa.account.utils.SafaLogger.log("ONLINE_REQUEST", "Online create customer")
                try {
                    val api = syncManager.getApiService()
                    val res = api.createCustomer(mapOf("name" to name, "phone" to phone, "address" to address))
                    if (res.isSuccessful && res.body() != null) {
                        val body = res.body()!!
                        val serverId = (body["id"] as? Number)?.toInt()
                            ?: ((body["customer"] as? Map<*, *>)?.get("id") as? Number)?.toInt() ?: 0
                        com.safa.account.utils.SafaLogger.log("SERVER_RESPONSE", "Server created customer id=$serverId")
                        repository.insertCustomer(
                            Customer(serverId = serverId, name = name, phone = phone, address = address, syncStatus = SyncStatus.SYNCED)
                        )
                        onComplete()
                        return@launch
                    } else {
                        com.safa.account.utils.SafaLogger.warn("SERVER_RESPONSE", "Create customer rejected with HTTP ${res.code()}")
                        setPinError(safeServerFailure("Create customer", res.code()))
                        return@launch
                    }
                } catch (e: Exception) {
                    com.safa.account.utils.SafaLogger.error("OFFLINE_QUEUE", "Create customer network call failed; using outbox", e)
                }
            }

            // Offline or fallback to outbox queue
            com.safa.account.utils.SafaLogger.log("OFFLINE_QUEUE", "Offline create customer")
            val localId = repository.insertCustomer(
                Customer(name = name, phone = phone, address = address, syncStatus = SyncStatus.PENDING_CREATE)
            ).toInt()

            val payloadJson = org.json.JSONObject(mapOf("local_id" to localId, "name" to name, "phone" to phone, "address" to address)).toString()
            repository.enqueueOutbox(
                SyncOutbox(
                    userId = _currentOperator.value?.id ?: 0,
                    entityType = "CUSTOMER",
                    entityLocalId = localId,
                    operation = OutboxOperation.CREATE,
                    payloadJson = payloadJson,
                    status = OutboxStatus.PENDING
                )
            )
            com.safa.account.utils.SafaLogger.log("OUTBOX_ENQUEUED", "Enqueued outbox CREATE for customer localId=$localId")
            onComplete()
            triggerFullSync()
        }
    }

    suspend fun updateCustomerProfile(customer: Customer) {
        updateCustomer(customer)
    }

    fun deleteCustomer(id: Int) {
        viewModelScope.launch {
            val target = repository.getCustomerById(id) ?: return@launch
            val ctx = tokenManager?.getContext()
            val isOnline = com.safa.account.utils.ConnectivityMonitor.isOnline(ctx)

            if (isOnline && syncManager != null && target.serverId > 0) {
                com.safa.account.utils.SafaLogger.log("ONLINE_REQUEST", "Online delete customer serverId=${target.serverId}")
                try {
                    val api = syncManager.getApiService()
                    val res = api.deleteCustomerApi(target.serverId)
                    if (res.isSuccessful) {
                        com.safa.account.utils.SafaLogger.log("SERVER_RESPONSE", "Server deleted customer serverId=${target.serverId}")
                        repository.deleteCustomerById(id)
                        return@launch
                    } else {
                        com.safa.account.utils.SafaLogger.warn("SERVER_RESPONSE", "Delete customer rejected with HTTP ${res.code()}")
                        setPinError(safeServerFailure("Delete customer", res.code()))
                        return@launch
                    }
                } catch (e: Exception) {
                    com.safa.account.utils.SafaLogger.error("OFFLINE_QUEUE", "Delete customer network call failed; using outbox", e)
                }
            }

            com.safa.account.utils.SafaLogger.log("OFFLINE_QUEUE", "Offline delete customer localId=$id")
            repository.softDeleteCustomerById(id)
            val payloadJson = org.json.JSONObject(mapOf("local_id" to id, "server_id" to target.serverId)).toString()
            repository.enqueueOutbox(
                SyncOutbox(
                    userId = _currentOperator.value?.id ?: 0,
                    entityType = "CUSTOMER",
                    entityLocalId = id,
                    entityServerId = target.serverId,
                    operation = OutboxOperation.DELETE,
                    payloadJson = payloadJson,
                    status = OutboxStatus.PENDING
                )
            )
            triggerFullSync()
        }
    }

    // 2. Save Supplier
    fun registerSupplier(name: String, phone: String, address: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            if (name.isBlank()) return@launch
            val ctx = tokenManager?.getContext()
            val isOnline = com.safa.account.utils.ConnectivityMonitor.isOnline(ctx)

            if (isOnline && syncManager != null) {
                com.safa.account.utils.SafaLogger.log("ONLINE_REQUEST", "Online create supplier")
                try {
                    val api = syncManager.getApiService()
                    val res = api.createSupplier(mapOf("name" to name, "phone" to phone, "address" to address))
                    if (res.isSuccessful && res.body() != null) {
                        val body = res.body()!!
                        val serverId = (body["id"] as? Number)?.toInt()
                            ?: ((body["supplier"] as? Map<*, *>)?.get("id") as? Number)?.toInt() ?: 0
                        com.safa.account.utils.SafaLogger.log("SERVER_RESPONSE", "Server created supplier id=$serverId")
                        repository.insertSupplier(
                            Supplier(serverId = serverId, name = name, phone = phone, address = address, syncStatus = SyncStatus.SYNCED)
                        )
                        onComplete()
                        return@launch
                    } else {
                        com.safa.account.utils.SafaLogger.warn("SERVER_RESPONSE", "Create supplier rejected with HTTP ${res.code()}")
                        setPinError(safeServerFailure("Create supplier", res.code()))
                        return@launch
                    }
                } catch (e: Exception) {
                    com.safa.account.utils.SafaLogger.error("OFFLINE_QUEUE", "Create supplier network call failed; using outbox", e)
                }
            }

            com.safa.account.utils.SafaLogger.log("OFFLINE_QUEUE", "Offline create supplier")
            val localId = repository.insertSupplier(
                Supplier(name = name, phone = phone, address = address, syncStatus = SyncStatus.PENDING_CREATE)
            ).toInt()

            val payloadJson = org.json.JSONObject(mapOf("local_id" to localId, "name" to name, "phone" to phone, "address" to address)).toString()
            repository.enqueueOutbox(
                SyncOutbox(
                    userId = _currentOperator.value?.id ?: 0,
                    entityType = "SUPPLIER",
                    entityLocalId = localId,
                    operation = OutboxOperation.CREATE,
                    payloadJson = payloadJson,
                    status = OutboxStatus.PENDING
                )
            )
            onComplete()
            triggerFullSync()
        }
    }

    fun deleteSupplier(id: Int) {
        viewModelScope.launch {
            val target = repository.getSupplierById(id) ?: return@launch
            val ctx = tokenManager?.getContext()
            val isOnline = com.safa.account.utils.ConnectivityMonitor.isOnline(ctx)

            if (isOnline && syncManager != null && target.serverId > 0) {
                com.safa.account.utils.SafaLogger.log("ONLINE_REQUEST", "Online delete supplier serverId=${target.serverId}")
                try {
                    val api = syncManager.getApiService()
                    val res = api.deleteSupplierApi(target.serverId)
                    if (res.isSuccessful) {
                        com.safa.account.utils.SafaLogger.log("SERVER_RESPONSE", "Server deleted supplier serverId=${target.serverId}")
                        repository.deleteSupplierById(id)
                        return@launch
                    } else {
                        com.safa.account.utils.SafaLogger.warn("SERVER_RESPONSE", "Delete supplier rejected with HTTP ${res.code()}")
                        setPinError(safeServerFailure("Delete supplier", res.code()))
                        return@launch
                    }
                } catch (e: Exception) {
                    com.safa.account.utils.SafaLogger.error("OFFLINE_QUEUE", "Delete supplier network call failed; using outbox", e)
                }
            }

            com.safa.account.utils.SafaLogger.log("OFFLINE_QUEUE", "Offline delete supplier localId=$id")
            repository.softDeleteSupplierById(id)
            val payloadJson = org.json.JSONObject(mapOf("local_id" to id, "server_id" to target.serverId)).toString()
            repository.enqueueOutbox(
                SyncOutbox(
                    userId = _currentOperator.value?.id ?: 0,
                    entityType = "SUPPLIER",
                    entityLocalId = id,
                    entityServerId = target.serverId,
                    operation = OutboxOperation.DELETE,
                    payloadJson = payloadJson,
                    status = OutboxStatus.PENDING
                )
            )
            triggerFullSync()
        }
    }

    // 3. Purchase / Deposit SAR to Supplier to Acquire BDT
    fun depositToSupplier(
        supplierId: Int,
        amountSar: BigDecimal,
        rate: BigDecimal,
        paidBdt: BigDecimal = MoneyMath.ZERO_AMOUNT,
        notes: String,
        transactionType: String = "SAR_GIVEN",
        ledgerId: Int = 0,
        timestamp: Long = System.currentTimeMillis(),
        onComplete: () -> Unit
    ) {
        viewModelScope.launch {
            if (supplierId > 0 && amountSar.signum() > 0 && rate.signum() > 0) {
                val exactAmountSar = MoneyMath.nonNegativeAmount(amountSar)
                val exactRate = MoneyMath.nonNegativeRate(rate)
                val exactPaidBdt = MoneyMath.nonNegativeAmount(paidBdt)
                val amtBdt = MoneyMath.multiply(exactAmountSar, exactRate)
                val depositId = repository.insertSupplierDeposit(
                    SupplierDeposit(
                        supplierId = supplierId,
                        amountSar = exactAmountSar,
                        rate = exactRate,
                        amountBdt = amtBdt,
                        paidBdt = exactPaidBdt,
                        transactionType = transactionType,
                        notes = notes,
                        timestamp = timestamp
                    )
                )
                
                if ((transactionType == "SAR_GIVEN" || transactionType == "SAR_DEPOSIT") && ledgerId > 0) {
                    val supplierName = suppliers.value.find { it.id == supplierId }?.name ?: "Supplier"
                    repository.insertWalletBatch(
                        WalletBatch(
                            ledgerId = ledgerId,
                            rate = exactRate,
                            initialBdt = amtBdt,
                            remainingBdt = amtBdt,
                            supplierId = supplierId,
                            supplierDepositId = depositId.toInt(),
                            notes = "Purchased BDT from $supplierName"
                        )
                    )
                }
                onComplete()
                syncManager?.syncAll()
            }
        }
    }

    fun deleteSupplierDeposit(id: Int) {
        viewModelScope.launch {
            repository.softDeleteSupplierDepositById(id)
            repository.softDeleteWalletBatchBySupplierDepositId(id)
            syncManager?.syncAll()
        }
    }

    fun updateSupplierDeposit(deposit: SupplierDeposit) {
        viewModelScope.launch {
            repository.updateSupplierDeposit(deposit)
            val batches = repository.allWalletBatches.firstOrNull() ?: emptyList()
            val match = batches.find { it.supplierDepositId == deposit.id }
            if (match != null) {
                val newAmountBdt = MoneyMath.multiply(deposit.amountSar, deposit.rate)
                val diff = MoneyMath.subtract(newAmountBdt, match.initialBdt)
                val updatedRemaining = MoneyMath.clampNonNegativeAmount(MoneyMath.add(match.remainingBdt, diff))
                repository.updateWalletBatch(
                    match.copy(
                        rate = deposit.rate,
                        initialBdt = newAmountBdt,
                        remainingBdt = updatedRemaining
                    )
                )
            }
            syncManager?.syncAll()
        }
    }

    // --- Wallet Manual Operations ---
    fun registerWalletLedger(name: String, onComplete: () -> Unit = {}) {
        if (name.isNotBlank()) {
            viewModelScope.launch {
                repository.insertWalletLedger(WalletLedger(name = name))
                onComplete()
                syncManager?.syncAll()
            }
        }
    }

    fun updateWalletLedgerName(id: Int, newName: String, onComplete: () -> Unit = {}) {
        if (newName.isNotBlank()) {
            viewModelScope.launch {
                val list = repository.allWalletLedgers.firstOrNull() ?: emptyList()
                val target = list.find { it.id == id }
                if (target != null) {
                    repository.updateWalletLedger(target.copy(name = newName))
                    onComplete()
                    syncManager?.syncAll()
                }
            }
        }
    }

    fun deleteWalletLedger(id: Int, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.softDeleteWalletLedgerById(id)
            val batches = repository.allWalletBatches.firstOrNull()?.filter { it.ledgerId == id } ?: emptyList()
            batches.forEach {
                repository.softDeleteWalletBatchById(it.id)
            }
            onComplete()
            syncManager?.syncAll()
        }
    }

    fun addMoneyToWallet(ledgerId: Int, amountBdt: BigDecimal, rate: BigDecimal, notes: String, onComplete: () -> Unit = {}) {
        if (ledgerId > 0 && amountBdt.signum() > 0 && rate.signum() > 0) {
            viewModelScope.launch {
                repository.insertWalletBatch(
                    WalletBatch(
                        ledgerId = ledgerId,
                        rate = MoneyMath.nonNegativeRate(rate),
                        initialBdt = MoneyMath.nonNegativeAmount(amountBdt),
                        remainingBdt = MoneyMath.nonNegativeAmount(amountBdt),
                        notes = if (notes.isNotBlank()) notes else "Manual Capital Deposit"
                    )
                )
                onComplete()
                syncManager?.syncAll()
            }
        }
    }

    fun deductMoneyFromWalletLedger(ledgerId: Int, amountBdtToDeduct: BigDecimal, onComplete: () -> Unit = {}) {
        if (ledgerId > 0 && amountBdtToDeduct.signum() > 0) {
            viewModelScope.launch {
                val batches = repository.allWalletBatches.firstOrNull()
                    ?.filter { it.ledgerId == ledgerId && it.remainingBdt.signum() > 0 }
                    ?.sortedBy { it.timestamp } ?: emptyList()
                var remainingToDeduct = MoneyMath.nonNegativeAmount(amountBdtToDeduct)
                for (b in batches) {
                    if (remainingToDeduct.signum() <= 0) break
                    val bRemaining = b.remainingBdt
                    val deductFromThisBatch = if (bRemaining < remainingToDeduct) bRemaining else remainingToDeduct
                    repository.updateWalletBatch(b.copy(remainingBdt = MoneyMath.subtract(bRemaining, deductFromThisBatch)))
                    remainingToDeduct = MoneyMath.subtract(remainingToDeduct, deductFromThisBatch)
                }
                onComplete()
                syncManager?.syncAll()
            }
        }
    }

    fun deductMoneyFromWalletBatch(batchId: Int, amountBdtToDeduct: BigDecimal, onComplete: () -> Unit = {}) {
        if (batchId > 0 && amountBdtToDeduct.signum() > 0) {
            viewModelScope.launch {
                val batch = repository.getWalletBatchById(batchId)
                if (batch != null) {
                    val updatedRemaining = MoneyMath.clampNonNegativeAmount(
                        MoneyMath.subtract(batch.remainingBdt, MoneyMath.nonNegativeAmount(amountBdtToDeduct))
                    )
                    repository.updateWalletBatch(batch.copy(remainingBdt = updatedRemaining))
                    onComplete()
                    syncManager?.syncAll()
                }
            }
        }
    }

    // 4. Create Remittance Transaction (Safa Entry)
    fun createRemittance(
        customerId: Int,
        walletBatchId: Int,
        amountSar: BigDecimal,
        customerRate: BigDecimal,
        receiverName: String,
        receiverPhone: String,
        receiverAccountType: String,
        receiverAccountNo: String,
        notes: String,
        sarCollected: BigDecimal? = null,
        bdtDisbursed: BigDecimal? = null,
        status: String = "Pending",
        timestamp: Long? = null,
        onComplete: () -> Unit
    ) {
        viewModelScope.launch {
            val batch = if (walletBatchId > 0) repository.getWalletBatchById(walletBatchId) else null
            val exactAmountSar = MoneyMath.nonNegativeAmount(amountSar)
            val exactCustomerRate = MoneyMath.nonNegativeRate(customerRate)
            val resolvedSupplierRate = batch?.rate ?: exactCustomerRate
            val resolvedSupplierId = batch?.supplierId ?: 0

            val operatorId = _currentOperator.value?.id ?: 1
            val amountBdt = MoneyMath.multiply(exactAmountSar, exactCustomerRate)
            val actualSarCollected = MoneyMath.amount(sarCollected ?: exactAmountSar)
            val actualBdtDisbursed = MoneyMath.nonNegativeAmount(bdtDisbursed ?: amountBdt)
            val actualTimestamp = timestamp ?: System.currentTimeMillis()

            val ctx = tokenManager?.getContext()
            val isOnline = com.safa.account.utils.ConnectivityMonitor.isOnline(ctx)

            if (isOnline && syncManager != null) {
                com.safa.account.utils.SafaLogger.log("ONLINE_REQUEST", "Online create transaction")
                try {
                    val api = syncManager.getApiService()
                    val reqMap = mapOf(
                        "type" to status,
                        "amount_sar" to MoneyMath.amountString(exactAmountSar),
                        "customer_id" to customerId,
                        "supplier_id" to resolvedSupplierId,
                        "customer_rate" to MoneyMath.rateString(exactCustomerRate),
                        "supplier_rate" to MoneyMath.rateString(resolvedSupplierRate),
                        "amount_bdt" to MoneyMath.amountString(amountBdt),
                        "sar_collected" to MoneyMath.amountString(actualSarCollected),
                        "bdt_disbursed" to MoneyMath.amountString(actualBdtDisbursed),
                        "receiver_name" to receiverName,
                        "receiver_phone" to receiverPhone,
                        "receiver_account_type" to receiverAccountType,
                        "receiver_account_no" to receiverAccountNo,
                        "wallet_batch_id" to walletBatchId,
                        "notes" to notes
                    )
                    val res = api.createTransactionApi(reqMap)
                    if (res.isSuccessful && res.body() != null) {
                        val body = res.body()!!
                        val serverId = (body["id"] as? Number)?.toInt()
                            ?: ((body["transaction"] as? Map<*, *>)?.get("id") as? Number)?.toInt() ?: 0
                        com.safa.account.utils.SafaLogger.log("SERVER_RESPONSE", "Server created transaction serverId=$serverId")
                        repository.insertTransaction(
                            RemittanceTransaction(
                                serverId = serverId,
                                customerId = customerId,
                                supplierId = resolvedSupplierId,
                                amountSar = exactAmountSar,
                                customerRate = exactCustomerRate,
                                supplierRate = resolvedSupplierRate,
                                amountBdt = amountBdt,
                                receiverName = receiverName,
                                receiverPhone = receiverPhone,
                                receiverAccountType = receiverAccountType,
                                receiverAccountNo = receiverAccountNo,
                                status = status,
                                operatorId = operatorId,
                                notes = notes,
                                sarCollected = actualSarCollected,
                                bdtDisbursed = actualBdtDisbursed,
                                timestamp = actualTimestamp,
                                walletBatchId = walletBatchId,
                                syncStatus = SyncStatus.SYNCED
                            )
                        )
                        if (batch != null) {
                            repository.updateWalletBatch(
                                batch.copy(remainingBdt = MoneyMath.clampNonNegativeAmount(MoneyMath.subtract(batch.remainingBdt, amountBdt)))
                            )
                        }
                        onComplete()
                        return@launch
                    } else {
                        com.safa.account.utils.SafaLogger.warn("SERVER_RESPONSE", "Create transaction rejected with HTTP ${res.code()}")
                        setPinError(safeServerFailure("Create transaction", res.code()))
                        return@launch
                    }
                } catch (e: Exception) {
                    com.safa.account.utils.SafaLogger.error("OFFLINE_QUEUE", "Create transaction network call failed; using outbox", e)
                }
            }

            // Offline or fallback to outbox queue
            com.safa.account.utils.SafaLogger.log("OFFLINE_QUEUE", "Offline create transaction")
            val localTx = RemittanceTransaction(
                customerId = customerId,
                supplierId = resolvedSupplierId,
                amountSar = exactAmountSar,
                customerRate = exactCustomerRate,
                supplierRate = resolvedSupplierRate,
                amountBdt = amountBdt,
                receiverName = receiverName,
                receiverPhone = receiverPhone,
                receiverAccountType = receiverAccountType,
                receiverAccountNo = receiverAccountNo,
                status = status,
                operatorId = operatorId,
                notes = notes,
                sarCollected = actualSarCollected,
                bdtDisbursed = actualBdtDisbursed,
                timestamp = actualTimestamp,
                walletBatchId = walletBatchId,
                syncStatus = SyncStatus.PENDING_CREATE
            )
            val localId = repository.insertTransaction(localTx).toInt()

            if (batch != null) {
                repository.updateWalletBatch(
                    batch.copy(remainingBdt = MoneyMath.clampNonNegativeAmount(MoneyMath.subtract(batch.remainingBdt, amountBdt)))
                )
            }

            val payloadMap = mapOf(
                "local_id" to localId,
                "type" to status,
                "amount_sar" to MoneyMath.amountString(exactAmountSar),
                "customer_id" to customerId,
                "supplier_id" to resolvedSupplierId,
                "customer_rate" to MoneyMath.rateString(exactCustomerRate),
                "supplier_rate" to MoneyMath.rateString(resolvedSupplierRate),
                "amount_bdt" to MoneyMath.amountString(amountBdt),
                "sar_collected" to MoneyMath.amountString(actualSarCollected),
                "bdt_disbursed" to MoneyMath.amountString(actualBdtDisbursed),
                "receiver_name" to receiverName,
                "receiver_phone" to receiverPhone,
                "receiver_account_type" to receiverAccountType,
                "receiver_account_no" to receiverAccountNo,
                "wallet_batch_id" to walletBatchId,
                "notes" to notes
            )
            repository.enqueueOutbox(
                SyncOutbox(
                    userId = _currentOperator.value?.id ?: 0,
                    entityType = "TRANSACTION",
                    entityLocalId = localId,
                    operation = OutboxOperation.CREATE,
                    payloadJson = org.json.JSONObject(payloadMap).toString(),
                    status = OutboxStatus.PENDING
                )
            )
            onComplete()
            triggerFullSync()
        }
    }

    fun updateTransactionStatus(transaction: RemittanceTransaction, newStatus: String) {
        viewModelScope.launch {
            if (newStatus == "Cancelled" && transaction.status != "Cancelled") {
                // Refund BDT back to the Wallet Batch
                if (transaction.walletBatchId > 0) {
                    val batch = repository.getWalletBatchById(transaction.walletBatchId)
                    if (batch != null) {
                        repository.updateWalletBatch(batch.copy(remainingBdt = MoneyMath.add(batch.remainingBdt, transaction.amountBdt)))
                    }
                }
            } else if (newStatus != "Cancelled" && transaction.status == "Cancelled") {
                // Re-deduct BDT from the Wallet Batch
                if (transaction.walletBatchId > 0) {
                    val batch = repository.getWalletBatchById(transaction.walletBatchId)
                    if (batch != null) {
                        repository.updateWalletBatch(
                            batch.copy(remainingBdt = MoneyMath.clampNonNegativeAmount(MoneyMath.subtract(batch.remainingBdt, transaction.amountBdt)))
                        )
                    }
                }
            }
            repository.updateTransaction(
                transaction.copy(status = newStatus)
            )
            syncManager?.syncAll()
        }
    }

    fun deleteTransaction(id: Int, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            val all = repository.allTransactions.firstOrNull() ?: emptyList()
            val tx = all.find { it.id == id }
            if (tx != null) {
                if (tx.status != "Cancelled" && tx.walletBatchId > 0) {
                    val batch = repository.getWalletBatchById(tx.walletBatchId)
                    if (batch != null) {
                        repository.updateWalletBatch(batch.copy(remainingBdt = MoneyMath.add(batch.remainingBdt, tx.amountBdt)))
                    }
                }
                repository.softDeleteTransactionById(id)
                onComplete()
                syncManager?.syncAll()
            }
        }
    }

    // 5. Save Operational Expense / Income
    fun addExpenseIncome(
        title: String,
        amount: BigDecimal,
        currency: String,
        isExpense: Boolean,
        category: String,
        onComplete: () -> Unit
    ) {
        viewModelScope.launch {
            if (title.isNotBlank() && amount.signum() > 0) {
                repository.insertExpenseIncome(
                    ExpenseIncome(
                        title = title,
                        amount = MoneyMath.nonNegativeAmount(amount),
                        currency = currency,
                        isExpense = isExpense,
                        category = category
                    )
                )
                onComplete()
                syncManager?.syncAll()
            }
        }
    }

    fun removeExpenseIncome(id: Int) {
        viewModelScope.launch {
            repository.softDeleteExpenseIncomeById(id)
            syncManager?.syncAll()
        }
    }

    fun triggerFullSync() {
        viewModelScope.launch {
            try {
                syncManager?.syncAll()
            } catch (e: Exception) {
                com.safa.account.utils.SafaLogger.error("SYNC", "Background sync trigger failed", e)
            }
        }
    }

    // 6. Update Daily Standard Market Rates
    fun publishDailyRates(customerRate: BigDecimal, supplierRate: BigDecimal, onComplete: () -> Unit) {
        viewModelScope.launch {
            val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            val updated = DailyRate(
                date = dateStr,
                customerRate = MoneyMath.nonNegativeRate(customerRate),
                supplierRate = MoneyMath.nonNegativeRate(supplierRate)
            )
            repository.insertDailyRate(updated)
            _currentRates.value = updated
            onComplete()
            syncManager?.syncAll()
        }
    }

    // 7. Add Staff Operator
    fun updateOperator(operator: OperatorAccount, onComplete: () -> Unit = {}) {
        _currentOperator.value = operator
        viewModelScope.launch {
            repository.updateOperator(operator)
            onComplete()
            syncManager?.syncAll()
        }
    }

    /** Change the current PIN through the authenticated server authority. */
    fun updateOperatorPin(currentPin: String, newPin: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val op = _currentOperator.value
            if (op == null || currentPin.length != 6 || newPin.length != 6 ||
                !currentPin.all { it.isDigit() } || !newPin.all { it.isDigit() } || currentPin == newPin
            ) {
                onResult(false, if (_currentLanguage.value == "BN") "দুটি আলাদা ৬ সংখ্যার পিন দিন।" else "Enter two different six-digit PINs.")
                return@launch
            }

            try {
                val api = syncManager?.getApiService()
                if (api == null) {
                    onResult(false, safeConnectionFailure())
                    return@launch
                }
                val response = api.changePin(com.safa.account.data.api.dto.ChangePinRequest(currentPin, newPin))
                if (!response.isSuccessful) {
                    com.safa.account.utils.SafaLogger.warn("PIN_CHANGE", "PIN change rejected with HTTP ${response.code()}")
                    val message = when (response.code()) {
                        401 -> if (_currentLanguage.value == "BN") "বর্তমান পিন সঠিক নয়।" else "Current PIN is incorrect."
                        422 -> if (_currentLanguage.value == "BN") "নতুন পিনটি আলাদা ৬ সংখ্যার হতে হবে।" else "The new PIN must be a different six-digit PIN."
                        else -> safeServerFailure("PIN change", response.code())
                    }
                    onResult(false, message)
                    return@launch
                }

                // PIN verifiers remain server-only; no reusable PIN material is
                // persisted in the Android operator cache.
                val updatedOp = op.copy(pin = "")
                repository.updateOperator(updatedOp)
                _currentOperator.value = updatedOp
                onResult(true, null)
            } catch (e: Exception) {
                com.safa.account.utils.SafaLogger.error("PIN_CHANGE", "PIN change request failed", e)
                onResult(false, safeConnectionFailure())
            }
        }
    }

    // Disable demo data injection; all data is fetched live from server
    fun injectDemoSandboxData() {
        // No-op: Only real server data is used
    }
}

class SafaViewModelFactory(
    private val repository: AppRepository,
    private val tokenManager: TokenManager? = null
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SafaViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SafaViewModel(repository, tokenManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
