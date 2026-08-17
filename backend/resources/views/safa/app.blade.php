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
    $appName = $setting?->app_name ?: 'SAFA';
    $userInitial = strtoupper(substr((string) ($user->name ?: 'U'), 0, 1));
@endphp
<!doctype html>
<html lang="{{ $language }}" dir="ltr">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
    <meta name="color-scheme" content="light dark">
    <meta name="csrf-token" content="{{ csrf_token() }}">
    <title>{{ $appName }}</title>
    <link rel="icon" href="{{ url('/favicon.svg') }}" type="image/svg+xml">
    <link rel="stylesheet" href="{{ url('/safa-web.css') }}">
    <script src="{{ url('/safa-web.js') }}" defer></script>
    <script src="{{ url('/safa-web-product.js') }}" defer></script>
</head>
<body class="app-body">
<div id="safa-app"
    data-language="{{ $language }}"
    data-active-account="{{ $activeAccountId ?: '' }}"
    data-workspace-url="{{ route('safa.web.mobile.workspace') }}"
    data-account-switch-url="{{ route('safa.web.account.switch') }}"
    data-customers-url="{{ $canCustomers ? route('safa.web.customers') : '' }}"
    data-suppliers-url="{{ $canSuppliers ? route('safa.web.suppliers') : '' }}"
    data-expenses-url="{{ $canExpenses ? route('safa.web.expenses') : '' }}"
    data-customer-sale-url="{{ $canAddTransactions ? route('safa.web.mobile.customer-sale') : '' }}"
    data-customer-adjustment-url="{{ $canAddTransactions ? route('safa.web.mobile.customer-adjustment') : '' }}"
    data-mobile-transactions-url="{{ $canTransactions ? url('/app/api/mobile/transactions') : '' }}"
    data-supplier-funds-url="{{ $canWallet ? route('safa.web.mobile.supplier-fund') : '' }}"
    data-wallet-ledgers-action-url="{{ $canWallet ? route('safa.web.mobile.wallet-ledger.create') : '' }}"
    data-wallet-deposit-url="{{ $canWallet ? route('safa.web.mobile.wallet-deposit') : '' }}"
    data-wallet-withdraw-url="{{ $canWallet ? route('safa.web.mobile.wallet-withdraw') : '' }}"
    data-profile-settings-url="{{ route('safa.web.settings.profile') }}"
    data-personal-settings-url="{{ route('safa.web.settings.personal') }}"
    data-pin-settings-url="{{ route('safa.web.settings.pin') }}"
    data-config-url="{{ $canManageSystemSettings ? route('safa.web.config') : '' }}"
    data-logo-url="{{ $canManageSystemSettings ? route('safa.web.logo') : '' }}"
    data-users-url="{{ $canManageUsers ? route('safa.web.users') : '' }}"
    data-can-add-transactions="{{ $canAddTransactions ? '1' : '0' }}"
    data-can-edit-transactions="{{ $canEditTransactions ? '1' : '0' }}"
    data-can-delete-transactions="{{ $canDeleteTransactions ? '1' : '0' }}"
    data-can-manage-wallet="{{ $canWallet ? '1' : '0' }}"
    data-can-manage-users="{{ $canManageUsers ? '1' : '0' }}"
    data-can-manage-system="{{ $canManageSystemSettings ? '1' : '0' }}"
    data-is-superadmin="{{ $isSuperAdmin ? '1' : '0' }}"
    class="safa-app-shell mobile-app-shell">

    <aside class="app-sidebar" aria-label="{{ $bn ? 'অ্যাপ নেভিগেশন' : 'Application navigation' }}">
        <div class="sidebar-brand">
            <button class="brand-trigger" type="button" data-open-settings aria-label="{{ $bn ? 'সেটিংস খুলুন' : 'Open settings' }}">
                <img class="brand-logo" src="{{ $logoSource }}" alt="">
                <span class="brand-copy"><strong class="brand-name">{{ $appName }}</strong><small>{{ $bn ? 'ব্যবসা ব্যবস্থাপনা' : 'Business workspace' }}</small></span>
            </button>
        </div>

        <nav class="mobile-bottom-nav app-navigation" aria-label="{{ $bn ? 'প্রধান নেভিগেশন' : 'Primary navigation' }}">
            <button class="bottom-nav-item active" type="button" data-nav="dashboard"><span class="icon icon-home" aria-hidden="true"></span><small>{{ $bn ? 'হোম' : 'Home' }}</small></button>
            @if($canCustomers)<button class="bottom-nav-item" type="button" data-nav="customers"><span class="icon icon-people" aria-hidden="true"></span><small>{{ $bn ? 'কাস্টমার' : 'Customers' }}</small></button>@endif
            @if($canSuppliers)<button class="bottom-nav-item" type="button" data-nav="suppliers"><span class="icon icon-supplier" aria-hidden="true"></span><small>{{ $bn ? 'সাপ্লায়ার' : 'Suppliers' }}</small></button>@endif
            @if($canWallet)<button class="bottom-nav-item" type="button" data-nav="wallet"><span class="icon icon-wallet" aria-hidden="true"></span><small>{{ $bn ? 'ওয়ালেট' : 'Wallet' }}</small></button>@endif
            @if($canExpenses)<button class="bottom-nav-item" type="button" data-nav="expenses"><span class="icon icon-payments" aria-hidden="true"></span><small>{{ $bn ? 'আয়/ব্যয়' : 'Expenses' }}</small></button>@endif
        </nav>

        <div class="sidebar-footer">
            <div class="sidebar-user">
                <span class="avatar">{{ $userInitial }}</span>
                <span><strong id="sidebar-user-name">{{ $user->name }}</strong><small>{{ $roleLabel }}</small></span>
            </div>
        </div>
    </aside>

    <div class="app-workspace">
        <header class="mobile-topbar app-topbar">
            <button class="brand-trigger" type="button" data-open-settings aria-label="{{ $bn ? 'সেটিংস' : 'Open settings' }}">
                <img class="brand-logo" src="{{ $logoSource }}" alt="">
                <span class="brand-copy"><strong class="brand-name">{{ $appName }}</strong><small id="signed-user-name">{{ $user->name }}</small></span>
            </button>

            <div class="desktop-topbar-context">
                <strong>{{ $bn ? 'ব্যবসার ওয়ার্কস্পেস' : 'Business workspace' }}</strong>
                <small>{{ $roleLabel }} · {{ $user->name }}</small>
            </div>

            <div class="topbar-actions">
                <label class="account-select">
                    <span class="sr-only">{{ $bn ? 'ব্যবসার অ্যাকাউন্ট' : 'Business account' }}</span>
                    <select id="account-select" {{ count($accounts) === 0 ? 'disabled' : '' }}>
                        @if(!$activeAccountId && count($accounts) > 1)<option value="">{{ $bn ? 'অ্যাকাউন্ট' : 'Account' }}</option>@endif
                        @foreach($accounts as $account)
                            <option value="{{ (int) $account['account_id'] }}" {{ (int)$activeAccountId === (int)$account['account_id'] ? 'selected' : '' }}>{{ $account['owner_name'] ?: 'Account' }} · #{{ (int)$account['account_id'] }}</option>
                        @endforeach
                    </select>
                </label>
                <button class="icon-button" type="button" id="theme-toggle" title="{{ $bn ? 'থিম' : 'Theme' }}" aria-label="{{ $bn ? 'থিম পরিবর্তন' : 'Switch theme' }}"><span class="icon icon-theme" aria-hidden="true"></span></button>
                <button class="icon-button" type="button" data-open-settings title="{{ $bn ? 'সেটিংস' : 'Settings' }}" aria-label="{{ $bn ? 'সেটিংস' : 'Settings' }}"><span class="icon icon-settings" aria-hidden="true"></span></button>
            </div>
        </header>

        <main class="mobile-content app-content" id="mobile-content">
            <div id="global-message" class="notice {{ $activeAccountId ? 'hidden' : '' }}">{{ $bn ? 'ব্যবসার ডেটা দেখতে একটি অ্যাকাউন্ট নির্বাচন করুন।' : 'Select an account to load business data.' }}</div>

            <section class="screen active dashboard-screen" data-screen="dashboard">
                <div class="screen-title">
                    <div class="screen-heading"><span class="screen-icon"><span class="icon icon-home" aria-hidden="true"></span></span><div><p class="eyebrow">{{ $bn ? 'SAFA ওয়ার্কস্পেস' : 'SAFA workspace' }}</p><h1>{{ $bn ? 'ড্যাশবোর্ড' : 'Dashboard' }}</h1></div></div>
                    <button class="compact-button" type="button" data-action="refresh"><span class="icon icon-refresh" aria-hidden="true"></span>{{ $bn ? 'রিফ্রেশ' : 'Refresh' }}</button>
                </div>

                <section class="dashboard-shortcuts" aria-label="{{ $bn ? 'দ্রুত অ্যাকশন' : 'Quick actions' }}">
                    <div class="shortcut-grid">
                        @if($canCustomers)<button class="shortcut-action red" type="button" data-nav="customers"><span class="shortcut-badge"><span class="icon icon-people" aria-hidden="true"></span></span><span>{{ $bn ? 'গ্রাহকগণ' : 'Customers' }}</span></button>@endif
                        @if($canExpenses)<button class="shortcut-action orange" type="button" data-nav="expenses"><span class="shortcut-badge"><span class="icon icon-payments" aria-hidden="true"></span></span><span>{{ $bn ? 'আয়/ব্যয়' : 'Income/Expense' }}</span></button>@endif
                        @if($canSuppliers)<button class="shortcut-action green" type="button" data-nav="suppliers"><span class="shortcut-badge"><span class="icon icon-supplier" aria-hidden="true"></span></span><span>{{ $bn ? 'সাপ্লায়ার' : 'Suppliers' }}</span></button>@endif
                        <button class="shortcut-action pink" type="button" data-open-settings><span class="shortcut-badge"><span class="icon icon-settings" aria-hidden="true"></span></span><span>{{ $bn ? 'আজকের রেট' : 'Exchange Rates' }}</span></button>
                        @if($canWallet)<button class="shortcut-action yellow" type="button" data-nav="wallet"><span class="shortcut-badge"><span class="icon icon-wallet" aria-hidden="true"></span></span><span>{{ $bn ? 'রিয়াল স্টক' : 'Riyal Stock' }}</span></button>@endif
                        <a class="shortcut-action teal" href="#dashboard-overview-card"><span class="shortcut-badge"><span class="icon icon-home" aria-hidden="true"></span></span><span>{{ $bn ? 'ব্যবসার হিসাব' : 'Overview' }}</span></a>
                        <button class="shortcut-action indigo" type="button" data-action="refresh"><span class="shortcut-badge"><span class="icon icon-sync" aria-hidden="true"></span></span><span>{{ $bn ? 'ডেটা সিঙ্ক' : 'Sync Data' }}</span></button>
                        <button class="shortcut-action brown" type="button" data-open-settings><span class="shortcut-badge"><span class="icon icon-user" aria-hidden="true"></span></span><span>{{ $bn ? 'আমার অ্যাকাউন্ট' : 'My Account' }}</span></button>
                    </div>
                </section>

                <div id="dashboard-stats" class="metric-grid"></div>
                <div class="surface-card" id="dashboard-overview-card"><div class="card-heading"><div><h2>{{ $bn ? 'আজকের কার্যক্রম' : 'Business overview' }}</h2><p>{{ $bn ? 'আপনার বর্তমান ব্যবসার সংক্ষিপ্ত অবস্থা' : 'Current operational snapshot for this account' }}</p></div></div><div id="dashboard-overview" class="overview-grid"></div></div>
            </section>

            @if($canCustomers)
            <section class="screen" data-screen="customers">
                <div class="screen-title"><div class="screen-heading"><span class="screen-icon"><span class="icon icon-people" aria-hidden="true"></span></span><div><p class="eyebrow">{{ $bn ? 'কাস্টমার তালিকা' : 'Customer directory' }}</p><h1>{{ $bn ? 'কাস্টমার' : 'Customers' }}</h1></div></div>@if($permissions['can_add_customers'] ?? false)<button class="primary-button" type="button" data-open-create="customer"><span class="icon icon-add" aria-hidden="true"></span>{{ $bn ? 'নতুন' : 'Add' }}</button>@endif</div>
                <div class="tool-row"><label class="search-box"><span class="icon icon-search" aria-hidden="true"></span><input id="customer-search" type="search" placeholder="{{ $bn ? 'নাম, ফোন বা ঠিকানা খুঁজুন' : 'Search name, phone or address' }}"></label><select id="customer-filter"><option value="all">{{ $bn ? 'সব কাস্টমার' : 'All customers' }}</option><option value="due">{{ $bn ? 'বকেয়া' : 'Due' }}</option><option value="advance">{{ $bn ? 'অগ্রিম' : 'Advance' }}</option></select><select id="customer-sort"><option value="newest">{{ $bn ? 'নতুন প্রথম' : 'Newest first' }}</option><option value="oldest">{{ $bn ? 'পুরাতন প্রথম' : 'Oldest first' }}</option><option value="name">A–Z</option><option value="due">{{ $bn ? 'বকেয়া অনুযায়ী' : 'By due' }}</option></select></div>
                <div id="customers-list" class="entity-list"></div>
            </section>
            @endif

            @if($canSuppliers)
            <section class="screen" data-screen="suppliers">
                <div class="screen-title"><div class="screen-heading"><span class="screen-icon"><span class="icon icon-supplier" aria-hidden="true"></span></span><div><p class="eyebrow">{{ $bn ? 'সাপ্লায়ার খাতা' : 'Supplier ledgers' }}</p><h1>{{ $bn ? 'সাপ্লায়ার' : 'Suppliers' }}</h1></div></div>@if($permissions['can_add_suppliers'] ?? false)<button class="primary-button" type="button" data-open-create="supplier"><span class="icon icon-add" aria-hidden="true"></span>{{ $bn ? 'নতুন' : 'Add' }}</button>@endif</div>
                <div class="tool-row"><label class="search-box"><span class="icon icon-search" aria-hidden="true"></span><input id="supplier-search" type="search" placeholder="{{ $bn ? 'নাম, ফোন বা ঠিকানা খুঁজুন' : 'Search supplier' }}"></label><select id="supplier-filter"><option value="all">{{ $bn ? 'সব সাপ্লায়ার' : 'All suppliers' }}</option><option value="receivable">{{ $bn ? 'পাওনা' : 'Receivable' }}</option><option value="payable">{{ $bn ? 'বকেয়া' : 'Payable' }}</option></select><select id="supplier-sort"><option value="newest">{{ $bn ? 'নতুন প্রথম' : 'Newest first' }}</option><option value="oldest">{{ $bn ? 'পুরাতন প্রথম' : 'Oldest first' }}</option><option value="name">A–Z</option><option value="balance">{{ $bn ? 'ব্যালেন্স' : 'Balance' }}</option></select></div>
                <div id="suppliers-list" class="entity-list"></div>
            </section>
            @endif

            @if($canWallet)
            <section class="screen" data-screen="wallet">
                <div class="screen-title"><div class="screen-heading"><span class="screen-icon"><span class="icon icon-wallet" aria-hidden="true"></span></span><div><p class="eyebrow">{{ $bn ? 'ওয়ালেট লেজার রেজিস্টার' : 'Wallet ledger registers' }}</p><h1>{{ $bn ? 'ওয়ালেট' : 'Wallet' }}</h1></div></div><button class="primary-button" type="button" data-open-wallet-ledger><span class="icon icon-add" aria-hidden="true"></span>{{ $bn ? 'লেজার' : 'Ledger' }}</button></div>
                <div id="wallet-summary" class="metric-grid"></div>
                <div id="wallet-ledgers-list" class="entity-list"></div>
            </section>
            @endif

            @if($canExpenses)
            <section class="screen" data-screen="expenses">
                <div class="screen-title"><div class="screen-heading"><span class="screen-icon"><span class="icon icon-payments" aria-hidden="true"></span></span><div><p class="eyebrow">{{ $bn ? 'দৈনিক খরচ ও অন্যান্য' : 'Daily operations' }}</p><h1>{{ $bn ? 'আয় / ব্যয়' : 'Income / Expense' }}</h1></div></div><button class="primary-button" type="button" data-open-expense><span class="icon icon-add" aria-hidden="true"></span>{{ $bn ? 'এন্ট্রি' : 'Entry' }}</button></div>
                <div id="expenses-summary" class="metric-grid"></div>
                <div id="expenses-list" class="entity-list"></div>
            </section>
            @endif

            <section class="screen sub-screen" data-screen="settings">
                <div class="screen-title"><div class="screen-heading"><span class="screen-icon"><span class="icon icon-settings" aria-hidden="true"></span></span><div><p class="eyebrow">{{ $roleLabel }}</p><h1>{{ $bn ? 'সেটিংস' : 'Settings' }}</h1></div></div><button class="compact-button" type="button" data-settings-back><span class="icon icon-back" aria-hidden="true"></span>{{ $bn ? 'ফিরুন' : 'Back' }}</button></div>
                <div class="settings-grid">
                    <article class="surface-card">
                        <div class="card-heading"><div><h2>{{ $bn ? 'আমার অ্যাকাউন্ট' : 'My account' }}</h2><p>{{ $bn ? 'আপনার নিজের লগইন পরিচয়' : 'Your signed-in identity' }}</p></div></div>
                        <form id="profile-settings-form" class="stack-form">
                            <label><span>{{ $bn ? 'নাম' : 'Name' }}</span><input name="name" maxlength="255" value="{{ $user->name }}" required></label>
                            <label><span>{{ $bn ? 'মোবাইল নম্বর' : 'Mobile number' }}</span><input name="mobile" maxlength="30" value="{{ $user->mobile }}" inputmode="tel" required></label>
                            <button class="primary-button" type="submit">{{ $bn ? 'আমার তথ্য সংরক্ষণ' : 'Save my profile' }}</button>
                        </form>
                    </article>
                    <article class="surface-card">
                        <div class="card-heading"><div><h2>{{ $bn ? 'ব্যক্তিগত পছন্দ' : 'Personal preferences' }}</h2><p>{{ $bn ? 'ভাষা ও অ্যাপের প্রদর্শন' : 'Language and appearance' }}</p></div></div>
                        <form id="personal-settings-form" class="stack-form"><label><span>{{ $bn ? 'ভাষা' : 'Language' }}</span><select name="language"><option value="en" {{ $language === 'en' ? 'selected' : '' }}>English</option><option value="bn" {{ $language === 'bn' ? 'selected' : '' }}>বাংলা</option></select></label><label><span>{{ $bn ? 'থিম' : 'Appearance' }}</span><select id="appearance-select"><option value="system">{{ $bn ? 'সিস্টেম' : 'System' }}</option><option value="light">{{ $bn ? 'লাইট' : 'Light' }}</option><option value="dark">{{ $bn ? 'ডার্ক' : 'Dark' }}</option></select></label><button class="primary-button" type="submit">{{ $bn ? 'পছন্দ সংরক্ষণ' : 'Save preferences' }}</button></form>
                    </article>
                    <article class="surface-card">
                        <div class="card-heading"><div><h2>{{ $bn ? 'পিন পরিবর্তন' : 'Change PIN' }}</h2><p>{{ $bn ? '৬-ডিজিট নিরাপত্তা পিন' : '6-digit security PIN' }}</p></div></div>
                        <form id="pin-form" class="stack-form"><label><span>{{ $bn ? 'বর্তমান ৬-ডিজিট পিন' : 'Current 6-digit PIN' }}</span><input type="password" name="current_pin" minlength="6" maxlength="6" inputmode="numeric" required></label><label><span>{{ $bn ? 'নতুন পিন' : 'New PIN' }}</span><input type="password" name="new_pin" minlength="6" maxlength="6" inputmode="numeric" required></label><label><span>{{ $bn ? 'নতুন পিন নিশ্চিত করুন' : 'Confirm new PIN' }}</span><input type="password" name="new_pin_confirmation" minlength="6" maxlength="6" inputmode="numeric" required></label><button class="primary-button" type="submit">{{ $bn ? 'পিন পরিবর্তন' : 'Change PIN' }}</button></form>
                    </article>

                    @if($canManageSystemSettings)
                    <article class="surface-card span-2" id="brand-business-config">
                        <div class="card-heading"><div><h2>{{ $bn ? 'ব্র্যান্ড ও ব্যবসা কনফিগারেশন' : 'Brand & Business Configuration' }}</h2><p>{{ $bn ? 'অ্যাপ ব্র্যান্ড, কারেন্সি ও রেট ফিচার' : 'Application branding, currencies and rate features' }}</p></div></div>
                        <form id="config-form" class="form-grid">
                            <label><span>{{ $bn ? 'অ্যাপ নাম' : 'Application name' }}</span><input name="app_name" maxlength="255" value="{{ $appName }}" required></label>
                            <label><span>{{ $bn ? 'লোকাল কারেন্সি' : 'Local currency' }}</span><input name="local_currency" maxlength="10" value="{{ $setting?->local_currency ?: 'BDT' }}" required></label>
                            <label><span>{{ $bn ? 'ফরেন কারেন্সি' : 'Foreign currency' }}</span><input name="foreign_currency" maxlength="10" value="{{ $setting?->foreign_currency ?: 'SAR' }}" required></label>
                            @if($isSuperAdmin)<label><span>{{ $bn ? 'অ্যাপ ভার্সন' : 'App version' }}</span><input name="app_version" maxlength="50" value="{{ $setting?->app_version ?: '1.0.0' }}"></label>@endif
                            <label class="toggle-line"><input type="checkbox" name="rate_based_mode" value="1" {{ ($setting?->rate_based_mode ?? true) ? 'checked' : '' }}><span>{{ $bn ? 'রেট ভিত্তিক মোড' : 'Rate-based mode' }}</span></label>
                            <label class="toggle-line"><input type="checkbox" name="supplier_rate_enabled" value="1" {{ ($setting?->supplier_rate_enabled ?? true) ? 'checked' : '' }}><span>{{ $bn ? 'সাপ্লায়ার রেট' : 'Supplier rate' }}</span></label>
                            <label class="toggle-line"><input type="checkbox" name="wallet_rate_enabled" value="1" {{ ($setting?->wallet_rate_enabled ?? true) ? 'checked' : '' }}><span>{{ $bn ? 'ওয়ালেট রেট' : 'Wallet rate' }}</span></label>
                            <div class="form-actions"><button class="primary-button" type="submit">{{ $bn ? 'কনফিগারেশন সংরক্ষণ' : 'Save configuration' }}</button></div>
                        </form>
                        <form id="logo-form" class="logo-form" enctype="multipart/form-data"><img id="settings-logo-preview" class="logo-preview" src="{{ $logoSource }}" alt=""><label><span>{{ $bn ? 'নতুন লোগো' : 'Upload logo' }}</span><input type="file" name="logo" accept="image/png,image/jpeg,image/gif,image/webp" required></label><button class="secondary-button" type="submit">{{ $bn ? 'লোগো আপলোড' : 'Upload logo' }}</button></form>
                    </article>
                    @endif

                    @if($isSuperAdmin)
                    <article class="surface-card span-2" id="database-update-settings">
                        <div class="card-heading"><div><h2>{{ $bn ? 'ডাটাবেজ আপডেট' : 'Database Update' }}</h2><p>{{ $bn ? 'নতুন মাইগ্রেশন ও অনুমোদিত release data update নিরাপদভাবে চালান' : 'Apply new migrations and approved release data updates safely' }}</p></div></div>
                        <form method="post" action="{{ route('system.update.run') }}" class="stack-form">
                            @csrf
                            <button class="primary-button wide" type="submit">{{ $bn ? 'ডাটাবেজ আপডেট রান করুন' : 'Run Database Update' }}</button>
                        </form>
                        <p>{{ $bn ? 'এই অপশন business data reset, rollback বা truncate করে না।' : 'This action does not reset, roll back, or truncate business data.' }}</p>
                    </article>
                    @endif

                    @if($canManageUsers)
                    <article class="surface-card span-2" id="user-management-settings">
                        <div class="card-heading"><div><h2>{{ $bn ? 'ইউজার ম্যানেজমেন্ট' : 'User Management' }}</h2><p>{{ $bn ? 'অন্য ব্যবহারকারী ও তাদের অ্যাক্সেস স্তর পরিচালনা করুন' : 'Manage users and role-based access' }}</p></div></div>
                        <form id="user-form" class="form-grid"><label><span>{{ $bn ? 'নাম' : 'Name' }}</span><input name="name" maxlength="255" required></label><label><span>{{ $bn ? 'মোবাইল' : 'Mobile' }}</span><input name="mobile" maxlength="30" required></label><label><span>{{ $bn ? 'ইমেইল' : 'Email' }}</span><input name="email" type="email" maxlength="255"></label><label><span>{{ $bn ? 'রোল' : 'Role' }}</span><select name="role">@if($user->isSuperAdmin())<option value="admin">Admin</option>@endif<option value="manager">Business User</option><option value="user">Normal User</option></select></label><label><span>6-digit PIN</span><input name="pin" minlength="6" maxlength="6" inputmode="numeric" required></label><label class="toggle-line"><input type="checkbox" name="is_activated" value="1" checked><span>{{ $bn ? 'সক্রিয়' : 'Active' }}</span></label><div class="form-actions"><button class="primary-button" type="submit">{{ $bn ? 'ইউজার তৈরি' : 'Create user' }}</button></div></form>
                        <div id="users-list" class="entity-list compact-list"></div>
                    </article>
                    @endif
                </div>
                <form method="post" action="{{ route('safa.logout') }}" class="logout-card">@csrf<button class="danger-button" type="submit">{{ $bn ? 'লগ আউট' : 'Sign out' }}</button></form>
            </section>
        </main>
    </div>

    <div id="subpage" class="subpage hidden" aria-live="polite"><div id="subpage-content" class="subpage-content"></div></div>
    <div id="modal" class="modal-layer hidden" role="dialog" aria-modal="true"><div id="modal-card" class="modal-card"></div></div>
    <div id="toast-region" class="toast-region" aria-live="polite"></div>

    <template id="create-customer-template"><form class="stack-form" data-create-entity="customers"><div class="subpage-toolbar"><button type="button" class="icon-button" data-close-modal aria-label="{{ $bn ? 'বন্ধ' : 'Close' }}"><span class="icon icon-close" aria-hidden="true"></span></button><div><p class="eyebrow">{{ $bn ? 'কাস্টমার' : 'Customer' }}</p><h2>{{ $bn ? 'নতুন কাস্টমার যোগ' : 'Add New Customer Profile' }}</h2></div></div><label><span>{{ $bn ? 'নাম' : 'Customer name' }}</span><input name="name" required maxlength="255"></label><label><span>{{ $bn ? 'মোবাইল' : 'Phone' }}</span><input name="phone" inputmode="tel" maxlength="50" required></label><label><span>{{ $bn ? 'ঠিকানা' : 'Address' }}</span><input name="address" maxlength="500"></label><button class="primary-button" type="submit">{{ $bn ? 'সংরক্ষণ' : 'Save' }}</button></form></template>
    <template id="create-supplier-template"><form class="stack-form" data-create-entity="suppliers"><div class="subpage-toolbar"><button type="button" class="icon-button" data-close-modal aria-label="{{ $bn ? 'বন্ধ' : 'Close' }}"><span class="icon icon-close" aria-hidden="true"></span></button><div><p class="eyebrow">{{ $bn ? 'সাপ্লায়ার' : 'Supplier' }}</p><h2>{{ $bn ? 'নতুন সাপ্লায়ার যোগ' : 'Add New Supplier Profile' }}</h2></div></div><label><span>{{ $bn ? 'প্রতিষ্ঠানের নাম' : 'Supplier or business name' }}</span><input name="name" required maxlength="255"></label><label><span>{{ $bn ? 'মোবাইল' : 'Phone' }}</span><input name="phone" inputmode="tel" maxlength="50" required></label><label><span>{{ $bn ? 'ঠিকানা' : 'Address' }}</span><input name="address" maxlength="500"></label><button class="primary-button" type="submit">{{ $bn ? 'সংরক্ষণ' : 'Save' }}</button></form></template>
</div>
</body>
</html>
