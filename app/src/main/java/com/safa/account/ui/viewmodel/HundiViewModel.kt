package com.safa.account.ui.viewmodel

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

class HundiViewModel(
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

    private val _customAppLogo = MutableStateFlow(tokenManager?.getCustomAppLogo() ?: "👑")
    val customAppLogo: StateFlow<String> = _customAppLogo.asStateFlow()

    private val _customAppLogoUri = MutableStateFlow<String?>(tokenManager?.getCustomAppLogoUri())
    val customAppLogoUri: StateFlow<String?> = _customAppLogoUri.asStateFlow()

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

    // Dark Mode Toggle (Default: false)
    private val _isDarkMode = MutableStateFlow(false)
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    fun toggleDarkMode() {
        _isDarkMode.value = !_isDarkMode.value
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
            repository.updateCustomer(customer)
            onComplete()
        }
    }

    fun updateSupplier(supplier: Supplier, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.updateSupplier(supplier)
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
        seedDefaultsIfEmpty()
    }

    private fun seedDefaultsIfEmpty() {
        viewModelScope.launch {
            try {
                val ownerExist = repository.getOperatorByUsername("Owner")
                if (ownerExist == null) {
                    repository.insertOperator(
                        OperatorAccount(
                            username = "Owner",
                            role = "Owner",
                            pin = com.safa.account.utils.HashUtils.hashPin("1234"),
                            isActive = true
                        )
                    )
                }
                val staffExist = repository.getOperatorByUsername("Operator Cashier")
                if (staffExist == null) {
                    repository.insertOperator(
                        OperatorAccount(
                            username = "Operator Cashier",
                            role = "Staff",
                            pin = com.safa.account.utils.HashUtils.hashPin("2580"),
                            isActive = true
                        )
                    )
                }
                
                // No default wallets to seed per user request
            } catch (e: Exception) {
                e.printStackTrace()
            }
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
        "enter_pin" to "৪-ডিজিটের সিকিউরিটি পিন দিন",
        "pin_incorrect" to "ভুল পিন নম্বর! আবার চেষ্টা করুন।",
        "logout" to "লগ আউট",
        "change_role" to "রোল পরিবর্তন করুন",
        "role_owner" to "মালিক",
        "role_staff" to "স্টাফ",
        "operator_blocked" to "অ্যাকাউন্টটি সাময়িকভাবে স্থগিত আছে।",
        
        "total_sar_received" to "মোট সংগৃহীত রিয়াল",
        "total_bdt_delivered" to "মোট প্রদেয় টাকা (বিতরণকৃত)",
        "total_bdt_pending" to "মোট প্রদেয় টাকা (অপেক্ষমান)",
        "estimated_profit" to "আজকের মোট মুনাফা",
        "net_profit_bdt" to "মোট মুনাফা (স্থানীয়)",
        "net_profit_sar" to "মোট মুনাফা (ফরেন)",
        "daily_operating_rates" to "আজকের বাজার এক্সচেঞ্জ রেট",
        "customer_sale_rate" to "কাস্টমার বিক্রি রেট",
        "supplier_buy_rate" to "সাপ্লায়ার ক্রয় রেট",
        
        "customer_mgmt" to "গ্রাহক ব্যবস্থাপনা",
        "add_customer" to "নতুন কাস্টমার যুক্ত করুন",
        "customer_name" to "কাস্টমারের নাম",
        "phone_number" to "মোবাইল নম্বর",
        "address" to "ঠিকানা/কর্মস্থল",
        "save_customer" to "কাস্টমার সংরক্ষণ করুন",
        "total_customers" to "মোট রেজিস্টার্ড কাস্টমার",
        
        "supplier_mgmt" to "সাপ্লায়ার",
        "add_supplier" to "নতুন সাপ্লায়ার যুক্ত করুন",
        "supplier_name" to "সাপ্লায়ার নাম (Forex Group / Person)",
        "save_supplier" to "সাপ্লায়ার সংরক্ষণ করুন",
        "buy_bdt_pool" to "সাপ্লায়ার থেকে ফান্ড কেনা",
        "amount_sar" to "ফরেন কারেন্সি পরিমাণ",
        "rate_applied" to "রেট (যেমন ৩২.৫০)",
        "purchase_success" to "টাকার ফান্ড সফলভাবে কেনা হয়েছে",
        "total_deposited_sar" to "মোট রিয়াল জমা",
        "acquired_bdt" to BdtSymbol() + " মোট সংগৃহীত ফান্ড",
        "pool_balance" to "সাপ্লায়ারের কাছে টাকা ব্যালেন্স",
        
        "new_remittance" to "নতুন লেনদেন",
        "select_customer" to "কাস্টমার খুঁজুন",
        "select_supplier" to "বিশ্বরস্ত সাপ্লায়ার ফান্ড",
        "saudi_amount" to "ফরেন কারেন্সি জমা পরিমাণ",
        "customer_assigned_rate" to "কাস্টমার রেট (যেমন ৩২.১০)",
        "supplier_rate_tx" to "সাপ্লায়ার রেট (ব্যবসায়িক ক্রয় রেট)",
        "receiver_bdt_amount" to "প্রাপক পাবে (টাকা)",
        "receiver_name" to "প্রাপকের নাম (যেমন আব্দুল হালিম)",
        "receiver_phone" to "প্রাপকের মোবাইল",
        "payment_method" to "টাকা পরিশোধের ক্ষেত্র",
        "receiver_account_no" to "হিসাব নম্বর (bKash/Nagad/Bank)",
        "notes" to "অতিরিক্ত মন্তব্য/ঠিকানা",
        "status" to "লেনদেনের অবস্থা",
        "status_pending" to "অপেক্ষমান (Pending)",
        "status_delivered" to "পৌঁছে গেছে (Delivered)",
        "status_cancelled" to "বাতিল করা হয়েছে (Cancelled)",
        "save_transaction" to "লেনদেন সম্পন্ন করুন",
        
        "expenses_overhead" to "দৈনিক অফিস চালনা ও অতিরিক্ত ব্যয়",
        "add_expense_income" to "আয়/ব্যয় এন্ট্রি করুন",
        "title" to "বিবরণ (যেমন চায়ের বিল/অফিস ভাড়া)",
        "amount" to "টাকা পরিমাণ",
        "is_expense" to "ব্যয় নাকি আয়?",
        "expense" to "অফিস ব্যয়",
        "income" to "অফিস বাড়তি আয়",
        "category" to "ক্যাটাগরি",
        "save_record" to "সংরক্ষণ করুন",
        
        "update_daily_rates" to "প্রতিদিনের এক্সচেঞ্জ রেট আপডেট করুন",
        "rate_saved" to "এক্সচেঞ্জ রেট সফলভাবে সংরক্ষিত!",
        "operator_list" to "ইউজার ও স্টাফ লিস্ট",
        "create_new_operator" to "নতুন স্টাফ/ইউজার যুক্ত করুন",
        "pinCode" to "৪-ডিজিট সিকিউরিটি পিন",
        "role" to "রোল (Owner/Staff)",
        "unsettled_supp" to "সাপ্লায়ার পাওনা/দেনা পরিমাণ"
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
        "enter_pin" to "Enter 4-Digit Security PIN",
        "pin_incorrect" to "Incorrect PIN! Please try again.",
        "logout" to "Log Out",
        "change_role" to "Change Role",
        "role_owner" to "Owner / Admin",
        "role_staff" to "Staff / Operator",
        "operator_blocked" to "Account is currently suspended.",
        
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
        "pinCode" to "4-Digit PIN Access",
        "role" to "User Authorization Role",
        "unsettled_supp" to "Net Supplier Credit Obligations"
    )

    fun t(key: String): String {
        val strings = if (_currentLanguage.value == "BN") bnMap else enMap
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

    fun appendPinDigit(digit: Char) {
        if (_pinBuffer.value.length < 4) {
            _pinBuffer.value = _pinBuffer.value + digit
            if (_pinBuffer.value.length == 4) {
                verifyPin()
            }
        }
    }

    fun deletePinDigit() {
        if (_pinBuffer.value.isNotEmpty()) {
            _pinBuffer.value = _pinBuffer.value.dropLast(1)
        }
    }

    private fun verifyPin() {
        val operator = _selectedLoginOperator.value
        if (operator != null) {
            if (com.safa.account.utils.HashUtils.verifyPin(_pinBuffer.value, operator.pin)) {
                if (operator.isActive) {
                    _currentOperator.value = operator
                    _pinError.value = null
                    navigateTo(AppScreen.DASHBOARD)
                } else {
                    _pinError.value = t("operator_blocked")
                    _pinBuffer.value = ""
                }
            } else {
                _pinError.value = t("pin_incorrect")
                _pinBuffer.value = ""
            }
        }
    }

    fun loginWithBiometric(operator: OperatorAccount) {
        if (operator.isActive) {
            _currentOperator.value = operator
            _pinError.value = null
            _pinBuffer.value = ""
            navigateTo(AppScreen.DASHBOARD)
        } else {
            _pinError.value = t("operator_blocked")
            _pinBuffer.value = ""
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
            repository.deleteCustomerById(id)
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
            repository.deleteSupplierById(id)
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
            }
        }
    }

    fun deleteSupplierDeposit(id: Int) {
        viewModelScope.launch {
            repository.deleteSupplierDepositById(id)
            repository.deleteWalletBatchBySupplierDepositId(id)
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
        }
    }

    // --- Wallet Manual Operations ---
    fun registerWalletLedger(name: String, onComplete: () -> Unit = {}) {
        if (name.isNotBlank()) {
            viewModelScope.launch {
                repository.insertWalletLedger(WalletLedger(name = name))
                onComplete()
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
                }
            }
        }
    }

    fun deleteWalletLedger(id: Int, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.deleteWalletLedgerById(id)
            // also delete all batches belonging to this ledger
            val batches = repository.allWalletBatches.firstOrNull()?.filter { it.ledgerId == id } ?: emptyList()
            batches.forEach {
                repository.deleteWalletBatchById(it.id)
            }
            onComplete()
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
                }
            }
        }
    }

    // 4. Create Remittance Transaction (Hundi Entry)
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
                repository.deleteTransactionById(id)
                onComplete()
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
            }
        }
    }

    fun removeExpenseIncome(id: Int) {
        viewModelScope.launch {
            repository.deleteExpenseIncomeById(id)
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
            }
        }
    }

    fun updateOperator(operator: OperatorAccount, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.updateOperator(operator)
            onComplete()
        }
    }

    // Change PIN of currently active operator
    fun updateOperatorPin(newPin: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            val op = _currentOperator.value
            if (op != null && newPin.length == 4 && newPin.all { it.isDigit() }) {
                val hashedPin = com.safa.account.utils.HashUtils.hashPin(newPin)
                val updatedOp = op.copy(pin = hashedPin)
                repository.updateOperator(updatedOp)
                _currentOperator.value = updatedOp
                onComplete()
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
            }
        }
    }

    // Prepopulate extra demo transactions for sandbox visual depth on click
    fun injectDemoSandboxData() {
        viewModelScope.launch {
            // Pick an active customer and supplier or fallback
            val custId = customers.value.firstOrNull()?.id ?: 1
            val suppId = suppliers.value.firstOrNull()?.id ?: 1
            val opId = _currentOperator.value?.id ?: 1
            
            val randomList = listOf(
                Pair(1000.0, "Bashir Alam"),
                Pair(3400.0, "Mominul Haque"),
                Pair(800.0, "Salma Begum"),
                Pair(5000.0, "Zakir Hossain")
            )
            
            for (idx in randomList.indices) {
                val amt = randomList[idx].first
                val name = randomList[idx].second
                repository.insertTransaction(
                    RemittanceTransaction(
                        customerId = custId,
                        supplierId = suppId,
                        amountSar = amt,
                        customerRate = 32.1 - (idx * 0.05),
                        supplierRate = 32.6,
                        amountBdt = amt * (32.1 - (idx * 0.05)),
                        receiverName = name,
                        receiverPhone = "015112223${idx}4",
                        receiverAccountType = "bKash",
                        receiverAccountNo = "015112223${idx}4",
                        status = if (idx % 2 == 0) "Delivered" else "Pending",
                        operatorId = opId,
                        timestamp = System.currentTimeMillis() - (idx * 14400000)
                    )
                )
            }
        }
    }
}

class HundiViewModelFactory(
    private val repository: AppRepository,
    private val tokenManager: TokenManager? = null
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HundiViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return HundiViewModel(repository, tokenManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
