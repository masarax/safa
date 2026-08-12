package com.safa.account.ui.components

/**
 * Compact UI vocabulary for SAFA. Keep visible labels to one or two words
 * whenever the meaning remains clear. This is intentionally language-aware
 * and is also used by shared dialogs so destructive actions never become
 * paragraph-sized buttons.
 */
object UiCopy {
    private val en = mapOf(
        "Customer List" to "Customers",
        "Supplier List" to "Suppliers",
        "Add Customer" to "New Customer",
        "Add New Customer" to "New Customer",
        "Add Supplier" to "New Supplier",
        "Add New Supplier" to "New Supplier",
        "Customer Management" to "Customers",
        "Supplier Management" to "Suppliers",
        "Customer Details" to "Customer",
        "Supplier Details" to "Supplier",
        "Transaction List" to "Transactions",
        "New Transaction" to "Transaction",
        "Add Transaction" to "Transaction",
        "Expense List" to "Expenses",
        "Add Expense" to "New Expense",
        "Expense & Income" to "Income",
        "Wallet List" to "Wallets",
        "Daily Exchange Rates" to "Rates",
        "Account Sharing Settings" to "Sharing",
        "User Management" to "Users",
        "Application Settings" to "Settings",
        "Manage Expenses" to "Expenses",
        "View Reports" to "Reports",
        "Select Customer" to "Customer",
        "Select Supplier" to "Supplier",
        "Save Customer" to "Save",
        "Save Supplier" to "Save",
        "Complete Activation" to "Activate",
        "Change Role" to "Role",
        "Permission Required" to "Permission",
        "Connection Failed" to "Offline",
        "Sync Completed" to "Synced",
        "Sync Error" to "Sync Error",
        "Wrong mobile or PIN" to "Invalid PIN",
        "Invalid mobile or PIN" to "Invalid PIN",
        "Delete Customer" to "Delete",
        "Delete Supplier" to "Delete",
        "Delete Transaction" to "Delete",
        "Delete Expense" to "Delete",
        "Delete Wallet" to "Delete",
        "Confirm Delete" to "Delete",
        "Cancel" to "Cancel",
        "Confirm" to "Confirm",
        "Save" to "Save",
        "Update" to "Update",
        "Close" to "Close",
        "Retry" to "Retry"
    )

    private val bn = mapOf(
        "Customer List" to "কাস্টমার",
        "Supplier List" to "সাপ্লায়ার",
        "Add Customer" to "নতুন কাস্টমার",
        "Add New Customer" to "নতুন কাস্টমার",
        "Add Supplier" to "নতুন সাপ্লায়ার",
        "Add New Supplier" to "নতুন সাপ্লায়ার",
        "Customer Management" to "কাস্টমার",
        "Supplier Management" to "সাপ্লায়ার",
        "Customer Details" to "কাস্টমার",
        "Supplier Details" to "সাপ্লায়ার",
        "Transaction List" to "লেনদেন",
        "New Transaction" to "নতুন লেনদেন",
        "Add Transaction" to "নতুন লেনদেন",
        "Expense List" to "খরচ",
        "Add Expense" to "নতুন খরচ",
        "Expense & Income" to "আয়",
        "Wallet List" to "ওয়ালেট",
        "Daily Exchange Rates" to "রেট",
        "Account Sharing Settings" to "শেয়ারিং",
        "User Management" to "ইউজার",
        "Application Settings" to "সেটিংস",
        "Manage Expenses" to "খরচ",
        "View Reports" to "রিপোর্ট",
        "Select Customer" to "কাস্টমার",
        "Select Supplier" to "সাপ্লায়ার",
        "Save Customer" to "সংরক্ষণ",
        "Save Supplier" to "সংরক্ষণ",
        "Complete Activation" to "অ্যাক্টিভেট",
        "Change Role" to "রোল",
        "Permission Required" to "অনুমতি",
        "Connection Failed" to "অফলাইন",
        "Sync Completed" to "সিঙ্ক",
        "Sync Error" to "সিঙ্ক সমস্যা",
        "Wrong mobile or PIN" to "পিন ভুল",
        "Invalid mobile or PIN" to "পিন ভুল",
        "Delete Customer" to "মুছুন",
        "Delete Supplier" to "মুছুন",
        "Delete Transaction" to "মুছুন",
        "Delete Expense" to "মুছুন",
        "Delete Wallet" to "মুছুন",
        "Confirm Delete" to "মুছুন",
        "Cancel" to "বাতিল",
        "Confirm" to "নিশ্চিত",
        "Save" to "সংরক্ষণ",
        "Update" to "আপডেট",
        "Close" to "বন্ধ",
        "Retry" to "আবার"
    )

    fun compact(value: String, language: String? = null): String {
        val normalized = value.trim()
        if (normalized.isEmpty()) return normalized
        val table = if (language.equals("BN", ignoreCase = true)) bn else en
        return table[normalized] ?: normalized
    }
}
