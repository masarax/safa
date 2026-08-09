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
import com.safa.account.data.repository.AppRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
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
    val totalSarReceived: Double = 0.0,
    val totalBdtDelivered: Double = 0.0,
    val totalBdtPending: Double = 0.0,
    val totalProfitBdt: Double = 0.0,
    val totalProfitSar: Double = 0.0,
    val totalPaidToSuppliersSar: Double = 0.0,
    val totalBoughtPoolBdt: Double = 0.0,
    val totalExpensesBdt: Double = 0.0,
    val totalOtherIncomeBdt: Double = 0.0,
    val supplierUnsettledSar: Double = 0.0,
    val supplierUnsettledBdt: Double = 0.0
)

class SafaViewModel(
    val repository: AppRepository,
    val tokenManager: TokenManager? = null
) : ViewModel() {

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
                    onResult(false, res.exceptionOrNull()?.localizedMessage ?: "Connection Failed")
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
                    onResult(false, res.exceptionOrNull()?.localizedMessage ?: "Sync Error")
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
                e.printStackTrace()
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
                e.printStackTrace()
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
                e.printStackTrace()
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
                e.printStackTrace()
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

    // Financial Summaries Derived State
    val financialStats: StateFlow<FinancialStats> = combine(
        transactions, supplierDeposits, expensesIncomes
    ) { txs, deposits, expenses ->
        
        var totalSar = java.math.BigDecimal.ZERO
        var totalDeliveredBdt = java.math.BigDecimal.ZERO
        var totalPendingBdt = java.math.BigDecimal.ZERO
        var profitBdt = java.math.BigDecimal.ZERO
        var profitSar = java.math.BigDecimal.ZERO
        var bdtFromSupplierPoolUsed = java.math.BigDecimal.ZERO

        for (tx in txs) {
            val amtSar = java.math.BigDecimal(tx.amountSar.toString())
            val amtBdt = java.math.BigDecimal(tx.amountBdt.toString())
            totalSar = totalSar.add(amtSar)
            if (tx.status == "Delivered") {
                totalDeliveredBdt = totalDeliveredBdt.add(amtBdt)
            } else if (tx.status == "Pending") {
                totalPendingBdt = totalPendingBdt.add(amtBdt)
            }
            if (tx.status != "Cancelled") {
                profitBdt = profitBdt.add(java.math.BigDecimal(tx.getProfitBdt().toString()))
                profitSar = profitSar.add(java.math.BigDecimal(tx.getProfitSar().toString()))
                bdtFromSupplierPoolUsed = bdtFromSupplierPoolUsed.add(amtBdt)
            }
        }

        var totalPaidSupplierSar = java.math.BigDecimal.ZERO
        var totalBoughtBdt = java.math.BigDecimal.ZERO

        for (dep in deposits) {
            val depAmtSar = java.math.BigDecimal(dep.amountSar.toString())
            val depAmtBdt = java.math.BigDecimal(dep.amountBdt.toString())
            if (dep.transactionType == "SAR_DEPOSIT" || dep.transactionType == "SAR_GIVEN") {
                totalPaidSupplierSar = totalPaidSupplierSar.add(depAmtSar)
                totalBoughtBdt = totalBoughtBdt.add(depAmtBdt)
            } else if (dep.transactionType == "BDT_WITHDRAW") {
                totalBoughtBdt = totalBoughtBdt.subtract(depAmtBdt)
            }
        }

        var totalExp = java.math.BigDecimal.ZERO
        var totalInc = java.math.BigDecimal.ZERO
        
        val currentRateVal = _currentRates.value?.supplierRate ?: 32.5
        val currentRate = java.math.BigDecimal(currentRateVal.toString())

        for (item in expenses) {
            val itemAmt = java.math.BigDecimal(item.amount.toString())
            val amt = if (item.currency == "SAR") itemAmt.multiply(currentRate) else itemAmt
            if (item.isExpense) {
                totalExp = totalExp.add(amt)
            } else {
                totalInc = totalInc.add(amt)
            }
        }

        val outstandingBdt = totalBoughtBdt.subtract(bdtFromSupplierPoolUsed)
        val outstandingSar = if (outstandingBdt.compareTo(java.math.BigDecimal.ZERO) != 0 && currentRate.compareTo(java.math.BigDecimal.ZERO) != 0) {
            val usedSar = bdtFromSupplierPoolUsed.divide(currentRate, 4, java.math.RoundingMode.HALF_UP)
            totalPaidSupplierSar.subtract(usedSar)
        } else {
            java.math.BigDecimal.ZERO
        }

        FinancialStats(
            totalSarReceived = totalSar.toDouble(),
            totalBdtDelivered = totalDeliveredBdt.toDouble(),
            totalBdtPending = totalPendingBdt.toDouble(),
            totalProfitBdt = profitBdt.toDouble(),
            totalProfitSar = profitSar.toDouble(),
            totalPaidToSuppliersSar = totalPaidSupplierSar.toDouble(),
            totalBoughtPoolBdt = totalBoughtBdt.toDouble(),
            totalExpensesBdt = totalExp.toDouble(),
            totalOtherIncomeBdt = totalInc.toDouble(),
            supplierUnsettledSar = outstandingSar.toDouble(),
            supplierUnsettledBdt = outstandingBdt.toDouble()
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FinancialStats())

    // --- Daily Rates ---
    private val _currentRates = MutableStateFlow<DailyRate?>(null)
    val currentRates: StateFlow<DailyRate?> = _currentRates.asStateFlow()

    init {
        // Automatically check/load rates for today on startup
        refreshTodayRates()
        fetchRemoteConfig()
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
                    val latest = allRates.first()
                    _currentRates.value = DailyRate(dateStr, latest.customerRate, latest.supplierRate)
                } else {
                    _currentRates.value = DailyRate(dateStr, 32.0, 32.5) // Standard baseline
                }
            }
        }
    }

    // --- Translations (BN/EN) ---
    val bnMap = mapOf(
        "app_title" to "SAFA",
        "dashboard" to "ড্যাশবোর্ড",
        "wallet" to "ওয়ালেট",
        "customers" to "গ্রাহকগণ",
        "suppliers" to "সাপ্লায়ার",
        "transactions" to "লেনদেন সমূহ",
        "expenses" to "আয়/ব্যায়",
        "settings" to "সেটিংস",
        "login_title" to "SAFA সিকিউরিটি লক",
        "select_operator" to "ইউজার অ্যাকাউন্ট নির্বাচন করুন",
        "enter_pin" to "৬-ডিজিটের সিকিউরিটি পিন দিন",
        "pin_incorrect" to "ভুল পিন নম্বর! আবার চেষ্টা করুন।",
        "logout" to "লগ আউট",
        "change_role" to "রোল পরিবর্তন করুন",
        "role_owner" to "মালিক",
        "role_staff" to "স্টাফ",
        "operator_blocked" to "অ্যাকাউন্টটি সাময়িকভাবে স্থগিত আছে।",
        "access_denied" to "অনুমতি নেই",
        "permission_required" to "এই ফিচারের জন্য আপনার অনুমতি প্রয়োজন।",
        "no_account_found" to "এই মোবাইল নম্বরে কোনো অ্যাকাউন্ট পাওয়া যায়নি",
        "activation_title" to "সুপার-এডমিন অ্যাক্টিভেশন",
        "activation_desc" to "আপনার অ্যাডমিনিস্ট্রেটর অ্যাকাউন্ট সেটআপ করুন",
        "full_name" to "পূর্ণ নাম",
        "email" to "ইমেইল ঠিকানা",
        "complete_activation" to "অ্যাক্টিভেশন সম্পন্ন করুন",
        "login_button" to "লগইন করুন",
        "mobile_number" to "মোবাইল নম্বর",
        "enter_mobile_ph" to "যেমন: 01700000000",
        "activate_super_admin" to "সুপার-এডমিন অ্যাক্টিভেশন",
        "invalid_credentials" to "মোবাইল বা পিন ভুল হয়েছে",

        "total_sar_received" to "মোট প্রাপ্ত বৈদেশিক কারেন্সি",
        "total_bdt_delivered" to "মোট বিতরণকৃত লোকাল টাকা",
        "total_bdt_pending" to "মোট পেন্ডিং লোকাল টাকা",
        "estimated_profit" to "আজকের আনুমানিক মোট লাভ",
        "net_profit_bdt" to "নিট লাভ (টাকা)",
        "net_profit_sar" to "নিট লাভ (রিয়াল)",
        "daily_operating_rates" to "লাইভ দৈনিক বিনিময় হার",
        "customer_sale_rate" to "কাস্টমার বিনিময় হার",
        "supplier_buy_rate" to "সাপ্লায়ার ক্রয় হার (খরচ)",

        "customer_mgmt" to "গ্রাহক ডিরেক্টরি",
        "add_customer" to "নতুন কাস্টমার যুক্ত করুন",
        "customer_name" to "কাস্টমারের পূর্ণ নাম",
        "phone_number" to "মোবাইল নম্বর",
        "address" to "ঠিকানা / কর্মস্থল",
        "save_customer" to "গ্রাহক নিবন্ধন করুন",
        "total_customers" to "মোট নিবন্ধিত কাস্টমার",

        "supplier_mgmt" to "সাপ্লায়ারসমূহ",
        "add_supplier" to "নতুন সাপ্লায়ার যুক্ত করুন",
        "supplier_name" to "সাপ্লায়ার গ্রুপ নাম",
        "save_supplier" to "সাপ্লায়ার তথ্য সংরক্ষণ করুন",
        "buy_bdt_pool" to "লোকাল ফান্ড ক্রয় করুন",
        "amount_sar" to "ডেবিট বৈদেশিক পরিমাণ",
        "rate_applied" to "বিনিময় হার (যেমন: ৩২.৫০)",
        "purchase_success" to "লোকাল ফান্ড সফলভাবে প্রক্রিয়াজাত হয়েছে",
        "total_deposited_sar" to "মোট জমা বৈদেশিক ফান্ড",
        "acquired_bdt" to "অর্জিত লোকাল ফান্ড",
        "pool_balance" to "সাপ্লায়ার বাকি ফান্ড",

        "new_remittance" to "নতুন রেমিট্যান্স লেনদেন",
        "select_customer" to "কাস্টমার নির্বাচন করুন",
        "select_supplier" to "সাপ্লায়ার নির্বাচন করুন",
        "saudi_amount" to "প্রাপ্ত বৈদেশিক পরিমাণ",
        "customer_assigned_rate" to "কাস্টমার নির্ধারিত হার",
        "supplier_rate_tx" to "প্রযোজ্য সাপ্লায়ার হার",
        "receiver_bdt_amount" to "প্রাপকের লোকাল টাকা",
        "receiver_name" to "প্রাপকের পূর্ণ নাম",
        "receiver_phone" to "প্রাপকের মোবাইল নম্বর",
        "payment_method" to "পেমেন্ট চ্যানেল",
        "receiver_account_no" to "অ্যাকাউন্ট নং (বিকাশ/নগদ/ব্যাংক)",
        "notes" to "অতিরিক্ত নির্দেশাবলী / নোট",
        "status" to "লেনদেনের স্ট্যাটাস",
        "status_pending" to "পেন্ডিং ট্রান্সমিট",
        "status_delivered" to "সফলভাবে ডেলিভার্ড",
        "status_cancelled" to "বাতিলকৃত লেনদেন",
        "save_transaction" to "লেনদেন নিশ্চিত ও সংরক্ষণ করুন",

        "expenses_overhead" to "অপারেটিং খরচ ও অন্যান্য",
        "add_expense_income" to "আয়/ব্যয় এন্ট্রি করুন",
        "title" to "বিবরণ (যেমন: অফিস ভাড়া, নাস্তা)",
        "amount" to "পরিমাণ",
        "is_expense" to "লেনদেনের ধরণ",
        "expense" to "অপারেটিং ব্যয়",
        "income" to "অপারেটিং আয়",
        "category" to "ক্যাটাগরি",
        "save_record" to "রেকর্ড সংরক্ষণ করুন",

        "update_daily_rates" to "লাইভ বিনিময় হার প্রকাশ করুন",
        "rate_saved" to "বিনিময় হার সফলভাবে সংরক্ষিত হয়েছে!",
        "operator_list" to "অনুমোদিত অপারেটর ও কর্মী",
        "create_new_operator" to "নতুন স্টাফ অ্যাকাউন্ট তৈরি করুন",
        "pinCode" to "৬-ডিজিটের পিন অ্যাক্সেস",
        "role" to "ইউজার রোল / অনুমতি",
        "unsettled_supp" to "সাপ্লায়ার নিট দেনা/পাওনা",

        "manage_operators" to "অপারেটর ও স্টাফ ইউজার ম্যানেজমেন্ট",
        "operator_management_desc" to "সার্ভারে ইউজারদের ১৫টি দানাদার RBAC অনুমতি নিয়ন্ত্রণ করুন",
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

    fun loginWithBiometric(operator: OperatorAccount) {
        if (operator.isActive) {
            _currentOperator.value = operator
            _pinError.value = null
            _pinBuffer.value = ""
            tokenManager?.saveLastMobile(operator.mobile)
            fetchOperatorsFromServer()
            fetchRemoteConfig()
            triggerFullSync()
            navigateTo(AppScreen.DASHBOARD)
        } else {
            _pinError.value = t("operator_blocked")
            _pinBuffer.value = ""
        }
    }

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
                        val tokens = body["tokens"] as? Map<String, Any?>
                        if (tokens != null) {
                            tokenManager?.saveAllTokens(
                                accessToken = tokens["access_token"]?.toString(),
                                refreshToken = tokens["refresh_token"]?.toString(),
                                deviceToken = tokens["device_token"]?.toString(),
                                sessionToken = tokens["session_token"]?.toString(),
                                fingerprintToken = tokens["fingerprint_token"]?.toString()
                            )
                        }

                        @Suppress("UNCHECKED_CAST")
                        val userMap = body["user"] as? Map<String, Any?>
                        @Suppress("UNCHECKED_CAST")
                        val permsMap = (userMap?.get("permissions") as? Map<String, Any?>)
                            ?: (body["permissions"] as? Map<String, Any?>) ?: emptyMap()

                        val isActivated = (userMap?.get("is_activated") as? Boolean) ?: true
                        val role = userMap?.get("role")?.toString() ?: "SuperAdmin"
                        val username = userMap?.get("name")?.toString() ?: "Operator"
                        val email = userMap?.get("email")?.toString() ?: ""

                        fun getPerm(key: String): Boolean {
                            val v = permsMap[key]
                            return if (v is Boolean) v else true
                        }

                        val hashedPin = com.safa.account.utils.HashUtils.hashPin(pin)
                        val op = OperatorAccount(
                            username = username,
                            role = if (role.equals("superadmin", true) || role.equals("manager", true) || role.equals("owner", true)) "SuperAdmin" else "Staff",
                            pin = hashedPin,
                            mobile = mobile.trim(),
                            email = email,
                            isActivated = isActivated,
                            isActive = true,
                            canViewCustomers = getPerm("can_view_customers"),
                            canAddCustomers = getPerm("can_add_customers"),
                            canEditCustomers = getPerm("can_edit_customers"),
                            canDeleteCustomers = getPerm("can_delete_customers"),
                            canViewSuppliers = getPerm("can_view_suppliers"),
                            canAddSuppliers = getPerm("can_add_suppliers"),
                            canEditSuppliers = getPerm("can_edit_suppliers"),
                            canDeleteSuppliers = getPerm("can_delete_suppliers"),
                            canViewTransactions = getPerm("can_view_transactions"),
                            canAddTransactions = getPerm("can_add_transactions"),
                            canEditTransactions = getPerm("can_edit_transactions"),
                            canDeleteTransactions = getPerm("can_delete_transactions"),
                            canManageWallet = getPerm("can_manage_wallet"),
                            canManageExpenses = getPerm("can_manage_expenses"),
                            canViewReports = getPerm("can_view_reports")
                        )

                        val existing = repository.getOperatorByMobile(mobile.trim())
                            ?: operators.value.find { it.mobile == mobile.trim() }
                        val savedId = if (existing != null) {
                            val updated = op.copy(id = existing.id)
                            repository.updateOperator(updated)
                            existing.id
                        } else {
                            repository.insertOperator(op).toInt()
                        }
                        val finalOp = op.copy(id = savedId)
                        _currentOperator.value = finalOp
                        _selectedLoginOperator.value = finalOp
                        _pinError.value = null
                        tokenManager?.saveLastMobile(mobile.trim())
                        fetchOperatorsFromServer()
                        fetchRemoteConfig()
                        triggerFullSync()
                        navigateTo(AppScreen.DASHBOARD)
                        onResult(true, null)
                        return@launch
                    } else {
                        val errorStr = response.errorBody()?.string() ?: ""
                        onResult(false, if (errorStr.isNotBlank() && !errorStr.startsWith("{")) errorStr else t("invalid_credentials"))
                        return@launch
                    }
                } else {
                    onResult(false, "Server API not configured")
                    return@launch
                }
            } catch (e: Exception) {
                e.printStackTrace()
                onResult(false, e.localizedMessage ?: t("invalid_credentials"))
                return@launch
            }
        }
    }

    // loginWithMobileAndPin() REMOVED: All authentication is server-driven via loginWithServer()

    fun activateSuperAdminServer(
        name: String,
        email: String,
        mobile: String,
        pin: String,
        onComplete: () -> Unit
    ) {
        viewModelScope.launch {
            if (pin.length != 6 || !pin.all { it.isDigit() }) return@launch
            try {
                val api = syncManager?.getApiService()
                if (api != null) {
                    val req = com.safa.account.data.api.dto.ActivateSuperAdminRequest(
                        name = name.trim(),
                        email = email.trim(),
                        mobile = mobile.trim(),
                        pin = pin,
                        newPin = pin
                    )
                    val response = api.activateSuperAdmin(req)
                    if (response.isSuccessful && response.body() != null) {
                        val body = response.body()!!
                        @Suppress("UNCHECKED_CAST")
                        val tokens = body["tokens"] as? Map<String, Any?>
                        if (tokens != null) {
                            tokenManager?.saveAllTokens(
                                accessToken = tokens["access_token"]?.toString(),
                                refreshToken = tokens["refresh_token"]?.toString(),
                                deviceToken = tokens["device_token"]?.toString(),
                                sessionToken = tokens["session_token"]?.toString(),
                                fingerprintToken = tokens["fingerprint_token"]?.toString()
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            activateSuperAdmin(name, email, mobile, pin, onComplete)
        }
    }

    fun activateSuperAdmin(
        name: String,
        email: String,
        mobile: String,
        pin: String,
        onComplete: () -> Unit
    ) {
        viewModelScope.launch {
            if (pin.length == 6 && pin.all { it.isDigit() }) {
                val hashedPin = com.safa.account.utils.HashUtils.hashPin(pin)
                val superAdmin = OperatorAccount(
                    username = name.ifBlank { "SuperAdmin" },
                    role = "SuperAdmin",
                    pin = hashedPin,
                    mobile = mobile.trim(),
                    email = email.trim(),
                    isActivated = true,
                    isActive = true,
                    canViewCustomers = true,
                    canAddCustomers = true,
                    canEditCustomers = true,
                    canDeleteCustomers = true,
                    canViewSuppliers = true,
                    canAddSuppliers = true,
                    canEditSuppliers = true,
                    canDeleteSuppliers = true,
                    canViewTransactions = true,
                    canAddTransactions = true,
                    canEditTransactions = true,
                    canDeleteTransactions = true,
                    canManageWallet = true,
                    canManageExpenses = true,
                    canViewReports = true
                )
                val unactivated = operators.value.find { !it.isActivated || it.role == "SuperAdmin" }
                val opId = if (unactivated != null) {
                    val updated = unactivated.copy(
                        username = name.ifBlank { "SuperAdmin" },
                        role = "SuperAdmin",
                        pin = hashedPin,
                        mobile = mobile.trim(),
                        email = email.trim(),
                        isActivated = true,
                        isActive = true,
                        canViewCustomers = true,
                        canAddCustomers = true,
                        canEditCustomers = true,
                        canDeleteCustomers = true,
                        canViewSuppliers = true,
                        canAddSuppliers = true,
                        canEditSuppliers = true,
                        canDeleteSuppliers = true,
                        canViewTransactions = true,
                        canAddTransactions = true,
                        canEditTransactions = true,
                        canDeleteTransactions = true,
                        canManageWallet = true,
                        canManageExpenses = true,
                        canViewReports = true
                    )
                    repository.updateOperator(updated)
                    updated.id
                } else {
                    repository.insertOperator(superAdmin).toInt()
                }
                val activeOp = superAdmin.copy(id = opId)
                _currentOperator.value = activeOp
                _selectedLoginOperator.value = activeOp
                _pinError.value = null
                navigateTo(AppScreen.DASHBOARD)
                onComplete()
            }
        }
    }

    fun fetchOperatorsFromServer() {
        viewModelScope.launch {
            try {
                val api = syncManager?.getApiService() ?: return@launch
                val res = api.getOperators()
                if (res.isSuccessful && res.body() != null) {
                    val body = res.body()!!
                    @Suppress("UNCHECKED_CAST")
                    val rawOps = body["operators"] as? List<Map<String, Any?>> ?: emptyList()
                    val currentOps = operators.value
                    val validMobiles = rawOps.mapNotNull { it["mobile"]?.toString()?.trim() }.filter { it.isNotBlank() }
                    val validIds = rawOps.mapNotNull { (it["id"] as? Number)?.toInt() }.filter { it > 0 }

                    rawOps.forEach { opMap ->
                        val serverId = (opMap["id"] as? Number)?.toInt() ?: 0
                        val mobile = opMap["mobile"]?.toString()?.trim() ?: ""
                        val name = opMap["name"]?.toString() ?: "Operator"
                        val email = opMap["email"]?.toString() ?: ""
                        val roleStr = opMap["role"]?.toString() ?: "Staff"
                        val isSuperAdmin = roleStr.equals("manager", true) || roleStr.equals("superadmin", true) || roleStr.equals("owner", true)
                        val isActivated = (opMap["is_activated"] as? Boolean) ?: true
                        @Suppress("UNCHECKED_CAST")
                        val permsMap = opMap["permissions"] as? Map<String, Any?> ?: emptyMap()

                        fun getPerm(key: String): Boolean {
                            val v = permsMap[key]
                            return if (v is Boolean) v else true
                        }

                        val existing = currentOps.find {
                            (serverId > 0 && it.id == serverId) ||
                            (mobile.isNotBlank() && it.mobile.trim() == mobile) ||
                            (isSuperAdmin && (it.role == "SuperAdmin" || it.role == "Owner" || it.role == "Admin"))
                        }
                        val hashedPin = existing?.pin ?: com.safa.account.utils.HashUtils.hashPin("1234")
                        val op = OperatorAccount(
                            id = existing?.id ?: 0,
                            username = name,
                            role = if (isSuperAdmin) "SuperAdmin" else "Staff",
                            pin = hashedPin,
                            mobile = mobile,
                            email = email,
                            isActivated = isActivated,
                            isActive = true,
                            canViewCustomers = getPerm("can_view_customers"),
                            canAddCustomers = getPerm("can_add_customers"),
                            canEditCustomers = getPerm("can_edit_customers"),
                            canDeleteCustomers = getPerm("can_delete_customers"),
                            canViewSuppliers = getPerm("can_view_suppliers"),
                            canAddSuppliers = getPerm("can_add_suppliers"),
                            canEditSuppliers = getPerm("can_edit_suppliers"),
                            canDeleteSuppliers = getPerm("can_delete_suppliers"),
                            canViewTransactions = getPerm("can_view_transactions"),
                            canAddTransactions = getPerm("can_add_transactions"),
                            canEditTransactions = getPerm("can_edit_transactions"),
                            canDeleteTransactions = getPerm("can_delete_transactions"),
                            canManageWallet = getPerm("can_manage_wallet"),
                            canManageExpenses = getPerm("can_manage_expenses"),
                            canViewReports = getPerm("can_view_reports")
                        )
                        if (existing != null) {
                            repository.updateOperator(op)
                            if (_currentOperator.value?.id == existing.id || (_currentOperator.value?.role == "SuperAdmin" && isSuperAdmin)) {
                                _currentOperator.value = op
                                if (mobile.isNotBlank()) tokenManager?.saveLastMobile(mobile)
                            }
                        } else {
                            repository.insertOperator(op)
                        }
                    }

                    // Purge old orphan local operators or old duplicates
                    if (validMobiles.isNotEmpty() || validIds.isNotEmpty()) {
                        val updatedOps = operators.value
                        val superAdmins = updatedOps.filter { it.role == "SuperAdmin" }
                        if (superAdmins.size > 1) {
                            val mainSuperAdmin = superAdmins.find { it.id == _currentOperator.value?.id } ?: superAdmins.last()
                            superAdmins.forEach { sa ->
                                if (sa.id != mainSuperAdmin.id) {
                                    repository.deleteOperator(sa)
                                }
                            }
                        }

                        currentOps.forEach { localOp ->
                            val isServerMobileMatch = validMobiles.contains(localOp.mobile.trim())
                            val isServerIdMatch = validIds.contains(localOp.id)
                            val isCurrentSuperAdmin = localOp.role == "SuperAdmin" && rawOps.any {
                                val r = it["role"]?.toString() ?: ""
                                r.equals("manager", true) || r.equals("superadmin", true) || r.equals("owner", true)
                            }

                            if (!isServerMobileMatch && !isServerIdMatch && !isCurrentSuperAdmin) {
                                repository.deleteOperator(localOp)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
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
        onComplete: () -> Unit
    ) {
        viewModelScope.launch {
            try {
                val api = syncManager?.getApiService()
                if (api != null) {
                    val apiRole = if (role.equals("Owner", true) || role.equals("Admin", true) || role.equals("SuperAdmin", true)) "manager" else "staff"
                    val req = com.safa.account.data.api.dto.OperatorApiRequest(
                        name = name.trim(),
                        mobile = mobile.trim(),
                        email = email.ifBlank { null },
                        role = apiRole,
                        pin = pin,
                        isActivated = true,
                        permissions = permissionsMap
                    )
                    api.createOperator(req)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            val hashedPin = com.safa.account.utils.HashUtils.hashPin(pin)
            val op = OperatorAccount(
                username = name,
                mobile = mobile.trim(),
                email = email.trim(),
                role = role,
                pin = hashedPin,
                isActivated = true,
                isActive = true,
                canViewCustomers = permissionsMap["can_view_customers"] ?: true,
                canAddCustomers = permissionsMap["can_add_customers"] ?: true,
                canEditCustomers = permissionsMap["can_edit_customers"] ?: true,
                canDeleteCustomers = permissionsMap["can_delete_customers"] ?: true,
                canViewSuppliers = permissionsMap["can_view_suppliers"] ?: true,
                canAddSuppliers = permissionsMap["can_add_suppliers"] ?: true,
                canEditSuppliers = permissionsMap["can_edit_suppliers"] ?: true,
                canDeleteSuppliers = permissionsMap["can_delete_suppliers"] ?: true,
                canViewTransactions = permissionsMap["can_view_transactions"] ?: true,
                canAddTransactions = permissionsMap["can_add_transactions"] ?: true,
                canEditTransactions = permissionsMap["can_edit_transactions"] ?: true,
                canDeleteTransactions = permissionsMap["can_delete_transactions"] ?: true,
                canManageWallet = permissionsMap["can_manage_wallet"] ?: true,
                canManageExpenses = permissionsMap["can_manage_expenses"] ?: true,
                canViewReports = permissionsMap["can_view_reports"] ?: true
            )
            repository.insertOperator(op)
            onComplete()
        }
    }

    fun updateOperatorOnServer(
        op: OperatorAccount,
        newPin: String? = null,
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val api = syncManager?.getApiService()
                if (api != null && op.id > 0) {
                    val apiRole = if (op.role.equals("Owner", true) || op.role.equals("Admin", true) || op.role.equals("SuperAdmin", true)) "manager" else "staff"
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
                    api.updateOperator(op.id, req)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            repository.updateOperator(op)
            onComplete()
        }
    }

    fun deleteOperatorOnServer(op: OperatorAccount, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                val api = syncManager?.getApiService()
                if (api != null && op.id > 0) {
                    api.deleteOperator(op.id)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            repository.deleteOperator(op)
            onComplete()
        }
    }

    fun logout() {
        _currentOperator.value = null
        _selectedLoginOperator.value = null
        _pinBuffer.value = ""
        _pinError.value = null
        navigateTo(AppScreen.LOCK_SCREEN)
    }

    fun switchOperatorDirectly(operator: com.safa.account.data.model.OperatorAccount) {
        if (operator.isActive) {
            _currentOperator.value = operator
            _selectedLoginOperator.value = operator
            _pinError.value = null
            navigateTo(AppScreen.DASHBOARD)
        }
    }


    // --- Business Functions ---

    // 1. Save Customer
    fun registerCustomer(name: String, phone: String, address: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            if (name.isNotBlank() && phone.isNotBlank()) {
                repository.insertCustomer(
                    Customer(name = name, phone = phone, address = address)
                )
                onComplete()
                // Auto Sync with backend
                syncManager?.syncAll()
            }
        }
    }

    suspend fun updateCustomerProfile(customer: Customer) {
        repository.updateCustomer(customer)
        syncManager?.syncAll()
    }

    fun deleteCustomer(id: Int) {
        viewModelScope.launch {
            repository.softDeleteCustomerById(id)
            syncManager?.syncAll()
        }
    }

    // 2. Save Supplier
    fun registerSupplier(name: String, phone: String, address: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            if (name.isNotBlank()) {
                repository.insertSupplier(
                    Supplier(name = name, phone = phone, address = address)
                )
                onComplete()
                // Auto Sync with backend
                syncManager?.syncAll()
            }
        }
    }

    fun deleteSupplier(id: Int) {
        viewModelScope.launch {
            repository.softDeleteSupplierById(id)
            syncManager?.syncAll()
        }
    }

    // 3. Purchase / Deposit SAR to Supplier to Acquire BDT
    fun depositToSupplier(
        supplierId: Int,
        amountSar: Double,
        rate: Double,
        paidBdt: Double = 0.0,
        notes: String,
        transactionType: String = "SAR_GIVEN",
        ledgerId: Int = 0,
        timestamp: Long = System.currentTimeMillis(),
        onComplete: () -> Unit
    ) {
        viewModelScope.launch {
            if (supplierId > 0 && amountSar > 0 && rate > 0) {
                val amtBdt = java.math.BigDecimal(amountSar.toString()).multiply(java.math.BigDecimal(rate.toString())).toDouble()
                val depositId = repository.insertSupplierDeposit(
                    SupplierDeposit(
                        supplierId = supplierId,
                        amountSar = amountSar,
                        rate = rate,
                        amountBdt = amtBdt,
                        paidBdt = paidBdt,
                        transactionType = transactionType,
                        notes = notes,
                        timestamp = timestamp
                    )
                )
                
                // If purchasing BDT and ledger is selected, add as a Wallet Batch
                if ((transactionType == "SAR_GIVEN" || transactionType == "SAR_DEPOSIT") && ledgerId > 0) {
                    val supplierName = suppliers.value.find { it.id == supplierId }?.name ?: "Supplier"
                    repository.insertWalletBatch(
                        WalletBatch(
                            ledgerId = ledgerId,
                            rate = rate,
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
                val newAmountBdt = java.math.BigDecimal(deposit.amountSar.toString()).multiply(java.math.BigDecimal(deposit.rate.toString())).toDouble()
                val diff = java.math.BigDecimal(newAmountBdt.toString()).subtract(java.math.BigDecimal(match.initialBdt.toString())).toDouble()
                val updatedRemaining = java.math.BigDecimal(match.remainingBdt.toString()).add(java.math.BigDecimal(diff.toString())).toDouble().coerceAtLeast(0.0)
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

    fun addMoneyToWallet(ledgerId: Int, amountBdt: Double, rate: Double, notes: String, onComplete: () -> Unit = {}) {
        if (ledgerId > 0 && amountBdt > 0 && rate > 0) {
            viewModelScope.launch {
                repository.insertWalletBatch(
                    WalletBatch(
                        ledgerId = ledgerId,
                        rate = rate,
                        initialBdt = amountBdt,
                        remainingBdt = amountBdt,
                        notes = if (notes.isNotBlank()) notes else "Manual Capital Deposit"
                    )
                )
                onComplete()
                syncManager?.syncAll()
            }
        }
    }

    fun deductMoneyFromWalletLedger(ledgerId: Int, amountBdtToDeduct: Double, onComplete: () -> Unit = {}) {
        if (ledgerId > 0 && amountBdtToDeduct > 0) {
            viewModelScope.launch {
                val batches = repository.allWalletBatches.firstOrNull()
                    ?.filter { it.ledgerId == ledgerId && it.remainingBdt > 0.01 }
                    ?.sortedBy { it.timestamp } ?: emptyList()  // Oldest first
                var remainingToDeduct = java.math.BigDecimal(amountBdtToDeduct.toString())
                for (b in batches) {
                    if (remainingToDeduct.compareTo(java.math.BigDecimal.ZERO) <= 0) break
                    val bRemaining = java.math.BigDecimal(b.remainingBdt.toString())
                    val deductFromThisBatch = if (bRemaining < remainingToDeduct) bRemaining else remainingToDeduct
                    val newRemaining = bRemaining.subtract(deductFromThisBatch).toDouble()
                    repository.updateWalletBatch(b.copy(remainingBdt = newRemaining))
                    remainingToDeduct = remainingToDeduct.subtract(deductFromThisBatch)
                }
                onComplete()
                syncManager?.syncAll()
            }
        }
    }

    fun deductMoneyFromWalletBatch(batchId: Int, amountBdtToDeduct: Double, onComplete: () -> Unit = {}) {
        if (batchId > 0 && amountBdtToDeduct > 0) {
            viewModelScope.launch {
                val batch = repository.getWalletBatchById(batchId)
                if (batch != null) {
                    val rem = java.math.BigDecimal(batch.remainingBdt.toString()).subtract(java.math.BigDecimal(amountBdtToDeduct.toString())).toDouble()
                    val updatedRemaining = rem.coerceAtLeast(0.0)
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
        amountSar: Double,
        customerRate: Double,
        receiverName: String,
        receiverPhone: String,
        receiverAccountType: String,
        receiverAccountNo: String,
        notes: String,
        sarCollected: Double? = null,
        bdtDisbursed: Double? = null,
        status: String = "Pending",
        timestamp: Long? = null,
        onComplete: () -> Unit
    ) {
        viewModelScope.launch {
            val batch = if (walletBatchId > 0) repository.getWalletBatchById(walletBatchId) else null
            val resolvedSupplierRate = batch?.rate ?: customerRate
            val resolvedSupplierId = batch?.supplierId ?: 0

            val operatorId = _currentOperator.value?.id ?: 1
            val amountBdt = java.math.BigDecimal(amountSar.toString()).multiply(java.math.BigDecimal(customerRate.toString())).toDouble()
            val actualSarCollected = sarCollected ?: amountSar
            val actualBdtDisbursed = bdtDisbursed ?: amountBdt
            val actualTimestamp = timestamp ?: System.currentTimeMillis()
            
            repository.insertTransaction(
                RemittanceTransaction(
                    customerId = customerId,
                    supplierId = resolvedSupplierId,
                    amountSar = amountSar,
                    customerRate = customerRate,
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
                    walletBatchId = walletBatchId
                )
            )

            // Deduct BDT count from the corresponding batch balance
            if (batch != null) {
                val rem = java.math.BigDecimal(batch.remainingBdt.toString()).subtract(java.math.BigDecimal(amountBdt.toString())).toDouble()
                val newRemaining = rem.coerceAtLeast(0.0)
                repository.updateWalletBatch(batch.copy(remainingBdt = newRemaining))
            }
            onComplete()

            // Background Auto Sync with Server (if internet connected)
            syncManager?.syncAll()
        }
    }

    fun updateTransactionStatus(transaction: RemittanceTransaction, newStatus: String) {
        viewModelScope.launch {
            if (newStatus == "Cancelled" && transaction.status != "Cancelled") {
                // Refund BDT back to the Wallet Batch
                if (transaction.walletBatchId > 0) {
                    val batch = repository.getWalletBatchById(transaction.walletBatchId)
                    if (batch != null) {
                        val newRemaining = java.math.BigDecimal(batch.remainingBdt.toString()).add(java.math.BigDecimal(transaction.amountBdt.toString())).toDouble()
                        repository.updateWalletBatch(batch.copy(remainingBdt = newRemaining))
                    }
                }
            } else if (newStatus != "Cancelled" && transaction.status == "Cancelled") {
                // Re-deduct BDT from the Wallet Batch
                if (transaction.walletBatchId > 0) {
                    val batch = repository.getWalletBatchById(transaction.walletBatchId)
                    if (batch != null) {
                        val rem = java.math.BigDecimal(batch.remainingBdt.toString()).subtract(java.math.BigDecimal(transaction.amountBdt.toString())).toDouble()
                        repository.updateWalletBatch(batch.copy(remainingBdt = rem.coerceAtLeast(0.0)))
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
                        val newRemaining = java.math.BigDecimal(batch.remainingBdt.toString()).add(java.math.BigDecimal(tx.amountBdt.toString())).toDouble()
                        repository.updateWalletBatch(batch.copy(remainingBdt = newRemaining))
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
        amount: Double,
        currency: String,
        isExpense: Boolean,
        category: String,
        onComplete: () -> Unit
    ) {
        viewModelScope.launch {
            if (title.isNotBlank() && amount > 0) {
                repository.insertExpenseIncome(
                    ExpenseIncome(
                        title = title,
                        amount = amount,
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
                e.printStackTrace()
            }
        }
    }

    // 6. Update Daily Standard Market Rates
    fun publishDailyRates(customerRate: Double, supplierRate: Double, onComplete: () -> Unit) {
        viewModelScope.launch {
            val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            val updated = DailyRate(
                date = dateStr,
                customerRate = customerRate,
                supplierRate = supplierRate
            )
            repository.insertDailyRate(updated)
            _currentRates.value = updated
            onComplete()
            syncManager?.syncAll()
        }
    }

    // 7. Add Staff Operator
    fun addOperator(username: String, pin: String, role: String, permissions: String = "edit,create,delete,update", onComplete: () -> Unit) {
        viewModelScope.launch {
            if (username.isNotBlank() && pin.length >= 4) {
                repository.insertOperator(
                    OperatorAccount(
                        username = username,
                        pin = com.safa.account.utils.HashUtils.hashPin(pin),
                        role = role,
                        isActive = true
                    )
                )
                onComplete()
                syncManager?.syncAll()
            }
        }
    }

    fun updateOperator(operator: OperatorAccount, onComplete: () -> Unit = {}) {
        _currentOperator.value = operator
        viewModelScope.launch {
            repository.updateOperator(operator)
            onComplete()
            syncManager?.syncAll()
        }
    }

    // Change PIN of currently active operator
    fun updateOperatorPin(newPin: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            val op = _currentOperator.value
            if (op != null && newPin.length == 6 && newPin.all { it.isDigit() }) {
                val hashedPin = com.safa.account.utils.HashUtils.hashPin(newPin)
                val updatedOp = op.copy(pin = hashedPin)
                repository.updateOperator(updatedOp)
                _currentOperator.value = updatedOp
                onComplete()
                syncManager?.syncAll()
            }
        }
    }

    // Delete registration of a staff or owner account
    fun deleteOperatorAccount(operator: OperatorAccount, onComplete: () -> Unit) {
        viewModelScope.launch {
            // Cannot delete current logged-in user
            if (operator.id != _currentOperator.value?.id) {
                repository.deleteOperator(operator)
                onComplete()
                syncManager?.syncAll()
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
