@php
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
    data-web-copy="{{ e(json_encode($webCopy, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES)) }}"
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

    <aside class="app-sidebar" aria-label="{{ __('web.application_navigation') }}">
        <div class="sidebar-brand">
            <button class="brand-trigger" type="button" data-open-settings aria-label="{{ __('web.open_settings') }}">
                <img class="brand-logo" src="{{ $logoSource }}" alt="">
                <span class="brand-copy"><strong class="brand-name">{{ $appName }}</strong><small>{{ __('web.business_workspace') }}</small></span>
            </button>
        </div>

        <nav class="mobile-bottom-nav app-navigation" aria-label="{{ __('web.primary_navigation') }}">
            <button class="bottom-nav-item active" type="button" data-nav="dashboard"><span class="icon icon-home" aria-hidden="true"></span><small>{{ __('web.home') }}</small></button>
            @if($canCustomers)<button class="bottom-nav-item" type="button" data-nav="customers"><span class="icon icon-people" aria-hidden="true"></span><small>{{ __('web.customers') }}</small></button>@endif
            @if($canSuppliers)<button class="bottom-nav-item" type="button" data-nav="suppliers"><span class="icon icon-supplier" aria-hidden="true"></span><small>{{ __('web.suppliers') }}</small></button>@endif
            @if($canWallet)<button class="bottom-nav-item" type="button" data-nav="wallet"><span class="icon icon-wallet" aria-hidden="true"></span><small>{{ __('web.wallet') }}</small></button>@endif
            @if($canExpenses)<button class="bottom-nav-item" type="button" data-nav="expenses"><span class="icon icon-payments" aria-hidden="true"></span><small>{{ __('web.expenses') }}</small></button>@endif
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
            <button class="brand-trigger" type="button" data-open-settings aria-label="{{ __('web.open_settings') }}">
                <img class="brand-logo" src="{{ $logoSource }}" alt="">
                <span class="brand-copy"><strong class="brand-name">{{ $appName }}</strong><small id="signed-user-name">{{ $user->name }}</small></span>
            </button>

            <div class="desktop-topbar-context">
                <strong>{{ __('web.business_workspace') }}</strong>
                <small>{{ $roleLabel }} · {{ $user->name }}</small>
            </div>

            <div class="topbar-actions">
                <label class="account-select">
                    <span class="sr-only">{{ __('web.business_account') }}</span>
                    <select id="account-select" {{ count($accounts) === 0 ? 'disabled' : '' }}>
                        @if(!$activeAccountId && count($accounts) > 1)<option value="">{{ __('web.account') }}</option>@endif
                        @foreach($accounts as $account)
                            <option value="{{ (int) $account['account_id'] }}" {{ (int)$activeAccountId === (int)$account['account_id'] ? 'selected' : '' }}>{{ $account['owner_name'] ?: __('web.account') }} · #{{ (int)$account['account_id'] }}</option>
                        @endforeach
                    </select>
                </label>
                <button class="icon-button" type="button" id="theme-toggle" title="{{ __('web.theme') }}" aria-label="{{ __('web.switch_theme') }}"><span class="icon icon-theme" aria-hidden="true"></span></button>
                <button class="icon-button" type="button" data-open-settings title="{{ __('web.settings') }}" aria-label="{{ __('web.settings') }}"><span class="icon icon-settings" aria-hidden="true"></span></button>
            </div>
        </header>

        <main class="mobile-content app-content" id="mobile-content">
            <div id="global-message" class="notice {{ $activeAccountId ? 'hidden' : '' }}">{{ __('web.select_account') }}</div>

            <section class="screen active dashboard-screen" data-screen="dashboard">
                <div class="screen-title">
                    <div class="screen-heading"><span class="screen-icon"><span class="icon icon-home" aria-hidden="true"></span></span><div><p class="eyebrow">{{ __('web.safa_workspace') }}</p><h1>{{ __('web.dashboard') }}</h1></div></div>
                    <button class="compact-button" type="button" data-action="refresh"><span class="icon icon-refresh" aria-hidden="true"></span>{{ __('web.refresh') }}</button>
                </div>

                <section class="dashboard-shortcuts" aria-label="{{ __('web.quick_actions') }}">
                    <div class="shortcut-grid">
                        @if($canCustomers)<button class="shortcut-action red" type="button" data-nav="customers"><span class="shortcut-badge"><span class="icon icon-people" aria-hidden="true"></span></span><span>{{ __('web.customers') }}</span></button>@endif
                        @if($canExpenses)<button class="shortcut-action orange" type="button" data-nav="expenses"><span class="shortcut-badge"><span class="icon icon-payments" aria-hidden="true"></span></span><span>{{ __('web.income_expense') }}</span></button>@endif
                        @if($canSuppliers)<button class="shortcut-action green" type="button" data-nav="suppliers"><span class="shortcut-badge"><span class="icon icon-supplier" aria-hidden="true"></span></span><span>{{ __('web.suppliers') }}</span></button>@endif
                        <button class="shortcut-action pink" type="button" data-open-settings><span class="shortcut-badge"><span class="icon icon-settings" aria-hidden="true"></span></span><span>{{ __('web.exchange_rates') }}</span></button>
                        @if($canWallet)<button class="shortcut-action yellow" type="button" data-nav="wallet"><span class="shortcut-badge"><span class="icon icon-wallet" aria-hidden="true"></span></span><span>{{ __('web.riyal_stock') }}</span></button>@endif
                        <a class="shortcut-action teal" href="#dashboard-overview-card"><span class="shortcut-badge"><span class="icon icon-home" aria-hidden="true"></span></span><span>{{ __('web.overview') }}</span></a>
                        <button class="shortcut-action indigo" type="button" data-action="refresh"><span class="shortcut-badge"><span class="icon icon-sync" aria-hidden="true"></span></span><span>{{ __('web.sync_data') }}</span></button>
                        <button class="shortcut-action brown" type="button" data-open-settings><span class="shortcut-badge"><span class="icon icon-user" aria-hidden="true"></span></span><span>{{ __('web.my_account') }}</span></button>
                    </div>
                </section>

                <div id="dashboard-stats" class="metric-grid"></div>
                <div class="surface-card" id="dashboard-overview-card"><div class="card-heading"><div><h2>{{ __('web.business_overview') }}</h2><p>{{ __('web.business_overview_help') }}</p></div></div><div id="dashboard-overview" class="overview-grid"></div></div>
            </section>

            @if($canCustomers)
            <section class="screen" data-screen="customers">
                <div class="screen-title"><div class="screen-heading"><span class="screen-icon"><span class="icon icon-people" aria-hidden="true"></span></span><div><p class="eyebrow">{{ __('web.customer_directory') }}</p><h1>{{ __('web.customers') }}</h1></div></div>@if($permissions['can_add_customers'] ?? false)<button class="primary-button" type="button" data-open-create="customer"><span class="icon icon-add" aria-hidden="true"></span>{{ __('web.add') }}</button>@endif</div>
                <div class="tool-row"><label class="search-box"><span class="icon icon-search" aria-hidden="true"></span><input id="customer-search" type="search" placeholder="{{ __('web.search_customer') }}"></label><select id="customer-filter"><option value="all">{{ __('web.all_customers') }}</option><option value="due">{{ __('web.due') }}</option><option value="advance">{{ __('web.advance') }}</option></select><select id="customer-sort"><option value="newest">{{ __('web.newest_first') }}</option><option value="oldest">{{ __('web.oldest_first') }}</option><option value="name">A–Z</option><option value="due">{{ __('web.by_due') }}</option></select></div>
                <div id="customers-list" class="entity-list"></div>
            </section>
            @endif

            @if($canSuppliers)
            <section class="screen" data-screen="suppliers">
                <div class="screen-title"><div class="screen-heading"><span class="screen-icon"><span class="icon icon-supplier" aria-hidden="true"></span></span><div><p class="eyebrow">{{ __('web.supplier_ledgers') }}</p><h1>{{ __('web.suppliers') }}</h1></div></div>@if($permissions['can_add_suppliers'] ?? false)<button class="primary-button" type="button" data-open-create="supplier"><span class="icon icon-add" aria-hidden="true"></span>{{ __('web.add') }}</button>@endif</div>
                <div class="tool-row"><label class="search-box"><span class="icon icon-search" aria-hidden="true"></span><input id="supplier-search" type="search" placeholder="{{ __('web.search_supplier') }}"></label><select id="supplier-filter"><option value="all">{{ __('web.all_suppliers') }}</option><option value="receivable">{{ __('web.receivable') }}</option><option value="payable">{{ __('web.payable') }}</option></select><select id="supplier-sort"><option value="newest">{{ __('web.newest_first') }}</option><option value="oldest">{{ __('web.oldest_first') }}</option><option value="name">A–Z</option><option value="balance">{{ __('web.balance') }}</option></select></div>
                <div id="suppliers-list" class="entity-list"></div>
            </section>
            @endif

            @if($canWallet)
            <section class="screen" data-screen="wallet">
                <div class="screen-title"><div class="screen-heading"><span class="screen-icon"><span class="icon icon-wallet" aria-hidden="true"></span></span><div><p class="eyebrow">{{ __('web.wallet_ledgers') }}</p><h1>{{ __('web.wallet') }}</h1></div></div><button class="primary-button" type="button" data-open-wallet-ledger><span class="icon icon-add" aria-hidden="true"></span>{{ __('web.ledger') }}</button></div>
                <div id="wallet-summary" class="metric-grid"></div>
                <div id="wallet-ledgers-list" class="entity-list"></div>
            </section>
            @endif

            @if($canExpenses)
            <section class="screen" data-screen="expenses">
                <div class="screen-title"><div class="screen-heading"><span class="screen-icon"><span class="icon icon-payments" aria-hidden="true"></span></span><div><p class="eyebrow">{{ __('web.daily_operations') }}</p><h1>{{ __('web.income_expense_title') }}</h1></div></div><button class="primary-button" type="button" data-open-expense><span class="icon icon-add" aria-hidden="true"></span>{{ __('web.entry') }}</button></div>
                <div id="expenses-summary" class="metric-grid"></div>
                <div id="expenses-list" class="entity-list"></div>
            </section>
            @endif

            <section class="screen sub-screen" data-screen="settings">
                <div class="screen-title"><div class="screen-heading"><span class="screen-icon"><span class="icon icon-settings" aria-hidden="true"></span></span><div><p class="eyebrow">{{ $roleLabel }}</p><h1>{{ __('web.settings') }}</h1></div></div><button class="compact-button" type="button" data-settings-back><span class="icon icon-back" aria-hidden="true"></span>{{ __('web.back') }}</button></div>
                <div class="settings-grid">
                    <article class="surface-card">
                        <div class="card-heading"><div><h2>{{ __('web.my_account_title') }}</h2><p>{{ __('web.signed_identity') }}</p></div></div>
                        <form id="profile-settings-form" class="stack-form">
                            <label><span>{{ __('web.name') }}</span><input name="name" maxlength="255" value="{{ $user->name }}" required></label>
                            <label><span>{{ __('web.mobile_number') }}</span><input name="mobile" maxlength="30" value="{{ $user->mobile }}" inputmode="tel" required></label>
                            <button class="primary-button" type="submit">{{ __('web.save_profile') }}</button>
                        </form>
                    </article>
                    <article class="surface-card">
                        <div class="card-heading"><div><h2>{{ __('web.personal_preferences') }}</h2><p>{{ __('web.language_appearance') }}</p></div></div>
                        <form id="personal-settings-form" class="stack-form"><label><span>{{ __('web.language') }}</span><select name="language"><option value="en" {{ $language === 'en' ? 'selected' : '' }}>English</option><option value="bn" {{ $language === 'bn' ? 'selected' : '' }}>বাংলা</option></select></label><label><span>{{ __('web.appearance') }}</span><select id="appearance-select"><option value="system">{{ __('web.system') }}</option><option value="light">{{ __('web.light') }}</option><option value="dark">{{ __('web.dark') }}</option></select></label><button class="primary-button" type="submit">{{ __('web.save_preferences') }}</button></form>
                    </article>
                    <article class="surface-card">
                        <div class="card-heading"><div><h2>{{ __('web.change_pin') }}</h2><p>{{ __('web.pin_help') }}</p></div></div>
                        <form id="pin-form" class="stack-form"><label><span>{{ __('web.current_pin') }}</span><input type="password" name="current_pin" minlength="6" maxlength="6" inputmode="numeric" required></label><label><span>{{ __('web.new_pin') }}</span><input type="password" name="new_pin" minlength="6" maxlength="6" inputmode="numeric" required></label><label><span>{{ __('web.confirm_pin') }}</span><input type="password" name="new_pin_confirmation" minlength="6" maxlength="6" inputmode="numeric" required></label><button class="primary-button" type="submit">{{ __('web.change_pin') }}</button></form>
                    </article>

                    @if($canManageSystemSettings)
                    <article class="surface-card span-2" id="brand-business-config">
                        <div class="card-heading"><div><h2>{{ __('web.brand_config') }}</h2><p>{{ __('web.brand_config_help') }}</p></div></div>
                        <form id="config-form" class="form-grid">
                            <label><span>{{ __('web.application_name') }}</span><input name="app_name" maxlength="255" value="{{ $appName }}" required></label>
                            <label><span>{{ __('web.local_currency') }}</span><input name="local_currency" maxlength="10" value="{{ $setting?->local_currency ?: 'BDT' }}" required></label>
                            <label><span>{{ __('web.foreign_currency') }}</span><input name="foreign_currency" maxlength="10" value="{{ $setting?->foreign_currency ?: 'SAR' }}" required></label>
                            @if($isSuperAdmin)<label><span>{{ __('web.app_version') }}</span><input name="app_version" maxlength="50" value="{{ $setting?->app_version ?: '1.0.0' }}"></label>@endif
                            <label class="toggle-line"><input type="checkbox" name="rate_based_mode" value="1" {{ ($setting?->rate_based_mode ?? true) ? 'checked' : '' }}><span>{{ __('web.rate_based_mode') }}</span></label>
                            <label class="toggle-line"><input type="checkbox" name="supplier_rate_enabled" value="1" {{ ($setting?->supplier_rate_enabled ?? true) ? 'checked' : '' }}><span>{{ __('web.supplier_rate') }}</span></label>
                            <label class="toggle-line"><input type="checkbox" name="wallet_rate_enabled" value="1" {{ ($setting?->wallet_rate_enabled ?? true) ? 'checked' : '' }}><span>{{ __('web.wallet_rate') }}</span></label>
                            <div class="form-actions"><button class="primary-button" type="submit">{{ __('web.save_configuration') }}</button></div>
                        </form>
                        <form id="logo-form" class="logo-form" enctype="multipart/form-data"><img id="settings-logo-preview" class="logo-preview" src="{{ $logoSource }}" alt=""><label><span>{{ __('web.upload_logo') }}</span><input type="file" name="logo" accept="image/png,image/jpeg,image/gif,image/webp" required></label><button class="secondary-button" type="submit">{{ __('web.upload_logo') }}</button></form>
                    </article>
                    @endif

                    @if($isSuperAdmin)
                    <article class="surface-card span-2" id="database-update-settings">
                        <div class="card-heading"><div><h2>{{ __('web.database_update') }}</h2><p>{{ __('web.database_update_help') }}</p></div></div>
                        <form method="post" action="{{ route('system.update.run') }}" class="stack-form">
                            @csrf
                            <button class="primary-button wide" type="submit">{{ __('web.run_database_update') }}</button>
                        </form>
                        <p>{{ __('web.database_update_safety') }}</p>
                    </article>
                    @endif

                    @if($canManageUsers)
                    <article class="surface-card span-2" id="user-management-settings">
                        <div class="card-heading"><div><h2>{{ __('web.user_management') }}</h2><p>{{ __('web.user_management_help') }}</p></div></div>
                        <form id="user-form" class="form-grid"><label><span>{{ __('web.name') }}</span><input name="name" maxlength="255" required></label><label><span>{{ __('web.mobile') }}</span><input name="mobile" maxlength="30" required></label><label><span>{{ __('web.email') }}</span><input name="email" type="email" maxlength="255"></label><label><span>{{ __('web.role') }}</span><select name="role">@if($user->isSuperAdmin())<option value="admin">Admin</option>@endif<option value="manager">Business User</option><option value="user">Normal User</option></select></label><label><span>6-digit PIN</span><input name="pin" minlength="6" maxlength="6" inputmode="numeric" required></label><label class="toggle-line"><input type="checkbox" name="is_activated" value="1" checked><span>{{ __('web.active') }}</span></label><div class="form-actions"><button class="primary-button" type="submit">{{ __('web.create_user') }}</button></div></form>
                        <div id="users-list" class="entity-list compact-list"></div>
                    </article>
                    @endif
                </div>
                <form method="post" action="{{ route('safa.logout') }}" class="logout-card">@csrf<button class="danger-button" type="submit">{{ __('web.sign_out') }}</button></form>
            </section>
        </main>
    </div>

    <div id="subpage" class="subpage hidden" aria-live="polite"><div id="subpage-content" class="subpage-content"></div></div>
    <div id="modal" class="modal-layer hidden" role="dialog" aria-modal="true" aria-labelledby="modal-title"><div id="modal-card" class="modal-card"></div></div>
    <div id="toast-region" class="toast-region" aria-live="polite" aria-atomic="true"></div>

    <template id="create-customer-template"><form class="stack-form" data-create-entity="customers"><div class="subpage-toolbar"><button type="button" class="icon-button" data-close-modal aria-label="{{ __('web.close') }}"><span class="icon icon-close" aria-hidden="true"></span></button><div><p class="eyebrow">{{ __('web.customer') }}</p><h2 id="modal-title">{{ __('web.add_customer') }}</h2></div></div><label><span>{{ __('web.customer_name') }}</span><input name="name" required maxlength="255"></label><label><span>{{ __('web.phone') }}</span><input name="phone" inputmode="tel" maxlength="50" required></label><label><span>{{ __('web.address') }}</span><input name="address" maxlength="500"></label><button class="primary-button" type="submit">{{ __('web.save') }}</button></form></template>
    <template id="create-supplier-template"><form class="stack-form" data-create-entity="suppliers"><div class="subpage-toolbar"><button type="button" class="icon-button" data-close-modal aria-label="{{ __('web.close') }}"><span class="icon icon-close" aria-hidden="true"></span></button><div><p class="eyebrow">{{ __('web.supplier') }}</p><h2>{{ __('web.add_supplier') }}</h2></div></div><label><span>{{ __('web.supplier_name') }}</span><input name="name" required maxlength="255"></label><label><span>{{ __('web.phone') }}</span><input name="phone" inputmode="tel" maxlength="50" required></label><label><span>{{ __('web.address') }}</span><input name="address" maxlength="500"></label><button class="primary-button" type="submit">{{ __('web.save') }}</button></form></template>
</div>
</body>
</html>
