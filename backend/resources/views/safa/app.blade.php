@php
    $bn = $language === 'bn';
    $canCustomers = (bool) ($permissions['can_view_customers'] ?? false);
    $canSuppliers = (bool) ($permissions['can_view_suppliers'] ?? false);
    $canTransactions = (bool) ($permissions['can_view_transactions'] ?? false);
    $canAddTransactions = (bool) ($permissions['can_add_transactions'] ?? false);
    $canEditTransactions = (bool) ($permissions['can_edit_transactions'] ?? false);
    $canDeleteTransactions = (bool) ($permissions['can_delete_transactions'] ?? false);
    $canWallet = (bool) ($permissions['can_manage_wallet'] ?? false);
    $canExpenses = (bool) ($permissions['can_manage_expenses'] ?? false);
    $canReports = (bool) ($permissions['can_view_reports'] ?? false);
@endphp
<!doctype html>
<html lang="{{ $language }}" dir="ltr">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <meta name="color-scheme" content="light dark">
    <meta name="csrf-token" content="{{ csrf_token() }}">
    <title>{{ $setting?->app_name ?: 'SAFA' }} — {{ $bn ? 'ওয়েব অ্যাপ' : 'Web App' }}</title>
    <link rel="icon" href="{{ url('/favicon.svg') }}" type="image/svg+xml">
    <link rel="stylesheet" href="{{ url('/safa-web.css') }}">
    <script src="{{ url('/safa-web.js') }}" defer></script>
</head>
<body class="app-body">
<div
    id="safa-app"
    class="app-shell"
    data-language="{{ $language }}"
    data-active-account="{{ $activeAccountId ?: '' }}"
    data-accounts-url="{{ route('safa.web.accounts') }}"
    data-account-switch-url="{{ route('safa.web.account.switch') }}"
    data-customers-url="{{ $canCustomers ? route('safa.web.customers') : '' }}"
    data-suppliers-url="{{ $canSuppliers ? route('safa.web.suppliers') : '' }}"
    data-transactions-url="{{ $canTransactions ? route('safa.web.transactions') : '' }}"
    data-wallet-ledgers-url="{{ $canWallet ? route('safa.web.wallet-ledgers') : '' }}"
    data-wallet-batches-url="{{ $canWallet ? route('safa.web.wallet-batches') : '' }}"
    data-supplier-deposits-url="{{ $canWallet ? route('safa.web.supplier-deposits') : '' }}"
    data-expenses-url="{{ $canExpenses ? route('safa.web.expenses') : '' }}"
    data-config-url="{{ $canManageSystemSettings ? route('safa.web.config') : '' }}"
    data-logo-url="{{ $canManageSystemSettings ? route('safa.web.logo') : '' }}"
    data-users-url="{{ $canManageUsers ? route('safa.web.users') : '' }}"
    data-personal-settings-url="{{ route('safa.web.settings.personal') }}"
    data-pin-settings-url="{{ route('safa.web.settings.pin') }}"
    data-can-add-transactions="{{ $canAddTransactions ? '1' : '0' }}"
    data-can-edit-transactions="{{ $canEditTransactions ? '1' : '0' }}"
    data-can-delete-transactions="{{ $canDeleteTransactions ? '1' : '0' }}"
