# SAFA (সাফা) — Multi-Account, Multi-Currency Financial Management

SAFA is an offline-first financial/account-management system with an Android client and Laravel 13 API backend. The current development architecture is intentionally API-first and account-scoped.

## Current Architecture

```text
Android (Kotlin / Jetpack Compose)
        |
        | HTTPS REST API
        | JWT + device/session/fingerprint verification
        v
Laravel 13 Backend
        |
        +-- Account authorization / sharing
        +-- Sync reconciliation / idempotency
        +-- Financial domain validation
        +-- Audit/security middleware
        |
        v
MySQL
```

### Android local storage

The current Android implementation uses a custom `SQLiteOpenHelper` local-first store. Payloads are encrypted before being written to the local database and the store contains a durable outbox, retry state, server versions and mutation metadata.

It is **not** currently SQLCipher/Room. Documentation should not describe it as SQLCipher/Room until the implementation is actually migrated.

### Authentication

The backend uses a custom JWT-based access token plus refresh/session/device/fingerprint security model. Auth-session token values are encrypted at rest and indexed using SHA-256 hashes for lookup.

The Android client stores its authentication credentials using `EncryptedSharedPreferences`.

### Database

The supported production configuration is currently MySQL, as reflected by `backend/.env.example` and the production-oriented migrations. SQLite is used by CI for isolated automated tests where supported.

## Core Modules

- Multi-account financial data isolation
- Customer management
- Supplier management
- Remittance/transaction management
- Wallet ledgers and batches
- Supplier deposits
- Expenses/incomes
- Offline-first Android synchronization
- Idempotent sync mutations and conflict reconciliation
- Account sharing with account-specific permission overrides
- JWT/device/session/fingerprint authentication
- Audit/security middleware

## Security Model

Every authenticated business request should resolve an explicit account context:

```text
Authenticated User
      |
      v
Requested Account
      |
      +--> Owner? -------- yes --> Authorized
      |
      +--> Exact Share? --- yes --> Authorized with effective permissions
      |
      +--> otherwise --------------> 403
```

A share for one account never authorizes another account owned by the same user.

Financial values are persisted using MySQL `DECIMAL` columns and API mutation paths normalize decimal strings instead of relying on binary floating-point arithmetic.

## Local Development

### Prerequisites

- Android Studio
- JDK 17
- PHP 8.3+
- Composer 2.x
- MySQL 8+ for normal backend development

### Android

From the repository root:

```bash
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
```

Do not put private API secrets in the APK. The mobile API key is a public client identifier; server secrets remain server-side.

### Laravel

```bash
cd backend
composer install
cp .env.example .env
php artisan key:generate
php artisan migrate
php artisan test
```

Provision the first SuperAdmin explicitly rather than embedding credentials in source code:

```bash
php artisan safa:provision-admin
```

### Fresh development database

This project is still under active development. For a clean schema, it is acceptable to reset the development database:

```bash
php artisan migrate:fresh
```

Only use `migrate:fresh` against a disposable development/test database.

## CI/CD

GitHub Actions currently verifies:

- PHP syntax
- Laravel migrations in isolated SQLite CI
- Full Laravel test suite
- Android unit tests
- Android lint
- Android debug build

Deployment is gated by the backend test suite before the Laravel backend is synchronized to cPanel.

## Important Development Rules

1. Never add passwords, PINs, JWT secrets, refresh tokens or private API secrets to Git.
2. Never authorize an account using owner identity alone when the requested account is not owned by the current user.
3. Every business table/query must remain account-scoped.
4. Every foreign business relationship must be verified against the active account.
5. Do not use PHP/Android binary floating point as the authoritative representation of money.
6. REST, GraphQL and Android sync should converge on the same domain validation rules.
7. Add a regression test for every security or synchronization bug that is fixed.

## Tests

Backend:

```bash
cd backend
php artisan test
```

Android:

```bash
./gradlew test
./gradlew lintDebug
./gradlew assembleDebug
```

## License

Private & Proprietary Financial Software. All rights reserved.
