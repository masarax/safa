# SAFA System Skill 01: Core Architecture & Strategic Blueprint

## 1. System Vision & Domain Model
SAFA (সাফা) is an offline-first enterprise multi-currency Hundi / Hawala accounting ledger and cashbook management application.
It integrates a **Kotlin + Jetpack Compose Android Client** with a **Laravel 13 REST API Backend**.

### Core Architecture Layers:
- **Presentation Layer**: 100% Jetpack Compose (Material Design 3), MVVM / MVI architecture with `StateFlow` and UI event state classes.
- **Data Layer (Offline-First)**: Room DB with SQLCipher encryption, repository layer mediating between Room and Remote API.
- **Security Layer**: AndroidX Biometric, SQLCipher AES-256 local database encryption, HMAC-SHA256 request signatures for sync.
- **Backend Sync Engine**: Laravel 13 REST API endpoints handling bi-directional delta synchronization with conflict resolution.

---

## 2. Directory & Module Navigation Strategy
When navigating or adding code to SAFA, strictly respect the modular directory layout:

```
safa/
├── app/src/main/java/com/safa/account/
│   ├── data/
│   │   ├── api/          # Retrofit endpoints, Interceptors, DTO Mappers
│   │   ├── dao/          # Room Data Access Objects
│   │   ├── database/     # Room Database instance & SQLCipher configuration
│   │   ├── model/        # Entity classes (Customer, Supplier, Transaction, Wallet, Expense)
│   │   ├── network/      # Network status observer & Sync engine primitives
│   │   └── repository/   # Single Source of Truth Repositories
│   └── ui/
│       ├── BiometricHelper.kt
│       ├── components/   # Modular Compose design system (M3 Cards, Dialogs, Headers)
│       ├── screens/      # Feature screens (Dashboard, Customer, Supplier, Transaction, Wallet, Expense, Reports, Settings, Login)
│       ├── theme/        # Color palette, Typography, M3 Shapes
│       └── viewmodel/    # StateFlow view models (CustomerViewModel, TransactionViewModel, etc.)
└── backend/
    ├── app/Http/Controllers/Api/   # AuthController, TransactionController, SyncController
    ├── app/Models/                  # Laravel Eloquent Models with Double-Entry logic
    ├── database/migrations/         # SQLite/PostgreSQL schema definitions
    └── routes/api.php               # HMAC authenticated REST API routes
```

---

## 3. Mandatory AI Execution Rules & Workflow
Any AI Agent working on this codebase MUST strictly follow these rules:

1. **Never mutate Room DB directly without repository abstraction**: All data modifications must flow through the Repository to ensure offline queueing and sync triggers.
2. **Offline First Integrity**: App operations must never fail due to lack of network connection. Offline actions receive a `sync_status = PENDING` flag and get pushed upon connection recovery.
3. **Double-Entry Ledger Accounting Standard**: Every money transfer or Hundi transaction must update both debit and credit entries or wallet balances symmetrically.
4. **M3 Design Standard**: No legacy XML views allowed. Use Material 3 Composables with proper state hoisting.
