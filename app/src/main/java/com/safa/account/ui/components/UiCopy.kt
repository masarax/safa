package com.safa.account.ui.components

/** Compact SAFA vocabulary for English and Bengali UI surfaces. */
object UiCopy {
    private val en = mapOf(
        "Customer List" to "Customers", "Supplier List" to "Suppliers",
        "Add Customer" to "New Customer", "Add New Customer" to "New Customer",
        "Add Supplier" to "New Supplier", "Add New Supplier" to "New Supplier",
        "Customer Management" to "Customers", "Supplier Management" to "Suppliers",
        "Customer Details" to "Customer", "Supplier Details" to "Supplier",
        "Transaction List" to "Transactions", "New Transaction" to "Transaction",
        "Add Transaction" to "Transaction", "Expense List" to "Expenses",
        "Add Expense" to "New Expense", "Expense & Income" to "Income",
        "Wallet List" to "Wallets", "Daily Exchange Rates" to "Rates",
        "Account Sharing Settings" to "Sharing", "User Management" to "Users",
        "Application Settings" to "Settings", "Manage Expenses" to "Expenses",
        "View Reports" to "Reports", "Select Customer" to "Customer",
        "Select Supplier" to "Supplier", "Save Customer" to "Save",
        "Save Supplier" to "Save", "Complete Activation" to "Activate",
        "Change Role" to "Role", "Permission Required" to "Permission",
        "Connection Failed" to "Offline", "Sync Completed" to "Synced",
        "Sync Error" to "Sync Error", "Wrong mobile or PIN" to "Invalid PIN",
        "Invalid mobile or PIN" to "Invalid PIN", "Delete Customer" to "Delete",
        "Delete Supplier" to "Delete", "Delete Transaction" to "Delete",
        "Delete Expense" to "Delete", "Delete Wallet" to "Delete",
        "Confirm Delete" to "Delete", "Cancel" to "Cancel",
        "Confirm" to "Confirm", "Save" to "Save", "Update" to "Update",
        "Close" to "Close", "Retry" to "Retry"
    )

    private val bn = mapOf(
        "কাস্টমার তালিকা" to "কাস্টমার", "সাপ্লায়ার তালিকা" to "সাপ্লায়ার",
        "কাস্টমার ম্যানেজমেন্ট" to "কাস্টমার", "সাপ্লায়ার ম্যানেজমেন্ট" to "সাপ্লায়ার",
        "কাস্টমারের বিস্তারিত" to "কাস্টমার", "সাপ্লায়ারের বিস্তারিত" to "সাপ্লায়ার",
        "কাস্টমার যুক্ত করুন" to "নতুন কাস্টমার", "নতুন কাস্টমার যোগ করুন" to "নতুন কাস্টমার",
        "সাপ্লায়ার যুক্ত করুন" to "নতুন সাপ্লায়ার", "নতুন সাপ্লায়ার যোগ করুন" to "নতুন সাপ্লায়ার",
        "লেনদেন তালিকা" to "লেনদেন", "নতুন লেনদেন" to "নতুন লেনদেন",
        "খরচের তালিকা" to "খরচ", "নতুন খরচ" to "নতুন খরচ",
        "আয়/ব্যয়" to "আয়", "ওয়ালেট তালিকা" to "ওয়ালেট",
        "দৈনিক বিনিময় হার" to "রেট", "অ্যাকাউন্ট শেয়ারিং সেটিংস" to "শেয়ারিং",
        "ইউজার ম্যানেজমেন্ট" to "ইউজার", "অ্যাপ্লিকেশন সেটিংস" to "সেটিংস",
        "রিপোর্ট দেখুন" to "রিপোর্ট", "কাস্টমার নির্বাচন" to "কাস্টমার",
        "সাপ্লায়ার নির্বাচন" to "সাপ্লায়ার", "কাস্টমার সংরক্ষণ" to "সংরক্ষণ",
        "সাপ্লায়ার সংরক্ষণ" to "সংরক্ষণ", "অ্যাক্টিভেশন সম্পন্ন" to "অ্যাক্টিভেট",
        "রোল পরিবর্তন" to "রোল", "অনুমতি প্রয়োজন" to "অনুমতি",
        "সংযোগ ব্যর্থ" to "অফলাইন", "সিঙ্ক সম্পন্ন" to "সিঙ্ক",
        "সিঙ্ক সমস্যা" to "সিঙ্ক সমস্যা", "ভুল মোবাইল বা পিন" to "পিন ভুল",
        "কাস্টমার মুছুন" to "মুছুন", "সাপ্লায়ার মুছুন" to "মুছুন",
        "লেনদেন মুছুন" to "মুছুন", "খরচ মুছুন" to "মুছুন",
        "ওয়ালেট মুছুন" to "মুছুন", "মুছে ফেলুন" to "মুছুন",
        "বাতিল করুন" to "বাতিল", "নিশ্চিত করুন" to "নিশ্চিত",
        "সংরক্ষণ করুন" to "সংরক্ষণ", "আপডেট করুন" to "আপডেট",
        "বন্ধ করুন" to "বন্ধ", "আবার চেষ্টা করুন" to "আবার"
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
