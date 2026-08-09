# SAFA Phase 5 — Current Source Baseline Inventory

## Environment & Repository Baseline
- **Repository**: `masarax/safa`
- **Current Branch**: `main`
- **Commit SHA**: `9d2c138decacdbf301f5fcc79f0d9d01f108add1`
- **Audit Date**: August 9, 2026

---

## Source Inventory & Component Mapping

### 1. Android Native Application (`app/src/main/`)
- **Compose Screens**:
  - [`LoginScreen.kt`](file:///D:/Nazmus%20Sakib/safa/app/src/main/java/com/safa/account/ui/screens/LoginScreen.kt): SAFA visual logo header, single-language toggle, PIN input, server authentication.
  - [`DashboardScreen.kt`](file:///D:/Nazmus%20Sakib/safa/app/src/main/java/com/safa/account/ui/screens/DashboardScreen.kt): TallyKhata-style shortcuts, search & filter, recent transactions, reserves details.
  - [`CustomerScreen.kt`](file:///D:/Nazmus%20Sakib/safa/app/src/main/java/com/safa/account/ui/screens/CustomerScreen.kt): Customer ledger list, add/edit form, customer profile summary.
  - [`SupplierScreen.kt`](file:///D:/Nazmus%20Sakib/safa/app/src/main/java/com/safa/account/ui/screens/SupplierScreen.kt): Supplier deposit list, rate settlement, fund tracking.
  - [`TransactionScreen.kt`](file:///D:/Nazmus%20Sakib/safa/app/src/main/java/com/safa/account/ui/screens/TransactionScreen.kt): Remittance form, customer/supplier rate input, status tracking.
  - [`WalletScreen.kt`](file:///D:/Nazmus%20Sakib/safa/app/src/main/java/com/safa/account/ui/screens/WalletScreen.kt): Riyal stock registers, BDT cash registers, fund deduction form.
  - [`ExpenseScreen.kt`](file:///D:/Nazmus%20Sakib/safa/app/src/main/java/com/safa/account/ui/screens/ExpenseScreen.kt): Income & expense logging, period filtering.
  - [`SettingsScreen.kt`](file:///D:/Nazmus%20Sakib/safa/app/src/main/java/com/safa/account/ui/screens/SettingsScreen.kt): App settings, rate configuration, dark mode toggle, system reset.
  - [`CalculatorDialog.kt`](file:///D:/Nazmus%20Sakib/safa/app/src/main/java/com/safa/account/ui/screens/CalculatorDialog.kt): Bottom sheet financial calculator with Material 3 harmonized theme.
- **Design System Components**:
  - Defined in [`DesignSystemComponents.kt`](file:///D:/Nazmus%20Sakib/safa/app/src/main/java/com/safa/account/ui/components/DesignSystemComponents.kt): `AppCard`, `AppStatusChip`, `AppMetricCard`, `AppSectionHeader`, `AppPrimaryButton`, `AppOutlinedButton`, `AppTextField`, `SafaConfirmDialog`, `SafaDestructiveDialog`.

### 2. Laravel Backend Application (`backend/`)
- **Controllers & Middleware**:
  - [`InstallerController.php`](file:///D:/Nazmus%20Sakib/safa/backend/app/Http/Controllers/InstallerController.php): `index`, `process`, `updateView`, `updateProcess`, `autoHealExistingSchema`.
  - [`RemoteConfigController.php`](file:///D:/Nazmus%20Sakib/safa/backend/app/Http/Controllers/RemoteConfigController.php): Remote config API, logo upload, app version check.
  - [`CheckInstalled.php`](file:///D:/Nazmus%20Sakib/safa/backend/app/Http/Middleware/CheckInstalled.php) & [`EnsureNotInstalled.php`](file:///D:/Nazmus%20Sakib/safa/backend/app/Http/Middleware/EnsureNotInstalled.php).
- **Database Migrations (10 total)**:
  - `0001_01_01_000000_create_users_table.php`
  - `0001_01_01_000001_create_cache_table.php`
  - `0001_01_01_000002_create_jobs_table.php`
  - `2026_01_01_000000_create_safa_tables.php`
  - `2026_01_02_000000_expand_safa_and_wallet_tables.php`
  - `2026_01_03_000000_add_deleted_at_to_sync_tables.php`
  - `2026_01_04_000000_create_device_bindings_and_tokens_tables.php`
  - `2026_01_05_000000_create_superadmin_and_rbac_tables.php`
  - `2026_01_06_000000_create_account_shares_table.php`
  - `2026_01_07_000000_create_system_settings_table.php`
- **Blade Views**:
  - `welcome.blade.php`, `install.blade.php`, `install_update.blade.php`, `install_success.blade.php`.
