package com.safa.account.ui.components

/** Compact SAFA vocabulary for English and Bengali UI surfaces. */
object UiCopy {
    private val en = mapOf(
        "Customer List" to "Customers", "Customer Directory" to "Customers", "Customer Management" to "Customers",
        "Add Customer" to "New Customer", "Add New Customer" to "New Customer", "Add New Customer Profile" to "New Customer",
        "Customer Details" to "Customer", "Register Customer" to "Save", "Save Customer" to "Save",
        "Supplier List" to "Suppliers", "Supplier Management" to "Suppliers", "Supplier Details" to "Supplier",
        "Add Supplier" to "New Supplier", "Add New Supplier" to "New Supplier", "Add Local Supplier Forex" to "New Supplier",
        "Save Supplier" to "Save", "Save Supplier Detail" to "Save",
        "Transaction List" to "Transactions", "New Transaction" to "New Transaction", "Add Transaction" to "New Transaction",
        "Search/Select Customer" to "Customer", "Select Customer" to "Customer", "Select Active Local Supplier" to "Supplier", "Select Supplier" to "Supplier",
        "Commit & Save Transaction" to "Save", "Save Transaction" to "Save",
        "Expense List" to "Expenses", "Expenses & Inc." to "Expenses", "Expense & Income" to "Income",
        "Operating overhead & Miscellaneous" to "Expenses", "Log Operating Expense/Income" to "New Entry", "Operating Expense" to "Expense", "Operating Credit" to "Income",
        "Add Expense" to "New Expense", "Add Expense/Income" to "New Entry",
        "Wallet List" to "Wallets", "Wallet Management" to "Wallets", "Daily Exchange Rates" to "Rates", "Live Daily Exchange Rates" to "Rates",
        "Customer Exchange Rate" to "Customer Rate", "Supplier Exchange Rate" to "Supplier Rate", "Supplier Exchange Rate (Cost)" to "Supplier Rate",
        "Account Sharing Settings" to "Sharing", "User Management" to "Users", "Operator & Staff User Management" to "Users", "Authorized Operators & Personnel" to "Users",
        "Application Settings" to "Settings", "Manage Expenses" to "Expenses", "View Reports" to "Reports",
        "SuperAdmin 1-Time Activation" to "Activation", "Activate SuperAdmin" to "Activate", "Complete Activation" to "Activate",
        "Create Staff Account" to "New User", "Provision New Staff Account" to "New User", "Change Role" to "Role", "User Authorization Role" to "Role",
        "Permission Required" to "Permission", "You do not have permission to access this feature." to "Permission required",
        "Connection Failed" to "Offline", "Sync Completed" to "Synced", "Sync Error" to "Sync Error",
        "Wrong mobile or PIN" to "Invalid PIN", "Invalid mobile or PIN" to "Invalid PIN", "Incorrect PIN! Please try again." to "Invalid PIN",
        "No account found with this mobile number" to "Account not found",
        "Delete Customer" to "Delete", "Delete Supplier" to "Delete", "Delete Transaction" to "Delete", "Delete Expense" to "Delete", "Delete Wallet" to "Delete", "Confirm Delete" to "Delete",
        "Cancel" to "Cancel", "Confirm" to "Confirm", "Save" to "Save", "Update" to "Update", "Close" to "Close", "Retry" to "Retry",
        "Dashboard" to "Dashboard", "Settings" to "Settings", "Reports" to "Reports", "Customers" to "Customers", "Suppliers" to "Suppliers", "Transactions" to "Transactions", "Expenses" to "Expenses", "Wallet" to "Wallet",
        "Log In" to "Login", "Mobile Number" to "Mobile", "Full Name" to "Name", "Email Address" to "Email", "Enter 6-Digit Security PIN" to "PIN", "Saudi Mobile Number" to "Mobile",
        "Receiver Full Name" to "Receiver", "Receiver Mobile No" to "Receiver Mobile", "Delivery Status" to "Status", "Payout Channel" to "Payment", "Additional Instructions / Delivery Address" to "Notes", "Account No (bKash/Nagad/Bank)" to "Account",
        "View Customers" to "Customers", "Add Customers" to "New Customer", "Edit Customers" to "Edit Customer", "Delete Customers" to "Delete Customer",
        "View Suppliers" to "Suppliers", "Add Suppliers" to "New Supplier", "Edit Suppliers" to "Edit Supplier", "Delete Suppliers" to "Delete Supplier",
        "View Transactions" to "Transactions", "Add Transactions" to "New Transaction", "Edit Transactions" to "Edit Transaction", "Delete Transactions" to "Delete Transaction",
        "Manage Wallet" to "Wallet", "View Reports" to "Reports"
    )