>
    <aside class="sidebar" id="sidebar" aria-label="{{ $bn ? 'প্রধান নেভিগেশন' : 'Primary navigation' }}">
        <div class="brand-row">
            <img class="brand-logo" src="{{ $logoSource }}" alt="{{ $setting?->app_name ?: 'SAFA' }}">
            <div>
                <strong class="brand-name">{{ $setting?->app_name ?: 'SAFA' }}</strong>
                <span class="brand-caption">{{ $setting?->captain_name ?: ($bn ? 'আর্থিক ব্যবস্থাপনা' : 'Financial Operations') }}</span>
            </div>
        </div>

        <nav class="sidebar-nav">
            <button class="nav-button active" type="button" data-section="dashboard">{{ $bn ? 'ড্যাশবোর্ড' : 'Dashboard' }}</button>
            @if($canCustomers)<button class="nav-button" type="button" data-section="customers">{{ $bn ? 'কাস্টমার' : 'Customers' }}</button>@endif
            @if($canSuppliers)<button class="nav-button" type="button" data-section="suppliers">{{ $bn ? 'সাপ্লায়ার' : 'Suppliers' }}</button>@endif
            @if($canWallet)<button class="nav-button" type="button" data-section="wallet">{{ $bn ? 'ওয়ালেট' : 'Wallet' }}</button>@endif
            @if($canExpenses)<button class="nav-button" type="button" data-section="expenses">{{ $bn ? 'দৈনিক আয়/ব্যয়' : 'Daily Income / Expense' }}</button>@endif
            @if($canReports)<button class="nav-button" type="button" data-section="reports">{{ $bn ? 'রিপোর্ট' : 'Reports' }}</button>@endif
            <button class="nav-button" type="button" data-section="settings">{{ $bn ? 'সেটিংস' : 'Settings' }}</button>
            @if($canManageUsers)<button class="nav-button" type="button" data-section="users">{{ $bn ? 'ইউজার ম্যানেজমেন্ট' : 'User Management' }}</button>@endif
        </nav>

        <div class="sidebar-footer">
            <div class="user-mini">
                <strong>{{ $user->name }}</strong>
                <span>{{ $roleLabel }}</span>
            </div>
            <form class="logout-form" method="post" action="{{ route('safa.logout') }}">
                @csrf
                <button class="button secondary" type="submit">{{ $bn ? 'লগআউট' : 'Sign out' }}</button>
            </form>
        </div>
    </aside>

    <div class="main-column">
        <header class="topbar">
            <div>
                <button class="button secondary small menu-toggle" id="menu-toggle" type="button" aria-controls="sidebar" aria-expanded="false">☰</button>
                <h1><span class="brand-name">{{ $setting?->app_name ?: 'SAFA' }}</span> {{ $bn ? 'ওয়েব ওয়ার্কস্পেস' : 'Web Workspace' }}</h1>
                <p>{{ $bn ? 'সার্ভার-নিয়ন্ত্রিত অ্যাকাউন্ট ও অনুমতি' : 'Server-authoritative account and permission context' }}</p>
            </div>
            <div class="topbar-actions">
                <label class="account-select">
                    <span class="hidden">{{ $bn ? 'ব্যবসার অ্যাকাউন্ট' : 'Business account' }}</span>
                    <select id="account-select" {{ count($accounts) === 0 ? 'disabled' : '' }} aria-label="{{ $bn ? 'ব্যবসার অ্যাকাউন্ট নির্বাচন' : 'Select business account' }}">
                        @if(!$activeAccountId && count($accounts) > 1)<option value="">{{ $bn ? 'অ্যাকাউন্ট নির্বাচন করুন' : 'Select account' }}</option>@endif
                        @foreach($accounts as $account)
                            <option value="{{ (int) $account['account_id'] }}" {{ (int) $activeAccountId === (int) $account['account_id'] ? 'selected' : '' }}>
                                {{ $account['owner_name'] ?: ($bn ? 'অ্যাকাউন্ট' : 'Account') }} · #{{ (int) $account['account_id'] }}
                            </option>
                        @endforeach
                    </select>
                </label>
            </div>
        </header>

        <main class="content">
            <div id="global-message" class="alert alert-info {{ $activeAccountId ? 'hidden' : '' }}" role="status">
                {{ $bn ? 'ব্যবসার ডেটা দেখার আগে একটি অনুমোদিত অ্যাকাউন্ট নির্বাচন করুন।' : 'Select an authorized business account before loading business data.' }}
            </div>

            <section class="panel active" data-panel="dashboard">
                <div class="panel-heading">
                    <div><h2>{{ $bn ? 'ড্যাশবোর্ড' : 'Dashboard' }}</h2><p>{{ $bn ? 'বর্তমান অ্যাকাউন্টের দ্রুত সারাংশ' : 'A live summary of the selected account.' }}</p></div>
                    <button class="button secondary" type="button" data-action="refresh-all">{{ $bn ? 'রিফ্রেশ' : 'Refresh' }}</button>
                </div>
                <div class="stats-grid">
                    @if($canCustomers)<article class="stat-card"><span>{{ $bn ? 'কাস্টমার' : 'Customers' }}</span><strong id="stat-customers">—</strong></article>@endif
                    @if($canSuppliers)<article class="stat-card"><span>{{ $bn ? 'সাপ্লায়ার' : 'Suppliers' }}</span><strong id="stat-suppliers">—</strong></article>@endif
                    @if($canTransactions)<article class="stat-card"><span>{{ $bn ? 'লেনদেন' : 'Transactions' }}</span><strong id="stat-transactions">—</strong></article>@endif
                    @if($canExpenses)<article class="stat-card"><span>{{ $bn ? 'আয়/ব্যয় এন্ট্রি' : 'Income / Expense entries' }}</span><strong id="stat-expenses">—</strong></article>@endif
                </div>
                <div class="card"><div class="card-title"><h3>{{ $bn ? 'নিরাপত্তা ও ভূমিকা' : 'Security & role' }}</h3></div><p class="muted">{{ $bn ? "আপনি {$roleLabel} হিসেবে লগইন করেছেন। নেভিগেশন এবং প্রতিটি সার্ভার অনুরোধ একই অনুমতি নীতি অনুসরণ করে।" : "You are signed in as {$roleLabel}. Navigation and every server request follow the same permission policy." }}</p></div>
            </section>

            @if($canCustomers)
            <section class="panel" data-panel="customers">
                <div id="customers-list-view">
                    <div class="panel-heading"><div><h2>{{ $bn ? 'কাস্টমার' : 'Customers' }}</h2><p>{{ $bn ? 'কাস্টমার খুলে তার প্রোফাইল থেকেই লেনদেন পরিচালনা করুন।' : 'Open a customer to manage customer details and transactions from the profile.' }}</p></div></div>
                    @if($permissions['can_add_customers'] ?? false)
                    <div class="card">
                        <div class="card-title"><h3>{{ $bn ? 'নতুন কাস্টমার' : 'Add customer' }}</h3></div>
                        <form class="resource-form form-grid" data-resource="customers">
                            <label class="field"><span>{{ $bn ? 'নাম' : 'Name' }}</span><input name="name" maxlength="255" required></label>
                            <label class="field"><span>{{ $bn ? 'ফোন' : 'Phone' }}</span><input name="phone" maxlength="50" inputmode="tel"></label>
                            <label class="field span-2"><span>{{ $bn ? 'ঠিকানা' : 'Address' }}</span><input name="address" maxlength="500"></label>
                            <div class="form-actions"><button class="button primary" type="submit">{{ $bn ? 'সংরক্ষণ' : 'Save customer' }}</button></div>
                        </form>
                    </div>
                    @endif
                    <div class="card"><div class="table-wrap"><table><thead><tr><th>{{ $bn ? 'নাম' : 'Name' }}</th><th>{{ $bn ? 'ফোন' : 'Phone' }}</th><th>{{ $bn ? 'ঠিকানা' : 'Address' }}</th><th></th></tr></thead><tbody id="customers-body"><tr><td class="empty-cell" colspan="4">{{ $bn ? 'লোড হচ্ছে…' : 'Loading…' }}</td></tr></tbody></table></div></div>
                </div>

                <div id="customer-profile" class="profile-view hidden" aria-live="polite">
                    <div class="panel-heading">
                        <div><p class="eyebrow">{{ $bn ? 'কাস্টমার প্রোফাইল' : 'Customer profile' }}</p><h2 id="customer-profile-name">—</h2><p id="customer-profile-contact" class="muted">—</p></div>
                        <button class="button secondary" type="button" data-close-profile="customer">{{ $bn ? 'তালিকায় ফিরুন' : 'Back to customers' }}</button>
                    </div>
                    @if($canTransactions)
                    <div class="stats-grid profile-stats">
                        <article class="stat-card"><span>{{ $bn ? 'লেনদেন' : 'Transactions' }}</span><strong id="customer-profile-count">0</strong></article>
                        <article class="stat-card"><span>{{ $bn ? 'মোট SAR' : 'Total SAR' }}</span><strong id="customer-profile-sar">0.00</strong></article>
                        <article class="stat-card"><span>{{ $bn ? 'মোট BDT' : 'Total BDT' }}</span><strong id="customer-profile-bdt">0.00</strong></article>
                        <article class="stat-card"><span>{{ $bn ? 'বাকি SAR' : 'Due SAR' }}</span><strong id="customer-profile-due">0.00</strong></article>
                    </div>
                    @endif

                    @if($canAddTransactions)
                    <div class="card">
                        <div class="card-title"><h3>{{ $bn ? 'কাস্টমার লেনদেন' : 'Customer transaction' }}</h3><span class="muted">{{ $bn ? 'কাস্টমার স্বয়ংক্রিয়ভাবে নির্বাচন করা আছে' : 'Customer is bound from this profile' }}</span></div>
                        <form id="customer-transaction-form" class="profile-transaction-form form-grid" data-context="customer">
                            <input type="hidden" name="customer_id">
                            @if($canSuppliers)<label class="field"><span>{{ $bn ? 'সাপ্লায়ার' : 'Supplier' }}</span><select name="supplier_id" data-profile-select="suppliers"><option value="">{{ $bn ? 'ঐচ্ছিক' : 'Optional' }}</option></select></label>@endif
                            <label class="field"><span>SAR</span><input name="amount_sar" inputmode="decimal" value="0.00" required></label>
                            <label class="field"><span>BDT</span><input name="amount_bdt" inputmode="decimal" value="0.00"></label>
                            <label class="field"><span>{{ $bn ? 'কাস্টমার রেট' : 'Customer rate' }}</span><input name="customer_rate" inputmode="decimal" value="0.0000"></label>
                            @if($canSuppliers && ($setting?->supplier_rate_enabled ?? true))<label class="field"><span>{{ $bn ? 'সাপ্লায়ার রেট' : 'Supplier rate' }}</span><input name="supplier_rate" inputmode="decimal" value="0.0000"></label>@endif
                            <label class="field"><span>{{ $bn ? 'সংগৃহীত SAR' : 'SAR collected' }}</span><input name="sar_collected" inputmode="decimal" value="0.00"></label>
                            <label class="field"><span>{{ $bn ? 'পাঠানো BDT' : 'BDT disbursed' }}</span><input name="bdt_disbursed" inputmode="decimal" value="0.00"></label>
                            <label class="field"><span>{{ $bn ? 'রিসিভারের নাম' : 'Receiver name' }}</span><input name="receiver_name" maxlength="255"></label>
                            <label class="field"><span>{{ $bn ? 'রিসিভারের ফোন' : 'Receiver phone' }}</span><input name="receiver_phone" maxlength="50"></label>
                            <label class="field"><span>{{ $bn ? 'অ্যাকাউন্ট ধরন' : 'Account type' }}</span><input name="receiver_account_type" maxlength="50"></label>
                            <label class="field"><span>{{ $bn ? 'অ্যাকাউন্ট নম্বর' : 'Account number' }}</span><input name="receiver_account_no" maxlength="100"></label>
                            <label class="field span-4"><span>{{ $bn ? 'নোট' : 'Notes' }}</span><textarea name="notes" maxlength="5000"></textarea></label>
                            <div class="form-actions"><button class="button primary" type="submit">{{ $bn ? 'লেনদেন সংরক্ষণ' : 'Save transaction' }}</button><button class="button secondary hidden" type="button" data-cancel-transaction-edit>{{ $bn ? 'বাতিল' : 'Cancel edit' }}</button></div>
                        </form>
                    </div>
                    @endif
                    @if($canTransactions)<div class="card"><div class="card-title"><h3>{{ $bn ? 'লেনদেন ইতিহাস' : 'Transaction history' }}</h3></div><div class="table-wrap"><table><thead><tr><th>SAR</th><th>BDT</th><th>{{ $bn ? 'রেট' : 'Rates' }}</th><th>{{ $bn ? 'রিসিভার' : 'Receiver' }}</th><th>{{ $bn ? 'তারিখ' : 'Date' }}</th><th></th></tr></thead><tbody id="customer-profile-transactions-body"></tbody></table></div></div>@endif
                </div>
            </section>
            @endif

            @if($canSuppliers)
            <section class="panel" data-panel="suppliers">
                <div id="suppliers-list-view">
                    <div class="panel-heading"><div><h2>{{ $bn ? 'সাপ্লায়ার' : 'Suppliers' }}</h2><p>{{ $bn ? 'সাপ্লায়ার খুলে তার প্রোফাইল থেকে সম্পর্কিত লেনদেন পরিচালনা করুন।' : 'Open a supplier to manage its related transaction workflow.' }}</p></div></div>
                    @if($permissions['can_add_suppliers'] ?? false)
                    <div class="card"><div class="card-title"><h3>{{ $bn ? 'নতুন সাপ্লায়ার' : 'Add supplier' }}</h3></div><form class="resource-form form-grid" data-resource="suppliers"><label class="field"><span>{{ $bn ? 'নাম' : 'Name' }}</span><input name="name" maxlength="255" required></label><label class="field"><span>{{ $bn ? 'ফোন' : 'Phone' }}</span><input name="phone" maxlength="50" inputmode="tel"></label><label class="field span-2"><span>{{ $bn ? 'ঠিকানা' : 'Address' }}</span><input name="address" maxlength="500"></label><div class="form-actions"><button class="button primary" type="submit">{{ $bn ? 'সংরক্ষণ' : 'Save supplier' }}</button></div></form></div>
                    @endif
                    <div class="card"><div class="table-wrap"><table><thead><tr><th>{{ $bn ? 'নাম' : 'Name' }}</th><th>{{ $bn ? 'ফোন' : 'Phone' }}</th><th>{{ $bn ? 'ঠিকানা' : 'Address' }}</th><th></th></tr></thead><tbody id="suppliers-body"><tr><td class="empty-cell" colspan="4">{{ $bn ? 'লোড হচ্ছে…' : 'Loading…' }}</td></tr></tbody></table></div></div>
                </div>

                <div id="supplier-profile" class="profile-view hidden" aria-live="polite">
                    <div class="panel-heading"><div><p class="eyebrow">{{ $bn ? 'সাপ্লায়ার প্রোফাইল' : 'Supplier profile' }}</p><h2 id="supplier-profile-name">—</h2><p id="supplier-profile-contact" class="muted">—</p></div><button class="button secondary" type="button" data-close-profile="supplier">{{ $bn ? 'তালিকায় ফিরুন' : 'Back to suppliers' }}</button></div>
                    @if($canTransactions)
                    <div class="stats-grid profile-stats"><article class="stat-card"><span>{{ $bn ? 'লেনদেন' : 'Transactions' }}</span><strong id="supplier-profile-count">0</strong></article><article class="stat-card"><span>{{ $bn ? 'মোট SAR' : 'Total SAR' }}</span><strong id="supplier-profile-sar">0.00</strong></article><article class="stat-card"><span>{{ $bn ? 'মোট BDT' : 'Total BDT' }}</span><strong id="supplier-profile-bdt">0.00</strong></article>@if($canWallet)<article class="stat-card"><span>{{ $bn ? 'ডিপোজিট' : 'Deposits' }}</span><strong id="supplier-profile-deposit-count">0</strong></article>@endif</div>
                    @endif

                    @if($canAddTransactions)
                    <div class="card"><div class="card-title"><h3>{{ $bn ? 'সাপ্লায়ার লেনদেন' : 'Supplier transaction' }}</h3><span class="muted">{{ $bn ? 'সাপ্লায়ার স্বয়ংক্রিয়ভাবে নির্বাচন করা আছে' : 'Supplier is bound from this profile' }}</span></div>
                        <form id="supplier-transaction-form" class="profile-transaction-form form-grid" data-context="supplier">
                            <input type="hidden" name="supplier_id">
                            @if($canCustomers)<label class="field"><span>{{ $bn ? 'কাস্টমার' : 'Customer' }}</span><select name="customer_id" data-profile-select="customers"><option value="">{{ $bn ? 'ঐচ্ছিক' : 'Optional' }}</option></select></label>@endif
                            <label class="field"><span>SAR</span><input name="amount_sar" inputmode="decimal" value="0.00" required></label><label class="field"><span>BDT</span><input name="amount_bdt" inputmode="decimal" value="0.00"></label><label class="field"><span>{{ $bn ? 'কাস্টমার রেট' : 'Customer rate' }}</span><input name="customer_rate" inputmode="decimal" value="0.0000"></label><label class="field"><span>{{ $bn ? 'সাপ্লায়ার রেট' : 'Supplier rate' }}</span><input name="supplier_rate" inputmode="decimal" value="0.0000"></label><label class="field"><span>{{ $bn ? 'সংগৃহীত SAR' : 'SAR collected' }}</span><input name="sar_collected" inputmode="decimal" value="0.00"></label><label class="field"><span>{{ $bn ? 'পাঠানো BDT' : 'BDT disbursed' }}</span><input name="bdt_disbursed" inputmode="decimal" value="0.00"></label><label class="field"><span>{{ $bn ? 'রিসিভারের নাম' : 'Receiver name' }}</span><input name="receiver_name" maxlength="255"></label><label class="field"><span>{{ $bn ? 'রিসিভারের ফোন' : 'Receiver phone' }}</span><input name="receiver_phone" maxlength="50"></label><label class="field"><span>{{ $bn ? 'অ্যাকাউন্ট ধরন' : 'Account type' }}</span><input name="receiver_account_type" maxlength="50"></label><label class="field"><span>{{ $bn ? 'অ্যাকাউন্ট নম্বর' : 'Account number' }}</span><input name="receiver_account_no" maxlength="100"></label><label class="field span-4"><span>{{ $bn ? 'নোট' : 'Notes' }}</span><textarea name="notes" maxlength="5000"></textarea></label><div class="form-actions"><button class="button primary" type="submit">{{ $bn ? 'লেনদেন সংরক্ষণ' : 'Save transaction' }}</button><button class="button secondary hidden" type="button" data-cancel-transaction-edit>{{ $bn ? 'বাতিল' : 'Cancel edit' }}</button></div>
                        </form>
                    </div>
                    @endif
                    @if($canTransactions)<div class="card"><div class="card-title"><h3>{{ $bn ? 'লেনদেন ইতিহাস' : 'Transaction history' }}</h3></div><div class="table-wrap"><table><thead><tr><th>SAR</th><th>BDT</th><th>{{ $bn ? 'রেট' : 'Rates' }}</th><th>{{ $bn ? 'রিসিভার' : 'Receiver' }}</th><th>{{ $bn ? 'তারিখ' : 'Date' }}</th><th></th></tr></thead><tbody id="supplier-profile-transactions-body"></tbody></table></div></div>@endif
                    @if($canWallet)<div class="card"><div class="card-title"><h3>{{ $bn ? 'সাপ্লায়ার ডিপোজিট ইতিহাস' : 'Supplier deposit history' }}</h3></div><div class="table-wrap"><table><thead><tr><th>SAR</th><th>{{ $bn ? 'রেট' : 'Rate' }}</th><th>BDT</th><th>{{ $bn ? 'পরিশোধিত' : 'Paid' }}</th><th>{{ $bn ? 'ধরন' : 'Type' }}</th></tr></thead><tbody id="supplier-profile-deposits-body"></tbody></table></div></div>@endif
                </div>
            </section>
            @endif

            @if($canWallet)
            <section class="panel" data-panel="wallet">
                <div class="panel-heading"><div><h2>{{ $bn ? 'ওয়ালেট' : 'Wallet' }}</h2><p>{{ $bn ? 'ওয়ালেট লেজার, ব্যাচ এবং সাপ্লায়ার ডিপোজিট।' : 'Wallet ledgers, batches, and supplier deposits.' }}</p></div></div>
                <div class="section-tabs"><button type="button" class="section-tab active" data-subsection="wallet-ledgers">{{ $bn ? 'লেজার' : 'Ledgers' }}</button><button type="button" class="section-tab" data-subsection="wallet-batches">{{ $bn ? 'ব্যাচ' : 'Batches' }}</button><button type="button" class="section-tab" data-subsection="supplier-deposits">{{ $bn ? 'ডিপোজিট' : 'Supplier deposits' }}</button></div>
                <div class="subpanel active" data-subpanel="wallet-ledgers"><div class="card"><form class="resource-form form-grid" data-resource="wallet-ledgers"><label class="field span-2"><span>{{ $bn ? 'লেজারের নাম' : 'Ledger name' }}</span><input name="name" maxlength="255" required></label><div class="form-actions"><button class="button primary" type="submit">{{ $bn ? 'লেজার যোগ করুন' : 'Add ledger' }}</button></div></form></div><div class="card"><div class="table-wrap"><table><thead><tr><th>ID</th><th>{{ $bn ? 'নাম' : 'Name' }}</th><th></th></tr></thead><tbody id="wallet-ledgers-body"></tbody></table></div></div></div>
                <div class="subpanel" data-subpanel="wallet-batches"><div class="card"><form class="resource-form form-grid" data-resource="wallet-batches"><label class="field"><span>{{ $bn ? 'লেজার ID' : 'Ledger ID' }}</span><input name="ledger_id" inputmode="numeric"></label><label class="field"><span>{{ $bn ? 'রেট' : 'Rate' }}</span><input name="rate" inputmode="decimal" value="0.0000" required></label><label class="field"><span>{{ $bn ? 'প্রাথমিক BDT' : 'Initial BDT' }}</span><input name="initial_bdt" inputmode="decimal" value="0.00" required></label><label class="field"><span>{{ $bn ? 'অবশিষ্ট BDT' : 'Remaining BDT' }}</span><input name="remaining_bdt" inputmode="decimal" value="0.00" required></label><label class="field"><span>{{ $bn ? 'সাপ্লায়ার ID' : 'Supplier ID' }}</span><input name="supplier_id" inputmode="numeric"></label><label class="field"><span>{{ $bn ? 'ডিপোজিট ID' : 'Deposit ID' }}</span><input name="supplier_deposit_id" inputmode="numeric"></label><label class="field span-2"><span>{{ $bn ? 'নোট' : 'Notes' }}</span><input name="notes" maxlength="10000"></label><div class="form-actions"><button class="button primary" type="submit">{{ $bn ? 'ব্যাচ যোগ করুন' : 'Add batch' }}</button></div></form></div><div class="card"><div class="table-wrap"><table><thead><tr><th>ID</th><th>{{ $bn ? 'রেট' : 'Rate' }}</th><th>{{ $bn ? 'প্রাথমিক' : 'Initial' }}</th><th>{{ $bn ? 'অবশিষ্ট' : 'Remaining' }}</th><th></th></tr></thead><tbody id="wallet-batches-body"></tbody></table></div></div></div>
                <div class="subpanel" data-subpanel="supplier-deposits"><div class="card"><form class="resource-form form-grid" data-resource="supplier-deposits"><label class="field"><span>{{ $bn ? 'সাপ্লায়ার ID' : 'Supplier ID' }}</span><input name="supplier_id" inputmode="numeric"></label><label class="field"><span>{{ $bn ? 'SAR পরিমাণ' : 'SAR amount' }}</span><input name="amount_sar" inputmode="decimal" value="0.00" required></label><label class="field"><span>{{ $bn ? 'রেট' : 'Rate' }}</span><input name="rate" inputmode="decimal" value="0.0000" required></label><label class="field"><span>{{ $bn ? 'BDT পরিমাণ' : 'BDT amount' }}</span><input name="amount_bdt" inputmode="decimal" value="0.00" required></label><label class="field"><span>{{ $bn ? 'পরিশোধিত BDT' : 'Paid BDT' }}</span><input name="paid_bdt" inputmode="decimal" value="0.00"></label><label class="field"><span>{{ $bn ? 'ধরন' : 'Type' }}</span><select name="transaction_type"><option value="SAR_GIVEN">SAR_GIVEN</option><option value="BDT_PAID">BDT_PAID</option></select></label><label class="field span-2"><span>{{ $bn ? 'নোট' : 'Notes' }}</span><input name="notes" maxlength="10000"></label><div class="form-actions"><button class="button primary" type="submit">{{ $bn ? 'ডিপোজিট যোগ করুন' : 'Add deposit' }}</button></div></form></div><div class="card"><div class="table-wrap"><table><thead><tr><th>ID</th><th>{{ $bn ? 'সাপ্লায়ার' : 'Supplier' }}</th><th>SAR</th><th>BDT</th><th>{{ $bn ? 'পরিশোধিত' : 'Paid' }}</th><th></th></tr></thead><tbody id="supplier-deposits-body"></tbody></table></div></div></div>
            </section>
            @endif

            @if($canExpenses)
            <section class="panel" data-panel="expenses">
                <div class="panel-heading"><div><h2>{{ $bn ? 'দৈনিক আয় / ব্যয়' : 'Daily Income / Expense' }}</h2><p>{{ $bn ? 'দৈনিক অপারেশনাল আয় ও ব্যয় রেকর্ড করুন।' : 'Record daily operational income and expenses.' }}</p></div></div>
                <div class="card"><form class="resource-form form-grid" data-resource="expenses"><label class="field span-2"><span>{{ $bn ? 'বিবরণ' : 'Title' }}</span><input name="title" maxlength="255" required></label><label class="field"><span>{{ $bn ? 'পরিমাণ' : 'Amount' }}</span><input name="amount" inputmode="decimal" value="0.00" required></label><label class="field"><span>{{ $bn ? 'মুদ্রা' : 'Currency' }}</span><select name="currency"><option value="{{ $setting?->local_currency ?: 'BDT' }}">{{ $setting?->local_currency ?: 'BDT' }}</option><option value="{{ $setting?->foreign_currency ?: 'SAR' }}">{{ $setting?->foreign_currency ?: 'SAR' }}</option></select></label><label class="field"><span>{{ $bn ? 'ক্যাটাগরি' : 'Category' }}</span><input name="category" maxlength="255" value="General"></label><label class="check-field"><input type="checkbox" name="is_expense" value="1" checked><span>{{ $bn ? 'এটি ব্যয়' : 'Expense entry' }}</span></label><div class="form-actions"><button class="button primary" type="submit">{{ $bn ? 'এন্ট্রি সংরক্ষণ' : 'Save entry' }}</button></div></form></div>
                <div class="card"><div class="table-wrap"><table><thead><tr><th>{{ $bn ? 'বিবরণ' : 'Title' }}</th><th>{{ $bn ? 'পরিমাণ' : 'Amount' }}</th><th>{{ $bn ? 'ধরন' : 'Type' }}</th><th>{{ $bn ? 'ক্যাটাগরি' : 'Category' }}</th><th></th></tr></thead><tbody id="expenses-body"></tbody></table></div></div>
            </section>
            @endif

            @if($canReports)
            <section class="panel" data-panel="reports">
                <div class="panel-heading"><div><h2>{{ $bn ? 'রিপোর্ট' : 'Reports' }}</h2><p>{{ $bn ? 'বর্তমান ব্রাউজার সেশনে লোড করা সার্ভার ডেটার সারাংশ।' : 'Summary of server data loaded in the current browser session.' }}</p></div></div>
                <div class="stats-grid"><article class="stat-card"><span>{{ $bn ? 'মোট SAR লেনদেন' : 'Transaction SAR total' }}</span><strong id="report-sar">0.00</strong></article><article class="stat-card"><span>{{ $bn ? 'মোট BDT লেনদেন' : 'Transaction BDT total' }}</span><strong id="report-bdt">0.00</strong></article><article class="stat-card"><span>{{ $bn ? 'দৈনিক ব্যয়' : 'Daily expenses' }}</span><strong id="report-expense">0.00</strong></article><article class="stat-card"><span>{{ $bn ? 'অন্যান্য আয়' : 'Other income' }}</span><strong id="report-income">0.00</strong></article></div>
            </section>
            @endif

            <section class="panel" data-panel="settings">
                <div class="panel-heading"><div><h2>{{ $bn ? 'সেটিংস' : 'Settings' }}</h2><p>{{ $bn ? 'আপনার ব্যক্তিগত পছন্দ ও অনুমোদিত সিস্টেম কনফিগারেশন।' : 'Personal preferences and system controls allowed by your role.' }}</p></div></div>
                <div class="settings-section">
                    <h3>{{ $bn ? 'ব্যক্তিগত ও নিরাপত্তা' : 'Personal & security' }}</h3>
                    <div class="grid-3">
                        <div class="card"><div class="card-title"><h3>{{ $bn ? 'ভাষা' : 'Language' }}</h3></div><form id="personal-settings-form" class="stack-form"><label><span>{{ $bn ? 'ওয়েব ভাষা' : 'Web language' }}</span><select name="language"><option value="en" {{ $language === 'en' ? 'selected' : '' }}>English</option><option value="bn" {{ $language === 'bn' ? 'selected' : '' }}>বাংলা</option></select></label><button class="button primary" type="submit">{{ $bn ? 'ভাষা সংরক্ষণ' : 'Save language' }}</button></form></div>
                        <div class="card"><div class="card-title"><h3>{{ $bn ? 'চেহারা' : 'Appearance' }}</h3></div><form id="theme-form" class="stack-form"><label><span>{{ $bn ? 'থিম' : 'Theme' }}</span><select name="theme"><option value="system">{{ $bn ? 'সিস্টেম' : 'System' }}</option><option value="light">{{ $bn ? 'লাইট' : 'Light' }}</option><option value="dark">{{ $bn ? 'ডার্ক' : 'Dark' }}</option></select></label><button class="button primary" type="submit">{{ $bn ? 'থিম প্রয়োগ' : 'Apply theme' }}</button></form></div>
                        <div class="card"><div class="card-title"><h3>{{ $bn ? 'পিন পরিবর্তন' : 'Change PIN' }}</h3></div><form id="pin-form" class="stack-form" autocomplete="off"><label><span>{{ $bn ? 'বর্তমান ৬-ডিজিট পিন' : 'Current 6-digit PIN' }}</span><input type="password" name="current_pin" inputmode="numeric" pattern="[0-9০-৯٠-٩۰-۹]{6}" minlength="6" maxlength="6" required></label><label><span>{{ $bn ? 'নতুন ৬-ডিজিট পিন' : 'New 6-digit PIN' }}</span><input type="password" name="new_pin" inputmode="numeric" minlength="6" maxlength="6" required></label><label><span>{{ $bn ? 'নতুন পিন আবার' : 'Confirm new PIN' }}</span><input type="password" name="new_pin_confirmation" inputmode="numeric" minlength="6" maxlength="6" required></label><button class="button primary" type="submit">{{ $bn ? 'পিন পরিবর্তন' : 'Change PIN' }}</button></form></div>
                    </div>
                </div>

                @if($canManageSystemSettings)
                <div class="settings-section">
                    <h3>{{ $bn ? 'অ্যাডমিন সেটিংস' : 'Admin Settings' }}</h3>
                    <div class="grid-2">
                        <div class="card">
                            <div class="card-title"><h3>{{ $bn ? 'ব্র্যান্ড ও ব্যবসা কনফিগারেশন' : 'Brand & business configuration' }}</h3></div>
                            <form id="config-form" class="form-grid">
                                <label class="field span-2"><span>{{ $bn ? 'অ্যাপ নাম' : 'Application name' }}</span><input name="app_name" maxlength="255" value="{{ $setting?->app_name ?: 'SAFA' }}" required></label>
                                <label class="field span-2"><span>{{ $bn ? 'ক্যাপ্টেন / প্রদর্শিত নাম' : 'Captain / display name' }}</span><input name="captain_name" maxlength="255" value="{{ $setting?->captain_name }}"></label>
                                <label class="field"><span>{{ $bn ? 'লোকাল কারেন্সি' : 'Local currency' }}</span><input name="local_currency" maxlength="10" value="{{ $setting?->local_currency ?: 'BDT' }}" required></label>
                                <label class="field"><span>{{ $bn ? 'ফরেন কারেন্সি' : 'Foreign currency' }}</span><input name="foreign_currency" maxlength="10" value="{{ $setting?->foreign_currency ?: 'SAR' }}" required></label>
                                @if($isSuperAdmin)<label class="field span-2"><span>{{ $bn ? 'অ্যাপ ভার্সন মেটাডেটা' : 'Application version metadata' }}</span><input name="app_version" maxlength="50" value="{{ $setting?->app_version ?: '1.0.0' }}"></label>@endif
                                <label class="check-field"><input type="checkbox" name="rate_based_mode" value="1" {{ ($setting?->rate_based_mode ?? true) ? 'checked' : '' }}><span>{{ $bn ? 'রেট ভিত্তিক মোড' : 'Rate-based mode' }}</span></label>
                                <label class="check-field"><input type="checkbox" name="supplier_rate_enabled" value="1" {{ ($setting?->supplier_rate_enabled ?? true) ? 'checked' : '' }}><span>{{ $bn ? 'সাপ্লায়ার রেট' : 'Supplier rate' }}</span></label>
                                <label class="check-field"><input type="checkbox" name="wallet_rate_enabled" value="1" {{ ($setting?->wallet_rate_enabled ?? true) ? 'checked' : '' }}><span>{{ $bn ? 'ওয়ালেট রেট' : 'Wallet rate' }}</span></label>
                                <div class="form-actions"><button class="button primary" type="submit">{{ $bn ? 'সিস্টেম সেটিংস সংরক্ষণ' : 'Save system settings' }}</button></div>
                            </form>
                        </div>
                        <div class="card branding-card">
                            <div class="card-title"><h3>{{ $bn ? 'লোগো' : 'Logo' }}</h3></div>
                            <div class="logo-preview"><img id="settings-logo-preview" class="brand-logo" src="{{ $logoSource }}" alt="{{ $setting?->app_name ?: 'SAFA' }}"><div><strong class="brand-name">{{ $setting?->app_name ?: 'SAFA' }}</strong><span class="brand-caption">{{ $setting?->captain_name ?: ($bn ? 'আর্থিক ব্যবস্থাপনা' : 'Financial Operations') }}</span></div></div>
                            <form id="logo-form" class="stack-form" enctype="multipart/form-data"><label><span>{{ $bn ? 'PNG/JPG/GIF/WEBP, সর্বোচ্চ 2 MB' : 'PNG/JPG/GIF/WEBP, max 2 MB' }}</span><input type="file" name="logo" accept="image/png,image/jpeg,image/gif,image/webp" required></label><button class="button primary" type="submit">{{ $bn ? 'লোগো আপলোড' : 'Upload logo' }}</button></form>
                        </div>
                    </div>
                </div>
                @endif
            </section>

            @if($canManageUsers)
            <section class="panel" data-panel="users">
                <div class="panel-heading"><div><h2>{{ $bn ? 'ইউজার ম্যানেজমেন্ট' : 'User Management' }}</h2><p>{{ $bn ? 'নিজের নিচের স্তরের ইউজার তৈরি, সক্রিয়/নিষ্ক্রিয় এবং পিন রিসেট করুন।' : 'Create, activate/deactivate, and reset credentials for users below your role.' }}</p></div></div>
                <div class="card"><form id="user-form" class="form-grid"><label class="field"><span>{{ $bn ? 'নাম' : 'Name' }}</span><input name="name" maxlength="255" required></label><label class="field"><span>{{ $bn ? 'মোবাইল' : 'Mobile' }}</span><input name="mobile" maxlength="30" required></label><label class="field"><span>{{ $bn ? 'ইমেইল' : 'Email' }}</span><input name="email" type="email" maxlength="255"></label><label class="field"><span>{{ $bn ? 'ভূমিকা' : 'Role' }}</span><select name="role">@if($user->isSuperAdmin())<option value="admin">Admin</option>@endif<option value="manager">{{ $bn ? 'বিজনেস ইউজার' : 'Business User' }}</option><option value="user">{{ $bn ? 'নরমাল ইউজার' : 'Normal User' }}</option></select></label><label class="field"><span>{{ $bn ? '৬-ডিজিট পিন' : '6-digit PIN' }}</span><input name="pin" minlength="6" maxlength="6" inputmode="numeric" required></label><label class="check-field"><input type="checkbox" name="is_activated" value="1" checked><span>{{ $bn ? 'সক্রিয়' : 'Active' }}</span></label><div class="form-actions"><button class="button primary" type="submit">{{ $bn ? 'ইউজার তৈরি' : 'Create user' }}</button></div></form></div>
                <div class="card"><div class="table-wrap"><table><thead><tr><th>{{ $bn ? 'নাম' : 'Name' }}</th><th>{{ $bn ? 'মোবাইল' : 'Mobile' }}</th><th>{{ $bn ? 'ইমেইল' : 'Email' }}</th><th>{{ $bn ? 'ভূমিকা' : 'Role' }}</th><th>{{ $bn ? 'অবস্থা' : 'Status' }}</th><th></th></tr></thead><tbody id="users-body"></tbody></table></div></div>
            </section>
            @endif
        </main>
    </div>

    <nav class="mobile-bottom-nav" aria-label="{{ $bn ? 'মোবাইল নেভিগেশন' : 'Mobile navigation' }}">
        <button class="nav-button active" type="button" data-section="dashboard">{{ $bn ? 'হোম' : 'Home' }}</button>
        @if($canCustomers)<button class="nav-button" type="button" data-section="customers">{{ $bn ? 'কাস্টমার' : 'Customers' }}</button>@endif
        @if($canSuppliers)<button class="nav-button" type="button" data-section="suppliers">{{ $bn ? 'সাপ্লায়ার' : 'Suppliers' }}</button>@endif
        @if($canExpenses)<button class="nav-button" type="button" data-section="expenses">{{ $bn ? 'আয়/ব্যয়' : 'Income/Expense' }}</button>@endif
        <button class="nav-button" type="button" data-section="settings">{{ $bn ? 'সেটিংস' : 'Settings' }}</button>
    </nav>
</div>
<div class="toast-region" id="toast-region" aria-live="polite" aria-atomic="true"></div>
</body>
</html>
