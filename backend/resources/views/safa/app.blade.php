@php($bn = $language === 'bn')
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
    data-customers-url="{{ route('safa.web.customers') }}"
    data-suppliers-url="{{ route('safa.web.suppliers') }}"
    data-transactions-url="{{ route('safa.web.transactions') }}"
    data-wallet-ledgers-url="{{ route('safa.web.wallet-ledgers') }}"
    data-wallet-batches-url="{{ route('safa.web.wallet-batches') }}"
    data-supplier-deposits-url="{{ route('safa.web.supplier-deposits') }}"
    data-expenses-url="{{ route('safa.web.expenses') }}"
    data-config-url="{{ route('safa.web.config') }}"
    data-logo-url="{{ route('safa.web.logo') }}"
    data-users-url="{{ route('safa.web.users') }}"
>
    <aside class="sidebar" id="sidebar" aria-label="{{ $bn ? 'প্রধান নেভিগেশন' : 'Primary navigation' }}">
        <div class="brand-row">
            <img src="{{ $setting?->app_logo_url ?: url('/safa-logo.png') }}" alt="">
            <div>
                <strong>{{ $setting?->app_name ?: 'SAFA' }}</strong>
                <span>{{ $bn ? 'আর্থিক ব্যবস্থাপনা' : 'Financial Operations' }}</span>
            </div>
        </div>

        <nav class="sidebar-nav">
            <button class="nav-button active" type="button" data-section="dashboard">{{ $bn ? 'ড্যাশবোর্ড' : 'Dashboard' }}</button>
            @if($permissions['can_view_customers'] ?? false)
                <button class="nav-button" type="button" data-section="customers">{{ $bn ? 'কাস্টমার' : 'Customers' }}</button>
            @endif
            @if($permissions['can_view_suppliers'] ?? false)
                <button class="nav-button" type="button" data-section="suppliers">{{ $bn ? 'সাপ্লায়ার' : 'Suppliers' }}</button>
            @endif
            @if($permissions['can_view_transactions'] ?? false)
                <button class="nav-button" type="button" data-section="transactions">{{ $bn ? 'লেনদেন' : 'Transactions' }}</button>
            @endif
            @if($permissions['can_manage_wallet'] ?? false)
                <button class="nav-button" type="button" data-section="wallet">{{ $bn ? 'ওয়ালেট' : 'Wallet' }}</button>
            @endif
            @if($permissions['can_manage_expenses'] ?? false)
                <button class="nav-button" type="button" data-section="expenses">{{ $bn ? 'দৈনিক আয়/ব্যয়' : 'Daily Income / Expense' }}</button>
            @endif
            @if($permissions['can_view_reports'] ?? false)
                <button class="nav-button" type="button" data-section="reports">{{ $bn ? 'রিপোর্ট' : 'Reports' }}</button>
            @endif
            @if($canManageBranding)
                <button class="nav-button" type="button" data-section="settings">{{ $bn ? 'সেটিংস' : 'Settings' }}</button>
            @endif
            @if($canManageUsers)
                <button class="nav-button" type="button" data-section="users">{{ $bn ? 'ইউজার ম্যানেজমেন্ট' : 'User Management' }}</button>
            @endif
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
                <h1>{{ $bn ? 'SAFA ওয়েব ওয়ার্কস্পেস' : 'SAFA Web Workspace' }}</h1>
                <p>{{ $bn ? 'সার্ভার-নিয়ন্ত্রিত অ্যাকাউন্ট ও অনুমতি' : 'Server-authoritative account and permission context' }}</p>
            </div>
            <div class="topbar-actions">
                <label class="account-select">
                    <span class="hidden">{{ $bn ? 'ব্যবসার অ্যাকাউন্ট' : 'Business account' }}</span>
                    <select id="account-select" {{ count($accounts) === 0 ? 'disabled' : '' }} aria-label="{{ $bn ? 'ব্যবসার অ্যাকাউন্ট নির্বাচন' : 'Select business account' }}">
                        @if(!$activeAccountId && count($accounts) > 1)
                            <option value="">{{ $bn ? 'অ্যাকাউন্ট নির্বাচন করুন' : 'Select account' }}</option>
                        @endif
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
                    <div>
                        <h2>{{ $bn ? 'ড্যাশবোর্ড' : 'Dashboard' }}</h2>
                        <p>{{ $bn ? 'বর্তমান অ্যাকাউন্টের দ্রুত সারাংশ' : 'A live summary of the selected account.' }}</p>
                    </div>
                    <button class="button secondary" type="button" data-action="refresh-all">{{ $bn ? 'রিফ্রেশ' : 'Refresh' }}</button>
                </div>
                <div class="stats-grid">
                    @if($permissions['can_view_customers'] ?? false)<article class="stat-card"><span>{{ $bn ? 'কাস্টমার' : 'Customers' }}</span><strong id="stat-customers">—</strong></article>@endif
                    @if($permissions['can_view_suppliers'] ?? false)<article class="stat-card"><span>{{ $bn ? 'সাপ্লায়ার' : 'Suppliers' }}</span><strong id="stat-suppliers">—</strong></article>@endif
                    @if($permissions['can_view_transactions'] ?? false)<article class="stat-card"><span>{{ $bn ? 'লেনদেন' : 'Transactions' }}</span><strong id="stat-transactions">—</strong></article>@endif
                    @if($permissions['can_manage_expenses'] ?? false)<article class="stat-card"><span>{{ $bn ? 'আয়/ব্যয় এন্ট্রি' : 'Income / Expense entries' }}</span><strong id="stat-expenses">—</strong></article>@endif
                </div>
                <div class="card">
                    <div class="card-title"><h3>{{ $bn ? 'নিরাপত্তা ও ভূমিকা' : 'Security & role' }}</h3></div>
                    <p class="muted">{{ $bn ? "আপনি {$roleLabel} হিসেবে লগইন করেছেন। নেভিগেশন এবং প্রতিটি সার্ভার অনুরোধ একই অনুমতি নীতি অনুসরণ করে।" : "You are signed in as {$roleLabel}. Navigation and every server request follow the same permission policy." }}</p>
                </div>
            </section>

            @if($permissions['can_view_customers'] ?? false)
            <section class="panel" data-panel="customers">
                <div class="panel-heading"><div><h2>{{ $bn ? 'কাস্টমার' : 'Customers' }}</h2><p>{{ $bn ? 'কাস্টমার রেকর্ড তৈরি, সম্পাদনা ও পরিচালনা করুন।' : 'Create, update, and manage customer records.' }}</p></div></div>
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
            </section>
            @endif

            @if($permissions['can_view_suppliers'] ?? false)
            <section class="panel" data-panel="suppliers">
                <div class="panel-heading"><div><h2>{{ $bn ? 'সাপ্লায়ার' : 'Suppliers' }}</h2><p>{{ $bn ? 'অনুমোদিত ব্যবসার সাপ্লায়ার তালিকা।' : 'Suppliers for the selected business account.' }}</p></div></div>
                @if($permissions['can_add_suppliers'] ?? false)
                <div class="card">
                    <div class="card-title"><h3>{{ $bn ? 'নতুন সাপ্লায়ার' : 'Add supplier' }}</h3></div>
                    <form class="resource-form form-grid" data-resource="suppliers">
                        <label class="field"><span>{{ $bn ? 'নাম' : 'Name' }}</span><input name="name" maxlength="255" required></label>
                        <label class="field"><span>{{ $bn ? 'ফোন' : 'Phone' }}</span><input name="phone" maxlength="50" inputmode="tel"></label>
                        <label class="field span-2"><span>{{ $bn ? 'ঠিকানা' : 'Address' }}</span><input name="address" maxlength="500"></label>
                        <div class="form-actions"><button class="button primary" type="submit">{{ $bn ? 'সংরক্ষণ' : 'Save supplier' }}</button></div>
                    </form>
                </div>
                @endif
                <div class="card"><div class="table-wrap"><table><thead><tr><th>{{ $bn ? 'নাম' : 'Name' }}</th><th>{{ $bn ? 'ফোন' : 'Phone' }}</th><th>{{ $bn ? 'ঠিকানা' : 'Address' }}</th><th></th></tr></thead><tbody id="suppliers-body"><tr><td class="empty-cell" colspan="4">{{ $bn ? 'লোড হচ্ছে…' : 'Loading…' }}</td></tr></tbody></table></div></div>
            </section>
            @endif

            @if($permissions['can_view_transactions'] ?? false)
            <section class="panel" data-panel="transactions">
                <div class="panel-heading"><div><h2>{{ $bn ? 'লেনদেন' : 'Transactions' }}</h2><p>{{ $bn ? 'নির্ভুল দশমিক মান সহ সার্ভার-নিয়ন্ত্রিত রেমিট্যান্স লেনদেন।' : 'Server-authoritative remittance transactions with exact decimal values.' }}</p></div></div>
                @if($permissions['can_add_transactions'] ?? false)
                <div class="card">
                    <div class="card-title"><h3>{{ $bn ? 'নতুন লেনদেন' : 'Add transaction' }}</h3></div>
                    <form class="resource-form form-grid" data-resource="transactions">
                        <label class="field"><span>{{ $bn ? 'কাস্টমার ID' : 'Customer ID' }}</span><input name="customer_id" inputmode="numeric"></label>
                        <label class="field"><span>{{ $bn ? 'সাপ্লায়ার ID' : 'Supplier ID' }}</span><input name="supplier_id" inputmode="numeric"></label>
                        <label class="field"><span>{{ $bn ? 'SAR পরিমাণ' : 'SAR amount' }}</span><input name="amount_sar" inputmode="decimal" value="0.00"></label>
                        <label class="field"><span>{{ $bn ? 'BDT পরিমাণ' : 'BDT amount' }}</span><input name="amount_bdt" inputmode="decimal" value="0.00"></label>
                        <label class="field"><span>{{ $bn ? 'কাস্টমার রেট' : 'Customer rate' }}</span><input name="customer_rate" inputmode="decimal" value="0.0000"></label>
                        <label class="field"><span>{{ $bn ? 'সাপ্লায়ার রেট' : 'Supplier rate' }}</span><input name="supplier_rate" inputmode="decimal" value="0.0000"></label>
                        <label class="field"><span>{{ $bn ? 'রিসিভারের নাম' : 'Receiver name' }}</span><input name="receiver_name" maxlength="255"></label>
                        <label class="field"><span>{{ $bn ? 'রিসিভারের ফোন' : 'Receiver phone' }}</span><input name="receiver_phone" maxlength="50"></label>
                        <label class="field span-4"><span>{{ $bn ? 'নোট' : 'Notes' }}</span><textarea name="notes" maxlength="5000"></textarea></label>
                        <div class="form-actions"><button class="button primary" type="submit">{{ $bn ? 'লেনদেন সংরক্ষণ' : 'Save transaction' }}</button></div>
                    </form>
                </div>
                @endif
                <div class="card"><div class="table-wrap"><table><thead><tr><th>ID</th><th>{{ $bn ? 'SAR' : 'SAR' }}</th><th>{{ $bn ? 'BDT' : 'BDT' }}</th><th>{{ $bn ? 'রেট' : 'Rates' }}</th><th>{{ $bn ? 'রিসিভার' : 'Receiver' }}</th><th></th></tr></thead><tbody id="transactions-body"><tr><td class="empty-cell" colspan="6">{{ $bn ? 'লোড হচ্ছে…' : 'Loading…' }}</td></tr></tbody></table></div></div>
            </section>
            @endif

            @if($permissions['can_manage_wallet'] ?? false)
            <section class="panel" data-panel="wallet">
                <div class="panel-heading"><div><h2>{{ $bn ? 'ওয়ালেট' : 'Wallet' }}</h2><p>{{ $bn ? 'ওয়ালেট লেজার, ব্যাচ এবং সাপ্লায়ার ডিপোজিট।' : 'Wallet ledgers, batches, and supplier deposits.' }}</p></div></div>
                <div class="section-tabs">
                    <button type="button" class="section-tab active" data-subsection="wallet-ledgers">{{ $bn ? 'লেজার' : 'Ledgers' }}</button>
                    <button type="button" class="section-tab" data-subsection="wallet-batches">{{ $bn ? 'ব্যাচ' : 'Batches' }}</button>
                    <button type="button" class="section-tab" data-subsection="supplier-deposits">{{ $bn ? 'ডিপোজিট' : 'Supplier deposits' }}</button>
                </div>
                <div class="subpanel active" data-subpanel="wallet-ledgers">
                    <div class="card"><form class="resource-form form-grid" data-resource="wallet-ledgers"><label class="field span-2"><span>{{ $bn ? 'লেজারের নাম' : 'Ledger name' }}</span><input name="name" maxlength="255" required></label><div class="form-actions"><button class="button primary" type="submit">{{ $bn ? 'লেজার যোগ করুন' : 'Add ledger' }}</button></div></form></div>
                    <div class="card"><div class="table-wrap"><table><thead><tr><th>ID</th><th>{{ $bn ? 'নাম' : 'Name' }}</th><th></th></tr></thead><tbody id="wallet-ledgers-body"><tr><td class="empty-cell" colspan="3">{{ $bn ? 'লোড হচ্ছে…' : 'Loading…' }}</td></tr></tbody></table></div></div>
                </div>
                <div class="subpanel" data-subpanel="wallet-batches">
                    <div class="card"><form class="resource-form form-grid" data-resource="wallet-batches"><label class="field"><span>{{ $bn ? 'লেজার ID' : 'Ledger ID' }}</span><input name="ledger_id" inputmode="numeric"></label><label class="field"><span>{{ $bn ? 'রেট' : 'Rate' }}</span><input name="rate" inputmode="decimal" value="0.0000" required></label><label class="field"><span>{{ $bn ? 'প্রাথমিক BDT' : 'Initial BDT' }}</span><input name="initial_bdt" inputmode="decimal" value="0.00" required></label><label class="field"><span>{{ $bn ? 'অবশিষ্ট BDT' : 'Remaining BDT' }}</span><input name="remaining_bdt" inputmode="decimal" value="0.00" required></label><label class="field"><span>{{ $bn ? 'সাপ্লায়ার ID' : 'Supplier ID' }}</span><input name="supplier_id" inputmode="numeric"></label><label class="field"><span>{{ $bn ? 'ডিপোজিট ID' : 'Deposit ID' }}</span><input name="supplier_deposit_id" inputmode="numeric"></label><label class="field span-2"><span>{{ $bn ? 'নোট' : 'Notes' }}</span><input name="notes" maxlength="10000"></label><div class="form-actions"><button class="button primary" type="submit">{{ $bn ? 'ব্যাচ যোগ করুন' : 'Add batch' }}</button></div></form></div>
                    <div class="card"><div class="table-wrap"><table><thead><tr><th>ID</th><th>{{ $bn ? 'রেট' : 'Rate' }}</th><th>{{ $bn ? 'প্রাথমিক' : 'Initial' }}</th><th>{{ $bn ? 'অবশিষ্ট' : 'Remaining' }}</th><th></th></tr></thead><tbody id="wallet-batches-body"><tr><td class="empty-cell" colspan="5">{{ $bn ? 'লোড হচ্ছে…' : 'Loading…' }}</td></tr></tbody></table></div></div>
                </div>
                <div class="subpanel" data-subpanel="supplier-deposits">
                    <div class="card"><form class="resource-form form-grid" data-resource="supplier-deposits"><label class="field"><span>{{ $bn ? 'সাপ্লায়ার ID' : 'Supplier ID' }}</span><input name="supplier_id" inputmode="numeric"></label><label class="field"><span>{{ $bn ? 'SAR পরিমাণ' : 'SAR amount' }}</span><input name="amount_sar" inputmode="decimal" value="0.00" required></label><label class="field"><span>{{ $bn ? 'রেট' : 'Rate' }}</span><input name="rate" inputmode="decimal" value="0.0000" required></label><label class="field"><span>{{ $bn ? 'BDT পরিমাণ' : 'BDT amount' }}</span><input name="amount_bdt" inputmode="decimal" value="0.00" required></label><label class="field"><span>{{ $bn ? 'পরিশোধিত BDT' : 'Paid BDT' }}</span><input name="paid_bdt" inputmode="decimal" value="0.00"></label><label class="field"><span>{{ $bn ? 'ধরন' : 'Type' }}</span><select name="transaction_type"><option value="SAR_GIVEN">SAR_GIVEN</option><option value="BDT_PAID">BDT_PAID</option></select></label><label class="field span-2"><span>{{ $bn ? 'নোট' : 'Notes' }}</span><input name="notes" maxlength="10000"></label><div class="form-actions"><button class="button primary" type="submit">{{ $bn ? 'ডিপোজিট যোগ করুন' : 'Add deposit' }}</button></div></form></div>
                    <div class="card"><div class="table-wrap"><table><thead><tr><th>ID</th><th>{{ $bn ? 'সাপ্লায়ার' : 'Supplier' }}</th><th>SAR</th><th>BDT</th><th>{{ $bn ? 'পরিশোধিত' : 'Paid' }}</th><th></th></tr></thead><tbody id="supplier-deposits-body"><tr><td class="empty-cell" colspan="6">{{ $bn ? 'লোড হচ্ছে…' : 'Loading…' }}</td></tr></tbody></table></div></div>
                </div>
            </section>
            @endif

            @if($permissions['can_manage_expenses'] ?? false)
            <section class="panel" data-panel="expenses">
                <div class="panel-heading"><div><h2>{{ $bn ? 'দৈনিক আয় / ব্যয়' : 'Daily Income / Expense' }}</h2><p>{{ $bn ? 'দৈনিক অপারেশনাল আয় ও ব্যয় রেকর্ড করুন।' : 'Record daily operational income and expenses.' }}</p></div></div>
                <div class="card">
                    <form class="resource-form form-grid" data-resource="expenses">
                        <label class="field span-2"><span>{{ $bn ? 'বিবরণ' : 'Title' }}</span><input name="title" maxlength="255" required></label>
                        <label class="field"><span>{{ $bn ? 'পরিমাণ' : 'Amount' }}</span><input name="amount" inputmode="decimal" value="0.00" required></label>
                        <label class="field"><span>{{ $bn ? 'মুদ্রা' : 'Currency' }}</span><select name="currency"><option value="BDT">BDT</option><option value="SAR">SAR</option></select></label>
                        <label class="field"><span>{{ $bn ? 'ক্যাটাগরি' : 'Category' }}</span><input name="category" maxlength="255" value="General"></label>
                        <label class="check-field"><input type="checkbox" name="is_expense" value="1" checked><span>{{ $bn ? 'এটি ব্যয়' : 'Expense entry' }}</span></label>
                        <div class="form-actions"><button class="button primary" type="submit">{{ $bn ? 'এন্ট্রি সংরক্ষণ' : 'Save entry' }}</button></div>
                    </form>
                </div>
                <div class="card"><div class="table-wrap"><table><thead><tr><th>{{ $bn ? 'বিবরণ' : 'Title' }}</th><th>{{ $bn ? 'পরিমাণ' : 'Amount' }}</th><th>{{ $bn ? 'ধরন' : 'Type' }}</th><th>{{ $bn ? 'ক্যাটাগরি' : 'Category' }}</th><th></th></tr></thead><tbody id="expenses-body"><tr><td class="empty-cell" colspan="5">{{ $bn ? 'লোড হচ্ছে…' : 'Loading…' }}</td></tr></tbody></table></div></div>
            </section>
            @endif

            @if($permissions['can_view_reports'] ?? false)
            <section class="panel" data-panel="reports">
                <div class="panel-heading"><div><h2>{{ $bn ? 'রিপোর্ট' : 'Reports' }}</h2><p>{{ $bn ? 'বর্তমান ব্রাউজার সেশনে লোড করা সার্ভার ডেটার সারাংশ।' : 'Summary of server data loaded in the current browser session.' }}</p></div></div>
                <div class="stats-grid">
                    <article class="stat-card"><span>{{ $bn ? 'মোট SAR লেনদেন' : 'Transaction SAR total' }}</span><strong id="report-sar">0.00</strong></article>
                    <article class="stat-card"><span>{{ $bn ? 'মোট BDT লেনদেন' : 'Transaction BDT total' }}</span><strong id="report-bdt">0.00</strong></article>
                    <article class="stat-card"><span>{{ $bn ? 'দৈনিক ব্যয়' : 'Daily expenses' }}</span><strong id="report-expense">0.00</strong></article>
                    <article class="stat-card"><span>{{ $bn ? 'অন্যান্য আয়' : 'Other income' }}</span><strong id="report-income">0.00</strong></article>
                </div>
            </section>
            @endif

            @if($canManageBranding)
            <section class="panel" data-panel="settings">
                <div class="panel-heading"><div><h2>{{ $bn ? 'অ্যাডমিন সেটিংস' : 'Admin Settings' }}</h2><p>{{ $bn ? 'অ্যাপ নাম, লোগো এবং সাধারণ ব্র্যান্ডিং।' : 'Application name, logo, and normal branding controls.' }}</p></div></div>
                <div class="grid-2">
                    <div class="card">
                        <div class="card-title"><h3>{{ $bn ? 'অ্যাপ নাম' : 'Application name' }}</h3></div>
                        <form id="config-form" class="stack-form">
                            <label><span>{{ $bn ? 'ক্যাপশন / নাম' : 'Caption / name' }}</span><input name="app_name" maxlength="255" value="{{ $setting?->app_name ?: 'SAFA' }}" required></label>
                            <button class="button primary" type="submit">{{ $bn ? 'নাম আপডেট' : 'Update name' }}</button>
                        </form>
                    </div>
                    <div class="card">
                        <div class="card-title"><h3>{{ $bn ? 'লোগো' : 'Logo' }}</h3></div>
                        <form id="logo-form" class="stack-form" enctype="multipart/form-data">
                            <label><span>{{ $bn ? 'PNG/JPG/GIF/WEBP, সর্বোচ্চ 2 MB' : 'PNG/JPG/GIF/WEBP, max 2 MB' }}</span><input type="file" name="logo" accept="image/png,image/jpeg,image/gif,image/webp" required></label>
                            <button class="button primary" type="submit">{{ $bn ? 'লোগো আপলোড' : 'Upload logo' }}</button>
                        </form>
                    </div>
                </div>
            </section>
            @endif

            @if($canManageUsers)
            <section class="panel" data-panel="users">
                <div class="panel-heading"><div><h2>{{ $bn ? 'ইউজার ম্যানেজমেন্ট' : 'User Management' }}</h2><p>{{ $bn ? 'নিজের নিচের স্তরের ইউজার তৈরি, সক্রিয়/নিষ্ক্রিয় এবং পিন রিসেট করুন।' : 'Create, activate/deactivate, and reset credentials for users below your role.' }}</p></div></div>
                <div class="card">
                    <form id="user-form" class="form-grid">
                        <label class="field"><span>{{ $bn ? 'নাম' : 'Name' }}</span><input name="name" maxlength="255" required></label>
                        <label class="field"><span>{{ $bn ? 'মোবাইল' : 'Mobile' }}</span><input name="mobile" maxlength="30" required></label>
                        <label class="field"><span>{{ $bn ? 'ইমেইল' : 'Email' }}</span><input name="email" type="email" maxlength="255"></label>
                        <label class="field"><span>{{ $bn ? 'ভূমিকা' : 'Role' }}</span><select name="role">@if($user->isSuperAdmin())<option value="admin">Admin</option>@endif<option value="manager">{{ $bn ? 'বিজনেস ইউজার' : 'Business User' }}</option><option value="user">{{ $bn ? 'নরমাল ইউজার' : 'Normal User' }}</option></select></label>
                        <label class="field"><span>{{ $bn ? '৬-ডিজিট পিন' : '6-digit PIN' }}</span><input name="pin" minlength="6" maxlength="6" inputmode="numeric" required></label>
                        <label class="check-field"><input type="checkbox" name="is_activated" value="1" checked><span>{{ $bn ? 'সক্রিয়' : 'Active' }}</span></label>
                        <div class="form-actions"><button class="button primary" type="submit">{{ $bn ? 'ইউজার তৈরি' : 'Create user' }}</button></div>
                    </form>
                </div>
                <div class="card"><div class="table-wrap"><table><thead><tr><th>{{ $bn ? 'নাম' : 'Name' }}</th><th>{{ $bn ? 'মোবাইল' : 'Mobile' }}</th><th>{{ $bn ? 'ইমেইল' : 'Email' }}</th><th>{{ $bn ? 'ভূমিকা' : 'Role' }}</th><th>{{ $bn ? 'অবস্থা' : 'Status' }}</th><th></th></tr></thead><tbody id="users-body"><tr><td class="empty-cell" colspan="6">{{ $bn ? 'লোড হচ্ছে…' : 'Loading…' }}</td></tr></tbody></table></div></div>
            </section>
            @endif
        </main>
    </div>

    <nav class="mobile-bottom-nav" aria-label="{{ $bn ? 'মোবাইল নেভিগেশন' : 'Mobile navigation' }}">
        <button class="nav-button active" type="button" data-section="dashboard">{{ $bn ? 'হোম' : 'Home' }}</button>
        @if($permissions['can_view_customers'] ?? false)<button class="nav-button" type="button" data-section="customers">{{ $bn ? 'কাস্টমার' : 'Customers' }}</button>@endif
        @if($permissions['can_view_suppliers'] ?? false)<button class="nav-button" type="button" data-section="suppliers">{{ $bn ? 'সাপ্লায়ার' : 'Suppliers' }}</button>@endif
        @if($permissions['can_manage_expenses'] ?? false)<button class="nav-button" type="button" data-section="expenses">{{ $bn ? 'আয়/ব্যয়' : 'Income/Expense' }}</button>@endif
    </nav>
</div>
<div class="toast-region" id="toast-region" aria-live="polite" aria-atomic="true"></div>
</body>
</html>