    private val bn = mapOf(
        "কাস্টমার তালিকা" to "কাস্টমার", "Customer List" to "কাস্টমার", "Customer Directory" to "কাস্টমার", "Customer Management" to "কাস্টমার", "Customer Details" to "কাস্টমার",
        "কাস্টমার যুক্ত করুন" to "নতুন কাস্টমার", "নতুন কাস্টমার যোগ করুন" to "নতুন কাস্টমার", "Add Customer" to "নতুন কাস্টমার", "Add New Customer" to "নতুন কাস্টমার", "Add New Customer Profile" to "নতুন কাস্টমার", "Register Customer" to "সংরক্ষণ", "Save Customer" to "সংরক্ষণ",
        "সাপ্লায়ার তালিকা" to "সাপ্লায়ার", "Supplier List" to "সাপ্লায়ার", "Supplier Management" to "সাপ্লায়ার", "Supplier Details" to "সাপ্লায়ার",
        "সাপ্লায়ার যুক্ত করুন" to "নতুন সাপ্লায়ার", "নতুন সাপ্লায়ার যোগ করুন" to "নতুন সাপ্লায়ার", "Add Supplier" to "নতুন সাপ্লায়ার", "Add New Supplier" to "নতুন সাপ্লায়ার", "Add Local Supplier Forex" to "নতুন সাপ্লায়ার", "Save Supplier" to "সংরক্ষণ", "Save Supplier Detail" to "সংরক্ষণ",
        "লেনদেন তালিকা" to "লেনদেন", "Transaction List" to "লেনদেন", "নতুন লেনদেন" to "নতুন লেনদেন", "New Transaction" to "নতুন লেনদেন", "Add Transaction" to "নতুন লেনদেন", "কাস্টমার নির্বাচন" to "কাস্টমার", "Select Customer" to "কাস্টমার", "সাপ্লায়ার নির্বাচন" to "সাপ্লায়ার", "Select Supplier" to "সাপ্লায়ার", "সংরক্ষণ" to "সংরক্ষণ", "Save Transaction" to "সংরক্ষণ", "Commit & Save Transaction" to "সংরক্ষণ",
        "খরচের তালিকা" to "খরচ", "Expense List" to "খরচ", "Expenses & Inc." to "খরচ", "আয়/ব্যয়" to "আয়", "Expense & Income" to "আয়", "Operating overhead & Miscellaneous" to "খরচ", "Log Operating Expense/Income" to "নতুন এন্ট্রি", "Operating Expense" to "খরচ", "Operating Credit" to "আয়", "নতুন খরচ" to "নতুন খরচ", "Add Expense" to "নতুন খরচ", "Add Expense/Income" to "নতুন এন্ট্রি",
        "ওয়ালেট তালিকা" to "ওয়ালেট", "Wallet List" to "ওয়ালেট", "Wallet Management" to "ওয়ালেট", "দৈনিক বিনিময় হার" to "রেট", "Daily Exchange Rates" to "রেট", "Live Daily Exchange Rates" to "রেট", "Customer Exchange Rate" to "কাস্টমার রেট", "Supplier Exchange Rate" to "সাপ্লায়ার রেট", "Supplier Exchange Rate (Cost)" to "সাপ্লায়ার রেট",
        "অ্যাকাউন্ট শেয়ারিং সেটিংস" to "শেয়ারিং", "Account Sharing Settings" to "শেয়ারিং", "ইউজার ম্যানেজমেন্ট" to "ইউজার", "User Management" to "ইউজার", "Operator & Staff User Management" to "ইউজার", "Authorized Operators & Personnel" to "ইউজার", "অ্যাপ্লিকেশন সেটিংস" to "সেটিংস", "Application Settings" to "সেটিংস", "Manage Expenses" to "খরচ", "রিপোর্ট দেখুন" to "রিপোর্ট", "View Reports" to "রিপোর্ট",
        "SuperAdmin 1-Time Activation" to "অ্যাক্টিভেশন", "Activate SuperAdmin" to "অ্যাক্টিভেট", "Complete Activation" to "অ্যাক্টিভেট", "Create Staff Account" to "নতুন ইউজার", "Provision New Staff Account" to "নতুন ইউজার", "Change Role" to "রোল", "User Authorization Role" to "রোল", "Permission Required" to "অনুমতি", "You do not have permission to access this feature." to "অনুমতি নেই",
        "Connection Failed" to "অফলাইন", "Sync Completed" to "সিঙ্ক", "Sync Error" to "সিঙ্ক সমস্যা", "Wrong mobile or PIN" to "পিন ভুল", "Invalid mobile or PIN" to "পিন ভুল", "Incorrect PIN! Please try again." to "পিন ভুল", "No account found with this mobile number" to "অ্যাকাউন্ট নেই",
        "কাস্টমার মুছুন" to "মুছুন", "Delete Customer" to "মুছুন", "সাপ্লায়ার মুছুন" to "মুছুন", "Delete Supplier" to "মুছুন", "লেনদেন মুছুন" to "মুছুন", "Delete Transaction" to "মুছুন", "খরচ মুছুন" to "মুছুন", "Delete Expense" to "মুছুন", "ওয়ালেট মুছুন" to "মুছুন", "Delete Wallet" to "মুছুন", "মুছে ফেলুন" to "মুছুন", "Confirm Delete" to "মুছুন",
        "বাতিল করুন" to "বাতিল", "Cancel" to "বাতিল", "নিশ্চিত করুন" to "নিশ্চিত", "Confirm" to "নিশ্চিত", "সংরক্ষণ করুন" to "সংরক্ষণ", "Save" to "সংরক্ষণ", "আপডেট করুন" to "আপডেট", "Update" to "আপডেট", "বন্ধ করুন" to "বন্ধ", "Close" to "বন্ধ", "আবার চেষ্টা করুন" to "আবার", "Retry" to "আবার",
        "Dashboard" to "ড্যাশবোর্ড", "Settings" to "সেটিংস", "Reports" to "রিপোর্ট", "Customers" to "কাস্টমার", "Suppliers" to "সাপ্লায়ার", "Transactions" to "লেনদেন", "Expenses" to "খরচ", "Wallet" to "ওয়ালেট", "Log In" to "লগইন", "Mobile Number" to "মোবাইল", "Full Name" to "নাম", "Email Address" to "ইমেইল", "Enter 6-Digit Security PIN" to "পিন", "Saudi Mobile Number" to "মোবাইল", "Receiver Full Name" to "প্রাপক", "Receiver Mobile No" to "প্রাপক মোবাইল", "Delivery Status" to "স্ট্যাটাস", "Payout Channel" to "পেমেন্ট", "Additional Instructions / Delivery Address" to "নোট", "Account No (bKash/Nagad/Bank)" to "অ্যাকাউন্ট",
        "View Customers" to "কাস্টমার", "Add Customers" to "নতুন কাস্টমার", "Edit Customers" to "কাস্টমার এডিট", "Delete Customers" to "কাস্টমার মুছুন", "View Suppliers" to "সাপ্লায়ার", "Add Suppliers" to "নতুন সাপ্লায়ার", "Edit Suppliers" to "সাপ্লায়ার এডিট", "Delete Suppliers" to "সাপ্লায়ার মুছুন", "View Transactions" to "লেনদেন", "Add Transactions" to "নতুন লেনদেন", "Edit Transactions" to "লেনদেন এডিট", "Delete Transactions" to "লেনদেন মুছুন", "Manage Wallet" to "ওয়ালেট", "View Reports" to "রিপোর্ট"
    )

    fun compact(value: String, language: String? = null): String {
        val normalized = value.trim()
        if (normalized.isEmpty()) return normalized
        val isBengali = normalized.any { it in '\u0980'..'\u09FF' }
        val useBn = language.equals("BN", ignoreCase = true) || isBengali
        val table = if (useBn) bn else en
        return table[normalized] ?: normalized
    }
}
