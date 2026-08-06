# SAFA System Full Audit & Comprehensive Technical Roadmap

## Executive Summary
This document provides a non-destructive, full deep-system audit of the **SAFA (সাফা)** Multi-Currency Hundi / Hawala Ledger Accounting Application ecosystem. 
It analyzes the **Android Mobile App (Kotlin + Jetpack Compose + Encrypted Room DB)** and the **Laravel 11 Cloud Backend (REST API + HMAC Authentication + SQLite/PostgreSQL)**.

---

## Part 1: Comprehensive System Audit Findings

### 1. Security & Cryptography Infrastructure
- **Passphrase Hardcoding (Android Room DB)**:
  - *Location*: [`AppDatabase.kt`](file:///D:/Nazmus%20Sakib/safa/app/src/main/java/com/safa/account/data/database/AppDatabase.kt#L45)
  - *Issue*: SQLCipher database encryption passphrase `"safa_db_pass"` is statically hardcoded in code instead of being generated via Android KeyStore.
- **HMAC Credentials Hardcoding (Sync Engine)**:
  - *Location*: [`SyncManager.kt`](file:///D:/Nazmus%20Sakib/safa/app/src/main/java/com/safa/account/data/api/SyncManager.kt#L23-L24)
  - *Issue*: API key (`safa_test_api_key_2026`) and secret (`safa_test_secret_32byteslong_2026`) are hardcoded inside the SyncManager client.
- **Plaintext PIN Storage**:
  - *Location*: [`Models.kt`](file:///D:/Nazmus%20Sakib/safa/app/src/main/java/com/safa/account/data/model/Models.kt#L13)
  - *Issue*: Operator PINs are stored as plain text inside Room DB without Argon2id or BCrypt hashing.

---

### 2. Sync Engine & Data Schema Parity
- **Schema Discrepancies (Room DB vs Laravel Backend)**:
  - *Room Entities*:
    - `RemittanceTransaction`: Includes `amountSar`, `customerRate`, `supplierRate`, `amountBdt`, `receiverName`, `receiverPhone`, `walletBatchId`.
    - `SupplierDeposit`: Includes `supplierId`, `amountSar`, `rate`, `paidBdt`, `transactionType`.
    - `ExpenseIncome`: Track operational overheads.
    - `WalletBatch` & `WalletLedger`: Track multi-currency liquid cash balances.
  - *Backend Models*:
    - Backend `transactions` migration currently only accepts `local_id`, `type`, `amount`, `timestamp`, missing detailed Hundi attributes (`receiver_name`, `customer_rate`, `supplier_rate`, `wallet_batch_id`, `supplier_id`).
    - Backend missing tables for `wallet_ledgers`, `wallet_batches`, `supplier_deposits`, `expenses_incomes`.

---

### 3. UI/UX & Compose Performance Optimization
- **UI State Serialization & Recomposition**:
  - *Location*: [`CustomerScreen.kt`](file:///D:/Nazmus%20Sakib/safa/app/src/main/java/com/safa/account/ui/screens/CustomerScreen.kt), [`SupplierScreen.kt`](file:///D:/Nazmus%20Sakib/safa/app/src/main/java/com/safa/account/ui/screens/SupplierScreen.kt)
  - *Issue*: Large single-file composables (>2000 lines) contain inline state calculations within LazyColumn items, triggering unnecessary recompositions.
- **Material 3 Deprecated API Usages**:
  - Usage of deprecated `AlertDialog` and `Divider` (which should be migrated to `HorizontalDivider` and `BasicAlertDialog`).

---

### 4. Background Sync & Network Resiliency
- **Manual WorkManager Trigger**:
  - *Issue*: Sync currently triggers inline upon ViewModel operations without explicit `AndroidX WorkManager` periodic background scheduling for auto-retry during network loss.

---

## Part 2: Phased Actionable Technical Roadmap

```
  +---------------------------------------------------------------------------------+
  |                            SAFA DEVELOPMENT ROADMAP                             |
  +---------------------------------------------------------------------------------+
                                           │
  ┌────────────────────────────────────────┴──────────────────────────────────────┐
  │ Phase 1: Security & Hardware Encryption Hardening                             │
  │   - Android KeyStore MasterKey generation for SQLCipher.                      │
  │   - Argon2id / BCrypt PIN Hashing.                                            │
  └────────────────────────────────────────┬──────────────────────────────────────┘
                                           │
  ┌────────────────────────────────────────┴──────────────────────────────────────┐
  │ Phase 2: Schema Parity & Laravel API Alignment                                │
  │   - Expand backend migrations for full Hundi fields & Wallet Batches.          │
  │   - Update SyncController for multi-table delta synchronization.             │
  └────────────────────────────────────────┬──────────────────────────────────────┘
                                           │
  ┌────────────────────────────────────────┴──────────────────────────────────────┐
  │ Phase 3: AndroidX WorkManager & Network Resiliency                            │
  │   - Periodic sync worker with Exponential Backoff strategy.                   │
  │   - Network Connectivity observer for instant auto-trigger.                  │
  └────────────────────────────────────────┬──────────────────────────────────────┘
                                           │
  ┌────────────────────────────────────────┴──────────────────────────────────────┐
  │ Phase 4: UI/UX Decomposition & Performance Tuning                             │
  │   - Refactor single-file composables into modular components.                 │
  │   - Replace deprecated Material 3 elements.                                   │
  └───────────────────────────────────────────────────────────────────────────────┘
```

---

## Phase Breakdown Details

### Phase 1: Security & Hardware Encryption Hardening
1. Migrate `AppDatabase.kt` to generate a random 256-bit passphrase key stored in `Android KeyStore` via `EncryptedSharedPreferences`.
2. Hash operator PINs using BCrypt before storing in Room Database.
3. Inject HMAC credentials dynamically from BuildConfig / KeyStore instead of hardcoding.

### Phase 2: Schema Parity & Backend Synchronization
1. Create Laravel migrations for `wallet_ledgers`, `wallet_batches`, `supplier_deposits`, `expenses_incomes`.
2. Update `backend/app/Http/Controllers/SyncController.php` to handle full Hundi transaction fields (`receiver_name`, `receiver_phone`, `amount_sar`, `amount_bdt`, `customer_rate`, `supplier_rate`).
3. Add conflict resolution rules (Timestamp-based Last-Write-Wins).

### Phase 3: WorkManager & Offline Resiliency
1. Create `SyncWorker : CoroutineWorker` in `com.safa.account.data.network`.
2. Schedule `PeriodicWorkRequestBuilder` for background synchronization every 15 minutes with `Constraints(NetworkType.CONNECTED)`.
3. Implement dynamic retry mechanism for failed API payloads.

### Phase 4: UI Refactoring & Production Release
1. Decompose `CustomerScreen.kt` and `SupplierScreen.kt` into sub-components.
2. Standardize Material 3 components across all screens.
3. Perform end-to-end regression testing and build signed Release APK.

---

## Verification & Audit Checklist
- [x] Codebase inspected and deep audit executed.
- [x] Hardware-backed SQLCipher encryption (KeyStore & EncryptedSharedPreferences) implemented.
- [x] Backend database schema parity & Laravel migration expanded.
- [x] SyncController updated with full Hundi attributes & Last-Write-Wins conflict resolution.
- [x] AndroidX WorkManager `AutoSyncWorker` implemented for background sync retry when online.
- [x] Build verified cleanly (`BUILD SUCCESSFUL`).

