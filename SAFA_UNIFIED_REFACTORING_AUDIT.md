# SAFA Enterprise System Architectural Deep Audit & Unified Refactoring Plan

## Executive Summary
This document outlines the full deep system audit and resolution roadmap to modernize the entire **SAFA (সাফা)** Multi-Currency Hawala/Hundi Ledger accounting application. 
It establishes 100% adherence to **Material Design 3 (M3) + Jetpack Compose** design principles, unifies UI screens & modals, centralizes localization strings, introduces global currency formatting, implements feature toggles (Rate-Based Operational Mode), and completes backend API synchronization.

---

## 1. Complete Architectural & UI Audit Findings

### A. Localization & Translation Integrity
- **Current Issue**: Multiple UI composables use inline English/Bengali ternary strings (`if (lang == "BN") "..." else "..."`) instead of reading from `HundiViewModel`'s centralized `t()` dictionary or Android standard string resources.
- **Resolution**:
  - Centralize ALL dictionary keys inside `bnMap` and `enMap` in `HundiViewModel.kt`.
  - Replace all inline string hardcodes across all screens (`CustomerScreen`, `SupplierScreen`, `TransactionScreen`, `WalletScreen`, `SettingsScreen`, `DashboardScreen`, `ExpenseScreen`, `ReportsScreen`, `LoginScreen`) with `viewModel.t(key)`.

### B. Dynamic Global Currency Symbol & Formatting
- **Current Issue**: Foreign currency symbol `"SAR"` and local currency symbol `"BDT"` are hardcoded in multiple UI card labels and transaction rows.
- **Resolution**:
  - Bind global currency state flows `selectedForeignCurrency` (e.g., SAR, AED, USD, MYR, INR, OMR, QAR, KWD) and `selectedLocalCurrency` (e.g., BDT) to all composables.
  - Automatically update all metric cards, transaction labels, and input fields dynamically when the user changes currency symbols in `SettingsScreen`.

### C. Rate-Based Operational Mode Global Toggle
- **Current Issue**: Buying rate vs selling rate calculation is always forced in transactions, which might confuse users who only track standard debit/credit Hundi amounts without supplier rate differentials.
- **Resolution**:
  - Introduce `isRateFeatureEnabled` boolean StateFlow in `HundiViewModel` and DataStore/SharedPreferences.
  - Add a toggle switch in `SettingsScreen` under "System Configuration".
  - When disabled, automatically hide supplier rate fields, profit margins, and wallet buying rate pools across `TransactionScreen`, `DashboardScreen`, and `WalletScreen`.

### D. Single-Page Unified Add & Edit Mode (Customer & Supplier)
- **Current Issue**: Editing customer/supplier profiles opens separate modals or subpages, creating UX fragmentation.
- **Resolution**:
  - Unify Add and Edit flows in `CustomerScreen` and `SupplierScreen` into a single reusable composable component (`CustomerFormContent` & `SupplierFormContent`).
  - When in "Edit Mode", pre-fill existing entity fields and update state upon submit; when in "Add Mode", initialize blank fields for new entry creation.

### E. App Branding & Custom Logo in Settings Page
- **Current Issue**: `SettingsScreen` lacks branding customization options for uploading or selecting a custom app logo.
- **Resolution**:
  - Implement dynamic App Logo selection/upload in `BrandingPage` of `SettingsScreen` via Android ImagePicker launcher.
  - Store selected image URI in `SharedPreferences` and display top header branding in `SettingsScreen` and App Header.

### F. Strict Material Design 3 (M3) & Jetpack Compose Standardization
- **Current Issue**: Deprecated M3 APIs (e.g., `AlertDialog`, `Divider`, `Icons.Filled.ArrowBack`) are still present in older screens. Custom non-M3 shapes are used in various dialog cards.
- **Resolution**:
  - Standardize all dialogs to Material 3 `BasicAlertDialog` / `AlertDialog` with M3 `Surface`, `MaterialTheme.colorScheme`, and `MaterialTheme.shapes`.
  - Replace all legacy `Divider` calls with `HorizontalDivider` / `VerticalDivider`.
  - Use `Icons.AutoMirrored.Filled.*` for directional icons.
  - Use M3 `CardDefaults.cardColors()`, `OutlinedTextField`, and `FilterChip` / `AssistChip` exclusively.

---

## 2. Comprehensive Execution Roadmap

```
+-----------------------------------------------------------------------------------+
|                        SAFA UNIFIED REFACTORING ROADMAP                           |
+-----------------------------------------------------------------------------------+
                                          │
 ┌────────────────────────────────────────┴───────────────────────────────────────┐
 │ Step 1: Centralized Localization & Global Currency Tokenization                │
 │   - Expand HundiViewModel bnMap/enMap for 100% UI key coverage.               │
 │   - Bind selectedForeignCurrency & selectedLocalCurrency across all screens.  │
 └────────────────────────────────────────┬───────────────────────────────────────┘
                                          │
 ┌────────────────────────────────────────┴───────────────────────────────────────┐
 │ Step 2: Rate-Based Hundi Mode Global Toggle                                   │
 │   - Add isRateFeatureEnabled StateFlow in ViewModel & Settings toggle.        │
 │   - Dynamically adapt Transaction & Wallet forms based on toggle state.        │
 └────────────────────────────────────────┬───────────────────────────────────────┘
                                          │
 ┌────────────────────────────────────────┴───────────────────────────────────────┐
 │ Step 3: Single-Page Unified Add/Edit Component Refactoring                     │
 │   - Refactor CustomerScreen & SupplierScreen to share single form composable.  │
 └────────────────────────────────────────┬───────────────────────────────────────┘
                                          │
 ┌────────────────────────────────────────┴───────────────────────────────────────┐
 │ Step 4: Strict Material Design 3 (M3) Overhaul                                 │
 │   - Replace deprecated M3 APIs, standardizing colors, shapes & dialogs.        │
 │   - Add custom App Logo branding picker in Settings page.                      │
 └────────────────────────────────────────┬───────────────────────────────────────┘
                                          │
 ┌────────────────────────────────────────┴───────────────────────────────────────┐
 │ Step 5: Full Build Verification & APK Compilation                              │
 │   - Verify zero compilation errors and compile signed Debug APK.               │
 └────────────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Verification & Compliance Checklist
- [x] Full architectural audit executed and documented.
- [ ] All UI strings localized through `HundiViewModel` `t()` dictionary.
- [ ] Global currency symbol dynamically bound across all UI screens.
- [ ] Global `isRateFeatureEnabled` toggle implemented in ViewModel & Settings.
- [ ] Unified Add/Edit page architecture implemented for Customers & Suppliers.
- [ ] 100% Material Design 3 (M3) API compliance verified across all screens.
- [ ] Debug APK successfully compiled and verified.
